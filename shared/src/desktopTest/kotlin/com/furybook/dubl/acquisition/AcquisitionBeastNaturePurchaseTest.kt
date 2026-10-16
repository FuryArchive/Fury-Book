package com.furybook.dubl.acquisition

import com.furybook.content.FcpTextSource
import com.furybook.dubl.content.DublFcp
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

/** Buying a single ability is distinct from automating its per-form game effects. */
class AcquisitionBeastNaturePurchaseTest {
    @Test fun actual_first_rank_needs_animagia_four_without_inventing_a_form_prerequisite() = runBlocking {
        val catalog = DublFcp.open(FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }).loadAcquisition().catalog
        val ref = DefinitionRef("dubl-3.69", "feat_fc1f69730433e92b")
        val animagia = DefinitionRef("dubl-3.69", "feat_29ff1cb23df7879d")
        val phase = RequirementNode.CreationPhase.Phase.Creating
        val before = StoredCharacter(DublCharacter("beast", development = mapOf(animagia.entryId to OwnedDevelopment(4))),
            CharacterSheetExtras(), listOf(AcquisitionInstance("known-animagia", animagia, 4, emptyMap(), null, AcquisitionOrigin.New)),
            Ledger(Opening("opening", "confirmed-existing-balance", Amount(1000, 10), phase, OpeningProvenance.UserConfirmed,
                "Existing Animagia is already paid; no beast form is invented"), emptyList()),
            emptyList(), emptyList(), SyncMetadata(0), 0, 0, phase)
        val store = InMemoryAcquisitionStore(mapOf("beast" to encodeEnvelope(before)))
        val service = AcquisitionService(store, { catalog }, { "generated" })
        val request = AcquisitionRequest("beast", listOf(AcquisitionTarget(ref, targetRank = 1)))
        val preview = service.preview(request)
        assertTrue(preview.executable, preview.issues.toString())
        assertEquals(Amount(60, 0), preview.cost)
        assertEquals(listOf(ref), preview.changes.map { it.definition })
        assertTrue(preview.changes.single().parameters.isEmpty(), "A form or effect choice must not be silently invented")
        val model = AcquisitionPresentation.present(AcquisitionScope("local", "beast", 0, 0, 0),
            AcquisitionQuoteKey("nature", 0), preview, before, catalog, AcquisitionCapabilities(true, false, true))
        assertEquals(AcquisitionUiPhase.Ready, model.phase)
        assertTrue(model.canConfirm)
        assertEquals(1, model.effectNotices.size)
        assertTrue(model.effectNotices.single().contains("способности освоенных форм выбираются отдельно"))
        assertTrue(model.effectNotices.single().contains("пока не поддержаны"))
        val result = assertIs<ApplyResult.Applied>(service.apply(ConfirmAcquisition("buy-nature", "beast", request,
            preview.baseRevision, preview.previewToken)))
        assertEquals(Amount(60, 0), result.receipt.result.cost)
        val after = assertIs<LoadResult.Loaded>(store.load("beast")).state
        val reopened = assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(after))).state
        assertEquals(1, reopened.character.development.getValue(ref.entryId).rank)
        assertEquals(4, reopened.character.development.getValue(animagia.entryId).rank)
        assertEquals(Amount(940, 10), balance(reopened.ledger))
        assertEquals(2, reopened.instances.size)
        assertFalse(plan(request.copy(targets = listOf(AcquisitionTarget(ref, targetRank = 2))), reopened, catalog).executable,
            "Unknown later-rank prices stay unknown")
        assertIs<ApplyResult.Applied>(AcquisitionUndo(store).undo("beast", "undo-nature", after.revision))
        assertEquals(acquisitionProjectionCanonical(before), acquisitionProjectionCanonical(assertIs<LoadResult.Loaded>(store.load("beast")).state))
    }
}
