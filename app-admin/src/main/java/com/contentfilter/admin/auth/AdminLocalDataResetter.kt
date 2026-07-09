package com.contentfilter.admin.auth

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
import com.contentfilter.core.network.config.DeviceTokenProvider
import com.contentfilter.core.security.AuthSessionStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class AdminLocalDataResetter
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
        private val authSessionStore: AuthSessionStore,
    ) {
        suspend fun resetForNewAdminToken() =
            withContext(Dispatchers.IO) {
                Log.i(LogTag, "Resetting local admin data for new admin token")
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
                deviceDao.deleteAll()
                deviceActivationDao.deleteAll()
                deviceTokenProvider.clearDeviceToken()
                authSessionStore.clear()
                context.cacheDir.listFiles()?.forEach { file ->
                    file.deleteRecursively()
                }
            }

        private companion object {
            const val LogTag = "AdminLocalDataResetter"
        }
    }
