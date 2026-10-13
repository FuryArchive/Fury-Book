@file:Suppress("UnsafeCastFromDynamic")
package com.furybook.web

import com.furybook.dubl.acquisition.canonicalJson
import com.furybook.dubl.model.*

/** Strict migration-only browser legacy reader; the captured original source is never rewritten. */
internal fun decodeBrowserLegacyExtras(raw:String?):Map<String,CharacterSheetExtras> {
    if(raw==null) return emptyMap()
    val root=js("JSON.parse")(canonicalJson(raw))
    fun keys(value:dynamic):Set<String> {
        require(value!=null && js("typeof value === 'object' && !Array.isArray(value)") as Boolean) { "Expected extras object" }
        return (js("Object.keys")(value) as Array<String>).toSet()
    }
    require(keys(root)==setOf("version","characters")) { "Unknown legacy extras fields" }
    require(root.version==1) { "Unsupported legacy extras version" }
    val characters=root.characters
    val fields=setOf("portraitUri","conditions","hiddenResources","preferredAttributes","skillGroups","developmentGroups","conditionOverrides","customConditions","notes","noteEntries")
    fun text(row:dynamic,key:String):String? {
        val value=row[key]
        if(js("typeof value === 'undefined'") as Boolean) return null
        return value as? String ?: error("Expected encoded extras string: $key")
    }
    fun strings(row:dynamic,key:String):List<String> {
        val value=row[key]
        if(js("typeof value === 'undefined'") as Boolean) return emptyList()
        require(js("Array.isArray(value)") as Boolean) { "Expected extras array: $key" }
        val values=(value as Array<dynamic>).map { it as? String ?: error("Expected extras array string") }
        require(values.distinct().size==values.size) { "Duplicate extras set value" }
        return values
    }
    return keys(characters).associateWith { id ->
        require(id.isNotBlank()) { "Invalid extras character identity" }
        val row=characters[id];val names=keys(row)
        require(names.all { it in fields }) { "Unknown character extras field" }
        val portrait=if("portraitUri" !in names || row.portraitUri==null) null else row.portraitUri as? String ?: error("Invalid portrait URI")
        val conditions=strings(row,"conditions").map { name -> CharacterConditionId.entries.singleOrNull { it.name==name } ?: error("Unknown condition") }.toSet()
        val hidden=strings(row,"hiddenResources").map { name -> CharacterSheetResourceId.entries.singleOrNull { it.name==name } ?: error("Unknown resource") }.toSet()
        val preferred=if("preferredAttributes" !in names) emptyMap() else keys(row.preferredAttributes).associateWith { key ->
            val name=row.preferredAttributes[key] as? String ?: error("Invalid preferred attribute")
            AttributeId.entries.singleOrNull { it.name==name } ?: error("Unknown preferred attribute")
        }
        val extras=CharacterSheetExtras(portraitUri=portrait,activeConditions=conditions,hiddenResourceIds=hidden,preferredSkillAttributes=preferred,
            skillGroups=SheetGroupingRules.decode(text(row,"skillGroups")),developmentGroups=SheetGroupingRules.decode(text(row,"developmentGroups")),
            conditionOverrides=ConditionLocalDataCodec.decodeOverrides(text(row,"conditionOverrides")),customConditions=ConditionLocalDataCodec.decodeCustom(text(row,"customConditions")),
            notes=text(row,"notes").orEmpty(),noteEntries=CharacterNoteDataCodec.decode(text(row,"noteEntries")))
        mapOf("skillGroups" to SheetGroupingRules.encode(extras.skillGroups),"developmentGroups" to SheetGroupingRules.encode(extras.developmentGroups),
            "conditionOverrides" to ConditionLocalDataCodec.encodeOverrides(extras.conditionOverrides),"customConditions" to ConditionLocalDataCodec.encodeCustom(extras.customConditions),
            "noteEntries" to CharacterNoteDataCodec.encode(extras.noteEntries)).forEach { (key,encoded) ->
                require(text(row,key).orEmpty()==encoded) { "Legacy extras $key would lose values" }
            }
        extras
    }
}
