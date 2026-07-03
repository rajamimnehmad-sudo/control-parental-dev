package com.contentfilter.core.network.remote

import com.contentfilter.core.network.config.AuthTokenProvider
import com.contentfilter.core.network.config.SupabaseConfigProvider
import com.contentfilter.core.network.dto.RemoteDeviceActivationDto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class SupabaseActivationClient
    @Inject
    constructor(
        private val configProvider: SupabaseConfigProvider,
        private val authTokenProvider: AuthTokenProvider,
        private val httpClient: OkHttpClient,
    ) {
        suspend fun createDevicePairingCode(ttlMinutes: Int = 15): RemoteResult<PairingCodeDto> =
            withContext(Dispatchers.IO) {
                val config = configProvider.current()
                val baseUrl = config.normalizedUrlOrNull()
                val token = authTokenProvider.currentToken()
                if (baseUrl == null || token == null) {
                    return@withContext RemoteResult.Failure(
                        "Supabase pairing requires an authenticated admin session.",
                        retryable = true,
                    )
                }
                val body =
                    JSONObject()
                        .put("ttl_minutes", ttlMinutes)
                        .toString()
                        .toRequestBody(JsonMediaType)
                val request =
                    Request.Builder()
                        .url("$baseUrl/rest/v1/rpc/create_device_pairing_code")
                        .header("apikey", config.anonKey)
                        .header("Authorization", "Bearer $token")
                        .header("Content-Type", "application/json")
                        .post(body)
                        .build()
                executePairingCodeRequest(request, "Supabase create_device_pairing_code RPC")
            }

        suspend fun activateDevice(
            activationCode: String,
            displayName: String,
            appVersionCode: Int,
            appRole: String,
        ): RemoteResult<RemoteDeviceActivationDto> =
            withContext(Dispatchers.IO) {
                val config = configProvider.current()
                val baseUrl = config.normalizedUrlOrNull()
                val token = authTokenProvider.currentToken()
                if (baseUrl == null || token == null) {
                    return@withContext RemoteResult.Failure(
                        "Supabase activation requires an authenticated session.",
                        retryable = true,
                    )
                }
                val body =
                    JSONObject()
                        .put("activation_code", activationCode)
                        .put("device_display_name", displayName)
                        .put("device_app_version_code", appVersionCode)
                        .put("device_app_role", appRole)
                        .toString()
                        .toRequestBody(JsonMediaType)
                val request =
                    Request.Builder()
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

        suspend fun pairDeviceWithCode(
            pairingCode: String,
            displayName: String,
            appVersionCode: Int,
            appRole: String,
        ): RemoteResult<RemoteDeviceActivationDto> =
            withContext(Dispatchers.IO) {
                val config = configProvider.current()
                val baseUrl = config.normalizedUrlOrNull()
                if (baseUrl == null) {
                    return@withContext RemoteResult.Failure("Supabase pairing is not configured.", retryable = true)
                }
                val body =
                    JSONObject()
                        .put("pairing_code", pairingCode)
                        .put("device_display_name", displayName)
                        .put("device_app_version_code", appVersionCode)
                        .put("device_app_role", appRole)
                        .toString()
                        .toRequestBody(JsonMediaType)
                val request =
                    Request.Builder()
                        .url("$baseUrl/rest/v1/rpc/pair_device_with_code")
                        .header("apikey", config.anonKey)
                        .header("Authorization", "Bearer ${config.anonKey}")
                        .header("Content-Type", "application/json")
                        .post(body)
                        .build()
                executeActivationRequest(request, "Supabase pair_device_with_code RPC")
            }

        suspend fun revokeDevice(deviceId: String): RemoteResult<Unit> =
            withContext(Dispatchers.IO) {
                val config = configProvider.current()
                val baseUrl = config.normalizedUrlOrNull()
                val token = authTokenProvider.currentToken()
                if (baseUrl == null || token == null) {
                    return@withContext RemoteResult.Failure(
                        "Supabase revoke requires an authenticated admin session.",
                        retryable = true,
                    )
                }
                val body =
                    JSONObject()
                        .put("target_device_id", deviceId)
                        .toString()
                        .toRequestBody(JsonMediaType)
                val request =
                    Request.Builder()
                        .url("$baseUrl/rest/v1/rpc/revoke_device")
                        .header("apikey", config.anonKey)
                        .header("Authorization", "Bearer $token")
                        .header("Content-Type", "application/json")
                        .post(body)
                        .build()
                try {
                    httpClient.newCall(request).execute().use { response ->
                        val responseBody = response.body?.string().orEmpty()
                        if (response.isSuccessful) {
                            RemoteResult.Success(Unit)
                        } else {
                            httpFailure("Supabase revoke_device RPC", response.code, responseBody)
                        }
                    }
                } catch (exception: Exception) {
                    exceptionFailure("Supabase revoke_device RPC", exception)
                }
            }

        private fun executeActivationRequest(
            request: Request,
            source: String,
        ): RemoteResult<RemoteDeviceActivationDto> =
            try {
                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val first = JSONArray(responseBody).getJSONObject(0)
                        RemoteResult.Success(RemoteDeviceActivationDto.fromJson(first))
                    } else {
                        httpFailure(source, response.code, responseBody)
                    }
                }
            } catch (exception: Exception) {
                exceptionFailure(source, exception)
            }

        private fun executePairingCodeRequest(
            request: Request,
            source: String,
        ): RemoteResult<PairingCodeDto> =
            try {
                httpClient.newCall(request).execute().use { response ->
                    val responseBody = response.body?.string().orEmpty()
                    if (response.isSuccessful) {
                        val first = JSONArray(responseBody).getJSONObject(0)
                        RemoteResult.Success(
                            PairingCodeDto(
                                code = first.getString("activation_code"),
                                expiresAt = first.getString("expires_at"),
                            ),
                        )
                    } else {
                        httpFailure(source, response.code, responseBody)
                    }
                }
            } catch (exception: Exception) {
                exceptionFailure(source, exception)
            }

        data class PairingCodeDto(
            val code: String,
            val expiresAt: String,
        )

        private companion object {
            const val Source = "Supabase activate_device RPC"
            val JsonMediaType = "application/json".toMediaType()
        }
    }
