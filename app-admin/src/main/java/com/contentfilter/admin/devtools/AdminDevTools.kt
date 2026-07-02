package com.contentfilter.admin.devtools

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
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.network.remote.SupabaseDevMaintenanceClient
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AdminDevTools
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val accessRequestDao: AccessRequestDao,
        private val extraTimeGrantDao: ExtraTimeGrantDao,
        private val deviceDao: DeviceDao,
        private val deviceActivationDao: DeviceActivationDao,
        private val outboxOperationDao: OutboxOperationDao,
        private val syncCursorDao: SyncCursorDao,
        private val systemHealthDao: SystemHealthDao,
        private val technicalDiagnosticDao: TechnicalDiagnosticDao,
        private val usageSessionDao: UsageSessionDao,
        private val dailyLimitDao: DailyLimitDao,
        private val policyDao: PolicyDao,
        private val remoteClient: SupabaseDevMaintenanceClient,
    ) {
        suspend fun clearLocalRequests(): String = withContext(Dispatchers.IO) {
            accessRequestDao.deleteAll()
            outboxOperationDao.deleteAll()
            syncCursorDao.deleteAll()
            Log.i(LogTag, "DEV local access_requests/outbox/sync cursors cleared.")
            "Solicitudes locales borradas."
        }

        suspend fun clearRemoteRequests(): String {
            val activation = activeActivation()
            val result = remoteClient.clearRemoteRequests(activation.accountId)
            return result.toMessage("Solicitudes remotas borradas.")
        }

        suspend fun clearAllRequests(): String = withContext(Dispatchers.IO) {
            val activation = activeActivation()
            val remoteResult = remoteClient.clearRemoteRequests(activation.accountId)
            accessRequestDao.deleteAll()
            outboxOperationDao.deleteAll()
            syncCursorDao.deleteAll()
            remoteResult.toMessage("Todas las solicitudes fueron borradas.")
        }

        suspend fun clearRules(): String = withContext(Dispatchers.IO) {
            val activation = activeActivation()
            val remoteResult = remoteClient.clearRemotePolicyRules(activation.accountId)
            policyDao.deleteAllRules()
            outboxOperationDao.deleteAll()
            syncCursorDao.deleteAll()
            remoteResult.toMessage("Reglas borradas.")
        }

        suspend fun clearExtraTimeGrants(): String = withContext(Dispatchers.IO) {
            val activation = activeActivation()
            val remoteResult = remoteClient.clearRemoteExtraTimeGrants(activation.accountId)
            extraTimeGrantDao.deleteAll()
            outboxOperationDao.deleteAll()
            syncCursorDao.deleteAll()
            remoteResult.toMessage("Tiempos extra borrados.")
        }

        suspend fun clearDuplicateDevices(): String = withContext(Dispatchers.IO) {
            val activation = activeActivation()
            deviceDao.deleteExcept(activation.deviceId)
            deviceActivationDao.deleteExceptDevice(activation.deviceId)
            val result = remoteClient.clearDuplicateDevices(
                accountId = activation.accountId,
                keepDeviceId = activation.deviceId,
            )
            result.toMessage("Dispositivos duplicados borrados.")
        }

        suspend fun resetDev(): String = withContext(Dispatchers.IO) {
            val activation = deviceActivationDao.latest()
            val remoteResult = activation?.let {
                remoteClient.resetRemoteDev(accountId = it.accountId, keepDeviceId = it.deviceId)
            }
            accessRequestDao.deleteAll()
            extraTimeGrantDao.deleteAll()
            policyDao.deleteAllRules()
            activation?.deviceId?.let { deviceId ->
                deviceDao.deleteExcept(deviceId)
                deviceActivationDao.deleteExceptDevice(deviceId)
            } ?: run {
                deviceDao.deleteAll()
                deviceActivationDao.deleteAll()
            }
            outboxOperationDao.deleteAll()
            syncCursorDao.deleteAll()
            systemHealthDao.deleteAll()
            technicalDiagnosticDao.deleteAll()
            usageSessionDao.deleteAll()
            dailyLimitDao.deleteAll()
            context.cacheDir.deleteRecursively()
            Log.i(LogTag, "DEV full local reset completed. Remote result=$remoteResult")
            remoteResult?.toMessage("Reset DEV completo.") ?: "Reset DEV local completo."
        }

        private suspend fun activeActivation() =
            deviceActivationDao.latest() ?: error("No hay dispositivo activado para ejecutar herramienta DEV.")

        private fun RemoteResult<Unit>.toMessage(success: String): String =
            when (this) {
                is RemoteResult.Success -> success
                is RemoteResult.Failure -> if (reason == OfflineMessage) reason else "No se pudo completar la acción DEV."
            }

        private companion object {
            const val LogTag = "AdminDevTools"
            const val OfflineMessage = "Sin conexión. Mostrando datos guardados."
        }
    }
