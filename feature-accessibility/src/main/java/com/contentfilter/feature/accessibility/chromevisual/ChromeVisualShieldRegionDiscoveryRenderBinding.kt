package com.contentfilter.feature.accessibility.chromevisual

/** Native R2A generation. It is deliberately separate from the shared R1 render token. */
data class ChromeVisualShieldRegionDiscoveryNativeGeneration(
    val protectionSessionId: Long,
    val windowId: Int,
    val contentEpoch: Long,
    val viewportEpoch: Long,
    val regionSequence: Long,
    val renderIdentityToken: String,
) {
    fun isStructurallyValid(): Boolean =
        protectionSessionId > 0 &&
            windowId >= 0 &&
            contentEpoch > 0 &&
            viewportEpoch > 0 &&
            regionSequence > 0 &&
            renderIdentityToken.isNotBlank()
}

/** Immutable attestation generation binding returned after beginFixtureRender invalidates. */
data class ChromeVisualShieldRegionDiscoveryRenderBinding(
    val protectionSessionId: Long,
    val windowId: Int,
    val contentEpoch: Long,
    val viewportEpoch: Long,
    val regionSequence: Long,
    val renderIdentityToken: String,
    val renderGeometryKeyDigest: String,
) {
    fun isStructurallyValid(): Boolean =
        generation().isStructurallyValid() && renderGeometryKeyDigest.matches(Sha256Pattern)

    fun matches(generation: ChromeVisualShieldRegionDiscoveryNativeGeneration): Boolean =
        this.generation() == generation

    internal fun matches(context: ChromeVisualShieldContext): Boolean =
        protectionSessionId == context.protectionSessionId &&
            windowId == context.windowId &&
            contentEpoch == context.contentEpoch &&
            viewportEpoch == context.viewportEpoch &&
            regionSequence == context.regionSequence &&
            renderIdentityToken == context.renderIdentityToken()

    internal fun matches(identity: ChromeVisualShieldIdentity): Boolean =
        protectionSessionId == identity.protectionSessionId &&
            windowId == identity.windowId &&
            contentEpoch == identity.contentEpoch &&
            viewportEpoch == identity.viewportEpoch &&
            regionSequence == identity.regionSequence &&
            renderIdentityToken == identity.renderIdentityToken()

    fun generation(): ChromeVisualShieldRegionDiscoveryNativeGeneration =
        ChromeVisualShieldRegionDiscoveryNativeGeneration(
            protectionSessionId = protectionSessionId,
            windowId = windowId,
            contentEpoch = contentEpoch,
            viewportEpoch = viewportEpoch,
            regionSequence = regionSequence,
            renderIdentityToken = renderIdentityToken,
        )

    private companion object {
        val Sha256Pattern = Regex("[0-9a-f]{64}")
    }
}

enum class ChromeVisualShieldRegionDiscoveryGenerationOutcome {
    Completed,
    Invalidated,
    Stopped,
    TimedOut,
}

internal fun ChromeVisualShieldContext.toRegionDiscoveryGeneration() =
    ChromeVisualShieldRegionDiscoveryNativeGeneration(
        protectionSessionId = protectionSessionId,
        windowId = windowId,
        contentEpoch = contentEpoch,
        viewportEpoch = viewportEpoch,
        regionSequence = regionSequence,
        renderIdentityToken = renderIdentityToken(),
    )

internal fun ChromeVisualShieldContext.toRegionDiscoveryBinding(renderGeometryKeyDigest: String) =
    ChromeVisualShieldRegionDiscoveryRenderBinding(
        protectionSessionId = protectionSessionId,
        windowId = windowId,
        contentEpoch = contentEpoch,
        viewportEpoch = viewportEpoch,
        regionSequence = regionSequence,
        renderIdentityToken = renderIdentityToken(),
        renderGeometryKeyDigest = renderGeometryKeyDigest,
    )
