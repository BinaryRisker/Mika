package com.wangzan101.mika.settings

import android.content.Context
import com.wangzan101.mika.core.action.ActionRules
import com.wangzan101.mika.core.backup.AllowSnap
import com.wangzan101.mika.core.backup.AssistantBackup
import com.wangzan101.mika.core.backup.DenySnap
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** 发送允许名单和按包禁止。导出导入走这一份，不进服务商密钥文件。 */
class ActionRulesStore(private val file: File) {
    @Serializable
    private data class Snap(val allowSend: List<AllowSnap> = emptyList(), val deny: List<DenySnap> = emptyList())

    @Synchronized fun current(): ActionRules {
        if (!file.isFile) return ActionRules.NONE
        return runCatching {
            val s = JSON.decodeFromString<Snap>(file.readText())
            ActionRules(AssistantBackup.allowRules(s.allowSend), AssistantBackup.denyRules(s.deny))
        }.getOrDefault(ActionRules.NONE)
    }

    @Synchronized fun save(rules: ActionRules) {
        file.parentFile?.mkdirs()
        val snap = Snap(rules.allowSend.map { AssistantBackup.allowOf(it) }, rules.deny.map { AssistantBackup.denyOf(it) })
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(JSON.encodeToString(snap))
        Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        private val JSON = Json { ignoreUnknownKeys = true }
        fun get(context: Context) = ActionRulesStore(File(context.filesDir, "action-rules.json"))
    }
}
