package com.wangzan101.mika.cap.sources

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.wangzan101.mika.core.cap.sources.NotificationPost

/** 只收标题和正文。包名是否放行在读取时再筛。 */
class NotifListener : NotificationListenerService() {
    override fun onListenerConnected() {
        NotifInbox.connected = true
    }

    override fun onListenerDisconnected() {
        NotifInbox.connected = false
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val n = sbn.notification ?: return
        val extras = n.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
        }.getOrDefault(sbn.packageName)
        NotificationAllowStore(this).noteSeen(sbn.packageName, label)
        NotifInbox.add(NotificationPost(sbn.packageName, title, text, sbn.postTime, label))
    }
}
