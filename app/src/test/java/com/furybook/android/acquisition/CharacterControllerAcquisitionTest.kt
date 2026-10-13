package com.furybook.android.acquisition

import com.furybook.android.data.*
import com.furybook.android.state.*
import com.furybook.dubl.acquisition.*
import com.furybook.dubl.data.*
import com.furybook.dubl.model.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class CharacterControllerAcquisitionTest {
    @Test fun production_gate_is_readable_and_fixture_capability_uses_actual_controller()=runBlocking<Unit> {
        val root=Files.createTempDirectory("t17-android");val store=AndroidAcquisitionStore(root,AndroidLegacyAcquisitionSource(root.resolve("shared_prefs")),JvmFileOps)
        store.compareAndSet("real",-1,t13State())
        val characters=object:CharacterStore { override fun load()=AppSnapshot(listOf(t13State().character),"real");override fun save(snapshot:AppSnapshot)=error("legacy write") }
        val extras=object:CharacterExtrasStore { override fun load(characterId:String)=CharacterSheetExtras();override fun save(characterId:String,extras:CharacterSheetExtras)=error("legacy write") }
        fun controller(capabilities:AcquisitionCapabilities)=CharacterController(characters,extras,store,mapOf("real" to t13State()),catalogProvider=::t13Catalog,capabilities=capabilities,mainDispatcher=Dispatchers.Unconfined)
        val production=controller(AndroidAcquisitionRuntimeGate.production)
        val disabled=production.acquisition.preview(AcquisitionIntent.development(t13Request().targets.single()))
        assertEquals(AcquisitionUiPhase.Blocked,disabled.phase);assertFalse(disabled.canConfirm)
        val active=controller(AcquisitionCapabilities(true,false,true))
        val quote=active.acquisition.preview(AcquisitionIntent.development(t13Request().targets.single()))
        assertEquals(AcquisitionUiPhase.Committed,active.acquisition.confirm(quote.key).phase)
        active.acquisition.confirm(quote.key)
        assertEquals(1,(store.load("real") as LoadResult.Loaded).state.receipts.size)
        assertTrue(active.exportActiveCharacter().contains("formatVersion"))
    }
}
