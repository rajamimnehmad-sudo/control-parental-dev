package com.contentfilter.core.network.remote

import android.util.Log
import com.contentfilter.core.network.config.AuthTokenProvider
import com.contentfilter.core.network.config.SupabaseConfigProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.time.Instant
import javax.inject.Inject

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
        ): RemoteResult<Unit> =
            withContext(Dispatchers.IO) {
                val devices =
                    softDelete(
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

        suspend fun purgeDevice(deviceId: String): RemoteResult<Unit> =
            withContext(Dispatchers.IO) {
                val requestIds =
                    when (val result = selectIds(SupabaseTable.AccessRequests.tableName, "device_id", deviceId)) {
                        is RemoteResult.Success -> result.value
                        is RemoteResult.Failure -> return@withContext result
                    }
                listOf(
                    hardDeleteIn(
                        table = SupabaseTable.ExtraTimeGrants.tableName,
                        column = "request_id",
                        values = requestIds,
                    ),
                    hardDelete(table = SupabaseTable.DeviceApps.tableName, column = "device_id", value = deviceId),
                    hardDelete(table = SupabaseTable.AccessRequests.tableName, column = "device_id", value = deviceId),
                    hardDelete(table = SupabaseTable.Policies.tableName, column = "device_id", value = deviceId),
                    hardDelete(table = "activation_codes", column = "consumed_device_id", value = deviceId),
                    hardDelete(table = "device_activations", column = "device_id", value = deviceId),
                    hardDelete(table = SupabaseTable.Devices.tableName, column = "id", value = deviceId),
                ).firstOrNull { it is RemoteResult.Failure } ?: RemoteResult.Success(Unit)
            }

        suspend fun resetRemoteDev(
            accountId: String,
            keepDeviceId: String?,
        ): RemoteResult<Unit> =
            withContext(Dispatchers.IO) {
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
        ): RemoteResult<Unit> =
            withContext(Dispatchers.IO) {
                val request =
                    requestBuilder(
                        path = "/rest/v1/$table?account_id=eq.${accountId.encode()}&deleted_at=is.null$extraFilter",
                    ) ?: return@withContext RemoteResult.Failure(OfflineUserMessage, retryable = true)
                val body =
                    JSONObject()
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

        private suspend fun hardDelete(
            table: String,
            column: String,
            value: String,
        ): RemoteResult<Unit> =
            withContext(Dispatchers.IO) {
                val request =
                    requestBuilder(
                        path = "/rest/v1/$table?$column=eq.${value.encode()}",
                    ) ?: return@withContext RemoteResult.Failure(OfflineUserMessage, retryable = true)
                try {
                    httpClient.newCall(
                        request
                            .delete()
                            .header("Prefer", "return=minimal")
                            .build(),
                    ).execute().use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            Log.i(LogTag, "DEV hard-delete table=$table column=$column ok")
                            RemoteResult.Success(Unit)
                        } else {
                            httpFailure("DEV hard-delete $table", response.code, responseBody)
                        }
                    }
                } catch (exception: Exception) {
                    exceptionFailure("DEV hard-delete $table", exception)
                }
            }

        private suspend fun hardDeleteIn(
            table: String,
            column: String,
            values: List<String>,
        ): RemoteResult<Unit> {
            if (values.isEmpty()) return RemoteResult.Success(Unit)
            val encodedValues = values.joinToString(",") { it.encode() }
            return hardDeleteByFilter(table, "$column=in.($encodedValues)")
        }

        private suspend fun hardDeleteByFilter(
            table: String,
            filter: String,
        ): RemoteResult<Unit> =
            withContext(Dispatchers.IO) {
                val request =
                    requestBuilder(path = "/rest/v1/$table?$filter")
                        ?: return@withContext RemoteResult.Failure(OfflineUserMessage, retryable = true)
                try {
                    httpClient.newCall(
                        request
                            .delete()
                            .header("Prefer", "return=minimal")
                            .build(),
                    ).execute().use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            Log.i(LogTag, "DEV hard-delete table=$table filter=$filter ok")
                            RemoteResult.Success(Unit)
                        } else {
                            httpFailure("DEV hard-delete $table", response.code, responseBody)
                        }
                    }
                } catch (exception: Exception) {
                    exceptionFailure("DEV hard-delete $table", exception)
                }
            }

        private suspend fun selectIds(
            table: String,
            column: String,
            value: String,
        ): RemoteResult<List<String>> =
            withContext(Dispatchers.IO) {
                val request =
                    requestBuilder(path = "/rest/v1/$table?select=id&$column=eq.${value.encode()}")
                        ?: return@withContext RemoteResult.Failure(OfflineUserMessage, retryable = true)
                try {
                    httpClient.newCall(request.get().build()).execute().use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            val ids =
                                JSONArray(responseBody).let { array ->
                                    (0 until array.length()).mapNotNull { index ->
                                        array.optJSONObject(index)?.optString("id")?.takeIf { it.isNotBlank() }
                                    }
                                }
                            RemoteResult.Success(ids)
                        } else {
                            httpFailure("DEV select ids $table", response.code, responseBody)
                        }
                    }
                } catch (exception: Exception) {
                    exceptionFailure("DEV select ids $table", exception)
                }
            }

        private suspend fun requestBuilder(path: String): Request.Builder? {
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
