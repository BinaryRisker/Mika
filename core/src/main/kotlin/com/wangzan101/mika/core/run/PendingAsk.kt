package com.wangzan101.mika.core.run

/**
 * 还没答上的 `ask_user`。人在场时等回答；人不在时先送达再立刻 `no_answer`，这一条仍留着。
 * 进程被杀后从落盘读回来。用户以后回答是同一条对话里一次新运行，不恢复上次的虚拟屏。
 */
data class PendingAsk(
    val askId: String,
    val threadId: String,
    val prompt: String,
    val options: List<String> = emptyList(),
    val atMs: Long = 0L,
)

/** 每个对话至多一条未答问句；后一次覆盖前一次。 */
interface PendingAskStore {
    fun get(threadId: String): PendingAsk?
    fun all(): List<PendingAsk>
    fun put(ask: PendingAsk)
    fun remove(threadId: String): PendingAsk?
}

object NoPendingAsks : PendingAskStore {
    override fun get(threadId: String): PendingAsk? = null
    override fun all(): List<PendingAsk> = emptyList()
    override fun put(ask: PendingAsk) = Unit
    override fun remove(threadId: String): PendingAsk? = null
}

class MemoryPendingAskStore(initial: List<PendingAsk> = emptyList()) : PendingAskStore {
    private val items = LinkedHashMap<String, PendingAsk>()
    init { initial.forEach { items[it.threadId] = it } }

    @Synchronized override fun get(threadId: String): PendingAsk? = items[threadId]
    @Synchronized override fun all(): List<PendingAsk> = items.values.toList()
    @Synchronized override fun put(ask: PendingAsk) { items[ask.threadId] = ask }
    @Synchronized override fun remove(threadId: String): PendingAsk? = items.remove(threadId)
}
