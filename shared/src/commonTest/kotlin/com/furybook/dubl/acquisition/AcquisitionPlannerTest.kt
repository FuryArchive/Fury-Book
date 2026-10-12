package com.furybook.dubl.acquisition

import com.furybook.dubl.model.*
import kotlin.test.*

class AcquisitionPlannerTest {
    private val source = RuleSourceRef("planner-fixture", "1", "Explicit test rule")
    private fun ref(id: String) = DefinitionRef("test", id)
    private fun none(id: String) = RequirementNode.AllOf("$id/none", source, emptyList(), NoRequirementsEvidence(source, "No prerequisites"))
    private fun owned(id: String) = RequirementNode.OwnedRank("need/$id", source, "test", id, ComparisonOperator.GE, 1)
    private fun definition(id: String, need: RequirementNode = none(id), max: Int = 1, price: Long = 10) =
        AcquisitionDefinition(ref(id), AcquisitionKind.DEVELOPMENT, max, RepeatPolicy.Single, need,
            PriceRule.PerRank(price, 0, source), SourceStatus.Confirmed, id, source)
    private fun catalog(vararg defs: AcquisitionDefinition) = AcquisitionCatalog(defs.associateBy { it.ref }, "c1", "r1")
    private fun state(instances: List<AcquisitionInstance> = emptyList()) = StoredCharacter(DublCharacter("hero"), CharacterSheetExtras(), instances,
        Ledger(Opening("new", "source", Amount(100, 10), RequirementNode.CreationPhase.Phase.Creating, OpeningProvenance.NewCharacter), emptyList()),
        emptyList(), emptyList(), SyncMetadata(0), 0, 0, RequirementNode.CreationPhase.Phase.Creating)
    private fun request(vararg ids: String) = AcquisitionRequest("hero", ids.map { AcquisitionTarget(ref(it), targetRank = 1) })
    private fun instance(id: String, rank: Int = 1, identity: String = "owned/$id", parameters: Map<String,String> = emptyMap()) =
        AcquisitionInstance(identity, ref(id), rank, parameters, null, AcquisitionOrigin.New)

    @Test fun shared_prerequisite_once_and_existing_ancestor_zero() {
        val c = catalog(definition("ancestor"), definition("one", owned("ancestor")), definition("two", owned("ancestor")))
        val p = plan(request("one", "two"), state(), c)
        assertTrue(p.executable, p.issues.toString())
        assertEquals(1, p.changes.count { it.definition == ref("ancestor") })
        assertEquals(Amount(30, 0), p.cost)
        val existing = plan(request("one", "two"), state(listOf(instance("ancestor"))), c)
        assertTrue(existing.executable)
        assertEquals(Amount(20, 0), existing.cost)
        assertTrue(validateAcquisitionProjection(p.changes, state(), c).issues.isEmpty())
    }

    @Test fun fixed_rank_idempotent_and_invalid_targets_not_clamped() {
        val c = catalog(definition("fixed"))
        val p = plan(request("fixed"), state(listOf(instance("fixed"))), c)
        assertTrue(p.executable)
        assertEquals(emptyList(), p.changes)
        assertEquals(Amount.ZERO, p.cost)
        for (rank in listOf(-1, 0, 2, Int.MAX_VALUE)) {
            val invalid = plan(AcquisitionRequest("hero", listOf(AcquisitionTarget(ref("fixed"), targetRank=rank))), state(), c)
            assertFalse(invalid.executable)
            assertTrue(invalid.changes.isEmpty())
        }
    }

    @Test fun or_choices_are_explicit_stable_and_quoted() {
        val need = RequirementNode.AnyOf("choose", source, listOf(owned("a"), owned("b")))
        val c = catalog(definition("a"), definition("b", price=20), definition("target", need))
        val p = plan(request("target"), state(), c)
        assertFalse(p.executable)
        val q = p.questions.single()
        assertEquals(setOf(Amount(10,0), Amount(20,0)), q.options.map { assertIs<PriceResult.Known>(it.quote).cost }.toSet())
        val selected = plan(AcquisitionRequest("hero", request("target").targets, mapOf(q.id to q.options[1].id)), state(), c)
        assertTrue(selected.executable, selected.issues.toString())
        assertEquals(Amount(30,0), selected.cost)
        assertEquals(listOf(ref("b"), ref("target")), selected.changes.map { it.definition })
    }

    @Test fun two_specializations_new_copy_and_raise_have_distinct_identity() {
        val spec = definition("spec", max=3).copy(repeatPolicy=RepeatPolicy.DistinctParameter(setOf("knowledgeId")),
            parameterDefinitions=listOf(ParameterDefinition("knowledgeId", ParameterDomain.UserDefinedIds)))
        val c = catalog(spec)
        val first = instance("spec", identity="academic", parameters=mapOf("knowledgeId" to "academic"))
        val targets = listOf(AcquisitionTarget(ref("spec"), "academic", 2, first.parameters),
            AcquisitionTarget(ref("spec"), "arcane", 1, mapOf("knowledgeId" to "arcane")))
        val p = plan(AcquisitionRequest("hero", targets), state(listOf(first)), c)
        assertTrue(p.executable, p.issues.toString())
        assertEquals(setOf("academic", "arcane"), p.changes.map { it.instanceId }.toSet())
        assertEquals(Amount(20,0), p.cost)
        assertFalse(plan(AcquisitionRequest("hero", listOf(targets[1].copy(parameters=first.parameters))), state(listOf(first)), c).executable)
    }

    @Test fun cyclic_unknown_and_price_only_are_blocked() {
        for (c in listOf(catalog(definition("a", owned("b")), definition("b", owned("a"))),
            catalog(definition("a", RequirementNode.Unresolved("unknown", source, "Missing source"))))) {
            val p = plan(request("a"), state(), c)
            assertFalse(p.executable)
            assertTrue(p.issues.isNotEmpty())
            assertNull(p.cost)
        }
    }

    @Test fun owned_cries_no_purchase_with_legal_typed_fixture() {
        val cries = listOf("cry1", "cry2", "cry3")
        for (count in 2..3) {
            val predicate = RequirementNode.AnyOf("cries", source, cries.map(::owned))
            val need = RequirementNode.DistinctCount("count", source, predicate, DistinctScope.Definition, ComparisonOperator.GE, count)
            val c = catalog(*(cries.map { definition(it) } + definition("target", need, price=30)).toTypedArray())
            val original = state(cries.take(count).map { instance(it) })
            val p = plan(request("target"), original, c)
            assertTrue(p.executable, p.issues.toString())
            assertEquals(listOf(ref("target")), p.changes.map { it.definition })
            assertEquals(Amount(30,0),p.cost)
            assertEquals(Amount(70,10),p.remaining)
            assertTrue(validateAcquisitionProjection(p.changes,original,c).issues.isEmpty())
        }
    }

    @Test fun immutable_request_parameters_and_canonical_token_changes() {
        val params = mutableMapOf("kindId" to "fire")
        val selections = mutableMapOf<String,String>()
        val targets = mutableListOf(AcquisitionTarget(ref("resistance"),"fire",1,params))
        val c = catalog(definition("resistance").copy(repeatPolicy=RepeatPolicy.DistinctParameter(setOf("kindId")),
            parameterDefinitions=listOf(ParameterDefinition("kindId",ParameterDomain.UserDefinedIds))))
        val r = AcquisitionRequest("hero",targets,selections)
        val original = plan(r,state(),c)
        assertTrue(original.executable)
        val hash = r.targets.single().parameters.hashCode()
        params["kindId"] = "poison"; selections["stale"] = "1"; targets.clear()
        assertEquals("fire",r.targets.single().parameters["kindId"])
        assertEquals(mapOf("kindId" to "fire").hashCode(),hash)
        assertEquals(original.previewToken,plan(r,state(),c).previewToken)
        assertNotEquals(original.previewToken,plan(r,state().copy(revision=1),c).previewToken)
        assertNotEquals(original.previewToken,plan(r,state(),c.copy(resolutionRevision="r2")).previewToken)
        assertNotEquals(original.previewToken,plan(r,state(),c.copy(definitions=c.definitions.mapValues { it.value.copy(priceRule=PriceRule.Fixed(20,0,source)) })).previewToken)
        assertFalse(r.targets is MutableList<*>)
        assertFalse(r.targets.single().parameters is MutableMap<*,*>)
    }

    @Test fun replay_rejects_price_known_but_invalid_identity_cap_requirement_and_stale_from() {
        val c = catalog(definition("a",max=2),definition("blocked",RequirementNode.Unresolved("unknown",source,"Unknown admission")))
        for (change in listOf(AcquisitionChange(AcquisitionKind.DEVELOPMENT,ref("a"),"x",0,3,emptyMap(),null),
            AcquisitionChange(AcquisitionKind.DEVELOPMENT,ref("a"),"x",1,2,emptyMap(),null),
            AcquisitionChange(AcquisitionKind.DEVELOPMENT,ref("blocked"),"x",0,1,emptyMap(),null),
            AcquisitionChange(AcquisitionKind.DEVELOPMENT,ref("a"),"x",0,1,mapOf("unexpected" to "id"),null))) {
            assertIs<PriceResult.Known>(quote(change,acquisitionContext(state(),c)))
            assertTrue(validateAcquisitionProjection(listOf(change),state(),c).issues.isNotEmpty())
        }
        val duplicate = state(listOf(instance("a"),instance("a",identity="other")))
        assertFalse(plan(request("a"),duplicate,c).executable)
    }

    @Test fun missing_parameter_domain_and_unknown_prices_never_become_zero() {
        val unresolved = definition("spec").copy(parameterDefinitions=listOf(ParameterDefinition("weaponId",ParameterDomain.Unresolved("Weapon ID domain not confirmed"))))
        val p = plan(request("spec"),state(),catalog(unresolved))
        assertFalse(p.executable); assertNull(p.cost)
        assertTrue(p.issues.any { it.reason.contains("domain") })
        val unknownPrice = definition("a").copy(priceRule=PriceRule.Unknown("Unconfirmed transition",source))
        assertNull(plan(request("a"),state(),catalog(unknownPrice)).cost)
    }

    @Test fun external_confirmations_are_explicit_scoped_retained_and_undo_sensitive() {
        val condition = RequirementNode.ExternalEvidence("permission",source,"approval","scope/a")
        val c = catalog(definition("a",condition),definition("unknown",RequirementNode.Unresolved("source",source,"Author rule unknown")))
        val confirmation = RequirementWitness.External("approval","scope/a","gm-confirmed-1",source)
        assertFalse(plan(request("a"),state(),c).executable)
        val r = AcquisitionRequest("hero",request("a").targets,externalConfirmations=listOf(confirmation))
        val p = plan(r,state(),c)
        assertTrue(p.executable,p.issues.toString())
        assertTrue(validateAcquisitionProjection(p.changes,state(),c,listOf(confirmation)).issues.isEmpty())
        assertFalse(plan(AcquisitionRequest("hero",request("a").targets,externalConfirmations=listOf(confirmation.copy(scopeId="scope/b"))),state(),c).executable)
        assertFalse(plan(AcquisitionRequest("hero",request("unknown").targets,externalConfirmations=listOf(confirmation)),state(),c).executable)
        val encoded = encodeAcquisitionEvidence(listOf(confirmation), "{\"phase\":\"Creating\"}")
        assertEquals(listOf(confirmation),decodeAcquisitionConfirmations(encoded))
        assertTrue(decodeAcquisitionConfirmations(encoded.replace("evidenceId","unknownField")).isEmpty())
        assertTrue(decodeAcquisitionConfirmations("{}").isEmpty())
        val entry = LedgerEntry("op",LedgerEntryType.Purchase,p.changes,Amount(-10,0),RuleVersions("c1","r1"),creationPhase=state().creationPhase,evidenceCanonical=encoded)
        val receipt = Receipt("op",r.canonical,SavedAcquisitionResult(p.cost!!,p.remaining!!,p.changes),ReceiptStatus.Applied,0,1)
        val retained = state().copy(ledger=state().ledger.copy(entries=listOf(entry)),receipts=listOf(receipt),revision=1)
        assertEquals(listOf(confirmation),retainedAcquisitionConfirmations(retained))
        assertTrue(retainedAcquisitionConfirmations(retained.copy(receipts=listOf(receipt.copy(status=ReceiptStatus.Undone)))).isEmpty())
    }

    @Test fun spell_purchase_uses_catalog_identity_and_learned_flag() {
        val spell = definition("spell").copy(kind=AcquisitionKind.SPELL)
        val need = RequirementNode.LearnedSpell("learn",source,"test","spell")
        val c = catalog(spell,definition("target",need))
        val unlearned = state(listOf(instance("spell"))).copy(character=DublCharacter("hero",magic=CharacterMagic(spells=listOf(KnownSpell("local",catalogId="spell",learned=false)))))
        assertFalse(plan(request("target"),unlearned,c).executable)
        val learned = unlearned.copy(character=unlearned.character.copy(magic=CharacterMagic(spells=listOf(KnownSpell("local",catalogId="spell",learned=true)))))
        assertEquals(Amount(10,0),plan(request("target"),learned,c).cost)
        val buy = plan(request("target"),state(),c)
        assertTrue(buy.executable,buy.issues.toString())
        assertEquals(listOf(ref("spell"),ref("target")),buy.changes.map { it.definition })
    }

    @Test fun overflow_budget_exclusion_and_deep_tree_are_blocked() {
        assertFalse(plan(request("a"),state(),catalog(definition("a",price=101))).executable)
        val huge = catalog(definition("a",price=Long.MAX_VALUE),definition("b",price=Long.MAX_VALUE))
        val overflow = plan(request("a","b"),state(),huge)
        assertNull(overflow.cost); assertFalse(overflow.executable)
        val c = catalog(definition("a",RequirementNode.Exclusive("exclude",source,owned("b"))), definition("b"))
        assertFalse(plan(request("a","b"),state(),c).executable)
        var deep: RequirementNode = owned("missing")
        repeat(100) { deep = RequirementNode.AllOf("depth/$it",source,listOf(deep)) }
        assertFalse(plan(request("a"),state(),catalog(definition("a",deep))).executable)
    }

    @Test fun source_blocked_or_alternative_stays_local_and_stale_indices_are_rejected() {
        val need = RequirementNode.AnyOf("or",source,listOf(RequirementNode.Unresolved("unknown",source,"Missing source"),owned("a")))
        val c = catalog(definition("a"),definition("target",need))
        val first = plan(request("target"),state(),c)
        val question = first.questions.single()
        assertIs<PriceResult.Unknown>(question.options[0].quote)
        assertTrue(question.options[0].issues.any { it.reason.contains("Missing source") })
        val r = AcquisitionRequest("hero",request("target").targets,mapOf(question.id to "need/a"))
        assertTrue(plan(r,state(),c).executable)
        assertFalse(plan(AcquisitionRequest("hero",r.targets,mapOf(question.id to "1")),state(),c).executable)
        assertFalse(plan(AcquisitionRequest("hero",r.targets,mapOf("stale" to "need/a")),state(),c).executable)
    }

    @Test fun explicit_parameterized_ancestor_selection_is_shared_without_name_matching() {
        val spec = definition("spec").copy(repeatPolicy=RepeatPolicy.DistinctParameter(setOf("kindId")),
            parameterDefinitions=listOf(ParameterDefinition("kindId",ParameterDomain.UserDefinedIds)))
        val need = RequirementNode.Specialization("spec-needed",source,"test","spec",ComparisonOperator.GE,1,ParameterMatch("kindId","fire"))
        val c = catalog(spec,definition("one",need),definition("two",need))
        val question = plan(request("one","two"),state(),c).questions.first()
        val r = AcquisitionRequest("hero",request("one","two").targets,prerequisiteSelections=mapOf(question.id to
            listOf(AcquisitionTarget(ref("spec"),"stable-fire",1,mapOf("kindId" to "fire")))))
        val p = plan(r,state(),c)
        assertTrue(p.executable,p.issues.toString())
        assertEquals(1,p.changes.count { it.instanceId == "stable-fire" })
        assertEquals(Amount(30,0),p.cost)
    }

    @Test fun attributes_replay_exact_base_and_include_targets_false_only_buys_requirements() {
        val attrRef = DefinitionRef("dubl-3.69","attribute:STRENGTH")
        val attr = definition("attribute").copy(ref=attrRef,kind=AcquisitionKind.ATTRIBUTE,maxRank=10,
            priceRule=PriceRule.TransitionTable(listOf(PriceRule.Transition(-1,0,50,0,source)),source))
        val need = RequirementNode.Attribute("strength",source,AttributeId.STRENGTH,ComparisonOperator.GE,0)
        val c = catalog(attr,definition("target",need))
        val original = state().copy(character=DublCharacter("hero",attributes=defaultAttributes()+(AttributeId.STRENGTH to AttributeValue(-1))))
        val p = plan(AcquisitionRequest("hero",request("target").targets,includeTargets=false),original,c)
        assertTrue(p.executable,p.issues.toString())
        assertEquals(Amount(50,0),p.cost)
        assertEquals(listOf(attrRef),p.changes.map { it.definition })
        assertEquals(0,p.projectedContext.character.attributes.getValue(AttributeId.STRENGTH).base)
        assertTrue(p.projectedContext.instances.isEmpty())
        assertTrue(validateAcquisitionProjection(p.changes,original,c).issues.isEmpty())
    }

    @Test fun replay_rejects_malformed_pending_confirmation_source() {
        val node = RequirementNode.ExternalEvidence("permission",source,"approval","scope")
        val c = catalog(definition("a",node))
        val invalid = RequirementWitness.External("approval","scope","id",source.copy(documentId=""))
        val change = AcquisitionChange(AcquisitionKind.DEVELOPMENT,ref("a"),"new",0,1,emptyMap(),null)
        assertTrue(validateAcquisitionProjection(listOf(change),state(),c,listOf(invalid)).issues.isNotEmpty())
        assertFalse(plan(AcquisitionRequest("hero",request("a").targets,externalConfirmations=listOf(invalid)),state(),c).executable)
    }

    @Test fun owned_prerequisite_repairs_missing_ancestor_without_charging_owned_rank() {
        val c = catalog(definition("b"),definition("a",owned("b")),definition("target",owned("a")))
        val original = state(listOf(instance("a")))
        val direct = plan(request("a"),original,c)
        assertTrue(direct.executable)
        assertEquals(emptyList(),direct.changes)
        assertEquals(Amount.ZERO,direct.cost)
        val requested = request("target")
        val explicit = AcquisitionRequest("hero",requested.targets,prerequisiteSelections=mapOf(
            acquisitionChoiceId(ref("target"),"need/a") to listOf(AcquisitionTarget(ref("a"),"owned/a",1))))
        for (r in listOf(requested,explicit)) {
            val repaired = plan(r,original,c)
            assertTrue(repaired.executable,repaired.issues.toString())
            assertEquals(listOf(ref("b"),ref("target")),repaired.changes.map { it.definition })
            assertEquals(Amount(20,0),repaired.cost)
            assertEquals(Amount(80,10),repaired.remaining)
            assertEquals(instance("a"),repaired.projectedContext.instances.single { it.definition == ref("a") })
            assertTrue(validateAcquisitionProjection(repaired.changes,original,c).issues.isEmpty())
        }
    }

    @Test fun owned_prerequisite_repair_retains_explicit_choices_and_cycle_bounds() {
        val alternatives = RequirementNode.AnyOf("repair-choice",source,listOf(owned("b"),owned("c")))
        val c = catalog(definition("b"),definition("c",price=20),definition("a",alternatives),definition("target",owned("a")))
        val original = state(listOf(instance("a")))
        val first = plan(request("target"),original,c)
        assertFalse(first.executable)
        val question = first.questions.single()
        assertEquals(setOf(Amount(10,0),Amount(20,0)),question.options.map { assertIs<PriceResult.Known>(it.quote).cost }.toSet())
        val selected = AcquisitionRequest("hero",request("target").targets,mapOf(question.id to "need/c"))
        val repaired = plan(selected,original,c)
        assertTrue(repaired.executable,repaired.issues.toString())
        assertEquals(listOf(ref("c"),ref("target")),repaired.changes.map { it.definition })
        assertEquals(Amount(30,0),repaired.cost)
        assertTrue(validateAcquisitionProjection(repaired.changes,original,c).issues.isEmpty())
        val cycle = catalog(definition("a",owned("b")),definition("b",owned("a")),definition("target",owned("a")))
        val blocked = plan(request("target"),original,cycle)
        assertFalse(blocked.executable)
        assertTrue(blocked.changes.isEmpty())
        assertTrue(blocked.issues.any { it.reason == "Cyclic dependency" })
    }

    @Test fun unrelated_mutation_records_cannot_donate_acquisition_confirmations() {
        val confirmation = RequirementWitness.External("approval","scope","confirmed",source)
        val c = catalog(definition("target",RequirementNode.ExternalEvidence("permission",source,"approval","scope")))
        val original = state()
        for (type in LedgerEntryType.entries.filter { it != LedgerEntryType.Purchase && it != LedgerEntryType.Undo }) {
            val entry = LedgerEntry("other",type,emptyList(),Amount.ZERO,RuleVersions("c1","r1"),
                creationPhase=original.creationPhase,evidenceCanonical=encodeAcquisitionEvidence(listOf(confirmation)))
            val receipt = Receipt("other","{\"characterId\":\"hero\"}",SavedAcquisitionResult(Amount.ZERO,Amount(100,10),emptyList()),
                ReceiptStatus.Applied,0,1)
            val persisted = original.copy(ledger=original.ledger.copy(entries=listOf(entry)),receipts=listOf(receipt),revision=1,storageRevision=1)
            if(type==LedgerEntryType.Import || type==LedgerEntryType.SyncAdoption) {
                // These typed history boundaries additionally require exact source/archive proofs.
                assertFailsWith<IllegalArgumentException>(type.name) { encodeEnvelope(persisted) }
                continue
            }
            val loaded = assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(persisted))).state
            assertTrue(retainedAcquisitionConfirmations(loaded).isEmpty(),type.name)
            assertFalse(plan(request("target"),loaded,c).executable,type.name)
        }
    }
}
