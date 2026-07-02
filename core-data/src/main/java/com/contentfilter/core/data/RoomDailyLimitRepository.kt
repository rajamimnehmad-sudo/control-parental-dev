package com.contentfilter.core.data

import com.contentfilter.core.database.dao.DailyLimitDao
import com.contentfilter.core.domain.model.DailyLimit
import com.contentfilter.core.domain.repository.DailyLimitRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomDailyLimitRepository
    @Inject
    constructor(
        private val dailyLimitDao: DailyLimitDao,
    ) : DailyLimitRepository {
        override fun observeLimits(): Flow<List<DailyLimit>> =
            dailyLimitDao.observeEnabled().map { limits -> limits.map { it.toDomain() } }

        override suspend fun saveLimit(limit: DailyLimit) {
            dailyLimitDao.upsert(limit.toEntity())
        }
    }
