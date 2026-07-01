package com.contentfilter.core.network.remote

import android.util.Log
import com.contentfilter.core.network.config.AuthTokenProvider
import com.contentfilter.core.network.config.SupabaseConfigProvider
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class SupabaseDevMaintenanceClient
    @Inject
    constructor(
        private val configProvider: SupabaseConfigProvider,
        private val authTokenProvider: AuthTokenProvider,
        private val httpClient: OkHttpClient,
    ) {
        suspend fun clearRemoteRequests(accountId: String): RemoteResult<Unit> =
            softDelete(
                table = SupabaseTable.AccessRequests.tableName,
                accountId = accountId,
            )

        suspend fun clearRemoteExtraTimeGrants(accountId: String): RemoteResult<Unit> =
            softDelete(
                table = SupabaseTable.ExtraTimeGrants.tableName,
                accountId = accountId,
            )

        suspend fun clearRemotePolicyRules(accountId: String): RemoteResult<Unit> =
            softDelete(
                table = SupabaseTable.PolicyRules.tableName,
                accountId = accountId,
            )

        suspend fun clearDuplicateDevices(
            accountId: String,
            keepDeviceId: String,
        ): RemoteResult<Unit> = withContext(Dispatchers.IO) {
            val devices = softDelete(
                table = SupabaseTable.Devices.tableName,
                accountId = accountId,
                extraFilter = "&id=neq.${keepDeviceId.encode()}",
            )
            if (devices is RemoteResult.Failure) return@withContext devices
            softDelete(
                table = "device_activations",
                accountId = accountId,
                extraFilter = "&device_id=neq.${keepDeviceId.encode()}",
            )
        }

        suspend fun resetRemoteDev(
            accountId: String,
            keepDeviceId: String?,
        ): RemoteResult<Unit> = withContext(Dispatchers.IO) {
            listOf(
                softDelete(table = SupabaseTable.AccessRequests.tableName, accountId = accountId),
                softDelete(table = SupabaseTable.ExtraTimeGrants.tableName, accountId = accountId),
                softDelete(table = SupabaseTable.PolicyRules.tableName, accountId = accountId),
                softDelete(table = "device_activations", accountId = accountId),
                softDelete(
                    table = SupabaseTable.Devices.tableName,
                    accountId = accountId,
                    extraFilter = keepDeviceId?.let { "&id=neq.${it.encode()}" }.orEmpty(),
                ),
            ).firstOrNull { it is RemoteResult.Failure } ?: RemoteResult.Success(Unit)
        }

        private suspend fun softDelete(
            table: String,
            accountId: String,
            extraFilter: String = "",
        ): RemoteResult<Unit> = withContext(Dispatchers.IO) {
            val request = requestBuilder(
                path = "/rest/v1/$table?account_id=eq.${accountId.encode()}&deleted_at=is.null$extraFilter",
            ) ?: return@withContext RemoteResult.Failure(OfflineUserMessage, retryable = true)
            val body = JSONObject()
                .put("deleted_at", Instant.now().toString())
                .toString()
                .toRequestBody(JsonMediaType)
            try {
                httpClient.newCall(
                    request
                        .patch(body)
                        .header("Prefer", "return=minimal")
                        .build(),
                ).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        Log.i(LogTag, "DEV soft-delete table=$table accountId=$accountId ok")
                        RemoteResult.Success(Unit)
                    } else {
                        httpFailure("DEV soft-delete $table", response.code, responseBody)
                    }
                }
            } catch (exception: Exception) {
                exceptionFailure("DEV soft-delete $table", exception)
            }
        }

        private fun requestBuilder(path: String): Request.Builder? {
            val config = configProvider.current()
            val baseUrl = config.normalizedUrlOrNull()
            val token = authTokenProvider.currentToken()
            if (baseUrl == null || token == null) return null
            return Request.Builder()
                .url("$baseUrl$path")
                .header("apikey", config.anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
        }

        private fun String.encode(): String = URLEncoder.encode(this, Charsets.UTF_8.name())

        private companion object {
            const val LogTag = "SupabaseDevTools"
            val JsonMediaType = "application/json".toMediaType()
        }
    }
