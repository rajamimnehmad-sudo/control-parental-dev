package com.contentfilter.user.chromedataplane

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChromeStockMediaRuntimeModeTest {
    @Test
    fun `explicit H19 requires and retains full tunnel`() {
        val mode =
            resolve(
                hasExplicitMode = true,
                explicitStock = true,
                explicitFullTunnel = true,
                explicitReplaceAll = true,
            )

        assertTrue(mode.stockMediaAuthorityEnabled)
        assertTrue(mode.fullTunnelDevGateEnabled)
        assertTrue(mode.replaceAllNetworkVisuals)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `explicit H19 without full tunnel fails closed`() {
        resolve(
            hasExplicitMode = true,
            explicitStock = true,
            explicitFullTunnel = false,
        )
    }

    @Test
    fun `restart without extras restores persisted H19 mode`() {
        val mode =
            resolve(
                hasExplicitMode = false,
                persistedStock = true,
                persistedFullTunnel = true,
                persistedReplaceAll = true,
            )

        assertTrue(mode.stockMediaAuthorityEnabled)
        assertTrue(mode.fullTunnelDevGateEnabled)
        assertTrue(mode.replaceAllNetworkVisuals)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `invalid persisted H19 mode cannot silently downgrade`() {
        resolve(
            hasExplicitMode = false,
            persistedStock = true,
            persistedFullTunnel = false,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `explicit Replace-All without H19 cannot publish weak coverage`() {
        resolve(
            hasExplicitMode = true,
            explicitStock = false,
            explicitFullTunnel = true,
            explicitReplaceAll = true,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `persisted Replace-All without H19 fails closed on restart`() {
        resolve(
            hasExplicitMode = false,
            persistedStock = false,
            persistedFullTunnel = true,
            persistedReplaceAll = true,
        )
    }

    @Test
    fun `explicit non H19 launch replaces persisted H19 request`() {
        val mode =
            resolve(
                hasExplicitMode = true,
                explicitStock = false,
                explicitFullTunnel = false,
                persistedStock = true,
                persistedFullTunnel = true,
                persistedReplaceAll = true,
            )

        assertFalse(mode.stockMediaAuthorityEnabled)
        assertFalse(mode.fullTunnelDevGateEnabled)
        assertFalse(mode.replaceAllNetworkVisuals)
    }

    private fun resolve(
        hasExplicitMode: Boolean,
        explicitStock: Boolean = false,
        explicitFullTunnel: Boolean = false,
        explicitReplaceAll: Boolean = false,
        persistedStock: Boolean = false,
        persistedFullTunnel: Boolean = false,
        persistedReplaceAll: Boolean = false,
    ): ChromeStockMediaRuntimeMode =
        ChromeStockMediaRuntimeModeResolver.resolve(
            hasExplicitMode = hasExplicitMode,
            explicitStockMediaAuthorityEnabled = explicitStock,
            explicitFullTunnelDevGateEnabled = explicitFullTunnel,
            explicitReplaceAllNetworkVisuals = explicitReplaceAll,
            persistedStockMediaAuthorityEnabled = persistedStock,
            persistedFullTunnelDevGateEnabled = persistedFullTunnel,
            persistedReplaceAllNetworkVisuals = persistedReplaceAll,
        )
}
