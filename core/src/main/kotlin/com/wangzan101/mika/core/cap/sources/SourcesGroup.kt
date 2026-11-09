package com.wangzan101.mika.core.cap.sources

import com.wangzan101.mika.core.tool.AgentTool

/** 只读设备来源一组：今天和明天的日程、系统报告的下一个闹钟。 */
internal object SourcesGroup {
    val tools: List<AgentTool> = listOf(ReadCalendar, ReadNextAlarm, ReadNotifications)
}

internal interface SourcesTool : AgentTool {
    override val capability: String get() = SOURCES
    override val offScreen: Boolean get() = true
}
