package com.furybook.dubl.acquisition

/** Read-only display metadata is bound to the exact quote, not a platform's current selection. */
data class AcquisitionReview(val model:AcquisitionUiModel,val details:AcquisitionReviewDetails?)
data class AcquisitionReviewDetails(val scope:AcquisitionScope,val key:AcquisitionQuoteKey,val request:AcquisitionRequest,
    val baseRevision:Long,val catalogRevision:String,val resolutionRevision:String,
    val abilityChoices:List<AcquisitionAbilitySelection>,val requirements:List<AcquisitionRequirementEvidence>)
data class AcquisitionAbilitySelection(val targetIndex:Int,val definition:DefinitionRef,val label:String,val selectedId:String?,
    val options:List<AcquisitionChoiceOptionView>,val unresolved:Boolean)
/** proof is the exact planned outcome; baseProof retains acknowledged pre-plan evidence. */
data class AcquisitionRequirementEvidence(val targetIndex:Int,val proof:AcquisitionWitnessProof,val baseProof:AcquisitionWitnessProof)
data class AcquisitionWitnessView(val witness:RequirementWitness,val label:String)
data class AcquisitionWitnessProof(val nodeId:String,val status:RequirementStatus,val witnesses:List<AcquisitionWitnessView>,val children:List<AcquisitionWitnessProof>)

/** Labels only. These stable option IDs remain catalog authority; no payment rule lives here. */
internal fun AcquisitionRequest.withTarget(index:Int,target:AcquisitionTarget)=AcquisitionRequest(characterId,
    targets.mapIndexed { i,old -> if(i==index)target else old },choiceSelections,includeTargets,prerequisiteSelections,externalConfirmations)
internal fun acquisitionReviewDetails(scope:AcquisitionScope,key:AcquisitionQuoteKey,request:AcquisitionRequest,state:StoredCharacter,catalog:AcquisitionCatalog,reviewedPlan:AcquisitionPlan?=null):AcquisitionReviewDetails {
    val labels=AcquisitionLabels(catalog);val instances=instanceViews(state,catalog)
    fun witness(value:RequirementWitness)=AcquisitionWitnessView(value,when(value) {
        is RequirementWitness.OwnedInstance->(instances.firstOrNull { it.instanceId==value.instanceId }?.label
            ?: labels.definition(DefinitionRef(value.packId,value.entryId)))+" · ранг ${value.rank}"
        is RequirementWitness.LearnedSpell->labels.definition(DefinitionRef(value.packId,value.entryId))+" · изучено"
        is RequirementWitness.Branch->labels.definition(DefinitionRef(value.packId,value.branchId))+" · "+acquisitionAbilityOptionLabel(catalog.definitions[DefinitionRef(value.packId,value.branchId)],value.accessOptionId,0)
        is RequirementWitness.External->"Подтверждённое внешнее условие"
    })
    fun proof(value:RequirementResult):AcquisitionWitnessProof=AcquisitionWitnessProof(value.nodeId,value.status,
        EnvelopeList(value.witnesses.map(::witness)),EnvelopeList(value.children.map(::proof)))
    val context=acquisitionContext(state,catalog,request.externalConfirmations)
    val exact=reviewedPlan ?: plan(request,state,catalog)
    require(exact.request.canonical==request.canonical && exact.baseRevision==state.revision &&
        exact.catalogRevision==catalog.catalogRevision && exact.resolutionRevision==catalog.resolutionRevision)
    return AcquisitionReviewDetails(scope,key,request,state.revision,catalog.catalogRevision,catalog.resolutionRevision,
        EnvelopeList(request.targets.mapIndexedNotNull { index,target ->
            val definition=catalog.definitions[target.definition] ?: return@mapIndexedNotNull null
            val ids=definition.abilityOptionIds
            if(ids?.isEmpty()==true) return@mapIndexedNotNull null
            AcquisitionAbilitySelection(index,target.definition,labels.definition(target.definition),target.optionId,
                EnvelopeList(ids.orEmpty().sorted().mapIndexed { number,id ->
                    val selected=plan(request.withTarget(index,target.copy(optionId=id)),state,catalog)
                    AcquisitionChoiceOptionView(id,acquisitionAbilityOptionLabel(definition,id,number),selected.cost?.let { AcquisitionDisplayedPrice.Known(it) } ?: AcquisitionDisplayedPrice.Unknown())
                }),ids==null)
        }),EnvelopeList(request.targets.mapIndexedNotNull { index,target -> catalog.definitions[target.definition]?.requirements?.let {
            AcquisitionRequirementEvidence(index,proof(evaluate(it,exact.projectedContext,target.parameters)),proof(evaluate(it,context,target.parameters)))
        } } + includedGrantRequirements(request,exact.projectedContext).map { selected ->
            AcquisitionRequirementEvidence(request.targets.indexOfFirst { it.definition==selected.owner },
                proof(evaluate(selected.requirement,exact.projectedContext,selected.target.parameters)),
                proof(evaluate(selected.requirement,context,selected.target.parameters)))
        }))
}
