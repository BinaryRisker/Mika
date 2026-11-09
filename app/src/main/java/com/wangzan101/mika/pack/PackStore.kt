package com.wangzan101.mika.pack

import android.content.Context
import com.wangzan101.mika.chat.ConversationStore
import com.wangzan101.mika.core.knowledge.GuideCatalog
import com.wangzan101.mika.core.knowledge.KnowledgeIndex
import com.wangzan101.mika.core.pack.ExamplePack
import com.wangzan101.mika.core.pack.PackCheck
import com.wangzan101.mika.core.pack.PackDiff
import com.wangzan101.mika.core.pack.PackManifest
import com.wangzan101.mika.core.pack.PackManifests
import com.wangzan101.mika.core.pack.PackTools
import com.wangzan101.mika.core.pack.PackLifecycle
import com.wangzan101.mika.core.pack.PackUninstall
import com.wangzan101.mika.core.pack.PackValidate
import com.wangzan101.mika.core.skills.PersonalSkill
import com.wangzan101.mika.core.skills.SkillStatus
import com.wangzan101.mika.core.trigger.TriggerDefinition
import com.wangzan101.mika.memory.MemoryRuntime
import com.wangzan101.mika.task.FileTriggerStore
import java.io.File
import java.util.UUID

/** 包落在应用私有目录。脚本经 sidecar 进沙箱 `/opt/mika/skills/pack/<id>/`。 */
object PackStore {
    fun root(context: Context) = File(context.filesDir, "packs")

    fun dir(context: Context, id: String) = File(root(context), id)

    fun installedIds(context: Context): Set<String> =
        root(context).listFiles()?.filter { File(it, "manifest.json").isFile }?.map { it.name }?.toSet().orEmpty()

    fun filesOf(context: Context, id: String): Map<String, ByteArray> {
        val base = dir(context, id)
        if (!base.isDirectory) return emptyMap()
        val out = linkedMapOf<String, ByteArray>()
        base.walkTopDown().filter { it.isFile }.forEach { f ->
            val rel = f.relativeTo(base).invariantSeparatorsPath
            out[rel] = f.readBytes()
        }
        return out
    }

    fun scriptFiles(context: Context): Map<String, ByteArray> =
        installedIds(context).flatMap { id -> PackLifecycle.sandboxScripts(id, filesOf(context, id)).toList() }.toMap()

    fun list(context: Context): List<PackManifest> = installedIds(context).mapNotNull { id ->
        filesOf(context, id)["manifest.json"]?.let { PackManifests.parse(it.toString(Charsets.UTF_8)) }
    }

    fun knownTools(): Set<String> = PackTools.NAMES
    fun builtinNames(): Set<String> =
        GuideCatalog.loadBundled().items.filter { it.kind == KnowledgeIndex.SKILL }.map { it.name }.toSet()

    fun enabledCount(context: Context, id: String): Int =
        FileTriggerStore(File(context.filesDir, "triggers-v3.json")).all().count { it.packId == id && it.enabled }

    fun installZip(context: Context, bytes: ByteArray, replace: Boolean = false): PackCheck {
        val (files, err) = com.wangzan101.mika.core.pack.PackZip.files(bytes)
        if (files == null) return PackCheck.Rejected(err ?: "bad_zip")
        return install(context, files, knownTools(), builtinNames(), replace)
    }

    fun install(
        context: Context, files: Map<String, ByteArray>, knownTools: Set<String>, builtinNames: Set<String>,
        replace: Boolean = false,
    ): PackCheck {
        val checked = PackValidate.check(files, knownTools, builtinNames)
        if (checked !is PackCheck.Ok) return checked
        val dest = dir(context, checked.manifest.id)
        if (dest.isDirectory && File(dest, "manifest.json").isFile && !replace) {
            val old = PackValidate.check(filesOf(context, checked.manifest.id), knownTools, builtinNames)
            if (old is PackCheck.Ok) return PackCheck.Update(checked, PackDiff.of(old, checked))
        }
        dest.deleteRecursively()
        dest.mkdirs()
        for ((path, bytes) in files) {
            val f = File(dest, path)
            f.parentFile?.mkdirs()
            f.writeBytes(bytes)
        }
        val skills = MemoryRuntime.of(context).skills()
        val now = System.currentTimeMillis()
        for (skill in checked.skills) {
            val existing = skills.get(skill.name)
            skills.put(
                PersonalSkill(
                    name = skill.name, markdown = skill.markdown,
                    status = existing?.status ?: SkillStatus.trial,
                    learnedFrom = existing?.learnedFrom, replacesDraftOf = existing?.replacesDraftOf,
                    createdAt = existing?.createdAt ?: now,
                    outcomes = existing?.outcomes ?: emptyList(),
                    source = existing?.source ?: "pack:${checked.manifest.id}",
                ),
            )
        }
        val conversations = ConversationStore.shared(context)
        val store = FileTriggerStore(File(context.filesDir, "triggers-v3.json"))
        val existingRoutines = store.all().filter { it.packId == checked.manifest.id }
        for (template in checked.templates) {
            val match = existingRoutines.firstOrNull { it.displayName == template.name }
            if (match != null) {
                if (!match.enabled) store.upsert(match.copy(goal = template.goal, kind = template.kind))
                continue
            }
            val threadId = conversations.newConversation()
            conversations.setTitle(threadId, template.name)
            store.upsert(
                TriggerDefinition(
                    id = "routine:pack:${checked.manifest.id}:${UUID.randomUUID()}",
                    threadId = threadId,
                    goal = template.goal,
                    nextAtMs = 0,
                    enabled = false,
                    displayName = template.name,
                    kind = template.kind,
                    packId = checked.manifest.id,
                ),
            )
        }
        return checked
    }

    fun uninstall(context: Context, id: String, keepEnabled: Boolean): PackUninstall? {
        val files = filesOf(context, id)
        if (files.isEmpty()) return null
        val checked = PackValidate.check(files, knownTools(), builtinNames())
        val skills = if (checked is PackCheck.Ok) checked.skills else emptyList()
        val templates = if (checked is PackCheck.Ok) checked.templates else emptyList()
        val store = FileTriggerStore(File(context.filesDir, "triggers-v3.json"))
        val enabled = store.all().filter { it.packId == id && it.enabled }.map { it.id }.toSet()
        val plan = PackLifecycle.uninstall(id, skills, templates, enabled, keepEnabled)
        val personal = MemoryRuntime.of(context).skills()
        plan.removeSkillNames.forEach { personal.remove(it) }
        store.all().filter { it.packId == id }.forEach { routine ->
            when {
                routine.id in plan.keepRoutineIds -> { }
                routine.enabled && routine.id in plan.stopRoutineIds -> store.upsert(routine.copy(enabled = false))
                !routine.enabled -> store.remove(routine.id)
            }
        }
        dir(context, id).deleteRecursively()
        return plan
    }

    fun seedExample(context: Context) {
        val prefs = context.getSharedPreferences("mika-packs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("seeded_list_to_csv", false)) return
        if (File(dir(context, ExamplePack.ID), "manifest.json").isFile) {
            prefs.edit().putBoolean("seeded_list_to_csv", true).apply()
            return
        }
        install(context, ExamplePack.files(), knownTools(), builtinNames())
        prefs.edit().putBoolean("seeded_list_to_csv", true).apply()
    }
}
