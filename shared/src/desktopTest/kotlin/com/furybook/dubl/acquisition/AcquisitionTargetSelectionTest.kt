package com.furybook.dubl.acquisition

import com.furybook.content.FcpTextSource
import com.furybook.dubl.content.DublFcp
import com.furybook.dubl.model.*
import kotlin.test.*

class AcquisitionTargetSelectionTest {
    @Test fun real_performance_definition_keeps_new_or_existing_choice_with_one_owned_rank_three() {
        val catalog=DublFcp.open(FcpTextSource { javaClass.classLoader.getResource(it)?.readText() }).loadAcquisition().catalog
        val ref=DefinitionRef("dubl-3.69","performance_template")
        val owned=AcquisitionInstance("performance-theatre",ref,3,mapOf("specialization" to "theatre","specializationName" to "Театр"),null,AcquisitionOrigin.New)
        val phase=RequirementNode.CreationPhase.Phase.Creating
        val state=StoredCharacter(DublCharacter("performer"),CharacterSheetExtras(),listOf(owned),
            Ledger(Opening("opening","confirmed-existing-balance",Amount(1000,1),phase,OpeningProvenance.NewCharacter),emptyList()),
            emptyList(),emptyList(),SyncMetadata(0),0,0,phase)
        val view=AcquisitionCharacterView(AcquisitionScope("store","performer",0,0,0),state,catalog,Amount(1000,1),emptyList(),emptyList(),
            AcquisitionSyncView(AcquisitionSyncStatus.LocalOnlyUnavailable))
        val fromCatalog=assertNotNull(acquisitionTargetForSelection(view,AcquisitionKind.SKILL,ref.entryId,4))
        assertNull(fromCatalog.instanceId)
        assertEquals(1,fromCatalog.targetRank)
        assertTrue(fromCatalog.parameters.isEmpty())
        assertIs<AcquisitionRouteResult.NeedsExplicitSelection>(AcquisitionCommandRouting.request(AcquisitionIntent.skill(fromCatalog),view.scope,state,catalog))
        val fromHolding=assertNotNull(acquisitionTargetForSelection(view,AcquisitionKind.SKILL,owned.instanceId,4))
        assertEquals(owned.instanceId,fromHolding.instanceId)
        assertEquals(4,fromHolding.targetRank)
        assertEquals(owned.parameters,fromHolding.parameters)
        assertIs<AcquisitionRouteResult.QuoteRequest>(AcquisitionCommandRouting.request(AcquisitionIntent.skill(fromHolding),view.scope,state,catalog))
    }
}
