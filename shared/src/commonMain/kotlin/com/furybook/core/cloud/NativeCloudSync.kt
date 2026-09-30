package com.furybook.core.cloud

import com.furybook.core.json.JsonValue
import com.furybook.core.json.array
import com.furybook.core.json.asObject
import com.furybook.core.json.asString
import com.furybook.core.json.bool
import com.furybook.core.json.int
import com.furybook.core.json.jsonArray
import com.furybook.core.json.jsonBoolean
import com.furybook.core.json.jsonNumber
import com.furybook.core.json.jsonObject
import com.furybook.core.json.jsonString
import com.furybook.core.json.jsonStringify
import com.furybook.core.json.objectValue
import com.furybook.core.json.parseRoot
import com.furybook.core.json.string
import com.furybook.dubl.data.CharacterExtrasStore
import com.furybook.dubl.data.CharacterStore
import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterNoteDataCodec
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.ConditionLocalDataCodec
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.SheetGroupingRules

interface FuryCloudTransport {
    suspend fun rpc(function: String, jsonBody: String): String
}

data class CloudCharacter(
    val id: String,
    val dataJson: String,
    val extrasJson: String,
    val revision: Int,
    val updatedAt: String,
    val updatedBy: String,
)

data class CloudBootstrap(
    val nickname: String,
    val activeCharacterId: String,
    val enabledPackIds: Set<String>,
    val stateRevision: Int,
    val characters: List<CloudCharacter>,
)

data class NativeSyncConflict(
    val characterId: String,
    val serverRevision: Int,
    val serverDataJson: String?,
    val serverExtrasJson: String?,
    val updatedAt: String?,
    val updatedBy: String?,
    val deleteRequested: Boolean,
)

data class NativeSyncResponse(
    val bootstrap: CloudBootstrap,
    val conflicts: List<NativeSyncConflict>,
)

data class NativeCloudMetadata(
    val linkedUserId: String = "",
    val knownRevisions: Map<String, Int> = emptyMap(),
    val stateRevision: Int = 0,
    val dirtyIds: Set<String> = emptySet(),
    val deletedIds: Set<String> = emptySet(),
)

interface NativeCloudMetadataStore {
    fun load(): NativeCloudMetadata
    fun save(metadata: NativeCloudMetadata)
    fun clear()
}

sealed interface NativeSyncOutcome {
    data class Synced(val bootstrap: CloudBootstrap) : NativeSyncOutcome
    data class Conflicts(val items: List<NativeSyncConflict>) : NativeSyncOutcome
}

class FuryNativeCloudApi(private val transport: FuryCloudTransport) {
    suspend fun bootstrap(): CloudBootstrap =
        CloudProtocol.parseBootstrap(transport.rpc("get_web_bootstrap", "{}"))

    suspend fun link(
        snapshot: AppSnapshot,
        extras: Map<String, CharacterSheetExtras>,
        enabledPackIds: Set<String>,
        device: String,
    ): CloudBootstrap {
        val body = jsonObject(
            "p_snapshot" to jsonString(SnapshotCodec.encode(snapshot)),
            "p_extras" to CloudProtocol.extrasValue(extras),
            "p_pack_state" to CloudProtocol.packStateValue(enabledPackIds),
            "p_device" to jsonString(device),
        )
        return CloudProtocol.parseBootstrap(transport.rpc("native_link_device", jsonStringify(body)))
    }

    suspend fun sync(
        snapshot: AppSnapshot,
        extras: Map<String, CharacterSheetExtras>,
        dirtyIds: Set<String>,
        deletedIds: Set<String>,
        knownRevisions: Map<String, Int>,
        enabledPackIds: Set<String>,
        knownStateRevision: Int,
        device: String,
    ): NativeSyncResponse {
        val body = jsonObject(
            "p_snapshot" to jsonString(SnapshotCodec.encode(snapshot)),
            "p_extras" to CloudProtocol.extrasValue(extras),
            "p_dirty_ids" to jsonArray(dirtyIds.map(::jsonString)),
            "p_deleted_ids" to jsonArray(deletedIds.map(::jsonString)),
            "p_known_revisions" to JsonValue.Obj(knownRevisions.mapValues { jsonNumber(it.value) }),
            "p_pack_state" to CloudProtocol.packStateValue(enabledPackIds),
            "p_known_state_revision" to jsonNumber(knownStateRevision),
            "p_device" to jsonString(device),
        )
        return CloudProtocol.parseSyncResponse(transport.rpc("native_sync_snapshot", jsonStringify(body)))
    }

    suspend fun force(
        conflict: NativeSyncConflict,
        localCharacterJson: String,
        localExtrasJson: String,
        device: String,
    ): String {
        val body = jsonObject(
            "p_character_id" to jsonString(conflict.characterId),
            "p_data" to parseRoot(localCharacterJson),
            "p_extras" to parseRoot(localExtrasJson),
            "p_server_revision" to jsonNumber(conflict.serverRevision),
            "p_device" to jsonString(device),
            "p_delete" to jsonBoolean(conflict.deleteRequested),
        )
        return transport.rpc("native_force_character", jsonStringify(body))
    }
}

class NativeCloudSyncCoordinator(
    private val userId: String,
    private val characterStore: CharacterStore,
    private val extrasStore: CharacterExtrasStore,
    private val metadataStore: NativeCloudMetadataStore,
    private val api: FuryNativeCloudApi,
    private val idFactory: () -> String,
    private val deviceLabel: String,
) {
    fun markSnapshotChanged(before: AppSnapshot, after: AppSnapshot) {
        val old = before.characters.associateBy { it.id }
        val next = after.characters.associateBy { it.id }
        val changed = next.values.filter { old[it.id] != it }.mapTo(linkedSetOf()) { it.id }
        val deleted = old.keys.filterTo(linkedSetOf()) { it !in next }

        val current = metadataStore.load()
        metadataStore.save(
            current.copy(
                dirtyIds = (current.dirtyIds + changed) - deleted,
                deletedIds = (current.deletedIds + deleted) - changed,
            ),
        )
    }

    fun markExtrasChanged(characterId: String) {
        val current = metadataStore.load()
        metadataStore.save(
            current.copy(
                dirtyIds = current.dirtyIds + characterId,
                deletedIds = current.deletedIds - characterId,
            ),
        )
    }

    suspend fun linkOrResume(enabledPackIds: Set<String>): NativeSyncOutcome {
        val local = characterStore.load()
        val localExtras = loadExtras(local)
        val metadata = metadataStore.load()
        val cloud = api.bootstrap()

        if (metadata.linkedUserId == userId) {
            return syncNow(enabledPackIds)
        }

        if (cloud.characters.isNotEmpty() && isPristineLocal(local, localExtras)) {
            applyBootstrap(cloud)
            saveCleanMetadata(cloud)
            return NativeSyncOutcome.Synced(cloud)
        }

        val merged = api.link(local, localExtras, enabledPackIds, deviceLabel)
        applyBootstrap(merged)
        saveCleanMetadata(merged)
        return NativeSyncOutcome.Synced(merged)
    }

    suspend fun syncNow(enabledPackIds: Set<String>): NativeSyncOutcome {
        var metadata = metadataStore.load()
        if (metadata.linkedUserId != userId) return linkOrResume(enabledPackIds)

        val local = characterStore.load()
        val response = api.sync(
            snapshot = local,
            extras = loadExtras(local),
            dirtyIds = metadata.dirtyIds,
            deletedIds = metadata.deletedIds,
            knownRevisions = metadata.knownRevisions,
            enabledPackIds = enabledPackIds,
            knownStateRevision = metadata.stateRevision,
            device = deviceLabel,
        )

        if (response.conflicts.isNotEmpty()) {
            return NativeSyncOutcome.Conflicts(response.conflicts)
        }

        applyBootstrap(response.bootstrap)
        saveCleanMetadata(response.bootstrap)
        return NativeSyncOutcome.Synced(response.bootstrap)
    }

    suspend fun useCloud(enabledPackIds: Set<String>): NativeSyncOutcome {
        val cloud = api.bootstrap()
        applyBootstrap(cloud)
        saveCleanMetadata(cloud)
        return NativeSyncOutcome.Synced(cloud)
    }

    suspend fun keepLocal(conflict: NativeSyncConflict, enabledPackIds: Set<String>): NativeSyncOutcome {
        val local = characterStore.load()
        val characterJson = if (conflict.deleteRequested) {
            "{}"
        } else {
            CloudProtocol.characterJson(local, conflict.characterId)
                ?: return useCloud(enabledPackIds)
        }
        val extrasJson = if (conflict.deleteRequested) {
            "{}"
        } else {
            CloudProtocol.extrasJson(extrasStore.load(conflict.characterId))
        }
        api.force(conflict, characterJson, extrasJson, deviceLabel)
        val current = metadataStore.load()
        metadataStore.save(
            current.copy(
                dirtyIds = current.dirtyIds - conflict.characterId,
                deletedIds = current.deletedIds - conflict.characterId,
            ),
        )
        return syncNow(enabledPackIds)
    }

    fun unlinkPreservingLocal() {
        metadataStore.clear()
    }

    private fun loadExtras(snapshot: AppSnapshot): Map<String, CharacterSheetExtras> =
        snapshot.characters.associate { it.id to extrasStore.load(it.id) }

    private fun applyBootstrap(bootstrap: CloudBootstrap) {
        if (bootstrap.characters.isEmpty()) return
        val snapshot = CloudProtocol.toSnapshot(bootstrap, idFactory)
        val oldIds = characterStore.load().characters.mapTo(linkedSetOf()) { it.id }
        characterStore.save(snapshot)

        val newIds = snapshot.characters.mapTo(linkedSetOf()) { it.id }
        (oldIds - newIds).forEach(extrasStore::delete)
        bootstrap.characters.forEach { cloudCharacter ->
            extrasStore.save(cloudCharacter.id, CloudProtocol.decodeExtras(cloudCharacter.extrasJson))
        }
    }

    private fun saveCleanMetadata(bootstrap: CloudBootstrap) {
        metadataStore.save(
            NativeCloudMetadata(
                linkedUserId = userId,
                knownRevisions = bootstrap.characters.associate { it.id to it.revision },
                stateRevision = bootstrap.stateRevision,
                dirtyIds = emptySet(),
                deletedIds = emptySet(),
            ),
        )
    }

    private fun isPristineLocal(
        snapshot: AppSnapshot,
        extras: Map<String, CharacterSheetExtras>,
    ): Boolean {
        if (snapshot.characters.size != 1) return false
        val character = snapshot.characters.single()
        val blank = DublCharacter(id = character.id, name = "Новый персонаж")
        return character == blank && extras[character.id] == CharacterSheetExtras()
    }
}

internal object CloudProtocol {
    fun parseBootstrap(raw: String): CloudBootstrap {
        val root = parseRoot(raw)
        return parseBootstrap(root)
    }

    fun parseSyncResponse(raw: String): NativeSyncResponse {
        val root = parseRoot(raw)
        val bootstrap = root.objectValue("bootstrap")?.let(::parseBootstrap)
            ?: error("Cloud response is missing bootstrap")
        val conflicts = root.array("conflicts").mapNotNull { value ->
            val item = value.asObject() ?: return@mapNotNull null
            NativeSyncConflict(
                characterId = item.string("characterId"),
                serverRevision = item.int("serverRevision", 0),
                serverDataJson = item.objectValue("serverData")?.let(::jsonStringify),
                serverExtrasJson = item.objectValue("serverExtras")?.let(::jsonStringify),
                updatedAt = item.string("updatedAt").takeIf(String::isNotBlank),
                updatedBy = item.string("updatedBy").takeIf(String::isNotBlank),
                deleteRequested = item.bool("deleteRequested", false),
            )
        }
        return NativeSyncResponse(bootstrap, conflicts)
    }

    fun toSnapshot(bootstrap: CloudBootstrap, idFactory: () -> String): AppSnapshot {
        val characters = bootstrap.characters.map { JsonReaderCompat.parse(it.dataJson) }
        val active = bootstrap.activeCharacterId.takeIf { id -> bootstrap.characters.any { it.id == id } }
            ?: bootstrap.characters.firstOrNull()?.id.orEmpty()
        val raw = jsonStringify(
            jsonObject(
                "schema" to jsonNumber(SnapshotCodec.SCHEMA),
                "activeCharacterId" to jsonString(active),
                "characters" to jsonArray(characters),
            ),
        )
        return SnapshotCodec.decode(raw, idFactory)
    }

    fun characterJson(snapshot: AppSnapshot, id: String): String? {
        val root = parseRoot(SnapshotCodec.encode(snapshot))
        val item = root.array("characters")
            .mapNotNull { it.asObject() }
            .firstOrNull { it.string("id") == id }
            ?: return null
        return jsonStringify(item)
    }

    fun packStateValue(enabledPackIds: Set<String>): JsonValue.Obj = jsonObject(
        "version" to jsonNumber(2),
        "enabledPackIds" to jsonArray(enabledPackIds.sorted().map(::jsonString)),
    )

    private fun parseEnabledPackIds(pack: JsonValue.Obj?): Set<String> {
        if (pack == null) return emptySet()
        val ids = pack.array("enabledPackIds").mapNotNull { it.asString() }.filter(String::isNotBlank).toSet()
        if (ids.isNotEmpty()) return ids
        // Backward compatibility with Fury Book 0.6 cloud state.
        return if (pack.bool("chiEnabled", false)) setOf("dubl-chi-3.69") else emptySet()
    }

    fun extrasValue(extras: Map<String, CharacterSheetExtras>): JsonValue.Obj =
        jsonObject(
            "version" to jsonNumber(1),
            "characters" to JsonValue.Obj(extras.mapValues { (_, value) -> extrasObject(value) }),
        )

    fun extrasJson(extras: CharacterSheetExtras): String = jsonStringify(extrasObject(extras))

    fun decodeExtras(raw: String): CharacterSheetExtras {
        val root = parseRoot(raw)
        val conditions = root.array("conditions").mapNotNull { it.asString() }
            .mapNotNull { name -> CharacterConditionId.entries.firstOrNull { it.name == name } }
            .toSet()
        val hidden = root.array("hiddenResources").mapNotNull { it.asString() }
            .mapNotNull { name -> CharacterSheetResourceId.entries.firstOrNull { it.name == name } }
            .toSet()
        val preferred = root.objectValue("preferredAttributes")?.values.orEmpty().mapNotNull { (skillId, value) ->
            val attr = value.asString()?.let { rawName -> AttributeId.entries.firstOrNull { it.name == rawName } }
            attr?.let { skillId to it }
        }.toMap()
        return CharacterSheetExtras(
            portraitUri = root.string("portraitUri").takeIf(String::isNotBlank),
            activeConditions = conditions,
            hiddenResourceIds = hidden,
            preferredSkillAttributes = preferred,
            skillGroups = SheetGroupingRules.decode(root.string("skillGroups")),
            developmentGroups = SheetGroupingRules.decode(root.string("developmentGroups")),
            conditionOverrides = ConditionLocalDataCodec.decodeOverrides(root.string("conditionOverrides")),
            customConditions = ConditionLocalDataCodec.decodeCustom(root.string("customConditions")),
            notes = root.string("notes"),
            noteEntries = CharacterNoteDataCodec.decode(root.string("noteEntries")),
        )
    }

    private fun parseBootstrap(root: JsonValue.Obj): CloudBootstrap {
        val profile = root.objectValue("profile")
        val state = root.objectValue("state")
        val pack = state?.objectValue("packState")
        val characters = root.array("characters").mapNotNull { value ->
            val item = value.asObject() ?: return@mapNotNull null
            val data = item.objectValue("data") ?: return@mapNotNull null
            val extras = item.objectValue("extras") ?: jsonObject()
            val id = item.string("id").ifBlank { data.string("id") }
            if (id.isBlank()) return@mapNotNull null
            CloudCharacter(
                id = id,
                dataJson = jsonStringify(data),
                extrasJson = jsonStringify(extras),
                revision = item.int("revision", 0),
                updatedAt = item.string("updatedAt"),
                updatedBy = item.string("updatedBy"),
            )
        }
        return CloudBootstrap(
            nickname = profile?.string("nickname").orEmpty(),
            activeCharacterId = state?.string("activeCharacterId").orEmpty(),
            enabledPackIds = parseEnabledPackIds(pack),
            stateRevision = state?.int("revision", 0) ?: 0,
            characters = characters,
        )
    }

    private fun extrasObject(extras: CharacterSheetExtras): JsonValue.Obj =
        jsonObject(
            "portraitUri" to extras.portraitUri?.let(::jsonString),
            "conditions" to jsonArray(extras.activeConditions.map { jsonString(it.name) }),
            "hiddenResources" to jsonArray(extras.hiddenResourceIds.map { jsonString(it.name) }),
            "preferredAttributes" to JsonValue.Obj(extras.preferredSkillAttributes.mapValues { jsonString(it.value.name) }),
            "skillGroups" to jsonString(SheetGroupingRules.encode(extras.skillGroups)),
            "developmentGroups" to jsonString(SheetGroupingRules.encode(extras.developmentGroups)),
            "conditionOverrides" to jsonString(ConditionLocalDataCodec.encodeOverrides(extras.conditionOverrides)),
            "customConditions" to jsonString(ConditionLocalDataCodec.encodeCustom(extras.customConditions)),
            "notes" to jsonString(extras.notes),
            "noteEntries" to jsonString(CharacterNoteDataCodec.encode(extras.noteEntries)),
        )
}

private object JsonReaderCompat {
    fun parse(raw: String): JsonValue = parseRoot(raw)
}
