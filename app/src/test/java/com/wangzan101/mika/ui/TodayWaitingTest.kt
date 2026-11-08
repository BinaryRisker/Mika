package com.wangzan101.mika.ui

import com.wangzan101.mika.core.agent.GateReason
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.core.gate.ConfirmationRequest
import com.wangzan101.mika.core.risk.IrrevAction
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayWaitingTest {
    @Test fun waitingIncludesUnansweredAsk() {
        assertTrue(waitingFromState(TaskState.WaitingInput("发给哪个张伟?", listOf("甲", "乙"))))
        assertTrue(waitingFromState(TaskState.WaitingConfirmation(ConfirmationRequest(IrrevAction.send, "张三", "明天见", null, "com.chat"))))
        assertTrue(waitingFromState(TaskState.WaitingTakeover("login", "需要登录")))
        assertTrue(waitingFromState(TaskState.Held(GateReason("paused", "等一下"))))
        assertFalse(waitingFromState(TaskState.Running(1, "在搜")))
        assertFalse(waitingFromState(TaskState.Succeeded("好了")))
    }
}
