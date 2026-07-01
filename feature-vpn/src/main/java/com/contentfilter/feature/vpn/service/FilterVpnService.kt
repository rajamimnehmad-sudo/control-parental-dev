package com.contentfilter.feature.vpn.service

import android.content.Intent
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.VpnService
import android.os.ParcelFileDescriptor
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
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.Inet4Address
import java.net.InetAddress
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action ?: ActionStart) {
            ActionStop -> stopVpn()
            ActionReconnect -> reconnectVpn()
            else -> startVpn()
        }
        return START_STICKY
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    private fun startVpn() {
        if (readerJob?.isActive == true) return
        if (DevProtectionMode.isProtectionDisabled(this)) {
            serviceScope?.launch { systemStatusRepository.updateVpnState(ComponentState.Disabled) }
            stopSelf()
            return
        }
        startForeground(
            VpnNotificationFactory.NotificationId,
            VpnNotificationFactory(this).create(),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        serviceScope = scope
        readerJob = scope.launch {
            runCatching {
                snapshotProvider.refresh()
                snapshotProvider.start(scope)
                upstreamDnsServers = currentDnsServers()
                vpnInterface = establishVpn()
                systemStatusRepository.updateVpnState(ComponentState.Enabled)
                telemetryReporter.recordServiceState("VPN started.")
                readPackets(requireNotNull(vpnInterface))
            }.onFailure {
                systemStatusRepository.updateVpnState(ComponentState.Warning)
                telemetryReporter.recordError("VPN failed: ${it.javaClass.simpleName}")
                cleanup()
            }
        }
    }

    private fun reconnectVpn() {
        cleanup()
        startVpn()
    }

    private fun stopVpn() {
        serviceScope?.launch {
            systemStatusRepository.updateVpnState(ComponentState.Disabled)
            telemetryReporter.recordServiceState("VPN stopped.")
        }
        cleanup()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun establishVpn(): ParcelFileDescriptor? =
        Builder()
            .setSession("Content Filter VPN")
            .addAddress(LocalVpnAddress, LocalVpnPrefixLength)
            .applyDnsRoutes(upstreamDnsServers)
            .excludeAdminApps()
            .setMtu(DefaultMtu)
            .setBlocking(true)
            .establish()

    private fun Builder.applyDnsRoutes(upstreamServers: List<InetAddress>): Builder =
        apply {
            upstreamServers
                .filterIsInstance<Inet4Address>()
                .forEach { server ->
                    addDnsServer(server)
                    server.hostAddress?.let { address ->
                        addRoute(address, SingleIpv4HostPrefixLength)
                    }
                }
        }

    private fun Builder.excludeAdminApps(): Builder =
        apply {
            AdminPackageNames.forEach { packageName ->
                runCatching { addDisallowedApplication(packageName) }
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
            telemetryReporter.recordUnsupportedPacket()
            return
        }
        val state = snapshotProvider.current()
        when (val decision = policyEvaluator.evaluate(question.domain, state.snapshot, state.health)) {
            is PolicyDecision.Allow -> {
                telemetryReporter.recordDnsDecision(decision)
                if (decision.safeSearchRequired) {
                    reportSafeSearchExtensionPoint()
                }
                forwardDns(question, output)
            }
            is PolicyDecision.GrantExtraTime -> {
                telemetryReporter.recordDnsDecision(decision)
                forwardDns(question, output)
            }
            is PolicyDecision.Block -> {
                telemetryReporter.recordDnsDecision(decision)
                output.write(responseFactory.nxdomainPacket(question))
            }
            is PolicyDecision.HealthWarning,
            is PolicyDecision.RequireActivation,
            is PolicyDecision.RequireUpdate -> {
                telemetryReporter.recordDnsDecision(decision)
                forwardDns(question, output)
            }
            is PolicyDecision.RequestAuthorization,
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
            dnsForwarder.forward(question.queryPayload, upstreamDnsServers)?.let { responsePayload ->
                output.write(responseFactory.responsePacket(question, responsePayload))
            }
        }.onFailure { telemetryReporter.recordError("DNS forward failed: ${it.javaClass.simpleName}") }
    }

    private suspend fun reportSafeSearchExtensionPoint() {
        telemetryReporter.recordServiceState("SafeSearch extension point reached.")
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

        private const val DefaultMtu = 1500
        private const val LocalVpnAddress = "10.8.0.2"
        private const val LocalVpnPrefixLength = 32
        private const val NoBytesRead = -1
        private const val PacketBufferSize = 32 * 1024
        private const val SingleIpv4HostPrefixLength = 32
        private val AdminPackageNames = listOf(
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
    }
}
