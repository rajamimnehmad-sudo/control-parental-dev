package com.contentfilter.user.chromeguard

import android.content.Context

internal class ChromeGuardStorage(
    context: Context,
) : ChromeGuardGenerationStore {
    private val preferences =
        context.createDeviceProtectedStorageContext()
            .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    init {
        migrateIfNeeded()
    }

    @Synchronized
    override fun nextGeneration(): Long {
        val next = preferences.getLong(KeyGeneration, 0L) + 1L
        check(preferences.edit().putLong(KeyGeneration, next).commit()) { "guard_generation_not_persisted" }
        return next
    }

    @Synchronized
    override fun recordState(
        suspended: Boolean,
        reason: String,
        bootMarker: Long,
    ) {
        check(
            preferences.edit()
                .putBoolean(KeyExpectedSuspended, suspended)
                .putString(KeyLastReason, reason.take(MaxReasonLength))
                .putLong(KeyBootMarker, bootMarker)
                .commit(),
        ) { "guard_state_not_persisted" }
    }

    @Synchronized
    override fun incrementRestartCount(): Long {
        val next = preferences.getLong(KeyRestartCount, 0L) + 1L
        check(preferences.edit().putLong(KeyRestartCount, next).commit()) { "guard_restart_not_persisted" }
        return next
    }

    internal fun storedSnapshot(): ChromeGuardStoredSnapshot =
        ChromeGuardStoredSnapshot(
            schemaVersion = preferences.getInt(KeySchemaVersion, 0),
            generation = preferences.getLong(KeyGeneration, 0L),
            expectedSuspended = preferences.getBoolean(KeyExpectedSuspended, true),
            lastReason = preferences.getString(KeyLastReason, "unknown").orEmpty(),
            bootMarker = preferences.getLong(KeyBootMarker, -1L),
            restartCount = preferences.getLong(KeyRestartCount, 0L),
        )

    private fun migrateIfNeeded() {
        if (!ChromeGuardStorageMigration.requiresReset(preferences.getInt(KeySchemaVersion, 0))) return
        check(
            preferences.edit().clear()
                .putInt(KeySchemaVersion, ChromeGuardContract.SchemaVersion)
                .putBoolean(KeyExpectedSuspended, true)
                .putString(KeyLastReason, "storage_migrated")
                .commit(),
        ) { "guard_storage_migration_failed" }
    }

    private companion object {
        const val PreferencesName = "chrome_guard_device_protected"
        const val KeySchemaVersion = "schema_version"
        const val KeyGeneration = "generation"
        const val KeyExpectedSuspended = "expected_suspended"
        const val KeyLastReason = "last_reason"
        const val KeyBootMarker = "boot_marker"
        const val KeyRestartCount = "restart_count"
        const val MaxReasonLength = 80
    }
}

internal object ChromeGuardStorageMigration {
    fun requiresReset(storedSchemaVersion: Int): Boolean =
        storedSchemaVersion != ChromeGuardContract.SchemaVersion
}

internal data class ChromeGuardStoredSnapshot(
    val schemaVersion: Int,
    val generation: Long,
    val expectedSuspended: Boolean,
    val lastReason: String,
    val bootMarker: Long,
    val restartCount: Long,
)
