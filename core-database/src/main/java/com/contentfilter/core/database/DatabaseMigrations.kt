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

    val All: Array<Migration> = arrayOf(Migration1To2, Migration2To3)
}
