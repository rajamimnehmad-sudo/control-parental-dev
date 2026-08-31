package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneRuntimeAttestation
import java.io.IOException

/**
 * H20 network-side invariant: once Chrome was reset for the self-shield policy,
 * no realm may acquire a new Service Worker main script through the Glosh data plane.
 */
internal object ChromeServiceWorkerScriptGate {
    fun blocks(
        request: ChromePhotosProxyRequest,
        documentSelfShieldEnabled: Boolean =
            ChromePhotosDataPlaneRuntimeAttestation.snapshot().documentSelfShieldEnabled,
    ): Boolean = documentSelfShieldEnabled && request.isServiceWorkerScriptRequest()

    @Throws(IOException::class)
    fun enforce(request: ChromePhotosProxyRequest) {
        if (blocks(request)) throw IOException("service_worker_script_disabled")
    }
}
