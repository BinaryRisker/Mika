package com.wangzan101.mika.core.agent

import com.wangzan101.mika.core.model.ModelReply
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.model.Role
import com.wangzan101.mika.core.model.StopReason
import com.wangzan101.mika.core.record.RunEvent
import com.wangzan101.mika.core.record.RunSink
import com.wangzan101.mika.core.run.Attendance
import com.wangzan101.mika.core.run.Delivery
import com.wangzan101.mika.core.run.DeliveryState
import com.wangzan101.mika.core.run.Interaction
import com.wangzan101.mika.core.run.MemoryPendingAskStore
import com.wangzan101.mika.core.run.RunId
import com.wangzan101.mika.core.run.RunInbox
import com.wangzan101.mika.core.run.RunOrigin
import com.wangzan101.mika.core.run.RunProvenance
import com.wangzan101.mika.core.run.RunSpec
import com.wangzan101.mika.core.tool.FakeScreen
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import com.wangzan101.mika.core.assembly.toolDispatcher

private var seq = 0
private fun call(name: String, args: String = "{}") = Part.ToolCall("i${++seq}", name, args)
private fun tools(vararg calls: Part.ToolCall) = ModelReply(calls.toList(), StopReason.ToolUse, null)
private fun text(s: String) = ModelReply(listOf(Part.Text(s)), StopReason.EndTurn, null)
private const val DONE = "好了"
private fun finish() = com.wangzan101.mika.core.testing.answerReply("好了")

@OptIn(ExperimentalCoroutinesApi::class)
class AgentLoopInputTest {
    private class ListSink : RunSink {
        val events = mutableListOf<RunEvent>()
        override fun event(e: RunEvent) { events += e }
    }

    private fun ask(options: String = ""","options":["同事张伟","同学张伟"]""") = call("ask_user", """{"prompt":"发给哪个张伟?"$options}""")
    private fun loop(m: FakeModel, timeoutMs: Long = 15 * 60_000L, sink: RunSink = RunSink.NONE) =
        AgentLoop(m, toolDispatcher(FakeScreen()), "BASE", sink = sink, inputTimeoutMs = timeoutMs)

    /** 第 [i] 次请求里配对 [c] 的工具结果,并断言后面没有另起的用户消息。 */
    private fun FakeModel.resultFor(c: Part.ToolCall, i: Int = 1): JsonObject {
        val h = requests[i].withoutRuntime()
        val at = h.indexOfFirst { m -> m.parts.any { it is Part.ToolCall && it.id == c.id } }
        val r = h[at + 1].parts.filterIsInstance<Part.ToolResult>().single()
        assertEquals(c.id, r.callId)
        assertEquals("answer is the tool result, no extra user message", at + 2, h.size)
        return Json.parseToJsonElement(r.content).jsonObject
    }

    private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content

    @Test fun answerIsToolResult() = runTest {
        val c = ask()
        val m = FakeModel(tools(c), finish(), finish())
        val l = loop(m)
        val run = async { l.run("t") }
        runCurrent() // advanceUntilIdle 会把虚拟时间推过追问时限
        assertEquals(TaskState.WaitingInput("发给哪个张伟?", listOf("同事张伟", "同学张伟")), l.state.value)
        assertTrue(l.answerInput("就第一个吧"))
        com.wangzan101.mika.core.testing.assertAnswered(DONE, run.await())
        // 用户答了、模型什么也没做就要交回答:提醒一次照做(或说明为什么不做),第二次照常收下
        val nudge = com.wangzan101.mika.core.agent.ContextBuilder.bodyOf(m.requests[2].last()).orEmpty()
        assertTrue(nudge, "发给哪个张伟?" in nudge && "Carry it out" in nudge)
        assertEquals(3, m.requests.size)
        val r = m.resultFor(c)
        assertEquals("true", r.s("ok")); assertEquals("就第一个吧", r.s("answer"))
        assertEquals(Role.Tool, m.requests[1].withoutRuntime().last().role)
    }

    @Test fun optionTextReturnedVerbatim() = runTest {
        val c = ask()
        val m = FakeModel(tools(c), finish())
        val l = loop(m)
        val run = async { l.run("t") }
        runCurrent() // advanceUntilIdle 会把虚拟时间推过追问时限
        l.answerInput("同学张伟")
        run.await()
        assertEquals("同学张伟", m.resultFor(c).s("answer"))
    }

    @Test fun timeoutIsErrorNotEmptyAnswer() = runTest {
        val c = ask()
        val m = FakeModel(tools(c), finish())
        val l = loop(m, timeoutMs = 60_000)
        val run = async { l.run("t") }
        runCurrent()
        advanceTimeBy(59_000); runCurrent()
        assertTrue(l.state.value is TaskState.WaitingInput)
        advanceTimeBy(2_000); runCurrent()
        com.wangzan101.mika.core.testing.assertAnswered(DONE, run.await())
        val r = m.resultFor(c)
        assertEquals("false", r.s("ok")); assertEquals("no_answer", r.s("error"))
        assertEquals("No answer came within the time limit.", r.s("message")); assertEquals("true", r.s("recoverable"))
        assertEquals("false", r.s("counts_as_failure"))
        assertNull(r["answer"])
        // 超时后才到的回答不算数,界面据此告诉用户来晚了
        assertFalse(l.answerInput("晚了"))
    }

    @Test fun defaultTimeoutIsFifteenMinutes() = runTest {
        val c = ask()
        val m = FakeModel(tools(c), finish())
        val l = AgentLoop(m, toolDispatcher(FakeScreen()), "BASE")
        val run = async { l.run("t") }
        runCurrent()
        advanceTimeBy(15 * 60_000L - 1_000); runCurrent()
        assertTrue(l.state.value is TaskState.WaitingInput)
        advanceTimeBy(2_000)
        run.await()
        assertEquals("no_answer", m.resultFor(c).s("error"))
    }

    @Test fun cancelDuringWaitEndsCancelled() = runTest {
        val m = FakeModel(tools(ask()))
        val l = loop(m)
        val run = async { l.run("t") }
        advanceTimeBy(1_000); runCurrent()
        assertTrue(l.state.value is TaskState.WaitingInput)
        l.cancel()
        assertEquals(TaskState.Cancelled, run.await())
        assertEquals(TaskState.Cancelled, l.state.value)
        assertEquals(1, m.requests.size)
    }

    @Test fun answerNotWrittenToRunRecord() = runTest {
        val sink = ListSink()
        val m = FakeModel(tools(ask(options = "")), finish())
        val l = loop(m, sink = sink)
        val run = async { l.run("t") }
        runCurrent() // advanceUntilIdle 会把虚拟时间推过追问时限
        l.answerInput("验证码 834921")
        run.await()
        assertFalse(sink.events.toString(), "834921" in sink.events.toString())
        assertTrue(sink.events.any { it is RunEvent.ToolResult && it.tool == "ask_user" })
    }

    @Test fun unattendedAskDeliversThenNoAnswerWithoutWaiting() = runTest {
        val published = mutableListOf<Delivery.Ask>()
        var resolved = 0
        val pending = MemoryPendingAskStore()
        val spec = RunSpec(RunId("g"), "t", "thread-g", provenance = RunProvenance(RunOrigin.Trigger, Attendance.Unattended))
        val c = ask()
        val m = FakeModel(tools(c), finish())
        val l = AgentLoop(
            m, toolDispatcher(FakeScreen(), runSpec = spec), "BASE",
            interaction = object : Interaction {
                override suspend fun publish(ask: Delivery.Ask): DeliveryState { published += ask; return DeliveryState.Delivered }
                override fun resolved(askId: String) { resolved++ }
            },
            pendingAsks = pending,
        )
        val start = currentTime
        com.wangzan101.mika.core.testing.assertAnswered(DONE, l.run(spec, RunInbox()))
        assertEquals("unattended ask_user must not wait", start, currentTime)
        assertEquals(1, published.size)
        assertEquals("发给哪个张伟?", published.single().text)
        assertEquals(listOf("同事张伟", "同学张伟"), published.single().options)
        assertEquals(0, resolved)
        val stored = pending.get("thread-g")!!
        assertEquals("发给哪个张伟?", stored.prompt)
        assertEquals(listOf("同事张伟", "同学张伟"), stored.options)
        val r = m.resultFor(c)
        assertEquals("false", r.s("ok")); assertEquals("no_answer", r.s("error"))
        assertNull(r["answer"])
    }

    @Test fun attendedAnswerClearsPendingAsk() = runTest {
        val pending = MemoryPendingAskStore()
        val c = ask()
        val m = FakeModel(tools(c), finish())
        val l = AgentLoop(m, toolDispatcher(FakeScreen()), "BASE", pendingAsks = pending)
        val run = async { l.run("t") }
        runCurrent()
        assertEquals("发给哪个张伟?", pending.get("local")?.prompt)
        assertTrue(l.answerInput("同事张伟"))
        run.await()
        assertNull(pending.get("local"))
        assertEquals("同事张伟", m.resultFor(c).s("answer"))
    }

    @Test fun attendedTimeoutClearsPendingAsk() = runTest {
        val pending = MemoryPendingAskStore()
        val c = ask()
        val m = FakeModel(tools(c), finish())
        val l = AgentLoop(m, toolDispatcher(FakeScreen()), "BASE", inputTimeoutMs = 60_000, pendingAsks = pending)
        val run = async { l.run("t") }
        runCurrent()
        assertEquals("发给哪个张伟?", pending.get("local")?.prompt)
        advanceTimeBy(61_000); runCurrent()
        run.await()
        assertNull(pending.get("local"))
        assertEquals("no_answer", m.resultFor(c).s("error"))
        assertNull(m.resultFor(c)["answer"])
    }

}
