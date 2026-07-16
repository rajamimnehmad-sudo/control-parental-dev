package com.contentfilter.core.data

import com.contentfilter.core.database.dao.SystemHealthDao
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.LicenseEntitlement
import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.repository.SystemStatusRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomSystemStatusRepository
    @Inject
    constructor(
        private val systemHealthDao: SystemHealthDao,
    ) : SystemStatusRepository {
        override fun observeHealth(): Flow<SystemHealthSnapshot> =
            systemHealthDao.observeCurrent().map { entity ->
                entity?.toDomain() ?: defaultHealthSnapshot(System.currentTimeMillis())
            }

        override suspend fun currentHealth(): SystemHealthSnapshot =
            systemHealthDao.current()?.toDomain() ?: defaultHealthSnapshot(System.currentTimeMillis())

        override suspend fun updateVpnState(state: ComponentState) {
            updateHealth { it.copy(vpnState = state) }
        }

        override suspend fun updateAccessibilityState(state: ComponentState) {
            updateHealth { it.copy(accessibilityState = state) }
        }

        override suspend fun updateDeviceAdminState(state: ComponentState) {
            updateHealth { it.copy(deviceAdminState = state) }
        }

        override suspend fun updateSyncState(state: ComponentState) {
            updateHealth { it.copy(syncState = state) }
        }

        override suspend fun updateLicenseState(state: LicenseState) {
            updateHealth { it.copy(licenseState = state) }
        }

        override suspend fun updateLicenseEntitlement(entitlement: LicenseEntitlement) {
            val now = System.currentTimeMillis()
            updateHealth {
                it.copy(
                    licenseState = entitlement.effectiveState(now),
                    licenseStartsAtEpochMillis = entitlement.startsAtEpochMillis,
                    licenseExpiresAtEpochMillis = entitlement.expiresAtEpochMillis,
                    licenseVerifiedAtEpochMillis = entitlement.verifiedAtEpochMillis,
                )
            }
        }

        override suspend fun refreshLicenseState() {
            val now = System.currentTimeMillis()
            updateHealth { current ->
                current.copy(
                    licenseState =
                        LicenseEntitlement(
                            state = current.licenseState,
                            startsAtEpochMillis = current.licenseStartsAtEpochMillis,
                            expiresAtEpochMillis = current.licenseExpiresAtEpochMillis,
                            verifiedAtEpochMillis = current.licenseVerifiedAtEpochMillis ?: current.checkedAtEpochMillis,
                        ).effectiveState(now),
                )
            }
        }

        private suspend fun updateHealth(transform: (SystemHealthSnapshot) -> SystemHealthSnapshot) {
            val now = System.currentTimeMillis()
            val current = systemHealthDao.current()?.toDomain() ?: defaultHealthSnapshot(now)
            systemHealthDao.upsert(transform(current).copy(checkedAtEpochMillis = now).toEntity())
        }
    }
