package com.contentfilter.core.network.remote

import com.contentfilter.core.domain.model.LicenseEntitlement
import com.contentfilter.core.domain.model.LicenseState
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject

class SupabaseRemoteLicenseRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) : RemoteLicenseRepository {
        override suspend fun getDeviceEntitlement(deviceId: String): RemoteResult<LicenseEntitlement> =
            when (
                val result =
                    client.invokeRpcForObject(
                        "get_device_license_entitlement",
                        JSONObject().put("p_device_id", deviceId),
                    )
            ) {
                is RemoteResult.Success ->
                    runCatching { result.value.toEntitlement() }
                        .fold(
                            onSuccess = { RemoteResult.Success(it) },
                            onFailure = { RemoteResult.Failure("La licencia remota no es válida.", retryable = true) },
                        )
                is RemoteResult.Failure -> result
            }

        private fun JSONObject.toEntitlement(): LicenseEntitlement =
            LicenseEntitlement(
                state =
                    when (getString("status")) {
                        "active" -> LicenseState.Active
                        "scheduled" -> LicenseState.Scheduled
                        "suspended" -> LicenseState.Suspended
                        "expired" -> LicenseState.Expired
                        else -> LicenseState.Expired
                    },
                startsAtEpochMillis = optionalInstant("starts_at"),
                expiresAtEpochMillis = optionalInstant("expires_at"),
                verifiedAtEpochMillis = Instant.parse(getString("evaluated_at")).toEpochMilli(),
                dagEntitled = optBoolean("dag_entitled", false),
            )

        private fun JSONObject.optionalInstant(key: String): Long? =
            optString(key)
                .takeIf { it.isNotBlank() && it != "null" }
                ?.let { Instant.parse(it).toEpochMilli() }
    }
