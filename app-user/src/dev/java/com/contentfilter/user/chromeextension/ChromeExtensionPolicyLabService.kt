package com.contentfilter.user.chromeextension

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.SystemClock
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver
import com.contentfilter.user.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.security.MessageDigest

class ChromeExtensionPolicyLabService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var devicePolicyManager: DevicePolicyManager
    private lateinit var admin: ComponentName
    private lateinit var preferences: SharedPreferences
    private var watchdog: Job? = null

    override fun onCreate() {
        super.onCreate()
        devicePolicyManager = getSystemService(DevicePolicyManager::class.java)
        admin = ComponentName(this, ProtectionDeviceAdminReceiver::class.java)
        preferences = getSharedPreferences(PreferencesName, MODE_PRIVATE)
        startForeground(NotificationId, notification())
        if (preferences.getBoolean(KeyActive, false)) startWatchdog()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        scope.launch {
            runCatching {
                when (intent?.action) {
                    ChromeExtensionPolicyLabReceiver.ActionSnapshot -> snapshot()
                    ChromeExtensionPolicyLabReceiver.ActionApply -> apply(intent)
                    ChromeExtensionPolicyLabReceiver.ActionHeartbeat -> heartbeat(intent)
                    ChromeExtensionPolicyLabReceiver.ActionStatus, null -> logStatus()
                    ChromeExtensionPolicyLabReceiver.ActionRestore -> restore("explicit")
                }
            }.onFailure { error ->
                Log.e(
                    LogTag,
                    "result=failed action=${intent?.action} " +
                        "error=${error.javaClass.simpleName}:${error.message}",
                )
                restore("exception")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        scope.launch { restore("task_removed") }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        if (preferences.getBoolean(KeyActive, false)) restoreBlocking("service_destroyed")
        watchdog?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun requireOwner() {
        check(packageName.endsWith(".dev")) { "DEV package required" }
        check(devicePolicyManager.isDeviceOwnerApp(packageName)) { "Device Owner required" }
    }

    private fun snapshot() {
        requireOwner()
        check(!preferences.getBoolean(KeyActive, false)) { "policy lab already active" }
        val bundle = currentRestrictions()
        val canonical = canonicalBundle(bundle)
        preferences.edit()
            .putString(KeySnapshotParcel, encode(bundle))
            .putString(KeySnapshotCanonical, canonical)
            .putString(KeySnapshotSha256, sha256(canonical.toByteArray()))
            .putBoolean(
                KeyChromeWasSuspended,
                packageManager.isPackageSuspended(ChromeExtensionPolicyContract.ChromePackage),
            )
            .commit()
        Log.i(
            LogTag,
            "phase=snapshot owner=${devicePolicyManager.isDeviceOwnerApp(packageName)} " +
                "affiliated=${devicePolicyManager.isAffiliatedUser} " +
                "chromeSuspended=${packageManager.isPackageSuspended(ChromeExtensionPolicyContract.ChromePackage)} " +
                "bundle=$canonical sha256=${sha256(canonical.toByteArray())}",
        )
    }

    private fun apply(intent: Intent) {
        requireOwner()
        if (preferences.getString(KeySnapshotParcel, null) == null) snapshot()
        val extensionId = requireNotNull(intent.getStringExtra(ChromeExtensionPolicyLabReceiver.ExtraExtensionId))
        val updateUrl = requireNotNull(intent.getStringExtra(ChromeExtensionPolicyLabReceiver.ExtraUpdateUrl))
        val leaseMillis =
            intent.getLongExtra(ChromeExtensionPolicyLabReceiver.ExtraLeaseMillis, DefaultLeaseMillis)
                .coerceIn(MinimumLeaseMillis, MaximumLeaseMillis)
        val current = currentRestrictions()
        val merged = Bundle(current)
        merged.putStringArray(
            ChromeExtensionPolicyContract.ExtensionInstallForcelist,
            ChromeExtensionPolicyContract.forceList(
                current.getStringArray(ChromeExtensionPolicyContract.ExtensionInstallForcelist),
                extensionId,
                updateUrl,
            ),
        )
        devicePolicyManager.setApplicationRestrictions(admin, ChromeExtensionPolicyContract.ChromePackage, merged)
        val applied = currentRestrictions()
        check(
            applied.getStringArray(ChromeExtensionPolicyContract.ExtensionInstallForcelist)
                ?.any { it == "$extensionId;$updateUrl" } == true,
        ) { "ExtensionInstallForcelist did not persist" }
        devicePolicyManager.setPackagesSuspended(
            admin,
            arrayOf(ChromeExtensionPolicyContract.ChromePackage),
            false,
        ).also { failures -> check(failures.isEmpty()) { "Chrome unsuspend failed: ${failures.joinToString()}" } }
        preferences.edit()
            .putBoolean(KeyActive, true)
            .putString(KeyExtensionId, extensionId)
            .putLong(KeyDeadlineElapsed, SystemClock.elapsedRealtime() + leaseMillis)
            .commit()
        startWatchdog()
        Log.i(
            LogTag,
            "phase=applied extensionId=$extensionId leaseMillis=$leaseMillis " +
                "bundle=${canonicalBundle(applied)}",
        )
    }

    private fun heartbeat(intent: Intent) {
        check(preferences.getBoolean(KeyActive, false)) { "policy lab not active" }
        val expectedId = preferences.getString(KeyExtensionId, null)
        check(expectedId == intent.getStringExtra(ChromeExtensionPolicyLabReceiver.ExtraExtensionId)) {
            "extension id mismatch"
        }
        val leaseMillis =
            intent.getLongExtra(ChromeExtensionPolicyLabReceiver.ExtraLeaseMillis, DefaultLeaseMillis)
                .coerceIn(MinimumLeaseMillis, MaximumLeaseMillis)
        preferences.edit().putLong(KeyDeadlineElapsed, SystemClock.elapsedRealtime() + leaseMillis).commit()
        Log.i(LogTag, "phase=heartbeat extensionId=$expectedId leaseMillis=$leaseMillis")
    }

    private fun logStatus() {
        val current = canonicalBundle(currentRestrictions())
        Log.i(
            LogTag,
            "phase=status active=${preferences.getBoolean(KeyActive, false)} " +
                "deadline=${preferences.getLong(KeyDeadlineElapsed, 0L)} current=$current " +
                "snapshot=${preferences.getString(KeySnapshotCanonical, Missing)}",
        )
    }

    private suspend fun restore(reason: String) {
        restoreBlocking(reason)
        stopSelf()
    }

    private fun restoreBlocking(reason: String) {
        val encoded = preferences.getString(KeySnapshotParcel, null)
        if (encoded == null) {
            Log.i(LogTag, "phase=restore result=no_snapshot reason=$reason")
            return
        }
        requireOwner()
        val snapshot = decode(encoded)
        devicePolicyManager.setApplicationRestrictions(admin, ChromeExtensionPolicyContract.ChromePackage, snapshot)
        if (preferences.getBoolean(KeyChromeWasSuspended, false)) {
            devicePolicyManager.setPackagesSuspended(
                admin,
                arrayOf(ChromeExtensionPolicyContract.ChromePackage),
                true,
            ).also { failures -> check(failures.isEmpty()) { "Chrome resuspend failed: ${failures.joinToString()}" } }
        }
        val restored = canonicalBundle(currentRestrictions())
        val expected = preferences.getString(KeySnapshotCanonical, null)
        check(restored == expected) { "Chrome restrictions restore mismatch" }
        preferences.edit().clear().commit()
        Log.i(LogTag, "phase=restore result=success reason=$reason bundle=$restored")
    }

    private fun startWatchdog() {
        watchdog?.cancel()
        watchdog =
            scope.launch {
                while (preferences.getBoolean(KeyActive, false)) {
                    if (SystemClock.elapsedRealtime() >= preferences.getLong(KeyDeadlineElapsed, 0L)) {
                        restore("heartbeat_timeout")
                        return@launch
                    }
                    delay(WatchdogPollMillis)
                }
            }
    }

    private fun currentRestrictions(): Bundle =
        devicePolicyManager.getApplicationRestrictions(admin, ChromeExtensionPolicyContract.ChromePackage)

    private fun encode(bundle: Bundle): String {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(bundle)
            Base64.encodeToString(parcel.marshall(), Base64.NO_WRAP)
        } finally {
            parcel.recycle()
        }
    }

    private fun decode(encoded: String): Bundle {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            requireNotNull(parcel.readBundle(javaClass.classLoader))
        } finally {
            parcel.recycle()
        }
    }

    private fun canonicalBundle(bundle: Bundle): String =
        bundle.keySet().sorted().joinToString(prefix = "{", postfix = "}") { key ->
            "${jsonString(key)}:${canonicalValue(bundle.get(key))}"
        }

    private fun canonicalValue(value: Any?): String =
        when (value) {
            null -> "null"
            is String -> jsonString(value)
            is Boolean, is Int, is Long, is Double -> value.toString()
            is Array<*> -> value.joinToString(prefix = "[", postfix = "]") { canonicalValue(it) }
            is Bundle -> canonicalBundle(value)
            else -> jsonString("unsupported:${value.javaClass.name}:$value")
        }

    private fun jsonString(value: String): String =
        buildString {
            append('"')
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(character)
                }
            }
            append('"')
        }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationChannelId,
                "Chrome extension DEV lab",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        return NotificationCompat.Builder(this, NotificationChannelId)
            .setSmallIcon(R.drawable.user_fish_icon)
            .setContentTitle("Glosh Chrome extension DEV")
            .setContentText("Política temporal con restauración automática")
            .setOngoing(true)
            .build()
    }

    private companion object {
        const val LogTag = "ChromeExtensionPolicy"
        const val PreferencesName = "chrome_extension_policy_lab"
        const val KeySnapshotParcel = "snapshot_parcel"
        const val KeySnapshotCanonical = "snapshot_canonical"
        const val KeySnapshotSha256 = "snapshot_sha256"
        const val KeyChromeWasSuspended = "chrome_was_suspended"
        const val KeyExtensionId = "extension_id"
        const val KeyDeadlineElapsed = "deadline_elapsed"
        const val KeyActive = "active"
        const val NotificationChannelId = "chrome_extension_policy_dev"
        const val NotificationId = 18_743
        const val DefaultLeaseMillis = 120_000L
        const val MinimumLeaseMillis = 30_000L
        const val MaximumLeaseMillis = 300_000L
        const val WatchdogPollMillis = 1_000L
        const val Missing = "<none>"
    }
}
