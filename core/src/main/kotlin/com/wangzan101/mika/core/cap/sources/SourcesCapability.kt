package com.wangzan101.mika.core.cap.sources

import com.wangzan101.mika.core.capability.Capability
import com.wangzan101.mika.core.capability.CapabilityKey
import com.wangzan101.mika.core.capability.CapabilityStatus
import com.wangzan101.mika.core.capability.PromptEnv
import com.wangzan101.mika.core.prompt.Prompts
import com.wangzan101.mika.core.tool.AgentTool
import com.wangzan101.mika.core.tool.ToolContext

/**
 * 只读设备来源：本机日历（今天和明天）和系统报告的下一个闹钟。
 * 不改任何数据。没接端口时工具不提供。
 */
internal class SourcesCapability(
    val calendar: CalendarSource?,
    val alarms: NextAlarmSource?,
) : Capability {
    override val id = SOURCES

    override fun status(): CapabilityStatus = when {
        calendar != null || alarms != null -> CapabilityStatus.Available
        else -> CapabilityStatus.Unavailable("no device sources")
    }

    override fun tools(): List<AgentTool> = SourcesGroup.tools

    override fun sections(env: PromptEnv): List<String> =
        if (env.isOpen(id) && SourcesGroup.tools.any { it.name in env.offered }) listOf(env.gate(Prompts.capability(SOURCES)))
        else emptyList()

    override fun onTaskStart(ctx: ToolContext) {
        ctx[KEY] = this
    }

    companion object {
        val KEY = CapabilityKey<SourcesCapability>(SOURCES)
    }
}
