package com.contentfilter.feature.vpn.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.feature.vpn.dns.DnsForwarder
import com.contentfilter.feature.vpn.dns.DnsPacketParser
import com.contentfilter.feature.vpn.dns.DnsQuestion
import com.contentfilter.feature.vpn.dns.DnsResponseFactory
import com.contentfilter.feature.vpn.policy.VpnDomainPolicyEvaluator
import com.contentfilter.feature.vpn.policy.VpnPolicySnapshotProvider
import com.contentfilter.feature.vpn.telemetry.VpnTelemetryReporter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject

/**
 * Local VPN entry point. Business decisions are delegated to PolicyEngine.
 */
@AndroidEntryPoint
class FilterVpnService : VpnService() {
    @Inject lateinit var snapshotProvider: VpnPolicySnapshotProvider

    @Inject lateinit var policyEvaluator: VpnDomainPolicyEvaluator

    @Inject lateinit var systemStatusRepository: SystemStatusRepository

    @Inject lateinit var telemetryReporter: VpnTelemetryReporter

    private val parser = DnsPacketParser()
    private val responseFactory = DnsResponseFactory()
    private val dnsForwarder = DnsForwarder { socket -> protect(socket) }
    private var serviceScope: CoroutineScope? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var readerJob: Job? = null
    private var upstreamDnsServers: List<InetAddress> = emptyList()
    private var strictWebBlockMode = false
    private val lastBlockedNotificationAt = mutableMapOf<String, Long>()

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action ?: ActionStart) {
            ActionStop -> stopVpn(StopReasonApp)
            ActionReconnect -> reconnectVpn()
            else -> startVpn()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        stopVpn(StopReasonRevoked)
        super.onRevoke()
    }

    override fun onDestroy() {
        if (VpnController.isRunning(this)) {
            stopVpn(StopReasonAndroid)
        }
        super.onDestroy()
    }

    private fun startVpn() {
        if (readerJob?.isActive == true) return
        if (DevProtectionMode.isProtectionDisabled(this)) {
            updateVpnState(ComponentState.Disabled)
            VpnController.markStopped(this, StopReasonDevRescue)
            stopSelf()
            return
        }
        startForeground(
            VpnNotificationFactory.NotificationId,
            VpnNotificationFactory(this).create(),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serviceScope = scope
        readerJob =
            scope.launch {
                runCatching {
                    snapshotProvider.refresh()
                    snapshotProvider.start(scope)
                    strictWebBlockMode = snapshotProvider.current().strictWebBlockEnabled
                    upstreamDnsServers = currentDnsServers()
                    Log.i(
                        LogTag,
                        "VPN active upstream=${upstreamDnsServers.safeAddresses()} rules=${snapshotProvider.current().snapshot.rules.size} strictWebBlock=$strictWebBlockMode",
                    )
                    vpnInterface = establishVpn()
                    observeStrictWebBlockMode(scope, strictWebBlockMode)
                    VpnController.markStarted(this@FilterVpnService)
                    systemStatusRepository.updateVpnState(ComponentState.Enabled)
                    telemetryReporter.recordServiceState("VPN started.")
                    readPackets(requireNotNull(vpnInterface))
                }.onFailure {
                    VpnController.markStopped(this@FilterVpnService, StopReasonFailed)
                    updateVpnState(ComponentState.Warning)
                    telemetryReporter.recordError("VPN failed: ${it.javaClass.simpleName}")
                    cleanup()
                }
            }
    }

    private fun reconnectVpn() {
        cleanup()
        startVpn()
    }

    private fun stopVpn(reason: String) {
        VpnController.markStopped(this, reason)
        updateVpnState(ComponentState.Disabled)
        recordServiceState("VPN stopped: $reason")
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateVpnState(state: ComponentState) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            systemStatusRepository.updateVpnState(state)
        }
    }

    private fun recordServiceState(message: String) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            telemetryReporter.recordServiceState(message)
        }
    }

    private fun establishVpn(): ParcelFileDescriptor? =
        Builder()
            .setSession("Content Filter VPN")
            .addAddress(LocalVpnAddress, LocalVpnPrefixLength)
            .applyTrafficRoutes(upstreamDnsServers, strictWebBlockMode)
            .allowBrowserApps()
            .setMtu(DefaultMtu)
            .setBlocking(true)
            .establish()

    private fun Builder.applyTrafficRoutes(
        upstreamServers: List<InetAddress>,
        strictWebBlock: Boolean,
    ): Builder =
        apply {
            upstreamServers.forEach { server -> addDnsServer(server) }
            if (strictWebBlock) {
                addRoute(AllIpv4Route, AllTrafficPrefixLength)
                runCatching { addRoute(AllIpv6Route, AllTrafficPrefixLength) }
            } else {
                upstreamServers
                    .filterIsInstance<Inet4Address>()
                    .forEach { server ->
                        server.hostAddress?.let { address ->
                            addRoute(address, SingleIpv4HostPrefixLength)
                        }
                    }
            }
        }

    private fun observeStrictWebBlockMode(
        scope: CoroutineScope,
        initialMode: Boolean,
    ) {
        scope.launch {
            var currentMode = initialMode
            snapshotProvider
                .observe()
                .map { it.strictWebBlockEnabled }
                .distinctUntilChanged()
                .collect { nextMode ->
                    if (nextMode != currentMode) {
                        Log.i(LogTag, "VPN strict web block changed: $currentMode -> $nextMode")
                        reconnectVpn()
                    }
                    currentMode = nextMode
                }
        }
    }

    private fun Builder.allowBrowserApps(): Builder =
        apply {
            var allowedCount = 0
            BrowserPackageNames.forEach { packageName ->
                runCatching { addAllowedApplication(packageName) }
                    .onSuccess { allowedCount++ }
            }
            if (allowedCount == 0) {
                AdminPackageNames.forEach { packageName ->
                    runCatching { addDisallowedApplication(packageName) }
                }
            }
        }

    private fun currentDnsServers(): List<InetAddress> {
        val connectivityManager = getSystemService(ConnectivityManager::class.java)
        val linkProperties: LinkProperties? = connectivityManager?.getLinkProperties(connectivityManager.activeNetwork)
        return linkProperties?.dnsServers.orEmpty()
    }

    private suspend fun readPackets(interfaceDescriptor: ParcelFileDescriptor) =
        withContext(Dispatchers.IO) {
            val input = FileInputStream(interfaceDescriptor.fileDescriptor)
            val output = FileOutputStream(interfaceDescriptor.fileDescriptor)
            val buffer = ByteArray(PacketBufferSize)
            try {
                while (isActive) {
                    val length = runCatching { input.read(buffer) }.getOrDefault(NoBytesRead)
                    if (length > 0) {
                        handlePacket(buffer, length, output)
                    }
                }
            } finally {
                input.close()
                output.close()
            }
        }

    private suspend fun handlePacket(
        packet: ByteArray,
        length: Int,
        output: FileOutputStream,
    ) {
        if (DevProtectionMode.isProtectionDisabled(this)) return
        val question = parser.parseQuery(packet, length)
        if (question == null) {
            if (!strictWebBlockMode) {
                telemetryReporter.recordUnsupportedPacket()
            }
            return
        }
        val domain = question.domain.normalizedDomain()
        val state = snapshotProvider.current()
        when (val decision = policyEvaluator.evaluate(domain, state.snapshot, state.health)) {
            is PolicyDecision.Allow -> {
                Log.i(
                    LogTag,
                    "DNS decision=allow domain=$domain rules=${state.snapshot.rules.size} limits=${state.snapshot.dailyLimits.size} upstream=${upstreamDnsServers.safeAddresses()}",
                )
                telemetryReporter.recordDnsDecision(decision)
                if (decision.safeSearchRequired) {
                    reportSafeSearchExtensionPoint()
                }
                forwardDns(question, output)
            }
            is PolicyDecision.GrantExtraTime -> {
                Log.i(
                    LogTag,
                    "DNS decision=grant domain=$domain rules=${state.snapshot.rules.size} limits=${state.snapshot.dailyLimits.size}",
                )
                telemetryReporter.recordDnsDecision(decision)
                forwardDns(question, output)
            }
            is PolicyDecision.Block,
            is PolicyDecision.RequestAuthorization,
            -> {
                Log.i(
                    LogTag,
                    "DNS decision=block domain=$domain rules=${state.snapshot.rules.size} limits=${state.snapshot.dailyLimits.size}",
                )
                telemetryReporter.recordDnsDecision(decision)
                notifyBlockedDomain(domain)
                output.write(responseFactory.nxdomainPacket(question))
            }
            is PolicyDecision.HealthWarning,
            is PolicyDecision.RequireActivation,
            is PolicyDecision.RequireUpdate,
            -> {
                telemetryReporter.recordDnsDecision(decision)
                forwardDns(question, output)
            }
            is PolicyDecision.Warn -> {
                telemetryReporter.recordDnsDecision(decision)
                forwardDns(question, output)
            }
        }
    }

    private suspend fun forwardDns(
        question: DnsQuestion,
        output: FileOutputStream,
    ) {
        runCatching {
            val responsePayload = dnsForwarder.forward(question.queryPayload, upstreamDnsServers)
            if (responsePayload != null) {
                output.write(responseFactory.responsePacket(question, responsePayload))
            } else {
                Log.w(
                    LogTag,
                    "DNS forward failed domain=${question.domain.normalizedDomain()} upstream=${upstreamDnsServers.safeAddresses()}",
                )
                output.write(responseFactory.servfailPacket(question))
            }
        }.onFailure { telemetryReporter.recordError("DNS forward failed: ${it.javaClass.simpleName}") }
    }

    private suspend fun reportSafeSearchExtensionPoint() {
        telemetryReporter.recordServiceState("SafeSearch extension point reached.")
    }

    private fun notifyBlockedDomain(domain: String) {
        val normalized = domain.notificationDomain() ?: return
        rememberBlockedDomain(normalized)
        val now = System.currentTimeMillis()
        if (now - (lastBlockedNotificationAt[normalized] ?: 0L) < BlockNotificationCooldownMillis) return
        lastBlockedNotificationAt[normalized] = now
        ensureBlockedNotificationChannel()
        val intent =
            Intent().apply {
                setClassName(packageName, UserMainActivityClassName)
                action = OpenInternetAction
                putExtra(ExtraBlockedDomain, normalized)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                normalized.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        val builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Notification.Builder(this, BlockedChannelId)
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }
        val notification =
            builder
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setContentTitle("Sitio bloqueado")
                .setContentText("$normalized - tocar para pedir permiso")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .build()
        getSystemService(NotificationManager::class.java)
            ?.notify(BlockedNotificationId, notification)
    }

    private fun String.notificationDomain(): String? {
        val labels = normalizedDomain().split(".").filter { it.isNotBlank() }
        if (labels.size < 2) return null
        if (labels.any { it in NoisyDomainLabels }) return null
        val baseDomain = labels.takeLast(2).joinToString(".")
        val isDirectNavigation = labels.size == 2 || (labels.size == 3 && labels.first() == "www")
        return baseDomain.takeIf { isDirectNavigation && it !in NoisyBaseDomains }
    }

    private fun rememberBlockedDomain(domain: String) {
        val prefs = getSharedPreferences(BlockedPrefsName, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val current =
            prefs.getString(BlockedDomainsKey, "")
                .orEmpty()
                .split("|")
                .mapNotNull { it.toBlockedDomainEntryOrNull() }
                .filter { it.domain != domain }
        prefs.edit()
            .putString(
                BlockedDomainsKey,
                (listOf(BlockedDomainEntry(domain, now)) + current)
                    .take(MaxRecentBlockedDomains)
                    .joinToString("|") { "${it.domain},${it.blockedAtEpochMillis}" },
            )
            .apply()
    }

    private fun String.toBlockedDomainEntryOrNull(): BlockedDomainEntry? {
        if (isBlank()) return null
        val domain = substringBefore(",").normalizedDomain()
        val timestamp =
            substringAfter(",", missingDelimiterValue = "")
                .toLongOrNull()
                ?: System.currentTimeMillis()
        return domain.takeIf { it.isNotBlank() }?.let { BlockedDomainEntry(it, timestamp) }
    }

    private fun ensureBlockedNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel =
            NotificationChannel(
                BlockedChannelId,
                "Sitios bloqueados",
                NotificationManager.IMPORTANCE_DEFAULT,
            )
        manager.createNotificationChannel(channel)
    }

    private fun cleanup() {
        readerJob?.cancel()
        readerJob = null
        snapshotProvider.stop()
        vpnInterface?.close()
        vpnInterface = null
        upstreamDnsServers = emptyList()
        serviceScope?.cancel()
        serviceScope = null
    }

    companion object {
        const val ActionStart = "com.contentfilter.feature.vpn.START"
        const val ActionStop = "com.contentfilter.feature.vpn.STOP"
        const val ActionReconnect = "com.contentfilter.feature.vpn.RECONNECT"
        const val OpenInternetAction = "com.contentfilter.user.OPEN_INTERNET"
        const val ExtraBlockedDomain = "blocked_domain"
        const val UserMainActivityClassName = "com.contentfilter.user.MainActivity"

        private const val DefaultMtu = 1500
        private const val LocalVpnAddress = "10.8.0.2"
        private const val LocalVpnPrefixLength = 32
        private const val NoBytesRead = -1
        private const val PacketBufferSize = 32 * 1024
        private const val AllIpv4Route = "0.0.0.0"
        private const val AllIpv6Route = "::"
        private const val AllTrafficPrefixLength = 0
        private const val SingleIpv4HostPrefixLength = 32
        private const val BlockNotificationCooldownMillis = 60_000L
        private const val BlockedChannelId = "content_filter_blocked_sites_v2"
        private const val BlockedNotificationId = 3_000
        private const val BlockedPrefsName = "blocked_domains"
        private const val BlockedDomainsKey = "domains"
        private const val MaxRecentBlockedDomains = 20
        private val NoisyDomainLabels =
            setOf(
                "api",
                "apis",
                "auth",
                "collect",
                "crash",
                "discover",
                "events",
                "firebase",
                "gms",
                "graph",
                "logs",
                "metrics",
                "optimizationguide-pa",
                "telemetry",
                "us-auth2",
                "vas",
            )
        private val NoisyBaseDomains =
            setOf(
                "googleapis.com",
                "gstatic.com",
                "samsungapps.com",
                "samsungosp.com",
                "ureca-lab.com",
            )
        private const val StopReasonAndroid = "android_stopped_vpn"
        private const val StopReasonApp = "app_stopped_vpn"
        private const val StopReasonDevRescue = "dev_protection_disabled"
        private const val StopReasonFailed = "vpn_failed"
        private const val StopReasonRevoked = "system_revoked_vpn"
        private const val LogTag = "FilterVpnService"

        private data class BlockedDomainEntry(
            val domain: String,
            val blockedAtEpochMillis: Long,
        )

        private val BrowserPackageNames =
            listOf(
                "com.android.chrome",
                "com.sec.android.app.sbrowser",
                "org.mozilla.firefox",
                "org.mozilla.firefox_beta",
                "com.microsoft.emmx",
                "com.brave.browser",
                "com.opera.browser",
                "com.opera.mini.native",
                "com.duckduckgo.mobile.android",
                "com.vivaldi.browser",
                "com.kiwibrowser.browser",
                "com.UCMobile.intl",
                "mark.via.gp",
            )
        private val AdminPackageNames =
            listOf(
                "com.android.contacts",
                "com.android.dialer",
                "com.android.packageinstaller",
                "com.android.permissioncontroller",
                "com.android.phone",
                "com.android.providers.downloads",
                "com.android.settings",
                "com.contentfilter.admin",
                "com.contentfilter.admin.dev",
                "com.contentfilter.admin.beta",
                "com.contentfilter.user",
                "com.contentfilter.user.dev",
                "com.contentfilter.user.beta",
                "com.google.android.contacts",
                "com.google.android.dialer",
                "com.google.android.gms",
                "com.google.android.gsf",
                "com.google.android.packageinstaller",
                "com.google.android.permissioncontroller",
                "com.google.android.setupwizard",
                "com.android.vending",
            )

        private fun String.normalizedDomain(): String =
            trim()
                .lowercase()
                .removeSuffix(".")
                .removePrefix("www.")

        private fun List<InetAddress>.safeAddresses(): String =
            joinToString(",") { it.hostAddress.orEmpty() }.ifBlank { "none" }
    }
}
