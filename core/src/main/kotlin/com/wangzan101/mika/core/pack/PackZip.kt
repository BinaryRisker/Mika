package com.wangzan101.mika.core.pack

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream

/** 压缩包只交出相对路径文件图；绝对路径和 `..` 整份丢掉。 */
object PackZip {
    const val MAX_BYTES = 8 shl 20

    fun files(bytes: ByteArray): Pair<Map<String, ByteArray>?, String?> {
        val raw = linkedMapOf<String, ByteArray>()
        var total = 0
        return try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) continue
                    val name = entry.name.replace('\\', '/').removePrefix("./")
                    if (!PackPath.safe(name) && stripName(name) == null) return null to "unsafe_path"
                    val data = zip.readBytes()
                    total += data.size
                    if (total > MAX_BYTES) return null to "too_large"
                    raw[name] = data
                }
            }
            val files = stripWrapper(raw)
            if (files.keys.any { !PackPath.safe(it) }) null to "unsafe_path" else files to null
        } catch (e: Exception) {
            null to "bad_zip"
        }
    }

    /** 允许整包套一层目录：`list-to-csv/manifest.json`。 */
    internal fun stripWrapper(files: Map<String, ByteArray>): Map<String, ByteArray> {
        if ("manifest.json" in files) return files
        val tops = files.keys.map { it.substringBefore('/') }.toSet()
        if (tops.size != 1) return files
        val prefix = tops.single() + "/"
        if (prefix + "manifest.json" !in files) return files
        return files.mapNotNull { (k, v) -> k.removePrefix(prefix).takeIf { it.isNotEmpty() }?.let { it to v } }.toMap()
    }

    private fun stripName(name: String): String? {
        val slash = name.indexOf('/')
        if (slash <= 0) return null
        val rest = name.substring(slash + 1)
        return rest.takeIf { PackPath.safe(it) }
    }
}
