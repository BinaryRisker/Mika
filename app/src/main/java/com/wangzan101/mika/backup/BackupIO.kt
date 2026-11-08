package com.wangzan101.mika.backup

import android.content.Context
import com.wangzan101.mika.core.backup.AssistantBackup
import com.wangzan101.mika.core.backup.AssistantSnapshot
import com.wangzan101.mika.core.backup.BackupOpen
import com.wangzan101.mika.core.backup.PackSnap
import com.wangzan101.mika.core.knowledge.GuideCatalog
import com.wangzan101.mika.core.knowledge.KnowledgeIndex
import com.wangzan101.mika.core.pack.PackCheck
import com.wangzan101.mika.core.pack.PackTools
import com.wangzan101.mika.core.pack.PackValidate
import com.wangzan101.mika.ledger.LightLedgers
import com.wangzan101.mika.memory.MemoryRuntime
import com.wangzan101.mika.pack.PackStore
import com.wangzan101.mika.settings.ActionRulesStore
import com.wangzan101.mika.task.FileTriggerStore
import java.io.File

/** 导出/导入不读写服务商密钥文件。 */
object BackupIO {
    fun snapshot(context: Context): AssistantSnapshot {
        val mem = MemoryRuntime.of(context)
        return AssistantSnapshot(
            memories = runCatching { mem.store().items.value }.getOrDefault(emptyList()),
            profile = runCatching { mem.profile().snapshot() }.getOrDefault(emptyList()),
            skills = runCatching { mem.skills().list().map { AssistantBackup.skillOf(it) } }.getOrDefault(emptyList()),
            packs = PackStore.installedIds(context).map { id ->
                PackSnap(PackStore.filesOf(context, id).mapValues { it.value.toString(Charsets.UTF_8) })
            },
            routines = FileTriggerStore(File(context.filesDir, "triggers-v3.json")).all(),
            allowSend = ActionRulesStore.get(context).current().allowSend.map { AssistantBackup.allowOf(it) },
            deny = ActionRulesStore.get(context).current().deny.map { AssistantBackup.denyOf(it) },
            ledger = LightLedgers.get(context).all(),
        )
    }

    private fun tools() = PackTools.NAMES
    private fun builtin() = GuideCatalog.loadBundled().items.filter { it.kind == KnowledgeIndex.SKILL }.map { it.name }.toSet()

    fun export(context: Context, password: CharArray): ByteArray = AssistantBackup.seal(snapshot(context), password)

    fun importFile(context: Context, blob: ByteArray, password: CharArray): BackupOpen {
        val opened = AssistantBackup.open(blob, password)
        if (opened !is BackupOpen.Ready) return opened
        return AssistantBackup.prepareImport(opened.snapshot, tools(), builtin())
    }

    /** 常规以关闭写入。不碰 ProviderStore。 */
    fun apply(context: Context, snapshot: AssistantSnapshot) {
        val prepared = AssistantBackup.prepareImport(snapshot, tools(), builtin())
        val snap = (prepared as? BackupOpen.Ready)?.snapshot ?: return
        val mem = MemoryRuntime.of(context)
        runCatching { mem.store().replaceAll(snap.memories) }
        runCatching {
            val profile = mem.profile()
            // 画像没有整份替换入口：有快照时按原文补上缺的
            val have = profile.snapshot().map { it.text }.toSet()
            snap.profile.filter { it.text !in have }.forEach { profile.add(it.text) }
        }
        snap.skills.forEach { s ->
            runCatching {
                mem.skills().put(
                    com.wangzan101.mika.core.skills.PersonalSkill(
                        s.name, s.markdown,
                        runCatching { com.wangzan101.mika.core.skills.SkillStatus.valueOf(s.status) }
                            .getOrDefault(com.wangzan101.mika.core.skills.SkillStatus.trial),
                        s.learnedFrom, null, s.createdAt, emptyList(), source = s.source,
                    ),
                )
            }
        }
        snap.packs.forEach { pack ->
            val files = pack.files.mapValues { it.value.toByteArray(Charsets.UTF_8) }
            if (PackValidate.check(files, tools(), builtin()) is PackCheck.Ok) {
                PackStore.install(context, files, tools(), builtin())
            }
        }
        val triggers = FileTriggerStore(File(context.filesDir, "triggers-v3.json"))
        snap.routines.forEach { triggers.upsert(it.copy(enabled = false)) }
        ActionRulesStore.get(context).save(
            com.wangzan101.mika.core.action.ActionRules(
                AssistantBackup.allowRules(snap.allowSend),
                AssistantBackup.denyRules(snap.deny),
            ),
        )
        LightLedgers.get(context).replaceAll(snap.ledger)
    }
}
