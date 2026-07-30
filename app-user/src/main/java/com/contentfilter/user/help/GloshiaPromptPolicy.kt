package com.contentfilter.user.help

import com.contentfilter.core.domain.help.HelpContext

internal object GloshiaPromptPolicy {
    val systemInstruction =
        """
        Sos GloshIA Ayuda, el asistente privado de la app Content Filter para el teléfono del usuario.
        Respondé siempre en español rioplatense, con tono cálido, claro y breve.
        Conversá naturalmente: saludá, entendé repreguntas y pedí una sola aclaración cuando falte información.
        Limitate a Content Filter, Glosh, DAG, protección de aplicaciones, Internet, activación, actualizaciones y configuración.
        No inventes funciones, estados ni acciones realizadas. Usá como verdad el estado y la respuesta base entregados con cada mensaje.
        Nunca pidas ni repitas contraseñas, códigos, tokens, fotos privadas, búsquedas ni información íntima.
        Si el usuario comparte un secreto, indicá brevemente que no debe compartirlo y seguí ayudando sin repetirlo.
        No des instrucciones para desactivar, evadir o desinstalar la protección.
        Si el tema no pertenece al proyecto, explicá amablemente que solo ayudás con Content Filter.
        No uses markdown complejo. Respondé en uno o dos párrafos cortos.
        """.trimIndent()

    fun userMessage(
        prompt: String,
        context: HelpContext,
        reliableAnswer: String,
    ): String =
        """
        Estado confiable del teléfono:
        - conexión/sincronización: ${if (context.offline) "sin conexión" else "activa"}
        - VPN web: ${context.vpnActive.onOff()}
        - Accesibilidad: ${context.accessibilityActive.onOff()}
        - protección contra desinstalación: ${context.uninstallProtectionActive.onOff()}
        - DAG instalado: ${context.dagInstalled.yesNo()}
        - protección requiere atención: ${context.protectionNeedsAttention.yesNo()}

        Guía técnica confiable para esta consulta:
        $reliableAnswer

        Mensaje del usuario:
        $prompt
        """.trimIndent()

    fun sanitizeResponse(
        response: String,
        originalPrompt: String,
    ): String {
        var sanitized = response.trim().take(MaxResponseLength)
        sensitiveValues(originalPrompt).forEach { value ->
            if (value.length >= MinSensitiveValueLength) {
                sanitized = sanitized.replace(value, "[dato privado]", ignoreCase = true)
            }
        }
        return sanitized
            .replace(SecretEchoPattern, "$1 [dato privado]")
            .trim()
    }

    private fun sensitiveValues(prompt: String): Set<String> =
        SecretInputPattern
            .findAll(prompt)
            .mapNotNull { match -> match.groupValues.getOrNull(2)?.trim()?.takeIf(String::isNotBlank) }
            .toSet()

    private fun Boolean.onOff() = if (this) "activa" else "inactiva"

    private fun Boolean.yesNo() = if (this) "sí" else "no"

    private val SecretInputPattern =
        Regex(
            """(?i)\b(contrase(?:ñ|n)a|clave|password|token|c[oó]digo)\s*(?:es|:|=)?\s*([^\s,;.]{4,})""",
        )
    private val SecretEchoPattern =
        Regex(
            """(?i)\b(contrase(?:ñ|n)a|clave|password|token|c[oó]digo)\s*(?:es|:|=)?\s*[^\s,;.]{4,}""",
        )
    private const val MinSensitiveValueLength = 4
    private const val MaxResponseLength = 900
}
