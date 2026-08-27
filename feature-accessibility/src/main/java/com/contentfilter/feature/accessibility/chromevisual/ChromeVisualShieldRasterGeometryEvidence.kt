package com.contentfilter.feature.accessibility.chromevisual

import java.util.Locale

/** DEV-only aggregate geometry evidence. It receives no pixels and cannot affect discovery. */
internal data class ChromeVisualShieldRasterGeometryEvidence(
    val searchEnvelope: ChromeVisualShieldRasterBox,
    val carrier: ChromeVisualShieldRasterBox,
    val expectedDraw: ChromeVisualShieldRasterBox?,
    val observedCard: ChromeVisualShieldRasterBox?,
    val cropExpectedDraw: ChromeVisualShieldRasterBox?,
    val cropObservedCard: ChromeVisualShieldRasterBox?,
    val intersection: Int,
    val oracleCoverage: Double,
    val candidateAreaRatio: Double,
    val insideSearchFraction: Double,
    val mappingDelta: ChromeVisualShieldRasterMappingDelta?,
) {
    fun logValue(): String =
        "searchEnvelope=${searchEnvelope.compact()} carrier=${carrier.compact()} " +
            "expectedDraw=${expectedDraw?.compact() ?: "none"} " +
            "observedCard=${observedCard?.compact() ?: "none"} " +
            "cropExpectedDraw=${cropExpectedDraw?.compact() ?: "none"} " +
            "cropObservedCard=${cropObservedCard?.compact() ?: "none"} intersection=$intersection " +
            "oracleCoverage=${oracleCoverage.fixed()} candidateAreaRatio=${candidateAreaRatio.fixed()} " +
            "insideSearchFraction=${insideSearchFraction.fixed()} " +
            "mappingDelta=${mappingDelta?.logValue() ?: "none"}"

    private fun ChromeVisualShieldRasterBox.compact(): String = "$left,$top,$right,$bottom"

    private fun ChromeVisualShieldRasterMappingDelta.logValue(): String =
        "dx=${deltaX.fixed()},dy=${deltaY.fixed()},sx=${scaleX.fixed()},sy=${scaleY.fixed()}"

    private fun Double.fixed(): String = String.format(Locale.US, "%.6f", this)
}

internal object ChromeVisualShieldRasterGeometryEvidenceFactory {
    fun create(
        searchEnvelope: ChromeVisualShieldRasterBox,
        carrier: ChromeVisualShieldRasterBox,
        expectedDraw: ChromeVisualShieldRasterBox?,
        observedCard: ChromeVisualShieldRasterBox?,
        cropExpectedDraw: ChromeVisualShieldRasterBox?,
        cropObservedCard: ChromeVisualShieldRasterBox?,
    ): ChromeVisualShieldRasterGeometryEvidence {
        val intersection =
            if (cropObservedCard != null && cropExpectedDraw != null) {
                cropObservedCard.intersectionArea(cropExpectedDraw)
            } else {
                0
            }
        val mappingDelta =
            if (cropObservedCard != null && cropExpectedDraw != null) {
                ChromeVisualShieldRasterMappingDelta(
                    deltaX = cropObservedCard.centerX - cropExpectedDraw.centerX,
                    deltaY = cropObservedCard.centerY - cropExpectedDraw.centerY,
                    scaleX = cropObservedCard.width.toDouble() / cropExpectedDraw.width.coerceAtLeast(1),
                    scaleY = cropObservedCard.height.toDouble() / cropExpectedDraw.height.coerceAtLeast(1),
                )
            } else {
                null
            }
        return ChromeVisualShieldRasterGeometryEvidence(
            searchEnvelope = searchEnvelope,
            carrier = carrier,
            expectedDraw = expectedDraw,
            observedCard = observedCard,
            cropExpectedDraw = cropExpectedDraw,
            cropObservedCard = cropObservedCard,
            intersection = intersection,
            oracleCoverage = intersection.toDouble() / (cropExpectedDraw?.area ?: 0).coerceAtLeast(1),
            candidateAreaRatio =
                (cropObservedCard?.area ?: 0).toDouble() / (cropExpectedDraw?.area ?: 0).coerceAtLeast(1),
            insideSearchFraction =
                observedCard?.let {
                    it.intersectionArea(searchEnvelope).toDouble() / it.area.coerceAtLeast(1)
                } ?: 0.0,
            mappingDelta = mappingDelta,
        )
    }
}
