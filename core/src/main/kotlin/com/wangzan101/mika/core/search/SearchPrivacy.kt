package com.wangzan101.mika.core.search

/** 「值得看」查询不得带上关注原文里没有的、记忆里的邮箱或电话。 */
object SearchPrivacy {
    private val EMAIL = Regex("[A-Za-z0-9._%+\\-]+@[A-Za-z0-9.\\-]+\\.[A-Za-z]{2,}")
    private val PHONE = Regex("(?:\\+\\d{1,3}[\\- ]?)?(?:1[3-9]\\d{9}|\\d{3,4}[\\- ]?\\d{7,8})")

    fun blocked(query: String, memories: List<String>, allowed: String): String? {
        val allow = allowed.lowercase()
        val q = query.lowercase()
        for (mem in memories) {
            for (hit in EMAIL.findAll(mem) + PHONE.findAll(mem)) {
                val token = hit.value.lowercase()
                if (token.length < 6) continue
                if (token in q && token !in allow) return token
            }
        }
        return null
    }
}
