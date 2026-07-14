package com.contentfilter.user.dag

import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.network.remote.SupabaseRestClient
import org.json.JSONObject
import javax.inject.Inject

class DagSearchRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) {
        suspend fun search(
            deviceId: String,
            query: String,
            language: String,
        ): RemoteResult<List<DagRemoteSearchResult>> {
            val payload =
                JSONObject()
                    .put("device_id", deviceId)
                    .put("query", query)
                    .put("language", language)
            return when (val response = client.invokeFunctionForObject(FunctionName, payload)) {
                is RemoteResult.Failure -> response
                is RemoteResult.Success ->
                    runCatching {
                        val results = response.value.getJSONArray("results")
                        (0 until results.length()).map { index ->
                            val json = results.getJSONObject(index)
                            DagRemoteSearchResult(
                                title = json.getString("title").take(MaxTitleCharacters),
                                url = json.getString("url").take(MaxUrlCharacters),
                                description = json.optString("description").take(MaxDescriptionCharacters),
                            )
                        }
                    }.fold(
                        onSuccess = { RemoteResult.Success(it) },
                        onFailure = {
                            RemoteResult.Failure(
                                "La respuesta del buscador no es válida.",
                                retryable = true,
                            )
                        },
                    )
            }
        }

        private companion object {
            const val FunctionName = "dag-search"
            const val MaxTitleCharacters = 240
            const val MaxUrlCharacters = 2_048
            const val MaxDescriptionCharacters = 600
        }
    }

data class DagRemoteSearchResult(
    val title: String,
    val url: String,
    val description: String,
)
