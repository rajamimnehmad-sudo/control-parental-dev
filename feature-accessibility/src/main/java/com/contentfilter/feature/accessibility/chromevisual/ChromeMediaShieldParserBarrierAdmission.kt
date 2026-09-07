package com.contentfilter.feature.accessibility.chromevisual

import com.contentfilter.core.domain.chrome.ChromeMediaShieldParserBarrierCompletion

/** Pure, bounded admission from parser barriers to one attached structural foreground WebView. */
internal class ChromeMediaShieldParserBarrierAdmission(
    private val readContext: () -> ChromeMediaShieldActiveDocumentContextReadResult,
    private val onWaiting: (String) -> Unit,
    private val onReady: (ChromeMediaShieldActiveDocumentNativeBinding) -> Unit,
    private val onCancelled: () -> Unit,
) : AutoCloseable {
    private val pending = linkedSetOf<ChromeMediaShieldParserBarrierCompletion>()
    private var lastChromeBinding: ChromeMediaShieldActiveDocumentNativeBinding? = null

    fun accept(completion: ChromeMediaShieldParserBarrierCompletion) {
        if (!completion.isPending()) return
        removeTerminalRequests()
        when (val observed = readContext()) {
            is ChromeMediaShieldActiveDocumentContextReadResult.Found -> {
                lastChromeBinding = observed.binding
                publishReady(observed.binding, completion)
            }
            is ChromeMediaShieldActiveDocumentContextReadResult.Unavailable -> {
                // A protected SurfaceControl may temporarily become the only accessibility
                // window while Chrome remains the same native foreground window. This barrier is
                // non-authoritative, so reuse the last exact Chrome binding only to release the
                // parser; the subsequent H19 HELLO performs a fresh context check before any
                // presentation authority can be granted.
                val cached = lastChromeBinding
                if (cached != null) {
                    publishReady(cached, completion)
                    return
                }
                if (pending.size >= MaximumPendingRequests) {
                    val oldest = pending.first()
                    pending.remove(oldest)
                    oldest.supersede()
                }
                if (!completion.isPending()) return
                pending += completion
                onWaiting(observed.reason)
            }
        }
    }

    fun onChromeStructuralEvent() {
        removeTerminalRequests()
        val observed = readContext() as? ChromeMediaShieldActiveDocumentContextReadResult.Found
        if (observed != null) lastChromeBinding = observed.binding
        if (pending.isEmpty()) return
        val binding = observed?.binding ?: lastChromeBinding ?: return
        val current = pending.toList()
        pending.clear()
        current.forEach { completion ->
            if (completion.ready()) onReady(binding)
        }
    }

    fun onTransportCancelled(completion: ChromeMediaShieldParserBarrierCompletion): Boolean {
        if (!pending.remove(completion)) return false
        onCancelled()
        return true
    }

    fun hasPending(): Boolean = pending.isNotEmpty()

    override fun close() {
        val current = pending.toList()
        pending.clear()
        current.forEach { it.reject() }
    }

    private fun publishReady(
        binding: ChromeMediaShieldActiveDocumentNativeBinding,
        completion: ChromeMediaShieldParserBarrierCompletion,
    ) {
        val current = linkedSetOf<ChromeMediaShieldParserBarrierCompletion>()
        current += pending
        current += completion
        pending.clear()
        current.forEach { candidate ->
            if (candidate.ready()) onReady(binding)
        }
    }

    private fun removeTerminalRequests() {
        pending.removeAll { !it.isPending() }
    }

    private companion object {
        const val MaximumPendingRequests = 4
    }
}
