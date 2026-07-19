package com.contentfilter.core.network.remote

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject

data class RemoteAnnouncement(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Instant,
    val isRead: Boolean,
)

class RemoteAnnouncementRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) {
        suspend fun listRecent(maxRows: Int = 50): RemoteResult<List<RemoteAnnouncement>> {
            val payload = JSONObject().put("max_rows", maxRows.coerceIn(1, 100))
            return when (val result = client.invokeRpcForArray("list_device_announcements_v2", payload)) {
                is RemoteResult.Failure -> result
                is RemoteResult.Success ->
                    runCatching { parseRemoteAnnouncements(result.value) }.fold(
                        onSuccess = { RemoteResult.Success(it) },
                        onFailure = { RemoteResult.Failure("Los avisos recibidos no son válidos.", retryable = true) },
                    )
            }
        }

        suspend fun markAllRead(): RemoteResult<Unit> = client.invokeRpc("mark_device_announcements_read", JSONObject())

        suspend fun dismiss(announcementId: String): RemoteResult<Unit> =
            client.invokeRpc(
                "dismiss_device_announcement",
                JSONObject().put("target_announcement_id", announcementId),
            )

        suspend fun restore(announcementId: String): RemoteResult<Unit> =
            client.invokeRpc(
                "restore_device_announcement",
                JSONObject().put("target_announcement_id", announcementId),
            )
    }

internal fun parseRemoteAnnouncements(payload: JSONArray): List<RemoteAnnouncement> =
    buildList {
        repeat(payload.length()) { index ->
            val item = payload.getJSONObject(index)
            add(
                RemoteAnnouncement(
                    id = item.getString("announcement_id"),
                    title = item.getString("title"),
                    body = item.getString("body"),
                    createdAt = Instant.parse(item.getString("created_at")),
                    isRead = item.optBoolean("is_read", false),
                ),
            )
        }
    }
