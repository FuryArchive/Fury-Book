package com.furybook.dubl.acquisition

/** The explicit UI choice captures labels beside opaque IDs; typing never creates or infers identity. */
object AcquisitionNamedParameters {
    fun complete(fields:List<AcquisitionParameterField>,parameters:Map<String,String>):Boolean=fields.all { field ->
        !field.unresolved && !parameters[field.key].isNullOrBlank() &&
            (field.displayNameKey==null || !parameters[field.displayNameKey].isNullOrBlank()) &&
            (field.sequenceLength==null || (AcquisitionOrderedSelections.decode(parameters[field.key])?.size==field.sequenceLength &&
                AcquisitionOrderedSelections.decode(field.displayNameKey?.let { parameters[it] })?.size==field.sequenceLength))
    }
    fun selectOption(field:AcquisitionParameterField,optionId:String,parameters:Map<String,String>):Map<String,String> {
        require(!field.readOnly && !field.unresolved)
        require(field.sequenceLength==null) { "Select an ordered slot" }
        val choice=field.options.single { it.id==optionId }
        return snapshotAcquisitionParameters(parameters+choice.parameterValues+(field.key to choice.id)+
            field.displayNameKey?.let { mapOf(it to choice.label) }.orEmpty())
    }
    fun selectCustom(field:AcquisitionParameterField,name:String,parameters:Map<String,String>,newId:()->String):Map<String,String> {
        require(!field.readOnly && !field.unresolved && field.userDefined)
        val nameKey=checkNotNull(field.displayNameKey) { "A custom identity needs a declared display-name field" }
        val cleaned=AcquisitionSkillSpecializations.cleanName(name)
        require(cleaned.isNotBlank()) { "Name the choice" }
        require(field.options.none { it.sourceChoice && it.label.equals(cleaned,ignoreCase=true) }) { "Choose the existing source option explicitly" }
        val id=newId().also { require(it.isNotBlank()) }
        val previouslyBound=field.options.firstOrNull { it.id==parameters[field.key] }?.parameterValues?.keys.orEmpty()
        return snapshotAcquisitionParameters(parameters.filterKeys { it !in previouslyBound }+(field.key to "custom:$id")+(nameKey to cleaned))
    }
    fun selectedLabel(field:AcquisitionParameterField,parameters:Map<String,String>):String? =
        field.options.firstOrNull { it.id==parameters[field.key] }?.label
            ?: field.displayNameKey?.let { parameters[it] }?.takeIf { it.isNotBlank() }
}
