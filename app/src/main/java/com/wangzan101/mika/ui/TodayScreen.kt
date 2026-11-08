package com.wangzan101.mika.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.wangzan101.mika.R
import com.wangzan101.mika.cap.sources.AndroidAlarmSource
import com.wangzan101.mika.cap.sources.AndroidCalendarSource
import com.wangzan101.mika.chat.ConversationStore
import com.wangzan101.mika.confirm.ConfirmationCopy
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.core.cap.sources.AlarmRead
import com.wangzan101.mika.core.cap.sources.CalendarEvent
import com.wangzan101.mika.core.cap.sources.CalendarRead
import com.wangzan101.mika.core.cap.sources.nextUpcomingEvent
import com.wangzan101.mika.core.trigger.RoutineStatus
import com.wangzan101.mika.core.trigger.TriggerDefinition
import com.wangzan101.mika.core.trigger.routineStatus
import com.wangzan101.mika.i18n.tr
import com.wangzan101.mika.task.AssistantLimits
import com.wangzan101.mika.task.TaskCenterState
import com.wangzan101.mika.task.TaskRun
import com.wangzan101.mika.task.TriggerRuntime
import com.wangzan101.mika.ui.components.ButtonKind
import com.wangzan101.mika.ui.components.ButtonSize
import com.wangzan101.mika.ui.components.EmptyNote
import com.wangzan101.mika.ui.components.GroupCard
import com.wangzan101.mika.ui.components.GroupLabel
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaButton
import com.wangzan101.mika.ui.components.PageBackground
import com.wangzan101.mika.ui.components.SearchField
import com.wangzan101.mika.ui.components.RowValue
import com.wangzan101.mika.ui.components.TileTone
import com.wangzan101.mika.ui.components.TopBar
import com.wangzan101.mika.ui.components.ValueTone
import com.wangzan101.mika.ui.theme.MikaIcons
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 今天这一页：下一个日程和闹钟、等你处理、今天的常规。
 * 打开 App 默认落在这里。不开始运行、不新建对话；立即执行沿用计划页的 [TriggerRuntime.runNow]。
 */
@Composable
fun TodayScreen(
    context: Context,
    taskState: TaskCenterState,
    othersRunning: Boolean,
    onOpenDrawer: () -> Unit,
    onOpenConversation: (String) -> Unit,
) {
    val runs by taskState.runs.collectAsState()
    var tick by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose {}
    }
    LaunchedEffect(Unit) {
        ConversationStore.shared(context).changes.collect { tick++ }
    }
    LaunchedEffect(runs) {
        if (runs.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(800)
            tick++
        }
    }
    val view = remember(runs, tick) { TodayView.load(context, runs) }
    var query by remember { mutableStateOf("") }
    val hits = remember(query, tick) {
        if (query.isBlank()) emptyList()
        else com.wangzan101.mika.ledger.LightLedgers.get(context).search(query)
    }
    val liveConversations = remember(tick) { ConversationStore.shared(context).list().map { it.id }.toSet() }

    PageBackground {
        Column(Modifier.fillMaxSize().testTag("today")) {
            TopBar(
                tr(R.string.today_title),
                left = MikaIcons.Menu, leftLabel = stringResource(R.string.chat_menu), onLeft = onOpenDrawer,
                leftBadge = othersRunning,
            )
            Column(
                Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            ) {
                SearchField(query, { query = it }, tr(R.string.today_search), Modifier.padding(bottom = 12.dp))
                if (query.isNotBlank()) {
                    GroupLabel(tr(R.string.today_search))
                    if (hits.isEmpty()) {
                        EmptyNote(tr(R.string.today_search_empty))
                    } else {
                        GroupCard {
                            hits.forEachIndexed { i, row ->
                                val open = row.conversationId in liveConversations
                                ListRow(
                                    MikaIcons.Doc, row.goal, listOfNotNull(row.outcome, row.files.joinToString().takeIf { it.isNotEmpty() }).joinToString(" · "),
                                    first = i == 0,
                                    onClick = if (open) ({ onOpenConversation(row.conversationId) }) else null,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                error?.let {
                    Text(it, Modifier.padding(bottom = 8.dp), style = MikaType.Caption, color = MikaTheme.colors.bad)
                }
                GroupLabel(tr(R.string.today_next_event))
                GroupCard {
                    ListRow(
                        MikaIcons.Calendar, view.eventTitle, view.eventDetail, first = true,
                        tone = if (view.eventFailed) TileTone.Warn else TileTone.Neutral,
                    )
                }
                GroupLabel(tr(R.string.today_next_alarm))
                GroupCard {
                    ListRow(
                        MikaIcons.Alarm, view.alarmTitle, view.alarmDetail, first = true,
                        tone = if (view.alarmFailed) TileTone.Warn else TileTone.Neutral,
                    )
                }
                GroupLabel(tr(R.string.today_waiting))
                if (view.waiting.isEmpty()) {
                    EmptyNote(tr(R.string.today_empty))
                } else {
                    GroupCard {
                        view.waiting.forEachIndexed { i, row ->
                            ListRow(
                                MikaIcons.Message, row.title, row.subtitle, first = i == 0,
                                tone = TileTone.Warn, onClick = { onOpenConversation(row.conversationId) },
                            ) { RowValue(tr(R.string.today_waiting), ValueTone.Warn) }
                        }
                    }
                }
                GroupLabel(tr(R.string.today_routines))
                if (view.routines.isEmpty()) {
                    EmptyNote(tr(R.string.today_empty))
                } else {
                    GroupCard {
                        view.routines.forEachIndexed { i, row ->
                            ListRow(
                                MikaIcons.Clock, row.title, row.subtitle, first = i == 0,
                                detail = row.folded,
                                tone = when (row.status) {
                                    RoutineStatus.Failed -> TileTone.Bad
                                    RoutineStatus.QuietPostponed, RoutineStatus.Unwatched -> TileTone.Warn
                                    RoutineStatus.Done, RoutineStatus.Unchanged, RoutineStatus.Recorded -> TileTone.Ok
                                    else -> TileTone.Neutral
                                },
                                onClick = { onOpenConversation(row.conversationId) },
                                trailing = {
                                    RowValue(row.statusLabel, row.statusTone)
                                    Spacer(Modifier.padding(start = 8.dp))
                                    MikaButton(
                                        tr(R.string.plans_run_now),
                                        onClick = {
                                            runCatching { TriggerRuntime.runNow(context, row.id); tick++; error = null }
                                                .onFailure { error = it.message ?: tr(R.string.op_failed) }
                                        },
                                        kind = ButtonKind.Text, size = ButtonSize.Small,
                                    )
                                },
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

internal data class TodayWaitingRow(val conversationId: String, val title: String, val subtitle: String)

internal data class TodayRoutineRow(
    val id: String,
    val conversationId: String,
    val title: String,
    val subtitle: String,
    val status: RoutineStatus,
    val statusLabel: String,
    val statusTone: ValueTone,
    val folded: String?,
)

internal data class TodayView(
    val eventTitle: String,
    val eventDetail: String?,
    val eventFailed: Boolean,
    val alarmTitle: String,
    val alarmDetail: String?,
    val alarmFailed: Boolean,
    val waiting: List<TodayWaitingRow>,
    val routines: List<TodayRoutineRow>,
) {
    companion object {
        fun load(context: Context, runs: List<TaskRun>, nowMs: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault()): TodayView {
            val calendar = AndroidCalendarSource(context).todayAndTomorrow()
            val alarm = AndroidAlarmSource(context).next()
            val (eventTitle, eventDetail, eventFailed) = eventLine(calendar, nowMs, zone)
            val (alarmTitle, alarmDetail, alarmFailed) = alarmLine(alarm, zone)
            val conversations = ConversationStore.shared(context)
            val liveIds = mutableSetOf<String>()
            val waiting = buildList {
                for (run in runs) {
                    val row = waitingFromRun(run) ?: continue
                    liveIds += run.conversationId
                    add(row)
                }
                val pending = (context.applicationContext as? com.wangzan101.mika.MikaApp)?.pendingAsks?.all().orEmpty()
                val titles = conversations.list().associate { it.id to it.title }
                for (ask in pending) {
                    if (ask.threadId in liveIds) continue
                    liveIds += ask.threadId
                    add(TodayWaitingRow(ask.threadId, ask.prompt, ChatCopy.plainTitle(titles[ask.threadId] ?: ask.prompt)))
                }
                for (summary in conversations.list()) {
                    if (summary.id in liveIds) continue
                    val turn = conversations.turns(summary.id).lastOrNull { isDraftOutcome(it.outcome) } ?: continue
                    add(TodayWaitingRow(summary.id, draftTitle(turn.outcome), ChatCopy.plainTitle(summary.title)))
                }
            }
            val snapshot = TriggerRuntime.snapshot(context)
            val quiet = AssistantLimits.quietHours(context)
            val routines = snapshot.triggers.filter { it.armsAlarm }.sortedBy { it.nextAtMs }.map { plan ->
                val lastDue = snapshot.windows.filter { it.triggerId == plan.id }.maxByOrNull { it.dueAtMs }?.dueAtMs
                val status = routineStatus(plan, nowMs, quiet, zone, lastDue)
                val folded = (plan.consecutiveFailures - 1).takeIf { it > 0 }?.let { tr(R.string.today_folded, it) }
                TodayRoutineRow(
                    id = plan.id,
                    conversationId = plan.threadId,
                    title = routineTitle(plan),
                    subtitle = worthSubtitle(plan, formatClock(plan.nextAtMs, zone)),
                    status = status,
                    statusLabel = statusLabel(status),
                    statusTone = statusTone(status),
                    folded = folded,
                )
            }
            return TodayView(eventTitle, eventDetail, eventFailed, alarmTitle, alarmDetail, alarmFailed, waiting, routines)
        }
    }
}

internal fun isDraftOutcome(outcome: String): Boolean =
    outcome.contains("没有发出") || outcome.contains("没有删除")

internal fun waitingFromState(state: TaskState): Boolean = when (state) {
    is TaskState.WaitingConfirmation, is TaskState.WaitingTakeover, is TaskState.WaitingInput, is TaskState.Held -> true
    else -> false
}

private fun waitingFromRun(run: TaskRun): TodayWaitingRow? {
    val state = run.state.value
    if (!waitingFromState(state)) return null
    val title = when (state) {
        is TaskState.WaitingConfirmation -> ConfirmationCopy.title(state.request)
        is TaskState.WaitingTakeover -> state.reason
        is TaskState.WaitingInput -> state.prompt
        is TaskState.Held -> state.reason.why.ifBlank { state.reason.code }
        else -> tr(R.string.today_waiting)
    }
    return TodayWaitingRow(run.conversationId, title, ChatCopy.plainTitle(run.goal))
}

private fun draftTitle(outcome: String): String =
    outcome.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.take(80) ?: tr(R.string.today_waiting)

private fun routineTitle(plan: TriggerDefinition): String =
    plan.displayName?.ifBlank { null } ?: plan.reminderText?.ifBlank { null } ?: plan.goal.lineSequence().first().trim()

private fun worthSubtitle(plan: TriggerDefinition, clock: String): String {
    if (plan.lastStatus == "unwatched") return tr(R.string.today_unwatched)
    if (com.wangzan101.mika.core.trigger.WorthReading.isThis(plan) && plan.lastStatus == "done") {
        val topic = plan.topics.firstOrNull { it.isNotBlank() }
        val host = plan.sharedClaims.lastOrNull()?.url?.let {
            runCatching { java.net.URI(it).host }.getOrNull()
        }
        val extra = listOfNotNull(topic, host).joinToString(" · ")
        if (extra.isNotEmpty()) return "$clock · $extra"
    }
    return clock
}

private fun eventLine(read: CalendarRead, nowMs: Long, zone: ZoneId): Triple<String, String?, Boolean> = when (read) {
    is CalendarRead.Unauthorized -> Triple(tr(R.string.today_next_event), read.howToGrant, true)
    is CalendarRead.Failed -> Triple(tr(R.string.today_next_event), read.reason, true)
    is CalendarRead.Ok -> {
        val next = nextUpcomingEvent(read.events, nowMs)
        if (next == null) Triple(tr(R.string.today_no_event), null, false)
        else Triple(next.title, formatEventWhen(next, zone), false)
    }
}

private fun alarmLine(read: AlarmRead, zone: ZoneId): Triple<String, String?, Boolean> = when (read) {
    is AlarmRead.Failed -> Triple(tr(R.string.today_next_alarm), read.reason, true)
    AlarmRead.None -> Triple(tr(R.string.today_no_alarm), null, false)
    is AlarmRead.Ok -> Triple(
        read.alarm.label?.ifBlank { null } ?: tr(R.string.today_next_alarm),
        formatClock(read.alarm.triggerMs, zone),
        false,
    )
}

private fun formatEventWhen(event: CalendarEvent, zone: ZoneId): String {
    val start = formatClock(event.startMs, zone)
    return when {
        event.allDay -> listOfNotNull(start, event.location, event.calendarName).joinToString(" · ")
        else -> listOfNotNull(start, event.location, event.calendarName).joinToString(" · ")
    }
}

private fun formatClock(ms: Long, zone: ZoneId): String {
    val t = Instant.ofEpochMilli(ms).atZone(zone)
    val today = LocalDate.now(zone)
    val day = t.toLocalDate()
    val clock = "%d:%02d".format(t.hour, t.minute)
    return when {
        day == today -> clock
        day == today.plusDays(1) -> "${day.monthValue}/${day.dayOfMonth} $clock"
        else -> "${day.monthValue}/${day.dayOfMonth} $clock"
    }
}

private fun statusLabel(status: RoutineStatus): String = tr(
    when (status) {
        RoutineStatus.Upcoming -> R.string.today_status_upcoming
        RoutineStatus.Unchanged -> R.string.today_status_unchanged
        RoutineStatus.Done -> R.string.today_status_done
        RoutineStatus.Failed -> R.string.today_status_failed
        RoutineStatus.QuietPostponed -> R.string.today_status_quiet
        RoutineStatus.Disabled -> R.string.today_status_disabled
        RoutineStatus.Recorded -> R.string.today_status_recorded
        RoutineStatus.Unwatched -> R.string.today_unwatched
    },
)

private fun statusTone(status: RoutineStatus): ValueTone = when (status) {
    RoutineStatus.Failed -> ValueTone.Bad
    RoutineStatus.QuietPostponed, RoutineStatus.Unwatched -> ValueTone.Warn
    RoutineStatus.Done, RoutineStatus.Unchanged, RoutineStatus.Recorded -> ValueTone.Ok
    else -> ValueTone.Plain
}
