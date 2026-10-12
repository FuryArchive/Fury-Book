package com.furybook.dubl.acquisition

import com.furybook.content.*
import com.furybook.dubl.model.*
import com.furybook.dubl.data.SnapshotCodec
import kotlin.test.*

/** Literal expected balances captured by executing f47a's calculator in an isolated compiler harness. */
class DesktopLegacyAcquisitionMigrationTest {
    private val source=FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }
    private val pack get()=FcpContentPack.load("fcp/dubl-3.69",source)
    private fun raw(name:String)=javaClass.classLoader.getResource("legacy-v11/$name.json")!!.readText()
    private fun origin(raw:String)=LegacyOrigin("old",11,"old-revision-8",raw,listOf("dubl-3.69"))
    private fun migrate(name:String):StoredCharacter {val raw=raw(name);return assertIs<MigrationResult.Ready>(prepareLegacy(raw,origin(raw),loadLegacyCatalogSnapshot(pack,listOf("dubl-3.69")))).state}

    @Test fun preserve_old_complete_and_incomplete() {
        for(done in listOf(false,true)) {
            val state=migrate("self_$done");assertEquals(Amount(100,0),balance(state.ledger));assertEquals(done,state.character.creationComplete)
            assertEquals(if(done) RequirementNode.CreationPhase.Phase.Finalized else RequirementNode.CreationPhase.Phase.Creating,state.creationPhase)
            assertEquals(0,state.revision);assertEquals(OpeningProvenance.LegacyCalculated,state.ledger.opening.provenance)
            assertEquals(state,assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(state))).state)
            assertTrue(state.instances.any {it.definition.entryId=="athletics" && it.rank==2})
        }
    }
    @Test fun do_not_reprice_old_eagle_and_local_overrides() {
        assertEquals(Amount(100,0),balance(migrate("eagle").ledger))
        assertEquals(Amount(1624,-3),balance(migrate("mixed").ledger))
    }
    @Test fun negative_balance_blocks_real_planner() {
        val state=migrate("negative");assertEquals(Amount(-10,0),balance(state.ledger))
        val ref=DefinitionRef("dubl-3.69","test-purchase");val source=RuleSourceRef("test","v1",originalText="20 XP")
        val definition=AcquisitionDefinition(ref,AcquisitionKind.DEVELOPMENT,1,RepeatPolicy.Single,RequirementNode.AllOf("all",source,emptyList(),NoRequirementsEvidence(source,"No prerequisites")),PriceRule.Fixed(20,0,source),SourceStatus.Confirmed,"Test",source)
        val plan=plan(AcquisitionRequest("old",listOf(AcquisitionTarget(ref,targetRank=1))),state,AcquisitionCatalog(mapOf(ref to definition),"test","test"))
        assertFalse(plan.executable);assertEquals(Amount(-30,0),plan.remaining)
    }
    @Test fun explicit_school_mapping_and_stable_non_development_instances() {
        val snapshot=loadLegacyCatalogSnapshot(pack,listOf("dubl-3.69"))!!
        val mixed=raw("mixed");val initial=assertIs<MigrationResult.Ready>(prepareLegacy(mixed,origin(mixed),snapshot)).state
        assertEquals(3,initial.character.development["feat_9012c539238a1e6f"]!!.rank)
        assertTrue(initial.instances.any {it.definition.entryId=="mana:rank" && it.rank==1})
        assertTrue(initial.instances.none {it.definition.entryId.startsWith("magic-school:")})
        val key=legacySchoolOwnershipKey("Боевая магия");val ref=DefinitionRef("dubl-3.69","magic-school:боевая магия")
        val mappedOrigin=origin(mixed).copy(ownershipMappings=mapOf(key to ref))
        val mapped=assertIs<MigrationResult.Ready>(prepareLegacy(mixed,mappedOrigin,snapshot)).state
        assertEquals(2,mapped.instances.single {it.definition==ref}.rank)
        assertEquals(mapped,assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(mapped))).state)
        assertEquals(mapped,assertIs<MigrationResult.Ready>(prepareLegacy(mixed,mappedOrigin,snapshot)).state)
        val p=mapped.ledger.opening.legacyMigration!!
        val altered=p.copy(origin=p.origin.copy(ownershipMappings=mapOf(key to DefinitionRef("unrelated","fake"))))
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(mapped.copy(ledger=mapped.ledger.copy(opening=mapped.ledger.opening.copy(legacyMigration=altered)))) }
    }
    @Test fun spell_proof_requires_explicit_unique_catalog_id_and_unknown_parameters_stay_absent() {
        val acquisition=loadAcquisitionCatalog(listOf(pack),false).catalog
        val spell=acquisition.definitions.values.first {it.kind==AcquisitionKind.SPELL}.ref
        val c=DublCharacter("old",experience=100,magic=CharacterMagic(spells=listOf(
            KnownSpell("a",catalogId=spell.entryId,name="renamed",cost=1),
            KnownSpell("b",name=spell.entryId,cost=1),
            KnownSpell("c",catalogId=spell.entryId,learned=false),
            KnownSpell("d",catalogId=spell.entryId,custom=true))),
            development=mapOf("feat_7fc6fc6e63da9688" to OwnedDevelopment(1,99)))
        val raw=SnapshotCodec.encode(AppSnapshot(listOf(c),c.id))
        val state=assertIs<MigrationResult.Ready>(prepareLegacy(raw,origin(raw),loadLegacyCatalogSnapshot(pack,listOf("dubl-3.69")))).state
        assertEquals(setOf(spell),acquisitionContext(state,acquisition).learnedSpellIds)
        assertEquals(1,state.instances.count { it.definition==spell })
        val owned=state.instances.single { it.definition.entryId=="feat_7fc6fc6e63da9688" }
        assertTrue(owned.parameters.isEmpty());assertNull(owned.abilityOptionId)
        val d=acquisition.definitions.getValue(owned.definition)
        assertIs<IdentityValidation.Blocked>(validateAcquisitionTarget(d.ref,3,d.parameterDefinitions,d.repeatPolicy,owned.instanceId,2,emptyMap(),null,state.instances,d.abilityOptionIds))
    }
    @Test fun chi_composition_changes_only_old_known_components_and_no_double_mana_charge() {
        val raw=raw("mixed");val packs=listOf("dubl-3.69","dubl-chi-3.69")
        val state=assertIs<MigrationResult.Ready>(prepareLegacy(raw,origin(raw).copy(activePackIds=packs),loadLegacyCatalogSnapshot(pack,packs))).state
        assertEquals(Amount(1524,-3),balance(state.ledger))
        assertEquals(2,state.instances.single {it.definition.entryId=="chi:bonus-ranks"}.rank)
        val old=raw("legacy_mana")
        val mana=assertIs<MigrationResult.Ready>(prepareLegacy(old,origin(old),loadLegacyCatalogSnapshot(pack,listOf("dubl-3.69")))).state
        assertEquals(Amount(100,0),balance(mana.ledger));assertEquals(3,mana.character.magic.manaRank)
        assertEquals(listOf("mana:rank"),mana.instances.map {it.definition.entryId})
    }
    @Test fun builtin_legacy_skill_key_is_an_explicit_id_but_display_name_is_not() {
        val c=DublCharacter("old",skills=mapOf("athletics" to CharacterSkill("athletics",name="Renamed",rank=2),
            "manual" to CharacterSkill("manual",name="Атлетика",rank=1)))
        val raw=SnapshotCodec.encode(AppSnapshot(listOf(c),c.id))
        val state=assertIs<MigrationResult.Ready>(prepareLegacy(raw,origin(raw),loadLegacyCatalogSnapshot(pack,listOf("dubl-3.69")))).state
        assertEquals(listOf("athletics"),state.instances.map {it.definition.entryId})
        assertEquals(2,state.instances.single().rank)
    }
    @Test fun missing_or_modified_pinned_input_requires_confirmation() {
        for(transform in listOf<(String)->String?>({null},{it.replace("\"cost\": 0","\"cost\": 45")})) {
            val altered=FcpContentPack.load("fcp/dubl-3.69",FcpTextSource { path ->
                source.readText(path)?.let { if(path.endsWith("legacy-v11-catalog.json")) transform(it) else it }
            })
            assertNull(loadLegacyCatalogSnapshot(altered,listOf("dubl-3.69")))
        }
    }
    @Test fun unavailable_composition_does_not_mean_zero() {
        val raw=raw("eagle")
        assertNull(loadLegacyCatalogSnapshot(pack,listOf("dubl-3.69","missing-addon")))
        assertIs<MigrationResult.NeedsBalanceConfirmation>(prepareLegacy(raw,origin(raw).copy(activePackIds=listOf("dubl-3.69","missing-addon")),loadLegacyCatalogSnapshot(pack,listOf("dubl-3.69"))))
    }
}
