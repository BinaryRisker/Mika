package com.wangzan101.mika.cap.sources

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.wangzan101.mika.core.cap.sources.CalendarEvent
import com.wangzan101.mika.core.cap.sources.CalendarRead
import com.wangzan101.mika.core.cap.sources.CalendarSource
import java.time.LocalDate
import java.time.ZoneId

/**
 * 经 CalendarContract 读今天和明天的事件。没有 READ_CALENDAR 时返回未授权，不带事件正文。
 * 不查 Attendees，避免把通讯录送进模型。
 */
class AndroidCalendarSource(private val context: Context) : CalendarSource {
    override fun todayAndTomorrow(): CalendarRead {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            return CalendarRead.Unauthorized(HOW)
        }
        return runCatching { query() }.fold(
            onSuccess = { CalendarRead.Ok(it) },
            onFailure = { e ->
                if (e is SecurityException) CalendarRead.Unauthorized(HOW)
                else CalendarRead.Failed(e.message?.take(160) ?: e::class.java.simpleName)
            },
        )
    }

    private fun query(): List<CalendarEvent> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = today.plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(start.toString())
            .appendPath(end.toString())
            .build()
        val events = ArrayList<CalendarEvent>()
        context.contentResolver.query(uri, PROJECTION, SELECTION, null, SORT)?.use { c ->
            val iTitle = c.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
            val iBegin = c.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
            val iEnd = c.getColumnIndexOrThrow(CalendarContract.Instances.END)
            val iAllDay = c.getColumnIndexOrThrow(CalendarContract.Instances.ALL_DAY)
            val iLocation = c.getColumnIndexOrThrow(CalendarContract.Events.EVENT_LOCATION)
            val iCal = c.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
            while (c.moveToNext()) {
                val begin = c.getLong(iBegin)
                if (begin < start || begin >= end) continue
                events += CalendarEvent(
                    title = c.getString(iTitle).orEmpty().ifBlank { "(无标题)" },
                    startMs = begin,
                    endMs = if (c.isNull(iEnd)) null else c.getLong(iEnd),
                    allDay = c.getInt(iAllDay) == 1,
                    location = c.getString(iLocation)?.ifBlank { null },
                    calendarName = c.getString(iCal)?.ifBlank { null },
                )
            }
        }
        return events
    }

    companion object {
        const val HOW = "打开系统设置 → 应用 → Mika → 权限 → 日历，允许读取日历。"
        private val PROJECTION = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
        )
        private const val SELECTION = "${CalendarContract.Calendars.VISIBLE}=1"
        private const val SORT = "${CalendarContract.Instances.BEGIN} ASC"
    }
}
