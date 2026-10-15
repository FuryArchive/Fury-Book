package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AcquisitionIncludedGrantsTest {
    private val catalog by lazy { loadAcquisitionCatalog(listOf(FcpContentPack.load("fcp/dubl-3.69",
        FcpTextSource { javaClass.classLoader.getResource(it)?.readText() })), false).catalog }
    private fun ref(id: String) = DefinitionRef("dubl-3.69", id)
    private val initiate = ref("feat_6e5f776e24effe94")
    private val gift = ref("spell_64339dedae079bd8")
    private fun initial(): StoredCharacter {
        fun owned(id: String, rank: Int) = AcquisitionInstance("owned/$id", ref(id), rank, emptyMap(), null, AcquisitionOrigin.New)
        val phase = RequirementNode.CreationPhase.Phase.Creating
        return StoredCharacter(DublCharacter("grant", magic = CharacterMagic(manaRank = 2, power = 4)),
            CharacterSheetExtras(), listOf(owned("mana:rank", 2), owned("access_c55a5bee28052c88", 1),
                owned("feat_7576d0a496b3b046", 1)),
            Ledger(Opening("opening", "confirmed-current-source", Amount(1000, 10), phase, OpeningProvenance.NewCharacter), emptyList()),
            emptyList(), emptyList(), SyncMetadata(0), 0, 0, phase)
    }

    @Test fun actual_catalog_keeps_the_unresolved_mystic_knight_admission_blocked() {
        val request = AcquisitionRequest("grant", listOf(AcquisitionTarget(initiate, "initiate", 1,
            mapOf("grantedSpell1" to gift.entryId))))
        val preview = plan(request, initial(), catalog)
        assertFalse(preview.executable)
        assertTrue(preview.issues.any { it.status == RequirementStatus.SourceUnresolved &&
            (it.reason.contains("Сила заклинаний") || it.definition == ref("feat_7576d0a496b3b046")) })
        assertTrue(preview.changes.none { it.definition == initiate || it.definition == gift })
    }

    @Test fun actual_grant_price_and_choices_are_atomic_under_explicit_controlled_admission() = runBlocking {
        val before = initial()
        // The current Mystic Knight prerequisite contains an unresolved «Сила заклинаний:4» atom.
        // This fixture isolates inclusion only; it does not assert acceptance of that real source chain.
        val admission = RuleSourceRef("test-controlled-admission", "1", "This isolated fixture admits the grantor.")
        val definition = catalog.definitions.getValue(initiate)
        assertEquals(5, definition.includedGrants.size)
        val controlled = catalog.copy(catalogRevision = catalog.catalogRevision + "/controlled-admission",
            definitions = catalog.definitions + (initiate to definition.copy(requirements =
                RequirementNode.AllOf("test/admitted", admission, emptyList(), NoRequirementsEvidence(admission, "Explicit fixture admission")))))
        val store = InMemoryAcquisitionStore(mapOf("grant" to encodeEnvelope(before)))
        val service = AcquisitionService(store, { controlled }, { "generated" })
        // Including the explicit spell target before its grantor must not charge its ordinary10XP price.
        val request = AcquisitionRequest("grant", listOf(AcquisitionTarget(gift, targetRank = 1),
            AcquisitionTarget(initiate, "initiate", 1, mapOf("grantedSpell1" to gift.entryId))))
        val preview = service.preview(request)
        assertTrue(preview.executable, preview.issues.toString())
        assertEquals(Amount(15, 0), preview.cost)
        assertEquals(listOf(initiate, gift), preview.changes.map { it.definition })
        assertEquals(listOf(Amount(15, 0), Amount.ZERO), preview.prices.map { it.cost })
        val command = ConfirmAcquisition("learn-included", "grant", request, preview.baseRevision, preview.previewToken)
        val receipt = assertIs<ApplyResult.Applied>(service.apply(command)).receipt
        assertEquals(preview.cost, receipt.result.cost)
        assertIs<ApplyResult.AlreadyApplied>(service.apply(command))
        val saved = assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(assertIs<LoadResult.Loaded>(store.load("grant")).state))).state
        assertTrue(saved.character.magic.spells.single { it.catalogId == gift.entryId }.learned)
        assertEquals(1, saved.instances.count { it.definition == gift })
        assertEquals(Amount(985, 10), balance(saved.ledger))
        assertIs<ApplyResult.Applied>(AcquisitionUndo(store).undo("grant", "undo-included", saved.revision))
        assertEquals(acquisitionProjectionCanonical(before), acquisitionProjectionCanonical(assertIs<LoadResult.Loaded>(store.load("grant")).state))
    }

    @Test fun existing_initiate_does_not_make_an_unrelated_future_spell_purchase_free() {
        val existing = AcquisitionInstance("old-initiate", initiate, 1, mapOf("grantedSpell1" to gift.entryId), null, AcquisitionOrigin.New)
        val before = initial().let { it.copy(instances = it.instances + existing) }
        val preview = plan(AcquisitionRequest("grant", listOf(AcquisitionTarget(gift, targetRank = 1))), before, catalog)
        assertTrue(preview.executable, preview.issues.toString())
        assertEquals(Amount(10, 0), preview.cost)
        assertEquals(listOf(gift), preview.changes.map { it.definition })
        assertEquals(before.instances, acquisitionContext(before, catalog).instances)
    }
}
