package com.wangzan101.mika.task

import com.wangzan101.mika.i18n.tr
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.wangzan101.mika.MikaApp
import com.wangzan101.mika.R
import com.wangzan101.mika.chat.ConversationStore
import com.wangzan101.mika.core.agent.Turn
import com.wangzan101.mika.ui.MainActivity
import com.wangzan101.mika.core.agent.TaskState
import com.wangzan101.mika.cap.sources.AndroidAlarmSource
import com.wangzan101.mika.cap.sources.AndroidCalendarSource
import com.wangzan101.mika.core.cap.sources.MorningNotify
import com.wangzan101.mika.core.cap.sources.MorningSnapshot
import com.wangzan101.mika.core.trigger.FailureFold
import com.wangzan101.mika.core.trigger.MorningRoutine
import com.wangzan101.mika.core.trigger.WatchPage
import com.wangzan101.mika.core.trigger.WorthNotify
import com.wangzan101.mika.core.trigger.WorthReading
import com.wangzan101.mika.core.trigger.PlanResult
import com.wangzan101.mika.core.trigger.RoutineStatus
import com.wangzan101.mika.core.trigger.Scheduled
import com.wangzan101.mika.core.trigger.TriggerDefinition
import com.wangzan101.mika.core.trigger.TriggerEngine
import com.wangzan101.mika.core.trigger.ConditionOutcome
import com.wangzan101.mika.core.trigger.TriggerSnapshot
import com.wangzan101.mika.core.trigger.RoutineKind
import com.wangzan101.mika.core.trigger.ShareMatch
import com.wangzan101.mika.core.trigger.ShareType
import com.wangzan101.mika.core.trigger.TriggerWindowState
import com.wangzan101.mika.core.trigger.rescheduledTo
import java.util.UUID
import com.wangzan101.mika.core.trigger.afterCheck
import com.wangzan101.mika.core.trigger.conditionCheckGoal
import com.wangzan101.mika.core.trigger.revertFire
import com.wangzan101.mika.settings.FeatureFlags
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** 安卓闹钟的薄壳；时间与去重由持久化 TriggerStore 决定，闹钟广播可以重复来。 */
object TriggerRuntime {
    private var recovered = false
    /** 存下并布置闹钟;系统没准许准时闹钟(到点可能不准时或起不来)时引导用户去开一次,结果里照实说有没有提示到。 */
    @Synchronized fun schedule(context: Context, definition: TriggerDefinition): Scheduled {
        val store = store(context)
        store.upsert(definition)
        engine(context, store).rearm(System.currentTimeMillis())
        val granted = canScheduleExactly(context)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // 只有通知真的发出去了才算提示过;没发出去下次再试
        val notified = ExactAlarmGuide.shouldPrompt(granted, prefs.getBoolean(PROMPTED, false)) && promptExactAlarm(context)
        if (notified) prefs.edit().putBoolean(PROMPTED, true).apply()
        return Scheduled(granted, notified)
    }

    /** 系统的「闹钟和提醒」授权页(安卓 12 起)。 */
    fun exactAlarmSettings(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** 第一次没有权限就建了定时任务:发一条通知,点开就是授权页(任务在后台跑,不直接跳页面)。返回通知有没有发出去。 */
    private fun promptExactAlarm(context: Context): Boolean {
        val notifications = context.getSystemService(NotificationManager::class.java)
        if (!notifications.areNotificationsEnabled()) return false
        TaskService.ensureChannels(context)
        val open = PendingIntent.getActivity(context, PROMPTED.hashCode(), exactAlarmSettings(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return runCatching { notifications.notify(PROMPTED.hashCode(), Notification.Builder(context, TaskService.CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification).setContentTitle(ExactAlarmGuide.TITLE)
            .setContentText(ExactAlarmGuide.BODY).setStyle(Notification.BigTextStyle().bigText(ExactAlarmGuide.BODY))
            .setContentIntent(open).setAutoCancel(true).build()) }
            .onFailure { Log.w("MikaTrigger", "exact alarm prompt failed", it) }.isSuccess
    }

    @Synchronized fun rearm(context: Context) {
        seedMorning(context)
        seedWorthReading(context)
        com.wangzan101.mika.pack.PackStore.seedExample(context)
        if (FeatureFlags.get(context).quitRequested()) arm(context, null)
        else engine(context, store(context)).rearm(System.currentTimeMillis())
    }

    /** 完全退出时只撤系统闹钟,不改计划与待处理账本;下次打开 App 由 [rearm] 接着排。 */
    @Synchronized fun suspendAlarms(context: Context) = arm(context, null)

    @Synchronized fun fire(context: Context) {
        // 已发出的广播可能晚于退出到达:不要认领时段,也不要发提醒,重新打开后仍可按原账本恢复
        if (FeatureFlags.get(context).quitRequested()) {
            arm(context, null)
            return
        }
        engine(context, store(context)).fire(System.currentTimeMillis())
    }

    /** 设备时区变化后,跟随旧设备时区的当地时间计划重算到新时区;显式钉了其他时区的不动。没有基线(首次使用)就只记下当前时区。 */
    @Synchronized fun syncTimeZone(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val current = ZoneId.systemDefault().id
        val previous = prefs.getString(KEY_DEVICE_ZONE, null)
        prefs.edit().putString(KEY_DEVICE_ZONE, current).apply()
        if (previous == null || previous == current) return
        engine(context, store(context)).rezone(System.currentTimeMillis(), previous, ZoneId.of(current))
    }

    @Synchronized fun delivered(context: Context, key: String) {
        val store = store(context)
        store.mark(key, TriggerWindowState.Delivered)
        store.retireDeliveredOneShots()
        engine(context, store).rearm(System.currentTimeMillis())
    }

    @Synchronized fun actionFailed(context: Context, key: String, reason: String) {
        val store = store(context)
        if (store.windows().none { it.key == key }) return
        store.mark(key, TriggerWindowState.Failed, reason.take(160))
        engine(context, store).rearm(System.currentTimeMillis())
    }

    @Synchronized fun interrupted(context: Context, key: String, reason: String) {
        val store = store(context)
        store.mark(key, TriggerWindowState.Unknown, reason)
        store.windows().firstOrNull { it.key == key }?.let { window ->
            store.get(window.triggerId)?.let { trigger ->
                if (!hasSavedResult(context, key)) runCatching { reportUncertain(context, trigger, key) }
                    .onFailure { Log.w("MikaTrigger", "could not report interrupted task", it) }
            }
        }
        engine(context, store).rearm(System.currentTimeMillis())
    }

    @Synchronized fun checked(context: Context, key: String, goal: String, threadId: String, outcome: ConditionOutcome) {
        val store = store(context)
        val window = store.windows().firstOrNull { it.key == key && it.state == TriggerWindowState.Dispatched } ?: return
        if (outcome is ConditionOutcome.Error) {
            store.mark(window.key, TriggerWindowState.Failed, outcome.reason)
            return
        }
        outcome as ConditionOutcome.Checked
        val trigger = store.get(window.triggerId) ?: return
        val decision = trigger.afterCheck(outcome, System.currentTimeMillis(), slotMs = window.dueAtMs)
        store.upsert(decision.next)
        if (!decision.fire) {
            store.mark(window.key, TriggerWindowState.Skipped, decision.reason)
            return
        }
        store.mark(window.key, TriggerWindowState.ActionDispatched)
        try { TaskService.startTrigger(context, goal, threadId, key, null, null) }
        catch (e: Exception) { reportStartFailure(context, store, trigger, window.key, e, before = trigger) }
    }

    @Synchronized fun list(context: Context): List<TriggerDefinition> = store(context).all()

    /** 启用中的分享常规，按这次分享的类型匹配。 */
    @Synchronized fun matchingShare(context: Context, type: ShareType): List<TriggerDefinition> =
        ShareMatch.matching(store(context).all(), type)

    /** 启用中的手动常规，作为空对话和新对话输入框上方的常用说法。 */
    @Synchronized fun commonPhrases(context: Context): List<TriggerDefinition> =
        ShareMatch.phrases(store(context).all())

    @Synchronized fun addManual(context: Context, name: String, goal: String, enabled: Boolean = true): TriggerDefinition {
        val def = TriggerDefinition(
            id = "routine:manual:${UUID.randomUUID()}",
            threadId = newRoutineThread(context, name),
            goal = goal.trim(),
            nextAtMs = 0,
            enabled = enabled,
            displayName = name.trim(),
            kind = RoutineKind.Manual,
        )
        upsertRoutine(context, def)
        return def
    }

    @Synchronized fun addWatch(context: Context, url: String, expect: String): TriggerDefinition {
        val u = url.trim()
        val e = expect.trim()
        require(u.startsWith("http://") || u.startsWith("https://"))
        require(e.isNotEmpty())
        val def = WatchPage.create(
            id = "routine:watch:${UUID.randomUUID()}",
            threadId = newRoutineThread(context, WatchPage.NAME),
            url = u, expect = e, nowMs = System.currentTimeMillis(),
        )
        upsertRoutine(context, def)
        return def
    }

    @Synchronized fun setTopics(context: Context, id: String, topics: List<String>): Boolean {
        val store = store(context)
        val t = store.get(id) ?: return false
        val next = topics.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val enabled = t.enabled && next.isNotEmpty()
        store.upsert(t.copy(topics = next, enabled = enabled))
        engine(context, store).rearm(System.currentTimeMillis())
        return true
    }

    @Synchronized fun addShare(
        context: Context, name: String, goal: String, types: List<ShareType>, enabled: Boolean = true,
    ): TriggerDefinition {
        require(types.isNotEmpty())
        val def = TriggerDefinition(
            id = "routine:share:${UUID.randomUUID()}",
            threadId = newRoutineThread(context, name),
            goal = goal.trim(),
            nextAtMs = 0,
            enabled = enabled,
            displayName = name.trim(),
            kind = RoutineKind.Share,
            shareTypes = types.distinct(),
        )
        upsertRoutine(context, def)
        return def
    }

    private fun newRoutineThread(context: Context, name: String): String {
        val conversations = ConversationStore.shared(context)
        val threadId = conversations.newConversation()
        conversations.setTitle(threadId, name.trim())
        return threadId
    }

    private fun upsertRoutine(context: Context, definition: TriggerDefinition) {
        val store = store(context)
        store.upsert(definition)
        engine(context, store).rearm(System.currentTimeMillis())
    }

    fun canScheduleExactly(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()

    @Synchronized fun snapshot(context: Context): TriggerSnapshot = store(context).let {
        TriggerSnapshot(it.all(), it.windows())
    }

    /** 暂停或恢复;改不了(没有这个计划、恢复跑过的一次性计划)时返回 false,见 [TriggerEngine.setEnabled]。 */
    @Synchronized fun setEnabled(context: Context, id: String, enabled: Boolean): Boolean =
        store(context).let { engine(context, it).setEnabled(id, enabled, System.currentTimeMillis()) }

    @Synchronized fun remove(context: Context, id: String) {
        val store = store(context)
        store.remove(id)
        engine(context, store).rearm(System.currentTimeMillis())
    }

    @Synchronized fun reschedule(context: Context, id: String, nextAtMs: Long) {
        val store = store(context)
        store.get(id)?.let { trigger ->
            store.upsert(trigger.rescheduledTo(nextAtMs))
        }
        engine(context, store).rearm(System.currentTimeMillis())
    }

    @Synchronized fun runNow(context: Context, id: String) {
        val store = store(context)
        val trigger = store.get(id) ?: error(tr(R.string.trigger_deleted))
        val window = store.claimNow(id, System.currentTimeMillis()) ?: error(tr(R.string.trigger_busy))
        try {
            if (missingPack(context, trigger, store, window.key)) return
            if (skipWorth(context, store, trigger, window.key)) return
            if (trigger.reminderText != null) deliverReminder(context, store, trigger, window.key)
            else TaskService.startTrigger(context, trigger.goal, trigger.threadId, window.key,
                trigger.conditionCheckGoal(ZoneId.systemDefault()), trigger.conditionGoal)
            store.mark(window.key, TriggerWindowState.Dispatched)
        } catch (e: Exception) {
            reportStartFailure(context, store, trigger, window.key, e)
            throw e
        } finally {
            engine(context, store).rearm(System.currentTimeMillis())
        }
    }

    private fun store(context: Context): FileTriggerStore {
        val store = FileTriggerStore(File(context.filesDir, "triggers-v3.json"))
        if (!recovered) {
            store.windows().filter { it.state in setOf(TriggerWindowState.Claimed,
                TriggerWindowState.Dispatched, TriggerWindowState.ActionDispatched) }.forEach {
                val trigger = store.get(it.triggerId)
                if (trigger?.reminderText != null) {
                    runCatching { deliverReminder(context, store, trigger, it.key) }
                        .onFailure { error -> store.mark(it.key, TriggerWindowState.Unknown, error::class.simpleName) }
                } else {
                    store.mark(it.key, TriggerWindowState.Unknown, "process restarted before result was confirmed")
                    if (trigger != null && !hasSavedResult(context, it.key))
                        runCatching { reportUncertain(context, trigger, it.key) }
                            .onFailure { error -> Log.w("MikaTrigger", "could not report recovered task", error) }
                }
            }
            recovered = true
        }
        return store
    }

    /**
     * 这一次运行要不要发结果通知。会记下连续失败：第一次失败通知，之后折叠直到成功。
     * 早晨安排另按设备快照比变化：第一次只记下，无变化不通知。
     * 不是定时任务的运行（没有窗口）仍通知。
     */
    @Synchronized fun shouldNotify(context: Context, windowKey: String?, end: TaskState): Boolean {
        if (windowKey.isNullOrEmpty()) return true
        val store = store(context)
        val id = store.windows().firstOrNull { it.key == windowKey }?.triggerId ?: return true
        val trigger = store.get(id) ?: return true
        val result = PlanResult.of(end)
        val fold = FailureFold.note(trigger.consecutiveFailures, result)
        if (WorthReading.isThis(trigger) || WatchPage.isThis(trigger)) {
            if (result == PlanResult.Cancel) {
                store.upsert(trigger.copy(consecutiveFailures = fold.consecutiveFailures))
                return true
            }
            val current = (end as? TaskState.Succeeded)?.answer.orEmpty()
            val previous = trigger.snapshot
            val speak = when {
                result == PlanResult.Failure -> true
                WorthNotify.emptyTopics(trigger) -> false
                WatchPage.isThis(trigger) -> WorthNotify.shouldSpeakWatch(previous, current)
                previous == null -> false
                current.contains("无变化") -> false
                else -> previous != current
            }
            val status = when {
                result == PlanResult.Failure -> RoutineStatus.Failed
                previous == null && !speak -> RoutineStatus.Recorded
                !speak -> RoutineStatus.Unchanged
                else -> RoutineStatus.Done
            }
            var next = trigger.copy(
                consecutiveFailures = fold.consecutiveFailures,
                snapshot = current.takeIf { it.isNotBlank() } ?: trigger.snapshot,
                lastStatus = status.name.lowercase(),
            )
            val notify = when (result) {
                PlanResult.Failure -> fold.notify
                PlanResult.Cancel -> true
                PlanResult.Success -> if (trigger.notifyOnChange) speak else true
            }
            if (notify) next = WorthNotify.noteNotified(next)
            store.upsert(next)
            return notify
        }
        if (isMorning(trigger)) {
            if (result == PlanResult.Cancel) {
                store.upsert(trigger.copy(consecutiveFailures = fold.consecutiveFailures))
                return true
            }
            val current = MorningSnapshot.of(
                AndroidCalendarSource(context).todayAndTomorrow(),
                AndroidAlarmSource(context).next(),
            )
            val previous = MorningSnapshot.decode(trigger.snapshot)
            val speak = MorningNotify.shouldSpeak(previous, current)
            val status = when {
                result == PlanResult.Failure || current.readFailed -> RoutineStatus.Failed
                previous == null && !speak -> RoutineStatus.Recorded
                !speak -> RoutineStatus.Unchanged
                else -> RoutineStatus.Done
            }
            store.upsert(trigger.copy(
                consecutiveFailures = fold.consecutiveFailures,
                snapshot = current.encode(),
                lastStatus = status.name.lowercase(),
            ))
            return when (result) {
                PlanResult.Failure -> fold.notify
                PlanResult.Cancel -> true
                PlanResult.Success -> if (trigger.notifyOnChange) speak else true
            }
        }
        if (fold.consecutiveFailures != trigger.consecutiveFailures) {
            store.upsert(trigger.copy(consecutiveFailures = fold.consecutiveFailures))
        }
        return fold.notify
    }

    /** 这条窗口所属计划；没有窗口或计划时为 null。 */
    @Synchronized fun routineForWindow(context: Context, windowKey: String?): TriggerDefinition? {
        if (windowKey.isNullOrEmpty()) return null
        val store = store(context)
        val id = store.windows().firstOrNull { it.key == windowKey }?.triggerId ?: return null
        return store.get(id)
    }

    /** 这条窗口所属计划的停止分钟数；没有或不另限时为 null。 */
    @Synchronized fun stopMinutesForWindow(context: Context, windowKey: String?): Int? =
        routineForWindow(context, windowKey)?.stopMinutes

    /** 这条窗口所属计划的常规 token 上限；没有计划或未设时为 0。 */
    @Synchronized fun tokenCapForWindow(context: Context, windowKey: String?): Long {
        if (windowKey.isNullOrEmpty()) return 0
        val store = store(context)
        val id = store.windows().firstOrNull { it.key == windowKey }?.triggerId ?: return 0
        return store.get(id)?.tokenCap ?: 0
    }

    /**
     * 装过一次就记下，用户删掉内置早晨安排也不再创建。
     * 第一次见到已有这条计划（升级前手动建过）也只记种过，不覆盖。
     */
    @Synchronized fun seedMorning(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(SEEDED_MORNING, false)) return
        val store = store(context)
        if (store.get(MorningRoutine.ID) != null) {
            prefs.edit().putBoolean(SEEDED_MORNING, true).apply()
            return
        }
        val conversations = ConversationStore.shared(context)
        val threadId = conversations.newConversation()
        conversations.setTitle(threadId, MorningRoutine.NAME)
        store.upsert(MorningRoutine.seed(threadId, System.currentTimeMillis()))
        prefs.edit().putBoolean(SEEDED_MORNING, true).apply()
    }

    @Synchronized fun seedWorthReading(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getBoolean(SEEDED_WORTH, false)) return
        val store = store(context)
        if (store.get(WorthReading.ID) != null) {
            prefs.edit().putBoolean(SEEDED_WORTH, true).apply()
            return
        }
        val conversations = ConversationStore.shared(context)
        val threadId = conversations.newConversation()
        conversations.setTitle(threadId, WorthReading.NAME)
        store.upsert(WorthReading.seed(threadId, System.currentTimeMillis()))
        prefs.edit().putBoolean(SEEDED_WORTH, true).apply()
    }

    @Synchronized fun noteConversationOpened(context: Context, conversationId: String) {
        val store = store(context)
        store.all().filter { it.threadId == conversationId && it.pendingUnseen }.forEach {
            store.upsert(WorthNotify.noteSeen(it))
        }
    }

    @Synchronized fun rejectSuggestion(context: Context, text: String, conversationId: String) {
        val mem = com.wangzan101.mika.memory.MemoryRuntime.of(context).store()
        mem.add(
            com.wangzan101.mika.core.memory.MemoryKind.preference,
            text,
            emptyList(),
            com.wangzan101.mika.core.memory.MemorySource("click", text, conversationId, System.currentTimeMillis()),
        )
        noteConversationOpened(context, conversationId)
    }

    private fun isMorning(trigger: TriggerDefinition): Boolean =
        trigger.builtinId == MorningRoutine.ID || trigger.id == MorningRoutine.ID

    private fun engine(context: Context, store: FileTriggerStore) = TriggerEngine(store,
        { at -> arm(context, at) },
        isIdle = { !((context.applicationContext as MikaApp).taskState.busy.value) },
        quietHours = { AssistantLimits.quietHours(context) },
        zone = { ZoneId.systemDefault() },
    ) { trigger, window ->
            if (missingPack(context, trigger, store, window.key)) { }
            else if (skipWorth(context, store, trigger, window.key)) { }
            else if (trigger.reminderText != null) deliverReminder(context, store, trigger, window.key)
            else try { TaskService.startTrigger(context, trigger.goal, trigger.threadId, window.key,
                    trigger.conditionCheckGoal(ZoneId.systemDefault()), trigger.conditionGoal) }
                catch (e: Exception) { reportStartFailure(context, store, trigger, window.key, e); throw e }
        }

    private fun skipWorth(context: Context, store: FileTriggerStore, trigger: TriggerDefinition, key: String): Boolean {
        var t = trigger
        if (t.pendingUnseen) {
            t = WorthNotify.onRunStart(t)
            store.upsert(t)
            if (WorthNotify.pausedUnwatched(t)) {
                store.mark(key, TriggerWindowState.Skipped, WorthNotify.UNWATCHED)
                return true
            }
        }
        if (WorthNotify.emptyTopics(t)) {
            val conversations = ConversationStore.shared(context)
            val runId = "worth-empty:$key"
            if (conversations.turns(t.threadId).none { it.runId == runId }) {
                conversations.append(t.threadId, Turn(t.goal, WorthReading.EMPTY_ANSWER, "succeeded", runId = runId))
            }
            store.upsert(t.copy(enabled = false, lastStatus = "disabled"))
            store.mark(key, TriggerWindowState.Delivered)
            return true
        }
        if ((WorthReading.isThis(t) || WatchPage.isThis(t)) &&
            com.wangzan101.mika.settings.WebSearchSettings.get(context).service() == null) {
            val message = tr(R.string.today_status_failed)
            val conversations = ConversationStore.shared(context)
            val runId = "worth-nosearch:$key"
            if (conversations.turns(t.threadId).none { it.runId == runId }) {
                conversations.append(t.threadId, Turn(t.goal, message, "failed", runId = runId))
            }
            store.upsert(t.copy(lastStatus = "failed"))
            store.mark(key, TriggerWindowState.Failed, "no_web_search")
            return true
        }
        return false
    }

    private fun missingPack(context: Context, trigger: TriggerDefinition, store: FileTriggerStore, key: String): Boolean {
        if (!com.wangzan101.mika.core.pack.PackLifecycle.missingScript(trigger.packId, com.wangzan101.mika.pack.PackStore.installedIds(context))) return false
        store.mark(key, TriggerWindowState.Failed, "pack_script_missing")
        return true
    }

    private fun deliverReminder(context: Context, store: FileTriggerStore, trigger: TriggerDefinition, key: String) {
        val text = trigger.reminderText ?: return
        val conversations = ConversationStore.shared(context)
        val runId = "reminder:$key"
        if (conversations.turns(trigger.threadId).none { it.runId == runId }) {
            conversations.append(trigger.threadId, Turn(trigger.goal, text, "succeeded", runId = runId))
        }
        notifyLocal(context, trigger.threadId, key, tr(R.string.trigger_reminder), text)
        store.mark(key, TriggerWindowState.Delivered)
        store.retireDeliveredOneShots()
    }

    private fun reportStartFailure(context: Context, store: FileTriggerStore, trigger: TriggerDefinition,
        key: String, error: Exception, before: TriggerDefinition? = null) {
        val message = tr(R.string.trigger_not_started_body)
        Log.w("MikaTrigger", "scheduled task could not start", error)
        store.mark(key, TriggerWindowState.Failed, tr(R.string.trigger_start_failed))
        if (before != null) store.get(trigger.id)?.let { store.upsert(it.revertFire(before)) }
        val conversations = ConversationStore.shared(context)
        val runId = "schedule-failed:$key"
        if (conversations.turns(trigger.threadId).none { it.runId == runId }) {
            conversations.append(trigger.threadId, Turn(trigger.goal, message, "failed", runId = runId))
        }
        notifyLocal(context, trigger.threadId, key, tr(R.string.trigger_not_started), message)
    }

    private fun hasSavedResult(context: Context, key: String): Boolean =
        FileRunStateStore(File(context.filesDir, "runs-v3.json")).all().any {
            it.triggerWindowKey == key && it.reply != null
        }

    private fun reportUncertain(context: Context, trigger: TriggerDefinition, key: String) {
        val message = tr(R.string.trigger_interrupted_body)
        val conversations = ConversationStore.shared(context)
        val runId = "schedule-uncertain:$key"
        if (conversations.turns(trigger.threadId).none { it.runId == runId }) {
            conversations.append(trigger.threadId, Turn(trigger.goal, message, "unverified", runId = runId))
        }
        notifyLocal(context, trigger.threadId, key, tr(R.string.trigger_unconfirmed), message)
    }

    private fun notifyLocal(context: Context, threadId: String, key: String, title: String, text: String) {
        val notifications = context.getSystemService(NotificationManager::class.java)
        TaskService.ensureChannels(context)
        val open = PendingIntent.getActivity(context, key.hashCode(),
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(MainActivity.EXTRA_OPEN_CONVERSATION, threadId),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        if (notifications.areNotificationsEnabled()) {
            runCatching { notifications.notify(key.hashCode(), Notification.Builder(context, TaskService.CHANNEL_RESULT)
                .setSmallIcon(R.drawable.ic_notification).setContentTitle(title)
                .setContentText(text).setStyle(Notification.BigTextStyle().bigText(text))
                .setContentIntent(open).setAutoCancel(true).build()) }
                .onFailure { Log.w("MikaTrigger", "reminder notification failed", it) }
        }
    }

    private fun arm(context: Context, atMs: Long?) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        val intent = Intent(context, TriggerAlarmReceiver::class.java).setAction(ACTION)
        val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        alarms.cancel(pending)
        val at = atMs?.takeUnless { FeatureFlags.get(context).quitRequested() }
        if (at != null) {
            if (canScheduleExactly(context)) alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
            else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending)
        }
    }

    private const val ACTION = "com.wangzan101.mika.task.TRIGGER_ALARM"
    private const val PREFS = "triggers"
    private const val PROMPTED = "exact_alarm_prompted"
    private const val KEY_DEVICE_ZONE = "device_timezone"
    private const val SEEDED_MORNING = "seeded_morning"
    private const val SEEDED_WORTH = "seeded_worth_reading"
}

/** 准时闹钟权限的引导:没开时只主动提醒一次,之后在定时任务页一直写明。 */
object ExactAlarmGuide {
    val TITLE: String get() = tr(R.string.trigger_alarm_title)
    val BODY: String get() = tr(R.string.trigger_alarm_body)

    fun shouldPrompt(granted: Boolean, prompted: Boolean): Boolean = !granted && !prompted
}

class TriggerAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != "com.wangzan101.mika.task.TRIGGER_ALARM") return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try { TriggerRuntime.fire(context.applicationContext) }
            catch (e: Exception) { Log.e("MikaTrigger", "alarm dispatch failed", e) }
            finally { pending.finish() }
        }
    }
}
