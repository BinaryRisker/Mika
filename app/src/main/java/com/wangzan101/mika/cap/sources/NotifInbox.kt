package com.wangzan101.mika.cap.sources

import com.wangzan101.mika.core.cap.sources.NotificationPost

/** 通知使用权连上之后，最近若干条只在内存里。 */
object NotifInbox {
    private const val KEEP = 80
    private val lock = Any()
    private val posts = ArrayDeque<NotificationPost>()
    @Volatile var connected: Boolean = false

    fun add(post: NotificationPost) = synchronized(lock) {
        posts.addLast(post)
        while (posts.size > KEEP) posts.removeFirst()
    }

    fun recent(): List<NotificationPost> = synchronized(lock) { posts.toList() }
}
