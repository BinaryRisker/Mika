package com.wangzan101.mika.core.trigger

import com.wangzan101.mika.core.search.SearchPrivacy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class PhaseFTriggerTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")

    @Test fun seedIsDisabledNarrowedAndEmptyTopicsCannotEnable() {
        val seed = WorthReading.seed("t", 1_000, shanghai)
        assertFalse(seed.enabled)
        assertEquals(WorthReading.TOOL_NAMES.toList(), seed.toolNames)
        assertEquals(listOf("web"), seed.capabilityIds)
        assertFalse(setOf("phone_tap", "run_command", "memory_save", "memory_edit", "web_navigate", "web_upload").any { it in seed.toolNames })
        assertFalse(WorthReading.canEnable(seed))
        val store = MemoryTriggerStore()
        store.upsert(seed)
        assertFalse(TriggerEngine(store, {}) { _, _ -> }.setEnabled(seed.id, true, 1_000))
        store.upsert(seed.copy(topics = listOf("固态电池")))
        assertTrue(TriggerEngine(store, {}) { _, _ -> }.setEnabled(seed.id, true, 1_000))
    }

    @Test fun emptyTopicsMeansNoSearchAndDisable() {
        val t = WorthReading.seed("t", 1_000, shanghai)
        assertTrue(WorthNotify.emptyTopics(t))
        assertFalse(WorthNotify.emptyTopics(t.copy(topics = listOf("关注甲"))))
        assertEquals("还没有关注", WorthReading.EMPTY_ANSWER)
    }

    @Test fun sameUrlOrClaimIsAlreadyShared() {
        val shared = listOf(SharedClaim("https://a.example/x", "固态电池量产"))
        assertTrue(WorthReading.alreadyShared("https://a.example/x/", "别的说法", shared))
        assertTrue(WorthReading.alreadyShared("https://b.example/y", "固态电池量产。", shared))
        assertFalse(WorthReading.alreadyShared("https://c.example/z", "钠电池", shared))
        val fresh = listOf(SharedClaim("https://c.example/z", "钠电池"))
        assertEquals(fresh, WorthNotify.newItems(fresh, shared))
        assertTrue(WorthNotify.newItems(shared, shared).isEmpty())
    }

    @Test fun unwatchedPausesAfterTwoUnseenNotifies() {
        var t = WorthReading.seed("t", 1_000, shanghai).copy(enabled = true, topics = listOf("甲"))
        t = WorthNotify.noteNotified(t)
        t = WorthNotify.onRunStart(t)
        assertTrue(t.enabled)
        assertEquals(1, t.unseenStreak)
        t = WorthNotify.noteNotified(t)
        t = WorthNotify.onRunStart(t)
        assertFalse(t.enabled)
        assertEquals(WorthNotify.UNWATCHED, t.lastStatus)
        assertEquals(RoutineStatus.Unwatched, routineStatus(t, 2_000, QuietHours.OFF, shanghai, lastDueAtMs = 1_000))
        t = WorthNotify.noteSeen(t.copy(enabled = true, lastStatus = null))
        t = WorthNotify.noteNotified(t)
        t = WorthNotify.noteSeen(t)
        t = WorthNotify.onRunStart(t)
        assertTrue(t.enabled)
        assertEquals(0, t.unseenStreak)
    }

    @Test fun watchPageFirstRunIsSilentThenChangeSpeaks() {
        assertFalse(WorthNotify.shouldSpeakWatch(null, "摘要甲"))
        assertFalse(WorthNotify.shouldSpeakWatch("摘要甲", "摘要甲"))
        assertTrue(WorthNotify.shouldSpeakWatch("摘要甲", "摘要乙"))
        val page = WatchPage.create("w1", "t", "https://example.com/n", "数字过 10", 1_000, shanghai)
        assertFalse(page.enabled)
        assertEquals(WorthReading.TOOL_NAMES.toList(), page.toolNames)
        assertEquals("https://example.com/n", page.watchUrl)
    }

    @Test fun searchPrivacyBlocksMemoryEmailNotInTopics() {
        val blocked = SearchPrivacy.blocked(
            "固态电池 alice@work.example",
            listOf("联系 alice@work.example"),
            "固态电池",
        )
        assertEquals("alice@work.example", blocked)
        assertNull(
            SearchPrivacy.blocked(
                "固态电池 alice@work.example",
                listOf("联系 alice@work.example"),
                "关注 alice@work.example 的固态电池",
            ),
        )
        assertNull(SearchPrivacy.blocked("固态电池", listOf("联系 alice@work.example"), "固态电池"))
    }
}
