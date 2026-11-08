package com.wangzan101.mika.core.usage

import com.wangzan101.mika.core.model.ChatModel
import com.wangzan101.mika.core.model.Message
import com.wangzan101.mika.core.model.ModelReply
import com.wangzan101.mika.core.model.StreamingChatModel
import com.wangzan101.mika.core.model.ToolSpec
import kotlinx.coroutines.currentCoroutineContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * 到达花费上限时在发出下一次模型调用之前抛出。不是 [com.wangzan101.mika.core.model.ModelError]，
 * 重试策略不会把它当成可重试的服务端故障。
 */
class SpendStopped(message: String) : RuntimeException(message)

/** 这一次运行自己的 token 上限；0 或不放进协程上下文表示这条常规不另限。 */
class RoutineTokenCap(val tokens: Long) : AbstractCoroutineContextElement(Key) {
    companion object Key : CoroutineContext.Key<RoutineTokenCap>
}

/**
 * 本地日历日总上限。[dailyCap] / 账本已用都是输入加输出的 token；0 表示不限制，行为与现在相同。
 * 每条常规的上限读协程里的 [RoutineTokenCap] 与 [RunUsageTally]，只停下这一次运行。
 */
data class SpendBudget(
    val dailyCap: () -> Long = { 0 },
    val dailyUsed: () -> Long = { 0 },
)

/** 在下一次 [ChatModel.complete] 之前核对上限；套在计量外面时，未发出的调用不会入账。 */
fun ChatModel.capped(budget: SpendBudget): ChatModel =
    if (this is StreamingChatModel) CappedStreamingModel(this, budget) else CappedModel(this, budget)

private open class CappedModel(private val inner: ChatModel, protected val budget: SpendBudget) : ChatModel {
    override val capabilities get() = inner.capabilities
    override val name: String get() = inner.name

    override suspend fun complete(system: String, messages: List<Message>, tools: List<ToolSpec>): ModelReply {
        checkBudget()
        return inner.complete(system, messages, tools)
    }

    protected suspend fun checkBudget() {
        val daily = budget.dailyCap()
        if (daily > 0 && budget.dailyUsed() >= daily) {
            throw SpendStopped("已到达当天花费上限")
        }
        val runCap = currentCoroutineContext()[RoutineTokenCap]?.tokens ?: 0L
        if (runCap > 0) {
            val used = currentCoroutineContext()[RunUsageTally]?.tokens() ?: 0L
            if (used >= runCap) throw SpendStopped("已到达本条常规花费上限")
        }
    }
}

private class CappedStreamingModel(private val inner: StreamingChatModel, budget: SpendBudget) :
    CappedModel(inner, budget), StreamingChatModel {
    override suspend fun completeStreaming(
        system: String, messages: List<Message>, tools: List<ToolSpec>, onText: (String) -> Unit,
    ): ModelReply {
        checkBudget()
        return inner.completeStreaming(system, messages, tools, onText)
    }
}
