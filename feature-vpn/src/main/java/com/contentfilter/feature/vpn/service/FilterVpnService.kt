package com.contentfilter.feature.vpn.service

import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.VpnService
import android.os.ParcelFileDescriptor
import android.util.Log
import com.contentfilter.core.domain.model.ComponentState
import com.contentfilter.core.domain.model.DeviceProtectionAlert
import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.ProtectionAlertType
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.SearchEngineCatalog
import com.contentfilter.core.domain.repository.PushNotificationRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.core.sync.engine.EffectivePolicyApplicationTracker
import com.contentfilter.core.sync.engine.PolicyConsumer
import com.contentfilter.feature.vpn.dns.DnsForwarder
import com.contentfilter.feature.vpn.dns.DnsPacketParser
import com.contentfilter.feature.vpn.dns.DnsParseResult
import com.contentfilter.feature.vpn.dns.DnsQuestion
import com.contentfilter.feature.vpn.dns.DnsResponseFactory
import com.contentfilter.feature.vpn.dns.VpnPacketDiagnostic
import com.contentfilter.feature.vpn.policy.VpnDomainPolicyEvaluator
import com.contentfilter.feature.vpn.policy.VpnPolicySnapshotProvider
import com.contentfilter.feature.vpn.policy.VpnPolicyState
import com.contentfilter.feature.vpn.search.SearchProtectionSignals
import com.contentfilter.feature.vpn.telemetry.VpnTelemetryReporter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    @Inject lateinit var pushNotificationRepository: PushNotificationRepository

    @Inject lateinit var applicationTracker: EffectivePolicyApplicationTracker

    private val parser = DnsPacketParser()
    private val responseFactory = DnsResponseFactory()
    private val dnsForwarder = DnsForwarder { socket -> protect(socket) }
    private var serviceScope: CoroutineScope? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var readerJob: Job? = null
    private var upstreamDnsServers: List<InetAddress> = emptyList()
    private var strictWebBlockMode = false
    private var appliedVpnReconnectKey: String? = null
    private var requestedVpnReconnectKey: String? = null
    private var lastReportedPolicy: Pair<String, Long>? = null
    private val lastUnsupportedPacketDiagnosticAt = mutableMapOf<String, Long>()
    private var lastParsedDnsPacketDiagnosticAt: Long = 0L

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action ?: ActionStart) {
            ActionStop -> stopVpn(StopReasonApp)
            ActionReconnect -> reconnectVpn(intent?.getStringExtra(ExtraReconnectKey))
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
                try {
                    snapshotProvider.refresh()
                    snapshotProvider.start(scope)
                    strictWebBlockMode = snapshotProvider.current().strictWebBlockEnabled
                    upstreamDnsServers = currentDnsServers()
                    Log.i(
                        LogTag,
                        "VPN active upstream=${upstreamDnsServers.safeAddresses()} rules=${snapshotProvider.current().snapshot.rules.size} strictWebBlock=$strictWebBlockMode",
                    )
                    vpnInterface = establishVpn()
                    val appliedState = snapshotProvider.current()
                    appliedVpnReconnectKey = appliedState.vpnReconnectKey
                    requestedVpnReconnectKey = null
                    reportVpnPolicyApplied(appliedState, source = "tunnel-established")
                    observeVpnReconnectPolicy(scope)
                    VpnController.markStarted(this@FilterVpnService)
                    systemStatusRepository.updateVpnState(ComponentState.Enabled)
                    telemetryReporter.recordServiceState("VPN started.")
                    readPackets(requireNotNull(vpnInterface))
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Throwable) {
                    VpnController.markStopped(this@FilterVpnService, StopReasonFailed)
                    updateVpnState(ComponentState.Warning)
                    telemetryReporter.recordError("VPN failed: ${exception.javaClass.simpleName}")
                    cleanup()
                }
            }
    }

    private fun reconnectVpn(expectedKey: String?) {
        val currentState = snapshotProvider.current()
        if (expectedKey != null && expectedKey != currentState.vpnReconnectKey) {
            Log.i(LogTag, "Ignoring stale VPN reconnect request")
            return
        }
        if (vpnInterface != null && appliedVpnReconnectKey == currentState.vpnReconnectKey) {
            requestedVpnReconnectKey = null
            reportVpnPolicyApplied(currentState, source = "tunnel-already-current")
            return
        }
        cleanup()
        startVpn()
    }

    private fun stopVpn(reason: String) {
        VpnController.markStopped(this, reason)
        updateVpnState(ComponentState.Disabled)
        recordServiceState("${DeviceProtectionAlert.WebDisabled} reason=$reason")
        reportProtectionAlert(ProtectionAlertType.WebDisabled)
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

    private fun reportProtectionAlert(type: ProtectionAlertType) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            pushNotificationRepository.reportProtectionAlert(type)
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
            if (strictWebBlock) {
                upstreamServers.forEach { server -> addDnsServer(server) }
                addRoute(AllIpv4Route, AllTrafficPrefixLength)
                runCatching { addRoute(AllIpv6Route, AllTrafficPrefixLength) }
            } else {
                upstreamServers
                    .filterIsInstance<Inet4Address>()
                    .forEach { server ->
                        addDnsServer(server)
                        server.hostAddress?.let { address ->
                            addRoute(address, SingleIpv4HostPrefixLength)
                        }
                    }
            }
        }

    private fun observeVpnReconnectPolicy(scope: CoroutineScope) {
        scope.launch {
            snapshotProvider
                .observe()
                .collect { state ->
                    val nextKey = state.vpnReconnectKey
                    if (nextKey == appliedVpnReconnectKey && vpnInterface != null) {
                        requestedVpnReconnectKey = null
                        reportVpnPolicyApplied(state, source = "snapshot-current")
                    } else if (requestedVpnReconnectKey != nextKey) {
                        requestedVpnReconnectKey = nextKey
                        Log.i(
                            LogTag,
                            "VPN domain policy changed; reconnecting tunnel " +
                                "revision=${state.snapshot.version} strict=${state.strictWebBlockEnabled}",
                        )
                        telemetryReporter.recordReconnectApplied("vpnReconnectKey-changed")
                        requestReconnectVpn(nextKey)
                    }
                }
        }
    }

    private fun requestReconnectVpn(expectedKey: String) {
        startService(
            Intent(this, FilterVpnService::class.java).apply {
                action = ActionReconnect
                putExtra(ExtraReconnectKey, expectedKey)
            },
        )
    }

    private fun reportVpnPolicyApplied(
        state: VpnPolicyState,
        source: String,
    ) {
        val appliedPolicy = state.snapshot.id to state.snapshot.version
        if (lastReportedPolicy == appliedPolicy) return
        lastReportedPolicy = appliedPolicy
        Log.i(
            LogTag,
            "VPN effective policy applied source=$source policyId=${state.snapshot.id.take(8)} " +
                "revision=${state.snapshot.version} strict=${state.strictWebBlockEnabled}",
        )
        applicationTracker.report(PolicyConsumer.Vpn, state.snapshot.id, state.snapshot.version)
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
        val question =
            when (val parsed = parser.parse(packet, length)) {
                is DnsParseResult.Parsed -> {
                    maybeRecordParsedPacket(parser.inspect(packet, length))
                    parsed.question
                }
                is DnsParseResult.Unsupported -> {
                    maybeRecordUnsupportedPacket(parsed.diagnostic)
                    return
                }
            }
        val domain = question.domain.normalizedDomain()
        val state = snapshotProvider.current()
        val searchEngine = SearchEngineCatalog.engineForDomain(domain)
        when (val decision = policyEvaluator.evaluate(domain, state.snapshot, state.health)) {
            is PolicyDecision.Allow -> {
                searchEngine?.let { engine ->
                    SearchProtectionSignals.recordSearchEngine(engine.id, state.snapshot.version)
                }
                logSearchProtectionDnsLayer(domain, decision, state)
                Log.i(
                    LogTag,
                    "DNS decision=allow domain=$domain snapshotVersion=${state.snapshot.version} rules=${state.snapshot.rules.size} limits=${state.snapshot.dailyLimits.size} reason=${decision.reasonLabel()} upstream=${upstreamDnsServers.safeAddresses()}",
                )
                telemetryReporter.recordDnsDecision(decision)
                val safeSearchTarget =
                    decision.safeSearchRequired.takeIf { it }
                        ?.let { SearchEngineCatalog.safeSearchDnsTarget(domain) }
                if (safeSearchTarget != null) {
                    output.write(responseFactory.cnamePacket(question, safeSearchTarget))
                    reportSafeSearchApplied(
                        engineId = searchEngine?.id ?: "unknown",
                        policyRevision = state.snapshot.version,
                    )
                } else {
                    forwardDns(question, output)
                }
            }
            is PolicyDecision.GrantExtraTime -> {
                logSearchProtectionDnsLayer(domain, decision, state)
                Log.i(
                    LogTag,
                    "DNS decision=grant domain=$domain snapshotVersion=${state.snapshot.version} rules=${state.snapshot.rules.size} limits=${state.snapshot.dailyLimits.size} reason=${decision.reasonLabel()}",
                )
                telemetryReporter.recordDnsDecision(decision)
                forwardDns(question, output)
            }
            is PolicyDecision.Block,
            is PolicyDecision.RequestAuthorization,
            -> {
                logSearchProtectionDnsLayer(domain, decision, state)
                Log.i(
                    LogTag,
                    "DNS decision=block domain=$domain snapshotVersion=${state.snapshot.version} rules=${state.snapshot.rules.size} limits=${state.snapshot.dailyLimits.size} reason=${decision.reasonLabel()}",
                )
                telemetryReporter.recordDnsDecision(decision)
                output.write(responseFactory.nxdomainPacket(question))
            }
            is PolicyDecision.HealthWarning,
            is PolicyDecision.RequireActivation,
            is PolicyDecision.RequireUpdate,
            -> {
                logSearchProtectionDnsLayer(domain, decision, state)
                telemetryReporter.recordDnsDecision(decision)
                forwardDns(question, output)
            }
            is PolicyDecision.Warn -> {
                logSearchProtectionDnsLayer(domain, decision, state)
                telemetryReporter.recordDnsDecision(decision)
                forwardDns(question, output)
            }
        }
    }

    private suspend fun logSearchProtectionDnsLayer(
        domain: String,
        decision: PolicyDecision,
        state: VpnPolicyState,
    ) {
        if (!domain.isSearchProtectionDomain()) return
        val blockRules =
            state.snapshot.rules.count {
                it.enabled &&
                    it.scope == RuleScope.Domain &&
                    it.action == RuleAction.Block &&
                    domain.matchesRuleTarget(it.target.normalizedDomain())
            }
        val allowRules =
            state.snapshot.rules.count {
                it.enabled &&
                    it.scope == RuleScope.Domain &&
                    it.action == RuleAction.Allow &&
                    domain.matchesRuleTarget(it.target.normalizedDomain())
            }
        Log.i(
            LogTag,
            "Search protection layer=vpn-dns domain=$domain decision=${decision.searchProtectionLabel()} snapshotVersion=${state.snapshot.version} strict=${state.strictWebBlockEnabled} allowRules=$allowRules blockRules=$blockRules totalRules=${state.snapshot.rules.size} reason=${decision.reasonLabel()}",
        )
        if (decision is PolicyDecision.Block) {
            telemetryReporter.recordRecentDnsSearchBlock(domain)
        }
        telemetryReporter.recordSearchProtectionDnsDecision(
            domainHost = domain,
            decision = decision,
            strictWebBlock = state.strictWebBlockEnabled,
            allowRules = allowRules,
            blockRules = blockRules,
            totalRules = state.snapshot.rules.size,
            snapshotVersion = state.snapshot.version,
            reason = decision.reasonLabel(),
        )
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

    private suspend fun reportSafeSearchApplied(
        engineId: String,
        policyRevision: Long,
    ) {
        Log.i(
            LogTag,
            "SafeSearch DNS mapping applied engine=$engineId policyRevision=$policyRevision",
        )
        telemetryReporter.recordServiceState(
            "SafeSearch DNS mapping applied engine=$engineId policyRevision=$policyRevision.",
        )
    }

    private suspend fun maybeRecordParsedPacket(diagnostic: VpnPacketDiagnostic) {
        val now = System.currentTimeMillis()
        if (now - lastParsedDnsPacketDiagnosticAt < PacketDiagnosticCooldownMillis) return
        lastParsedDnsPacketDiagnosticAt = now
        telemetryReporter.recordParsedPacket(diagnostic)
    }

    private suspend fun maybeRecordUnsupportedPacket(diagnostic: VpnPacketDiagnostic) {
        val key =
            "${diagnostic.reason}:${diagnostic.ipVersion}:${diagnostic.protocol}:" +
                "${diagnostic.sourcePort ?: "none"}:${diagnostic.destinationPort ?: "none"}"
        val now = System.currentTimeMillis()
        if (now - (lastUnsupportedPacketDiagnosticAt[key] ?: 0L) < PacketDiagnosticCooldownMillis) return
        lastUnsupportedPacketDiagnosticAt[key] = now
        telemetryReporter.recordUnsupportedPacket(diagnostic)
    }

    private fun cleanup() {
        readerJob?.cancel()
        readerJob = null
        snapshotProvider.stop()
        vpnInterface?.close()
        vpnInterface = null
        upstreamDnsServers = emptyList()
        strictWebBlockMode = false
        appliedVpnReconnectKey = null
        requestedVpnReconnectKey = null
        lastReportedPolicy = null
        serviceScope?.cancel()
        serviceScope = null
    }

    companion object {
        const val ActionStart = "com.contentfilter.feature.vpn.START"
        const val ActionStop = "com.contentfilter.feature.vpn.STOP"
        const val ActionReconnect = "com.contentfilter.feature.vpn.RECONNECT"
        private const val ExtraReconnectKey = "vpn_reconnect_key"

        private const val DefaultMtu = 1500
        private const val LocalVpnAddress = "10.8.0.2"
        private const val LocalVpnPrefixLength = 32
        private const val NoBytesRead = -1
        private const val PacketBufferSize = 32 * 1024
        private const val AllIpv4Route = "0.0.0.0"
        private const val AllIpv6Route = "::"
        private const val AllTrafficPrefixLength = 0
        private const val SingleIpv4HostPrefixLength = 32
        private const val PacketDiagnosticCooldownMillis = 5_000L
        private const val StopReasonAndroid = "android_stopped_vpn"
        private const val StopReasonApp = "app_stopped_vpn"
        private const val StopReasonDevRescue = "dev_protection_disabled"
        private const val StopReasonFailed = "vpn_failed"
        private const val StopReasonRevoked = "system_revoked_vpn"
        private const val LogTag = "FilterVpnService"

        private val BrowserPackageNames =
            listOf(
                "com.android.chrome",
                "com.google.android.googlequicksearchbox",
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

        private fun String.matchesRuleTarget(target: String): Boolean =
            target == "*" || this == target || endsWith(".$target")

        private fun String.isSearchProtectionDomain(): Boolean {
            val domain = normalizedDomain()
            return SearchEngineCatalog.searchEngineDomains.any { domain.matchesRuleTarget(it) } ||
                SearchEngineCatalog.searchSupportDomains.any { domain.matchesRuleTarget(it) } ||
                SearchEngineCatalog.secureDnsDomains.any { domain.matchesRuleTarget(it) }
        }

        private fun PolicyDecision.searchProtectionLabel(): String =
            when (this) {
                is PolicyDecision.Allow -> if (safeSearchRequired) "AllowSafeSearch" else "Allow"
                is PolicyDecision.Block -> "Block"
                is PolicyDecision.GrantExtraTime -> "GrantExtraTime"
                is PolicyDecision.HealthWarning -> "HealthWarning"
                is PolicyDecision.RequestAuthorization -> "RequestAuthorization"
                is PolicyDecision.RequireActivation -> "RequireActivation"
                is PolicyDecision.RequireUpdate -> "RequireUpdate"
                is PolicyDecision.Warn -> "Warn"
            }

        private fun PolicyDecision.reasonLabel(): String =
            when (this) {
                is PolicyDecision.Allow -> if (safeSearchRequired) "safe-search-required" else "no-blocking-rule"
                is PolicyDecision.Block -> reason.take(120)
                is PolicyDecision.GrantExtraTime -> "extra-time"
                is PolicyDecision.HealthWarning -> message.take(120)
                is PolicyDecision.RequestAuthorization -> "request-authorization"
                is PolicyDecision.RequireActivation -> reason.take(120)
                is PolicyDecision.RequireUpdate -> reason.take(120)
                is PolicyDecision.Warn -> message.take(120)
            }

        private fun List<InetAddress>.safeAddresses(): String =
            joinToString(",") { it.hostAddress.orEmpty() }.ifBlank { "none" }
    }
}
