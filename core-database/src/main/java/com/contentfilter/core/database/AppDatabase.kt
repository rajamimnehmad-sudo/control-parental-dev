package com.contentfilter.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
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
import com.contentfilter.core.database.entity.AccessRequestEntity
import com.contentfilter.core.database.entity.AccountEntity
import com.contentfilter.core.database.entity.DailyLimitEntity
import com.contentfilter.core.database.entity.DeviceActivationEntity
import com.contentfilter.core.database.entity.DeviceEntity
import com.contentfilter.core.database.entity.ExtraTimeGrantEntity
import com.contentfilter.core.database.entity.OutboxOperationEntity
import com.contentfilter.core.database.entity.PolicyEntity
import com.contentfilter.core.database.entity.PolicyRuleEntity
import com.contentfilter.core.database.entity.SyncCursorEntity
import com.contentfilter.core.database.entity.SystemHealthEntity
import com.contentfilter.core.database.entity.TechnicalDiagnosticEntity
import com.contentfilter.core.database.entity.UsageSessionEntity

@Database(
    entities = [
        PolicyEntity::class,
        PolicyRuleEntity::class,
        AccountEntity::class,
        DeviceEntity::class,
        DeviceActivationEntity::class,
        SystemHealthEntity::class,
        TechnicalDiagnosticEntity::class,
        DailyLimitEntity::class,
        UsageSessionEntity::class,
        AccessRequestEntity::class,
        ExtraTimeGrantEntity::class,
        SyncCursorEntity::class,
        OutboxOperationEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun policyDao(): PolicyDao

    abstract fun systemHealthDao(): SystemHealthDao

    abstract fun technicalDiagnosticDao(): TechnicalDiagnosticDao

    abstract fun dailyLimitDao(): DailyLimitDao

    abstract fun usageSessionDao(): UsageSessionDao

    abstract fun accessRequestDao(): AccessRequestDao

    abstract fun deviceActivationDao(): DeviceActivationDao

    abstract fun deviceDao(): DeviceDao

    abstract fun extraTimeGrantDao(): ExtraTimeGrantDao

    abstract fun syncCursorDao(): SyncCursorDao

    abstract fun outboxOperationDao(): OutboxOperationDao
}
