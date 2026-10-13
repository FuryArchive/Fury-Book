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
