package com.furybook.dubl.acquisition

/** Exact acknowledged snapshot, never a plan's projected character. IDs are data, labels are text. */
data class AcquisitionCharacterView(val scope:AcquisitionScope,val state:StoredCharacter,val catalog:AcquisitionCatalog,
    val balance:Amount,val history:List<AcquisitionReceiptView>,val instances:List<AcquisitionInstanceView>,val sync:AcquisitionSyncView)
data class AcquisitionInstanceView(val instanceId:String,val definition:DefinitionRef,val label:String,val rank:Int,val parameters:Map<String,String>,val optionId:String?)

/** Rich ownership stays in the canonical instances; the legacy map cannot store its identity. */
fun AcquisitionCharacterView.ownedDevelopmentInstances(unprojectedOnly:Boolean=false):List<AcquisitionInstanceView> = EnvelopeList(instances.filter { owned ->
    val definition=catalog.definitions[owned.definition]
    definition?.kind==AcquisitionKind.DEVELOPMENT && owned.rank>0 && (!unprojectedOnly ||
        definition.repeatPolicy!=RepeatPolicy.Single || owned.parameters.isNotEmpty() || owned.optionId!=null ||
        catalog.definitions.keys.count { it.entryId==owned.definition.entryId }!=1 ||
        state.character.development[owned.definition.entryId]?.rank!=owned.rank)
})
class AcquisitionParameterOption(val id:String,val label:String,parameterValues:Map<String,String> = emptyMap(),val sourceChoice:Boolean=false) {
    val parameterValues:Map<String,String> = snapshotAcquisitionParameters(parameterValues)
    override fun equals(other:Any?):Boolean=other is AcquisitionParameterOption && id==other.id && label==other.label && parameterValues==other.parameterValues && sourceChoice==other.sourceChoice
    override fun hashCode():Int=listOf(id,label,parameterValues,sourceChoice).hashCode()
}
data class AcquisitionParameterField(val key:String,val label:String,val options:List<AcquisitionParameterOption>,val userDefined:Boolean,val unresolved:Boolean,
    val readOnly:Boolean=false,val requiredAtRank:Int=1,val displayNameKey:String?=null,val identityDomain:String?=null,
    val sequenceLength:Int?=null,val sequenceReadOnlyPrefix:Int=0)
data class AcquisitionSelectionPrompt(val targetIndex:Int,val definition:DefinitionRef,val label:String,val instances:List<AcquisitionInstanceView>,
    val parameters:List<AcquisitionParameterField>,val canCreateNew:Boolean,val specializedSkill:Boolean=false,val targetRank:Int=1,
    val selectedInstanceId:String?=null,val selectedParameters:Map<String,String> = emptyMap(),val canEditSelectedParameters:Boolean=false)

internal fun instanceViews(state:StoredCharacter,catalog:AcquisitionCatalog):List<AcquisitionInstanceView> = EnvelopeList(state.instances.mapIndexed { index,instance ->
    val name=state.character.skills[instance.instanceId]?.name?.takeIf { it.isNotBlank() }
    val definition=catalog.definitions[instance.definition]
    val specialized=definition?.takeIf { it.parameterDefinitions.isNotEmpty() }?.let { AcquisitionLabels(catalog).acquisition(instance.definition,instance.parameters) }
    AcquisitionInstanceView(instance.instanceId,instance.definition,name ?: specialized ?: "${AcquisitionLabels(catalog).definition(instance.definition)} · экземпляр ${index+1}",
        instance.rank,instance.parameters,if (definition?.hasTransitionPaymentOptions == true) null else instance.abilityOptionId)
})
internal fun canonicalView(scope:AcquisitionScope,state:StoredCharacter,catalog:AcquisitionCatalog,capabilities:AcquisitionCapabilities)=AcquisitionCharacterView(
    scope,state,catalog,balance(state.ledger),EnvelopeList(state.receipts.map { AcquisitionPresentation.receipt(it,catalog,true,state.ledger.entries.singleOrNull { entry -> entry.operationId==it.operationId }) }),instanceViews(state,catalog),AcquisitionPresentation.sync(state,capabilities))
/** Same source-resolved fields for main targets and explicit prerequisite editors. */
fun acquisitionParameterFields(definition:AcquisitionDefinition,state:StoredCharacter,catalog:AcquisitionCatalog,targetRank:Int,
    selectedParameters:Map<String,String> = emptyMap(),readOnlyKeys:Set<String> = emptySet(),previousParameters:Map<String,String> = emptyMap()):List<AcquisitionParameterField> {
    val instances=instanceViews(state,catalog)
    val specialized=AcquisitionSkillSpecializations.supported(definition)
    val displayKeys=definition.parameterDefinitions.mapNotNull { it.displayNameKey }.toSet()
    return EnvelopeList(definition.parameterDefinitions.filter { it.requiredAtRank<=targetRank && it.key !in displayKeys }
        .filterNot { specialized && it.key==AcquisitionSkillSpecializations.NAME_PARAMETER }.mapIndexed { number,parameter ->
            val ordered=parameter.selectionSource as? ParameterSelectionSource.OrderedOwnedInstances
            val sequenceLength=ordered?.lengthAt(targetRank)
            val prefix=if(parameter.key in readOnlyKeys) AcquisitionOrderedSelections.decode(previousParameters[parameter.key] ?: selectedParameters[parameter.key])?.size ?: 0 else 0
            val options=parameter.choices.map { AcquisitionParameterOption(it.id,it.label,it.parameterValues,true) } +
                (if(parameter.selectionSource==ParameterSelectionSource.OwnedWeaponKinds)
                    AcquisitionWeaponProficiencies.ownedKinds(state.instances,catalog).map { AcquisitionParameterOption(it.id,it.label) }
                else emptyList()) +
                (parameter.domain as? ParameterDomain.StableIds)?.valueIds?.filter { id -> parameter.choices.none { it.id==id } }?.sorted()?.mapIndexed { option,id ->
                    AcquisitionParameterOption(id,instances.firstOrNull { it.instanceId==id }?.label ?: "Вариант ${option+1}")
                }.orEmpty() + (parameter.selectionSource as? ParameterSelectionSource.OwnedInstances)?.let { source ->
                    instances.filter { owned -> catalog.definitions[owned.definition]?.let {
                        source.familyId in it.familyIds && it.classificationSources["family:${source.familyId}"]!=null }==true
                    }.map { AcquisitionParameterOption(it.instanceId,"${it.label} · ранг ${it.rank}" +
                        if(it.rank<source.minimumRank) " → требуется ${source.minimumRank}" else "") }
                }.orEmpty() + parameter.identityDomain?.let { domain ->
                    state.instances.flatMap { owned -> catalog.definitions[owned.definition]?.parameterDefinitions.orEmpty()
                        .filter { it.identityDomain==domain }.mapNotNull { source ->
                            val value=owned.parameters[source.key] ?: return@mapNotNull null
                            val name=source.displayNameKey?.let { owned.parameters[it] }?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                            AcquisitionParameterOption(value,name,source.choices.singleOrNull { it.id==value }?.parameterValues.orEmpty())
                        }
                    }
                }.orEmpty() + parameter.displayNameKey?.takeIf { ordered==null }?.let { nameKey ->
                    val value=selectedParameters[parameter.key]
                    val name=selectedParameters[nameKey]
                    if(value.isNullOrBlank() || name.isNullOrBlank()) emptyList() else listOf(AcquisitionParameterOption(value,name))
                }.orEmpty() + ordered?.let { source ->
                    instances.filter { it.definition in source.definitions && it.rank>=source.minimumRank }
                        .map { AcquisitionParameterOption(it.instanceId,AcquisitionLabels(catalog).acquisition(it.definition,it.parameters)) }
                }.orEmpty()
            AcquisitionParameterField(parameter.key,parameter.label ?: if(specialized)"Специализация" else "Параметр ${number+1}",EnvelopeList(options.distinctBy { it.id }),
                parameter.domain==ParameterDomain.UserDefinedIds && parameter.selectionSource==null,parameter.domain is ParameterDomain.Unresolved,
                parameter.key in readOnlyKeys && (sequenceLength==null || prefix>=sequenceLength),parameter.requiredAtRank,parameter.displayNameKey,parameter.identityDomain,
                sequenceLength,prefix)
        })
}
internal fun selectionPrompts(request:AcquisitionRequest,state:StoredCharacter,catalog:AcquisitionCatalog):List<AcquisitionSelectionPrompt> {
    val instances=instanceViews(state,catalog)
    return EnvelopeList(request.targets.mapIndexedNotNull { index,target ->
        val definition=catalog.definitions[target.definition] ?: return@mapIndexedNotNull null
        val specialized=AcquisitionSkillSpecializations.supported(definition)
        val mana=manaReserveDefinition(target.definition,catalog)
        val selectable=if(mana!=null) manaReserveOwnership(state.character,state.instances,mana.ref).map {
            AcquisitionInstanceView(it.instanceId,it.definition,AcquisitionLabels(catalog).definition(mana.ref),it.rank,it.parameters,it.abilityOptionId)
        } else instances.filter { it.definition==target.definition }
        val selected=target.instanceId?.let { id -> selectable.singleOrNull { it.instanceId==id } }
            ?: if(definition.repeatPolicy==RepeatPolicy.Single) selectable.singleOrNull() else null
        val selectedParameters=target.parameters.ifEmpty { selected?.parameters.orEmpty() }
        AcquisitionSelectionPrompt(index,target.definition,AcquisitionLabels(catalog).definition(target.definition),EnvelopeList(selectable),
            acquisitionParameterFields(definition,state,catalog,target.targetRank,selectedParameters,selected?.parameters?.keys.orEmpty(),selected?.parameters.orEmpty()),
            if(mana!=null) manaReserveRank(state.character,state.instances)==0 else definition.repeatPolicy !is RepeatPolicy.Unresolved &&
                (definition.repeatPolicy != RepeatPolicy.Single || instances.none { it.definition==target.definition }),specialized,target.targetRank,
            selected?.instanceId,selectedParameters,selected!=null && definition.parameterDefinitions.any {
                it.requiredAtRank<=target.targetRank && (it.key !in selected.parameters ||
                    (it.selectionSource as? ParameterSelectionSource.OrderedOwnedInstances)?.let { source ->
                        source.lengthAt(target.targetRank)>(AcquisitionOrderedSelections.decode(selected.parameters[it.key])?.size ?: 0) }==true) })
    })
}
