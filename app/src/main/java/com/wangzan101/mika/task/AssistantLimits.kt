package com.wangzan101.mika.task

import android.content.Context
import com.wangzan101.mika.core.trigger.QuietHours

/** 免打扰与花费上限的本机设置；默认关、上限 0，行为与现在相同。计划页/设置界面后补。 */
object AssistantLimits {
    private const val PREFS = "assistant_limits"
    private const val QUIET_ENABLED = "quiet_enabled"
    private const val QUIET_START = "quiet_start_minutes"
    private const val QUIET_END = "quiet_end_minutes"
    private const val DAILY_TOKEN_CAP = "daily_token_cap"

    fun quietHours(context: Context): QuietHours {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return QuietHours(
            enabled = p.getBoolean(QUIET_ENABLED, false),
            startMinutes = p.getInt(QUIET_START, QuietHours.DEFAULT_START),
            endMinutes = p.getInt(QUIET_END, QuietHours.DEFAULT_END),
        )
    }

    fun dailyTokenCap(context: Context): Long =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(DAILY_TOKEN_CAP, 0L).coerceAtLeast(0L)
}
