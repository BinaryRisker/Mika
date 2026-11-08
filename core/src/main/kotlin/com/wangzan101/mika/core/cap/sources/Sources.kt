package com.wangzan101.mika.core.cap.sources

/** 只读设备来源（日程、下一个闹钟）的能力 id。 */
const val SOURCES = "sources"

/** 本机日历里的一件事。不含受邀人邮箱。 */
data class CalendarEvent(
    val title: String,
    val startMs: Long,
    val endMs: Long?,
    val allDay: Boolean,
    val location: String?,
    val calendarName: String?,
)

sealed interface CalendarRead {
    data class Ok(val events: List<CalendarEvent>) : CalendarRead
    /** 没有读日历的权限。 [howToGrant] 告诉人怎么去系统设置打开。结果里不得带事件正文。 */
    data class Unauthorized(val howToGrant: String) : CalendarRead
    data class Failed(val reason: String) : CalendarRead
}

data class NextAlarm(val triggerMs: Long, val label: String?)

sealed interface AlarmRead {
    data class Ok(val alarm: NextAlarm) : AlarmRead
    data object None : AlarmRead
    data class Failed(val reason: String) : AlarmRead
}

fun interface CalendarSource {
    fun todayAndTomorrow(): CalendarRead
}

fun interface NextAlarmSource {
    fun next(): AlarmRead
}

/** 还没结束的下一件：按开始时间最早。已经过了结束（没有结束则按开始）的不算。 */
fun nextUpcomingEvent(events: List<CalendarEvent>, nowMs: Long): CalendarEvent? =
    events.filter { (it.endMs ?: it.startMs) >= nowMs }.minByOrNull { it.startMs }
