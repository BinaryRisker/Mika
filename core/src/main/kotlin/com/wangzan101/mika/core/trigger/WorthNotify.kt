package com.wangzan101.mika.core.trigger

/**
 * 「值得看」和「盯这个页面」的出声与没人看。
 * 关注空着：不搜索、回答还没有关注、常规关回。
 * 通知发出后到下一次运行开始对话没打开，算一次没人看；连续两次暂停。
 */
object WorthNotify {
    const val UNWATCHED = "unwatched"

    fun emptyTopics(t: TriggerDefinition): Boolean = WorthReading.isThis(t) && t.topics.none { it.isNotBlank() }

    /** 下一次运行开始时：上一次通知还没人看，记一次；满两次就暂停，不再开工。 */
    fun onRunStart(t: TriggerDefinition): TriggerDefinition {
        if (!t.pendingUnseen) return t
        val streak = t.unseenStreak + 1
        return if (streak >= 2) t.copy(
            enabled = false, unseenStreak = streak, pendingUnseen = false, lastStatus = UNWATCHED,
        ) else t.copy(unseenStreak = streak, pendingUnseen = false)
    }

    fun pausedUnwatched(t: TriggerDefinition): Boolean =
        !t.enabled && t.lastStatus == UNWATCHED

    fun noteNotified(t: TriggerDefinition): TriggerDefinition = t.copy(pendingUnseen = true)

    fun noteSeen(t: TriggerDefinition): TriggerDefinition =
        t.copy(pendingUnseen = false, unseenStreak = 0)

    fun shouldSpeakWatch(previous: String?, current: String?): Boolean {
        if (current.isNullOrBlank()) return true
        if (previous == null) return false
        return previous != current
    }

    fun newItems(found: List<SharedClaim>, shared: List<SharedClaim>): List<SharedClaim> =
        found.filterNot { WorthReading.alreadyShared(it.url, it.claim, shared) }
}
