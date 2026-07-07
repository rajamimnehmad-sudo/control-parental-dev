package com.contentfilter.core.network.dto

import org.json.JSONObject

data class RemoteAccountDto(
    val id: String,
    val name: String,
    val communityId: String?,
    val communityName: String,
    val guideName: String,
    val updatedAt: String,
    val deletedAt: String?,
) {
    companion object {
        fun fromJson(json: JSONObject): RemoteAccountDto {
            val community = json.optJSONObject("communities")
            return RemoteAccountDto(
                id = json.getString("id"),
                name = json.getString("name"),
                communityId = json.optNullableString("community_id"),
                communityName =
                    community?.optString("name")?.takeIf { it.isNotBlank() }
                        ?: json.optString("name", "Comunidad"),
                guideName =
                    community?.optString("guide_label")?.takeIf { it.isNotBlank() }
                        ?: "Equipo de guías",
                updatedAt = json.getString("updated_at"),
                deletedAt = json.optNullableString("deleted_at"),
            )
        }
    }
}
