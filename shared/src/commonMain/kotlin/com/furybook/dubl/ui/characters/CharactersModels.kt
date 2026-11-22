package com.furybook.dubl.ui.characters

import com.furybook.core.cloud.CampaignSummary
import com.furybook.core.json.jsonArray
import com.furybook.core.json.jsonObject
import com.furybook.core.json.jsonString
import com.furybook.core.json.jsonStringify
import com.furybook.core.json.parseRoot
import com.furybook.core.json.stringList

data class CharacterCard(
    val id: String,
    val name: String,
    val concept: String,
    val xp: Int,
    val active: Boolean,
    val archived: Boolean,
    val campaign: String?,
)

/** The roster's cards, in roster order: either the active list or «Архив». [campaign] names a linked campaign. */
fun characterCards(host: DublCharactersHost, archived: Boolean, campaigns: List<CampaignSummary>): List<CharacterCard> {
    val archive = host.archive.load()
    return host.characters.filter { (it.id in archive) == archived }.map { character ->
        CharacterCard(
            id = character.id,
            name = character.name,
            concept = character.concept,
            xp = character.experience,
            active = character.id == host.activeId,
            archived = archived,
            campaign = campaigns.firstOrNull { it.linkedCharacterId == character.id }?.name,
        )
    }
}

/** Moves [id] to «Архив». Archiving the active character opens the next one, or a new one when none is left. */
fun archiveCharacter(host: DublCharactersHost, id: String) {
    val archive = host.archive.load() + id
    host.archive.save(archive)
    if (host.activeId != id) return
    val next = host.characters.firstOrNull { it.id !in archive }
    if (next != null) host.select(next.id) else host.create()
}

/** Brings [id] back from «Архив». */
fun unarchiveCharacter(host: DublCharactersHost, id: String) {
    host.archive.save(host.archive.load() - id)
}

/** The archive list as the platforms store it (`{"archived":[…]}`); anything unreadable is an empty archive. */
fun encodeArchiveIds(ids: Set<String>): String = jsonStringify(jsonObject("archived" to jsonArray(ids.sorted().map(::jsonString))))

fun decodeArchiveIds(raw: String?): Set<String> =
    raw?.let { runCatching { parseRoot(it).stringList("archived").toSet() }.getOrNull() }.orEmpty()
