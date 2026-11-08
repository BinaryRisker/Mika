package com.wangzan101.mika.cap.sources

import android.app.AlarmManager
import android.content.Context
import com.wangzan101.mika.core.cap.sources.AlarmRead
import com.wangzan101.mika.core.cap.sources.NextAlarm
import com.wangzan101.mika.core.cap.sources.NextAlarmSource

/** 系统报告的下一个闹钟。没有则明确返回没有。系统接口不提供标签时 label 为空。 */
class AndroidAlarmSource(private val context: Context) : NextAlarmSource {
    override fun next(): AlarmRead = runCatching {
        val info = context.getSystemService(AlarmManager::class.java).nextAlarmClock
            ?: return AlarmRead.None
        AlarmRead.Ok(NextAlarm(info.triggerTime, label = null))
    }.getOrElse { AlarmRead.Failed(it.message?.take(160) ?: it::class.java.simpleName) }
}
