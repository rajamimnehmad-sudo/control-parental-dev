package com.contentfilter.core.network.remote

import android.util.Log
import com.contentfilter.core.network.config.AuthTokenProvider
import com.contentfilter.core.network.config.DeviceTokenProvider
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
        private val deviceTokenProvider: DeviceTokenProvider,
        private val httpClient: OkHttpClient,
    ) {
        suspend fun createDevicePairingCode(ttlMinutes: Int = 15): RemoteResult<PairingCodeDto> =
            withContext(Dispatchers.IO) {
                val config = configProvider.current()
                val baseUrl = config.normalizedUrlOrNull()
                val token = authTokenProvider.currentToken()
                val deviceToken = deviceTokenProvider.currentDeviceToken()
                if (baseUrl == null || (token == null && deviceToken == null)) {
                    return@withContext RemoteResult.Failure(
                        "Supabase pairing requires an activated admin device.",
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
                        .header("Authorization", "Bearer ${token ?: config.anonKey}")
                        .header("Content-Type", "application/json")
                        .post(body)
                if (deviceToken != null) {
                    request.header("x-device-token", deviceToken)
                }
                val builtRequest = request.build()
                executePairingCodeRequest(builtRequest, "Supabase create_device_pairing_code RPC")
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
                val normalizedPairingCode = pairingCode.normalizedPairingCodeForRpc()
                Log.i(
                    LogTag,
                    "pair_device_with_code request. normalizedCode=${normalizedPairingCode.maskForLog()} appRole=$appRole",
                )
                val body =
                    JSONObject()
                        .put("pairing_code", normalizedPairingCode)
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

        suspend fun pairAdminDeviceWithPassword(
            pairingCode: String,
            email: String,
            password: String,
            displayName: String,
            appVersionCode: Int,
            accessToken: String? = null,
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
                        .put("admin_email", email)
                        .put("admin_password", password)
                        .put("device_display_name", displayName)
                        .put("device_app_version_code", appVersionCode)
                        .toString()
                        .toRequestBody(JsonMediaType)
                val request =
                    Request.Builder()
                        .url("$baseUrl/rest/v1/rpc/pair_admin_device_with_password")
                        .header("apikey", config.anonKey)
                        .header("Authorization", "Bearer ${accessToken ?: config.anonKey}")
                        .header("Content-Type", "application/json")
                        .post(body)
                        .build()
                executeActivationRequest(request, "Supabase pair_admin_device_with_password RPC")
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
                        RemoteDeviceActivationDto.fromJson(first).also { dto ->
                            Log.i(LogTag, "$source success. deviceId=${dto.deviceId} activationId=${dto.activationId}")
                        }.let { dto -> RemoteResult.Success(dto) }
                    } else {
                        httpFailure(source, response.code, responseBody).also { failure ->
                            Log.w(LogTag, "$source rpcResult=failure rpcErrorCode=${failure.reason}")
                        }
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
            const val LogTag = "SupabaseActivation"
            val JsonMediaType = "application/json".toMediaType()

            fun String.normalizedPairingCodeForRpc(): String =
                filter { it.isLetterOrDigit() }.uppercase()

            fun String.maskForLog(): String =
                if (length <= 4) "****" else "${take(2)}***${takeLast(2)}"
        }
    }
