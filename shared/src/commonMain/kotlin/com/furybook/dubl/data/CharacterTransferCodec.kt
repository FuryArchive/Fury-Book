package com.furybook.dubl.data

import com.furybook.core.json.*
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.ConditionLocalDataCodec
import com.furybook.dubl.model.DublCharacter
import com.furybook.dubl.model.DublRuleset
import com.furybook.dubl.model.SheetGroupingRules

enum class CharacterTransferRejectReason {
    INVALID_FILE,
    UNSUPPORTED_FORMAT_VERSION,
    UNSUPPORTED_RULESET,
}

data class CharacterTransferPayload(
    val character: DublCharacter,
    val extras: CharacterSheetExtras,
)

sealed interface CharacterTransferDecodeResult {
    data class Success(val payload: CharacterTransferPayload) : CharacterTransferDecodeResult
    data class Failure(val reason: CharacterTransferRejectReason) : CharacterTransferDecodeResult
}

object CharacterTransferCodec {
    const val FORMAT = "dubl.character"
    const val VERSION = 1

    fun encode(character: DublCharacter, extras: CharacterSheetExtras): String {
        val snapshot = SnapshotCodec.encode(AppSnapshot(listOf(character), character.id))
        return jsonStringify(
            jsonObject(
                "format" to jsonString(FORMAT),
                "version" to jsonNumber(VERSION),
                "characterSnapshot" to parseRoot(snapshot),
                "extras" to encodeExtras(extras),
            ),
        )
    }

    fun decode(raw: String, idFactory: () -> String): CharacterTransferDecodeResult = runCatching {
        val root = parseRoot(raw)
        if (root.string("format") != FORMAT) {
            return CharacterTransferDecodeResult.Failure(CharacterTransferRejectReason.INVALID_FILE)
        }
        if (root.int("version", -1) != VERSION) {
            return CharacterTransferDecodeResult.Failure(CharacterTransferRejectReason.UNSUPPORTED_FORMAT_VERSION)
        }

        val snapshotRoot = root.objectValue("characterSnapshot")
            ?: return CharacterTransferDecodeResult.Failure(CharacterTransferRejectReason.INVALID_FILE)
        if (snapshotRoot.int("schema", 1) > SnapshotCodec.SCHEMA) {
            return CharacterTransferDecodeResult.Failure(CharacterTransferRejectReason.UNSUPPORTED_FORMAT_VERSION)
        }
        if (snapshotRoot.array("characters").size != 1) {
            return CharacterTransferDecodeResult.Failure(CharacterTransferRejectReason.INVALID_FILE)
        }
        val extrasRoot = root.objectValue("extras")
            ?: return CharacterTransferDecodeResult.Failure(CharacterTransferRejectReason.INVALID_FILE)

        val snapshot = SnapshotCodec.decode(jsonStringify(snapshotRoot), idFactory)
        if (snapshot.characters.size != 1) {
            return CharacterTransferDecodeResult.Failure(CharacterTransferRejectReason.INVALID_FILE)
        }
        val character = snapshot.characters.single()
        if (character.ruleset != DublRuleset.reference) {
            return CharacterTransferDecodeResult.Failure(CharacterTransferRejectReason.UNSUPPORTED_RULESET)
        }

        CharacterTransferDecodeResult.Success(
            CharacterTransferPayload(
                character = character,
                extras = decodeExtras(extrasRoot),
            ),
        )
    }.getOrElse {
        CharacterTransferDecodeResult.Failure(CharacterTransferRejectReason.INVALID_FILE)
    }

    private fun encodeExtras(extras: CharacterSheetExtras): JsonValue.Obj = jsonObject(
        "activeConditions" to jsonArray(extras.activeConditions.map { it.name }.sorted().map(::jsonString)),
        "hiddenResourceIds" to jsonArray(extras.hiddenResourceIds.map { it.name }.sorted().map(::jsonString)),
        "preferredSkillAttributes" to JsonValue.Obj(linkedMapOf<String, JsonValue>().apply {
            extras.preferredSkillAttributes.entries.sortedBy { it.key }.forEach { entry ->
                put(entry.key, jsonString(entry.value.name))
            }
        }),
        "skillGroups" to jsonString(SheetGroupingRules.encode(extras.skillGroups)),
        "developmentGroups" to jsonString(SheetGroupingRules.encode(extras.developmentGroups)),
        "conditionOverrides" to jsonString(ConditionLocalDataCodec.encodeOverrides(extras.conditionOverrides)),
        "customConditions" to jsonString(ConditionLocalDataCodec.encodeCustom(extras.customConditions)),
        "notes" to jsonString(extras.notes),
    )

    private fun decodeExtras(root: JsonValue.Obj): CharacterSheetExtras = CharacterSheetExtras(
        portraitUri = null,
        activeConditions = root.stringList("activeConditions")
            .mapNotNull { name -> CharacterConditionId.entries.firstOrNull { it.name == name } }
            .toSet(),
        hiddenResourceIds = root.stringList("hiddenResourceIds")
            .mapNotNull { name -> CharacterSheetResourceId.entries.firstOrNull { it.name == name } }
            .toSet(),
        preferredSkillAttributes = root.stringMap("preferredSkillAttributes").mapNotNull { (skillId, attributeName) ->
            AttributeId.entries.firstOrNull { it.name == attributeName }?.let { skillId to it }
        }.toMap(),
        skillGroups = SheetGroupingRules.decode(root.string("skillGroups")),
        developmentGroups = SheetGroupingRules.decode(root.string("developmentGroups")),
        conditionOverrides = ConditionLocalDataCodec.decodeOverrides(root.string("conditionOverrides")),
        customConditions = ConditionLocalDataCodec.decodeCustom(root.string("customConditions")),
        notes = root.string("notes"),
    )
}
