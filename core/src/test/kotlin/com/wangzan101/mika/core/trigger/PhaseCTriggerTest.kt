package com.wangzan101.mika.core.trigger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class PhaseCTriggerTest {
    private val shanghai = ZoneId.of("Asia/Shanghai")
    private val quiet = QuietHours(enabled = true, startMinutes = 22 * 60, endMinutes = 8 * 60)
    /** 2026-11-07 22:30 上海。 */
    private val insideDnd = Instant.parse("2026-11-07T14:30:00Z").toEpochMilli()
    private val todayStart = Instant.parse("2026-11-06T16:00:00Z").toEpochMilli()

    @Test fun disabledRoutineDoesNotArmAnAlarm() {
        val store = MemoryTriggerStore()
        store.upsert(
            TriggerDefinition(
                "routine:morning", "t", "do", insideDnd,
                enabled = false,
                calendarTime = "08:00",
                calendarZone = shanghai.id,
                calendarDays = listOf(1, 2, 3, 4, 5),
            ),
        )
        var armed: Long? = 0L
        TriggerEngine(store, { armed = it }) { _, _ -> }.rearm(insideDnd)
        assertNull(armed)
    }

    @Test fun routineStatusQuietPostponedWhenDueInsideDnd() {
        val trigger = TriggerDefinition("job", "t", "do", insideDnd)
        assertEquals(
            RoutineStatus.QuietPostponed,
            routineStatus(trigger, insideDnd, quiet, shanghai, lastDueAtMs = null),
        )
    }

    @Test fun routineStatusUpcomingWhenNotYetDue() {
        val trigger = TriggerDefinition("job", "t", "do", insideDnd + 3_600_000)
        assertEquals(
            RoutineStatus.Upcoming,
            routineStatus(trigger, insideDnd, quiet, shanghai, lastDueAtMs = null),
        )
    }

    @Test fun routineStatusDisabledWhenOff() {
        val trigger = TriggerDefinition("job", "t", "do", insideDnd, enabled = false)
        assertEquals(
            RoutineStatus.Disabled,
            routineStatus(trigger, insideDnd, quiet, shanghai, lastDueAtMs = null),
        )
    }

    @Test fun routineStatusUsesLastStatusOnlyWhenLastDueIsToday() {
        val trigger = TriggerDefinition("job", "t", "do", insideDnd + 86_400_000, lastStatus = "unchanged")
        assertEquals(
            RoutineStatus.Unchanged,
            routineStatus(trigger, insideDnd, QuietHours.OFF, shanghai, lastDueAtMs = insideDnd),
        )
        assertEquals(
            RoutineStatus.Upcoming,
            routineStatus(trigger, insideDnd, QuietHours.OFF, shanghai, lastDueAtMs = todayStart - 1),
        )
    }

    @Test fun morningSeedIsDisabledAndNarrowed() {
        val seed = MorningRoutine.seed("thread", insideDnd, shanghai)
        assertEquals(false, seed.enabled)
        assertEquals(true, seed.notifyOnChange)
        assertEquals(10, seed.stopMinutes)
        assertEquals(MorningRoutine.ID, seed.builtinId)
        assertEquals(listOf("read_calendar", "read_next_alarm", "end_not_done"), seed.toolNames)
        assertEquals(listOf("sources"), seed.capabilityIds)
    }
}
