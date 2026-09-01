package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentIdentity
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract

internal data class ChromeMediaShieldBootstrapEndpoints(
    val selfReady: String,
    val selfShieldTrace: String,
    val diagnostic: String,
    val rendererMetrics: String,
) {
    companion object {
        fun forIdentity(identity: ChromeMediaShieldDocumentIdentity?): ChromeMediaShieldBootstrapEndpoints =
            if (identity != null) {
                ChromeMediaShieldBootstrapEndpoints(
                    selfReady = ChromePhotosDataPlaneLabContract.MediaShieldSelfReadyPath,
                    selfShieldTrace = ChromePhotosDataPlaneLabContract.MediaShieldSelfShieldTracePath,
                    diagnostic = ChromePhotosDataPlaneLabContract.MediaShieldBootstrapDiagnosticPath,
                    rendererMetrics = ChromePhotosDataPlaneLabContract.MediaShieldRendererMetricsPath,
                )
            } else {
                ChromeMediaShieldBootstrapEndpoints(
                    selfReady = ChromePhotosDataPlaneLabContract.MediaShieldSelfReadyUrl,
                    selfShieldTrace = ChromePhotosDataPlaneLabContract.MediaShieldSelfShieldTraceUrl,
                    diagnostic = ChromePhotosDataPlaneLabContract.MediaShieldBootstrapDiagnosticUrl,
                    rendererMetrics = ChromePhotosDataPlaneLabContract.MediaShieldRendererMetricsUrl,
                )
            }
    }
}
