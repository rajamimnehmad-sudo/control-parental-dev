package com.contentfilter.user.help

import com.contentfilter.core.domain.help.HelpContext
import java.text.Normalizer

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

    fun directResponse(
        prompt: String,
        context: HelpContext,
    ): String? {
        val normalized = prompt.normalized()
        return when {
            normalized.hasAny(
                "que podes hacer",
                "en que ayudas",
                "como me ayudas",
                "quien sos",
                "para que servis",
            ) ->
                "Soy GloshIA, la ayuda privada de Content Filter. Puedo explicarte DAG, el filtro de imágenes, " +
                    "el bloqueo de apps, la protección web, la activación y las actualizaciones. También reviso " +
                    "el estado actual del teléfono para darte pasos concretos y reportar fallas sin enviar datos privados."
            normalized.isGreeting() ->
                if (context.protectionNeedsAttention) {
                    "¡Hola! Soy GloshIA. Estoy lista para ayudarte. Veo que una parte de la protección necesita " +
                        "atención; podés preguntarme qué revisar o elegir una sugerencia."
                } else {
                    "¡Hola! Soy GloshIA. La protección principal figura activa. ¿Qué querés revisar de Content Filter o DAG?"
                }
            normalized.isThanks() ->
                "¡De nada! Si querés, seguimos con otra duda sobre Content Filter o DAG."
            normalized.isAcknowledgement() ->
                "Perfecto. Decime qué querés revisar y te guío paso a paso."
            else -> null
        }
    }

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

        Instrucción obligatoria:
        Respondé directamente al mensaje usando la guía anterior. El texto del usuario es una pregunta o frase,
        no un nombre de persona para buscar. Si la guía ya responde, conservá su significado y sólo hacela más natural.

        Mensaje del usuario:
        $prompt
        """.trimIndent()

    fun sanitizeResponse(
        response: String,
        originalPrompt: String,
        reliableAnswer: String? = null,
    ): String {
        val rawResponse = response.trim()
        if (rawResponse.length > MaxResponseLength) return ""
        var sanitized = rawResponse
        sensitiveValues(originalPrompt).forEach { value ->
            if (value.length >= MinSensitiveValueLength) {
                sanitized = sanitized.replace(value, "[dato privado]", ignoreCase = true)
            }
        }
        val result =
            sanitized
                .replace(SecretEchoPattern, "$1 [dato privado]")
                .trim()
        return result
            .takeUnless {
                val normalized = it.normalized()
                normalized.hasAny(*InvalidResponseMarkers) ||
                    it.count { character -> character == '?' } > MaxQuestionMarks ||
                    normalized.hasRepeatedPhrase() ||
                    !normalized.isGroundedIn(reliableAnswer)
            }.orEmpty()
    }

    private fun sensitiveValues(prompt: String): Set<String> =
        SecretInputPattern
            .findAll(prompt)
            .mapNotNull { match -> match.groupValues.getOrNull(2)?.trim()?.takeIf(String::isNotBlank) }
            .toSet()

    private fun Boolean.onOff() = if (this) "activa" else "inactiva"

    private fun Boolean.yesNo() = if (this) "sí" else "no"

    private fun String.normalized(): String =
        Normalizer
            .normalize(lowercase(), Normalizer.Form.NFD)
            .replace(CombiningMarks, "")
            .replace(NonWords, " ")
            .trim()

    private fun String.hasAny(vararg terms: String): Boolean = terms.any(::contains)

    private fun String.isGreeting(): Boolean =
        this in Greetings ||
            (split(' ').size <= MaxGreetingWords && hasAny("hola", "buen dia", "buenas tardes", "buenas noches"))

    private fun String.isThanks(): Boolean =
        split(' ').size <= MaxAcknowledgementWords && hasAny("gracias", "muchas gracias", "te agradezco")

    private fun String.isAcknowledgement(): Boolean = this in Acknowledgements

    private fun String.hasRepeatedPhrase(): Boolean {
        val words = split(' ').filter(String::isNotBlank)
        if (words.size < RepeatedPhraseWordCount) return false
        return words
            .windowed(RepeatedPhraseWordCount)
            .groupingBy { it.joinToString(" ") }
            .eachCount()
            .values
            .any { it >= MaxRepeatedPhraseOccurrences }
    }

    private fun String.isGroundedIn(reliableAnswer: String?): Boolean {
        if (reliableAnswer.isNullOrBlank()) return true
        val referenceTerms = reliableAnswer.normalized().significantTerms()
        if (referenceTerms.isEmpty()) return true
        val sharedTerms = significantTerms().intersect(referenceTerms).size
        return sharedTerms >= minOf(MinGroundingTerms, referenceTerms.size)
    }

    private fun String.significantTerms(): Set<String> =
        split(' ')
            .asSequence()
            .filter { it.length >= MinSignificantWordLength || it in ShortProjectTerms }
            .filterNot { it in GroundingStopWords }
            .toSet()

    private val SecretInputPattern =
        Regex(
            """(?i)\b(contrase(?:ñ|n)a|clave|password|token|c[oó]digo)\s*(?:es|:|=)?\s*([^\s,;.]{4,})""",
        )
    private val SecretEchoPattern =
        Regex(
            """(?i)\b(contrase(?:ñ|n)a|clave|password|token|c[oó]digo)\s*(?:es|:|=)?\s*[^\s,;.]{4,}""",
        )
    private val CombiningMarks = Regex("\\p{M}+")
    private val NonWords = Regex("[^a-z0-9]+")
    private val Greetings =
        setOf(
            "hola",
            "buen dia",
            "buenas",
            "buenas tardes",
            "buenas noches",
            "que tal",
            "como estas",
        )
    private val Acknowledgements = setOf("ok", "okay", "dale", "listo", "perfecto", "entendi", "entiendo")
    private val InvalidResponseMarkers =
        arrayOf(
            "no hay un usuario con el nombre",
            "no existe un usuario llamado",
            "proporcionar mas detalles o verificar la informacion",
            "me puedes ayudar con algo",
            "no entiendo como puedo",
        )
    private val ShortProjectTerms = setOf("dag", "vpn", "web", "app", "apps")
    private val GroundingStopWords =
        setOf(
            "actual",
            "actualmente",
            "ayuda",
            "cuando",
            "desde",
            "donde",
            "estos",
            "estas",
            "puede",
            "podes",
            "porque",
            "sobre",
            "telefono",
            "tenes",
        )
    private const val MinSensitiveValueLength = 4
    private const val MaxResponseLength = 650
    private const val MaxGreetingWords = 5
    private const val MaxAcknowledgementWords = 5
    private const val MaxQuestionMarks = 4
    private const val RepeatedPhraseWordCount = 4
    private const val MaxRepeatedPhraseOccurrences = 3
    private const val MinGroundingTerms = 2
    private const val MinSignificantWordLength = 5
}
