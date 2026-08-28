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
import com.contentfilter.user.chromeguard.ChromeGuardClient
import com.contentfilter.user.chromeguard.ChromeGuardClientSession
import com.contentfilter.user.chromeguard.ChromeGuardContract
import com.contentfilter.user.chromeguard.ChromeGuardHealth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class ChromePhotosDataPlaneLabService : Service() {
    private val lifecycle = ChromePhotosDataPlaneLifecycle()
    private var serviceScope: CoroutineScope? = null
    private var operation: Job? = null
    private var healthJob: Job? = null
    private var proxy: ChromePhotosHttpsProxy? = null
    private var modelLoadMs = 0.0
    private var gloshiaReady = false
    private var currentSessionId = ""
    private var vpnRollbackCompleted = false
    private lateinit var policyController: ChromePhotosLabPolicyController
    private lateinit var bootstrapController: ChromePhotosTrustedBootstrapController
    private lateinit var guardClient: ChromeGuardClient
    private var guardSession: ChromeGuardClientSession? = null
    private var lastGuardHeartbeatElapsed = 0L

    override fun onCreate() {
        super.onCreate()
        policyController = ChromePhotosLabPolicyController(this)
        bootstrapController = ChromePhotosTrustedBootstrapController(this)
        serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        guardClient =
            ChromeGuardClient(this) {
                serviceScope?.launch { markFailClosed("guard_lost") }
            }
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
            ActionAuditMark -> markCoverageState(intent)
            else -> startLab(intent)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        guardClient.revoke("service_destroyed")
        markFailClosed("service_destroyed")
        healthJob?.cancel()
        runCatching { proxy?.close() }
        proxy = null
        runCatching { policyController.rollbackOwnedPolicyAndCa() }
        runCatching { restoreVpnStateAfterLab() }
        operation?.cancel()
        serviceScope?.cancel()
        serviceScope = null
        currentSessionId = ""
        guardSession = null
        guardClient.disconnect()
        ChromePhotosDataPlaneRuntimeAttestation.clear()
        super.onDestroy()
    }

    private fun startLab(intent: Intent?) {
        if (operation?.isActive == true || lifecycle.current() in ActivePhases) {
            logStatus()
            return
        }
        operation =
            serviceScope?.launch {
                val preferences = labPreferences()
                try {
                    val udpFixtureGate = ChromePhotosUdpFixtureGateConfig.fromIntent(intent)
                    val fullTunnelDevGateEnabled =
                        intent?.getBooleanExtra(
                            ChromePhotosDataPlaneLabReceiver.ExtraFullTunnelDevGateEnabled,
                            false,
                        ) == true
                    if (udpFixtureGate.enabled) {
                        require(
                            packageManager.getApplicationInfo(ChromePhotosDataPlaneLabContract.UdpFixturePackage, 0).enabled,
                        ) { "UDP fixture package is not installed and enabled" }
                    }
                    lifecycle.begin()
                    val vpnWasRunningBeforeLab = VpnController.isRunning(this@ChromePhotosDataPlaneLabService)
                    vpnRollbackCompleted = false
                    bootstrapController.requireDevOwnerAndBlockChrome("session_start")
                    policyController.verifyOwnerAndCleanOrphanedState()
                    bootstrapController.ensureInitialReset()
                    val sessionId = UUID.randomUUID().toString()
                    currentSessionId = sessionId
                    val coverageLedger =
                        ChromeRealWebProvenanceLedger { message -> Log.i(CoverageLogTag, message) }
                            .also { ledger -> ledger.beginSession(sessionId) }
                    ChromePhotosDataPlaneRuntimeAttestation.beginSession(sessionId)
                    guardSession =
                        guardClient.openSession(
                            sessionId = sessionId,
                            mainProcessNonce = UUID.randomUUID().toString(),
                            bootstrapGeneration = ChromePhotosDataPlaneLabContract.TrustedBootstrapGeneration,
                        )
                    lastGuardHeartbeatElapsed = 0L
                    bootstrapController.preserveAcrossSessionReset(preferences.edit().clear())
                        .putString(ChromePhotosDataPlaneLabContract.KeySessionId, sessionId)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyActive, false)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyPresentationReady, false)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyProxyHealthy, false)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyPolicyConfirmed, false)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyVpnConfirmed, false)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyFixtureConfirmed, false)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyRealWebScopeConfirmed, false)
                        .putBoolean(KeyVpnWasRunningBeforeLab, vpnWasRunningBeforeLab)
                        .putBoolean(ChromePhotosDataPlaneLabContract.KeyUdpFixtureGateEnabled, udpFixtureGate.enabled)
                        .putString(ChromePhotosDataPlaneLabContract.KeyUdpFixtureAddress, udpFixtureGate.address)
                        .putInt(ChromePhotosDataPlaneLabContract.KeyUdpFixturePort, udpFixtureGate.port)
                        .putBoolean(
                            ChromePhotosDataPlaneLabContract.KeyUdpFixtureMalformedProbeEnabled,
                            udpFixtureGate.malformedProbeEnabled,
                        )
                        .commit()
                    val routeAddresses =
                        if (fullTunnelDevGateEnabled) {
                            emptySet()
                        } else {
                            ChromePhotosRealWebRouteResolver().resolve(ChromePhotosRealWebLabConfig.controlledRouteHosts)
                        }
                    preferences.edit()
                        .putStringSet(
                            ChromePhotosDataPlaneLabContract.KeyResolvedRouteAddresses,
                            routeAddresses,
                        )
                        .commit()
                    val tls = ChromePhotosEphemeralTls.create()
                    val origin = ChromePhotosFixtureOrigin()
                    val gloshia =
                        when (val creation = ChromePhotosGloshiaEngineFactory.create(this@ChromePhotosDataPlaneLabService)) {
                            is ChromePhotosGloshiaEngineCreation.Ready -> creation
                            is ChromePhotosGloshiaEngineCreation.Unavailable ->
                                error("gloshia_${creation.reason}")
                        }
                    modelLoadMs = gloshia.modelLoadMs
                    gloshiaReady = true
                    val modelIdentity = gloshia.engine.identity
                    lateinit var startedProxy: ChromePhotosHttpsProxy
                    val decisionSession =
                        ChromePhotosBoundedDecisionSession(
                            engine = gloshia.engine,
                            onSystemicFailure = { reason ->
                                startedProxy.fatal(IllegalStateException("gloshia_$reason"))
                            },
                        )
                    val transformer = chromePhotosGloshiaTransformer(decisionSession, origin)
                    startedProxy =
                        ChromePhotosHttpsProxy(
                            tls = tls,
                            origin = origin,
                            onFixtureHeartbeat = ::markFixtureHeartbeat,
                            onFatalFailure = ::markFailClosed,
                            transformer = transformer,
                            coverageLedger = coverageLedger,
                        )
                    proxy = startedProxy
                    startedProxy.start()
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
                    check(
                        VpnController.configureDevFullTunnelGate(
                            this@ChromePhotosDataPlaneLabService,
                            fullTunnelDevGateEnabled,
                        ),
                    ) { "full_tunnel_gate_configuration_failed" }
                    VpnController.refreshDevLabRoutes(this@ChromePhotosDataPlaneLabService)
                    startHealthAttestation(sessionId, tls.caCertificateDer)
                    Log.i(
                        LogTag,
                        "phase=active owner=device_owner scope=chrome_only session=${sessionId.take(SessionLogLength)} " +
                            "ca=${policy.caFingerprint.take(FingerprintLogLength)} " +
                            "model=${modelIdentity.modelVersion} modelSha=${modelIdentity.modelSha256} " +
                            "policy=${modelIdentity.policyVersion} " +
                            "modelLoadMs=${"%.3f".format(Locale.US, modelLoadMs)} " +
                            "fixture=controlled fallbackHosts=${ChromePhotosRealWebLabConfig.controlledRouteHosts.size} " +
                            "routes=${routeAddresses.size} udpFixture=${udpFixtureGate.enabled} " +
                            "udpTarget=${udpFixtureGate.address}:${udpFixtureGate.port} " +
                            "transport=${if (fullTunnelDevGateEnabled) "full_tunnel_dev" else "controlled"} " +
                            "privacy=public_only_memory_only",
                    )
                } catch (error: Throwable) {
                    lifecycle.fail()
                    markFailClosed(error.javaClass.simpleName)
                    proxy?.close()
                    proxy = null
                    runCatching { policyController.rollbackOwnedPolicyAndCa() }
                    runCatching { restoreVpnStateAfterLab() }
                    guardClient.stopGuard("start_failed")
                    guardSession = null
                    guardClient.disconnect()
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
                restoreVpnStateAfterLab()
                lifecycle.stop()
                currentSessionId = ""
                guardClient.stopGuard("manual_stop")
                guardSession = null
                guardClient.disconnect()
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

    private fun markCoverageState(intent: Intent) {
        val label = intent.getStringExtra(ExtraAuditStateLabel).orEmpty()
        val newNavigation = intent.getBooleanExtra(ExtraAuditNewNavigation, false)
        val state = runCatching { proxy?.markCoverageState(label, newNavigation) }.getOrNull()
        Log.i(
            CoverageLogTag,
            "audit17 event=mark_result accepted=${state != null} state=${state ?: 0} " +
                "newNavigation=$newNavigation",
        )
    }

    private fun markFailClosed(reason: String) {
        gloshiaReady = false
        guardClient.revoke(reason)
        runCatching { bootstrapController.requireDevOwnerAndBlockChrome(reason) }
            .onFailure { error ->
                Log.e(LogTag, "bootstrap=chrome_block_failed error=${error.javaClass.simpleName}")
            }
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
                                (
                                    VpnController.isDevFullTunnelGateActive(this@ChromePhotosDataPlaneLabService) ||
                                        labPreferences()
                                            .getStringSet(
                                                ChromePhotosDataPlaneLabContract.KeyResolvedRouteAddresses,
                                                emptySet(),
                                            ).orEmpty().isNotEmpty()
                                )
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
                    val bootstrapHealth =
                        ChromePhotosTrustedBootstrapHealth(
                            proxyHealthy = proxyHealthy,
                            policyConfirmed = policyConfirmed,
                            vpnConfirmed = runtime.vpnConfirmed,
                            gloshiaReady = gloshiaReady,
                            accessibilityBound = runtime.accessibilityBound,
                        )
                    ChromePhotosTrustedBootstrapPolicy.failCloseReason(
                        previouslyReleased = wasRealWebReady,
                        health = bootstrapHealth,
                    )?.let { reason ->
                        markFailClosed(reason)
                        return@launch
                    }
                    val chromeReleased =
                        realWebScopeConfirmed &&
                            bootstrapController.markChromeReleaseEligibleIfHealthy(bootstrapHealth) &&
                            publishGuardHeartbeatIfDue(
                                now = now,
                                health = bootstrapHealth,
                                realWebScopeConfirmed = realWebScopeConfirmed,
                            ) &&
                            !bootstrapController.isChromeSuspended()
                    if (chromeReleased && !wasRealWebReady) {
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
                        markFailClosed("real_web_scope_lost")
                        return@launch
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

    private fun publishGuardHeartbeatIfDue(
        now: Long,
        health: ChromePhotosTrustedBootstrapHealth,
        realWebScopeConfirmed: Boolean,
    ): Boolean {
        val session = guardSession ?: return false
        if (now - lastGuardHeartbeatElapsed >= ChromeGuardContract.HeartbeatIntervalMillis) {
            val published =
                guardClient.publishHeartbeat(
                    session = session,
                    health =
                        ChromeGuardHealth(
                            vpnHealthy = health.vpnConfirmed,
                            transportHealthy = realWebScopeConfirmed && health.vpnConfirmed,
                            proxyHealthy = health.proxyHealthy,
                            policyHealthy = health.policyConfirmed,
                            gloshiaHealthy = health.gloshiaReady,
                            accessibilityHealthy = health.accessibilityBound,
                            bootstrapHealthy =
                                bootstrapController.state().resetGeneration ==
                                    ChromePhotosDataPlaneLabContract.TrustedBootstrapGeneration,
                        ),
                )
            if (!published) return false
            lastGuardHeartbeatElapsed = now
        }
        return true
    }

    private fun logStatus() {
        val preferences = labPreferences()
        val metrics = proxy?.metrics() ?: ChromePhotosProxyMetrics()
        val decisions = metrics.decisionSession
        val images = metrics.imageAuthority
        val bootstrap = bootstrapController.state()
        val coverage = proxy?.coverageSnapshot()
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
                "streamed=${metrics.streamedResponses} proxyQueueRejects=${metrics.queueRejected} " +
                "proxyActivePeak=${metrics.activeConnectionsPeak} " +
                "proxyP50Ms=${"%.3f".format(Locale.US, metrics.latencyP50Millis)} " +
                "proxyP95Ms=${"%.3f".format(Locale.US, metrics.latencyP95Millis)} " +
                "proxyP99Ms=${"%.3f".format(Locale.US, metrics.latencyP99Millis)} " +
                "upstreamSockets=${metrics.upstream.protectedSocketsCreated} " +
                "protectSuccess=${metrics.upstream.protectSuccess} protectFailure=${metrics.upstream.protectFailure} " +
                "web11a=${metrics.webSemanticsReport} web11b=${metrics.imageAuthorityReport} " +
                "modelLoadMs=${"%.3f".format(Locale.US, modelLoadMs)} " +
                "engineCalls=${decisions.engineCalls} dedupeHits=${decisions.dedupeHits} " +
                "inferencePeak=${decisions.inferencePeak} inFlightPeak=${decisions.inFlightPeak} " +
                "queuePeak=${decisions.queuePeak} queueRejects=${decisions.queueRejects} " +
                "timeouts=${decisions.timeouts} inferenceP50Ms=${decisions.inferenceP50Ms} " +
                "inferenceP95Ms=${decisions.inferenceP95Ms} inferenceP99Ms=${decisions.inferenceP99Ms} " +
                "decisionP50Ms=${decisions.decisionP50Ms} decisionP95Ms=${decisions.decisionP95Ms} " +
                "decisionP99Ms=${decisions.decisionP99Ms} cacheHitP50Ms=${decisions.cacheHitP50Ms} " +
                "cacheHitP95Ms=${decisions.cacheHitP95Ms} " +
                "imageCandidates=${images.candidates} imagePrefixPeeks=${images.prefixPeeks} " +
                "imageMagicCandidates=${images.magicCandidates} imageBodyAdmissionPeak=${images.bodyAdmissionPeak} " +
                "imageBodyAdmissionRejects=${images.bodyAdmissionRejects} " +
                "quicAttempts=${preferences.getLong(ChromePhotosDataPlaneLabContract.KeyQuicAttempts, 0L)} " +
                "directTcpAttempts=${preferences.getLong(ChromePhotosDataPlaneLabContract.KeyDirectTcpAttempts, 0L)} " +
                "bootstrapResetGeneration=${bootstrap.resetGeneration} " +
                "bootstrapCompleteGeneration=${bootstrap.completeGeneration} " +
                "bootstrapResetCount=${bootstrap.resetCount} " +
                "chromeSuspended=${bootstrapController.isChromeSuspended()} " +
                "guardSession=${guardSession?.protectionGeneration ?: 0L} " +
                "guardHeartbeatSequence=${guardSession?.heartbeatSequence ?: 0L} " +
                "audit17State=${coverage?.stateSequence ?: 0L} " +
                "audit17Navigation=${coverage?.navigationSequence ?: 0L} " +
                "audit17Events=${coverage?.events?.size ?: 0} " +
                "audit17Dropped=${coverage?.droppedEvents ?: 0L}",
        )
    }

    private fun restoreVpnStateAfterLab() {
        if (vpnRollbackCompleted) return
        val preferences = labPreferences()
        if (!preferences.contains(KeyVpnWasRunningBeforeLab)) return
        val action =
            chromePhotosVpnRollbackAction(
                vpnWasRunningBeforeLab = preferences.getBoolean(KeyVpnWasRunningBeforeLab, false),
            )
        when (action) {
            ChromePhotosVpnRollbackAction.RefreshRoutes -> VpnController.refreshDevLabRoutes(this)
            ChromePhotosVpnRollbackAction.Stop -> VpnController.stop(this)
        }
        preferences.edit().remove(KeyVpnWasRunningBeforeLab).commit()
        vpnRollbackCompleted = true
        Log.i(LogTag, "rollback=vpn_restored action=${action.logValue}")
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
        const val ActionAuditMark = "com.contentfilter.user.chromedataplane.AUDIT_MARK"
        const val ExtraAuditStateLabel = "chrome_coverage_audit_state_label"
        const val ExtraAuditNewNavigation = "chrome_coverage_audit_new_navigation"
        const val LogTag = "ChromePhotosDataPlane"

        private const val NotificationChannelId = "chrome_photos_data_plane_dev"
        private const val NotificationId = 18_742
        private const val CoverageLogTag = "ChromeCoverageAudit17"
        private const val FingerprintLogLength = 16
        private const val SessionLogLength = 8
        private const val MaxFailureLength = 80
        private const val KeyVpnWasRunningBeforeLab = "vpn_was_running_before_lab"
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

internal enum class ChromePhotosVpnRollbackAction(
    val logValue: String,
) {
    RefreshRoutes("refresh_routes"),
    Stop("stop"),
}

internal fun chromePhotosVpnRollbackAction(vpnWasRunningBeforeLab: Boolean): ChromePhotosVpnRollbackAction =
    if (vpnWasRunningBeforeLab) ChromePhotosVpnRollbackAction.RefreshRoutes else ChromePhotosVpnRollbackAction.Stop
