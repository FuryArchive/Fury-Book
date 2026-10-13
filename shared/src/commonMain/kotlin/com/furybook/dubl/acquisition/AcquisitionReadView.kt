package com.furybook.dubl.acquisition

/** Exact acknowledged snapshot, never a plan's projected character. IDs are data, labels are text. */
data class AcquisitionCharacterView(val scope:AcquisitionScope,val state:StoredCharacter,val catalog:AcquisitionCatalog,
    val balance:Amount,val history:List<AcquisitionReceiptView>,val instances:List<AcquisitionInstanceView>,val sync:AcquisitionSyncView)
data class AcquisitionInstanceView(val instanceId:String,val definition:DefinitionRef,val label:String,val rank:Int,val parameters:Map<String,String>,val optionId:String?)
data class AcquisitionParameterOption(val id:String,val label:String)
data class AcquisitionParameterField(val key:String,val label:String,val options:List<AcquisitionParameterOption>,val userDefined:Boolean,val unresolved:Boolean)
data class AcquisitionSelectionPrompt(val targetIndex:Int,val definition:DefinitionRef,val label:String,val instances:List<AcquisitionInstanceView>,
    val parameters:List<AcquisitionParameterField>,val canCreateNew:Boolean)

internal fun instanceViews(state:StoredCharacter,catalog:AcquisitionCatalog):List<AcquisitionInstanceView> = EnvelopeList(state.instances.mapIndexed { index,instance ->
    val name=state.character.skills[instance.instanceId]?.name?.takeIf { it.isNotBlank() }
    AcquisitionInstanceView(instance.instanceId,instance.definition,name ?: "${AcquisitionLabels(catalog).definition(instance.definition)} · экземпляр ${index+1}",
        instance.rank,instance.parameters,instance.abilityOptionId)
})
internal fun canonicalView(scope:AcquisitionScope,state:StoredCharacter,catalog:AcquisitionCatalog,capabilities:AcquisitionCapabilities)=AcquisitionCharacterView(
    scope,state,catalog,balance(state.ledger),EnvelopeList(state.receipts.map { AcquisitionPresentation.receipt(it,catalog,true,state.ledger.entries.singleOrNull { entry -> entry.operationId==it.operationId }) }),instanceViews(state,catalog),AcquisitionPresentation.sync(state,capabilities))
internal fun selectionPrompts(request:AcquisitionRequest,state:StoredCharacter,catalog:AcquisitionCatalog):List<AcquisitionSelectionPrompt> {
    val instances=instanceViews(state,catalog)
    return EnvelopeList(request.targets.mapIndexedNotNull { index,target ->
        val definition=catalog.definitions[target.definition] ?: return@mapIndexedNotNull null
        AcquisitionSelectionPrompt(index,target.definition,AcquisitionLabels(catalog).definition(target.definition),
            EnvelopeList(instances.filter { it.definition==target.definition }),EnvelopeList(definition.parameterDefinitions.mapIndexed { number,parameter ->
                AcquisitionParameterField(parameter.key,"Параметр ${number+1}",EnvelopeList((parameter.domain as? ParameterDomain.StableIds)?.valueIds?.sorted()?.mapIndexed { option,id ->
                    AcquisitionParameterOption(id,instances.firstOrNull { it.instanceId==id }?.label ?: "Вариант ${option+1}")
                }.orEmpty()),parameter.domain==ParameterDomain.UserDefinedIds,parameter.domain is ParameterDomain.Unresolved)
            }),definition.repeatPolicy !is RepeatPolicy.Unresolved && (definition.repeatPolicy != RepeatPolicy.Single || instances.none { it.definition==target.definition }))
    })
}
