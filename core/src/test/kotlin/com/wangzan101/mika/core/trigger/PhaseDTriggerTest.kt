package com.wangzan101.mika.core.trigger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseDTriggerTest {
    private fun share(id: String, enabled: Boolean = true, types: List<ShareType> = listOf(ShareType.Image)): TriggerDefinition =
        TriggerDefinition(
            id, "t", "把图存下来", 0, enabled = enabled,
            displayName = "存图", kind = RoutineKind.Share, shareTypes = types,
        )

    private fun phrase(id: String, enabled: Boolean = true): TriggerDefinition =
        TriggerDefinition(
            id, "t", "总结今天的未读", 0, enabled = enabled,
            displayName = "总结未读", kind = RoutineKind.Manual,
        )

    @Test fun matchingShareImageSeesEnabledImageRoutines() {
        val routines = listOf(
            share("img"),
            share("off", enabled = false),
            share("text-only", types = listOf(ShareType.Text)),
            share("both", types = listOf(ShareType.Text, ShareType.Image)),
            phrase("say"),
            TriggerDefinition("timed", "t", "do", 1_000),
        )
        assertEquals(listOf("img", "both"), ShareMatch.matching(routines, ShareType.Image).map { it.id })
        assertEquals(listOf("text-only", "both"), ShareMatch.matching(routines, ShareType.Text).map { it.id })
    }

    @Test fun noMatchingShareIsEmpty() {
        assertTrue(ShareMatch.matching(listOf(phrase("say"), TriggerDefinition("timed", "t", "do", 1_000)), ShareType.Image).isEmpty())
    }

    @Test fun phrasesAreEnabledManualRoutines() {
        val routines = listOf(
            phrase("on"),
            phrase("off", enabled = false),
            share("img"),
            TriggerDefinition("timed", "t", "do", 1_000),
        )
        assertEquals(listOf("on"), ShareMatch.phrases(routines).map { it.id })
        assertEquals("总结未读", ShareMatch.phrases(routines).single().phraseName)
    }

    @Test fun composeGoalPutsShareAfterRoutine() {
        assertEquals("存图\n\nhttps://example.com", ShareMatch.composeGoal("存图", "https://example.com"))
        assertEquals("存图", ShareMatch.composeGoal("存图", "  "))
        assertEquals("https://example.com", ShareMatch.composeGoal(" ", "https://example.com"))
    }

    @Test fun enabledShareDoesNotArmAnAlarm() {
        val store = MemoryTriggerStore()
        store.upsert(share("img"))
        var armed: Long? = 0L
        TriggerEngine(store, { armed = it }) { _, _ -> }.rearm(1_000)
        assertNull(armed)
        assertTrue(store.claimDue(1_000).isEmpty())
    }

    @Test fun enabledManualDoesNotArmAnAlarm() {
        val store = MemoryTriggerStore()
        store.upsert(phrase("say"))
        var armed: Long? = 0L
        TriggerEngine(store, { armed = it }) { _, _ -> }.rearm(1_000)
        assertNull(armed)
    }

    @Test fun scheduledRoutineStillArms() {
        val store = MemoryTriggerStore()
        store.upsert(TriggerDefinition("timed", "t", "do", 5_000))
        var armed: Long? = null
        TriggerEngine(store, { armed = it }) { _, _ -> }.rearm(1_000)
        assertEquals(5_000L, armed)
    }

    @Test fun shareTypeOfImageAndText() {
        assertEquals(ShareType.Image, ShareMatch.shareType(hasImage = true))
        assertEquals(ShareType.Text, ShareMatch.shareType(hasImage = false))
        assertFalse(share("img").armsAlarm)
        assertTrue(TriggerDefinition("timed", "t", "do", 1_000).armsAlarm)
    }

    @Test fun fireDoesNotDispatchShareOrManualAndStillDispatchesScheduled() {
        val store = MemoryTriggerStore()
        store.upsert(share("img"))
        store.upsert(phrase("say"))
        store.upsert(TriggerDefinition("timed", "t", "do", 1_000))
        val sent = mutableListOf<String>()
        TriggerEngine(store, {}) { t, _ -> sent += t.id }.fire(1_000)
        assertEquals(listOf("timed"), sent)
    }
}
