package com.wangzan101.mika.core.trigger

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * 本地时间的一个免打扰区间。[enabled] 默认关；打开后建议 22:00–08:00。
 * 区间是半开的 [start, end)：跨午夜时，当天 [start, 24:00) 与次日 [00:00, end) 都算在内。
 */
data class QuietHours(
    val enabled: Boolean = false,
    val startMinutes: Int = DEFAULT_START,
    val endMinutes: Int = DEFAULT_END,
) {
    init {
        require(startMinutes in 0 until MINUTES_PER_DAY && endMinutes in 0 until MINUTES_PER_DAY)
    }

    /** [nowMs] 是否落在打开的区间里。关着时永远不是。 */
    fun active(nowMs: Long, zone: ZoneId): Boolean {
        if (!enabled) return false
        return contains(minutesOf(nowMs, zone))
    }

    /**
     * 含 [nowMs] 的这一段区间何时结束（半开端点的瞬间）。
     * 调用方只在 [active] 为真时使用；关着或此刻不在区间内时退回 [nowMs]。
     */
    fun endAtMs(nowMs: Long, zone: ZoneId): Long {
        if (!enabled || !active(nowMs, zone)) return nowMs
        val zoned = Instant.ofEpochMilli(nowMs).atZone(zone)
        val minutes = zoned.hour * 60 + zoned.minute
        val date = zoned.toLocalDate()
        val endToday = date.atTime(clock(endMinutes)).atZone(zone).toInstant().toEpochMilli()
        val wraps = startMinutes >= endMinutes
        return if (!wraps || minutes < endMinutes) endToday
        else date.plusDays(1).atTime(clock(endMinutes)).atZone(zone).toInstant().toEpochMilli()
    }

    private fun contains(minutes: Int): Boolean =
        if (startMinutes < endMinutes) minutes in startMinutes until endMinutes
        else minutes >= startMinutes || minutes < endMinutes

    companion object {
        const val DEFAULT_START = 22 * 60
        const val DEFAULT_END = 8 * 60
        private const val MINUTES_PER_DAY = 24 * 60
        val OFF = QuietHours(enabled = false)

        private fun minutesOf(nowMs: Long, zone: ZoneId): Int {
            val local = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalTime()
            return local.hour * 60 + local.minute
        }

        private fun clock(minutes: Int): LocalTime = LocalTime.of(minutes / 60, minutes % 60)
    }
}
