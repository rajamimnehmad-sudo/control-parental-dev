package com.contentfilter.core.network.remote

import com.contentfilter.core.network.config.AuthTokenProvider
import com.contentfilter.core.network.config.SupabaseConfigProvider
import java.net.URLEncoder
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class SupabaseRestClient
    @Inject
    constructor(
        private val configProvider: SupabaseConfigProvider,
        private val authTokenProvider: AuthTokenProvider,
        private val httpClient: OkHttpClient,
    ) {
        suspend fun selectUpdatedSince(
            table: SupabaseTable,
            updatedAfterIso: String?,
        ): RemoteResult<JSONArray> = withContext(Dispatchers.IO) {
            val path = buildString {
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

        suspend fun upsert(
            table: SupabaseTable,
            json: JSONObject,
        ): RemoteResult<Unit> = withContext(Dispatchers.IO) {
            val request = requestBuilder("/rest/v1/${table.tableName}")
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

        private fun executeArray(
            path: String,
            source: String,
        ): RemoteResult<JSONArray> {
            val request = requestBuilder(path)
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

        private fun requestBuilder(path: String): Request.Builder? {
            val config = configProvider.current()
            val token = authTokenProvider.currentToken()
            val baseUrl = config.normalizedUrlOrNull()
            if (baseUrl == null || token == null) return null
            return Request.Builder()
                .url("$baseUrl$path")
                .header("apikey", config.anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
        }

        private companion object {
            val JsonMediaType = "application/json".toMediaType()
        }
    }
