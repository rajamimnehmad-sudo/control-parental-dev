package com.contentfilter.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Central registry for Room migrations.
 *
 * Version 1 is the initial schema.
 */
object DatabaseMigrations {
    val Migration1To2: Migration =
        object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE usage_sessions ADD COLUMN deviceId TEXT NOT NULL DEFAULT 'local-device'")
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_usage_sessions_deviceId_packageName_startedAtEpochMillis
                    ON usage_sessions(deviceId, packageName, startedAtEpochMillis)
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE INDEX IF NOT EXISTS index_usage_sessions_deviceId_startedAtEpochMillis
                    ON usage_sessions(deviceId, startedAtEpochMillis)
                    """.trimIndent(),
                )
            }
        }

    val Migration2To3: Migration =
        object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE access_requests ADD COLUMN requestType TEXT NOT NULL DEFAULT 'APP_ACCESS'")
                db.execSQL("ALTER TABLE access_requests ADD COLUMN targetPackageName TEXT")
                db.execSQL("ALTER TABLE access_requests ADD COLUMN targetDomain TEXT")
            }
        }

    val Migration3To4: Migration =
        object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE devices ADD COLUMN appRole TEXT NOT NULL DEFAULT 'user'")
                db.execSQL("ALTER TABLE devices ADD COLUMN lastSeenAtEpochMillis INTEGER")
            }
        }

    val Migration4To5: Migration =
        object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE policies ADD COLUMN deviceId TEXT")
                db.execSQL("ALTER TABLE daily_limits ADD COLUMN policyId TEXT")
                db.execSQL("ALTER TABLE access_requests ADD COLUMN deviceId TEXT")
                db.execSQL("DROP INDEX IF EXISTS index_daily_limits_targetType_target")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_policies_deviceId ON policies(deviceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_limits_policyId ON daily_limits(policyId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_access_requests_deviceId ON access_requests(deviceId)")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_daily_limits_policyId_targetType_target
                    ON daily_limits(policyId, targetType, target)
                    """.trimIndent(),
                )
            }
        }

    val Migration5To6: Migration =
        object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE accounts ADD COLUMN communityId TEXT")
                db.execSQL(
                    "ALTER TABLE accounts ADD COLUMN communityName TEXT NOT NULL DEFAULT 'Comunidad Primero Año'",
                )
                db.execSQL("ALTER TABLE accounts ADD COLUMN guideName TEXT NOT NULL DEFAULT 'Equipo de guías'")
            }
        }

    val Migration6To7: Migration =
        object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_groups (
                        id TEXT NOT NULL PRIMARY KEY,
                        deviceId TEXT NOT NULL,
                        name TEXT NOT NULL,
                        color TEXT NOT NULL,
                        limitMinutes INTEGER NOT NULL,
                        resetMinuteOfDay INTEGER NOT NULL,
                        enabled INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_groups_deviceId ON app_groups(deviceId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_groups_enabled ON app_groups(enabled)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS app_group_apps (
                        id TEXT NOT NULL PRIMARY KEY,
                        groupId TEXT NOT NULL,
                        packageName TEXT NOT NULL,
                        enabled INTEGER NOT NULL,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_group_apps_groupId ON app_group_apps(groupId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_app_group_apps_packageName ON app_group_apps(packageName)")
                db.execSQL(
                    """
                    CREATE UNIQUE INDEX IF NOT EXISTS index_app_group_apps_groupId_packageName
                    ON app_group_apps(groupId, packageName)
                    """.trimIndent(),
                )
            }
        }

    val Migration7To8: Migration =
        object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE devices ADD COLUMN vpnState TEXT NOT NULL DEFAULT 'Unknown'")
                db.execSQL("ALTER TABLE devices ADD COLUMN accessibilityState TEXT NOT NULL DEFAULT 'Unknown'")
                db.execSQL("ALTER TABLE devices ADD COLUMN protectionAlert TEXT")
                db.execSQL("ALTER TABLE devices ADD COLUMN protectionUpdatedAtEpochMillis INTEGER")
            }
        }

    val Migration8To9: Migration =
        object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE policy_rules ADD COLUMN updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0",
                )
            }
        }

    val Migration9To10: Migration =
        object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE outbox_operations ADD COLUMN requestId TEXT")
                db.execSQL("ALTER TABLE outbox_operations ADD COLUMN aggregateId TEXT")
                db.execSQL("ALTER TABLE outbox_operations ADD COLUMN deviceId TEXT")
                db.execSQL("ALTER TABLE outbox_operations ADD COLUMN revision INTEGER")
                db.execSQL("ALTER TABLE outbox_operations ADD COLUMN priority INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_outbox_operations_status_priority_createdAtEpochMillis " +
                        "ON outbox_operations(status, priority, createdAtEpochMillis)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_outbox_operations_aggregateId_status_revision " +
                        "ON outbox_operations(aggregateId, status, revision)",
                )
                db.execSQL("ALTER TABLE daily_limits ADD COLUMN updatedAtEpochMillis INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE devices ADD COLUMN appliedPolicyId TEXT")
                db.execSQL("ALTER TABLE devices ADD COLUMN appliedPolicyRevision INTEGER")
                db.execSQL("ALTER TABLE devices ADD COLUMN policyAppliedAtEpochMillis INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS installed_apps (
                        id TEXT NOT NULL PRIMARY KEY,
                        accountId TEXT NOT NULL,
                        deviceId TEXT NOT NULL,
                        appName TEXT NOT NULL,
                        packageName TEXT NOT NULL,
                        versionName TEXT,
                        isSystemApp INTEGER NOT NULL,
                        iconBase64 TEXT,
                        updatedAtEpochMillis INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_installed_apps_deviceId ON installed_apps(deviceId)")
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_installed_apps_deviceId_packageName " +
                        "ON installed_apps(deviceId, packageName)",
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS index_installed_apps_updatedAtEpochMillis " +
                        "ON installed_apps(updatedAtEpochMillis)",
                )
            }
        }

    val Migration10To11: Migration =
        object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE system_health ADD COLUMN deviceAdminState TEXT NOT NULL DEFAULT 'Unknown'")
                db.execSQL("ALTER TABLE devices ADD COLUMN deviceAdminState TEXT NOT NULL DEFAULT 'Unknown'")
            }
        }

    val Migration11To12: Migration =
        object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE system_health ADD COLUMN licenseStartsAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE system_health ADD COLUMN licenseExpiresAtEpochMillis INTEGER")
                db.execSQL("ALTER TABLE system_health ADD COLUMN licenseVerifiedAtEpochMillis INTEGER")
            }
        }

    val All: Array<Migration> =
        arrayOf(
            Migration1To2,
            Migration2To3,
            Migration3To4,
            Migration4To5,
            Migration5To6,
            Migration6To7,
            Migration7To8,
            Migration8To9,
            Migration9To10,
            Migration10To11,
            Migration11To12,
        )
}
