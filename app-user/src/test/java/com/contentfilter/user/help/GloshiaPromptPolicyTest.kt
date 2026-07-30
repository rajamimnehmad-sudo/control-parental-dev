package com.contentfilter.user.help

import com.contentfilter.core.domain.help.HelpAudience
import com.contentfilter.core.domain.help.HelpContext
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

    @Test
    fun `greeting receives a useful local answer without model inference`() {
        val response =
            GloshiaPromptPolicy.directResponse(
                prompt = "Hola",
                context =
                    HelpContext(
                        audience = HelpAudience.User,
                        protectionNeedsAttention = true,
                    ),
            )

        assertContains(response.orEmpty(), "Soy GloshIA")
        assertContains(response.orEmpty(), "necesita atención")
    }

    @Test
    fun `capability question explains the project scope`() {
        val response =
            GloshiaPromptPolicy.directResponse(
                prompt = "Hola, ¿qué podés hacer?",
                context = HelpContext(audience = HelpAudience.User),
            )

        assertContains(response.orEmpty(), "DAG")
        assertContains(response.orEmpty(), "bloqueo de apps")
        assertContains(response.orEmpty(), "sin enviar datos privados")
    }

    @Test
    fun `known tiny model confusion is rejected in favor of reliable fallback`() {
        val response =
            GloshiaPromptPolicy.sanitizeResponse(
                response = "No hay un usuario con el nombre de hola. Podrías verificar la información.",
                originalPrompt = "hola",
            )

        assertTrue(response.isBlank())
    }

    @Test
    fun `repetitive model answer is rejected in favor of reliable fallback`() {
        val response =
            GloshiaPromptPolicy.sanitizeResponse(
                response =
                    "¿Estás buscando cómo configurar DAG? " +
                        "¿Estás buscando cómo configurar DAG? " +
                        "¿Estás buscando cómo configurar DAG? " +
                        "¿Estás buscando cómo configurar DAG?",
                originalPrompt = "No abre una página",
            )

        assertTrue(response.isBlank())
    }

    @Test
    fun `ungrounded model answer is rejected in favor of reliable fallback`() {
        val response =
            GloshiaPromptPolicy.sanitizeResponse(
                response = "Hola, ¿me puedes ayudar con algo? No entiendo cómo puedo abrir una página en DAG.",
                originalPrompt = "No me abre una página",
                reliableAnswer =
                    "DAG es el navegador protegido. Comprobá conexión, versión instalada y que continúe como navegador predeterminado.",
            )

        assertTrue(response.isBlank())
    }

    @Test
    fun `grounded concise model answer is accepted`() {
        val response =
            GloshiaPromptPolicy.sanitizeResponse(
                response = "Comprobá la conexión y que DAG siga configurado como navegador predeterminado.",
                originalPrompt = "No me abre una página",
                reliableAnswer =
                    "DAG es el navegador protegido. Comprobá conexión, versión instalada y que continúe como navegador predeterminado.",
            )

        assertContains(response, "conexión")
        assertContains(response, "navegador predeterminado")
    }
}
