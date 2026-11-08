package com.wangzan101.mika.core.ledger

import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 轻账一条：不存屏幕文字，也不存工具参数里的输入。 */
@Serializable
data class LightEntry(
    val atMs: Long,
    val conversationId: String,
    val routineId: String? = null,
    val goal: String,
    val outcome: String,
    val files: List<String> = emptyList(),
)

/**
 * 本机轻账：和用量账本同一量级，保留 [keepDays] 天。运行记录清掉不影响这里。
 * 搜索只看目标原文、结局那句话、产出文件名。
 */
class LightLedger(
    private val file: File,
    private val now: () -> Long = System::currentTimeMillis,
    private val keepDays: Int = KEEP_DAYS,
) {
    private val lock = Any()
    private var entries: MutableList<LightEntry> = read()

    fun all(): List<LightEntry> = synchronized(lock) { entries.toList() }

    fun record(entry: LightEntry) = synchronized(lock) {
        entries.add(entry)
        pruneLocked()
        saveLocked()
    }

    fun search(query: String): List<LightEntry> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val needle = q.lowercase()
        return synchronized(lock) {
            entries.filter { e ->
                needle in e.goal.lowercase() || needle in e.outcome.lowercase() ||
                    e.files.any { needle in it.lowercase() }
            }.sortedByDescending { it.atMs }
        }
    }

    fun replaceAll(next: List<LightEntry>) = synchronized(lock) {
        entries = next.toMutableList()
        pruneLocked()
        saveLocked()
    }

    private fun pruneLocked() {
        val cut = now() - TimeUnit.DAYS.toMillis(keepDays.toLong())
        entries.removeAll { it.atMs < cut }
    }

    private fun read(): MutableList<LightEntry> {
        if (!file.isFile) return mutableListOf()
        return runCatching {
            Json.decodeFromString<LightSnapshot>(file.readText()).entries.toMutableList()
        }.getOrElse { mutableListOf() }
    }

    private fun saveLocked() {
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(tmp).use { out ->
            out.write(Json.encodeToString(LightSnapshot(entries.toList())).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        check(tmp.renameTo(file) || (file.delete() && tmp.renameTo(file))) { "light ledger rename failed" }
    }

    companion object {
        const val KEEP_DAYS = 400
    }
}

@Serializable
private data class LightSnapshot(val entries: List<LightEntry> = emptyList())
