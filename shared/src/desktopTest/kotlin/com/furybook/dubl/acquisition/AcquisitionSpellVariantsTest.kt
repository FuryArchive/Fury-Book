package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.content.FcpTextSource
import com.furybook.dubl.model.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AcquisitionSpellVariantsTest {
    @Test fun actual_elemental_spear_preserves_each_learned_element_and_undo_removes_only_the_last()=runBlocking {
        val catalog=loadAcquisitionCatalog(listOf(FcpContentPack.load("fcp/dubl-3.69",
            FcpTextSource { javaClass.classLoader.getResource(it)?.readText() })),false).catalog
        val ref=DefinitionRef("dubl-3.69","spell_c09c1ac0315b29ca")
        val phase=RequirementNode.CreationPhase.Phase.Creating
        val initial=StoredCharacter(DublCharacter("spear-caster",magic=CharacterMagic(manaRank=1,power=3)),CharacterSheetExtras(),emptyList(),
            Ledger(Opening("opening","current-native-spear",Amount(100,3),phase,OpeningProvenance.NewCharacter),emptyList()),
            emptyList(),emptyList(),SyncMetadata(0),0,0,phase)
        val store=InMemoryAcquisitionStore(mapOf("spear-caster" to encodeEnvelope(initial)))
        val service=AcquisitionService(store,{catalog},{"id"})
        suspend fun buy(element:String):ConfirmAcquisition {
            val request=AcquisitionRequest("spear-caster",listOf(AcquisitionTarget(ref,"spear/$element",1,mapOf("elementId" to element))))
            val quote=service.preview(request)
            assertTrue(quote.executable,quote.issues.toString())
            assertEquals(Amount(10,0),quote.cost)
            val command=ConfirmAcquisition("learn/$element","spear-caster",request,quote.baseRevision,quote.previewToken)
            assertIs<ApplyResult.Applied>(service.apply(command))
            return command
        }
        buy("fire")
        val cold=buy("cold")
        val saved=assertIs<LoadResult.Loaded>(decodeEnvelope(encodeEnvelope(assertIs<LoadResult.Loaded>(store.load("spear-caster")).state))).state
        assertEquals(2,saved.character.magic.spells.size)
        assertEquals(setOf("spear/fire","spear/cold"),saved.character.magic.spells.map { it.uid }.toSet())
        assertEquals(setOf("Стихийное копьё (Огонь)","Стихийное копьё (Холод)"),saved.character.magic.spells.map { it.name }.toSet())
        assertEquals(Amount(80,3),balance(saved.ledger))
        assertIs<ApplyResult.AlreadyApplied>(service.apply(cold))
        assertFalse(plan(AcquisitionRequest("spear-caster",listOf(AcquisitionTarget(ref,"duplicate-fire",1,mapOf("elementId" to "fire")))),saved,catalog).executable)
        val undo=AcquisitionUndo(store)
        assertIs<UndoEligibility.Available>(undo.eligible("spear-caster"))
        assertIs<ApplyResult.Applied>(undo.undo("spear-caster","undo-cold",saved.revision))
        val restored=assertIs<LoadResult.Loaded>(store.load("spear-caster")).state
        assertEquals(listOf("spear/fire"),restored.character.magic.spells.map { it.uid })
        assertEquals(listOf("fire"),restored.instances.map { it.parameters.getValue("elementId") })
        assertEquals(Amount(90,3),balance(restored.ledger))
    }
}
