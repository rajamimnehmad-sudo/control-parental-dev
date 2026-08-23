package com.contentfilter.user.chromedataplane

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneRuntimeAttestation
import com.contentfilter.feature.vpn.service.VpnController
import com.contentfilter.user.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

class ChromePhotosDataPlaneLabService : Service() {
    private val lifecycle = ChromePhotosDataPlaneLifecycle()
    private var serviceScope: CoroutineScope? = null
    private var operation: Job? = null
    private var healthJob: Job? = null
    private var proxy: ChromePhotosHttpsProxy? = null
    private var currentSessionId = ""
    private lateinit var policyController: ChromePhotosLabPolicyController

    override fun onCreate() {
        super.onCreate()
        policyController = ChromePhotosLabPolicyController(this)
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        startForeground(NotificationId, notification())
        when (intent?.action) {
            ActionStop -> stopLab()
            ActionStatus -> logStatus()
            else -> startLab()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        markFailClosed("service_destroyed")
        healthJob?.cancel()
        runCatching { proxy?.close() }
        proxy = null
        runCatching { policyController.rollbackOwnedPolicyAndCa() }
        runCatching { VpnController.refreshDevLabRoutes(this) }
        operation?.cancel()
        serviceScope?.cancel()
        serviceScope = null
        currentSessionId = ""
        ChromePhotosDataPlaneRuntimeAttestation.clear()
        super.onDestroy()
    }

    private fun startLab() {
        if (operation?.isActive == true || lifecycle.current() in ActivePhases) {
            logStatus()
            return
        }
        operation =
            serviceScope?.launch {
                val preferences = labPreferences()
                try {
                    lifecycle.begin()
                    val sessionId = UUID.randomUUID().toString()
                    currentSessionId = sessionId
                    ChromePhotosDataPlaneRuntimeAttestation.beginSession(sessionId)
                    preferences.edit()
                        .clear()
                        .putString(ChromePhotosDataPlaneLabContract.KeySessionId, sessionId)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyActive, false)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyPresentationReady, false)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyProxyHealthy, false)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyPolicyConfirmed, false)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyVpnConfirmed, false)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyFixtureConfirmed, false)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyRealWebScopeConfirmed, false)
                        .commit()
                    policyController.verifyOwnerAndCleanOrphanedState()
                    val routeAddresses =
                        ChromePhotosRealWebRouteResolver().resolve(ChromePhotosRealWebLabConfig.realHosts)
                    preferences.edit()
                        .putStringSet(
                            ChromePhotosDataPlaneLabContract.KeyResolvedRouteAddresses,
                            routeAddresses,
                        )
                        .commit()
                    val tls = ChromePhotosEphemeralTls.create()
                    val origin = ChromePhotosFixtureOrigin()
                    val startedProxy =
                        ChromePhotosHttpsProxy(
                            tls = tls,
                            origin = origin,
                            onFixtureHeartbeat = ::markFixtureHeartbeat,
                            onFatalFailure = ::markFailClosed,
                        )
                    startedProxy.start()
                    proxy = startedProxy
                    ChromePhotosDataPlaneRuntimeAttestation.markProxyHealthy(sessionId, true)
                    lifecycle.proxyReady()
                    val policy = policyController.apply(tls)
                    ChromePhotosDataPlaneRuntimeAttestation.markPolicyConfirmed(sessionId, true)
                    preferences.edit()
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyActive, true)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyPresentationReady, false)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyProxyHealthy, true)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyPolicyConfirmed, true)
                        .putLong(ChromePhotosDataPlaneLabContract.KeyQuicAttempts, 0L)
                        .putLong(ChromePhotosDataPlaneLabContract.KeyDirectTcpAttempts, 0L)
                        .remove(ChromePhotosDataPlaneLabContract.KeyLastFailure)
                        .commit()
                    VpnController.refreshDevLabRoutes(this@ChromePhotosDataPlaneLabService)
                    startHealthAttestation(sessionId, tls.caCertificateDer)
                    Log.i(
                        LogTag,
                        "phase=active owner=device_owner scope=chrome_only session=${sessionId.take(SessionLogLength)} " +
                            "ca=${policy.caFingerprint.take(FingerprintLogLength)} " +
                            "fixture=controlled realHosts=${ChromePhotosRealWebLabConfig.realHosts.size} " +
                            "routes=${routeAddresses.size} privacy=public_only_memory_only",
                    )
                } catch (error: Throwable) {
                    lifecycle.fail()
                    markFailClosed(error.javaClass.simpleName)
                    proxy?.close()
                    proxy = null
                    runCatching { policyController.rollbackOwnedPolicyAndCa() }
                    runCatching { VpnController.refreshDevLabRoutes(this@ChromePhotosDataPlaneLabService) }
                    Log.e(LogTag, "phase=start_failed error=${error.javaClass.simpleName}")
                }
            }
    }

    private fun stopLab() {
        operation?.cancel()
        operation =
            serviceScope?.launch {
                markFailClosed("manual_stop")
                healthJob?.cancel()
                logStatus()
                proxy?.close()
                proxy = null
                policyController.rollbackOwnedPolicyAndCa()
                VpnController.refreshDevLabRoutes(this@ChromePhotosDataPlaneLabService)
                lifecycle.stop()
                currentSessionId = ""
                ChromePhotosDataPlaneRuntimeAttestation.clear()
                Log.i(LogTag, "phase=stopped rollback=complete cache=cleared")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
    }

    private fun markFixtureHeartbeat() {
        if (lifecycle.current() !in ActivePhases) return
        lifecycle.presentationReady()
        val wasConfirmed = ChromePhotosDataPlaneRuntimeAttestation.snapshot().fixtureConfirmed
        ChromePhotosDataPlaneRuntimeAttestation.markFixtureConfirmed(
            sessionId = currentSessionId,
            confirmed = true,
            heartbeatElapsed = SystemClock.elapsedRealtime(),
        )
        if (!wasConfirmed) {
            labPreferences().edit()
                .putBoolean(ChromePhotosDataPlaneLabContract.KeyPresentationReady, true)
                .putBoolean(ChromePhotosDataPlaneLabContract.KeyFixtureConfirmed, true)
                .commit()
            Log.i(LogTag, "phase=presentation_ready fixture=visible heartbeat=fresh failSafe=armed")
        }
    }

    private fun markFailClosed(reason: String) {
        ChromePhotosDataPlaneRuntimeAttestation.failClosed(currentSessionId)
        labPreferences().edit()
            .putBoolean(ChromePhotosDataPlaneLabContract.KeyPresentationReady, false)
            .putBoolean(ChromePhotosDataPlaneLabContract.KeyProxyHealthy, false)
            .putBoolean(ChromePhotosDataPlaneLabContract.KeyPolicyConfirmed, false)
            .putBoolean(ChromePhotosDataPlaneLabContract.KeyVpnConfirmed, false)
            .putBoolean(ChromePhotosDataPlaneLabContract.KeyFixtureConfirmed, false)
            .putBoolean(ChromePhotosDataPlaneLabContract.KeyRealWebScopeConfirmed, false)
            .putString(ChromePhotosDataPlaneLabContract.KeyLastFailure, reason.take(MaxFailureLength))
            .commit()
        Log.i(LogTag, "phase=fail_closed reason=${reason.take(MaxFailureLength)}")
    }

    private fun startHealthAttestation(
        sessionId: String,
        caCertificateDer: ByteArray,
    ) {
        healthJob?.cancel()
        healthJob =
            serviceScope?.launch {
                while (currentSessionId == sessionId && lifecycle.current() in ActivePhases) {
                    val proxyHealthy = proxy?.isHealthy() == true
                    val policyConfirmed =
                        runCatching {
                            policyController.isApplied(caCertificateDer) &&
                                labPreferences()
                                    .getStringSet(
                                        ChromePhotosDataPlaneLabContract.KeyResolvedRouteAddresses,
                                        emptySet(),
                                    ).orEmpty().isNotEmpty()
                        }.getOrDefault(false)
                    ChromePhotosDataPlaneRuntimeAttestation.markProxyHealthy(sessionId, proxyHealthy)
                    ChromePhotosDataPlaneRuntimeAttestation.markPolicyConfirmed(sessionId, policyConfirmed)
                    val runtime = ChromePhotosDataPlaneRuntimeAttestation.snapshot()
                    val now = SystemClock.elapsedRealtime()
                    val fixtureFresh =
                        runtime.fixtureConfirmed &&
                            now - runtime.fixtureHeartbeatElapsed <= FixtureHeartbeatMaximumAgeMillis
                    if (!fixtureFresh && runtime.fixtureConfirmed) {
                        ChromePhotosDataPlaneRuntimeAttestation.markFixtureConfirmed(sessionId, false)
                        labPreferences().edit()
                            .putBoolean(ChromePhotosDataPlaneLabContract.KeyPresentationReady, false)
                            .putBoolean(ChromePhotosDataPlaneLabContract.KeyFixtureConfirmed, false)
                            .apply()
                        Log.i(LogTag, "phase=fixture_scope_lost action=lease_revoked")
                    }
                    val realWebScopeConfirmed = proxyHealthy && policyConfirmed && runtime.vpnConfirmed
                    ChromePhotosDataPlaneRuntimeAttestation.markRealWebScopeConfirmed(
                        sessionId = sessionId,
                        confirmed = realWebScopeConfirmed,
                        heartbeatElapsed = now,
                    )
                    val wasRealWebReady =
                        labPreferences().getBoolean(
                            ChromePhotosDataPlaneLabContract.KeyRealWebScopeConfirmed,
                            false,
                        )
                    if (realWebScopeConfirmed && !wasRealWebReady) {
                        lifecycle.presentationReady()
                        labPreferences().edit()
                            .putBoolean(ChromePhotosDataPlaneLabContract.KeyPresentationReady, true)
                            .putBoolean(ChromePhotosDataPlaneLabContract.KeyRealWebScopeConfirmed, true)
                            .commit()
                        Log.i(LogTag, "phase=presentation_ready scope=real_web failSafe=armed")
                    } else if (!realWebScopeConfirmed && wasRealWebReady) {
                        labPreferences().edit()
                            .putBoolean(ChromePhotosDataPlaneLabContract.KeyPresentationReady, false)
                            .putBoolean(ChromePhotosDataPlaneLabContract.KeyRealWebScopeConfirmed, false)
                            .commit()
                    }
                    if (realWebScopeConfirmed || fixtureFresh) {
                        ChromePhotosDataPlaneRuntimeAttestation.publishHeartbeat(
                            sessionId = sessionId,
                            elapsed = now,
                            validUntilElapsed = now + AttestationLifetimeMillis,
                        )
                    } else {
                        ChromePhotosDataPlaneRuntimeAttestation.publishHeartbeat(sessionId, 0L, 0L)
                    }
                    if (!proxyHealthy || !policyConfirmed) {
                        markFailClosed(if (!proxyHealthy) "proxy_lost" else "policy_lost")
                        return@launch
                    }
                    delay(HealthCheckMillis)
                }
            }
    }

    private fun logStatus() {
        val preferences = labPreferences()
        val metrics = proxy?.metrics() ?: ChromePhotosProxyMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0)
        Log.i(
            LogTag,
            "phase=status lifecycle=${lifecycle.current()} active=${preferences.getBoolean(
                ChromePhotosDataPlaneLabContract.KeyActive,
                false,
            )} ready=${preferences.getBoolean(ChromePhotosDataPlaneLabContract.KeyPresentationReady, false)} " +
                "connections=${metrics.connections} requests=${metrics.requests} safe=${metrics.safeDecisions} " +
                "blocked=${metrics.blockedDecisions} unknown=${metrics.unknownDecisions} " +
                "passthrough=${metrics.passthroughResponses} cacheHits=${metrics.cacheHits} " +
                "cacheMisses=${metrics.cacheMisses} failures=${metrics.failures} " +
                "bytesIn=${metrics.originalBytes} bytesOut=${metrics.deliveredBytes} " +
                "quicAttempts=${preferences.getLong(ChromePhotosDataPlaneLabContract.KeyQuicAttempts, 0L)} " +
                "directTcpAttempts=${preferences.getLong(ChromePhotosDataPlaneLabContract.KeyDirectTcpAttempts, 0L)}",
        )
    }

    private fun labPreferences() =
        getSharedPreferences(
            ChromePhotosDataPlaneLabContract.PreferencesName,
            Context.MODE_PRIVATE,
        )

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NotificationChannelId,
                "Chrome Photos DEV lab",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        return NotificationCompat.Builder(this, NotificationChannelId)
            .setSmallIcon(R.drawable.user_fish_icon)
            .setContentTitle("Glosh Chrome Photos DEV")
            .setContentText("Laboratorio HTTPS local activo")
            .setOngoing(true)
            .build()
    }

    companion object {
        const val ActionStart = "com.contentfilter.user.chromedataplane.START"
        const val ActionStop = "com.contentfilter.user.chromedataplane.STOP"
        const val ActionStatus = "com.contentfilter.user.chromedataplane.STATUS"
        const val LogTag = "ChromePhotosDataPlane"

        private const val NotificationChannelId = "chrome_photos_data_plane_dev"
        private const val NotificationId = 18_742
        private const val FingerprintLogLength = 16
        private const val SessionLogLength = 8
        private const val MaxFailureLength = 80
        private const val HealthCheckMillis = 100L
        private const val AttestationLifetimeMillis = 400L
        private const val FixtureHeartbeatMaximumAgeMillis = 700L
        private val ActivePhases =
            setOf(
                ChromePhotosDataPlanePhase.ProxyReady,
                ChromePhotosDataPlanePhase.PresentationReady,
            )
    }
}
