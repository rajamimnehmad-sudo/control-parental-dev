package com.contentfilter.user.chromedataplane

internal data class ChromeStockMediaRuntimeMode(
    val stockMediaAuthorityEnabled: Boolean,
    val fullTunnelDevGateEnabled: Boolean,
    val replaceAllNetworkVisuals: Boolean,
) {
    init {
        require(!stockMediaAuthorityEnabled || fullTunnelDevGateEnabled) {
            "stock_media_authority_requires_full_tunnel"
        }
        require(!replaceAllNetworkVisuals || stockMediaAuthorityEnabled) {
            "replace_all_requires_stock_media_authority"
        }
    }
}

/** Resolves an explicit DEV launch or restores the last requested fail-close mode after restart. */
internal object ChromeStockMediaRuntimeModeResolver {
    fun resolve(
        hasExplicitMode: Boolean,
        explicitStockMediaAuthorityEnabled: Boolean,
        explicitFullTunnelDevGateEnabled: Boolean,
        explicitReplaceAllNetworkVisuals: Boolean,
        persistedStockMediaAuthorityEnabled: Boolean,
        persistedFullTunnelDevGateEnabled: Boolean,
        persistedReplaceAllNetworkVisuals: Boolean,
    ): ChromeStockMediaRuntimeMode =
        if (hasExplicitMode) {
            ChromeStockMediaRuntimeMode(
                stockMediaAuthorityEnabled = explicitStockMediaAuthorityEnabled,
                fullTunnelDevGateEnabled = explicitFullTunnelDevGateEnabled,
                replaceAllNetworkVisuals = explicitReplaceAllNetworkVisuals,
            )
        } else {
            ChromeStockMediaRuntimeMode(
                stockMediaAuthorityEnabled = persistedStockMediaAuthorityEnabled,
                fullTunnelDevGateEnabled = persistedFullTunnelDevGateEnabled,
                replaceAllNetworkVisuals = persistedReplaceAllNetworkVisuals,
            )
        }
}
