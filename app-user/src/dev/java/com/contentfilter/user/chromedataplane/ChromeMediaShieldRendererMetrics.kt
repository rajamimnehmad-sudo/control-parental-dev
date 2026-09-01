package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldSelfReadyIdentity
import java.nio.charset.StandardCharsets
import java.util.LinkedHashSet

internal data class ChromeMediaShieldRendererMetricsSnapshot(
    val reports: Long = 0,
    val rejected: Long = 0,
    val values: List<Long> = List(FieldCount) { 0L },
) {
    operator fun get(index: Int): Long = values.getOrElse(index) { 0L }

    companion object {
        const val FieldCount = 38
    }
}

internal data class ChromeMediaShieldRendererMetricsReport(
    val token: String,
    val identity: ChromeMediaShieldSelfReadyIdentity,
    val values: List<Long>,
)

/** Bounded, capability-authenticated DEV telemetry. It never affects shielding or release. */
internal class ChromeMediaShieldRendererMetrics {
    private val recordedTokenDigests = LinkedHashSet<String>()
    private val totals = MutableList(ChromeMediaShieldRendererMetricsSnapshot.FieldCount) { 0L }
    private var reports = 0L
    private var rejected = 0L

    @Synchronized
    fun record(report: ChromeMediaShieldRendererMetricsReport): Boolean {
        if (
            report.values.size != ChromeMediaShieldRendererMetricsSnapshot.FieldCount ||
            !ChromeMediaShieldDocumentAuthorityRegistry.validatesClaimedSelfReady(report.token, report.identity)
        ) {
            rejected += 1L
            return false
        }
        val digest = ChromeMediaShieldDocumentAuthorityRegistry.digestReadyToken(report.token)
        if (!recordedTokenDigests.add(digest)) {
            rejected += 1L
            return false
        }
        while (recordedTokenDigests.size > MaximumDocuments) {
            recordedTokenDigests.remove(recordedTokenDigests.first())
        }
        report.values.forEachIndexed { index, value ->
            totals[index] =
                if (index in MaximumFieldIndexes) {
                    maxOf(totals[index], value)
                } else {
                    saturatedAdd(totals[index], value)
                }
        }
        reports += 1L
        return true
    }

    @Synchronized
    fun snapshot(): ChromeMediaShieldRendererMetricsSnapshot =
        ChromeMediaShieldRendererMetricsSnapshot(reports, rejected, totals.toList())

    companion object {
        fun parse(bytes: ByteArray): ChromeMediaShieldRendererMetricsReport? {
            if (bytes.isEmpty() || bytes.size > MaximumBodyBytes || bytes.any { it.toInt() !in 0x20..0x7e }) return null
            val parts = bytes.toString(StandardCharsets.US_ASCII).split('|')
            if (parts.size != 10 || parts[0] != "v1" || parts[1] != "RENDERER_METRICS") return null
            val token = parts[2]
            if (token.length !in 22..64 || token.any { !it.isLetterOrDigit() && it != '-' && it != '_' }) return null
            val identity =
                ChromeMediaShieldSelfReadyIdentity(
                    protectionSessionId = parts[3].takeIf(String::isNotBlank) ?: return null,
                    policyEpoch = parts[4].toLongOrNull()?.takeIf { it > 0L } ?: return null,
                    navigationSequence = parts[5].toLongOrNull()?.takeIf { it >= 0L } ?: return null,
                    documentSequence = parts[6].toLongOrNull()?.takeIf { it > 0L } ?: return null,
                    lifecycleSequence = parts[7].toLongOrNull()?.takeIf { it > 0L } ?: return null,
                    topLevel =
                        when (parts[8]) {
                            "T" -> true
                            "S" -> false
                            else -> return null
                        },
                )
            val values = parts[9].split(',').map { it.toLongOrNull()?.takeIf { value -> value >= 0L } ?: return null }
            if (values.size != ChromeMediaShieldRendererMetricsSnapshot.FieldCount) return null
            return ChromeMediaShieldRendererMetricsReport(token, identity, values)
        }

        private fun saturatedAdd(
            current: Long,
            value: Long,
        ): Long = if (Long.MAX_VALUE - current < value) Long.MAX_VALUE else current + value

        private const val MaximumDocuments = 128
        private const val MaximumBodyBytes = 4096
        private val MaximumFieldIndexes = setOf(4, 8, 10)
    }
}
