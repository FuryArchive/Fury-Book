package com.furybook.dubl.acquisition

import com.furybook.dubl.data.CharacterTransferCodec
import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.model.*
import kotlin.test.*

class LegacyAcquisitionMigrationTest {
    private fun raw(c:DublCharacter=DublCharacter(id="old",experience=100)) = " \n" + SnapshotCodec.encode(AppSnapshot(listOf(c),c.id)) + "\n"
    private fun origin(raw:String, revision:String="4") = LegacyOrigin("old",11,revision,raw,listOf("dubl-3.69"))

    @Test fun missing_catalog_requires_confirmation_and_persists_exact_backup() {
        val raw=raw(); val pending=assertIs<MigrationResult.NeedsBalanceConfirmation>(prepareLegacy(raw,origin(raw),null)).pending
        assertFailsWith<IllegalArgumentException> { confirmLegacyBalance(pending,Amount(100,2)," ") }
        val state=confirmLegacyBalance(pending,Amount(100,2),"Player verified old sheet")
        assertEquals(Amount(100,2),balance(state.ledger));assertEquals(OpeningProvenance.UserConfirmed,state.ledger.opening.provenance)
        val loaded=assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(state))).state
        assertEquals(state,loaded); assertEquals(raw,loaded.ledger.opening.legacyMigration!!.origin.raw)
        assertEquals("4",loaded.ledger.opening.legacyMigration.origin.sourceRevision)
        assertEquals(0,state.revision); assertTrue(state.ledger.entries.isEmpty());assertTrue(state.receipts.isEmpty())
        assertEquals(state,assertIs<LoadResult.Loaded>(CharacterTransferCodec.decodeEnvelope(CharacterTransferCodec.encodeEnvelope(state))).state)
        assertEquals(state,assertIs<MigrationResult.Ready>(prepareLegacy(encodeEnvelope(state),origin(raw),null)).state)
    }
    @Test fun repeat_import_same_origin_once_and_different_revision_conflicts() {
        val raw=raw();fun migrate(revision:String)=confirmLegacyBalance(assertIs<MigrationResult.NeedsBalanceConfirmation>(prepareLegacy(raw,origin(raw,revision),null)).pending,Amount(100,0),"Known balance")
        val one=migrate("4"); val two=migrate("4");val other=migrate("5")
        assertEquals(one,two);assertEquals(LegacyMigrationComparison.SameOrigin,compareLegacyMigrations(one,two))
        assertEquals(LegacyMigrationComparison.Conflict,compareLegacyMigrations(one,other));assertNotEquals(one.ledger.opening.migrationId,other.ledger.opening.migrationId)
    }
    @Test fun malformed_and_mismatched_sources_never_supply_fresh_character() {
        val raw=raw()
        assertIs<MigrationResult.Corrupt>(prepareLegacy("{}",origin(raw),null))
        assertIs<MigrationResult.Corrupt>(prepareLegacy(raw,origin(raw).copy(characterId="other"),null))
        assertIs<MigrationResult.Corrupt>(prepareLegacy(raw,origin(raw).copy(schema=10),null))
        assertIs<MigrationResult.Unsupported>(prepareLegacy(raw.replace("\"schema\":11","\"schema\":99"),origin(raw),null))
    }
    private val proof=RuleSourceRef("test","v1","No prerequisites; 20XP")
    private fun definition(id:String,kind:AcquisitionKind=AcquisitionKind.DEVELOPMENT,requirements:RequirementNode=RequirementNode.AllOf("free",proof,emptyList(),NoRequirementsEvidence(proof,"None"))) =
        AcquisitionDefinition(DefinitionRef("other-pack",id),kind,3,RepeatPolicy.Single,requirements,PriceRule.PerRank(20,0,proof),SourceStatus.Confirmed,id,proof)
    private fun confirmed(c:DublCharacter):StoredCharacter {
        val raw=raw(c)
        return confirmLegacyBalance(assertIs<MigrationResult.NeedsBalanceConfirmation>(prepareLegacy(raw,origin(raw),null)).pending,Amount(100,0),"Verified balance")
    }
    @Test fun confirmed_missing_catalog_blocks_duplicate_and_prerequisite_but_allows_unrelated() {
        val state=confirmed(DublCharacter("old",development=mapOf("known-id" to OwnedDevelopment(1))))
        val duplicate=definition("known-id");val unrelated=definition("other-id")
        val dependent=definition("dependent",requirements=RequirementNode.OwnedRank("need",proof,"other-pack","known-id",ComparisonOperator.GE,1))
        val catalog=AcquisitionCatalog(listOf(duplicate,unrelated,dependent).associateBy {it.ref},"test","test")
        fun request(d:AcquisitionDefinition)=AcquisitionRequest("old",listOf(AcquisitionTarget(d.ref,targetRank=1)))
        assertEquals(listOf("development:known-id"),unresolvedLegacyOwnership(state,catalog).map {it.ownershipKey})
        val evidence=state.ledger.opening.legacyMigration!!
        val tamperedOpening=state.ledger.opening.copy(legacyMigration=evidence.copy(ownershipRecords=emptyList()))
        assertFailsWith<IllegalArgumentException> { encodeEnvelope(state.copy(ledger=state.ledger.copy(opening=tamperedOpening))) }
        assertFalse(plan(request(duplicate),state,catalog).executable)
        assertFalse(plan(request(dependent),state,catalog).executable)
        assertTrue(plan(request(unrelated),state,catalog).executable)
        val change=AcquisitionChange(AcquisitionKind.DEVELOPMENT,duplicate.ref,"attempt",0,1,emptyMap(),null)
        assertTrue(validateAcquisitionProjection(listOf(change),state,catalog).issues.isNotEmpty())
        val o=state.ledger.opening.legacyMigration!!.origin
        val legacy=AcquisitionOrigin.Legacy(o.characterId,o.schema.toString(),o.sourceRevision,o.sourceFingerprint,"development:known-id")
        val resolved=state.copy(instances=listOf(AcquisitionInstance(legacyInstanceId(duplicate.ref,legacy),duplicate.ref,1,emptyMap(),null,legacy)))
        assertEquals(resolved,assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(resolved))).state)
        assertTrue(unresolvedLegacyOwnership(resolved,catalog).isEmpty())
        assertTrue(plan(request(duplicate),resolved,catalog).executable)
        assertEquals(Amount.ZERO,plan(request(duplicate),resolved,catalog).cost)
        assertTrue(plan(request(dependent),resolved,catalog).executable)
        assertEquals(Amount(100,0),balance(resolved.ledger))
        val wrong=resolved.instances.single().copy(origin=legacy.copy(ownershipKey="development:wrong"))
        assertFalse(plan(request(duplicate),state.copy(instances=listOf(wrong)),catalog).executable)
    }
    @Test fun unmapped_schools_block_schools_but_not_other_kinds_and_explicit_custom_spells_do_not_block() {
        val state=confirmed(DublCharacter("old",magic=CharacterMagic(schools=listOf(MagicSchool("Unknown school",2)),
            spells=listOf(KnownSpell("custom",custom=true)))))
        val school=definition("school",AcquisitionKind.SCHOOL);val other=definition("other");val spell=definition("spell",AcquisitionKind.SPELL)
        val catalog=AcquisitionCatalog(listOf(school,other,spell).associateBy {it.ref},"test","test")
        fun preview(d:AcquisitionDefinition)=plan(AcquisitionRequest("old",listOf(AcquisitionTarget(d.ref,targetRank=1))),state,catalog)
        assertFalse(preview(school).executable);assertTrue(preview(other).executable);assertTrue(preview(spell).executable)
    }
    @Test fun fingerprint_vectors_and_immutable_origin() {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",legacySha256(byteArrayOf()))
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",legacySha256("abc".encodeToByteArray()))
        assertNotEquals(legacySourceFingerprint("x"),legacySourceFingerprint("x "))
        assertNotEquals(legacySourceFingerprint("\uD800"),legacySourceFingerprint("?"))
        val packs=mutableListOf("dubl-3.69");val mappings=mutableMapOf("school" to DefinitionRef("pack","id"))
        val o=LegacyOrigin("old",11,"1",raw(),packs,mappings);packs.clear();mappings.clear()
        assertEquals(listOf("dubl-3.69"),o.activePackIds);assertEquals(1,o.ownershipMappings.size)
        assertEquals(o,o.copy());assertEquals(o.hashCode(),o.copy().hashCode())
    }
    @Test fun canonical_manual_values_are_never_renormalized() {
        val raw=raw();val initial=confirmLegacyBalance(assertIs<MigrationResult.NeedsBalanceConfirmation>(prepareLegacy(raw,origin(raw),null)).pending,Amount(100,0),"Verified")
        val manual=initial.copy(character=initial.character.copy(experience=-20,skills=mapOf("manual" to CharacterSkill("manual",rank=17))))
        val wire=encodeEnvelope(manual)
        assertEquals(manual,assertIs<MigrationResult.Ready>(prepareLegacy(wire,origin(raw),null)).state)
    }
    @Test fun provenance_tampering_is_rejected() {
        val raw=raw();val state=confirmLegacyBalance(assertIs<MigrationResult.NeedsBalanceConfirmation>(prepareLegacy(raw,origin(raw),null)).pending,Amount(-10,-1),"Verified debt")
        val p=state.ledger.opening.legacyMigration!!
        for(bad in listOf(p.copy(origin=p.origin.copy(raw=raw+" ")),p.copy(origin=p.origin.copy(characterId="other")),p.copy(historyUnknown=false),p.copy(catalogIdentity="forged-catalog"))) {
            assertFailsWith<IllegalArgumentException> { encodeEnvelope(state.copy(ledger=state.ledger.copy(opening=state.ledger.opening.copy(legacyMigration=bad)))) }
        }
    }
}
