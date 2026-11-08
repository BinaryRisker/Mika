package com.wangzan101.mika.core.backup

import com.wangzan101.mika.core.action.ActionRules
import com.wangzan101.mika.core.ledger.LightEntry
import com.wangzan101.mika.core.memory.MemoryItem
import com.wangzan101.mika.core.memory.ProfileEntry
import com.wangzan101.mika.core.pack.PackCheck
import com.wangzan101.mika.core.pack.PackValidate
import com.wangzan101.mika.core.skills.PersonalSkill
import com.wangzan101.mika.core.skills.SkillDrafter
import com.wangzan101.mika.core.trigger.TriggerDefinition
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class SkillSnap(
    val name: String,
    val markdown: String,
    val status: String,
    val learnedFrom: String? = null,
    val source: String? = null,
    val createdAt: Long = 0,
)

@Serializable
data class AllowSnap(val scope: String, val objectName: String)

@Serializable
data class DenySnap(
    val packageName: String,
    val send: Boolean = false,
    val delete: Boolean = false,
    val payment: Boolean = false,
)

@Serializable
data class PackSnap(
    val files: Map<String, String>,
)

@Serializable
data class AssistantSnapshot(
    val version: Int = 1,
    val memories: List<MemoryItem> = emptyList(),
    val profile: List<ProfileEntry> = emptyList(),
    val skills: List<SkillSnap> = emptyList(),
    val packs: List<PackSnap> = emptyList(),
    val routines: List<TriggerDefinition> = emptyList(),
    val allowSend: List<AllowSnap> = emptyList(),
    val deny: List<DenySnap> = emptyList(),
    val ledger: List<LightEntry> = emptyList(),
)

data class BackupPreview(
    val memories: Int,
    val skills: Int,
    val packs: Int,
    val routines: Int,
    val allow: Int,
    val deny: Int,
)

sealed interface BackupOpen {
    data class Ready(val snapshot: AssistantSnapshot, val preview: BackupPreview) : BackupOpen
    data class Rejected(val reason: String) : BackupOpen
}

/**
 * 导出里没有服务商密钥、MCP 令牌、锁屏 PIN。导入后常规全部关着，名单覆盖前先给出数量。
 */
object AssistantBackup {
    const val VERSION = 1
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(snapshot: AssistantSnapshot): String = json.encodeToString(snapshot.copy(version = VERSION))

    fun containsSecrets(text: String): Boolean =
        Regex("\"(apiKey|api_key|secretKey|mcpToken|mcp_token|lockPin|pin_hash)\"").containsMatchIn(text)

    fun seal(snapshot: AssistantSnapshot, password: CharArray): ByteArray {
        val text = encode(snapshot)
        check(!containsSecrets(text)) { "backup must not contain provider keys or tokens" }
        return PackCrypto.seal(text.toByteArray(Charsets.UTF_8), password)
    }

    fun open(blob: ByteArray, password: CharArray): BackupOpen {
        val plain = PackCrypto.open(blob, password) ?: return BackupOpen.Rejected("bad_password")
        val snap = runCatching { json.decodeFromString<AssistantSnapshot>(plain.toString(Charsets.UTF_8)) }
            .getOrElse { return BackupOpen.Rejected("bad_file") }
        if (snap.version != VERSION) return BackupOpen.Rejected("bad_version")
        return BackupOpen.Ready(
            snap,
            BackupPreview(snap.memories.size, snap.skills.size, snap.packs.size, snap.routines.size, snap.allowSend.size, snap.deny.size),
        )
    }

    fun prepareImport(
        snapshot: AssistantSnapshot,
        knownTools: Set<String>,
        builtinSkillNames: Set<String>,
        scriptTool: String? = "run_bundled_script",
    ): BackupOpen {
        for (pack in snapshot.packs) {
            val files = pack.files.mapValues { it.value.toByteArray(Charsets.UTF_8) }
            if (PackValidate.check(files, knownTools, builtinSkillNames, scriptTool) is PackCheck.Rejected) {
                return BackupOpen.Rejected("invalid_pack")
            }
        }
        for (skill in snapshot.skills) {
            if (SkillDrafter.validate(skill.markdown, knownTools, scriptTool) != null) {
                return BackupOpen.Rejected("invalid_skill")
            }
        }
        val routines = snapshot.routines.map { it.copy(enabled = false) }
        return BackupOpen.Ready(
            snapshot.copy(routines = routines),
            BackupPreview(snapshot.memories.size, snapshot.skills.size, snapshot.packs.size, routines.size, snapshot.allowSend.size, snapshot.deny.size),
        )
    }

    fun skillOf(s: PersonalSkill) = SkillSnap(s.name, s.markdown, s.status.name, s.learnedFrom, s.source, s.createdAt)

    fun allowOf(a: ActionRules.SendAllow) = AllowSnap(a.scope, a.objectName)
    fun denyOf(d: ActionRules.PackageDeny) = DenySnap(d.packageName, d.send, d.delete, d.payment)
    fun allowRules(items: List<AllowSnap>) = items.map { ActionRules.SendAllow(it.scope, it.objectName) }
    fun denyRules(items: List<DenySnap>) = items.map { ActionRules.PackageDeny(it.packageName, it.send, it.delete, it.payment) }
}
