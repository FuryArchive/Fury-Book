package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Transition-scoped source inclusions, without minting missing historical creation choices. */
class AcquisitionWeaponProficiencyGrantsTest {
    private val skill = DefinitionRef("dubl-3.69", "test-combat-skill")
    private val proficiency = DefinitionRef("dubl-3.69", "test-proficiency")
    private fun rules(newOnly: Boolean = true): AcquisitionCatalog {
        val source = """{"documentId":"test-book","documentVersion":"1","originalText":"At creation, each newly gained pair of combat skill ranks includes one chosen weapon group proficiency."}"""
        fun definition(id: String, kind: String, maxRank: Int, repeat: String, parameters: String, price: String, extra: String = "") =
            """{"ref":{"packId":"dubl-3.69","entryId":"$id"},"kind":"$kind","name":"$id","maxRank":$maxRank,
              "repeatPolicy":$repeat,"sourceStatus":"Confirmed","sourceRef":$source,"parameterDefinitions":$parameters,
              "abilityOptionIds":[],"requirementsText":"Explicit none",
              "requirements":{"type":"AllOf","nodeId":"$id/none","sourceRef":$source,"children":[],"noRequirementsStatement":"Explicit none"},
              "priceRule":$price$extra}"""
        val grants = (2..6 step 2).joinToString { rank ->
            """{"sourceRef":$source,"parameterKey":"proficiency-rank-$rank","activateAtRank":$rank,"targetByValue":{},
              "targetDefinition":{"packId":"dubl-3.69","entryId":"test-proficiency"},"targetRank":1,
              "targetParameterChoices":{"groupId":["bows","bladed","axes"]},
              "customTargetEvidence":{"parameterKey":"groupId","evidenceKey":"gm-weapon-proficiency-group","sourceRef":$source},
              "creationOnly":true,"requireNewOwnership":$newOnly,"retainsSourceEligibility":true}"""
        }
        val owner = definition(skill.entryId, "SKILL", 6, """{"type":"Single"}""", "[]",
            """{"type":"PerRank","sourceRef":$source,"xp":"10","ap":"0"}""", ",\"includedGrants\":[$grants]")
        val gift = definition(proficiency.entryId, "DEVELOPMENT", 1,
            """{"type":"DistinctParameter","parameterKeys":["groupId"],"requireUnique":true}""",
            """[{"key":"groupId","domain":{"type":"UserDefinedIds"},"displayNameKey":"groupName","identityDomain":"weapon-proficiency-group",
              "choices":[{"id":"bows","label":"Group bows","sourceRef":$source},
                {"id":"bladed","label":"Group bladed","sourceRef":$source},{"id":"axes","label":"Group axes","sourceRef":$source},
                {"id":"polearms","label":"Group polearms","sourceRef":$source}]}]""",
            """{"type":"Unknown","sourceRef":$source,"reason":"No independently confirmed paid price"}""",
            ",\"classificationSources\":{\"weapon-proficiency\":$source}")
        val manifest = """{"format":"fury.content-pack","formatVersion":1,"id":"dubl-3.69","name":"Core","version":"3.69","ruleset":{"id":"dubl","version":"3.69","engineApi":1},"modules":[],"entries":[{"kind":"dubl.acquisition","path":"acquisition.json","modules":[],"order":1}]}"""
        val payload = """{"schemaVersion":1,"catalogRevision":"weapon-test","resolutionRevision":"source","definitions":[$owner,$gift],"diagnostics":[]}"""
        val files = mapOf("manifest.json" to manifest, "acquisition.json" to payload)
        val loaded = loadAcquisitionCatalog(listOf(FcpContentPack.load("", FcpTextSource(files::get))), false)
        assertTrue(loaded.diagnostics.isEmpty(), loaded.diagnostics.toString())
        return loaded.catalog
    }
    private fun before(rank: Int = 0, creating: Boolean = true, groups: List<AcquisitionInstance> = emptyList()): StoredCharacter {
        val phase = if (creating) RequirementNode.CreationPhase.Phase.Creating else RequirementNode.CreationPhase.Phase.Finalized
        val prior = if (rank == 0) emptyList() else listOf(AcquisitionInstance("combat", skill, rank, emptyMap(), null, AcquisitionOrigin.New))
        return StoredCharacter(DublCharacter("weapons", creationComplete = !creating), CharacterSheetExtras(), prior + groups,
            Ledger(Opening("opening", "source", Amount(1000, 0), phase, OpeningProvenance.NewCharacter), emptyList()),
            emptyList(), emptyList(), SyncMetadata(0), 0, 0, phase)
    }
    private fun group(id: String) = AcquisitionTarget(proficiency, "proficiency/$id", 1,
        mapOf("groupId" to id, "groupName" to "Group $id"))
    private fun slot(rank: Int) = acquisitionChoiceId(skill, "grant/proficiency-rank-$rank")
    private fun request(rank: Int, vararg choices: Pair<Int, AcquisitionTarget>) = AcquisitionRequest("weapons",
        listOf(AcquisitionTarget(skill, "combat", rank)), prerequisiteSelections = choices.associate { slot(it.first) to listOf(it.second) })

    @Test fun newly_crossed_creation_rank_requires_an_explicit_group_and_includes_it_once() {
        val catalog = rules()
        val missing = plan(request(2), before(), catalog)
        assertFalse(missing.executable)
        assertTrue(missing.questions.any { it.id == slot(2) })
        val result = plan(request(2, 2 to group("bows")), before(), catalog)
        assertTrue(result.executable, result.issues.toString())
        assertEquals(listOf(skill, proficiency), result.changes.map { it.definition })
        assertEquals(group("bows").parameters, result.changes.last().parameters)
        assertEquals(Amount.ZERO, result.prices.last().cost)
        assertEquals(result.prices.first().cost, result.cost)
        assertTrue(validateAcquisitionProjection(result.changes, before(), catalog).issues.isEmpty())
        assertTrue(validateAcquisitionProjection(result.changes.take(1), before(), catalog).issues.isNotEmpty())
        assertFalse(plan(AcquisitionRequest("weapons", listOf(group("bows"))), before(), catalog).executable)
        assertFalse(plan(request(2, 2 to group("polearms")), before(), catalog).executable,
            "A known incompatible source group cannot be selected for this skill")
        assertFalse(plan(request(2, 2 to group("custom-group")), before(), catalog).executable,
            "A custom group requires an explicit scoped GM decision")
    }

    @Test fun old_rank_has_no_retroactive_credit_or_missing_choice_requirement_and_finalized_rank_grants_none() {
        val catalog = rules()
        val old = before(2)
        val bytes = encodeEnvelope(old)
        val later = plan(request(3), old, catalog)
        assertTrue(later.executable, later.issues.toString())
        assertTrue(later.questions.isEmpty())
        assertEquals(listOf(skill), later.changes.map { it.definition })
        assertEquals(bytes, encodeEnvelope(old))
        assertFalse(plan(AcquisitionRequest("weapons", listOf(group("bows"))), old, catalog).executable)
        val finalized = plan(request(2), before(creating = false), catalog)
        assertTrue(finalized.executable, finalized.issues.toString())
        assertEquals(listOf(skill), finalized.changes.map { it.definition })
        assertTrue(finalized.questions.isEmpty())
    }

    @Test fun two_new_slots_require_two_distinct_groups_and_cannot_discount_a_third_copy() {
        val catalog = rules()
        val two = plan(request(4, 2 to group("bows"), 4 to group("bladed")), before(), catalog)
        assertTrue(two.executable, two.issues.toString())
        assertEquals(listOf(Amount.ZERO, Amount.ZERO), two.prices.drop(1).map { it.cost })
        assertEquals(setOf("bows", "bladed"), two.projectedContext.instances.filter { it.definition == proficiency }
            .map { it.parameters.getValue("groupId") }.toSet())
        assertFalse(plan(request(4, 2 to group("bows"), 4 to group("bows")), before(), catalog).executable)
        val forged = two.changes + AcquisitionChange(AcquisitionKind.DEVELOPMENT, proficiency, "third", 0, 1,
            group("axes").parameters, null)
        assertTrue(validateAcquisitionProjection(forged, before(), catalog).issues.isNotEmpty())
    }

    @Test fun saved_proficiency_instances_and_receipts_reopen_and_undo_without_rewriting_the_old_owner() = runBlocking {
        val original = before(1)
        val store = InMemoryAcquisitionStore(mapOf("weapons" to encodeEnvelope(original)))
        val service = AcquisitionService(store, { rules() }, { "generated" })
        val chosen = request(2, 2 to group("custom-group"))
        val evidence = RequirementWitness.External("gm-weapon-proficiency-group", "dubl-3.69/test-combat-skill/custom-group",
            "confirmed-gm-group", rules().definitions.getValue(proficiency).sourceRef)
        val request = AcquisitionRequest(chosen.characterId, chosen.targets, prerequisiteSelections = chosen.prerequisiteSelections,
            externalConfirmations = listOf(evidence))
        val preview = service.preview(request)
        assertTrue(preview.executable, preview.issues.toString())
        val applied = assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("group-purchase", "weapons", request,
            preview.baseRevision, preview.previewToken)))
        assertEquals(preview.cost, applied.receipt.result.cost)
        val saved = assertIs<LoadResult.Loaded>(store.load("weapons")).state
        val evidencePrices = assertIs<AcquisitionSavedEvidence.Recorded>(savedAcquisitionOperation(applied.receipt,
            saved.ledger.entries.single { it.operationId == "group-purchase" }).evidence)
        assertEquals(Amount.ZERO, evidencePrices.prices.last().cost)
        assertEquals(group("custom-group").parameters, saved.instances.single { it.definition == proficiency }.parameters)
        assertEquals(saved.receipts, assertIs<LoadResult.Loaded>(store.load("weapons")).state.receipts)
        assertIs<ApplyResult.Applied>(AcquisitionUndo(store).undo("weapons", "undo-group", saved.revision))
        assertEquals(acquisitionProjectionCanonical(original), acquisitionProjectionCanonical(assertIs<LoadResult.Loaded>(store.load("weapons")).state))
    }

    @Test fun weapon_kind_registry_uses_owned_source_groups_and_repeats_add_a_new_kind() {
        val base = rules()
        val gift = base.definitions.getValue(proficiency)
        val source = gift.sourceRef
        val short = WeaponProficiencyKind("native:short-bow", "Short bow", source)
        val long = WeaponProficiencyKind("native:long-bow", "Long bow", source)
        val dagger = WeaponProficiencyKind("native:dagger", "Dagger", source)
        val groups = gift.parameterDefinitions.single().copy(choices = listOf(
            ParameterChoice("bows", "Group bows", source, weaponKinds = listOf(short, long)),
            ParameterChoice("bladed", "Group bladed", source, weaponKinds = listOf(dagger))))
        val summon = DefinitionRef("dubl-3.69", "test-summon-weapon")
        val parameters = (1..2).map { rank -> ParameterDefinition("weaponKind$rank", ParameterDomain.UserDefinedIds,
            selectionSource = ParameterSelectionSource.OwnedWeaponKinds, uniqueWithinGroup = "summoned-kinds",
            displayNameKey = "weaponKind${rank}Name") }
        val spell = gift.copy(ref = summon, name = "Summon", parameterDefinitions = parameters,
            repeatPolicy = RepeatPolicy.DistinctParameter(setOf("weaponKind1", "weaponKind2")),
            priceRule = PriceRule.Fixed(10, 0, source), classificationSources = emptyMap(),
            newParameterValueRequirement = NewParameterValueRequirement(setOf("weaponKind1", "weaponKind2"), 1, source))
        val catalog = base.copy(definitions = base.definitions + (proficiency to gift.copy(parameterDefinitions = listOf(groups))) + (summon to spell))
        val owned = listOf("bows", "bladed", "custom-group").map { id -> AcquisitionInstance("owned/$id", proficiency, 1,
            group(id).parameters, null, AcquisitionOrigin.New) }
        val state = before(creating = false, groups = owned)
        assertEquals(setOf(short.id, long.id, dagger.id), AcquisitionWeaponProficiencies.ownedKinds(owned, catalog).map { it.id }.toSet())
        assertTrue(AcquisitionWeaponProficiencies.ownedGroups(owned, catalog).single { it.groupId == "custom-group" }.weaponKinds.isEmpty())
        val fields = acquisitionParameterFields(spell, state, catalog, 1)
        assertTrue(fields.all { !it.userDefined && it.options.map { option -> option.id }.toSet() == setOf(short.id, long.id, dagger.id) })
        fun target(id: String, first: WeaponProficiencyKind, second: WeaponProficiencyKind) = AcquisitionTarget(summon, id, 1,
            mapOf("weaponKind1" to first.id, "weaponKind1Name" to first.label, "weaponKind2" to second.id, "weaponKind2Name" to second.label))
        val first = target("summon-first", short, long)
        val initial = plan(AcquisitionRequest("weapons", listOf(first)), state, catalog)
        assertTrue(initial.executable, initial.issues.toString())
        val prior = AcquisitionInstance(first.instanceId!!, summon, 1, first.parameters, null, AcquisitionOrigin.New)
        val later = before(creating = false, groups = owned + prior)
        assertFalse(plan(AcquisitionRequest("weapons", listOf(target("reversed", long, short))), later, catalog).executable)
        assertTrue(plan(AcquisitionRequest("weapons", listOf(target("adds-dagger", short, dagger))), later, catalog).executable)
        assertFalse(plan(AcquisitionRequest("weapons", listOf(target("invented", short,
            WeaponProficiencyKind("invented", "Short bow", source)))), state, catalog).executable)
        val unproven = catalog.copy(definitions = catalog.definitions + (proficiency to gift.copy(parameterDefinitions = listOf(groups), classificationSources = emptyMap())))
        assertTrue(AcquisitionWeaponProficiencies.ownedKinds(owned, unproven).isEmpty())
    }

    @Test fun a_source_without_newness_allows_an_explicit_owned_group_no_op_and_captures_that_exact_choice() = runBlocking {
        val catalog = rules(newOnly = false)
        val old = AcquisitionInstance("old-bows", proficiency, 1, group("bows").parameters, null, AcquisitionOrigin.New)
        val original = before(1, groups = listOf(old))
        val selected = AcquisitionTarget(proficiency, old.instanceId, 1, old.parameters)
        val request = request(2, 2 to selected)
        val result = plan(request, original, catalog)
        assertTrue(result.executable, result.issues.toString())
        assertEquals(listOf(skill), result.changes.map { it.definition })
        assertEquals(old, result.projectedContext.instances.single { it.definition == proficiency })
        assertEquals(result.prices.single().cost, result.cost)
        assertTrue(validateAcquisitionProjection(result.changes,original,catalog,operationGrantSelections=request.prerequisiteSelections).issues.isEmpty())
        assertTrue(validateAcquisitionProjection(result.changes,original,catalog).issues.isNotEmpty(),
            "The captured old-group choice is required for an exact no-op replay")
        val missing=selected.copy(instanceId="not-owned")
        assertTrue(validateAcquisitionProjection(result.changes,original,catalog,
            operationGrantSelections=mapOf(slot(2) to listOf(missing))).issues.isNotEmpty())
        val two=plan(request(4,2 to selected,4 to selected),before(groups=listOf(old)),catalog)
        assertTrue(two.executable,two.issues.toString())
        assertEquals(listOf(skill),two.changes.map { it.definition })
        val view=AcquisitionPresentation.present(AcquisitionScope("source","weapons",0,0,0),AcquisitionQuoteKey("owned-group",0),
            result,original,catalog,AcquisitionCapabilities(true,false,true))
        assertTrue(view.rows.any { it.definition==proficiency && it.alreadyOwned && it.price==AcquisitionDisplayedPrice.Known(Amount.ZERO) })
        assertFalse(plan(request(2), original, catalog).executable, "An old group never silently supplies the choice")
        val store = InMemoryAcquisitionStore(mapOf("weapons" to encodeEnvelope(original)))
        val service = AcquisitionService(store, { catalog }, { "generated" })
        val preview = service.preview(request)
        val receipt = assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("owned-group-choice", "weapons", request,
            preview.baseRevision, preview.previewToken))).receipt
        val saved = assertIs<LoadResult.Loaded>(store.load("weapons")).state
        assertEquals(old, saved.instances.single { it.definition == proficiency })
        assertTrue(receipt.requestCanonical.contains("old-bows"))
        assertEquals(1, receipt.result.changes.size)
        assertIs<ApplyResult.Applied>(AcquisitionUndo(store).undo("weapons", "undo-owned-choice", saved.revision))
        assertEquals(acquisitionProjectionCanonical(original), acquisitionProjectionCanonical(assertIs<LoadResult.Loaded>(store.load("weapons")).state))
    }
}
