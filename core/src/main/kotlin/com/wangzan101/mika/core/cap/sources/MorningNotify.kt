package com.wangzan101.mika.core.cap.sources

/**
 * 早晨安排上次记下的日程集合和下一个闹钟。存在常规上，不进记忆。
 * [readFailed] 为真时这次没读成（未授权或失败）；[alarm] 为 null 表示系统没有下一个闹钟。
 */
data class MorningSnapshot(
    val events: List<CalendarEvent> = emptyList(),
    val alarm: NextAlarm? = null,
    val readFailed: Boolean = false,
    val failReason: String? = null,
) {
    /** 用来比「有没有变化」的一行：事件按开始时间和标题排，不含受邀人。 */
    fun fingerprint(): String {
        val ev = events.sortedWith(compareBy<CalendarEvent> { it.startMs }.thenBy { it.title }).joinToString("|") { e ->
            listOf(e.title, e.startMs.toString(), e.endMs?.toString().orEmpty(), e.allDay.toString(),
                e.location.orEmpty(), e.calendarName.orEmpty()).joinToString("\u001f")
        }
        val al = alarm?.let { "${it.triggerMs}\u001f${it.label.orEmpty()}" } ?: "none"
        return "$ev::$al"
    }

    fun encode(): String = buildString {
        append(if (readFailed) "1" else "0")
        append('\n')
        append(failReason.orEmpty())
        append('\n')
        if (alarm == null) append("none") else {
            append(alarm.triggerMs)
            append('\t')
            append(alarm.label.orEmpty())
        }
        events.forEach { e ->
            append('\n')
            append(listOf(e.title, e.startMs.toString(), e.endMs?.toString().orEmpty(),
                if (e.allDay) "1" else "0", e.location.orEmpty(), e.calendarName.orEmpty())
                .joinToString("\t") { it.replace('\t', ' ').replace('\n', ' ') })
        }
    }

    companion object {
        fun decode(raw: String?): MorningSnapshot? {
            if (raw.isNullOrBlank()) return null
            val lines = raw.split('\n')
            if (lines.size < 3) return null
            val failed = lines[0] == "1"
            val reason = lines[1].ifBlank { null }
            val alarm = if (lines[2] == "none") null else {
                val p = lines[2].split('\t', limit = 2)
                NextAlarm(p[0].toLongOrNull() ?: return null, p.getOrNull(1)?.ifBlank { null })
            }
            val events = lines.drop(3).mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val p = line.split('\t')
                if (p.size < 4) return@mapNotNull null
                CalendarEvent(
                    title = p[0],
                    startMs = p[1].toLongOrNull() ?: return@mapNotNull null,
                    endMs = p.getOrNull(2)?.toLongOrNull(),
                    allDay = p[3] == "1",
                    location = p.getOrNull(4)?.ifBlank { null },
                    calendarName = p.getOrNull(5)?.ifBlank { null },
                )
            }
            return MorningSnapshot(events, alarm, failed, reason)
        }

        fun of(calendar: CalendarRead, alarm: AlarmRead): MorningSnapshot {
            val failed = calendar is CalendarRead.Unauthorized || calendar is CalendarRead.Failed || alarm is AlarmRead.Failed
            val reason = when (calendar) {
                is CalendarRead.Unauthorized -> "未授权"
                is CalendarRead.Failed -> calendar.reason
                else -> (alarm as? AlarmRead.Failed)?.reason
            }
            return MorningSnapshot(
                events = (calendar as? CalendarRead.Ok)?.events.orEmpty(),
                alarm = (alarm as? AlarmRead.Ok)?.alarm,
                readFailed = failed,
                failReason = reason,
            )
        }
    }
}

/**
 * 早晨安排出不出声：第一次只记下；读失败或没有下一个闹钟要通知；之后日程集合或闹钟时刻变了才通知。
 */
object MorningNotify {
    fun shouldSpeak(previous: MorningSnapshot?, current: MorningSnapshot): Boolean {
        if (current.readFailed) return true
        if (current.alarm == null) return true
        if (previous == null) return false
        return previous.fingerprint() != current.fingerprint()
    }
}
