package com.furybook.dubl.acquisition

import com.furybook.content.FcpContentPack
import com.furybook.core.json.*
import com.furybook.dubl.model.*

const val LEGACY_ECONOMY_BASELINE = "f47a5256a66ddc5e1b7b5c1e204e57279fc7227f"
private const val PINNED_CATALOG_SHA256 = "ee62b7d86dc16a49943fe586ede5cafe18470b82779a5b7bd1680ced2e90c0a0"
internal fun legacyCatalogIdentity(activePackIds: List<String>): String? =
    if (activePackIds == listOf("dubl-3.69") || activePackIds == listOf("dubl-3.69", "dubl-chi-3.69"))
        "$LEGACY_ECONOMY_BASELINE:$PINNED_CATALOG_SHA256:${activePackIds.joinToString(",")}" else null

internal data class LegacyDevelopmentPrice(val ref: DefinitionRef, val cost: Int, val ability: Boolean, val options: List<Int>)

/** Verified old inputs only. No constructor accepting today's catalogs/prices. */
class LegacyCatalogSnapshot internal constructor(
    val identity: String,
    activePackIds: List<String>,
    internal val developments: Map<String, LegacyDevelopmentPrice>,
    internal val skillCosts: List<Int>,
    internal val skillIds: Set<String>,
    internal val builtInSkillIds: Set<String>,
    internal val spellIds: Set<String>,
) {
    val activePackIds: List<String> = EnvelopeList(activePackIds)
    val includeChi: Boolean get() = "dubl-chi-3.69" in activePackIds
}

/** Explicit composition required. Missing/modified snapshot or unavailable addons require confirmation. */
fun loadLegacyCatalogSnapshot(pack: FcpContentPack, activePackIds: List<String>): LegacyCatalogSnapshot? = try {
    require(pack.manifest.id == "dubl-3.69")
    val identity = requireNotNull(legacyCatalogIdentity(activePackIds))
    val raw = pack.readSingle("dubl.legacy-acquisition-v11")
    require(legacySha256(raw.encodeToByteArray()) == PINNED_CATALOG_SHA256)
    val root = JsonReader(raw, strict = true).read() as JsonValue.Obj
    require(root.string("baseline") == LEGACY_ECONOMY_BASELINE)
    val includeChi = "dubl-chi-3.69" in activePackIds
    val inactive = if (includeChi) emptySet() else root.stringList("inactiveChiClaims").toSet()
    val rows = root.array("developments").map { it.asObject()!! }.filter {
        it.string("packId") in activePackIds && it.string("id") !in inactive
    }.associate { item ->
        val id = item.string("id")
        id to LegacyDevelopmentPrice(DefinitionRef(item.string("packId"), id), item.int("cost"), item.bool("ability"),
            item.array("options").map { (it as JsonValue.Num).raw.toInt() })
    }
    LegacyCatalogSnapshot(identity, activePackIds,
        rows, root.array("skillRankCosts").map { (it as JsonValue.Num).raw.toInt() }, root.stringList("skillIds").toSet(),
        root.stringList("builtInSkillIds").toSet(), root.stringList("spellIds").toSet())
} catch (_: Exception) { null }

/**
 * Frozen f47a economic semantics, including old zero-priced/unpriced rows, Int arithmetic and
 * cumulative Self-Taught after creation. This is NEVER a price source for a new acquisition.
 * Input is the public legacy-normalized character, not an exact canonical envelope projection.
 */
object LegacyEconomyV11 {
    private val attributeCosts = listOf(-200,-170,-135,-95,-50,0,55,115,180,250,325,405,490,580,675,775)
    private val schoolNames = setOf("Боевая магия","Воплощение","Друид","Магия крови","Молитва","Некромантия",
        "Ограждение","Призыв","Природа","Прорицание","Разрушение","Разум","Трансмутация")
    private val schoolAliases = mapOf("ограждения" to "Ограждение", "молитвы" to "Молитва", "некромант" to "Некромантия",
        "друидичество" to "Друид", "друидическая магия" to "Друид", "боевая" to "Боевая магия")
    private fun recognizedSchool(name: String): Boolean {
        val key = name.trim().lowercase().replace('ё','е')
        return key in schoolAliases || schoolNames.any { it.lowercase().replace('ё','е') == key }
    }
    fun balance(character: DublCharacter, catalog: LegacyCatalogSnapshot): Amount {
        val c = character
        val attributes = AttributeId.entries.sumOf { attributeCosts.getOrNull((c.attributes[it]?.base ?: 0) + 5) ?: 0 }
        val discounted = c.creationComplete && (c.development["feat_dd0ba74aedebce10"]?.rank ?: 0) > 0
        // f47a resolvedSkills visits built-in map keys, then all non-built-in state IDs (even hidden).
        val skills = (catalog.builtInSkillIds.mapNotNull { c.skills[it] } + c.skills.values.filter { it.id !in catalog.builtInSkillIds }).sumOf {
            val rank = it.rank.coerceIn(0,10)
            catalog.skillCosts[rank] - if(discounted) catalog.skillCosts[rank.coerceAtMost(2)] / 2 else 0
        }
        val prices = catalog.developments.toMutableMap()
        c.developmentOverrides.forEach { (id, e) -> if (id in prices) prices[id] = localPrice(e,id,prices.getValue(id).ref.packId) }
        c.customDevelopmentEntries.distinctBy { it.id.trim() }.forEach { e ->
            val id=e.id.trim(); if(id.isNotBlank() && id !in prices) prices[id]=localPrice(e,id,"legacy-custom")
        }
        val development = c.development.entries.sumOf { (id, owned) ->
            val price=prices[id]
            if(id=="feat_bf7b04eb68961725" || price==null || price.ability) 0 else price.cost.coerceAtLeast(0)*owned.rank
        }
        val ap = c.development.entries.sumOf { (id,owned) ->
            val price=prices[id]
            if(price==null || !price.ability) 0 else (if(price.options.isEmpty()) price.cost.coerceAtLeast(0)
                else price.options[owned.optionIndex.coerceIn(0,price.options.lastIndex)].coerceAtLeast(0))*owned.rank
        }
        val mana=c.magic.manaRank.coerceIn(0,5)*100
        val chi=if(catalog.includeChi) c.chiBonusRanks.coerceIn(0,10)*50 else 0
        val schools=c.magic.schools.filter { recognizedSchool(it.name) }.sumOf { it.rank.coerceAtLeast(0)*25 }
        val spells=c.magic.spells.filter { it.learned }.sumOf { spell ->
            if(spell.incomplete && spell.xpOverride==null) 0 else spell.xpOverride ?: when(spell.cost) {
                in 0..3 -> 10; in 4..8 -> 20; in 9..12 -> 30; in 13..16 -> 40; in 17..20 -> 50; else -> 0
            }
        }
        val creationExperience=when {c.creationExperience>0 -> c.creationExperience.coerceAtMost(c.experience.coerceAtLeast(0)); !c.creationComplete -> c.experience.coerceAtLeast(0);else -> 0}
        val budget=c.abilityPointsOverride ?: (creationExperience/1000)
        return Amount((c.experience-(attributes+skills+development+mana+chi+schools+spells+c.xpAdjustment)).toLong(),(budget-ap).toLong())
    }
    private fun localPrice(e:DevelopmentEntry,id:String,packId:String)=LegacyDevelopmentPrice(DefinitionRef(packId,id),e.cost.coerceAtLeast(0),
        e.costType==DevelopmentCostType.ABILITY,e.abilityOptions.map { it.value.coerceAtLeast(0) })
}

/** SHA-256; common Kotlin implementation, no platform provider or runtime file paths. */
internal fun legacySha256(bytes: ByteArray): String {
    val constants = intArrayOf(0x428a2f98,0x71374491,0xb5c0fbcfu.toInt(),0xe9b5dba5u.toInt(),0x3956c25b,0x59f111f1,0x923f82a4u.toInt(),0xab1c5ed5u.toInt(),
        0xd807aa98u.toInt(),0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1feu.toInt(),0x9bdc06a7u.toInt(),0xc19bf174u.toInt(),
        0xe49b69c1u.toInt(),0xefbe4786u.toInt(),0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,
        0x983e5152u.toInt(),0xa831c66du.toInt(),0xb00327c8u.toInt(),0xbf597fc7u.toInt(),0xc6e00bf3u.toInt(),0xd5a79147u.toInt(),0x06ca6351,0x14292967,
        0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92eu.toInt(),0x92722c85u.toInt(),
        0xa2bfe8a1u.toInt(),0xa81a664bu.toInt(),0xc24b8b70u.toInt(),0xc76c51a3u.toInt(),0xd192e819u.toInt(),0xd6990624u.toInt(),0xf40e3585u.toInt(),0x106aa070,
        0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,
        0x748f82ee,0x78a5636f,0x84c87814u.toInt(),0x8cc70208u.toInt(),0x90befffau.toInt(),0xa4506cebu.toInt(),0xbef9a3f7u.toInt(),0xc67178f2u.toInt())
    val hash=intArrayOf(0x6a09e667,0xbb67ae85u.toInt(),0x3c6ef372,0xa54ff53au.toInt(),0x510e527f,0x9b05688cu.toInt(),0x1f83d9ab,0x5be0cd19)
    require(bytes.size <= Int.MAX_VALUE-72)
    val padded=bytes.copyOf(((bytes.size+9+63)/64)*64);padded[bytes.size]=0x80.toByte()
    val bits=bytes.size.toLong()*8
    for(i in 0..7) padded[padded.lastIndex-i]=(bits ushr (i*8)).toByte()
    val w=IntArray(64)
    for(offset in padded.indices step 64) {
        for(i in 0..15) {val j=offset+i*4;w[i]=((padded[j].toInt() and 255) shl 24) or ((padded[j+1].toInt() and 255) shl 16) or ((padded[j+2].toInt() and 255) shl 8) or (padded[j+3].toInt() and 255)}
        for(i in 16..63){val x=w[i-15];val y=w[i-2];w[i]=w[i-16]+(x.rotateRight(7) xor x.rotateRight(18) xor (x ushr 3))+w[i-7]+(y.rotateRight(17) xor y.rotateRight(19) xor (y ushr 10))}
        var a=hash[0];var b=hash[1];var c=hash[2];var d=hash[3];var e=hash[4];var f=hash[5];var g=hash[6];var h=hash[7]
        for(i in 0..63){val t1=h+(e.rotateRight(6) xor e.rotateRight(11) xor e.rotateRight(25))+((e and f) xor (e.inv() and g))+constants[i]+w[i];val t2=(a.rotateRight(2) xor a.rotateRight(13) xor a.rotateRight(22))+((a and b) xor (a and c) xor (b and c));h=g;g=f;f=e;e=d+t1;d=c;c=b;b=a;a=t1+t2}
        hash[0]+=a;hash[1]+=b;hash[2]+=c;hash[3]+=d;hash[4]+=e;hash[5]+=f;hash[6]+=g;hash[7]+=h
    }
    return hash.joinToString(""){ it.toUInt().toString(16).padStart(8,'0') }
}
