package com.wangzan101.mika.task

import com.wangzan101.mika.R
import com.wangzan101.mika.core.run.PendingAsk
import com.wangzan101.mika.core.run.PendingAskStore
import com.wangzan101.mika.i18n.tr
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.io.File
import java.io.FileOutputStream

/** 未答 `ask_user` 的落盘方式,测试里换成内存实现。 */
interface FilePendingAskBacking {
    fun load(): List<PendingAsk>
    fun save(items: List<PendingAsk>)
}

/**
 * App 私有目录里每个对话至多一条未答问句。读不出来时把原文件挪到 `.corrupt-时间戳` 留着,当作空账本。
 */
class FilePendingAskStore(private val file: File) : FilePendingAskBacking {
    @Synchronized override fun load(): List<PendingAsk> {
        if (!file.isFile) return emptyList()
        return runCatching {
            Json.parseToJsonElement(file.readText()).jsonArray.mapNotNull { element ->
                val o = element as? JsonObject ?: return@mapNotNull null
                val askId = o["askId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val threadId = o["threadId"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val prompt = o["prompt"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                PendingAsk(
                    askId = askId,
                    threadId = threadId,
                    prompt = prompt,
                    options = o["options"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }.orEmpty(),
                    atMs = o["atMs"]?.jsonPrimitive?.longOrNull ?: 0L,
                )
            }.distinctBy { it.threadId }
        }.getOrElse {
            var backup = File(file.parentFile, file.name + ".corrupt-" + System.currentTimeMillis())
            var n = 1
            while (backup.exists()) backup = File(file.parentFile, file.name + ".corrupt-" + System.currentTimeMillis() + "-" + n++)
            runCatching { file.renameTo(backup) }
            emptyList()
        }
    }

    @Synchronized override fun save(items: List<PendingAsk>) {
        file.parentFile?.mkdirs()
        val json = JsonArray(items.map { ask ->
            buildJsonObject {
                put("askId", ask.askId)
                put("threadId", ask.threadId)
                put("prompt", ask.prompt)
                put("options", JsonArray(ask.options.map { JsonPrimitive(it) }))
                put("atMs", ask.atMs)
            }
        }).toString()
        val temp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temp).use { out -> out.write(json.toByteArray(Charsets.UTF_8)); out.fd.sync() }
        if (!temp.renameTo(file)) {
            temp.delete()
            error("pending-ask rename failed")
        }
    }
}

class MemoryPendingAskBacking(initial: List<PendingAsk> = emptyList()) : FilePendingAskBacking {
    private var items = initial.toList()
    @Synchronized override fun load(): List<PendingAsk> = items
    @Synchronized override fun save(items: List<PendingAsk>) { this.items = items.toList() }
}

/**
 * 还没答上的 `ask_user`:人不在时送达后仍留着,进程被杀后从落盘读回来。
 * 每个对话一条,后一次覆盖前一次。[changes] 是界面看到的唯一来源。
 */
class PendingAsks(private val store: FilePendingAskBacking = MemoryPendingAskBacking()) : PendingAskStore {
    private val _changes = MutableStateFlow(store.load())
    val changes: StateFlow<List<PendingAsk>> = _changes.asStateFlow()

    @Synchronized override fun get(threadId: String): PendingAsk? = _changes.value.firstOrNull { it.threadId == threadId }
    @Synchronized override fun all(): List<PendingAsk> = _changes.value
    @Synchronized override fun put(ask: PendingAsk) {
        val next = _changes.value.filterNot { it.threadId == ask.threadId } + ask
        publish(next)
    }
    @Synchronized override fun remove(threadId: String): PendingAsk? {
        val current = _changes.value
        val found = current.firstOrNull { it.threadId == threadId } ?: return null
        return if (publish(current.filterNot { it.threadId == threadId })) found else null
    }

    private fun publish(items: List<PendingAsk>): Boolean {
        return runCatching { store.save(items); _changes.value = items; true }.getOrDefault(false)
    }
}

/** 用户以后回答时,同一条对话里一次新运行看到的原话。 */
fun PendingAsk.asReply(answer: String): String = tr(R.string.pending_ask_reply, prompt.trim(), answer.trim())
