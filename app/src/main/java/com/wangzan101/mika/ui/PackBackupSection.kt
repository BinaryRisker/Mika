package com.wangzan101.mika.ui

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import com.wangzan101.mika.R
import com.wangzan101.mika.backup.BackupIO
import com.wangzan101.mika.core.backup.AssistantSnapshot
import com.wangzan101.mika.core.backup.BackupOpen
import com.wangzan101.mika.core.backup.BackupPreview
import com.wangzan101.mika.core.pack.PackCheck
import com.wangzan101.mika.pack.PackStore
import com.wangzan101.mika.ui.components.ActionSheet
import com.wangzan101.mika.ui.components.KeyboardDismissArea
import com.wangzan101.mika.ui.components.ListRow
import com.wangzan101.mika.ui.components.MikaDialog
import com.wangzan101.mika.ui.components.RowChevron
import com.wangzan101.mika.ui.components.SheetAction
import com.wangzan101.mika.ui.components.keepsKeyboard
import com.wangzan101.mika.ui.theme.MikaTheme
import com.wangzan101.mika.ui.theme.MikaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun PackBackupSection() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tick by remember { mutableIntStateOf(0) }
    val packs = remember(tick) { PackStore.list(context) }
    var packMenu by remember { mutableStateOf(false) }
    var backupMenu by remember { mutableStateOf(false) }
    var uninstallId by remember { mutableStateOf<String?>(null) }
    var passwordMode by remember { mutableStateOf<PasswordMode?>(null) }
    var preview by remember { mutableStateOf<Pair<AssistantSnapshot, BackupPreview>?>(null) }
    var pendingExport by remember { mutableStateOf<ByteArray?>(null) }
    var note by remember { mutableStateOf<String?>(null) }

    val pickPack = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: return@launch
            val result = withContext(Dispatchers.IO) { PackStore.installZip(context, bytes) }
            note = when (result) {
                is PackCheck.Ok -> {
                    tick++
                    context.getString(R.string.packs_installed, result.manifest.displayName)
                }
                is PackCheck.Rejected -> context.getString(R.string.packs_rejected, result.reason)
            }
        }
    }
    val saveBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val blob = pendingExport ?: return@rememberLauncherForActivityResult
        pendingExport = null
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(blob) }; true }.getOrDefault(false)
            }
            note = context.getString(if (ok) R.string.backup_saved else R.string.backup_failed)
        }
    }
    val pickBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } ?: return@launch
            passwordMode = PasswordMode.Import(bytes)
        }
    }

    ListRow(null, stringResource(R.string.packs_title), stringResource(R.string.packs_sub), onClick = { packMenu = true }) {
        RowChevron()
    }
    ListRow(null, stringResource(R.string.backup_export), stringResource(R.string.backup_export_sub), onClick = { backupMenu = true }) {
        RowChevron()
    }
    LaunchedEffect(note) {
        val n = note ?: return@LaunchedEffect
        Toast.makeText(context, n, Toast.LENGTH_SHORT).show()
        note = null
    }

    if (packMenu) {
        ActionSheet(
            stringResource(R.string.packs_title),
            listOf(SheetAction(stringResource(R.string.packs_import)) {
                pickPack.launch(arrayOf("application/zip", "application/octet-stream"))
            }) + packs.map { pack ->
                SheetAction(stringResource(R.string.packs_uninstall, pack.displayName), destructive = true) { uninstallId = pack.id }
            },
            onDismiss = { packMenu = false },
        )
    }
    if (backupMenu) {
        ActionSheet(
            stringResource(R.string.backup_export),
            listOf(
                SheetAction(stringResource(R.string.backup_export)) { passwordMode = PasswordMode.Export },
                SheetAction(stringResource(R.string.backup_import)) { pickBackup.launch(arrayOf("application/octet-stream", "*/*")) },
            ),
            onDismiss = { backupMenu = false },
        )
    }
    LaunchedEffect(uninstallId) {
        val id = uninstallId ?: return@LaunchedEffect
        if (PackStore.enabledCount(context, id) == 0) {
            PackStore.uninstall(context, id, keepEnabled = false)
            tick++
            uninstallId = null
            note = context.getString(R.string.packs_removed)
        }
    }
    uninstallId?.takeIf { PackStore.enabledCount(context, it) > 0 }?.let { id ->
        ActionSheet(
            stringResource(R.string.packs_uninstall_title),
            listOf(
                SheetAction(stringResource(R.string.packs_keep_routines)) {
                    PackStore.uninstall(context, id, keepEnabled = true); tick++; uninstallId = null
                },
                SheetAction(stringResource(R.string.packs_stop_routines), destructive = true) {
                    PackStore.uninstall(context, id, keepEnabled = false); tick++; uninstallId = null
                },
            ),
            onDismiss = { uninstallId = null },
        )
    }
    passwordMode?.let { mode ->
        PasswordDialog(
            onDismiss = { passwordMode = null },
            onConfirm = { pw ->
                when (mode) {
                    PasswordMode.Export -> scope.launch {
                        val blob = withContext(Dispatchers.IO) { runCatching { BackupIO.export(context, pw.toCharArray()) }.getOrNull() }
                        passwordMode = null
                        if (blob == null) note = context.getString(R.string.backup_failed)
                        else {
                            pendingExport = blob
                            saveBackup.launch("mika-backup.mika")
                        }
                    }
                    is PasswordMode.Import -> scope.launch {
                        val opened = withContext(Dispatchers.IO) { BackupIO.importFile(context, mode.bytes, pw.toCharArray()) }
                        passwordMode = null
                        when (opened) {
                            is BackupOpen.Ready -> preview = opened.snapshot to opened.preview
                            is BackupOpen.Rejected -> note = context.getString(
                                if (opened.reason == "bad_password") R.string.backup_bad_password else R.string.backup_failed,
                            )
                        }
                    }
                }
            },
        )
    }
    preview?.let { (snap, counts) ->
        MikaDialog(
            onDismissRequest = { preview = null },
            title = { Text(stringResource(R.string.backup_import)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_preview,
                        counts.memories, counts.skills, counts.packs, counts.routines, counts.allow, counts.deny,
                    ),
                    style = MikaType.Body,
                )
            },
            confirmButton = {
                TextButton({
                    val s = snap
                    preview = null
                    scope.launch {
                        withContext(Dispatchers.IO) { BackupIO.apply(context, s) }
                        tick++
                        note = context.getString(R.string.backup_imported)
                    }
                }) { Text(stringResource(R.string.backup_import_confirm)) }
            },
            dismissButton = { TextButton({ preview = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

private sealed interface PasswordMode {
    data object Export : PasswordMode
    data class Import(val bytes: ByteArray) : PasswordMode
}

@Composable
private fun PasswordDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf("") }
    MikaDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.backup_password)) },
        text = {
            KeyboardDismissArea {
                Column {
                    Text(stringResource(R.string.backup_password_hint), style = MikaType.Caption, color = MikaTheme.colors.ink3)
                    OutlinedTextField(
                        value, { value = it }, Modifier.fillMaxWidth().keepsKeyboard(),
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        singleLine = true,
                    )
                }
            }
        },
        confirmButton = {
            TextButton({ onConfirm(value) }, enabled = value.isNotEmpty()) { Text(stringResource(R.string.ok_yes)) }
        },
        dismissButton = { TextButton(onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
