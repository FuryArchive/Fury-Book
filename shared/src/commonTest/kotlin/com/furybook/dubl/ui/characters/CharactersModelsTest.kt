package com.furybook.dubl.ui.characters

import com.furybook.core.cloud.CampaignSummary
import com.furybook.dubl.application.CharacterTransferImportResult
import com.furybook.dubl.model.DublCharacter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private class ListHost(names: List<String>, override var activeId: String) : DublCharactersHost {
    override var characters = names.mapIndexed { i, n -> DublCharacter(id = "c$i", name = n, concept = "к$i", experience = i * 10) }
    override val archive = InMemoryFuryArchiveStore()
    var created = 0
    override fun portraitOf(id: String): String? = null
    override fun select(id: String) { activeId = id }
    override fun create() { created++; val c = DublCharacter(id = "new$created"); characters = characters + c; activeId = c.id }
    override fun delete(id: String) { characters = characters.filterNot { it.id == id } }
    override fun duplicate(id: String): String? = null
    override fun exportCharacter(id: String): String = ""
    override fun importCharacter(raw: String): CharacterTransferImportResult = error("unused")
}

class CharactersModelsTest {
    @Test
    fun cardsSplitByArchive() {
        val host = ListHost(listOf("Шершень", "Вика", "Гром"), "c0")
        host.archive.save(setOf("c1"))
        assertEquals(listOf("c0", "c2"), characterCards(host, archived = false, campaigns = emptyList()).map { it.id })
        val archived = characterCards(host, archived = true, campaigns = emptyList())
        assertEquals(listOf("c1"), archived.map { it.id })
        assertTrue(archived.single().archived)
        val first = characterCards(host, false, emptyList()).first()
        assertEquals(CharacterCard("c0", "Шершень", "к0", 0, active = true, archived = false, campaign = null), first)
    }

    @Test
    fun archivingActivePicksNext() {
        val host = ListHost(listOf("Шершень", "Вика", "Гром"), "c0")
        host.archive.save(setOf("c1"))
        archiveCharacter(host, "c0")
        assertEquals(setOf("c0", "c1"), host.archive.load())
        assertEquals("c2", host.activeId)
        assertEquals(0, host.created)
    }

    @Test
    fun archivingLastCreatesNew() {
        val host = ListHost(listOf("Шершень"), "c0")
        archiveCharacter(host, "c0")
        assertEquals(1, host.created)
        assertEquals("new1", host.activeId)
        assertEquals(setOf("c0"), host.archive.load())
    }

    @Test
    fun archivingInactiveKeepsActive() {
        val host = ListHost(listOf("Шершень", "Вика"), "c0")
        archiveCharacter(host, "c1")
        assertEquals("c0", host.activeId)
    }

    @Test
    fun activeNeverStaysArchived() {
        // Deleting the active one can leave an archived character active; it must hand over like archiving does.
        val host = ListHost(listOf("Шершень", "Вика", "Гром"), "c0")
        host.archive.save(setOf("c0", "c1"))
        keepActiveUnarchived(host)
        assertEquals("c2", host.activeId)
        val lonely = ListHost(listOf("Шершень"), "c0")
        lonely.archive.save(setOf("c0"))
        keepActiveUnarchived(lonely)
        assertEquals("new1", lonely.activeId)
        val fine = ListHost(listOf("Шершень", "Вика"), "c1")
        keepActiveUnarchived(fine)
        assertEquals("c1", fine.activeId); assertEquals(0, fine.created)
        assertEquals(listOf("c2"), unarchivedCharacters(host).map { it.id })
    }

    @Test
    fun cardShowsLinkedCampaign() {
        val host = ListHost(listOf("Шершень", "Вика"), "c0")
        val campaigns = listOf(
            CampaignSummary("k1", "Дорога на Лест", isOwner = false, linkedCharacterId = "c1", memberCount = 3, createdAt = ""),
            CampaignSummary("k2", "Моя кампания", isOwner = true, linkedCharacterId = null, memberCount = 1, createdAt = ""),
        )
        val cards = characterCards(host, false, campaigns)
        assertEquals(null, cards[0].campaign)
        assertEquals("Дорога на Лест", cards[1].campaign)
    }

    @Test
    fun joinWarnsWhenMoving() {
        val before = listOf(CampaignSummary("k1", "Дорога на Лест", false, linkedCharacterId = "a", memberCount = 3, createdAt = ""))
        val joined = before.single().copy(linkedCharacterId = "b")
        assertEquals("a", joinReplaced(before, joined, "b"))
        // A new campaign, or the same character again: nothing moved.
        assertEquals(null, joinReplaced(emptyList(), joined, "b"))
        assertEquals(null, joinReplaced(before, before.single(), "a"))
    }

    @Test
    fun importMessages() {
        assertEquals("Импортирован персонаж: Шершень.", importResultMessage(CharacterTransferImportResult.Imported("x", "Шершень")))
        assertEquals(
            "Этот файл создан для другого рулбука или версии правил.",
            importResultMessage(CharacterTransferImportResult.Rejected(com.furybook.dubl.data.CharacterTransferRejectReason.UNSUPPORTED_RULESET)),
        )
    }

    @Test
    fun exportFileNameIsSafe() {
        assertEquals("Вика_Ястреб_.dubl", exportFileName("Вика/Ястреб?"))
        assertEquals("character.dubl", exportFileName("  "))
    }

    @Test
    fun archiveIdsRoundTrip() {
        assertEquals(setOf("a", "b c"), decodeArchiveIds(encodeArchiveIds(setOf("a", "b c"))))
        assertEquals(emptySet(), decodeArchiveIds(null))
        assertEquals(emptySet(), decodeArchiveIds("garbage"))
    }
}
