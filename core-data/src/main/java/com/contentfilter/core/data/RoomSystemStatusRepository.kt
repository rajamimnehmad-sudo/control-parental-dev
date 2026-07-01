package com.contentfilter.core.data

import com.contentfilter.core.database.dao.SystemHealthDao
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.SystemHealthSnapshot
import com.contentfilter.core.domain.repository.SystemStatusRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

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

        private suspend fun updateHealth(transform: (SystemHealthSnapshot) -> SystemHealthSnapshot) {
            val now = System.currentTimeMillis()
            val current = systemHealthDao.current()?.toDomain() ?: defaultHealthSnapshot(now)
            systemHealthDao.upsert(transform(current).copy(checkedAtEpochMillis = now).toEntity())
        }
    }
