package com.contentfilter.core.network.remote

import com.contentfilter.core.network.config.AuthTokenProvider
import com.contentfilter.core.network.config.DeviceTokenProvider
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

class SupabaseRestClient
    @Inject
    constructor(
        private val configProvider: SupabaseConfigProvider,
        private val authTokenProvider: AuthTokenProvider,
        private val deviceTokenProvider: DeviceTokenProvider,
        private val httpClient: OkHttpClient,
    ) {
        suspend fun selectUpdatedSince(
            table: SupabaseTable,
            updatedAfterIso: String?,
        ): RemoteResult<JSONArray> =
            withContext(Dispatchers.IO) {
                val path =
                    buildString {
                        append("/rest/v1/")
                        append(table.tableName)
                        append("?select=*")
                        if (updatedAfterIso != null) {
                            append("&updated_at=gt.")
                            append(URLEncoder.encode(updatedAfterIso, Charsets.UTF_8.name()))
                        }
                        append("&order=updated_at.asc")
                    }
                executeArray(path, source = "Supabase select ${table.tableName}")
            }

        suspend fun selectAccounts(updatedAfterIso: String?): RemoteResult<JSONArray> =
            withContext(Dispatchers.IO) {
                val path =
                    buildString {
                        append("/rest/v1/accounts?select=id,name,community_id,updated_at,deleted_at,communities(name,guide_label)")
                        if (updatedAfterIso != null) {
                            append("&updated_at=gt.")
                            append(URLEncoder.encode(updatedAfterIso, Charsets.UTF_8.name()))
                        }
                        append("&order=updated_at.asc")
                    }
                executeArray(path, source = "Supabase select accounts")
            }

        suspend fun selectAll(table: SupabaseTable): RemoteResult<JSONArray> =
            withContext(Dispatchers.IO) {
                executeArray(
                    path = "/rest/v1/${table.tableName}?select=*&order=updated_at.asc",
                    source = "Supabase select ${table.tableName}",
                )
            }

        suspend fun upsert(
            table: SupabaseTable,
            json: JSONObject,
        ): RemoteResult<Unit> =
            withContext(Dispatchers.IO) {
                val request =
                    requestBuilder("/rest/v1/${table.tableName}")
                        ?: return@withContext RemoteResult.Failure(OfflineUserMessage, retryable = true)
                val body = json.toString().toRequestBody(JsonMediaType)
                try {
                    httpClient.newCall(
                        request
                            .post(body)
                            .header("Prefer", "resolution=merge-duplicates")
                            .build(),
                    ).execute().use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            RemoteResult.Success(Unit)
                        } else {
                            httpFailure(
                                source = "Supabase upsert ${table.tableName}",
                                code = response.code,
                                responseBody = responseBody,
                            )
                        }
                    }
                } catch (exception: Exception) {
                    exceptionFailure("Supabase upsert ${table.tableName}", exception)
                }
            }

        suspend fun invokeFunction(
            functionName: String,
            json: JSONObject,
        ): RemoteResult<Unit> =
            withContext(Dispatchers.IO) {
                val request =
                    requestBuilder("/functions/v1/$functionName")
                        ?: return@withContext RemoteResult.Failure(OfflineUserMessage, retryable = true)
                val body = json.toString().toRequestBody(JsonMediaType)
                try {
                    httpClient.newCall(request.post(body).build()).execute().use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            RemoteResult.Success(Unit)
                        } else {
                            httpFailure(
                                source = "Supabase function $functionName",
                                code = response.code,
                                responseBody = responseBody,
                            )
                        }
                    }
                } catch (exception: Exception) {
                    exceptionFailure("Supabase function $functionName", exception)
                }
            }

        suspend fun patchById(
            table: SupabaseTable,
            id: String,
            json: JSONObject,
        ): RemoteResult<Unit> =
            withContext(Dispatchers.IO) {
                val encodedId = URLEncoder.encode(id, Charsets.UTF_8.name())
                val request =
                    requestBuilder("/rest/v1/${table.tableName}?id=eq.$encodedId")
                        ?: return@withContext RemoteResult.Failure(OfflineUserMessage, retryable = true)
                val body = json.toString().toRequestBody(JsonMediaType)
                try {
                    httpClient.newCall(
                        request
                            .patch(body)
                            .header("Prefer", "return=minimal")
                            .build(),
                    ).execute().use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            RemoteResult.Success(Unit)
                        } else {
                            httpFailure(
                                source = "Supabase patch ${table.tableName}",
                                code = response.code,
                                responseBody = responseBody,
                            )
                        }
                    }
                } catch (exception: Exception) {
                    exceptionFailure("Supabase patch ${table.tableName}", exception)
                }
            }

        fun deviceSeenJson(): JSONObject {
            val now = Instant.now().toString()
            return JSONObject()
                .put("last_seen_at", now)
                .put("updated_at", now)
        }

        private suspend fun executeArray(
            path: String,
            source: String,
        ): RemoteResult<JSONArray> {
            val request =
                requestBuilder(path)
                    ?: return RemoteResult.Failure(OfflineUserMessage, retryable = true)
            return try {
                httpClient.newCall(request.get().build()).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        RemoteResult.Success(JSONArray(body))
                    } else {
                        httpFailure(source = source, code = response.code, responseBody = body)
                    }
                }
            } catch (exception: Exception) {
                exceptionFailure(source, exception)
            }
        }

        private suspend fun requestBuilder(path: String): Request.Builder? {
            val config = configProvider.current()
            val token = authTokenProvider.currentToken()
            val deviceToken = deviceTokenProvider.currentDeviceToken()
            val baseUrl = config.normalizedUrlOrNull()
            if (baseUrl == null) return null
            if (token == null && deviceToken == null) return null
            val builder =
                Request.Builder()
                    .url("$baseUrl$path")
                    .header("apikey", config.anonKey)
                    .header("Authorization", "Bearer ${token ?: config.anonKey}")
                    .header("Content-Type", "application/json")
            if (deviceToken != null) {
                builder.header("x-device-token", deviceToken)
            }
            return builder
        }

        private companion object {
            val JsonMediaType = "application/json".toMediaType()
        }
    }
