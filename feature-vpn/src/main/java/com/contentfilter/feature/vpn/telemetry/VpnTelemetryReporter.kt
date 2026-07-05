package com.contentfilter.feature.vpn.telemetry

import com.contentfilter.core.domain.model.PolicyDecision
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.TechnicalDiagnostic
import com.contentfilter.core.domain.repository.DeviceActivationRepository
import com.contentfilter.core.domain.repository.TelemetryRepository
import com.contentfilter.feature.vpn.dns.VpnPacketDiagnostic
import java.util.UUID
import javax.inject.Inject

/**
 * Emits sanitized VPN diagnostics without storing visited domains.
 */
class VpnTelemetryReporter
    @Inject
    constructor(
        private val telemetryRepository: TelemetryRepository,
        private val deviceActivationRepository: DeviceActivationRepository,
    ) {
        suspend fun recordServiceState(state: String) {
            record(type = "vpn-state", message = state)
        }

        suspend fun recordDnsDecision(decision: PolicyDecision) {
            record(
                type = "vpn-dns-decision",
                message = "DNS decision: ${decision.label()}",
            )
        }

        suspend fun recordSearchProtectionDnsDecision(
            domainHost: String,
            decision: PolicyDecision,
            strictWebBlock: Boolean,
            allowRules: Int,
            blockRules: Int,
            totalRules: Int,
        ) {
            val deviceId = deviceActivationRepository.currentActivation()?.deviceId?.safeDeviceId() ?: "none"
            record(
                type = "search-protection",
                message =
                    "layer=vpn-dns deviceId=$deviceId action=dns-decision host=${domainHost.sanitizeHost()} " +
                        "result=${decision.label()} strict=$strictWebBlock allowRules=$allowRules " +
                        "blockRules=$blockRules ruleCount=$totalRules",
            )
        }

        suspend fun recordReconnectApplied(reason: String) {
            val deviceId = deviceActivationRepository.currentActivation()?.deviceId?.safeDeviceId() ?: "none"
            record(
                type = "search-protection",
                message = "layer=reconnect deviceId=$deviceId action=reconnect result=applied reason=${reason.take(MaxMessageLength)}",
            )
        }

        suspend fun recordRecentDnsSearchBlock(host: String) {
            val deviceId = deviceActivationRepository.currentActivation()?.deviceId?.safeDeviceId() ?: "none"
            record(
                type = "search-protection",
                message =
                    "layer=vpn-dns deviceId=$deviceId action=recentDnsSearchBlock " +
                        "host=${host.sanitizeHost()} packageName=unknown result=recorded",
            )
        }

        suspend fun recordSnapshotReceived(
            snapshot: PolicySnapshot,
            strictWebBlock: Boolean,
        ) {
            val deviceId = deviceActivationRepository.currentActivation()?.deviceId?.safeDeviceId() ?: "none"
            val searchBlockRules =
                snapshot.rules.count {
                    it.enabled && it.scope == RuleScope.Domain && it.action == RuleAction.Block && it.target.isSearchRuleTarget()
                }
            record(
                type = "search-protection",
                message =
                    "layer=sync deviceId=$deviceId action=snapshot-received result=applied " +
                        "ruleCount=${snapshot.rules.size} searchBlockRules=$searchBlockRules strict=$strictWebBlock",
            )
        }

        suspend fun recordUnsupportedPacket() {
            record(type = "vpn-unsupported-packet", message = "Unsupported packet ignored by VPN parser.")
        }

        suspend fun recordUnsupportedPacket(diagnostic: VpnPacketDiagnostic) {
            val deviceId = deviceActivationRepository.currentActivation()?.deviceId?.safeDeviceId() ?: "none"
            record(
                type = "vpn-unsupported-packet",
                message =
                    "layer=vpn-packet deviceId=$deviceId action=unsupported result=ignored " +
                        "ipVersion=${diagnostic.ipVersion} protocol=${diagnostic.protocol} " +
                        "srcPort=${diagnostic.sourcePort ?: "none"} dstPort=${diagnostic.destinationPort ?: "none"} " +
                        "looksLikeDns=${diagnostic.looksLikeDns} reason=${diagnostic.reason}",
            )
        }

        suspend fun recordParsedPacket(diagnostic: VpnPacketDiagnostic) {
            val deviceId = deviceActivationRepository.currentActivation()?.deviceId?.safeDeviceId() ?: "none"
            record(
                type = "vpn-packet",
                message =
                    "layer=vpn-packet deviceId=$deviceId action=parsed result=ok " +
                        "ipVersion=${diagnostic.ipVersion} protocol=${diagnostic.protocol} " +
                        "srcPort=${diagnostic.sourcePort ?: "none"} dstPort=${diagnostic.destinationPort ?: "none"} " +
                        "looksLikeDns=${diagnostic.looksLikeDns}",
            )
        }

        suspend fun recordError(message: String) {
            record(type = "vpn-error", message = message.take(MaxMessageLength))
        }

        private suspend fun record(
            type: String,
            message: String,
        ) {
            telemetryRepository.record(
                TechnicalDiagnostic(
                    id = UUID.randomUUID().toString(),
                    type = type,
                    message = message,
                    occurredAtEpochMillis = System.currentTimeMillis(),
                ),
            )
        }

        private fun PolicyDecision.label(): String =
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

        private fun String.safeDeviceId(): String = take(8)

        private fun String.sanitizeHost(): String =
            lowercase()
                .substringBefore("/")
                .substringBefore("?")
                .take(MaxMessageLength)

        private fun String.isSearchRuleTarget(): Boolean {
            val value = lowercase()
            return value.contains("google") ||
                value.contains("bing") ||
                value.contains("yahoo") ||
                value.contains("duckduckgo")
        }

        private companion object {
            const val MaxMessageLength = 120
        }
    }
