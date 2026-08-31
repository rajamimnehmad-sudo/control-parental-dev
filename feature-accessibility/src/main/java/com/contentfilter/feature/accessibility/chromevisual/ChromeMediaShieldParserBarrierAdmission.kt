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

    fun accept(completion: ChromeMediaShieldParserBarrierCompletion) {
        if (!completion.isPending()) return
        removeTerminalRequests()
        when (val observed = readContext()) {
            is ChromeMediaShieldActiveDocumentContextReadResult.Found -> {
                publishReady(observed.binding, completion)
            }
            is ChromeMediaShieldActiveDocumentContextReadResult.Unavailable -> {
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
        if (pending.isEmpty()) return
        val observed = readContext() as? ChromeMediaShieldActiveDocumentContextReadResult.Found ?: return
        val current = pending.toList()
        pending.clear()
        current.forEach { completion ->
            if (completion.ready()) onReady(observed.binding)
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
