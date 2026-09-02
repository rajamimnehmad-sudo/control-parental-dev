package com.contentfilter.user.chromeguard

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.Process
import android.os.RemoteException
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.contentfilter.user.R
import com.contentfilter.user.chromedataplane.ChromeBatteryBaselinePreconditions

class ChromeGuardService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var coordinator: ChromeGuardCoordinator
    private lateinit var messenger: Messenger
    private var mainBinder: IBinder? = null
    private var mainDeathRecipient: IBinder.DeathRecipient? = null
    private val expiryRunnable = Runnable { onLeaseDeadline() }
    private val baselineExpiryRunnable = Runnable { onBaselineDeadline() }
    private var baselineLease: ChromeBatteryBaselineLease? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(NotificationId, notification())
        val storage = ChromeGuardStorage(this)
        coordinator =
            ChromeGuardCoordinator(
                generationStore = storage,
                suspension = ChromeSuspensionAuthority(this),
                currentBootMarker = { ChromeGuardBootMarker.current(this) },
            )
        coordinator.initialize("guard_start")
        messenger = Messenger(IncomingHandler())
        logStatus("created")
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ActionStart -> revokeBaseline("normal_start")
            ActionLockedBoot -> invalidate("boot_guard")
            ActionBootCompleted -> invalidate("boot_guard")
            ActionPackageReplaced -> invalidate("package_replaced_guard")
            ActionDevKillSelf -> {
                Log.w(LogTag, "event=dev_kill_guard pid=${Process.myPid()}")
                // Return a non-redelivering result before killing the process. Killing synchronously
                // leaves this start unfinished, which lets Android redeliver the destructive DEV
                // action to the replacement process and creates a diagnostic-only crash loop.
                handler.post { Process.killProcess(Process.myPid()) }
                return START_NOT_STICKY
            }
            ActionStatus -> {
                expireBaselineIfNeeded()
                logStatus("requested")
            }
            ActionBatteryBaselineStart -> startBatteryBaseline(intent)
            ActionBatteryBaselineStop -> {
                revokeBaseline("baseline_stop")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ActionStop -> {
                invalidate(intent.getStringExtra(ChromeGuardContract.KeyReason) ?: "guard_stop")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            else -> if (intent == null) invalidate("guard_restarted")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        handler.removeCallbacks(expiryRunnable)
        handler.removeCallbacks(baselineExpiryRunnable)
        baselineLease = null
        unlinkMainDeathRecipient()
        coordinator.revoke("guard_destroyed")
        super.onDestroy()
    }

    private inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(message: Message) {
            val callerAuthorized = ChromeGuardCallerVerifier.isSameApplicationUid(this@ChromeGuardService, message.sendingUid)
            when (message.what) {
                ChromeGuardContract.MessageBeginSession -> {
                    revokeBaseline("normal_start")
                    beginSession(message, callerAuthorized)
                }
                ChromeGuardContract.MessageHeartbeat -> heartbeat(message, callerAuthorized)
                ChromeGuardContract.MessageRevoke -> {
                    val reason = message.data.getString(ChromeGuardContract.KeyReason).orEmpty().ifBlank { "main_revoked" }
                    invalidate(if (callerAuthorized) reason else "wrong_caller")
                }
                ChromeGuardContract.MessageStatus -> logStatus("ipc")
                ChromeGuardContract.MessageStop -> {
                    invalidate(if (callerAuthorized) "guard_stop" else "wrong_caller")
                    if (callerAuthorized) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
                ChromeGuardContract.MessageDevKillGuard -> {
                    if (!callerAuthorized) {
                        invalidate("wrong_caller")
                    } else {
                        Log.w(LogTag, "event=dev_kill_guard pid=${Process.myPid()}")
                        Process.killProcess(Process.myPid())
                    }
                }
                else -> super.handleMessage(message)
            }
        }
    }

    private fun beginSession(
        message: Message,
        callerAuthorized: Boolean,
    ) {
        val request = ChromeGuardBundleCodec.sessionRequest(message.data)
        val generation = coordinator.beginSession(request, callerAuthorized)
        if (generation == null) {
            reply(message, ChromeGuardContract.MessageRejected, 0L)
            logStatus("session_rejected")
            return
        }
        linkMainDeathRecipient(message.replyTo)
        reply(message, ChromeGuardContract.MessageSessionOpened, generation)
        Log.i(
            LogTag,
            "event=session_opened generation=$generation session=${request.sessionId.take(SessionLogLength)} " +
                "mainPidHint=${message.data.getInt(KeyMainPid, -1)} guardPid=${Process.myPid()}",
        )
    }

    private fun heartbeat(
        message: Message,
        callerAuthorized: Boolean,
    ) {
        val lease = ChromeGuardBundleCodec.lease(message.data)
        when (
            val result =
                coordinator.heartbeat(
                    lease = lease,
                    nowElapsed = SystemClock.elapsedRealtime(),
                    callerAuthorized = callerAuthorized,
                )
        ) {
            ChromeGuardLeaseVerification.Accepted -> {
                scheduleLeaseDeadline(lease.expiresAtElapsedRealtime)
                if (lease.heartbeatSequence == 1L) logStatus("first_lease")
            }
            is ChromeGuardLeaseVerification.Rejected -> {
                handler.removeCallbacks(expiryRunnable)
                Log.w(LogTag, "event=lease_rejected reason=${result.reason}")
            }
        }
    }

    private fun onLeaseDeadline() {
        val now = SystemClock.elapsedRealtime()
        if (coordinator.expireIfNeeded(now)) {
            Log.w(LogTag, "event=lease_expired reason=main_process_lost at=$now")
            logStatus("expired")
        } else {
            val expiry = coordinator.snapshot().leaseExpiresAtElapsed
            if (expiry > now) scheduleLeaseDeadline(expiry, now)
        }
    }

    private fun startBatteryBaseline(intent: Intent) {
        revokeBaseline("baseline_pending")
        val durationMillis =
            chromeBatteryBaselineDurationMillis(
                intent.getIntExtra(ExtraBatteryBaselineMinutes, 0),
            )
        val preconditions = ChromeBatteryBaselinePreconditions.capture(this)
        val reasons = preconditions.rejectionReasons()
        if (durationMillis == null || reasons.isNotEmpty()) {
            coordinator.revoke("baseline_precondition")
            Log.e(
                LogTag,
                "event=baseline_rejected durationValid=${durationMillis != null} " +
                    "reasons=${reasons.joinToString(",").ifBlank { "duration" }}",
            )
            logStatus("baseline_rejected")
            return
        }
        val now = SystemClock.elapsedRealtime()
        val lease =
            ChromeBatteryBaselineLease(
                bootMarker = ChromeGuardBootMarker.current(this),
                issuedAtElapsed = now,
                expiresAtElapsed = now + durationMillis,
            )
        if (!ChromeSuspensionAuthority(this).ensureReleased()) {
            coordinator.revoke("baseline_release_failed")
            logStatus("baseline_rejected")
            return
        }
        baselineLease = lease
        handler.removeCallbacks(baselineExpiryRunnable)
        handler.postDelayed(baselineExpiryRunnable, durationMillis)
        Log.i(
            LogTag,
            "event=baseline_started devOnly=true chrome=${ChromeGuardContract.ChromePackage} " +
                "boot=${lease.bootMarker} issued=${lease.issuedAtElapsed} expires=${lease.expiresAtElapsed}",
        )
        logStatus("baseline_started")
    }

    private fun onBaselineDeadline() {
        if (!expireBaselineIfNeeded()) {
            val lease = baselineLease ?: return
            val now = SystemClock.elapsedRealtime()
            handler.postDelayed(baselineExpiryRunnable, (lease.expiresAtElapsed - now).coerceAtLeast(0L))
        }
    }

    private fun expireBaselineIfNeeded(): Boolean {
        val lease = baselineLease ?: return false
        val current = lease.isCurrent(ChromeGuardBootMarker.current(this), SystemClock.elapsedRealtime())
        if (current) return false
        revokeBaseline("baseline_expired")
        Log.w(LogTag, "event=baseline_expired")
        return true
    }

    private fun revokeBaseline(reason: String) {
        if (baselineLease == null) return
        handler.removeCallbacks(baselineExpiryRunnable)
        baselineLease = null
        coordinator.revoke(reason)
        Log.i(LogTag, "event=baseline_revoked reason=$reason")
    }

    private fun scheduleLeaseDeadline(
        expiresAtElapsed: Long,
        nowElapsed: Long = SystemClock.elapsedRealtime(),
    ) {
        handler.removeCallbacks(expiryRunnable)
        handler.postDelayed(
            expiryRunnable,
            chromeGuardDeadlineDelayMillis(expiresAtElapsed, nowElapsed),
        )
    }

    private fun invalidate(reason: String) {
        handler.removeCallbacks(expiryRunnable)
        handler.removeCallbacks(baselineExpiryRunnable)
        baselineLease = null
        coordinator.revoke(reason)
        unlinkMainDeathRecipient()
        logStatus("invalidated")
    }

    private fun linkMainDeathRecipient(replyTo: Messenger?) {
        unlinkMainDeathRecipient()
        val binder = replyTo?.binder ?: return
        val recipient =
            IBinder.DeathRecipient {
                handler.post {
                    Log.w(LogTag, "event=main_binder_died reason=main_process_lost")
                    invalidate("main_process_lost")
                }
            }
        runCatching { binder.linkToDeath(recipient, 0) }
            .onSuccess {
                mainBinder = binder
                mainDeathRecipient = recipient
            }
            .onFailure { invalidate("main_process_lost") }
    }

    private fun unlinkMainDeathRecipient() {
        val binder = mainBinder
        val recipient = mainDeathRecipient
        if (binder != null && recipient != null) runCatching { binder.unlinkToDeath(recipient, 0) }
        mainBinder = null
        mainDeathRecipient = null
    }

    private fun reply(
        request: Message,
        what: Int,
        generation: Long,
    ) {
        val reply = Message.obtain(null, what)
        reply.data.putLong(ChromeGuardContract.KeyRequestId, request.data.getLong(ChromeGuardContract.KeyRequestId))
        reply.data.putLong(ChromeGuardContract.KeyProtectionGeneration, generation)
        try {
            request.replyTo?.send(reply)
        } catch (_: RemoteException) {
            invalidate("main_process_lost")
        }
    }

    private fun logStatus(event: String) {
        val snapshot = coordinator.snapshot()
        val baseline = baselineLease
        Log.i(
            LogTag,
            "event=$event state=${snapshot.state.name.lowercase()} generation=${snapshot.protectionGeneration} " +
                "session=${snapshot.sessionId.take(SessionLogLength)} leaseExpiry=${snapshot.leaseExpiresAtElapsed} " +
                "reason=${snapshot.lastReason} accepted=${snapshot.acceptedHeartbeats} " +
                "staleRejects=${snapshot.staleRejects} wrongCallerRejects=${snapshot.wrongCallerRejects} " +
                "guardRestarts=${snapshot.guardRestarts} pid=${Process.myPid()} " +
                "chromeSuspended=${ChromeSuspensionAuthority(this).isSuspended()} " +
                "baselineActive=${baseline != null} baselineBoot=${baseline?.bootMarker ?: -1L} " +
                "baselineIssued=${baseline?.issuedAtElapsed ?: 0L} baselineExpires=${baseline?.expiresAtElapsed ?: 0L}",
        )
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationChannelId,
                "Chrome protection guard DEV",
                NotificationManager.IMPORTANCE_MIN,
            ),
        )
        return NotificationCompat.Builder(this, NotificationChannelId)
            .setSmallIcon(R.drawable.user_fish_icon)
            .setContentTitle("Glosh Chrome guard DEV")
            .setContentText("Protección fail-closed activa")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ActionStart = "com.contentfilter.user.chromeguard.START"
        const val ActionStop = "com.contentfilter.user.chromeguard.STOP"
        const val ActionStatus = "com.contentfilter.user.chromeguard.STATUS"
        const val ActionLockedBoot = "com.contentfilter.user.chromeguard.LOCKED_BOOT"
        const val ActionBootCompleted = "com.contentfilter.user.chromeguard.BOOT_COMPLETED"
        const val ActionPackageReplaced = "com.contentfilter.user.chromeguard.PACKAGE_REPLACED"
        const val ActionDevKillSelf = "com.contentfilter.user.chromeguard.DEV_KILL_SELF"
        const val ActionBatteryBaselineStart = "com.contentfilter.user.chromeguard.BATTERY_BASELINE_START"
        const val ActionBatteryBaselineStop = "com.contentfilter.user.chromeguard.BATTERY_BASELINE_STOP"
        const val ExtraBatteryBaselineMinutes = "battery_baseline_minutes"
        const val KeyMainPid = "main_pid"
        const val LogTag = "ChromeProcessGuard"

        private const val NotificationChannelId = "chrome_process_guard_dev"
        private const val NotificationId = 18_743
        private const val SessionLogLength = 8
    }
}
