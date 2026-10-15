package com.furybook.dubl.acquisition

import com.furybook.dubl.model.*

internal enum class AcquisitionDoubleLeaf { ManualLoad, ItemLoad }

/** Opaque derived lexical evidence. No JSON fields, arbitrary paths, or caller-supplied trust. */
class AcquisitionDoubleToken private constructor(
    private val text:String,private val bits:Long,private val leaf:AcquisitionDoubleLeaf,private val itemUid:String?,
) {
    internal fun textIfValid(value:Double,kind:AcquisitionDoubleLeaf,uid:String?):String? =
        text.takeIf { leaf==kind && itemUid==uid && value.isFinite() && value.toRawBits()==bits &&
            NUMBER.matches(it) && it.toDoubleOrNull()?.toRawBits()==bits &&
            (value!=0.0 || it.substringBefore('e').substringBefore('E').none { c -> c in '1'..'9' }) }
    companion object {
        private val NUMBER=Regex("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?")
        internal fun capture(text:String,value:Double,leaf:AcquisitionDoubleLeaf,uid:String?=null):AcquisitionDoubleToken {
            require(NUMBER.matches(text)) { "Invalid acquisition Double token" }
            val decoded=text.toDoubleOrNull() ?: error("Invalid acquisition Double")
            require(decoded.isFinite() && decoded.toRawBits()==value.toRawBits()) { "Acquisition Double token differs from value" }
            val coefficient=text.substringBefore('e').substringBefore('E')
            require(decoded!=0.0 || coefficient.none { it in '1'..'9' }) { "Acquisition Double token underflows to zero" }
            require((leaf==AcquisitionDoubleLeaf.ItemLoad)==(uid!=null)) { "Invalid acquisition Double owner" }
            return AcquisitionDoubleToken(text,value.toRawBits(),leaf,uid)
        }
    }
}

internal fun acquisitionDoubleText(value:Double,evidence:AcquisitionDoubleToken?,kind:AcquisitionDoubleLeaf,uid:String?=null):String =
    evidence?.textIfValid(value,kind,uid) ?: acquisitionDoubleText(value)

/** Discard mismatches at every canonical/captured staging boundary. Never resurrect discarded data. */
internal fun reconcileAcquisitionDoubleTokens(character:DublCharacter,previous:DublCharacter?=null):DublCharacter {
    val gear=character.gear
    fun retained(value:Double,current:AcquisitionDoubleToken?,old:AcquisitionDoubleToken?,kind:AcquisitionDoubleLeaf,uid:String?):AcquisitionDoubleToken? {
        if(current!=null) return current.takeIf { it.textIfValid(value,kind,uid)!=null }
        return old?.takeIf { it.textIfValid(value,kind,uid)!=null }
    }
    val manual=retained(gear.loadManual,gear.acquisitionLoadToken,previous?.gear?.acquisitionLoadToken,AcquisitionDoubleLeaf.ManualLoad,null)
    val items=gear.items.map { item ->
        val old=previous?.gear?.items?.singleOrNull { it.uid==item.uid }
        val token=retained(item.load,item.acquisitionLoadToken,old?.acquisitionLoadToken,AcquisitionDoubleLeaf.ItemLoad,item.uid)
        if(token===item.acquisitionLoadToken) item else item.copy(acquisitionLoadToken=token)
    }
    if(manual===gear.acquisitionLoadToken && items.indices.all { items[it]===gear.items[it] }) return character
    return character.copy(gear=gear.copy(items=items,acquisitionLoadToken=manual))
}

internal val MANA_RESERVE_REF = DefinitionRef("dubl-3.69", "mana:rank")
internal val MANA_RESERVE_ALIAS_REF = DefinitionRef("dubl-3.69", MagicEquipmentRules.BASE_MANA_ENTRY_ID)

/** Two source entries name one reserve. Historical ownership and paid evidence stay as recorded. */
internal fun canonicalManaReserveDefinition(catalog: AcquisitionCatalog): AcquisitionDefinition? =
    catalog.definitions[MANA_RESERVE_REF]?.takeIf { it.kind == AcquisitionKind.MANA &&
        it.classificationSources["economic:mana-reserve"]?.originalText?.isNotBlank() == true }

internal fun manaReserveDefinition(ref: DefinitionRef, catalog: AcquisitionCatalog): AcquisitionDefinition? {
    val canonical = canonicalManaReserveDefinition(catalog) ?: return null
    if (ref == MANA_RESERVE_REF) return canonical
    return canonical.takeIf { ref == MANA_RESERVE_ALIAS_REF &&
        catalog.definitions[ref]?.classificationSources?.get("economic:mana-reserve")?.originalText?.isNotBlank() == true }
}

internal fun isManaReserveRef(ref: DefinitionRef): Boolean = ref == MANA_RESERVE_REF || ref == MANA_RESERVE_ALIAS_REF
internal fun manaReserveInstances(instances: List<AcquisitionInstance>): List<AcquisitionInstance> = instances.filter { isManaReserveRef(it.definition) }

/** Existing/manual ranks are never billed again or retroactively turned into a first-rank grant. */
internal fun manaReserveRank(character: DublCharacter, instances: List<AcquisitionInstance>): Int = maxOf(
    MagicEquipmentRules.manaReserveRank(character),
    manaReserveInstances(instances).maxOfOrNull { it.rank } ?: 0)

internal fun manaReserveOwnership(context: AcquisitionContext, ref: DefinitionRef): List<AcquisitionInstance> {
    return manaReserveOwnership(context.character, context.instances, ref)
}

internal fun manaReserveOwnership(character: DublCharacter, instances: List<AcquisitionInstance>, ref: DefinitionRef): List<AcquisitionInstance> {
    val owned = manaReserveInstances(instances)
    val witness = owned.firstOrNull { it.definition == MANA_RESERVE_REF } ?: owned.maxByOrNull { it.rank } ?: return emptyList()
    return listOf(witness.copy(definition = ref, rank = manaReserveRank(character, instances)))
}

internal fun projectManaReserveCharacter(character: DublCharacter, fromRank: Int, toRank: Int,
    grantFirstPower: Boolean, synchronizeAlias: Boolean): DublCharacter {
    val power = if (grantFirstPower && fromRank == 0 && MagicEquipmentRules.highestMagicPower(character) == 0)
        maxOf(character.magic.power, 1) else character.magic.power
    val development = if (synchronizeAlias) character.development + (MagicEquipmentRules.BASE_MANA_ENTRY_ID to
        (character.development[MagicEquipmentRules.BASE_MANA_ENTRY_ID]?.copy(rank = toRank) ?: OwnedDevelopment(toRank))) else character.development
    return character.copy(manaEnabled = true, magic = character.magic.copy(manaRank = toRank, power = power), development = development)
}

/** Shared projection for the legacy container's lossless subset; rich instances remain authoritative. */
internal fun projectSingleDevelopment(character: DublCharacter, definition: AcquisitionDefinition,
    change: AcquisitionChange, catalog: AcquisitionCatalog): DublCharacter {
    if (definition.repeatPolicy != RepeatPolicy.Single || change.parameters.isNotEmpty() || change.ownedAbilityOptionId() != null ||
        catalog.definitions.keys.count { it.entryId == change.definition.entryId } != 1) return character
    val prior = character.development[change.definition.entryId]
    return character.copy(development = character.development + (change.definition.entryId to
        (prior?.copy(rank = change.toRank) ?: OwnedDevelopment(change.toRank))))
}
