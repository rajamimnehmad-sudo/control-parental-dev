package com.contentfilter.feature.accessibility.chromevisual

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChromeMediaShieldAccessibilityTokenScannerTest {
    private val scanner = ChromeMediaShieldAccessibilityTokenScanner()

    @Test
    fun `strict ready marker returns only bounded token and positive lifecycle`() {
        assertEquals(
            ChromeMediaShieldReadyMarker(Token, 7L),
            scanner.readyMarkerOrNull("glosh-shield-ready:$Token:7"),
        )
        assertNull(scanner.readyMarkerOrNull("prefix glosh-shield-ready:$Token:7"))
        assertNull(scanner.readyMarkerOrNull("glosh-shield-ready:short:7"))
        assertNull(scanner.readyMarkerOrNull("glosh-shield-ready:$Token!:7"))
        assertNull(scanner.readyMarkerOrNull("glosh-shield-ready:$Token:0"))
        assertNull(scanner.readyMarkerOrNull("glosh-shield-ready:$Token:not-a-number"))
    }

    @Test
    fun `same token in both node fields is one identity while conflicting fields stay ambiguous`() {
        val marker = ChromeMediaShieldReadyMarker(Token, 1L)
        val otherMarker = ChromeMediaShieldReadyMarker(OtherToken, 2L)
        val label = "glosh-shield-ready:$Token:1"

        assertEquals(listOf(marker), scanner.markersForNodeFields(label, label))
        assertEquals(
            listOf(marker, otherMarker),
            scanner.markersForNodeFields(label, "glosh-shield-ready:$OtherToken:2"),
        )
    }

    private companion object {
        const val Token = "AAAAAAAAAAAAAAAAAAAAAA"
        const val OtherToken = "BBBBBBBBBBBBBBBBBBBBBB"
    }
}
