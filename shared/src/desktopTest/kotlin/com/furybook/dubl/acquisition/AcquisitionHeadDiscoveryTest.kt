package com.furybook.dubl.acquisition

import com.furybook.dubl.data.DesktopAcquisitionStore
import kotlinx.coroutines.runBlocking
import java.nio.file.Files
import kotlin.test.*

class AcquisitionHeadDiscoveryTest {
    @Test fun strict_enumeration_discovers_created_heads_not_temporary_files() = runBlocking<Unit> {
        val dir=Files.createTempDirectory("t17-heads");val store=DesktopAcquisitionStore(dir)
        assertEquals(emptyList(),store.characterIds())
        val source=RuleSourceRef("policy","1","Explicit initial funding",resolutionId="UR_NEW_CHARACTER_INITIAL_AP_PER_1000_XP",resolutionRevision="r")
        val catalog=AcquisitionCatalog(emptyMap(),"c","r",CreationFundingPolicy("UR_NEW_CHARACTER_INITIAL_AP_PER_1000_XP",1000,source))
        val creator=AcquisitionCharacterCreator(store,{catalog},{AcquisitionCapabilities(true,false,true)})
        assertIs<NewCharacterCreationResult.Created>(creator.create(CreateAcquisitionCharacter("create","new-target",2500,"Hero","","c","r")))
        Files.writeString(store.envelopePath("new-target").parent.resolve("ignored.tmp"),"broken")
        assertEquals(listOf("new-target"),DesktopAcquisitionStore(dir).characterIds())
        assertFalse(Files.exists(dir.resolve("characters.json")))
        Files.copy(store.envelopePath("new-target"),store.envelopePath("wrong"))
        assertFails { store.characterIds() }
    }
}
