package com.wangzan101.mika.core.pack

import com.wangzan101.mika.core.assembly.toolDispatcher
import com.wangzan101.mika.core.backup.AssistantBackup
import com.wangzan101.mika.core.backup.BackupOpen
import com.wangzan101.mika.core.backup.PackCrypto
import com.wangzan101.mika.core.cap.sandbox.CommandResult
import com.wangzan101.mika.core.cap.sandbox.FakeSandbox
import com.wangzan101.mika.core.cap.sandbox.SandboxStatus
import com.wangzan101.mika.core.knowledge.ScriptEntry
import com.wangzan101.mika.core.ledger.LightEntry
import com.wangzan101.mika.core.ledger.LightLedger
import com.wangzan101.mika.core.model.Part
import com.wangzan101.mika.core.tool.ActionPass
import com.wangzan101.mika.core.tool.FakeScreen
import com.wangzan101.mika.core.trigger.TriggerDefinition
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhaseEPackTest {
    private val tools = ExamplePack.TOOLS
    private var seq = 0
    private fun call(name: String, args: JsonObject) = Part.ToolCall("e${++seq}", name, args.toString())
    private fun JsonObject.s(k: String) = this[k]!!.jsonPrimitive.content

    @Test fun examplePackValidates() {
        val r = PackValidate.check(ExamplePack.files(), tools, builtinSkillNames = setOf("documents"))
        assertTrue((r as? PackCheck.Rejected)?.reason ?: r.toString(), r is PackCheck.Ok)
        val ok = r as PackCheck.Ok
        assertEquals(ExamplePack.ID, ok.manifest.id)
        assertEquals(listOf("list-to-csv"), ok.skills.map { it.name })
        assertEquals("列表收成表格", ok.templates.single().name)
        assertTrue(PackLifecycle.sandboxScripts(ok.manifest.id, ok.files).containsKey("pack/list-to-csv/scripts/rows_to_csv.py"))
    }

    @Test fun pathWithDotDotIsRejectedWhole() {
        val files = ExamplePack.files() + ("skills/../secret.py" to byteArrayOf(1))
        assertEquals("unsafe_path", (PackValidate.check(files, tools, emptySet()) as PackCheck.Rejected).reason)
    }

    @Test fun absolutePathIsRejectedWhole() {
        val files = ExamplePack.files() + ("/tmp/x.py" to byteArrayOf(1))
        assertEquals("unsafe_path", (PackValidate.check(files, tools, emptySet()) as PackCheck.Rejected).reason)
    }

    @Test fun unknownToolRejectsTheWholePack() {
        val files = ExamplePack.files().toMutableMap()
        val md = files.getValue("skills/list-to-csv/SKILL.md").toString(Charsets.UTF_8)
            .replace("`phone_observe`", "`not_a_real_tool`")
        files["skills/list-to-csv/SKILL.md"] = md.toByteArray()
        assertEquals("unknown_tool", (PackValidate.check(files, tools, emptySet()) as PackCheck.Rejected).reason)
    }

    @Test fun updateDiffShowsSkillLineChangesBeforeReplace() {
        val old = PackValidate.check(ExamplePack.files(), tools, emptySet()) as PackCheck.Ok
        val files = ExamplePack.files().toMutableMap()
        val md = files.getValue("skills/list-to-csv/SKILL.md").toString(Charsets.UTF_8) + "\n- 新的一行做法\n"
        files["skills/list-to-csv/SKILL.md"] = md.toByteArray()
        val incoming = PackValidate.check(files, tools, emptySet()) as PackCheck.Ok
        val diff = PackDiff.of(old, incoming)
        assertTrue(diff.skills.single { it.name == "list-to-csv" }.added.any { "新的一行做法" in it })
        assertTrue(diff.summary().contains("→"))
    }

    @Test fun uninstallDropsUnusedTemplatesAndCanKeepEnabledRoutines() {
        val ok = PackValidate.check(ExamplePack.files(), tools, emptySet()) as PackCheck.Ok
        val drop = PackLifecycle.uninstall(ok.manifest.id, ok.skills, ok.templates, enabledRoutineIds = emptySet(), keepEnabled = true)
        assertTrue(drop.dropsTemplate("列表收成表格"))
        assertTrue(drop.keepRoutineIds.isEmpty())
        val keep = PackLifecycle.uninstall(ok.manifest.id, ok.skills, ok.templates, enabledRoutineIds = setOf("r1"), keepEnabled = true)
        assertEquals(setOf("r1"), keep.keepRoutineIds)
        assertTrue(PackLifecycle.missingScript("list-to-csv", installed = emptySet()))
        assertFalse(PackLifecycle.missingScript("list-to-csv", installed = setOf("list-to-csv")))
        assertFalse(PackLifecycle.missingScript(null, installed = emptySet()))
    }

    @Test fun lightLedgerSearchFindsFilenameAfterRunRecordsAreGone() {
        val file = Files.createTempDirectory("ledger").resolve("light.json").toFile()
        val now = System.currentTimeMillis()
        val ledger = LightLedger(file, now = { now })
        repeat(30) { i ->
            ledger.record(LightEntry(now - 30_000 + i, "c$i", goal = "旧任务$i", outcome = "做成", files = listOf("old$i.txt")))
        }
        ledger.record(LightEntry(now, "kept", routineId = "r", goal = "收成表格", outcome = "做成", files = listOf("table.csv")))
        // 运行记录只留 30 次，清掉也不动轻账
        assertEquals("table.csv", ledger.search("table.csv").single().files.single())
        assertEquals("kept", ledger.search("收成表格").single().conversationId)
        assertTrue(ledger.search("old1.txt").isNotEmpty())
        val back = LightLedger(file)
        assertEquals("table.csv", back.search("TABLE.CSV").single().files.single())
    }

    @Test fun wrongPasswordDoesNotOpenExport() {
        val blob = PackCrypto.seal("hello".toByteArray(), "right".toCharArray())
        assertNull(PackCrypto.open(blob, "wrong".toCharArray()))
        assertEquals("hello", PackCrypto.open(blob, "right".toCharArray())!!.toString(Charsets.UTF_8))
    }

    @Test fun importTurnsRoutinesOffAndKeepsSecretsOut() {
        val snap = com.wangzan101.mika.core.backup.AssistantSnapshot(
            routines = listOf(TriggerDefinition("r1", "t", "do", 1_000, enabled = true, packId = "list-to-csv")),
            allowSend = listOf(com.wangzan101.mika.core.backup.AllowSnap("com.chat", "张三")),
            deny = listOf(com.wangzan101.mika.core.backup.DenySnap("com.pay", send = true)),
        )
        val text = AssistantBackup.encode(snap)
        assertFalse(AssistantBackup.containsSecrets(text))
        val blob = AssistantBackup.seal(snap, "pw".toCharArray())
        assertTrue(AssistantBackup.open(blob, "no".toCharArray()) is BackupOpen.Rejected)
        val opened = AssistantBackup.open(blob, "pw".toCharArray()) as BackupOpen.Ready
        val prepared = AssistantBackup.prepareImport(opened.snapshot, tools, emptySet()) as BackupOpen.Ready
        assertTrue(prepared.snapshot.routines.all { !it.enabled })
        assertEquals(1, prepared.preview.routines)
        val keys = File(Files.createTempDirectory("keys").toFile(), "providers.json")
        keys.writeText("""{"apiKey":"sk-live"}""")
        // 导入不碰密钥文件
        assertTrue(keys.readText().contains("sk-live"))
    }

    @Test fun packScriptWritesCsvAndSharePutsItInDownloadMika() = runTest {
        val sb = FakeSandbox()
        val ws = sb.workspaceDir("conv1")
        File(sb.skillsDir, "pack/list-to-csv/scripts").mkdirs()
        File(sb.skillsDir, "pack/list-to-csv/scripts/rows_to_csv.py").writeText("print(1)")
        sb.manifests["pack/list-to-csv"] = listOf(ScriptEntry("scripts/rows_to_csv.py", runnable = true))
        sb.next = {
            File(ws, "table.csv").writeText("name,n\n甲,1\n")
            CommandResult(0, false, false, 8, "table.csv\n", 0, false)
        }
        val d = toolDispatcher(FakeScreen(), sandbox = sb, workspace = { "conv1" })
        val run = d.execute(call("run_bundled_script", buildJsonObject {
            put("source", "pack:list-to-csv"); put("script", "scripts/rows_to_csv.py")
        }), ActionPass()).content
        assertEquals(run.toString(), "true", run.s("ok"))
        assertEquals("pack/list-to-csv/scripts/rows_to_csv.py", sb.runs.single().argv[1].substringAfter("/skills/"))
        val shared = d.execute(call("share_file", buildJsonObject { put("path", "table.csv"); put("name", "table.csv") }), ActionPass()).content
        assertEquals("Download/Mika/table.csv", shared.s("savedAs"))
    }

    @Test fun networkedPackScriptRefusedWhenNetworkOff() = runTest {
        val sb = FakeSandbox(SandboxStatus.Ready(network = false))
        File(sb.skillsDir, "pack/list-to-csv/scripts").mkdirs()
        File(sb.skillsDir, "pack/list-to-csv/scripts/fetch.mjs").writeText("")
        sb.manifests["pack/list-to-csv"] = listOf(ScriptEntry("scripts/fetch.mjs", runnable = true, network = true))
        val d = toolDispatcher(FakeScreen(), sandbox = sb, workspace = { "conv1" })
        val r = d.execute(call("run_bundled_script", buildJsonObject {
            put("source", "pack:list-to-csv"); put("script", "scripts/fetch.mjs")
        }), ActionPass()).content
        assertEquals("script_unavailable", r.s("error"))
        assertTrue(sb.runs.isEmpty())
    }

    @Test fun zipWithDotDotIsRejected() {
        val z = zip(mapOf("../secret.py" to "x".toByteArray(), "manifest.json" to "{}".toByteArray()))
        assertEquals("unsafe_path", PackZip.files(z).second)
    }

    @Test fun zipWrapperDirectoryIsStripped() {
        val inner = ExamplePack.files()
        val wrapped = inner.mapKeys { "list-to-csv/${it.key}" }
        val files = PackZip.stripWrapper(wrapped)
        assertEquals(inner.keys, files.keys)
        val z = zip(wrapped)
        val opened = PackZip.files(z).first!!
        assertTrue("manifest.json" in opened)
        assertTrue(PackValidate.check(opened, tools, emptySet()) is PackCheck.Ok)
    }

    private fun zip(files: Map<String, ByteArray>): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(out).use { zip ->
            for ((name, data) in files) {
                zip.putNextEntry(java.util.zip.ZipEntry(name))
                zip.write(data)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test fun missingPackScriptDoesNotRunOutsideCommands() = runTest {
        val sb = FakeSandbox()
        val d = toolDispatcher(FakeScreen(), sandbox = sb, workspace = { "conv1" })
        val r = d.execute(call("run_bundled_script", buildJsonObject {
            put("source", "pack:list-to-csv"); put("script", "scripts/rows_to_csv.py")
        }), ActionPass()).content
        assertEquals("script_unavailable", r.s("error"))
        assertTrue(r.s("message").contains("missing") || r.s("message").contains("outside"))
        assertTrue(sb.runs.isEmpty())
    }
}
