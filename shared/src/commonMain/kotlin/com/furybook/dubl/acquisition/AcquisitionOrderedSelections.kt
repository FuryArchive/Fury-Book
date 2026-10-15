package com.furybook.dubl.acquisition

import com.furybook.core.json.*

/** A canonical ordered list is one identity value; it is never parsed by display-name matching. */
object AcquisitionOrderedSelections {
    private data class TechniqueIdentity(val definition:DefinitionRef,val parameters:Map<String,String>,val abilityOptionId:String?)

    /** Duplicate legacy instances of one technique do not create a different source combination. */
    internal fun sameTechniques(left:String,right:String,instances:List<AcquisitionInstance>,catalog:AcquisitionCatalog):Boolean? {
        fun identities(value:String):List<TechniqueIdentity>? = decode(value)?.map { id ->
            val owned=instances.singleOrNull { it.instanceId==id } ?: return null
            val definition=catalog.definitions[owned.definition] ?: return null
            val displayKeys=definition.parameterDefinitions.mapNotNull { it.displayNameKey }.toSet() +
                if(AcquisitionSkillSpecializations.supported(definition)) setOf(AcquisitionSkillSpecializations.NAME_PARAMETER) else emptySet()
            val parameters=definition.parameterDefinitions.filter { it.requiredAtRank<=owned.rank && it.key !in displayKeys }
                .associate { parameter -> parameter.key to (owned.parameters[parameter.key] ?: return null) }
            val option=if(definition.hasTransitionPaymentOptions) null else {
                val options=definition.abilityOptionIds ?: return null
                if(options.isNotEmpty() && owned.abilityOptionId !in options) return null
                if(options.isEmpty()) null else owned.abilityOptionId
            }
            TechniqueIdentity(owned.definition,parameters,option)
        }
        val first=identities(left) ?: return null
        val second=identities(right) ?: return null
        return first==second
    }

    fun encode(values:List<String>):String {
        require(values.all { it.isNotBlank() })
        return jsonStringify(jsonArray(values.map(::jsonString)))
    }
    fun decode(value:String?):List<String>? {
        if(value==null) return null
        return runCatching {
            val array=JsonReader(value,true).read() as? JsonValue.Arr ?: return null
            val values=array.values.map { (it as? JsonValue.Str)?.value?.takeIf(String::isNotBlank) ?: return null }
            if(encode(values)!=value) return null
            EnvelopeList(values)
        }.getOrNull()
    }
    internal fun preservesPrefix(before:String,after:String?):Boolean {
        if(before==after) return true
        val old=decode(before) ?: return false
        val next=decode(after) ?: return false
        return next.size>old.size && next.take(old.size)==old
    }
    fun select(field:AcquisitionParameterField,index:Int,optionId:String,parameters:Map<String,String>):Map<String,String> {
        val length=checkNotNull(field.sequenceLength)
        require(!field.readOnly && !field.unresolved && index in field.sequenceReadOnlyPrefix until length)
        val option=field.options.single { it.id==optionId }
        val values=decode(parameters[field.key]).orEmpty().toMutableList()
        val names=decode(field.displayNameKey?.let { parameters[it] }).orEmpty().toMutableList()
        require(index<=values.size && values.size==names.size) { "Choose the sequence in order" }
        if(index==values.size) { values+=option.id;names+=option.label } else { values[index]=option.id;names[index]=option.label }
        return snapshotAcquisitionParameters(parameters+(field.key to encode(values))+(checkNotNull(field.displayNameKey) to encode(names)))
    }
}
