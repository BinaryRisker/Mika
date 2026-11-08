package com.wangzan101.mika.core.trigger

import java.time.Instant
import java.time.ZoneId

/** 今天这一页上一条常规的状态。 */
enum class RoutineStatus {
    Upcoming, Unchanged, Done, Failed, QuietPostponed, Disabled, Recorded, Unwatched
}

/** 今天这一页上一条常规该显示哪一种状态。关着、免打扰里到期未跑、今天已跑过，三种分开。 */
fun routineStatus(
    trigger: TriggerDefinition,
    nowMs: Long,
    quiet: QuietHours,
    zone: ZoneId,
    lastDueAtMs: Long?,
): RoutineStatus {
    if (!trigger.enabled && trigger.lastStatus?.lowercase() == "unwatched") return RoutineStatus.Unwatched
    if (!trigger.enabled) return RoutineStatus.Disabled
    if (trigger.callsModel && quiet.active(nowMs, zone) && trigger.nextAtMs <= nowMs) return RoutineStatus.QuietPostponed
    val todayStart = Instant.ofEpochMilli(nowMs).atZone(zone).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
    if (lastDueAtMs == null || lastDueAtMs < todayStart) return RoutineStatus.Upcoming
    return when (trigger.lastStatus?.lowercase()) {
        "unchanged" -> RoutineStatus.Unchanged
        "done" -> RoutineStatus.Done
        "failed" -> RoutineStatus.Failed
        "recorded" -> RoutineStatus.Recorded
        "quietpostponed" -> RoutineStatus.QuietPostponed
        "disabled" -> RoutineStatus.Disabled
        else -> RoutineStatus.Upcoming
    }
}
