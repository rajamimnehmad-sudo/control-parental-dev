package com.contentfilter.core.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PremiumFeedbackBannerTest {
    @Test
    fun `long messages remain visible longer than short messages`() {
        val shortDuration = requireNotNull(feedbackVisibleMillis("Guardado.", isError = false))
        val longDuration =
            requireNotNull(
                feedbackVisibleMillis(
                    "La configuración se guardó correctamente y se aplicará cuando el dispositivo vuelva a conectarse.",
                    isError = false,
                ),
            )

        assertTrue(longDuration > shortDuration)
    }

    @Test
    fun `long messages have a bounded duration`() {
        assertEquals(8_000L, feedbackVisibleMillis("Mensaje ".repeat(100), isError = false))
    }

    @Test
    fun `busy messages remain visible while work continues`() {
        assertNull(feedbackVisibleMillis("Sincronizando reglas...", isError = false))
    }
}
