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
    fun archiveIdsRoundTrip() {
        assertEquals(setOf("a", "b c"), decodeArchiveIds(encodeArchiveIds(setOf("a", "b c"))))
        assertEquals(emptySet(), decodeArchiveIds(null))
        assertEquals(emptySet(), decodeArchiveIds("garbage"))
    }
}
