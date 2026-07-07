package com.contentfilter.user.repair

import android.content.Context
import android.util.Log
import com.contentfilter.core.database.dao.AccessRequestDao
import com.contentfilter.core.database.dao.DailyLimitDao
import com.contentfilter.core.database.dao.DeviceActivationDao
import com.contentfilter.core.database.dao.DeviceDao
import com.contentfilter.core.database.dao.ExtraTimeGrantDao
import com.contentfilter.core.database.dao.OutboxOperationDao
import com.contentfilter.core.database.dao.PolicyDao
import com.contentfilter.core.database.dao.SyncCursorDao
import com.contentfilter.core.database.dao.SystemHealthDao
import com.contentfilter.core.database.dao.TechnicalDiagnosticDao
import com.contentfilter.core.database.dao.UsageSessionDao
import com.contentfilter.core.domain.model.DeviceActivation
import com.contentfilter.core.network.config.DeviceTokenProvider
import com.contentfilter.core.network.remote.RemoteDeviceRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.user.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UserLocalDataRepair
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val accessRequestDao: AccessRequestDao,
        private val dailyLimitDao: DailyLimitDao,
        private val deviceActivationDao: DeviceActivationDao,
        private val deviceDao: DeviceDao,
        private val extraTimeGrantDao: ExtraTimeGrantDao,
        private val outboxOperationDao: OutboxOperationDao,
        private val policyDao: PolicyDao,
        private val syncCursorDao: SyncCursorDao,
        private val systemHealthDao: SystemHealthDao,
        private val technicalDiagnosticDao: TechnicalDiagnosticDao,
        private val usageSessionDao: UsageSessionDao,
        private val deviceTokenProvider: DeviceTokenProvider,
        private val remoteDeviceRepository: RemoteDeviceRepository,
    ) {
        private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

        suspend fun repairIfNeeded(): RepairResult =
            withContext(Dispatchers.IO) {
                clearOldVersionCacheIfNeeded()
                val activation = deviceActivationDao.latest()
                val deviceToken = deviceTokenProvider.currentDeviceToken()

                if (activation == null) {
                    clearForRelink("missing-local-activation")
                    return@withContext RepairResult.NeedsActivation(cleaned = true)
                }

                if (deviceToken == null) {
                    clearForRelink("activation-without-device-token")
                    return@withContext RepairResult.NeedsActivation(cleaned = true)
                }

                if (isDifferentLocalSession(activation.accountId, activation.deviceId)) {
                    clearAccountScopedData("room-session-changed", activation.deviceId)
                }

                when (val remoteDevices = remoteDeviceRepository.pullDevices(updatedAfterIso = null)) {
                    is RemoteResult.Success -> {
                        val remoteDeviceExists =
                            remoteDevices.value.any { it.id == activation.deviceId && it.deletedAt == null }
                        if (!remoteDeviceExists) {
                            clearForRelink("remote-device-missing")
                            return@withContext RepairResult.NeedsActivation(cleaned = true)
                        }
                        deviceDao.deleteExcept(activation.deviceId)
                        deviceActivationDao.deleteExceptDevice(activation.deviceId)
                        rememberLocalSession(activation.accountId, activation.deviceId)
                        RepairResult.Valid
                    }
                    is RemoteResult.Failure -> {
                        if (remoteDevices.retryable) {
                            Log.w(LogTag, "Remote device validation skipped: ${remoteDevices.reason}")
                            RepairResult.Valid
                        } else {
                            clearForRelink("remote-device-validation-failed")
                            RepairResult.NeedsActivation(cleaned = true)
                        }
                    }
                }
            }

        fun hasRevokedLicenseNotice(): Boolean = preferences.getBoolean(RevokedLicenseNoticeKey, false)

        suspend fun clearStaleDataAfterActivation(activation: DeviceActivation) =
            withContext(Dispatchers.IO) {
                val cleanupKey = activation.cleanupKey()
                if (preferences.getString(LastActivationCleanupKey, null) == cleanupKey) return@withContext
                clearAccountScopedData("activation-linked", keepDeviceId = activation.deviceId)
                rememberLocalSession(activation.accountId, activation.deviceId)
                rememberActivationCleanup(activation)
                preferences.edit()
                    .remove(RevokedLicenseNoticeKey)
                    .apply()
            }

        private suspend fun clearForRelink(reason: String) {
            Log.i(LogTag, "Clearing local user data for relink. reason=$reason")
            clearAccountScopedData(reason, keepDeviceId = null)
            deviceTokenProvider.clearDeviceToken()
            val editor =
                preferences.edit()
                .remove(LastAccountIdKey)
                .remove(LastDeviceIdKey)
                .remove(LastActivationCleanupKey)
            if (reason != "missing-local-activation") {
                editor.putBoolean(RevokedLicenseNoticeKey, true)
            }
            editor.apply()
        }

        private suspend fun clearAccountScopedData(
            reason: String,
            keepDeviceId: String?,
        ) {
            Log.i(LogTag, "Clearing account-scoped local data. reason=$reason keepDeviceId=$keepDeviceId")
            accessRequestDao.deleteAll()
            extraTimeGrantDao.deleteAll()
            policyDao.deleteAllRules()
            policyDao.deleteAllPolicies()
            dailyLimitDao.deleteAll()
            usageSessionDao.deleteAll()
            outboxOperationDao.deleteAll()
            syncCursorDao.deleteAll()
            systemHealthDao.deleteAll()
            technicalDiagnosticDao.deleteAll()
            if (keepDeviceId == null) {
                deviceDao.deleteAll()
                deviceActivationDao.deleteAll()
            } else {
                deviceDao.deleteExcept(keepDeviceId)
                deviceActivationDao.deleteExceptDevice(keepDeviceId)
            }
            clearCacheDir()
        }

        private fun isDifferentLocalSession(
            accountId: String,
            deviceId: String,
        ): Boolean {
            val lastAccountId = preferences.getString(LastAccountIdKey, null)
            val lastDeviceId = preferences.getString(LastDeviceIdKey, null)
            if (lastAccountId == null && lastDeviceId == null) return false
            return lastAccountId != accountId || lastDeviceId != deviceId
        }

        private fun rememberLocalSession(
            accountId: String,
            deviceId: String,
        ) {
            preferences.edit()
                .putString(LastAccountIdKey, accountId)
                .putString(LastDeviceIdKey, deviceId)
                .apply()
        }

        private fun rememberActivationCleanup(activation: DeviceActivation) {
            preferences.edit()
                .putString(LastActivationCleanupKey, activation.cleanupKey())
                .apply()
        }

        private fun DeviceActivation.cleanupKey(): String = "$accountId:$deviceId"

        private fun clearOldVersionCacheIfNeeded() {
            val lastVersion = preferences.getInt(LastVersionCodeKey, MissingVersionCode)
            if (lastVersion != BuildConfig.VERSION_CODE) {
                Log.i(LogTag, "Clearing old cache after version change. from=$lastVersion to=${BuildConfig.VERSION_CODE}")
                clearCacheDir()
                preferences.edit()
                    .putInt(LastVersionCodeKey, BuildConfig.VERSION_CODE)
                    .apply()
            }
        }

        private fun clearCacheDir() {
            context.cacheDir.listFiles()?.forEach { file ->
                file.deleteRecursively()
            }
        }

        private companion object {
            const val LogTag = "UserLocalRepair"
            const val PreferencesName = "user-local-repair"
            const val LastVersionCodeKey = "last-version-code"
            const val LastAccountIdKey = "last-account-id"
            const val LastDeviceIdKey = "last-device-id"
            const val LastActivationCleanupKey = "last-activation-cleanup"
            const val RevokedLicenseNoticeKey = "revoked-license-notice"
            const val MissingVersionCode = -1
        }
    }

sealed interface RepairResult {
    data object Valid : RepairResult

    data class NeedsActivation(val cleaned: Boolean) : RepairResult
}
