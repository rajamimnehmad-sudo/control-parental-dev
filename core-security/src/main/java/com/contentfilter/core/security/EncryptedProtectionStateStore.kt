package com.contentfilter.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.contentfilter.core.domain.model.DeviceProtectionControl
import com.contentfilter.core.domain.model.ProtectionAuthorizationScope
import com.contentfilter.core.domain.model.RecoveryUnlockResult
import com.contentfilter.core.domain.repository.ProtectionStateStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EncryptedProtectionStateStore
    @Inject
    constructor(
        @ApplicationContext context: Context,
        private val recoveryCodeHasher: RecoveryCodeHasher,
    ) : ProtectionStateStore {
        private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

        @Volatile
        private var state: StoredState = loadSafely()

        private val controlState = MutableSharedFlow<DeviceProtectionControl?>(replay = 1).apply { tryEmit(state.control) }

        override fun observeControl(): Flow<DeviceProtectionControl?> = controlState

        override fun currentControl(): DeviceProtectionControl? = state.control

        @Synchronized
        override fun saveControl(control: DeviceProtectionControl) {
            val consumed = maxOf(state.pendingRecoveryConsumedRevision ?: 0, control.recoveryConsumedRevision)
            val recoveryChanged = recoveryMaterialChanged(state.control, control)
            persist(
                state.copy(
                    control = control.copy(recoveryConsumedRevision = consumed),
                    failedAttempts = if (recoveryChanged) 0 else state.failedAttempts,
                    lockedUntilEpochMillis = if (recoveryChanged) null else state.lockedUntilEpochMillis,
                    pendingRecoveryConsumedRevision = consumed.takeIf { it > control.recoveryConsumedRevision },
                ),
            )
        }

        override fun isArmed(): Boolean = state.control?.armed == true

        override fun isAuthorized(
            scope: ProtectionAuthorizationScope,
            nowEpochMillis: Long,
        ): Boolean {
            val current = state
            if (current.control?.hasAuthorization(scope, nowEpochMillis) == true) return true
            return scope == ProtectionAuthorizationScope.Removal &&
                current.localRemovalUntilEpochMillis?.let { it > nowEpochMillis } == true
        }

        override fun authorizationExpiresAtEpochMillis(nowEpochMillis: Long): Long? =
            listOfNotNull(
                state.control?.authorizationExpiresAtEpochMillis,
                state.localRemovalUntilEpochMillis,
            ).filter { it > nowEpochMillis }.maxOrNull()

        @Synchronized
        override fun authorizeTrustedInstall(untilEpochMillis: Long) {
            if (untilEpochMillis <= System.currentTimeMillis()) return
            persist(state.copy(trustedInstallUntilEpochMillis = untilEpochMillis))
        }

        override fun isTrustedInstallAuthorized(nowEpochMillis: Long): Boolean =
            state.trustedInstallUntilEpochMillis?.let { it > nowEpochMillis } == true

        @Synchronized
        override fun verifyAndConsumeRecovery(
            code: String,
            nowEpochMillis: Long,
        ): RecoveryUnlockResult {
            val current = state
            current.lockedUntilEpochMillis?.takeIf { it > nowEpochMillis }?.let {
                return RecoveryUnlockResult.Locked(it)
            }
            val control = current.control
            if (control == null || !control.hasAvailableRecovery) return RecoveryUnlockResult.Unavailable
            val matches =
                recoveryCodeHasher.matches(
                    rawCode = code,
                    salt = requireNotNull(control.recoverySalt),
                    expectedVerifier = requireNotNull(control.recoveryVerifier),
                )
            if (matches) {
                val validUntil = nowEpochMillis + RecoveryAuthorizationMillis
                persist(
                    current.copy(
                        failedAttempts = 0,
                        lockedUntilEpochMillis = null,
                        localRemovalUntilEpochMillis = validUntil,
                        pendingRecoveryConsumedRevision = control.recoveryRevision,
                        control = control.copy(recoveryConsumedRevision = control.recoveryRevision),
                    ),
                )
                return RecoveryUnlockResult.Unlocked(validUntil)
            }
            val attempts = current.failedAttempts + 1
            if (attempts >= MaxAttempts) {
                val lockedUntil = nowEpochMillis + LockoutMillis
                persist(current.copy(failedAttempts = 0, lockedUntilEpochMillis = lockedUntil))
                return RecoveryUnlockResult.Locked(lockedUntil)
            }
            persist(current.copy(failedAttempts = attempts))
            return RecoveryUnlockResult.Invalid(MaxAttempts - attempts)
        }

        override fun pendingRecoveryConsumedRevision(): Long? = state.pendingRecoveryConsumedRevision

        @Synchronized
        override fun markRecoveryConsumptionAcknowledged(revision: Long) {
            if (state.pendingRecoveryConsumedRevision == revision) {
                persist(state.copy(pendingRecoveryConsumedRevision = null))
            }
        }

        @Synchronized
        override fun cancelLocalRemovalAuthorization() {
            persist(state.copy(localRemovalUntilEpochMillis = null))
        }

        private fun loadSafely(): StoredState {
            val encoded = preferences.getString(EncryptedStateKey, null) ?: return StoredState()
            return runCatching { decrypt(encoded).toStoredState() }
                .getOrElse {
                    preferences.edit().remove(EncryptedStateKey).apply()
                    StoredState()
                }
        }

        @Synchronized
        private fun persist(updated: StoredState) {
            runCatching {
                preferences.edit().putString(EncryptedStateKey, encrypt(updated.toJson())).commit()
            }.onSuccess {
                state = updated
                controlState.tryEmit(updated.control)
            }
                .onFailure {
                    preferences.edit().remove(EncryptedStateKey).commit()
                    state = StoredState()
                    controlState.tryEmit(null)
                }
        }

        private fun encrypt(value: String): String {
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey())
            val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            return Base64.encodeToString(payload, Base64.NO_WRAP)
        }

        private fun decrypt(value: String): String {
            val payload = Base64.decode(value, Base64.NO_WRAP)
            require(payload.size > IvLength)
            val cipher = Cipher.getInstance(Transformation)
            cipher.init(
                Cipher.DECRYPT_MODE,
                secretKey(),
                GCMParameterSpec(TagLengthBits, payload.copyOfRange(0, IvLength)),
            )
            return cipher.doFinal(payload.copyOfRange(IvLength, payload.size)).toString(Charsets.UTF_8)
        }

        private fun secretKey(): SecretKey {
            val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
            (keyStore.getKey(KeyAlias, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
            generator.init(
                KeyGenParameterSpec.Builder(
                    KeyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            return generator.generateKey()
        }

        private fun StoredState.toJson(): String =
            JSONObject()
                .put("control", control?.toJson() ?: JSONObject.NULL)
                .put("failed_attempts", failedAttempts)
                .put("locked_until", lockedUntilEpochMillis ?: JSONObject.NULL)
                .put("local_removal_until", localRemovalUntilEpochMillis ?: JSONObject.NULL)
                .put("pending_consumed_revision", pendingRecoveryConsumedRevision ?: JSONObject.NULL)
                .put("trusted_install_until", trustedInstallUntilEpochMillis ?: JSONObject.NULL)
                .toString()

        private fun String.toStoredState(): StoredState {
            val json = JSONObject(this)
            return StoredState(
                control = json.optJSONObject("control")?.toControl(),
                failedAttempts = json.optInt("failed_attempts", 0),
                lockedUntilEpochMillis = json.optNullableLong("locked_until"),
                localRemovalUntilEpochMillis = json.optNullableLong("local_removal_until"),
                pendingRecoveryConsumedRevision = json.optNullableLong("pending_consumed_revision"),
                trustedInstallUntilEpochMillis = json.optNullableLong("trusted_install_until"),
            )
        }

        private fun DeviceProtectionControl.toJson(): JSONObject =
            JSONObject()
                .put("device_id", deviceId)
                .put("account_id", accountId)
                .put("armed", armed)
                .put("scope", authorizationScope.remoteValue)
                .put("expires_at", authorizationExpiresAtEpochMillis ?: JSONObject.NULL)
                .put("command_revision", commandRevision)
                .put("applied_revision", appliedRevision)
                .put("recovery_salt", recoverySalt ?: JSONObject.NULL)
                .put("recovery_verifier", recoveryVerifier ?: JSONObject.NULL)
                .put("recovery_revision", recoveryRevision)
                .put("recovery_consumed_revision", recoveryConsumedRevision)

        private fun JSONObject.toControl(): DeviceProtectionControl =
            DeviceProtectionControl(
                deviceId = getString("device_id"),
                accountId = getString("account_id"),
                armed = optBoolean("armed", false),
                authorizationScope = ProtectionAuthorizationScope.fromRemote(optString("scope")),
                authorizationExpiresAtEpochMillis = optNullableLong("expires_at"),
                commandRevision = optLong("command_revision", 0),
                appliedRevision = optLong("applied_revision", 0),
                recoverySalt = optString("recovery_salt").takeIf { it.isNotBlank() && it != "null" },
                recoveryVerifier = optString("recovery_verifier").takeIf { it.isNotBlank() && it != "null" },
                recoveryRevision = optLong("recovery_revision", 0),
                recoveryConsumedRevision = optLong("recovery_consumed_revision", 0),
            )

        private fun JSONObject.optNullableLong(key: String): Long? =
            if (isNull(key) || !has(key)) null else getLong(key)

        private data class StoredState(
            val control: DeviceProtectionControl? = null,
            val failedAttempts: Int = 0,
            val lockedUntilEpochMillis: Long? = null,
            val localRemovalUntilEpochMillis: Long? = null,
            val pendingRecoveryConsumedRevision: Long? = null,
            val trustedInstallUntilEpochMillis: Long? = null,
        )

        private companion object {
            const val PreferencesName = "reinforced-protection"
            const val EncryptedStateKey = "state"
            const val AndroidKeyStore = "AndroidKeyStore"
            const val KeyAlias = "content-filter-protection-v1"
            const val Transformation = "AES/GCM/NoPadding"
            const val IvLength = 12
            const val TagLengthBits = 128
            const val MaxAttempts = 5
            const val LockoutMillis = 15 * 60_000L
            const val RecoveryAuthorizationMillis = 10 * 60_000L
        }
    }

internal fun recoveryMaterialChanged(
    previous: DeviceProtectionControl?,
    current: DeviceProtectionControl,
): Boolean =
    previous == null ||
        previous.recoveryRevision != current.recoveryRevision ||
        previous.recoverySalt != current.recoverySalt ||
        previous.recoveryVerifier != current.recoveryVerifier
