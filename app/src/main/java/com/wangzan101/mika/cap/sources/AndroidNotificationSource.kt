package com.wangzan101.mika.cap.sources

import android.content.Context
import com.wangzan101.mika.core.cap.sources.NotificationPrivacy
import com.wangzan101.mika.core.cap.sources.NotificationRead
import com.wangzan101.mika.core.cap.sources.NotificationSource

class AndroidNotificationSource(private val context: Context) : NotificationSource {
    override fun recent(): NotificationRead {
        if (!NotificationAllowStore.listenerEnabled(context) && !NotifInbox.connected) {
            return NotificationRead.Unauthorized(HOW)
        }
        val allowed = NotificationAllowStore(context).allowed()
        return NotificationRead.Ok(NotificationPrivacy.allowed(NotifInbox.recent(), allowed))
    }

    companion object {
        const val HOW = "打开系统设置 › 通知使用权，打开 Mika。再在 Mika 设置里按 App 打开。"
    }
}
