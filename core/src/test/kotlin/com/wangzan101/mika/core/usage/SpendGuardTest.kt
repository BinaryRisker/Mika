package com.wangzan101.mika.core.usage

import com.wangzan101.mika.core.agent.FailKind
import com.wangzan101.mika.core.agent.StopRules
import com.wangzan101.mika.core.model.Author
import com.wangzan101.mika.core.model.Capabilities
import com.wangzan101.mika.core.model.ChatModel
import com.wangzan101.mika.core.model.Message
import com.wangzan101.mika.core.model.ModelReply
import com.wangzan101.mika.core.model.ModelRole
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.model.Role
import com.wangzan101.mika.core.model.StopReason
import com.wangzan101.mika.core.model.ToolSpec
import com.wangzan101.mika.core.model.Usage
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.ZoneOffset

class SpendGuardTest {
    @get:Rule val tmp = TemporaryFolder()

    private val user = listOf(Message(Role.User, listOf(Part.Text("hi")), Author.User))
    private val source = UsageSource("p1", "甲", "m1", UsagePurpose.of(ModelRole.Main))
    private val zone = ZoneOffset.UTC
    private val now = Instant.parse("2026-11-07T10:00:00Z")

    private class CountingModel : ChatModel {
        var calls = 0
        override val capabilities = Capabilities(toolCalls = true, vision = false, contextTokens = 8000, reasoning = false)
        override val name = "count"
        override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply {
            calls += 1
            return ModelReply(listOf(Part.Text("ok")), StopReason.EndTurn, Usage(10, 5))
        }
    }

    @Test fun capZeroStillCompletesEvenWhenTheLedgerAlreadyHasTokens() = runTest {
        val inner = CountingModel()
        val m = inner.capped(SpendBudget(dailyCap = { 0 }, dailyUsed = { 9_999 }))
        m.complete("s", user, emptyList())
        assertEquals(1, inner.calls)
    }

    @Test fun exhaustedDailyTotalDoesNotSendTheNextCompleteAndTheRunResultNamesSpend() = runTest {
        val ledger = UsageLedger(tmp.newFile("usage.json"), now = { now.toEpochMilli() }, flushDelayMs = 60_000)
        ledger.record(source, Usage(80, 20))
        val used = UsageQuery.tokensOnLocalDay(ledger.snapshot(), now, zone)
        assertEquals(100L, used)
        val inner = CountingModel()
        val m = inner.capped(SpendBudget(dailyCap = { 100 }, dailyUsed = {
            UsageQuery.tokensOnLocalDay(ledger.snapshot(), now, zone)
        }))
        try {
            m.complete("s", user, emptyList())
            fail("expected SpendStopped")
        } catch (e: SpendStopped) {
            assertTrue(e.message, "花费" in e.message.orEmpty())
        }
        assertEquals(0, inner.calls)
        val end = StopRules.modelFailed(SpendStopped("已到达当天花费上限"))
        assertTrue(end.reason, "花费" in end.reason)
        assertEquals(FailKind.Model, end.kind)
    }

    @Test fun routineCapExhaustedOnOneRunDoesNotBlockAnotherRoutine() = runTest {
        val inner = CountingModel()
        val m = inner.capped(SpendBudget())
        val spent = RunUsageTally().also { it.add(Usage(100, 0), 1) }
        val fresh = RunUsageTally()
        try {
            withContext(spent + RoutineTokenCap(100)) { m.complete("s", user, emptyList()) }
            fail("expected SpendStopped")
        } catch (e: SpendStopped) {
            assertTrue(e.message, "常规" in e.message.orEmpty() || "花费" in e.message.orEmpty())
        }
        assertEquals(0, inner.calls)
        val reply = withContext(fresh + RoutineTokenCap(100)) { m.complete("s", user, emptyList()) }
        assertEquals("ok", (reply.parts.first() as Part.Text).text)
        assertEquals(1, inner.calls)
    }

    @Test fun meteredThenCappedDoesNotRecordWhenTheCallIsBlocked() = runTest {
        val recorded = mutableListOf<Usage?>()
        val inner = CountingModel()
        val m = inner.metered(source) { _, u -> recorded += u }.capped(SpendBudget(dailyCap = { 1 }, dailyUsed = { 1 }))
        runCatching { m.complete("s", user, emptyList()) }
        assertEquals(0, inner.calls)
        assertTrue(recorded.isEmpty())
    }

    @Test fun tokensOnLocalDayUsesTheLocalCalendarDay() {
        val hour = UsageLedger.HOUR_MS
        val h = Math.floorDiv(now.toEpochMilli(), hour)
        val snap = UsageSnapshot(
            listOf(
                UsageRow(h, UsageKey("p1", "m1", UsagePurpose.Main), UsageCounts(input = 7, output = 3, calls = 1)),
                UsageRow(h - 24, UsageKey("p1", "m1", UsagePurpose.Main), UsageCounts(input = 100, output = 0, calls = 1)),
            ),
            emptyMap(),
        )
        assertEquals(10L, UsageQuery.tokensOnLocalDay(snap, now, zone))
    }
}
