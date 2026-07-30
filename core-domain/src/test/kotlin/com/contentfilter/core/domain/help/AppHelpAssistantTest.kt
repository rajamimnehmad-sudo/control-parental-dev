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

    @Test
    fun `page navigation failure is diagnosed as DAG`() {
        val answer =
            AppHelpAssistant.answer(
                "No me abre una página",
                HelpContext(audience = HelpAudience.User, dagInstalled = true),
            )

        assertEquals("Navegación protegida DAG", answer.title)
        assertEquals(HelpAction.Web, answer.action)
        assertEquals(HelpReportCategory.DagNavigation, answer.report?.category)
    }

    @Test
    fun `greeting responds conversationally without creating a report`() {
        val answer =
            AppHelpAssistant.answer(
                "Hola, ¿cómo estás?",
                HelpContext(audience = HelpAudience.Admin),
            )

        assertEquals("¡Hola!", answer.title)
        assertEquals(null, answer.report)
    }

    @Test
    fun `capabilities explain safe reporting`() {
        val answer =
            AppHelpAssistant.answer(
                "¿Qué podés hacer?",
                HelpContext(audience = HelpAudience.User),
            )

        assertEquals("Cómo puedo ayudarte", answer.title)
        assertTrue(answer.body.contains("sin copiar tu conversación"))
    }
}
