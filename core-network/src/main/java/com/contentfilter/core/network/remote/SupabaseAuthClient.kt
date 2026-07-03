package com.contentfilter.core.network.remote

import com.contentfilter.core.network.config.SupabaseConfigProvider
import com.contentfilter.core.network.dto.AuthSessionDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import javax.inject.Inject

class SupabaseAuthClient
    @Inject
    constructor(
        private val configProvider: SupabaseConfigProvider,
        private val httpClient: OkHttpClient,
    ) {
        suspend fun signInWithPassword(
            email: String,
            password: String,
        ): RemoteResult<AuthSessionDto> =
            withContext(Dispatchers.IO) {
                val config = configProvider.current()
                val baseUrl = config.normalizedUrlOrNull()
                if (baseUrl == null) {
                    return@withContext RemoteResult.Failure("Supabase Auth is not configured.", retryable = true)
                }
                val body =
                    JSONObject()
                        .put("email", email)
                        .put("password", password)
                        .toString()
                        .toRequestBody(JsonMediaType)
                val request =
                    Request.Builder()
                        .url("$baseUrl/auth/v1/token?grant_type=password")
                        .header("apikey", config.anonKey)
                        .header("Content-Type", "application/json")
                        .post(body)
                        .build()
                try {
                    httpClient.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            RemoteResult.Success(AuthSessionDto.fromJson(JSONObject(responseBody)))
                        } else {
                            httpFailure(Source, response.code, responseBody)
                        }
                    }
                } catch (exception: Exception) {
                    exceptionFailure(Source, exception)
                }
            }

        suspend fun refreshSession(refreshToken: String): RemoteResult<AuthSessionDto> =
            withContext(Dispatchers.IO) {
                val config = configProvider.current()
                val baseUrl = config.normalizedUrlOrNull()
                if (baseUrl == null) {
                    return@withContext RemoteResult.Failure("Supabase Auth is not configured.", retryable = true)
                }
                val body =
                    JSONObject()
                        .put("refresh_token", refreshToken)
                        .toString()
                        .toRequestBody(JsonMediaType)
                val request =
                    Request.Builder()
                        .url("$baseUrl/auth/v1/token?grant_type=refresh_token")
                        .header("apikey", config.anonKey)
                        .header("Content-Type", "application/json")
                        .post(body)
                        .build()
                try {
                    httpClient.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            RemoteResult.Success(AuthSessionDto.fromJson(JSONObject(responseBody)))
                        } else {
                            httpFailure("Supabase Auth refresh", response.code, responseBody)
                        }
                    }
                } catch (exception: Exception) {
                    exceptionFailure("Supabase Auth refresh", exception)
                }
            }

        private companion object {
            const val Source = "Supabase Auth sign-in"
            val JsonMediaType = "application/json".toMediaType()
        }
    }
