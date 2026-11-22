package com.furybook.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.furybook.desktop.cloud.DesktopCloudController
import com.furybook.dubl.ui.characters.DublPackRow
import com.furybook.dubl.ui.characters.DublPacksSection

/** «Пакеты контента» on desktop: the installed .fcp list, on/off (synced to the account) and import. */
@Composable
internal fun rememberDesktopPacks(state: DesktopAppState, cloud: DesktopCloudController): DublPacksSection {
    var status by remember { mutableStateOf<String?>(null) }
    val composition = state.contentPackComposition
    val rows = composition.available.map { manifest ->
        val required = manifest.id in composition.requiredPackIds
        val canActivate = state.canActivateContentPack(manifest)
        val note = when {
            required -> "Основной ruleset · обязателен"
            !canActivate -> "Установлен · поддержка в приложении пока отсутствует"
            manifest.id != state.corePackManifest.id && manifest.id != state.chiPackManifest.id -> "DUBL data-only · дополнительное развитие"
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
                state.setContentPackActive(id, enabled)
                cloud.updateEnabledPackIds(cloud.enabledPackIds.toMutableSet().apply { if (enabled) add(id) else remove(id) })
                cloud.requestSync()
                if (enabled) "$name включён." else "$name выключен."
            }.getOrElse { "Ошибка FCP: ${it.message ?: "неизвестная ошибка"}" }
        },
        onImport = {
            DesktopFiles.pickFcp()?.let { file ->
                status = runCatching { state.installContentPack(file).let { "Установлен FCP: ${it.name} v${it.version}." } }
                    .getOrElse { "Ошибка импорта FCP: ${it.message ?: "неизвестная ошибка"}" }
            }
        },
        status = status,
    )
}
