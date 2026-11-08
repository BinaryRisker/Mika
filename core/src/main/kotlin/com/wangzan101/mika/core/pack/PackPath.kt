package com.wangzan101.mika.core.pack

import com.wangzan101.mika.core.skills.PersonalSkills
import com.wangzan101.mika.core.workspace.SandboxPaths

/** 压缩包和包目录里只认相对路径：不要绝对路径，也不要 `..`。 */
object PackPath {
    val ALLOWED_TOP = setOf("manifest.json", "knowledge", "skills", "routines", "scripts")

    fun safe(rel: String): Boolean {
        if (rel.isBlank() || rel.startsWith("/") || rel.startsWith("\\") || '\\' in rel) return false
        val parts = rel.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." || !segmentOk(it) }) return false
        val top = parts.first()
        return top == "manifest.json" && parts.size == 1 || top in ALLOWED_TOP
    }

    fun packId(id: String): Boolean = PersonalSkills.NAME.matches(id)

    private fun segmentOk(seg: String): Boolean {
        if (seg.isEmpty() || seg == "." || seg == "..") return false
        if ('/' in seg || '\\' in seg) return false
        return seg.codePoints().noneMatch(SandboxPaths::hiddenChar)
    }
}
