package com.wangzan101.mika.core.trigger

import java.time.ZoneId

/** 内置「早晨安排」：安装后关着，工作日本地 8:00，变化才通知，停止 10 分钟。 */
object MorningRoutine {
    const val ID = "routine:morning"
    const val NAME = "早晨安排"
    const val TIME = "08:00"
    const val STOP_MINUTES = 10
    val WEEKDAYS: List<Int> = listOf(1, 2, 3, 4, 5)
    /** 能力 id 用字面量，避免 trigger 包引用 cap。 */
    val CAPABILITIES: Set<String> = setOf("sources")
    val TOOL_NAMES: Set<String> = setOf("read_calendar", "read_next_alarm", "end_not_done")
    const val GOAL = "读今天和明天的日程、下一个闹钟。和上次记下的日程集合、下一次闹钟时刻都相同，就回答「无变化」并带上今天的日期。有变化就列出变化。"

    fun seed(threadId: String, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): TriggerDefinition {
        val zoneId = zone.id
        return TriggerDefinition(
            id = ID,
            threadId = threadId,
            goal = GOAL,
            nextAtMs = nextCalendarAt(TIME, zoneId, WEEKDAYS, nowMs),
            enabled = false,
            calendarTime = TIME,
            calendarZone = zoneId,
            calendarDays = WEEKDAYS,
            displayName = NAME,
            notifyOnChange = true,
            stopMinutes = STOP_MINUTES,
            toolNames = TOOL_NAMES.toList(),
            capabilityIds = CAPABILITIES.toList(),
            builtinId = ID,
        )
    }
}
