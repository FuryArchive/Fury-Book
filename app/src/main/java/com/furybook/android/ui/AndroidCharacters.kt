package com.furybook.android.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.furybook.android.data.AndroidContentPackState
import com.furybook.android.data.AndroidFcpInstaller
import com.furybook.dubl.content.DublChiFcp
import com.furybook.dubl.ui.characters.DublPackRow
import com.furybook.dubl.ui.characters.DublPacksSection
import java.nio.charset.StandardCharsets

/** Android's document pickers for Персонажи: open a character file, save one. Cancelling calls nothing back. */
internal class AndroidCharacterFiles(
    val importFile: (onRead: (String?) -> Unit) -> Unit,
    val exportFile: (fileName: String, content: String, onSaved: (Boolean) -> Unit) -> Unit,
)

@Composable
internal fun rememberAndroidCharacterFiles(): AndroidCharacterFiles {
    val context = LocalContext.current
    var pendingRead by remember { mutableStateOf<((String?) -> Unit)?>(null) }
    var pendingSave by remember { mutableStateOf<Pair<String, (Boolean) -> Unit>?>(null) }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val onRead = pendingRead
        pendingRead = null
        if (uri != null && onRead != null) onRead(readText(context, uri))
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val pending = pendingSave
        pendingSave = null
        if (uri != null && pending != null) pending.second(writeText(context, uri, pending.first))
    }
    return remember(importLauncher, exportLauncher) {
        AndroidCharacterFiles(
            importFile = { onRead -> pendingRead = onRead; importLauncher.launch(arrayOf("*/*")) },
            exportFile = { fileName, content, onSaved -> pendingSave = content to onSaved; exportLauncher.launch(fileName) },
        )
    }
}

/** «Пакеты контента» on Android: bundled and installed .fcp, on/off (synced to the account) and import. */
@Composable
internal fun rememberAndroidPacks(chiPackEnabled: Boolean, onActiveChange: (String, Boolean) -> Unit): DublPacksSection {
    val context = LocalContext.current
    var status by remember { mutableStateOf<String?>(null) }
    var installedRevision by remember { mutableIntStateOf(0) }
    val fcpLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            status = runCatching {
                val result = AndroidFcpInstaller.install(context = context, uri = uri, reservedPackIds = AndroidContentPackState.bundledPackIds())
                AndroidContentPackState.setPackEnabled(context, result.manifest.id, false)
                installedRevision += 1
                "Установлен FCP: ${result.manifest.name} v${result.manifest.version}."
            }.getOrElse { "Ошибка импорта FCP: ${it.message ?: "неизвестная ошибка"}" }
        }
    }
    @Suppress("UNUSED_EXPRESSION") installedRevision
    val composition = AndroidContentPackState.composition(context, chiPackEnabled)
    val rows = composition.available.map { manifest ->
        val required = manifest.id in composition.requiredPackIds
        val canActivate = AndroidContentPackState.canActivatePack(manifest)
        val note = when {
            required -> "Основной ruleset · обязателен"
            !canActivate -> "Установлен · поддержка в приложении пока отсутствует"
            manifest.id !in AndroidContentPackState.bundledPackIds() -> "DUBL data-only · дополнительное развитие"
            manifest.dependencies.isEmpty() -> "Опциональный FCP"
            else -> "Опциональный FCP · зависит от ${manifest.dependencies.joinToString { it.id }}"
        }
        DublPackRow(manifest.id, manifest.name, "$note · v${manifest.version}", composition.isActive(manifest.id), locked = required || !canActivate)
    }
    return DublPacksSection(
        rows = rows,
        onToggle = { id, enabled ->
            val name = composition.available.firstOrNull { it.id == id }?.name ?: id
            status = runCatching {
                onActiveChange(id, enabled)
                if (enabled) "$name включён." else "$name выключен."
            }.getOrElse { "Ошибка FCP: ${it.message ?: "неизвестная ошибка"}" }
        },
        onImport = { fcpLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
        status = status,
    )
}

private fun writeText(context: Context, uri: Uri, raw: String): Boolean = runCatching {
    val stream = context.contentResolver.openOutputStream(uri) ?: error("Cannot open output stream")
    stream.bufferedWriter(StandardCharsets.UTF_8).use { it.write(raw) }
}.isSuccess

private fun readText(context: Context, uri: Uri): String? = runCatching {
    val stream = context.contentResolver.openInputStream(uri) ?: error("Cannot open input stream")
    stream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
}.getOrNull()
