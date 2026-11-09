package com.wangzan101.mika.core.pack

/** 一份技能或常规模板相对已装版本改了哪些行。 */
data class NamedDiff(val name: String, val added: List<String>, val removed: List<String>) {
    val changed: Boolean get() = added.isNotEmpty() || removed.isNotEmpty()
}

/** 更新包时给用户看的差异：技能正文和常规模板。 */
data class PackUpdateDiff(
    val fromVersion: String,
    val toVersion: String,
    val skills: List<NamedDiff>,
    val templates: List<NamedDiff>,
) {
    fun summary(limit: Int = 12): String = buildString {
        append(fromVersion).append(" → ").append(toVersion)
        val lines = mutableListOf<String>()
        for (d in skills + templates) {
            if (!d.changed) continue
            d.removed.take(3).forEach { lines += "- ${d.name}: $it" }
            d.added.take(3).forEach { lines += "+ ${d.name}: $it" }
        }
        if (lines.isEmpty()) {
            append("\n(unchanged)")
            return@buildString
        }
        append('\n')
        append(lines.take(limit).joinToString("\n"))
        val extra = lines.size - limit
        if (extra > 0) append("\n… +").append(extra)
    }
}

object PackDiff {
    fun of(old: PackCheck.Ok, incoming: PackCheck.Ok): PackUpdateDiff {
        val oldSkills = old.skills.associateBy { it.name }
        val newSkills = incoming.skills.associateBy { it.name }
        val skillNames = (oldSkills.keys + newSkills.keys).sorted()
        val skills = skillNames.map { name ->
            lineDiff(name, oldSkills[name]?.markdown.orEmpty(), newSkills[name]?.markdown.orEmpty())
        }
        val oldT = old.templates.associate { it.name to "${it.kind.name}\n${it.goal}" }
        val newT = incoming.templates.associate { it.name to "${it.kind.name}\n${it.goal}" }
        val templateNames = (oldT.keys + newT.keys).sorted()
        val templates = templateNames.map { name -> lineDiff(name, oldT[name].orEmpty(), newT[name].orEmpty()) }
        return PackUpdateDiff(old.manifest.version, incoming.manifest.version, skills, templates)
    }

    private fun lineDiff(name: String, before: String, after: String): NamedDiff {
        val old = before.lineSequence().map { it.trimEnd() }.filter { it.isNotEmpty() }.toSet()
        val incoming = after.lineSequence().map { it.trimEnd() }.filter { it.isNotEmpty() }.toSet()
        return NamedDiff(name, (incoming - old).toList(), (old - incoming).toList())
    }
}
