package com.furybook.android.data

import com.furybook.dubl.data.CharacterExtrasStore
import android.content.Context
import com.furybook.dubl.model.AttributeId
import com.furybook.dubl.model.CharacterConditionId
import com.furybook.dubl.model.ConditionLocalDataCodec
import com.furybook.dubl.model.CharacterNoteDataCodec
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.CharacterSheetResourceId
import com.furybook.dubl.model.SheetGroupingRules

class CharacterSheetExtrasRepository(
    context: Context,
    private val source: AndroidLegacyAcquisitionSource = AndroidLegacyAcquisitionSource(context.applicationContext.dataDir.toPath().resolve("shared_prefs")),
) : CharacterExtrasStore {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, CharacterSheetExtras>()
    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences(
        "dubl_character_sheet_extras",
        Context.MODE_PRIVATE,
    ) }

    internal fun initialize(captured: AndroidLegacyCapture, characterIds: List<String>) {
        val values=characterIds.associateWith(captured::extras)
        check(typedPreferences(prefs.all)==captured.extrasEntries) { "Legacy extras memory/disk disagreement" }
        cache.clear();cache.putAll(values)
    }
    internal fun memoryValues():Map<String,*> = prefs.all
    override fun load(characterId:String):CharacterSheetExtras = cache[characterId] ?: CharacterSheetExtras()

    override fun save(characterId: String, extras: CharacterSheetExtras) = source.gate.legacyWrite {
        val prefix = prefix(characterId)
        prefs.edit()
            .putString("${prefix}portrait", extras.portraitUri)
            .putStringSet("${prefix}conditions", extras.activeConditions.map { it.name }.toSet())
            .putStringSet("${prefix}hidden_resources", extras.hiddenResourceIds.map { it.name }.toSet())
            .putString("${prefix}skill_groups", SheetGroupingRules.encode(extras.skillGroups))
            .putString("${prefix}development_groups", SheetGroupingRules.encode(extras.developmentGroups))
            .putString("${prefix}condition_overrides", ConditionLocalDataCodec.encodeOverrides(extras.conditionOverrides))
            .putString("${prefix}custom_conditions", ConditionLocalDataCodec.encodeCustom(extras.customConditions))
            .putString("${prefix}notes", extras.notes)
            .putString("${prefix}note_entries", CharacterNoteDataCodec.encode(extras.noteEntries))
            .putStringSet(
                "${prefix}preferred_skill_attributes",
                extras.preferredSkillAttributes.map { (skillId, attribute) ->
                    "$skillId$PREFERRED_ATTRIBUTE_SEPARATOR${attribute.name}"
                }.toSet(),
            )
            .remove("${prefix}favorite_skill_ids") // 0.6: all learned skills are shown; favorites are retired.
            .remove("${prefix}favorites") // v4 pre-skill favorites were stats/attributes and are intentionally discarded.
            .apply()
        cache[characterId] = extras
    }

    override fun delete(characterId: String) = source.gate.legacyWrite {
        val prefix = prefix(characterId)
        prefs.edit()
            .remove("${prefix}portrait")
            .remove("${prefix}conditions")
            .remove("${prefix}hidden_resources")
            .remove("${prefix}skill_groups")
            .remove("${prefix}development_groups")
            .remove("${prefix}condition_overrides")
            .remove("${prefix}custom_conditions")
            .remove("${prefix}notes")
            .remove("${prefix}note_entries")
            .remove("${prefix}preferred_skill_attributes")
            .remove("${prefix}favorite_skill_ids")
            .remove("${prefix}favorites")
            .apply()
        cache.remove(characterId)
        Unit
    }

    private fun prefix(characterId: String): String = "character.$characterId."

    private companion object {
        const val PREFERRED_ATTRIBUTE_SEPARATOR = "::"
    }
}
