package com.wangzan101.mika.core.cap.sources

import com.wangzan101.mika.core.memory.SensitiveFilter

/** 一条通知的标题和正文。不含被敏感过滤丢掉的条目。 */
data class NotificationPost(
    val packageName: String,
    val title: String,
    val text: String,
    val atMs: Long,
    val appLabel: String? = null,
)

sealed interface NotificationRead {
    data class Ok(val items: List<NotificationPost>) : NotificationRead
    data class Unauthorized(val howToGrant: String) : NotificationRead
    data class Failed(val reason: String) : NotificationRead
}

fun interface NotificationSource {
    fun recent(): NotificationRead
}

/**
 * 验证码、密码、卡号一类整条丢掉，结果里不出现，也不写「有一条被丢弃」。
 * 只对用户打开过的包名放行。
 */
object NotificationPrivacy {
    fun visible(title: String, text: String): Boolean =
        SensitiveFilter.hit("$title\n$text") == null

    fun allowed(posts: List<NotificationPost>, allowedPackages: Set<String>): List<NotificationPost> =
        posts.filter { it.packageName in allowedPackages && visible(it.title, it.text) }
}
