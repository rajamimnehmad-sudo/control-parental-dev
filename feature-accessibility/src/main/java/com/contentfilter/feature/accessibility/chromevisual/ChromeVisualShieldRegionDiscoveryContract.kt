package com.contentfilter.feature.accessibility.chromevisual

internal data class ChromeVisualShieldDiscoveryRaster(
    val width: Int,
    val height: Int,
    val argb: IntArray,
) {
    val size: Int get() = width * height

    fun isValid(): Boolean = width > 0 && height > 0 && argb.size == size
}

internal data class ChromeVisualShieldCarrierHint(
    val bounds: ChromeVisualRegion,
    val roleFlags: Int,
    val visible: Boolean,
    val childCount: Int,
)

internal data class ChromeVisualShieldDiscoveredRegion(
    val id: String,
    val bounds: ChromeVisualRegion,
    val visualSignature: String,
    val assignedPixels: Int,
)

internal data class ChromeVisualShieldCoverageEvidence(
    val totalPixels: Int,
    val certifiedBackgroundPixels: Int,
    val certifiedNonContentPixels: Int,
    val assignedPixels: Int,
    val residualPixels: Int,
    val carrierHintCount: Int,
    val basis: String,
)

internal data class ChromeVisualShieldResidualEvidence(
    val totalPixels: Int,
    val foregroundPixels: Int,
    val residualPixels: Int,
    val componentCount: Int,
    val carrierHintCount: Int,
    val detail: String,
)

internal enum class ChromeVisualShieldDiscoveryUnknownReason {
    InvalidSearchEnvelope,
    StaleIdentity,
    BackgroundAmbiguous,
    CutComponent,
    SignificantResidual,
    OverlappingRegions,
    RegionOverflow,
    InvalidGeometry,
    InsufficientEvidence,
    Cancelled,
    Failure,
}

internal sealed interface ChromeVisualShieldRegionDiscoveryResult {
    data class Complete(
        val regions: List<ChromeVisualShieldDiscoveredRegion>,
        val discoverySequence: Long,
        val regionSetDigest: String,
        val coverageEvidence: ChromeVisualShieldCoverageEvidence,
    ) : ChromeVisualShieldRegionDiscoveryResult

    data class Unknown(
        val reason: ChromeVisualShieldDiscoveryUnknownReason,
        val residualEvidence: ChromeVisualShieldResidualEvidence,
    ) : ChromeVisualShieldRegionDiscoveryResult
}
