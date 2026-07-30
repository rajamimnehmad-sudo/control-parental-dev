package com.contentfilter.user.help

import com.contentfilter.core.domain.help.HelpAudience
import com.contentfilter.core.domain.help.HelpContext
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GloshiaPromptPolicyTest {
    @Test
    fun `prompt grounds the model in current device state`() {
        val prompt =
            GloshiaPromptPolicy.userMessage(
                prompt = "¿Por qué no abre?",
                context =
                    HelpContext(
                        audience = HelpAudience.User,
                        offline = true,
                        vpnActive = false,
                        accessibilityActive = true,
                        uninstallProtectionActive = true,
                        dagInstalled = true,
                    ),
                reliableAnswer = "La VPN necesita activarse.",
            )

        assertContains(prompt, "conexión/sincronización: sin conexión")
        assertContains(prompt, "VPN web: inactiva")
        assertContains(prompt, "La VPN necesita activarse.")
    }

    @Test
    fun `response never echoes a password shared by the user`() {
        val result =
            GloshiaPromptPolicy.sanitizeResponse(
                response = "Entiendo. Tu contraseña es SuperSecreta99. Revisemos DAG.",
                originalPrompt = "Mi contraseña es SuperSecreta99 y DAG no abre",
            )

        assertFalse(result.contains("SuperSecreta99"))
        assertContains(result, "[dato privado]")
    }

    @Test
    fun `ordinary conversational response is preserved`() {
        val response = "¡Hola! Soy GloshIA. ¿Qué querés revisar de Content Filter?"

        assertEquals(
            response,
            GloshiaPromptPolicy.sanitizeResponse(
                response = response,
                originalPrompt = "Hola",
            ),
        )
    }
}
