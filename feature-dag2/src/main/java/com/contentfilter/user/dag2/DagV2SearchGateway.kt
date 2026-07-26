package com.contentfilter.user.dag2

import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.network.remote.SupabaseRestClient
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import org.json.JSONObject
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

interface DagV2SearchGateway {
    suspend fun search(query: String): DagV2SearchOutcome
}

@Singleton
class SupabaseDagV2SearchGateway
    @Inject
    constructor(
        private val client: SupabaseRestClient,
        private val activationRepository: DeviceActivationRepository,
    ) : DagV2SearchGateway {
        override suspend fun search(query: String): DagV2SearchOutcome {
            val activation =
                activationRepository.currentActivation()
                    ?: return DagV2SearchOutcome.Failure("El dispositivo no está activado.")
            val payload =
                JSONObject()
                    .put("device_id", activation.deviceId)
                    .put("query", query.take(MaxQueryCharacters))
                    .put("language", Locale.getDefault().language.ifBlank { "es" })
                    .put("page", 0)
            return when (val response = client.invokeFunctionForObject(FunctionName, payload)) {
                is RemoteResult.Failure -> DagV2SearchOutcome.Failure(response.reason)
                is RemoteResult.Success ->
                    runCatching {
                        val array = response.value.getJSONArray("results")
                        DagV2SearchOutcome.Success(
                            (0 until array.length()).map { index ->
                                val item = array.getJSONObject(index)
                                DagV2SearchResult(
                                    title = item.optString("title").take(MaxTitleCharacters),
                                    url = item.optString("url").take(MaxUrlCharacters),
                                    description = item.optString("description").take(MaxDescriptionCharacters),
                                )
                            },
                        )
                    }.getOrElse {
                        DagV2SearchOutcome.Failure("La respuesta de Brave no es válida.")
                    }
            }
        }

        private companion object {
            const val FunctionName = "dag-search"
            const val MaxQueryCharacters = 500
            const val MaxTitleCharacters = 240
            const val MaxUrlCharacters = 2_048
            const val MaxDescriptionCharacters = 600
        }
    }

@Module
@InstallIn(SingletonComponent::class)
abstract class DagV2SearchModule {
    @Binds
    abstract fun bindSearchGateway(implementation: SupabaseDagV2SearchGateway): DagV2SearchGateway
}
