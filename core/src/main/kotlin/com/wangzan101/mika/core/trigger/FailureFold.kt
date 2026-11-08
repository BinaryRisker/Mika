package com.wangzan101.mika.core.trigger

import com.wangzan101.mika.core.agent.TaskState

/** 一条计划这一次运行的结局：失败才计入连续失败；取消不算失败。 */
enum class PlanResult {
    Success, Failure, Cancel;

    companion object {
        fun of(end: TaskState): PlanResult = when (end) {
            is TaskState.Succeeded -> Success
            TaskState.Cancelled -> Cancel
            is TaskState.Failed -> Failure
            else -> Failure
        }
    }
}

/** [consecutiveFailures] 是记下之后的连续失败次数；[notify] 说这一次要不要发结果通知。 */
data class FoldDecision(val consecutiveFailures: Int, val notify: Boolean)

/**
 * 同一计划连续失败时，第一次通知，之后折叠，直到一次成功。
 * 成功仍通知（和今天一样）并清零；用户取消不算失败，计数不动，通知照发。
 */
object FailureFold {
    fun note(previous: Int, result: PlanResult): FoldDecision {
        val prior = previous.coerceAtLeast(0)
        return when (result) {
            PlanResult.Success -> FoldDecision(0, notify = true)
            PlanResult.Cancel -> FoldDecision(prior, notify = true)
            PlanResult.Failure -> {
                val count = prior + 1
                FoldDecision(count, notify = count == 1)
            }
        }
    }
}
