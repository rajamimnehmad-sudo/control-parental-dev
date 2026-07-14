package com.contentfilter.core.network.remote

import com.contentfilter.core.domain.model.DeviceProtectionControl
import com.contentfilter.core.domain.model.ProtectionAuthorizationScope
import com.contentfilter.core.domain.repository.ProtectionControlRepository
import org.json.JSONObject
import java.time.Instant
import javax.inject.Inject

class SupabaseProtectionControlRepository
    @Inject
    constructor(
        private val client: SupabaseRestClient,
    ) : ProtectionControlRepository {
        override suspend fun get(deviceId: String): Result<DeviceProtectionControl?> =
            when (
                val result =
                    client.selectByEquals(
                        table = SupabaseTable.DeviceProtectionControls,
                        filters = mapOf("device_id" to deviceId),
                    )
            ) {
                is RemoteResult.Success ->
                    runCatching {
                        result.value.optJSONObject(0)?.toProtectionControl()
                    }
                is RemoteResult.Failure -> Result.failure(IllegalStateException(result.reason))
            }

        override suspend fun setArmed(
            accountId: String,
            deviceId: String,
            armed: Boolean,
        ): Result<DeviceProtectionControl> =
            mutate(accountId, deviceId) { current, json ->
                json
                    .put("armed", armed)
                    .put("authorization_scope", "none")
                    .put("authorization_expires_at", JSONObject.NULL)
                    .put("command_revision", current.commandRevision + 1)
            }

        override suspend fun authorize(
            accountId: String,
            deviceId: String,
            scope: ProtectionAuthorizationScope,
            durationMinutes: Long,
        ): Result<DeviceProtectionControl> =
            mutate(accountId, deviceId) { current, json ->
                val expiration = Instant.now().plusSeconds(durationMinutes.coerceIn(1, 60) * 60)
                json
                    .put("authorization_scope", scope.remoteValue)
                    .put("authorization_expires_at", expiration.toString())
                    .put("command_revision", current.commandRevision + 1)
            }

        override suspend fun rotateRecovery(
            accountId: String,
            deviceId: String,
            salt: String,
            verifier: String,
        ): Result<DeviceProtectionControl> =
            mutate(accountId, deviceId) { current, json ->
                json
                    .put("recovery_salt", salt)
                    .put("recovery_verifier", verifier)
                    .put("recovery_revision", current.recoveryRevision + 1)
                    .put("command_revision", current.commandRevision + 1)
            }

        override suspend fun acknowledge(
            deviceId: String,
            commandRevision: Long,
            recoveryConsumedRevision: Long?,
        ): Result<Unit> {
            val json =
                JSONObject()
                    .put("p_device_id", deviceId)
                    .put("p_command_revision", commandRevision)
                    .put("p_recovery_consumed_revision", recoveryConsumedRevision ?: JSONObject.NULL)
            return client.invokeRpc("ack_device_protection_control", json).toResult()
        }

        private suspend fun mutate(
            accountId: String,
            deviceId: String,
            update: (DeviceProtectionControl, JSONObject) -> JSONObject,
        ): Result<DeviceProtectionControl> {
            val current =
                get(deviceId).getOrElse { return Result.failure(it) }
                    ?: DeviceProtectionControl(deviceId = deviceId, accountId = accountId)
            val now = Instant.now().toString()
            val json =
                update(
                    current,
                    JSONObject()
                        .put("device_id", deviceId)
                        .put("account_id", accountId)
                        .put("updated_at", now),
                )
            return when (val result = client.upsert(SupabaseTable.DeviceProtectionControls, json)) {
                is RemoteResult.Success ->
                    get(deviceId).mapCatching { requireNotNull(it) { "El control no quedó disponible." } }
                is RemoteResult.Failure -> Result.failure(IllegalStateException(result.reason))
            }
        }

        private fun JSONObject.toProtectionControl(): DeviceProtectionControl =
            DeviceProtectionControl(
                deviceId = getString("device_id"),
                accountId = getString("account_id"),
                armed = optBoolean("armed", false),
                authorizationScope = ProtectionAuthorizationScope.fromRemote(optString("authorization_scope")),
                authorizationExpiresAtEpochMillis =
                    optString("authorization_expires_at")
                        .takeIf { it.isNotBlank() && it != "null" }
                        ?.let { Instant.parse(it).toEpochMilli() },
                commandRevision = optLong("command_revision", 0),
                appliedRevision = optLong("applied_revision", 0),
                recoverySalt = optString("recovery_salt").takeIf { it.isNotBlank() && it != "null" },
                recoveryVerifier = optString("recovery_verifier").takeIf { it.isNotBlank() && it != "null" },
                recoveryRevision = optLong("recovery_revision", 0),
                recoveryConsumedRevision = optLong("recovery_consumed_revision", 0),
            )

        private fun RemoteResult<Unit>.toResult(): Result<Unit> =
            when (this) {
                is RemoteResult.Success -> Result.success(Unit)
                is RemoteResult.Failure -> Result.failure(IllegalStateException(reason))
            }
    }
