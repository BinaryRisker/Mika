package com.wangzan101.mika.core.cap.sources

import com.wangzan101.mika.core.assembly.toolDispatcher
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.run.RunId
import com.wangzan101.mika.core.run.RunSpec
import com.wangzan101.mika.core.tool.FakeScreen
import com.wangzan101.mika.core.tool.execute
import com.wangzan101.mika.core.trigger.MorningRoutine
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourcesToolsTest {
    @Test fun unauthorizedCalendarOmitsEventBodies() = runTest {
        val dispatcher = toolDispatcher(
            FakeScreen(),
            calendar = { CalendarRead.Unauthorized("打开系统设置") },
        )
        val result = dispatcher.execute(Part.ToolCall("c", "read_calendar", "{}"))
        assertEquals("false", result.content["ok"]?.jsonPrimitive?.content)
        assertEquals("unauthorized", result.content["error"]?.jsonPrimitive?.content)
        assertEquals("未授权", result.content["message"]?.jsonPrimitive?.content)
        assertFalse(result.content.containsKey("events"))
        assertFalse(result.content.toString().contains("会议"))
    }

    @Test fun morningToolTableHasOnlySourcesAndEnd() {
        val spec = RunSpec(
            RunId("morning"), MorningRoutine.GOAL, "thread",
            capabilities = MorningRoutine.CAPABILITIES,
            toolNames = MorningRoutine.TOOL_NAMES,
        )
        val dispatcher = toolDispatcher(
            FakeScreen(),
            calendar = { CalendarRead.Ok(emptyList()) },
            nextAlarm = { AlarmRead.None },
            runSpec = spec,
        )
        val names = dispatcher.definitions().map { it.name }.toSet()
        assertEquals(setOf("read_calendar", "read_next_alarm", "end_not_done"), names)
        assertTrue(names.none { it.startsWith("phone_") })
        assertTrue(names.none { it.startsWith("web_") })
        assertTrue("run_command" !in names)
        assertTrue("run_bundled_script" !in names)
        assertTrue("ask_user" !in names)
        assertTrue("memory_save" !in names)
    }

    @Test fun firstMorningRunIsSilentUnlessReadFailedOrNoAlarm() {
        val event = CalendarEvent("周会", 1L, 2L, false, null, null)
        val alarm = NextAlarm(9L, "起床")
        val first = MorningSnapshot(listOf(event), alarm)
        assertFalse(MorningNotify.shouldSpeak(null, first))
        assertFalse(MorningNotify.shouldSpeak(first, first))
        assertTrue(MorningNotify.shouldSpeak(first, first.copy(alarm = NextAlarm(10L, "起床"))))
        assertTrue(MorningNotify.shouldSpeak(first, MorningSnapshot(listOf(event), alarm, readFailed = true)))
        assertTrue(MorningNotify.shouldSpeak(first, MorningSnapshot(listOf(event), alarm = null)))
        assertTrue(MorningNotify.shouldSpeak(null, MorningSnapshot(readFailed = true)))
        assertTrue(MorningNotify.shouldSpeak(null, MorningSnapshot(alarm = null)))
    }

    @Test fun nextUpcomingEventSkipsEndedAndPicksEarliestStart() {
        val past = CalendarEvent("过了", 1L, 2L, false, null, null)
        val live = CalendarEvent("进行中", 5L, 20L, false, null, null)
        val later = CalendarEvent("稍后", 15L, 16L, false, null, null)
        assertEquals(live, nextUpcomingEvent(listOf(past, later, live), nowMs = 10L))
        assertEquals(null, nextUpcomingEvent(listOf(past), nowMs = 10L))
    }

    @Test fun snapshotRoundTripKeepsFingerprint() {
        val snap = MorningSnapshot(
            listOf(CalendarEvent("周会", 1L, 2L, false, "A座", "工作")),
            NextAlarm(9L, "起床"),
        )
        val back = MorningSnapshot.decode(snap.encode())
        assertEquals(snap.fingerprint(), back!!.fingerprint())
        assertEquals(snap, back)
    }
}
