package com.contentfilter.core.network.remote

import javax.inject.Inject

class SupabaseRemoteProtectionAlertRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) : RemoteProtectionAlertRepository {
        override suspend fun pullAlerts(): RemoteResult<List<RemoteProtectionAlert>> =
            when (
                val result =
                    client.selectByEquals(
                        table = SupabaseTable.ProtectionAlertEvents,
                        filters = emptyMap(),
                        orderColumn = "created_at",
                        ascending = false,
                    )
            ) {
                is RemoteResult.Failure -> result
                is RemoteResult.Success ->
                    RemoteResult.Success(
                        buildList {
                            for (index in 0 until result.value.length()) {
                                val item = result.value.getJSONObject(index)
                                add(
                                    RemoteProtectionAlert(
                                        id = item.getString("id"),
                                        deviceId = item.getString("device_id"),
                                        alertType = item.getString("alert_type"),
                                        title = item.getString("title"),
                                        body = item.getString("body"),
                                        createdAt = item.getString("created_at"),
                                    ),
                                )
                            }
                        }.take(MaxAlerts),
                    )
            }

        private companion object {
            const val MaxAlerts = 200
        }
    }
