package com.wangzan101.mika.core.cap.sources

import com.wangzan101.mika.core.observation.ObsKind
import com.wangzan101.mika.core.observation.Observation
import com.wangzan101.mika.core.observation.Trust
import com.wangzan101.mika.core.prompt.Prompts
import com.wangzan101.mika.core.tool.Args
import com.wangzan101.mika.core.tool.Concurrency
import com.wangzan101.mika.core.tool.Risk
import com.wangzan101.mika.core.tool.ToolContext
import com.wangzan101.mika.core.tool.ToolDef
import com.wangzan101.mika.core.tool.ToolExecution
import com.wangzan101.mika.core.tool.Verb
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val LOCAL: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

internal object ReadCalendar : SourcesTool {
    override val verb = Verb.ReadCalendar
    override val label = "读日程"
    override val risk = Risk.Read
    override val concurrency = Concurrency.ParallelSafe
    override val def = ToolDef("read_calendar", Prompts.tool("read_calendar"), emptyMap(), emptyList())

    override fun available(ctx: ToolContext) = ctx[SourcesCapability.KEY]?.calendar != null

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val source = checkNotNull(ctx[SourcesCapability.KEY]?.calendar)
        return when (val read = source.todayAndTomorrow()) {
            is CalendarRead.Ok -> ToolExecution(
                buildJsonObject {
                    put("ok", true)
                    put("events", JsonArray(read.events.map { it.json() }))
                },
                observations = listOf(
                    Observation(
                        ObsKind.Read,
                        if (read.events.isEmpty()) "今天和明天没有日程"
                        else read.events.joinToString("\n") { e ->
                            listOfNotNull(e.title, local(e.startMs), e.location, e.calendarName).joinToString(" · ")
                        },
                        source = name, trust = Trust.Trusted,
                    ),
                ),
            )
            is CalendarRead.Unauthorized -> ToolExecution(buildJsonObject {
                put("ok", false)
                put("error", "unauthorized")
                put("message", "未授权")
                put("how", read.howToGrant)
            })
            is CalendarRead.Failed -> ToolExecution(buildJsonObject {
                put("ok", false)
                put("error", "failed")
                put("message", read.reason)
            })
        }
    }
}

internal object ReadNotifications : SourcesTool {
    override val verb = Verb.ReadNotifications
    override val label = "读通知"
    override val risk = Risk.Read
    override val concurrency = Concurrency.ParallelSafe
    override val def = ToolDef("read_notifications", Prompts.tool("read_notifications"), emptyMap(), emptyList())

    override fun available(ctx: ToolContext) = ctx[SourcesCapability.KEY]?.notifications != null

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val source = checkNotNull(ctx[SourcesCapability.KEY]?.notifications)
        return when (val read = source.recent()) {
            is NotificationRead.Ok -> ToolExecution(
                buildJsonObject {
                    put("ok", true)
                    put("items", JsonArray(read.items.map { it.json() }))
                },
                observations = listOf(
                    Observation(
                        ObsKind.Read,
                        if (read.items.isEmpty()) "没有可看的通知"
                        else read.items.joinToString("\n") { n ->
                            listOfNotNull(n.appLabel ?: n.packageName, n.title, n.text).filter { it.isNotBlank() }.joinToString(" · ")
                        },
                        source = name, trust = Trust.Trusted,
                    ),
                ),
            )
            is NotificationRead.Unauthorized -> ToolExecution(buildJsonObject {
                put("ok", false)
                put("error", "unauthorized")
                put("message", "未授权")
                put("how", read.howToGrant)
            })
            is NotificationRead.Failed -> ToolExecution(buildJsonObject {
                put("ok", false)
                put("error", "failed")
                put("message", read.reason)
            })
        }
    }

    private fun NotificationPost.json() = buildJsonObject {
        put("app", appLabel ?: packageName)
        put("package", packageName)
        put("title", title)
        put("text", text)
        put("atMs", atMs)
    }
}

internal object ReadNextAlarm : SourcesTool {
    override val verb = Verb.ReadNextAlarm
    override val label = "下一个闹钟"
    override val risk = Risk.Read
    override val concurrency = Concurrency.ParallelSafe
    override val def = ToolDef("read_next_alarm", Prompts.tool("read_next_alarm"), emptyMap(), emptyList())

    override fun available(ctx: ToolContext) = ctx[SourcesCapability.KEY]?.alarms != null

    override suspend fun execute(a: Args, ctx: ToolContext): ToolExecution {
        val source = checkNotNull(ctx[SourcesCapability.KEY]?.alarms)
        return when (val read = source.next()) {
            is AlarmRead.Ok -> ToolExecution(
                buildJsonObject {
                    put("ok", true)
                    put("none", false)
                    put("triggerMs", read.alarm.triggerMs)
                    put("time", local(read.alarm.triggerMs))
                    val label = read.alarm.label
                    if (label.isNullOrBlank()) put("label", JsonNull) else put("label", label)
                },
                observations = listOf(
                    Observation(
                        ObsKind.Read,
                        listOfNotNull(local(read.alarm.triggerMs), read.alarm.label).joinToString(" · "),
                        source = name, trust = Trust.Trusted,
                    ),
                ),
            )
            AlarmRead.None -> ToolExecution(
                buildJsonObject {
                    put("ok", true)
                    put("none", true)
                    put("message", "没有下一个闹钟")
                },
                observations = listOf(Observation(ObsKind.Read, "没有下一个闹钟", source = name, trust = Trust.Trusted)),
            )
            is AlarmRead.Failed -> ToolExecution(buildJsonObject {
                put("ok", false)
                put("error", "failed")
                put("message", read.reason)
            })
        }
    }
}

private fun CalendarEvent.json() = buildJsonObject {
    put("title", title)
    put("startMs", startMs)
    put("start", local(startMs))
    if (endMs != null) {
        put("endMs", endMs)
        put("end", local(endMs))
    } else put("end", JsonNull)
    put("allDay", allDay)
    if (location.isNullOrBlank()) put("location", JsonNull) else put("location", location)
    if (calendarName.isNullOrBlank()) put("calendarName", JsonNull) else put("calendarName", calendarName)
}

private fun local(ms: Long): String = LOCAL.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
