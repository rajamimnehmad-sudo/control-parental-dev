package com.contentfilter.feature.accessibility.chromevisual

/**
 * Work gate derived exclusively from the current Visual Shield viewport identity. It owns no
 * authority: release still requires the complete capture identity in [ChromeVisualShieldIdentityGate].
 */
internal class ChromeVisualShieldViewportRenderGate {
    private var requiredToken: String? = null
    private var attestedToken: String? = null
    private var opaqueToken: String? = null

    @Synchronized
    fun requireCurrentRender(context: ChromeVisualShieldContext) {
        requiredToken = context.renderIdentityToken()
        attestedToken = null
        opaqueToken = null
    }

    @Synchronized
    fun recordAttestation(
        token: String,
        context: ChromeVisualShieldContext,
    ): Boolean {
        val current = context.renderIdentityToken()
        if (token != current) return false
        if (requiredToken == current) attestedToken = current
        return true
    }

    @Synchronized
    fun recordOpaqueCommit(context: ChromeVisualShieldContext) {
        val current = context.renderIdentityToken()
        if (requiredToken == current) opaqueToken = current
    }

    @Synchronized
    fun requireOpaqueCommitAfterAttestation(context: ChromeVisualShieldContext): Boolean {
        val current = context.renderIdentityToken()
        if (requiredToken != current || attestedToken != current) return false
        opaqueToken = null
        return true
    }

    @Synchronized
    fun consumeCapturePermission(context: ChromeVisualShieldContext): Boolean {
        val required = requiredToken ?: return true
        val current = context.renderIdentityToken()
        if (required != current || attestedToken != current || opaqueToken != current) return false
        requiredToken = null
        attestedToken = null
        opaqueToken = null
        return true
    }

    @Synchronized
    fun isWaiting(context: ChromeVisualShieldContext): Boolean = requiredToken == context.renderIdentityToken()

    @Synchronized
    fun reset() {
        requiredToken = null
        attestedToken = null
        opaqueToken = null
    }
}

internal fun ChromeVisualShieldContext.renderIdentityToken(): String =
    listOf(
        protectionSessionId,
        windowId,
        viewportEpoch,
        viewport.left,
        viewport.top,
        viewport.right,
        viewport.bottom,
        regionId,
        region.left,
        region.top,
        region.right,
        region.bottom,
    ).joinToString(":")
