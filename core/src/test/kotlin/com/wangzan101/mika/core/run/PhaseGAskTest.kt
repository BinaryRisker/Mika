package com.wangzan101.mika.core.run

import com.wangzan101.mika.core.assembly.toolDispatcher
import com.wangzan101.mika.core.cap.web.FakeWebPort
import com.wangzan101.mika.core.tool.FakeScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseGAskTest {
    private fun names(spec: RunSpec) = toolDispatcher(FakeScreen(), runSpec = spec).definitions().map { it.name }.toSet()

    @Test fun unattendedRunKeepsAskUserAndDropsOtherInteractiveTools() {
        val spec = RunSpec(RunId("g"), "check", "t", provenance = RunProvenance(RunOrigin.Trigger, Attendance.Unattended))
        val names = names(spec)
        assertTrue("ask_user" in names)
        assertFalse("request_confirmation" in names)
        assertFalse("hand_to_user" in names)
        assertFalse("schedule_task" in names)
        assertFalse("profile_edit" in names)
        assertFalse("skill_save" in names)
        assertFalse("memory_edit" in names)
    }

    @Test fun childRunStillHasNoAskUser() {
        val spec = RunSpec(
            RunId("c"), "查", "t",
            provenance = RunProvenance(RunOrigin.Child, Attendance.Unattended),
            parent = RunId("p"),
        )
        val names = toolDispatcher(FakeScreen(), web = FakeWebPort(), runSpec = spec).definitions().map { it.name }.toSet()
        assertFalse("ask_user" in names)
        assertFalse("hand_to_user" in names)
        assertFalse("request_confirmation" in names)
    }

    @Test fun pendingAskStoreKeepsLatestPerThreadAcrossReload() {
        val first = PendingAsk("a1", "t1", "发给哪个?", listOf("甲", "乙"), 10)
        val later = PendingAsk("a2", "t1", "还是哪个?", listOf("丙", "丁"), 20)
        val other = PendingAsk("a3", "t2", "几点?", emptyList(), 30)
        val store = MemoryPendingAskStore(listOf(first))
        store.put(later)
        store.put(other)
        assertEquals(later, store.get("t1"))
        assertEquals(other, store.get("t2"))
        assertEquals(listOf(later, other), store.all())
        assertEquals(later, store.remove("t1"))
        assertNull(store.get("t1"))
        assertEquals(listOf(other), store.all())
        val reloaded = MemoryPendingAskStore(store.all())
        assertEquals(other, reloaded.get("t2"))
        assertNull(reloaded.get("t1"))
    }

    @Test fun noPendingAsksIsANoOp() {
        NoPendingAsks.put(PendingAsk("a", "t", "问"))
        assertNull(NoPendingAsks.get("t"))
        assertTrue(NoPendingAsks.all().isEmpty())
        assertNull(NoPendingAsks.remove("t"))
    }
}
