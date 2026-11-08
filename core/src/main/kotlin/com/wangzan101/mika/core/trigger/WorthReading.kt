package com.wangzan101.mika.core.trigger

import java.time.ZoneId

/** 内置「值得看」：安装后关着，工作日本地 12:30，关注空着不能启用。 */
object WorthReading {
    const val ID = "routine:worth-reading"
    const val NAME = "值得看"
    const val TIME = "12:30"
    const val STOP_MINUTES = 15
    const val KEEP_CLAIMS = 200
    val WEEKDAYS: List<Int> = listOf(1, 2, 3, 4, 5)
    val CAPABILITIES: Set<String> = setOf("web")
    val TOOL_NAMES: Set<String> = setOf(
        "web_search", "web_read_page", "memory_search", "skill_search", "skill_read", "end_not_done",
    )
    const val EMPTY_ANSWER = "还没有关注"
    const val GOAL = "先读关注名单，以及这次运行能读到的事实和偏好。用 web_search 和 web_read_page 找到原始出处，写上时间；只有一处来源就标明，同一篇通稿的转载不算第二个来源。查询词只用关注里的题目，加上年份、语言这类限定。每条建议对得上名单上的某一句原文，并给出这次读过的网址和页面上的日期，页面没有日期就写「页面上没有日期」。已经分享过的网址，或已经分享过的同一主张，不再说。没有新的就回答「无变化」并带上今天的日期。最多三条。建议只写可以接着做的一步。"

    fun seed(threadId: String, nowMs: Long, zone: ZoneId = ZoneId.systemDefault()): TriggerDefinition {
        val zoneId = zone.id
        return TriggerDefinition(
            id = ID,
            threadId = threadId,
            goal = GOAL,
            nextAtMs = nextCalendarAt(TIME, zoneId, WEEKDAYS, nowMs),
            enabled = false,
            calendarTime = TIME,
            calendarZone = zoneId,
            calendarDays = WEEKDAYS,
            displayName = NAME,
            notifyOnChange = true,
            stopMinutes = STOP_MINUTES,
            toolNames = TOOL_NAMES.toList(),
            capabilityIds = CAPABILITIES.toList(),
            builtinId = ID,
        )
    }

    fun isThis(t: TriggerDefinition): Boolean = t.builtinId == ID || t.id == ID

    fun canEnable(t: TriggerDefinition): Boolean = t.topics.any { it.isNotBlank() }

    fun remember(shared: List<SharedClaim>, more: List<SharedClaim>): List<SharedClaim> =
        (shared + more).distinctBy { normalizeUrl(it.url) to normalizeClaim(it.claim) }.takeLast(KEEP_CLAIMS)

    fun alreadyShared(url: String, claim: String, shared: List<SharedClaim>): Boolean {
        val u = normalizeUrl(url)
        val c = normalizeClaim(claim)
        if (u.isEmpty() && c.isEmpty()) return false
        return shared.any { (u.isNotEmpty() && normalizeUrl(it.url) == u) || (c.isNotEmpty() && normalizeClaim(it.claim) == c) }
    }

    fun normalizeUrl(url: String): String =
        url.trim().lowercase().removeSuffix("/").substringBefore('#').substringBefore('?')

    fun normalizeClaim(claim: String): String =
        claim.lowercase().replace(Regex("\\s+"), " ").trim().trimEnd('。', '.', '！', '!', '？', '?')
}

/** 「盯这个页面」模板：用户填网址和要等的变化后才成为常规，保存时关着。 */
object WatchPage {
    const val ID = "routine:watch-page"
    const val NAME = "盯这个页面"
    const val STOP_MINUTES = 15
    val TOOL_NAMES: Set<String> = WorthReading.TOOL_NAMES
    val CAPABILITIES: Set<String> = WorthReading.CAPABILITIES

    fun create(
        id: String, threadId: String, url: String, expect: String, nowMs: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): TriggerDefinition {
        val zoneId = zone.id
        val u = url.trim()
        val e = expect.trim()
        return TriggerDefinition(
            id = id,
            threadId = threadId,
            goal = "盯 $u ：$e",
            nextAtMs = nextCalendarAt("09:00", zoneId, WorthReading.WEEKDAYS, nowMs),
            enabled = false,
            calendarTime = "09:00",
            calendarZone = zoneId,
            calendarDays = WorthReading.WEEKDAYS,
            displayName = NAME,
            notifyOnChange = true,
            stopMinutes = STOP_MINUTES,
            toolNames = TOOL_NAMES.toList(),
            capabilityIds = CAPABILITIES.toList(),
            builtinId = ID,
            watchUrl = u,
            watchExpect = e,
        )
    }

    fun isThis(t: TriggerDefinition): Boolean = t.builtinId == ID
}
