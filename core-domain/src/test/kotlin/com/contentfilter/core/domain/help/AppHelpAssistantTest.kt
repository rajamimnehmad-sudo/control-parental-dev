package com.contentfilter.core.domain.help

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class AppHelpAssistantTest {
    @Test
    fun `critical state puts uninstall questions first`() {
        val suggestions =
            AppHelpAssistant.suggestions(
                HelpContext(audience = HelpAudience.Admin, possibleUninstall = true),
            )

        assertEquals("¿Qué significa alerta máxima?", suggestions.first())
        assertTrue(suggestions.size <= 5)
    }

    @Test
    fun `recovery answer works without accents`() {
        val answer =
            AppHelpAssistant.answer(
                "como preparo recuperacion sin conexion",
                HelpContext(audience = HelpAudience.Admin, offline = true),
            )

        assertEquals(HelpAction.Recovery, answer.action)
    }

    @Test
    fun `out of scope question is refused`() {
        val answer =
            AppHelpAssistant.answer(
                "¿Quién ganó el partido?",
                HelpContext(audience = HelpAudience.User),
            )

        assertEquals("Sólo puedo ayudar con Content Filter", answer.title)
    }
}
