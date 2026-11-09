package com.wangzan101.mika.core.pack

import com.wangzan101.mika.core.knowledge.FrontMatter
import com.wangzan101.mika.core.knowledge.ScriptEntry
import com.wangzan101.mika.core.knowledge.ScriptManifest
import com.wangzan101.mika.core.skills.SkillDrafter
import com.wangzan101.mika.core.trigger.RoutineKind
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

data class PackSkill(val name: String, val markdown: String)

data class PackScript(val skill: String, val entry: ScriptEntry, val bytes: ByteArray)

data class RoutineTemplate(
    val name: String,
    val goal: String,
    val kind: RoutineKind = RoutineKind.Manual,
)

sealed interface PackCheck {
    data class Ok(
        val manifest: PackManifest,
        val skills: List<PackSkill>,
        val scripts: List<PackScript>,
        val templates: List<RoutineTemplate>,
        val files: Map<String, ByteArray>,
    ) : PackCheck

    data class Rejected(val reason: String) : PackCheck

    /** 已有同 id 的包：先给差异，用户确认后再 [PackStore.install] 且 replace=true。 */
    data class Update(val incoming: Ok, val diff: PackUpdateDiff) : PackCheck
}

/**
 * 一份包的文件图（相对路径 → 内容）。任一路径不安全、技能点了未知工具、脚本清单不合法，整份不装。
 */
object PackValidate {
    private val json = Json { ignoreUnknownKeys = true }

    fun check(
        files: Map<String, ByteArray>,
        knownTools: Set<String>,
        builtinSkillNames: Set<String>,
        scriptTool: String? = "run_bundled_script",
    ): PackCheck {
        if (files.isEmpty()) return PackCheck.Rejected("empty")
        if (files.keys.any { !PackPath.safe(it) }) return PackCheck.Rejected("unsafe_path")
        val manifestBytes = files["manifest.json"] ?: return PackCheck.Rejected("bad_manifest")
        val manifest = PackManifests.parse(manifestBytes.toString(Charsets.UTF_8))
            ?: return PackCheck.Rejected("bad_manifest")
        val skills = mutableListOf<PackSkill>()
        val scripts = mutableListOf<PackScript>()
        for ((path, bytes) in files) {
            val parts = path.split('/')
            if (parts.size >= 3 && parts[0] == "skills" && parts.last() == "SKILL.md") {
                val name = parts[1]
                if (!PackPath.packId(name)) return PackCheck.Rejected("invalid_skill")
                val md = bytes.toString(Charsets.UTF_8)
                val listed = files.filter { it.key.startsWith("skills/$name/") && it.key.contains("/scripts/") }
                    .keys.map { it.removePrefix("skills/$name/") }.toSet()
                val err = SkillDrafter.validate(md, knownTools, scriptTool, listed)
                    ?: skillNameMismatch(md, name)
                if (err != null) return PackCheck.Rejected(if (err == "unknown_tool") "unknown_tool" else "invalid_skill")
                if (name in builtinSkillNames) return PackCheck.Rejected("builtin_name")
                skills += PackSkill(name, md)
            }
        }
        for (skill in skills) {
            val prefix = "skills/${skill.name}/"
            val manifestPath = prefix + "scripts/manifest.json"
            val listed = files[manifestPath]?.let { ScriptManifest.parse(it.toString(Charsets.UTF_8)) }.orEmpty()
            if (files.keys.any { it.startsWith(prefix + "scripts/") && it != manifestPath } && listed.isEmpty()) {
                return PackCheck.Rejected("bad_script")
            }
            for (e in listed) {
                if (!PackPath.safe(prefix + e.file)) return PackCheck.Rejected("unsafe_path")
                val body = files[prefix + e.file] ?: return PackCheck.Rejected("bad_script")
                if (e.runnable && ScriptManifest.interpreterFor(e.file) == null) return PackCheck.Rejected("bad_script")
                scripts += PackScript(skill.name, e, body)
            }
        }
        val templates = files.filter { it.key.startsWith("routines/") && it.key.endsWith(".json") }.map { (_, bytes) ->
            parseTemplate(bytes.toString(Charsets.UTF_8)) ?: return PackCheck.Rejected("bad_template")
        }
        return PackCheck.Ok(manifest, skills, scripts, templates, files)
    }

    private fun skillNameMismatch(md: String, dir: String): String? {
        val name = FrontMatter.parse(md).first["name"] as? String ?: return "invalid_skill"
        return if (name != dir) "invalid_skill" else null
    }

    private fun parseTemplate(text: String): RoutineTemplate? = runCatching {
        val o = json.parseToJsonElement(text) as? JsonObject ?: return null
        val name = o["name"]?.jsonPrimitive?.content?.trim().orEmpty()
        val goal = o["goal"]?.jsonPrimitive?.content?.trim().orEmpty()
        if (name.isEmpty() || goal.isEmpty()) return null
        val kind = o["kind"]?.jsonPrimitive?.content?.let { runCatching { RoutineKind.valueOf(it) }.getOrNull() }
            ?: RoutineKind.Manual
        if (o["enabled"]?.jsonPrimitive?.booleanOrNull == true) return null
        RoutineTemplate(name, goal, kind)
    }.getOrNull()
}
