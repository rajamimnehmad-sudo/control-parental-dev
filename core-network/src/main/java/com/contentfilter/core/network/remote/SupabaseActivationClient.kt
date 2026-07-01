package com.contentfilter.core.network.remote

import com.contentfilter.core.network.config.AuthTokenProvider
import com.contentfilter.core.network.config.SupabaseConfigProvider
import com.contentfilter.core.network.dto.RemoteDeviceActivationDto
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class SupabaseActivationClient
    @Inject
    constructor(
        private val configProvider: SupabaseConfigProvider,
        private val authTokenProvider: AuthTokenProvider,
        private val httpClient: OkHttpClient,
    ) {
        suspend fun activateDevice(
            activationCode: String,
            displayName: String,
            appVersionCode: Int,
            appRole: String,
        ): RemoteResult<RemoteDeviceActivationDto> = withContext(Dispatchers.IO) {
            val config = configProvider.current()
            val baseUrl = config.normalizedUrlOrNull()
            val token = authTokenProvider.currentToken()
            if (baseUrl == null || token == null) {
                return@withContext RemoteResult.Failure("Supabase activation requires an authenticated session.", retryable = true)
            }
            val body = JSONObject()
                .put("activation_code", activationCode)
                .put("device_display_name", displayName)
                .put("device_app_version_code", appVersionCode)
                .put("device_app_role", appRole)
                .toString()
                .toRequestBody(JsonMediaType)
            val request = Request.Builder()
                .url("$baseUrl/rest/v1/rpc/activate_device")
                .header("apikey", config.anonKey)
                .header("Authorization", "Bearer $token")
                .header("Content-Type", "application/json")
                .post(body)
                .build()
            try {
                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val first = JSONArray(responseBody).getJSONObject(0)
                        RemoteResult.Success(RemoteDeviceActivationDto.fromJson(first))
                    } else {
                        httpFailure(Source, response.code, responseBody)
                    }
                }
            } catch (exception: Exception) {
                exceptionFailure(Source, exception)
            }
        }

        private companion object {
            const val Source = "Supabase activate_device RPC"
            val JsonMediaType = "application/json".toMediaType()
        }
    }
