package com.furybook.core.cloud

import com.furybook.core.json.JsonReader
import com.furybook.core.json.JsonValue
import com.furybook.core.json.asObject
import com.furybook.core.json.array
import com.furybook.core.json.bool
import com.furybook.core.json.int
import com.furybook.core.json.jsonArray
import com.furybook.core.json.jsonNumber
import com.furybook.core.json.jsonObject
import com.furybook.core.json.jsonString
import com.furybook.core.json.jsonStringify
import com.furybook.core.json.objectValue
import com.furybook.core.json.parseRoot
import com.furybook.core.json.string
import com.furybook.dubl.data.SnapshotCodec
import com.furybook.dubl.model.AppSnapshot
import com.furybook.dubl.model.CharacterSheetExtras
import com.furybook.dubl.model.DublCharacter

data class CampaignSummary(
    val id: String,
    val name: String,
    val isOwner: Boolean,
    val linkedCharacterId: String?,
    val memberCount: Int,
    val createdAt: String,
)

data class CampaignInvite(
    val campaignId: String,
    val token: String,
    val expiresAt: String,
)

data class CampaignMember(
    val userId: String,
    val nickname: String,
    val characterId: String,
    val character: DublCharacter,
    val extras: CharacterSheetExtras,
    val revision: Int,
    val updatedAt: String,
    val updatedBy: String,
    val joinedAt: String,
)

data class CampaignDashboard(
    val id: String,
    val name: String,
    val ownerUserId: String,
    val createdAt: String,
    val players: List<CampaignMember>,
)

class FuryCampaignApi(
    private val transport: FuryCloudTransport,
    private val idFactory: () -> String,
) {
    suspend fun listCampaigns(): List<CampaignSummary> {
        val root = JsonReader(transport.rpc("campaign_list", "{}")).read()
        return (root as? JsonValue.Arr)?.values.orEmpty().mapNotNull { value ->
            val item = value.asObject() ?: return@mapNotNull null
            CampaignSummary(
                id = item.string("id"),
                name = item.string("name", "Кампания"),
                isOwner = item.bool("isOwner", false),
                linkedCharacterId = item.string("linkedCharacterId").takeIf(String::isNotBlank),
                memberCount = item.int("memberCount", 0),
                createdAt = item.string("createdAt"),
            )
        }
    }

    suspend fun createCampaign(name: String): CampaignSummary =
        parseSummary(
            transport.rpc(
                "campaign_create",
                jsonStringify(jsonObject("p_name" to jsonString(name))),
            ),
        )

    suspend fun createInvite(campaignId: String, expiresHours: Int = 168): CampaignInvite {
        val root = parseRoot(
            transport.rpc(
                "campaign_create_invite",
                jsonStringify(
                    jsonObject(
                        "p_campaign_id" to jsonString(campaignId),
                        "p_expires_hours" to jsonNumber(expiresHours),
                    ),
                ),
            ),
        )
        return CampaignInvite(
            campaignId = root.string("campaignId", campaignId),
            token = root.string("token"),
            expiresAt = root.string("expiresAt"),
        )
    }

    suspend fun joinCampaign(inviteCode: String, characterId: String): CampaignSummary =
        parseSummary(
            transport.rpc(
                "campaign_join",
                jsonStringify(
                    jsonObject(
                        "p_token" to jsonString(inviteCode),
                        "p_character_id" to jsonString(characterId),
                    ),
                ),
            ),
        )

    suspend fun setCharacter(campaignId: String, characterId: String) {
        transport.rpc(
            "campaign_set_character",
            jsonStringify(
                jsonObject(
                    "p_campaign_id" to jsonString(campaignId),
                    "p_character_id" to jsonString(characterId),
                ),
            ),
        )
    }

    suspend fun leaveCampaign(campaignId: String) {
        transport.rpc(
            "campaign_leave",
            jsonStringify(jsonObject("p_campaign_id" to jsonString(campaignId))),
        )
    }

    suspend fun removeMember(campaignId: String, userId: String) {
        transport.rpc(
            "campaign_remove_member",
            jsonStringify(
                jsonObject(
                    "p_campaign_id" to jsonString(campaignId),
                    "p_user_id" to jsonString(userId),
                ),
            ),
        )
    }

    suspend fun getDashboard(campaignId: String): CampaignDashboard {
        val root = parseRoot(
            transport.rpc(
                "campaign_get_dashboard",
                jsonStringify(jsonObject("p_campaign_id" to jsonString(campaignId))),
            ),
        )
        return CampaignDashboard(
            id = root.string("id", campaignId),
            name = root.string("name", "Кампания"),
            ownerUserId = root.string("ownerUserId"),
            createdAt = root.string("createdAt"),
            players = root.array("players").mapNotNull { value ->
                val item = value.asObject() ?: return@mapNotNull null
                val data = item.objectValue("data") ?: return@mapNotNull null
                val extras = item.objectValue("extras") ?: jsonObject()
                val character = decodeCharacter(data)
                CampaignMember(
                    userId = item.string("userId"),
                    nickname = item.string("nickname", "Player"),
                    characterId = item.string("characterId", character.id),
                    character = character,
                    extras = CloudProtocol.decodeExtras(jsonStringify(extras)),
                    revision = item.int("revision", 0),
                    updatedAt = item.string("updatedAt"),
                    updatedBy = item.string("updatedBy"),
                    joinedAt = item.string("joinedAt"),
                )
            },
        )
    }

    private fun parseSummary(raw: String): CampaignSummary {
        val root = parseRoot(raw)
        return CampaignSummary(
            id = root.string("id"),
            name = root.string("name", "Кампания"),
            isOwner = root.bool("isOwner", false),
            linkedCharacterId = root.string("linkedCharacterId").takeIf(String::isNotBlank),
            memberCount = root.int("memberCount", 0),
            createdAt = root.string("createdAt"),
        )
    }

    private fun decodeCharacter(data: JsonValue.Obj): DublCharacter {
        val id = data.string("id").ifBlank(idFactory)
        val snapshot = jsonObject(
            "schema" to jsonNumber(SnapshotCodec.SCHEMA),
            "activeCharacterId" to jsonString(id),
            "characters" to jsonArray(listOf(data)),
        )
        return SnapshotCodec.decode(jsonStringify(snapshot), idFactory).characters.single()
    }
}
