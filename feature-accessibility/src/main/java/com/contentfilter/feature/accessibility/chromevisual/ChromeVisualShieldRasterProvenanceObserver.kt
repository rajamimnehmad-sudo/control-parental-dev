package com.contentfilter.feature.accessibility.chromevisual

import android.graphics.Bitmap
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * DEV R2A observer. It retains only identities, geometry, counts, hashes, and controlled samples.
 * Full-frame and crop pixels stay in temporary arrays that are wiped before each callback returns.
 */
internal class ChromeVisualShieldRasterProvenanceObserver(
    private val log: (String) -> Unit,
) {
    private data class BoundOracle(
        val identity: ChromeVisualShieldIdentity,
        val value: ChromeVisualShieldRegionDiscoveryOracle,
    )

    private data class PreparedGeometry(
        val fullSearchEnvelope: ChromeVisualShieldRasterBox,
        val fullCarrier: ChromeVisualShieldRasterBox,
        val fullDraws: List<ChromeVisualShieldRasterBox>,
        val cropCarrier: ChromeVisualShieldRasterBox,
        val cropDraws: List<ChromeVisualShieldRasterBox>,
    )

    private var active = false
    private var renderGeometryKeyDigest: String? = null
    private var renderedIdentity: ChromeVisualShieldIdentity? = null
    private var attestedIdentity: ChromeVisualShieldIdentity? = null
    private var boundOracle: BoundOracle? = null
    private var captureIdentity: ChromeVisualShieldIdentity? = null
    private var plannerWorkIdentity: ChromeVisualShieldIdentity? = null
    private var plannerCurrentContext: ChromeVisualShieldContext? = null
    private var fullFingerprint: ChromeVisualShieldRasterFingerprint? = null
    private var cropFingerprint: ChromeVisualShieldRasterFingerprint? = null
    private var preparedGeometry: PreparedGeometry? = null
    private var cropSha256: String? = null
    private var classification: ChromeVisualShieldRasterProvenanceClassification? = null

    @Synchronized
    fun reset(enabled: Boolean) {
        active = enabled
        renderGeometryKeyDigest = null
        renderedIdentity = null
        attestedIdentity = null
        boundOracle = null
        captureIdentity = null
        plannerWorkIdentity = null
        plannerCurrentContext = null
        fullFingerprint = null
        cropFingerprint = null
        preparedGeometry = null
        cropSha256 = null
        classification = null
    }

    @Synchronized
    fun onSessionEnded() {
        active = false
    }

    @Synchronized
    fun onRenderIdentityAccepted(
        identity: ChromeVisualShieldIdentity,
        geometryKeyDigest: String,
    ) {
        if (!active) return
        renderedIdentity = identity
        renderGeometryKeyDigest = geometryKeyDigest
        log("phase=raster_provenance_identity ${identity.logFields()} renderGeometryKeyDigest=$geometryKeyDigest")
    }

    @Synchronized
    fun onAttestationAccepted(
        identity: ChromeVisualShieldIdentity,
        oracle: ChromeVisualShieldRegionDiscoveryOracle?,
    ) {
        if (!active) return
        attestedIdentity = identity
        boundOracle = oracle?.let { BoundOracle(identity, it) }
        log(
            "phase=raster_provenance_attestation ${identity.logFields()} " +
                "renderGeometryKeyDigest=${renderGeometryKeyDigest ?: "none"} oraclePresent=${oracle != null}",
        )
    }

    @Synchronized
    fun onOpaqueCommitted(
        committedEpoch: Long,
        current: ChromeVisualShieldIdentity,
    ) {
        if (!active) return
        log(
            "phase=raster_provenance_opaque committedEpoch=$committedEpoch " +
                "currentContentEpoch=${current.contentEpoch} viewportEpoch=${current.viewportEpoch} " +
                "regionSequence=${current.regionSequence}",
        )
    }

    @Synchronized
    fun onBeginCapture(identity: ChromeVisualShieldIdentity) {
        if (!active) return
        captureIdentity = identity
        log("phase=raster_provenance_capture ${identity.logFields()}")
        classifyEpochMismatchIfPresent()
    }

    @Synchronized
    fun onPlannerEntry(
        workIdentity: ChromeVisualShieldIdentity,
        currentContext: ChromeVisualShieldContext?,
    ) {
        if (!active) return
        plannerWorkIdentity = workIdentity
        plannerCurrentContext = currentContext
        val oracleIdentity = boundOracle?.identity
        log(
            "phase=raster_provenance_planner workContentEpoch=${workIdentity.contentEpoch} " +
                "workViewportEpoch=${workIdentity.viewportEpoch} workRegionSequence=${workIdentity.regionSequence} " +
                "currentContentEpoch=${currentContext?.contentEpoch} currentViewportEpoch=${currentContext?.viewportEpoch} " +
                "currentRegionSequence=${currentContext?.regionSequence} " +
                "oracleContentEpoch=${oracleIdentity?.contentEpoch} oracleRegionSequence=${oracleIdentity?.regionSequence}",
        )
    }

    fun observeFullFrame(
        bitmap: Bitmap,
        identity: ChromeVisualShieldIdentity,
    ) {
        val oracle = synchronized(this) { if (active) boundOracle else null }
        if (oracle == null) {
            recordUnknown("oracle_missing_before_full_frame")
            return
        }
        if (hasEpochMismatch()) return
        try {
            val geometry = prepareGeometry(identity, oracle.value, bitmap.width, bitmap.height)
            if (geometry == null) {
                recordUnknown("full_frame_geometry_unavailable")
                return
            }
            val pixels = bitmap.copyPixelsForObserver()
            try {
                val fingerprint =
                    ChromeVisualShieldRasterFingerprintFactory.create(
                        bitmap.width,
                        bitmap.height,
                        pixels,
                        geometry.fullSamplePoints(),
                    )
                synchronized(this) {
                    if (!active || captureIdentity != identity) return
                    preparedGeometry = geometry
                    fullFingerprint = fingerprint
                    log("phase=raster_provenance_full ${fingerprint.logValue()}")
                }
            } finally {
                pixels.fill(0)
            }
        } catch (failure: RuntimeException) {
            recordUnknown("full_frame_observer_failure=${failure::class.java.simpleName}")
        }
    }

    fun observeCrop(
        bitmap: Bitmap,
        identity: ChromeVisualShieldIdentity,
    ) {
        if (hasEpochMismatch()) return
        val geometry = synchronized(this) { preparedGeometry }
        if (geometry == null) {
            recordUnknown("crop_geometry_unavailable")
            return
        }
        try {
            val pixels = bitmap.copyPixelsForObserver()
            try {
                val fingerprint =
                    ChromeVisualShieldRasterFingerprintFactory.create(
                        bitmap.width,
                        bitmap.height,
                        pixels,
                        geometry.cropSamplePoints(),
                    )
                val drawForeign =
                    geometry.cropDraws.maxOfOrNull { draw ->
                        ChromeVisualShieldRasterFingerprintFactory.foreignFraction(
                            bitmap.width,
                            bitmap.height,
                            pixels,
                            draw,
                        )
                    } ?: 0.0
                val evidenceSha = ChromeVisualShieldCropEvidenceFactory.from(bitmap).rgbaSha256
                val signals = buildSignals(fingerprint, geometry, drawForeign)
                val result = ChromeVisualShieldRasterProvenanceClassifier.classify(signals)
                val expectedCropDraw = geometry.cropDraws.firstOrNull()
                val expectedFullDraw = geometry.fullDraws.firstOrNull()
                val geometryEvidence =
                    ChromeVisualShieldRasterGeometryEvidenceFactory.create(
                        searchEnvelope = geometry.fullSearchEnvelope,
                        carrier = geometry.fullCarrier,
                        expectedDraw = expectedFullDraw,
                        observedCard = fullFingerprint?.cardClusters?.bestSizeMatch(expectedFullDraw),
                        cropExpectedDraw = expectedCropDraw,
                        cropObservedCard = fingerprint.cardClusters.bestSizeMatch(expectedCropDraw),
                    )
                synchronized(this) {
                    if (!active || captureIdentity != identity) return
                    cropFingerprint = fingerprint
                    cropSha256 = evidenceSha
                    classification = result
                    log("phase=raster_provenance_crop cropSha=$evidenceSha ${fingerprint.logValue()}")
                    log("phase=raster_provenance_geometry ${geometryEvidence.logValue()}")
                    log(
                        "phase=raster_provenance_classification rootCause=${result.cause} basis=${result.basis} " +
                            "mappingDelta=${result.mappingDelta?.logValue() ?: "none"}",
                    )
                }
            } finally {
                pixels.fill(0)
            }
        } catch (failure: RuntimeException) {
            recordUnknown("crop_observer_failure=${failure::class.java.simpleName}")
        }
    }

    @Synchronized
    fun statusValue(): String =
        "rasterProvenanceActive=$active rasterRootCause=${classification?.cause ?: "none"} " +
            "rasterCropSha=${cropSha256 ?: "none"} attestedContentEpoch=${attestedIdentity?.contentEpoch} " +
            "captureContentEpoch=${captureIdentity?.contentEpoch} " +
            "attestedRegionSequence=${attestedIdentity?.regionSequence} " +
            "captureRegionSequence=${captureIdentity?.regionSequence}"

    private fun buildSignals(
        crop: ChromeVisualShieldRasterFingerprint,
        geometry: PreparedGeometry,
        drawForeign: Double,
    ): ChromeVisualShieldRasterProvenanceSignals {
        val full = synchronized(this) { fullFingerprint }
        val expectedDraw = geometry.cropDraws.firstOrNull()
        val expectedFullDraw = geometry.fullDraws.firstOrNull()
        val cropCard = crop.cardClusters.bestSizeMatch(expectedDraw)
        val expectedCardPresent = cropCard != null && cropCard.isAtExpected(expectedDraw)
        val fullCard = full?.cardClusters?.bestSizeMatch(expectedFullDraw)
        val insideSearchFraction =
            fullCard?.let {
                it.intersectionArea(geometry.fullSearchEnvelope).toDouble() / it.area.coerceAtLeast(1)
            }
        val matchingOutside =
            fullCard != null && expectedFullDraw != null &&
                !fullCard.isAtExpected(expectedFullDraw) &&
                checkNotNull(insideSearchFraction) < MinimumInsideSearchFraction
        val delta =
            if (fullCard != null && expectedFullDraw != null) {
                ChromeVisualShieldRasterMappingDelta(
                    deltaX = fullCard.centerX - expectedFullDraw.centerX,
                    deltaY = fullCard.centerY - expectedFullDraw.centerY,
                    scaleX = fullCard.width.toDouble() / expectedFullDraw.width,
                    scaleY = fullCard.height.toDouble() / expectedFullDraw.height,
                )
            } else {
                null
            }
        return ChromeVisualShieldRasterProvenanceSignals(
            attestedContentEpoch = synchronized(this) { attestedIdentity?.contentEpoch },
            attestedRegionSequence = synchronized(this) { attestedIdentity?.regionSequence },
            captureContentEpoch = synchronized(this) { captureIdentity?.contentEpoch },
            captureRegionSequence = synchronized(this) { captureIdentity?.regionSequence },
            fullFrame = full,
            crop = crop,
            carrierAlignedWithSearchEnvelope = geometry.fullCarrier.edgesNear(geometry.fullSearchEnvelope),
            expectedCardPresentInCrop = expectedCardPresent,
            expectedDrawNonNeutralFraction = drawForeign,
            matchingCardOutsideSearchEnvelope = matchingOutside,
            mappingDelta = delta,
        )
    }

    @Synchronized
    private fun classifyEpochMismatchIfPresent() {
        val attested = attestedIdentity ?: return
        val captured = captureIdentity ?: return
        if (
            attested.contentEpoch == captured.contentEpoch &&
            attested.regionSequence == captured.regionSequence
        ) {
            return
        }
        classification =
            ChromeVisualShieldRasterProvenanceClassifier.classify(
                ChromeVisualShieldRasterProvenanceSignals(
                    attested.contentEpoch,
                    attested.regionSequence,
                    captured.contentEpoch,
                    captured.regionSequence,
                    null,
                    null,
                    false,
                    false,
                    0.0,
                    false,
                    null,
                ),
            )
        log(
            "phase=raster_provenance_classification rootCause=${classification?.cause} " +
                "basis=${classification?.basis} rasterInspection=skipped",
        )
    }

    @Synchronized
    private fun hasEpochMismatch(): Boolean = classification?.cause == ChromeVisualShieldRasterRootCause.EPOCH_MISMATCH

    @Synchronized
    private fun recordUnknown(basis: String) {
        if (!active || classification?.cause == ChromeVisualShieldRasterRootCause.EPOCH_MISMATCH) return
        classification =
            ChromeVisualShieldRasterProvenanceClassification(
                ChromeVisualShieldRasterRootCause.UNKNOWN,
                basis,
            )
        log("phase=raster_provenance_classification rootCause=UNKNOWN basis=$basis")
    }

    private fun prepareGeometry(
        identity: ChromeVisualShieldIdentity,
        oracle: ChromeVisualShieldRegionDiscoveryOracle,
        frameWidth: Int,
        frameHeight: Int,
    ): PreparedGeometry? {
        if (oracle.renderIdentityToken != identity.renderIdentityToken()) return null
        val globalCarrier =
            ChromeVisualShieldBrowserViewportMapper.map(
                source = oracle.carrierCss,
                target = identity.viewport,
                visualViewport = oracle.visualViewportCss,
                devicePixelRatio = oracle.devicePixelRatio,
                visualViewportScale = oracle.visualViewportScale,
                navigationInsets = oracle.navigationInsets,
                id = "raster-provenance-carrier",
            ) ?: return null
        val fullCarrier =
            ChromeVisualGeometryMapper.toFrame(globalCarrier, identity.viewport, frameWidth, frameHeight)
                ?.toRasterBox() ?: return null
        val fullSearch =
            ChromeVisualGeometryMapper.toFrame(identity.region, identity.viewport, frameWidth, frameHeight)
                ?.toRasterBox() ?: return null
        val fullDraws =
            oracle.regions.map { region ->
                val global =
                    ChromeVisualRegion(
                        id = region.oracleId,
                        left =
                            floor(
                                globalCarrier.left + globalCarrier.width * region.drawCanvas.left / oracle.canvasWidth,
                            ).toInt(),
                        top =
                            floor(
                                globalCarrier.top + globalCarrier.height * region.drawCanvas.top / oracle.canvasHeight,
                            ).toInt(),
                        right =
                            ceil(
                                globalCarrier.left + globalCarrier.width * region.drawCanvas.right / oracle.canvasWidth,
                            ).toInt(),
                        bottom =
                            ceil(
                                globalCarrier.top + globalCarrier.height * region.drawCanvas.bottom / oracle.canvasHeight,
                            ).toInt(),
                    )
                ChromeVisualGeometryMapper.toFrame(global, identity.viewport, frameWidth, frameHeight)
                    ?.toRasterBox() ?: return null
            }
        return PreparedGeometry(
            fullSearchEnvelope = fullSearch,
            fullCarrier = fullCarrier,
            fullDraws = fullDraws,
            cropCarrier = fullCarrier.toCrop(fullSearch),
            cropDraws = fullDraws.map { it.toCrop(fullSearch) },
        )
    }

    private fun PreparedGeometry.fullSamplePoints(): List<Pair<String, Pair<Int, Int>>> =
        points("search", fullSearchEnvelope) +
            points("carrier", fullCarrier) +
            drawPoints(fullDraws) +
            listOf(
                "outside-carrier-left" to ((fullCarrier.left - 2) to fullCarrier.centerY.toInt()),
                "outside-carrier-top" to (fullCarrier.centerX.toInt() to (fullCarrier.top - 2)),
            )

    private fun PreparedGeometry.cropSamplePoints(): List<Pair<String, Pair<Int, Int>>> =
        points("carrier", cropCarrier) + drawPoints(cropDraws)

    private fun points(
        prefix: String,
        box: ChromeVisualShieldRasterBox,
    ): List<Pair<String, Pair<Int, Int>>> =
        listOf(
            "$prefix-tl" to (box.left to box.top),
            "$prefix-tr" to ((box.right - 1) to box.top),
            "$prefix-bl" to (box.left to (box.bottom - 1)),
            "$prefix-br" to ((box.right - 1) to (box.bottom - 1)),
            "$prefix-center" to (box.centerX.toInt() to box.centerY.toInt()),
        )

    private fun drawPoints(draws: List<ChromeVisualShieldRasterBox>): List<Pair<String, Pair<Int, Int>>> =
        draws.flatMapIndexed { index, draw ->
            listOf(
                "draw-${index + 1}-center" to (draw.centerX.toInt() to draw.centerY.toInt()),
                "draw-${index + 1}-card-left" to ((draw.left - 1) to draw.centerY.toInt()),
                "draw-${index + 1}-card-top" to (draw.centerX.toInt() to (draw.top - 1)),
            )
        }

    private fun ChromeVisualShieldRasterBox.toCrop(search: ChromeVisualShieldRasterBox) =
        ChromeVisualShieldRasterBox(
            left = left - search.left,
            top = top - search.top,
            right = right - search.left,
            bottom = bottom - search.top,
        )

    private fun ChromeVisualRegion.toRasterBox() = ChromeVisualShieldRasterBox(left, top, right, bottom)

    private fun List<ChromeVisualShieldRasterBox>.bestSizeMatch(
        expected: ChromeVisualShieldRasterBox?,
    ): ChromeVisualShieldRasterBox? {
        expected ?: return null
        return filter { candidate ->
            candidate.width.toDouble() / expected.width in MinimumSizeRatio..MaximumSizeRatio &&
                candidate.height.toDouble() / expected.height in MinimumSizeRatio..MaximumSizeRatio
        }.minByOrNull { candidate ->
            abs(candidate.width - expected.width) + abs(candidate.height - expected.height)
        }
    }

    private fun ChromeVisualShieldRasterBox.isAtExpected(expected: ChromeVisualShieldRasterBox?): Boolean {
        expected ?: return false
        val horizontalTolerance = maxOf(MinimumPositionTolerance, expected.width * PositionToleranceFraction)
        val verticalTolerance = maxOf(MinimumPositionTolerance, expected.height * PositionToleranceFraction)
        return abs(centerX - expected.centerX) <= horizontalTolerance &&
            abs(centerY - expected.centerY) <= verticalTolerance
    }

    private fun ChromeVisualShieldRasterBox.edgesNear(other: ChromeVisualShieldRasterBox): Boolean =
        abs(left - other.left) <= GeometryTolerance &&
            abs(top - other.top) <= GeometryTolerance &&
            abs(right - other.right) <= GeometryTolerance &&
            abs(bottom - other.bottom) <= GeometryTolerance

    private fun Bitmap.copyPixelsForObserver(): IntArray =
        IntArray(width * height).also { pixels ->
            getPixels(pixels, 0, width, 0, 0, width, height)
        }

    private fun ChromeVisualShieldIdentity.logFields(): String =
        "protectionSessionId=$protectionSessionId windowId=$windowId contentEpoch=$contentEpoch " +
            "viewportEpoch=$viewportEpoch regionSequence=$regionSequence captureSequence=$captureSequence " +
            "renderIdentityToken=${renderIdentityToken()}"

    private fun ChromeVisualShieldRasterFingerprint.logValue(): String {
        val colorValue =
            colors.joinToString(",") { value ->
                "${value.name}:${value.count}:${value.fraction.fixed()}:${value.bounds ?: "none"}"
            }
        val clusterValue = cardClusters.joinToString(";").ifEmpty { "none" }
        val sampleValue = samples.joinToString(",") { "${it.label}@${it.x},${it.y}=#${it.rgbHex}" }
        return "dimensions=${width}x$height colors=$colorValue cardClusters=$clusterValue samples=$sampleValue"
    }

    private fun ChromeVisualShieldRasterMappingDelta.logValue(): String =
        "dx=${deltaX.fixed()},dy=${deltaY.fixed()},sx=${scaleX.fixed()},sy=${scaleY.fixed()}"

    private fun Double.fixed(): String = String.format(Locale.US, "%.6f", this)

    private companion object {
        const val MinimumSizeRatio = 0.75
        const val MaximumSizeRatio = 1.25
        const val MinimumPositionTolerance = 8.0
        const val PositionToleranceFraction = 0.05
        const val GeometryTolerance = 3
        const val MinimumInsideSearchFraction = 0.5
    }
}
