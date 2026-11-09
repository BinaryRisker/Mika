package com.wangzan101.mika.cap.sources

import android.content.Context
import android.provider.Settings
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 用户逐个打开的包名，默认一个都没有。见过的包只用来在设置里列名字。 */
class NotificationAllowStore(private val file: File) {
    constructor(context: Context) : this(File(context.filesDir, "notif-allow-v1.json"))

    @Synchronized fun allowed(): Set<String> = load().allowed
    @Synchronized fun seen(): List<Pair<String, String>> = load().seen.toList()

    @Synchronized fun setAllowed(packageName: String, on: Boolean) {
        val cur = load()
        val next = if (on) cur.allowed + packageName else cur.allowed - packageName
        save(cur.copy(allowed = next))
    }

    @Synchronized fun noteSeen(packageName: String, label: String) {
        if (packageName.isBlank()) return
        val cur = load()
        val rest = cur.seen.filterNot { it.first == packageName }
        save(cur.copy(seen = listOf(packageName to label.ifBlank { packageName }) + rest))
    }

    private data class State(val allowed: Set<String>, val seen: List<Pair<String, String>>)

    private fun load(): State {
        if (!file.isFile) return State(emptySet(), emptyList())
        return runCatching {
            val o = JSONObject(file.readText())
            val allowed = o.optJSONArray("allowed")?.let { a ->
                (0 until a.length()).mapNotNull { a.optString(it).takeIf { s -> s.isNotBlank() } }
            }.orEmpty().toSet()
            val seen = o.optJSONArray("seen")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val row = a.optJSONObject(i) ?: return@mapNotNull null
                    val pkg = row.optString("pkg")
                    if (pkg.isBlank()) null else pkg to row.optString("label", pkg)
                }
            }.orEmpty()
            State(allowed, seen)
        }.getOrDefault(State(emptySet(), emptyList()))
    }

    private fun save(state: State) {
        file.parentFile?.mkdirs()
        val o = JSONObject()
        o.put("allowed", JSONArray(state.allowed.toList()))
        o.put("seen", JSONArray(state.seen.map { (pkg, label) ->
            JSONObject().put("pkg", pkg).put("label", label)
        }))
        file.writeText(o.toString())
    }

    companion object {
        fun listenerEnabled(context: Context): Boolean {
            val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val pkg = context.packageName
            return flat.split(':').any { it.substringBefore('/') == pkg || it.contains(pkg) }
        }
    }
}
