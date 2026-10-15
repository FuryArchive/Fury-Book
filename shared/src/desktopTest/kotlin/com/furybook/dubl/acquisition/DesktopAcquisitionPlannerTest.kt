package com.furybook.dubl.acquisition

import com.furybook.content.*
import com.furybook.dubl.model.*
import com.furybook.core.json.*
import java.io.File
import kotlin.test.*

class DesktopAcquisitionPlannerTest {
    private val packs by lazy { listOf("dubl-3.69","dubl-chi-3.69").map { id ->
        FcpContentPack.load("fcp/$id",FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }) } }
    private fun catalog(chi: Boolean = false) = loadAcquisitionCatalog(packs,chi).catalog
    private fun ref(id: String) = DefinitionRef("dubl-3.69",id)
    private fun instance(id: String,rank: Int = 1,option: String? = null) = AcquisitionInstance("owned/$id",ref(id),rank,emptyMap(),option,AcquisitionOrigin.New)
    private fun state(instances: List<AcquisitionInstance> = emptyList(), creating: Boolean = true): StoredCharacter {
        val phase = if (creating) RequirementNode.CreationPhase.Phase.Creating else RequirementNode.CreationPhase.Phase.Finalized
        return StoredCharacter(DublCharacter("real",creationComplete=!creating,
            attributes=AttributeId.entries.associateWith { AttributeValue(6) }),CharacterSheetExtras(),instances,
            Ledger(Opening("test-funding","literal",Amount(100,10),phase,OpeningProvenance.NewCharacter),emptyList()),
            emptyList(),emptyList(),SyncMetadata(0),0,0,phase)
    }
    private fun request(id: String,option: String? = null) = AcquisitionRequest("real",listOf(AcquisitionTarget(ref(id),"new/$id",1,optionId=option)))

    @Test fun hook_trailing_requirement_and_other_martial_tails_never_silently_succeed() {
        val c = catalog()
        for (id in listOf("martial_technique_880f59e730436e3f","martial_technique_9bfa154b252a4237","martial_technique_e1110cd9a24338fd")) {
            val p = plan(request(id),state(),c)
            assertFalse(p.executable,id)
            assertTrue(p.issues.isNotEmpty(),id)
            assertNull(p.cost)
        }
        fun nodes(n: RequirementNode): List<RequirementNode> = listOf(n)+when(n) {
            is RequirementNode.AllOf -> n.children.flatMap(::nodes)
            is RequirementNode.AnyOf -> n.children.flatMap(::nodes)
            else -> emptyList()
        }
        assertTrue(nodes(c.definitions.getValue(ref("martial_technique_880f59e730436e3f")).requirements)
            .any { it is RequirementNode.OwnedRank && it.entryId == "feat_9a0dbfed2efa56b7" })
    }

    @Test fun actual_owned_cries_target_only_30xp_with_legal_ancestors() {
        val c = catalog()
        // Independently recorded source prerequisites: Commander Voice CHA4/CON4;
        // Unbreakable Spirit CON4/WIL4; Thunder Warning intimidation5; Rally eloquence4/leadership4.
        val base = listOf(instance("access_17f99cc35c559d82",option="morale"),instance("feat_a700b29906107b2b"),
            instance("feat_9813ba6aff7624ad"),instance("feat_ff626e2585de2dc7"),instance("intimidation",5),
            instance("feat_e000383baa5b3afa"),instance("eloquence",4),instance("leadership",4))
        for ((target,count) in listOf("feat_8b6f3261d7c5c8ea" to 2,"feat_127f2d478d76e66d" to 3)) {
            val owned = if (count==2) base.filterNot { it.definition.entryId == "feat_e000383baa5b3afa" } else base
            val original = state(owned,creating=false)
            val proof = evaluate(c.definitions.getValue(ref(target)).requirements,acquisitionContext(original,c))
            assertEquals(RequirementStatus.Satisfied,proof.status,proof.toString())
            val p = plan(request(target),original,c)
            assertTrue(p.executable,p.issues.toString())
            assertEquals(Amount(30,0),p.cost)
            assertEquals(Amount(70,10),p.remaining)
            assertEquals(listOf(ref(target)),p.changes.map { it.definition })
            assertTrue(validateAcquisitionProjection(p.changes,original,c).issues.isEmpty())
        }
    }

    @Test fun access_oracle_options_creation_and_postcreation() {
        val c = catalog()
        val matrix = generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .map { File(it,"rulesets/dubl-3.69/acquisition/expected_prices.json") }.first { it.isFile }
        for ((option,ap) in listOf("morale" to 1L,"chi" to 2L)) {
            val id = "combat_cries_access_${option}_creation"
            val literal = matrix.useLines { lines -> lines.first { it.startsWith("{\"caseId\":\"$id\",") }.removeSuffix(",") }
            val row = JsonReader(literal).read() as JsonValue.Obj
            assertEquals(ap,(row.values.getValue("expectedCostAP") as JsonValue.Num).raw.toLong())
            val original = state(listOf(instance("feat_a700b29906107b2b")))
            val p = plan(request("access_17f99cc35c559d82",option),original,c)
            assertTrue(p.executable,p.issues.toString())
            assertEquals(Amount(0,ap),p.cost); assertEquals(Amount(100,10-ap),p.remaining)
            val after = plan(request("access_17f99cc35c559d82",option),state(original.instances,creating=false),c)
            assertTrue(after.executable, after.issues.toString())
            assertEquals(Amount(0,ap), after.cost); assertEquals(Amount(100,10-ap), after.remaining)
            assertEquals("UR_CREATION_BALANCE_CARRYOVER_SAME_PRICE_CURRENCY",
                assertIs<PriceRule.Conditional>(c.definitions.getValue(ref("access_17f99cc35c559d82")).priceRule).whenFalse.sourceRef.resolutionId)
        }
    }

    @Test fun every_real_id_is_classified_and_stress_is_separate_from_valid_input() {
        for (chi in listOf(false,true)) {
            val c = catalog(chi)
            val definitions = c.definitions.values.filter { it.kind == AcquisitionKind.DEVELOPMENT }
            // Current catalog inventory; the frozen source-only price matrix has its own denominator.
            assertEquals(if (chi) 1261 else 1233,definitions.size)
            val counts = linkedMapOf("input-valid" to 0,"manual-blocked" to 0,"stress-rejected" to 0)
            // Explicitly funded, in-cap attributes, source-backed fixed skills only. No imaginary owned feats.
            val skills = c.definitions.values.filter { it.kind == AcquisitionKind.SKILL && it.repeatPolicy is RepeatPolicy.Single && it.maxRank != null }
                .map { AcquisitionInstance("baseline/${it.ref.entryId}",it.ref,it.maxRank!!,emptyMap(),null,AcquisitionOrigin.New) }
            val original = state(skills).let { it.copy(ledger=it.ledger.copy(opening=it.ledger.opening.copy(balance=Amount(100000,1000)))) }
            for (d in definitions) {
                val r = AcquisitionRequest("real",listOf(AcquisitionTarget(d.ref,"new/${d.ref.packId}/${d.ref.entryId}",1)))
                val p = plan(r,original,c)
                if (p.executable) {
                    counts["input-valid"] = counts.getValue("input-valid")+1
                    assertTrue(validateAcquisitionProjection(p.changes,original,c).issues.isEmpty(),d.ref.toString())
                    assertNotNull(p.cost); assertNotNull(p.remaining)
                    p.changes.forEach { change ->
                        val changed = c.definitions.getValue(change.definition)
                        if (changed.maxRank != null) {
                            assertTrue(change.toRank <= changed.maxRank, changed.ref.toString())
                        } else {
                            // A confirmed first purchase does not invent an unknown later rank ceiling.
                            assertEquals(0, change.fromRank, changed.ref.toString())
                            assertEquals(1, change.toRank, changed.ref.toString())
                            assertIs<RepeatPolicy.Single>(changed.repeatPolicy)
                            assertTrue(changed.classificationSources["purchase:initial-rank-confirmed"]
                                ?.originalText?.isNotBlank() == true, changed.ref.toString())
                        }
                    }
                } else {
                    counts["manual-blocked"] = counts.getValue("manual-blocked")+1
                    assertTrue(p.issues.isNotEmpty() || p.questions.any { !it.resolved },d.ref.toString())
                    assertNull(p.cost,d.ref.toString())
                }
                for (rank in listOf(-1,(d.maxRank ?: 10)+1)) {
                    val bad = plan(AcquisitionRequest("real",listOf(r.targets.single().copy(targetRank=rank))),original,c)
                    assertFalse(bad.executable,d.ref.toString()); assertTrue(bad.changes.isEmpty())
                    counts["stress-rejected"] = counts.getValue("stress-rejected")+1
                }
            }
            assertEquals(definitions.size,counts.getValue("input-valid")+counts.getValue("manual-blocked"))
            assertEquals(definitions.size*2,counts.getValue("stress-rejected"))
            assertTrue(counts.getValue("input-valid")>0); assertTrue(counts.getValue("manual-blocked")>0)
            println("T07 actual IDs chi=$chi $counts")
        }
    }

    @Test fun shared_martial_style_choices_remain_valid_after_the_first_chain_acquires_that_style() {
        val c = catalog()
        val hidari = ref("martial_technique_3ee589b0fb715375")
        val migi = ref("martial_technique_23fd4936857b5c0a")
        val style = ref("martial_style_2cb08b5fe00825ab")
        val original = state(listOf(instance("melee_weapon", 4), AcquisitionInstance("proficiency-polearms",
            ref("weapon_proficiency_template"), 1, mapOf("groupId" to "polearms", "groupName" to "Древковое"), null, AcquisitionOrigin.New)))
            .let { it.copy(ledger = it.ledger.copy(opening = it.ledger.opening.copy(balance = Amount(1000, 10)))) }
        val choices = listOf(hidari, migi).associate { entry ->
            acquisitionChoiceId(entry, entry.entryId + "/requirements/styles") to entry.entryId + "/requirements/style/0"
        }
        for (targets in listOf(listOf(hidari, migi), listOf(migi, hidari))) {
            val request = AcquisitionRequest("real", targets.map { AcquisitionTarget(it, targetRank = 1) }, choices)
            val result = plan(request, original, c)
            assertTrue(result.executable, result.issues.toString())
            assertEquals(Amount(120, 0), result.cost)
            assertEquals(1, result.changes.count { it.definition == style })
            assertEquals(setOf(style, migi, hidari), result.changes.map { it.definition }.toSet())
            val unknown = plan(AcquisitionRequest("real", request.targets,
                choices + (acquisitionChoiceId(migi, migi.entryId + "/requirements/styles") to "unknown-style")), original, c)
            assertFalse(unknown.executable)
            assertTrue(unknown.issues.any { it.status == RequirementStatus.Conflict })
            val irrelevant = plan(AcquisitionRequest("real", request.targets, choices + ("unrelated-choice" to "unrelated")), original, c)
            assertFalse(irrelevant.executable)
        }
    }
}
