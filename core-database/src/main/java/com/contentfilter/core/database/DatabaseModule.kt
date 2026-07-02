package com.contentfilter.core.database

import android.content.Context
import androidx.room.Room
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
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "content-filter.db")
            .addMigrations(*DatabaseMigrations.All)
            .build()

    @Provides
    fun providePolicyDao(database: AppDatabase): PolicyDao = database.policyDao()

    @Provides
    fun provideSystemHealthDao(database: AppDatabase): SystemHealthDao = database.systemHealthDao()

    @Provides
    fun provideTechnicalDiagnosticDao(database: AppDatabase): TechnicalDiagnosticDao = database.technicalDiagnosticDao()

    @Provides
    fun provideDailyLimitDao(database: AppDatabase): DailyLimitDao = database.dailyLimitDao()

    @Provides
    fun provideUsageSessionDao(database: AppDatabase): UsageSessionDao = database.usageSessionDao()

    @Provides
    fun provideAccessRequestDao(database: AppDatabase): AccessRequestDao = database.accessRequestDao()

    @Provides
    fun provideDeviceActivationDao(database: AppDatabase): DeviceActivationDao = database.deviceActivationDao()

    @Provides
    fun provideDeviceDao(database: AppDatabase): DeviceDao = database.deviceDao()

    @Provides
    fun provideExtraTimeGrantDao(database: AppDatabase): ExtraTimeGrantDao = database.extraTimeGrantDao()

    @Provides
    fun provideSyncCursorDao(database: AppDatabase): SyncCursorDao = database.syncCursorDao()

    @Provides
    fun provideOutboxOperationDao(database: AppDatabase): OutboxOperationDao = database.outboxOperationDao()
}
