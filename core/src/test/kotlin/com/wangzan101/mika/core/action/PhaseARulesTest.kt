package com.wangzan101.mika.core.action

import com.wangzan101.mika.core.agent.AgentLoop
import com.wangzan101.mika.core.agent.FakeModel
import com.wangzan101.mika.core.agent.SpeedFlags
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.core.assembly.toolDispatcher
import com.wangzan101.mika.core.cap.web.FakeWebPort
import com.wangzan101.mika.core.gate.ConfirmationGate
import com.wangzan101.mika.core.gate.ConfirmationRequest
import com.wangzan101.mika.core.guard.OperationGuards
import com.wangzan101.mika.core.model.ModelReply
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.model.Role
import com.wangzan101.mika.core.model.StopReason
import com.wangzan101.mika.core.record.RunEvent
import com.wangzan101.mika.core.record.RunSink
import com.wangzan101.mika.core.risk.IrrevAction
import com.wangzan101.mika.core.run.Attendance
import com.wangzan101.mika.core.run.RunId
import com.wangzan101.mika.core.run.RunInbox
import com.wangzan101.mika.core.run.RunOrigin
import com.wangzan101.mika.core.run.RunProvenance
import com.wangzan101.mika.core.run.RunSpec
import com.wangzan101.mika.core.testing.answerReply
import com.wangzan101.mika.core.tool.FakeScreen
import com.wangzan101.mika.core.tool.execute
import com.wangzan101.mika.core.uistate.ActionResult
import com.wangzan101.mika.core.uistate.ScreenPort
import com.wangzan101.mika.core.uistate.ScreenSnapshot
import com.wangzan101.mika.core.uistate.UiElement
import com.wangzan101.mika.core.uistate.WebInfo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 阶段 A:人在场的发送和删除要这一次同意;人不在场留草稿;禁止名单不出卡;付款和没有应付金额的提交保持原样。 */
@OptIn(ExperimentalCoroutinesApi::class)
class PhaseARulesTest {
    private class Page(
        private val elements: List<UiElement>,
        private val pkg: String = "com.chat",
        private val site: WebInfo? = null,
    ) : ScreenPort by FakeScreen() {
        val taps = mutableListOf<String>()
        private val shown = ScreenSnapshot("s1", pkg, elements, 1080, 2400, web = site)
        override suspend fun snapshot() = shown
        override suspend fun peek() = shown
        override suspend fun tapRef(ref: String, snapshotId: String) = ActionResult.Ok(changed = true).also { taps += ref }
    }

    private class Events : RunSink {
        val all = mutableListOf<RunEvent>()
        override fun event(e: RunEvent) { all += e }
    }

    private fun el(ref: String, role: String, name: String, bounds: List<Int>, editable: Boolean = false) =
        UiElement(ref, role, name, 0, 0, bounds, null, !editable, editable, false)

    private fun chat(title: String = "张三", body: String = "明天见") = Page(listOf(
        el("@e1", "text", title, listOf(0, 0, 999, 80)),
        el("@e2", "edit", body, listOf(0, 900, 790, 999), editable = true),
        el("@e3", "button", "发送", listOf(800, 900, 990, 990)),
    ))

    private fun button(name: String, extra: UiElement? = null) = Page(listOfNotNull(
        extra,
        el("@e1", "button", name, listOf(800, 940, 999, 999)),
    ))

    private fun reply(name: String, args: String) = ModelReply(listOf(Part.ToolCall("c-$name", name, args)), StopReason.ToolUse, null)
    private fun observe() = reply("phone_observe", "{}")
    private fun tap(ref: String = "@e3") = reply("phone_tap", """{"ref":"$ref","snapshotId":"s1"}""")
    private fun askSend() = reply("request_confirmation", """{"action":"send","target":"张三","content":"明天见","amount":"","app":"com.chat"}""")

    private fun FakeModel.toolResult(i: Int): JsonObject =
        Json.parseToJsonElement(requests[i].last { it.role == Role.Tool }.parts.filterIsInstance<Part.ToolResult>().first().content).jsonObject

    private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content

    private suspend fun executor(
        screen: ScreenPort,
        provenance: RunProvenance = RunProvenance(),
        autonomous: Boolean = false,
        rules: ActionRules = ActionRules.NONE,
        sink: RunSink = RunSink.NONE,
    ): ActionExecutor {
        val tools = toolDispatcher(screen)
        tools.execute(Part.ToolCall("o", "phone_observe", "{}"))
        return ActionExecutor(tools, OperationGuards(), ConfirmationGate(), gate = {}, recorder = ActionRecorder(sink),
            provenance = provenance, autonomous = autonomous, rules = rules)
    }

    private fun throwingFollowUp(into: (JsonObject) -> Unit) = object : FollowUp {
        override fun refused(content: JsonObject) = into(content)
        override suspend fun approve(request: ConfirmationRequest) = error("confirmation card was requested")
    }

    private suspend fun tapSend(executor: ActionExecutor, followUp: FollowUp = FollowUp.NONE, ref: String = "@e1") =
        executor.run(Part.ToolCall("tap", "phone_tap", """{"ref":"$ref","snapshotId":"s1"}"""), Origin.Model(1), followUp)

    @Test fun presentSendAsksAndOneApprovalCoversOnlyTheNextSend() = runTest {
        val s = chat()
        val m = FakeModel(observe(), tap(), askSend(), tap(), tap(), answerReply("发了"))
        val l = AgentLoop(m, toolDispatcher(s), "BASE", confirmations = ConfirmationGate())
        val run = async { l.run("发给张三") }
        advanceUntilIdle()
        assertEquals("confirmation_required", m.toolResult(2).s("error"))
        assertTrue(s.taps.isEmpty())
        advanceUntilIdle()
        assertTrue(l.state.value is TaskState.WaitingConfirmation)
        l.answerConfirmation(ConfirmChoice.Once)
        run.await()
        assertEquals(listOf("@e3"), s.taps)
        assertEquals("true", m.toolResult(4).s("ok"))
        assertEquals("confirmation_required", m.toolResult(5).s("error"))
        assertTrue("next matching" in m.toolResult(5).s("message"))
    }

    @Test fun refusalMeansThatSendDoesNotRun() = runTest {
        val s = chat()
        val m = FakeModel(observe(), askSend(), tap(), answerReply("没发"))
        val l = AgentLoop(m, toolDispatcher(s), "BASE", confirmations = ConfirmationGate())
        val run = async { l.run("发给张三") }
        advanceUntilIdle()
        assertTrue(l.state.value is TaskState.WaitingConfirmation)
        l.answerConfirmation(ConfirmChoice.Deny)
        run.await()
        assertTrue(s.taps.isEmpty())
        assertEquals("confirmation_declined", m.toolResult(2).s("error"))
        assertEquals("confirmation_required", m.toolResult(3).s("error"))
    }

    @Test fun unattendedAndComputerSendLeaveADraftInTheConversation() = runTest {
        for (provenance in listOf(
            RunProvenance(RunOrigin.Trigger, Attendance.Unattended),
            RunProvenance(RunOrigin.Mcp, Attendance.Unattended),
            RunProvenance(RunOrigin.Channel, Attendance.Present),
        )) {
            val s = chat()
            val m = FakeModel(observe(), tap(), answerReply("没有发出"))
            val end = AgentLoop(m, toolDispatcher(s), "BASE", confirmations = ConfirmationGate())
                .run(RunSpec(RunId("draft"), "发给张三", "t", provenance = provenance), RunInbox())
            assertTrue("$provenance $end", end is TaskState.Succeeded)
            assertTrue(s.taps.isEmpty())
            val draft = m.toolResult(2)
            assertEquals("not_sent", draft.s("error"))
            assertEquals("false", draft.s("executed"))
            assertEquals("true", draft.s("draft"))
            assertEquals("张三", draft.s("target"))
            assertEquals("明天见", draft.s("body"))
            assertEquals("没有发出", draft.s("message"))
            assertTrue(m.requests[2].last { it.role == Role.Tool }.parts.filterIsInstance<Part.ToolResult>().first().isError)
        }
    }

    @Test fun allowlistedSendStillDraftsWhenNobodyIsPresent() = runTest {
        val rules = ActionRules(allowSend = listOf(ActionRules.SendAllow("com.chat", "张三")))
        val cases = listOf(
            RunProvenance(RunOrigin.Trigger, Attendance.Unattended) to false,
            RunProvenance(RunOrigin.Mcp, Attendance.Unattended) to true,
            RunProvenance(RunOrigin.Channel, Attendance.Present) to false,
            RunProvenance(RunOrigin.Child, Attendance.Present) to false,
            RunProvenance(RunOrigin.Ui, Attendance.Unattended) to true,
        )
        for ((provenance, autonomous) in cases) {
            val s = chat()
            val sink = Events()
            var reported: JsonObject? = null
            val out = tapSend(
                executor(s, provenance, autonomous = autonomous, rules = rules, sink = sink),
                throwingFollowUp { reported = it },
                ref = "@e3",
            )
            out as ActionOutcome.Refused
            assertEquals("$provenance autonomous=$autonomous", "not_sent", out.code)
            assertEquals("张三", reported!!.s("target"))
            assertEquals("明天见", reported!!.s("body"))
            assertEquals("没有发出", reported!!.s("message"))
            assertEquals("false", reported!!.s("executed"))
            assertTrue(s.taps.isEmpty())
            assertEquals("drafted", sink.all.filterIsInstance<RunEvent.Approval>().single().decision)
        }
    }

    @Test fun unattendedSendInsideDoStepsKeepsTheDraft() = runTest {
        val s = chat()
        val rules = ActionRules(allowSend = listOf(ActionRules.SendAllow("com.chat", "张三")))
        val steps = reply("do_steps", """{"steps":[{"tool":"phone_tap","args":{"ref":"@e3","snapshotId":"s1"}}]}""")
        val m = FakeModel(observe(), steps, answerReply("没有发出"))
        val end = AgentLoop(
            m, toolDispatcher(s), "BASE", confirmations = ConfirmationGate(), rules = rules, flags = SpeedFlags(batchSteps = true),
        ).run(RunSpec(RunId("batch-draft"), "发给张三", "t", provenance = RunProvenance(RunOrigin.Trigger, Attendance.Unattended)), RunInbox())
        assertTrue("$end", end is TaskState.Succeeded)
        assertTrue(s.taps.isEmpty())
        val draft = m.toolResult(2)
        assertEquals("not_sent", draft.s("error"))
        assertEquals("false", draft.s("executed"))
        assertEquals("true", draft.s("draft"))
        assertEquals("张三", draft.s("target"))
        assertEquals("明天见", draft.s("body"))
        assertEquals("没有发出", draft.s("message"))
        assertFalse(draft.toString(), "batch_stopped" in draft.toString() || "was not run" in draft.toString() || "spends money" in draft.toString())
    }

    @Test fun presentSendInsideDoStepsKeepsTheSendConfirmation() = runTest {
        val s = chat()
        val steps = reply("do_steps", """{"steps":[{"tool":"phone_tap","args":{"ref":"@e3","snapshotId":"s1"}}]}""")
        val m = FakeModel(observe(), steps, answerReply("要确认"))
        val end = AgentLoop(m, toolDispatcher(s), "BASE", confirmations = ConfirmationGate(), flags = SpeedFlags(batchSteps = true)).run("发给张三")
        assertTrue("$end", end is TaskState.Succeeded)
        assertTrue(s.taps.isEmpty())
        val asked = m.toolResult(2)
        assertEquals("confirmation_required", asked.s("error"))
        assertEquals("send", asked.s("action"))
        val message = asked.s("message")
        assertTrue(message, "request_confirmation" in message && "action send" in message)
        assertFalse(message, "spends money" in message)
    }

    @Test fun unattendedDeleteIsNotDeletedAndPaidDeleteStaysUnavailable() = runTest {
        val plain = button("删除")
        var reported: JsonObject? = null
        val drafted = tapSend(executor(plain, RunProvenance(RunOrigin.Trigger, Attendance.Unattended)), throwingFollowUp { reported = it })
        drafted as ActionOutcome.Refused
        assertEquals("not_deleted", drafted.code)
        assertEquals("删除", reported!!.s("target"))
        assertEquals("", reported!!.s("body"))
        assertEquals("没有删除", reported!!.s("message"))
        assertTrue(plain.taps.isEmpty())

        val paid = button("删除", el("@e2", "text", "合计 ¥25", listOf(0, 800, 600, 860)))
        val remote = RunProvenance(RunOrigin.Mcp, Attendance.Unattended)
        var paidReport: JsonObject? = null
        val refused = tapSend(executor(paid, remote), throwingFollowUp { paidReport = it })
        refused as ActionOutcome.Refused
        assertEquals("approval_unavailable", refused.code)
        assertFalse("没有删除" in paidReport.toString())
        assertTrue(paid.taps.isEmpty())
    }

    @Test fun computerSubmittedPaymentStaysUnavailable() = runTest {
        val s = button("确认支付")
        val out = tapSend(executor(s, RunProvenance(RunOrigin.Mcp, Attendance.Unattended)), throwingFollowUp {})
        out as ActionOutcome.Refused
        assertEquals("approval_unavailable", out.code)
        assertEquals(IrrevAction.pay, out.hit?.action)
        assertTrue(s.taps.isEmpty())
    }

    @Test fun autonomousStillClearsPaymentAndStillAsksForSend() = runTest {
        val pay = button("确认支付")
        val order = button("提交订单")
        assertTrue(tapSend(executor(pay, autonomous = true)) is ActionOutcome.Ran)
        assertEquals(listOf("@e1"), pay.taps)
        assertTrue(tapSend(executor(order, autonomous = true)) is ActionOutcome.Ran)
        assertEquals(listOf("@e1"), order.taps)
        val s = chat()
        val asked = tapSend(executor(s, autonomous = true), throwingFollowUp {}, ref = "@e3")
        asked as ActionOutcome.Refused
        assertEquals("confirmation_required", asked.code)
        assertEquals(IrrevAction.send, asked.hit?.action)
        assertTrue(s.taps.isEmpty())
    }

    @Test fun packageDenyRefusesSendWithNoCardEvenWhenAllowlistedAndAutonomous() = runTest {
        val s = chat()
        val sink = Events()
        val rules = ActionRules(
            allowSend = listOf(ActionRules.SendAllow("com.chat", "张三")),
            deny = listOf(ActionRules.PackageDeny("com.chat", send = true, payment = true)),
        )
        val out = tapSend(executor(s, autonomous = true, rules = rules, sink = sink), throwingFollowUp {}, ref = "@e3")
        out as ActionOutcome.Refused
        assertEquals("package_denied", out.code)
        assertTrue("No confirmation was requested" in out.content.s("message"))
        assertTrue(s.taps.isEmpty())
        assertTrue(sink.all.none { it is RunEvent.Confirmation })
        assertEquals("denied", sink.all.filterIsInstance<RunEvent.Approval>().single().decision)
        val pay = button("确认支付")
        val paid = tapSend(executor(pay, autonomous = true, rules = rules), throwingFollowUp {})
        paid as ActionOutcome.Refused
        assertEquals("package_denied", paid.code)
        assertTrue(pay.taps.isEmpty())
    }

    @Test fun titleBarMismatchAsksAndAMatchingTitleRuns() = runTest {
        val mismatch = chat(title = "李四")
        val rules = ActionRules(allowSend = listOf(ActionRules.SendAllow("com.chat", "张三")))
        val asked = tapSend(executor(mismatch, rules = rules), ref = "@e3")
        asked as ActionOutcome.Refused
        assertEquals("confirmation_required", asked.code)
        assertTrue(mismatch.taps.isEmpty())
        val self = chat(title = "张三")
        val notSelf = ActionRules(allowSend = listOf(ActionRules.SendAllow("com.chat", "自己")))
        val stillAsked = tapSend(executor(self, rules = notSelf), ref = "@e3")
        assertTrue(stillAsked is ActionOutcome.Refused)
        assertTrue(self.taps.isEmpty())
        val matched = chat()
        val sink = Events()
        val ran = tapSend(executor(matched, rules = rules, sink = sink), ref = "@e3")
        assertTrue("$ran", ran is ActionOutcome.Ran)
        assertEquals(listOf("@e3"), matched.taps)
        assertEquals("allowed", sink.all.filterIsInstance<RunEvent.Approval>().single().decision)
        val web = Page(listOf(
            el("@e1", "text", "张三", listOf(0, 0, 999, 80)),
            el("@e2", "edit", "明天见", listOf(0, 900, 790, 999), editable = true),
            el("@e3", "button", "发送", listOf(800, 900, 990, 990)),
        ), pkg = "com.android.browser", site = WebInfo("https://www.example.com/in/chat", "张三", "t", 200))
        val siteRules = ActionRules(allowSend = listOf(ActionRules.SendAllow("https://example.com/chat", "张三")))
        assertTrue(tapSend(executor(web, rules = siteRules), ref = "@e3") is ActionOutcome.Ran)
        assertEquals(listOf("@e3"), web.taps)
    }

    @Test fun exactSubmitWithoutAPayableAmountStillRuns() = runTest {
        val s = button("提交")
        val out = tapSend(executor(s))
        assertTrue("$out", out is ActionOutcome.Ran)
        assertEquals(listOf("@e1"), s.taps)
    }

    @Test fun childCannotSendOrDelete() = runTest {
        val spec = RunSpec(RunId("child"), "research", "thread",
            provenance = RunProvenance(RunOrigin.Child, Attendance.Present), readOnly = false, parent = RunId("parent"))
        val names = toolDispatcher(FakeScreen(), web = FakeWebPort(), runSpec = spec).definitions().map { it.name }
        assertFalse(names.any { it.startsWith("phone_") || it == "request_confirmation" })
        val send = chat()
        val del = button("删除")
        val child = RunProvenance(RunOrigin.Child, Attendance.Unattended)
        val sent = tapSend(executor(send, child), throwingFollowUp {}, ref = "@e3")
        val deleted = tapSend(executor(del, child), throwingFollowUp {})
        assertEquals("not_sent", (sent as ActionOutcome.Refused).code)
        assertEquals("not_deleted", (deleted as ActionOutcome.Refused).code)
        assertTrue(send.taps.isEmpty())
        assertTrue(del.taps.isEmpty())
    }
}
