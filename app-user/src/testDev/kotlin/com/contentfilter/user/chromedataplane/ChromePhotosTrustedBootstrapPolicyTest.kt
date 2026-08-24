package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import kotlin.test.Test
import kotlin.test.assertEquals

class ChromePhotosTrustedBootstrapPolicyTest {
    @Test
    fun `fresh install requires one full reset before health can release Chrome`() {
        assertEquals(
            ChromePhotosTrustedBootstrapAction.ResetRequired,
            ChromePhotosTrustedBootstrapPolicy.nextAction(state(resetGeneration = 0), healthy()),
        )
    }

    @Test
    fun `current reset remains blocked until every protection dependency is ready`() {
        val current = state(resetGeneration = ChromePhotosDataPlaneLabContract.TrustedBootstrapGeneration)

        assertEquals(
            ChromePhotosTrustedBootstrapAction.WaitForHealth,
            ChromePhotosTrustedBootstrapPolicy.nextAction(current, healthy(proxyHealthy = false)),
        )
        assertEquals(
            ChromePhotosTrustedBootstrapAction.WaitForHealth,
            ChromePhotosTrustedBootstrapPolicy.nextAction(current, healthy(policyConfirmed = false)),
        )
        assertEquals(
            ChromePhotosTrustedBootstrapAction.WaitForHealth,
            ChromePhotosTrustedBootstrapPolicy.nextAction(current, healthy(vpnConfirmed = false)),
        )
        assertEquals(
            ChromePhotosTrustedBootstrapAction.WaitForHealth,
            ChromePhotosTrustedBootstrapPolicy.nextAction(current, healthy(gloshiaReady = false)),
        )
        assertEquals(
            ChromePhotosTrustedBootstrapAction.WaitForHealth,
            ChromePhotosTrustedBootstrapPolicy.nextAction(current, healthy(accessibilityBound = false)),
        )
    }

    @Test
    fun `verified current generation releases Chrome without requiring another reset`() {
        val completed =
            state(
                resetGeneration = ChromePhotosDataPlaneLabContract.TrustedBootstrapGeneration,
                completeGeneration = ChromePhotosDataPlaneLabContract.TrustedBootstrapGeneration,
                resetCount = 1,
            )

        assertEquals(
            ChromePhotosTrustedBootstrapAction.ReleaseChrome,
            ChromePhotosTrustedBootstrapPolicy.nextAction(completed, healthy()),
        )
    }

    @Test
    fun `bootstrap generation change invalidates previous reset and completion`() {
        val old = state(resetGeneration = 0, completeGeneration = 0, resetCount = 1)

        assertEquals(
            ChromePhotosTrustedBootstrapAction.ResetRequired,
            ChromePhotosTrustedBootstrapPolicy.nextAction(old, healthy()),
        )
    }

    private fun state(
        resetGeneration: Int,
        completeGeneration: Int = 0,
        resetCount: Int = 0,
    ) = ChromePhotosTrustedBootstrapState(
        resetGeneration = resetGeneration,
        completeGeneration = completeGeneration,
        resetCount = resetCount,
    )

    private fun healthy(
        proxyHealthy: Boolean = true,
        policyConfirmed: Boolean = true,
        vpnConfirmed: Boolean = true,
        gloshiaReady: Boolean = true,
        accessibilityBound: Boolean = true,
    ) = ChromePhotosTrustedBootstrapHealth(
        proxyHealthy = proxyHealthy,
        policyConfirmed = policyConfirmed,
        vpnConfirmed = vpnConfirmed,
        gloshiaReady = gloshiaReady,
        accessibilityBound = accessibilityBound,
    )
}
