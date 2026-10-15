package com.furybook.dubl.acquisition

import com.furybook.content.FcpTextSource
import com.furybook.dubl.content.DublFcp
import com.furybook.dubl.model.*
import kotlin.test.*

class AcquisitionNativeAccessChoicesTest {
    @Test fun berserk_payment_alternatives_keep_their_source_meanings_and_prices() {
        val catalog = DublFcp.open(FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }).loadAcquisition().catalog
        val ref = DefinitionRef("dubl-3.69", "access_5b50c9c8ead88fd5")
        val creating = RequirementNode.CreationPhase.Phase.Creating
        val state = StoredCharacter(DublCharacter("hero"),CharacterSheetExtras(),emptyList(),
            Ledger(Opening("opening","current-corebook",Amount(1000,10),creating,OpeningProvenance.NewCharacter),emptyList()),
            emptyList(),emptyList(),SyncMetadata(0),0,0,creating)
        val scope = AcquisitionScope("store","hero",0,0,0)
        val request = AcquisitionRequest("hero",listOf(AcquisitionTarget(ref,targetRank=1)))
        val details = acquisitionReviewDetails(scope,AcquisitionQuoteKey("review",0),request,state,catalog)
        val options = details.abilityChoices.single().options.associateBy { it.id }
        assertEquals("Мораль",options.getValue("morale").label)
        assertEquals("Мастерство и Мораль",options.getValue("mastery-morale").label)
        assertEquals(Amount(0,1),assertIs<AcquisitionDisplayedPrice.Known>(options.getValue("morale").price).amount)
        assertEquals(Amount(0,2),assertIs<AcquisitionDisplayedPrice.Known>(options.getValue("mastery-morale").price).amount)
        assertNull(details.abilityChoices.single().selectedId)
    }
}
