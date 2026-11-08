package com.wangzan101.mika.core.pack

/**
 * 卸包：技能、脚本和还没启用的模板去掉。已经启用的常规由用户选停用还是留下。
 * 留下的常规不再引用已卸的脚本；到点脚本缺失就失败，不改跑包外命令。
 */
data class PackUninstall(
    val packId: String,
    val removeSkillNames: Set<String>,
    val dropTemplates: List<RoutineTemplate>,
    val stopRoutineIds: Set<String>,
    val keepRoutineIds: Set<String>,
) {
    fun dropsTemplate(name: String) = dropTemplates.any { it.name == name }
}

object PackLifecycle {
    /** 装进沙箱 `/opt/mika/skills/pack/<id>/` 的脚本；一份包里多份技能的 scripts/ 文件名不要撞。 */
    fun sandboxScripts(packId: String, files: Map<String, ByteArray>): Map<String, ByteArray> {
        val out = linkedMapOf<String, ByteArray>()
        for ((path, bytes) in files) {
            val parts = path.split('/')
            if (parts.size >= 4 && parts[0] == "skills" && parts[2] == "scripts") {
                val rest = parts.drop(2).joinToString("/")
                out["pack/$packId/$rest"] = bytes
            }
        }
        return out
    }

    fun uninstall(
        packId: String,
        skills: List<PackSkill>,
        templates: List<RoutineTemplate>,
        enabledRoutineIds: Set<String>,
        keepEnabled: Boolean,
    ): PackUninstall {
        val stop = if (keepEnabled) emptySet() else enabledRoutineIds
        val keep = if (keepEnabled) enabledRoutineIds else emptySet()
        return PackUninstall(packId, skills.map { it.name }.toSet(), templates, stop, keep)
    }

    /** 常规带着包编号、包已经不在时，这一次不能开工。 */
    fun missingScript(packId: String?, installed: Set<String>): Boolean =
        packId != null && packId !in installed
}
