package com.contentfilter.core.network.remote

import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject

data class RemoteAnnouncement(
    val id: String,
    val title: String,
    val body: String,
    val createdAt: Instant,
)

class RemoteAnnouncementRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) {
        suspend fun listRecent(maxRows: Int = 50): RemoteResult<List<RemoteAnnouncement>> {
            val payload = JSONObject().put("max_rows", maxRows.coerceIn(1, 100))
            return when (val result = client.invokeRpcForArray("list_device_announcements", payload)) {
                is RemoteResult.Failure -> result
                is RemoteResult.Success ->
                    runCatching {
                        buildList {
                            repeat(result.value.length()) { index ->
                                val item = result.value.getJSONObject(index)
                                add(
                                    RemoteAnnouncement(
                                        id = item.getString("announcement_id"),
                                        title = item.getString("title"),
                                        body = item.getString("body"),
                                        createdAt = Instant.parse(item.getString("created_at")),
                                    ),
                                )
                            }
                        }
                    }.fold(
                        onSuccess = { RemoteResult.Success(it) },
                        onFailure = { RemoteResult.Failure("Los avisos recibidos no son válidos.", retryable = true) },
                    )
            }
        }
    }
