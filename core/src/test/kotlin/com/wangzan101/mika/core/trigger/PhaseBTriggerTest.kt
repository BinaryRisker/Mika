package com.wangzan101.mika.core.trigger

import com.wangzan101.mika.core.agent.FailKind
import com.wangzan101.mika.core.agent.StopRules
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.core.usage.SpendStopped
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PhaseBTriggerTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val quiet = QuietHours(enabled = true, startMinutes = 22 * 60, endMinutes = 8 * 60)
    /** 2026-11-07 22:30 上海 = 14:30Z；区间结束 11-08 08:00 上海 = 00:00Z。 */
    private val insideDnd = Instant.parse("2026-11-07T14:30:00Z").toEpochMilli()
    private val quietEnd = Instant.parse("2026-11-07T16:00:00Z").toEpochMilli() // 上海 00:00, still in DND
    private val afterDnd = Instant.parse("2026-11-08T00:00:00Z").toEpochMilli()

    private fun engine(
        store: MemoryTriggerStore,
        quietHours: QuietHours = quiet,
        sent: MutableList<String> = mutableListOf(),
        armed: Array<Long?> = arrayOf(null),
    ) = TriggerEngine(
        store, { armed[0] = it },
        quietHours = { quietHours },
        zone = { shanghai },
    ) { t, _ -> sent += t.id }

    @Test fun modelPlanDueInsideDndIsNotClaimedAndArmsQuietEnd() {
        val store = MemoryTriggerStore()
        store.upsert(TriggerDefinition("job", "t", "do", insideDnd, intervalMs = 3_600_000))
        val sent = mutableListOf<String>()
        val armed = arrayOf<Long?>(null)
        val engine = engine(store, sent = sent, armed = armed)
        engine.fire(insideDnd)
        assertTrue(sent.isEmpty())
        assertEquals(null, store.windows().firstOrNull { it.triggerId == "job" })
        assertEquals(insideDnd, store.get("job")!!.nextAtMs)
        assertEquals(afterDnd, armed[0])
        engine.fire(insideDnd)
        assertTrue(sent.isEmpty())
        assertEquals(afterDnd, armed[0])
    }

    @Test fun afterDndTheMissedIntervalRunsOnceNotTwice() {
        val store = MemoryTriggerStore()
        store.upsert(TriggerDefinition("job", "t", "do", insideDnd, intervalMs = 3_600_000))
        val sent = mutableListOf<String>()
        val engine = engine(store, sent = sent)
        engine.fire(insideDnd)
        assertTrue(sent.isEmpty())
        engine.fire(afterDnd)
        assertEquals(listOf("job"), sent)
        assertEquals(1, store.windows().count { it.triggerId == "job" })
        engine.fire(afterDnd)
        assertEquals(listOf("job"), sent)
    }

    @Test fun reminderStillDispatchesInsideDnd() {
        val store = MemoryTriggerStore()
        store.upsert(TriggerDefinition("note", "t", "喝水", insideDnd, reminderText = "该喝水了"))
        val sent = mutableListOf<String>()
        engine(store, sent = sent).fire(insideDnd)
        assertEquals(listOf("note"), sent)
        assertEquals(TriggerWindowState.Dispatched, store.windows().single().state)
    }

    @Test fun postponedConditionCheckDoesNotWriteAFailure() {
        val store = MemoryTriggerStore()
        store.upsert(TriggerDefinition("rain", "t", "告诉我", insideDnd, intervalMs = 3_600_000, conditionGoal = "今天下雨吗"))
        val sent = mutableListOf<String>()
        engine(store, sent = sent).fire(insideDnd)
        assertTrue(sent.isEmpty())
        assertTrue(store.windows().none { it.triggerId == "rain" })
        assertTrue(store.windows().none { it.state == TriggerWindowState.Failed })
        assertEquals(insideDnd, store.get("rain")!!.nextAtMs)
    }

    @Test fun quietHoursOffLeavesExistingDispatchAlone() {
        val store = MemoryTriggerStore()
        store.upsert(TriggerDefinition("job", "t", "do", 1_000, intervalMs = 1_000))
        val sent = mutableListOf<String>()
        engine(store, quietHours = QuietHours.OFF, sent = sent).fire(1_000)
        assertEquals(listOf("job"), sent)
    }

    @Test fun firstFailureNotifiesThenTwoConsecutiveStayQuietThenSuccessResets() {
        val store = MemoryTriggerStore()
        store.upsert(TriggerDefinition("p", "t", "do", 1_000, intervalMs = 1_000))
        val engine = TriggerEngine(store, {}) { _, _ -> }
        engine.fire(1_000)
        assertTrue(engine.notePlanResult("p", PlanResult.Failure))
        assertEquals(1, store.get("p")!!.consecutiveFailures)
        assertFalse(engine.notePlanResult("p", PlanResult.Failure))
        assertFalse(engine.notePlanResult("p", PlanResult.Failure))
        assertEquals(3, store.get("p")!!.consecutiveFailures)
        assertTrue(engine.notePlanResult("p", PlanResult.Success))
        assertEquals(0, store.get("p")!!.consecutiveFailures)
        assertTrue(engine.notePlanResult("p", PlanResult.Failure))
    }

    @Test fun userCancelDoesNotCountAsFailure() {
        val store = MemoryTriggerStore()
        store.upsert(TriggerDefinition("p", "t", "do", 1_000, intervalMs = 1_000))
        val engine = TriggerEngine(store, {}) { _, _ -> }
        engine.fire(1_000)
        assertTrue(engine.notePlanResult("p", PlanResult.Failure))
        assertTrue(engine.notePlanResult("p", PlanResult.Cancel))
        assertEquals(1, store.get("p")!!.consecutiveFailures)
        assertFalse(engine.notePlanResult("p", PlanResult.Failure))
        assertEquals(2, store.get("p")!!.consecutiveFailures)
    }

    @Test fun modelErrorStepBudgetSpendStopAndRulesRefusalAreFailures() {
        assertEquals(PlanResult.Failure, PlanResult.of(TaskState.Failed("模型出错", FailKind.Model)))
        assertEquals(PlanResult.Failure, PlanResult.of(StopRules.stepLimit(25)))
        assertEquals(PlanResult.Failure, PlanResult.of(StopRules.modelFailed(SpendStopped("已到达当天花费上限"))))
        assertEquals(PlanResult.Failure, PlanResult.of(TaskState.Failed("用户没同意,没有发送", FailKind.GaveUp)))
        assertEquals(PlanResult.Cancel, PlanResult.of(TaskState.Cancelled))
        assertEquals(PlanResult.Success, PlanResult.of(TaskState.Succeeded("好了")))
        val spend = StopRules.modelFailed(SpendStopped("已到达当天花费上限"))
        assertTrue(spend.reason, "花费" in spend.reason)
    }

    @Test fun wrappingQuietIntervalIsActiveOvernightAndOpenAtEnd() {
        assertTrue(quiet.active(insideDnd, shanghai))
        assertTrue(quiet.active(quietEnd, shanghai))
        assertFalse(quiet.active(afterDnd, shanghai))
        assertEquals(afterDnd, quiet.endAtMs(insideDnd, shanghai))
        assertFalse(QuietHours.OFF.active(insideDnd, shanghai))
    }
}
