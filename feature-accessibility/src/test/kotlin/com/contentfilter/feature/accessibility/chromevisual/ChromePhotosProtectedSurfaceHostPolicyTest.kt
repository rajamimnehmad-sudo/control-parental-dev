package com.contentfilter.feature.accessibility.chromevisual

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChromePhotosProtectedSurfaceHostPolicyTest {
    @Test
    fun `diagnostic marker is off by default and requires explicit dev gate`() {
        ChromePhotosProtectedSurfaceDiagnostics.setMarkerEnabledForExplicitDevGate(false)
        assertEquals(false, ChromePhotosProtectedSurfaceDiagnostics.isMarkerEnabled())

        ChromePhotosProtectedSurfaceDiagnostics.setMarkerEnabledForExplicitDevGate(true)
        assertEquals(true, ChromePhotosProtectedSurfaceDiagnostics.isMarkerEnabled())

        ChromePhotosProtectedSurfaceDiagnostics.setMarkerEnabledForExplicitDevGate(false)
        assertEquals(false, ChromePhotosProtectedSurfaceDiagnostics.isMarkerEnabled())
    }

    @Test
    fun `rotation keeps one square host extent without relayout`() {
        val portrait = ChromeVisualViewport(0, 66, 1_080, 2_408)
        val landscape = ChromeVisualViewport(66, 0, 2_408, 1_080)

        val portraitExtent =
            ChromePhotosProtectedSurfaceHostPolicy.requiredExtent(
                viewport = portrait,
                displayWidth = 1_080,
                displayHeight = 2_408,
            )
        val landscapeExtent =
            ChromePhotosProtectedSurfaceHostPolicy.requiredExtent(
                viewport = landscape,
                displayWidth = 1_080,
                displayHeight = 2_408,
            )

        assertEquals(2_408, portraitExtent)
        assertEquals(portraitExtent, landscapeExtent)
    }

    @Test
    fun `host extent does not shrink when input method clips viewport`() {
        val clippedPortrait = ChromeVisualViewport(0, 66, 1_080, 1_500)

        assertEquals(
            2_408,
            ChromePhotosProtectedSurfaceHostPolicy.requiredExtent(
                viewport = clippedPortrait,
                displayWidth = 1_080,
                displayHeight = 2_408,
            ),
        )
    }

    @Test
    fun `evidence marker lattice remains measurable in a rotated visible crop`() {
        val projectedLineWidth =
            ChromePhotosProtectedSurfaceHostPolicy.MarkerLineWidthPx *
                RecordingWidth / SourceLongEdge
        val projectedViewportEdge = MinimumChromeViewportEdge * RecordingWidth / SourceLongEdge

        assertTrue(projectedLineWidth * projectedViewportEdge >= RequiredMarkerPixels)
        assertEquals(
            112f,
            ChromePhotosProtectedSurfaceHostPolicy.MarkerPitchPx -
                ChromePhotosProtectedSurfaceHostPolicy.MarkerLineWidthPx,
        )
    }

    private companion object {
        const val RecordingWidth = 720f
        const val SourceLongEdge = 2_408f
        const val MinimumChromeViewportEdge = 1_080f
        const val RequiredMarkerPixels = 120f
    }
}
