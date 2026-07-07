package com.contentfilter.core.network.remote

import android.util.Log
import com.contentfilter.core.network.config.AuthTokenProvider
import com.contentfilter.core.network.config.SupabaseConfigProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.net.URLEncoder
import javax.inject.Inject

class SupabaseDevMaintenanceClient
    @Inject
    constructor(
        private val configProvider: SupabaseConfigProvider,
        private val authTokenProvider: AuthTokenProvider,
        private val httpClient: OkHttpClient,
    ) {
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
        }
    }
