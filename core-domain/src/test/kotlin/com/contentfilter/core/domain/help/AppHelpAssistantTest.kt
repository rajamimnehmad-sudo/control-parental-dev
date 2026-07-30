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

    @Test
    fun `dag image failure creates only a sanitized report`() {
        val answer =
            AppHelpAssistant.answer(
                "H&M no muestra fotos y mi contraseña es secreta",
                HelpContext(audience = HelpAudience.User),
            )

        assertEquals(HelpReportCategory.DagImages, answer.report?.category)
        assertEquals(
            "DAG presentó un problema al cargar, analizar o mostrar imágenes.",
            answer.report?.safeSummary,
        )
        assertTrue(answer.report?.safeSummary?.contains("contraseña") == false)
    }

    @Test
    fun `ordinary help question is not reported`() {
        val answer =
            AppHelpAssistant.answer(
                "¿Cómo funciona DAG?",
                HelpContext(audience = HelpAudience.User),
            )

        assertEquals(null, answer.report)
    }
}
