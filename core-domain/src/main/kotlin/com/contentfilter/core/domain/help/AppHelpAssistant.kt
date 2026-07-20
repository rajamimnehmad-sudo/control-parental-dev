package com.contentfilter.core.domain.help

import java.text.Normalizer

enum class HelpAudience {
    Admin,
    User,
}

enum class HelpAction {
    Apps,
    Web,
    Security,
    Recovery,
    Settings,
}

data class HelpContext(
    val audience: HelpAudience,
    val offline: Boolean = false,
    val possibleUninstall: Boolean = false,
    val protectionNeedsAttention: Boolean = false,
    val vpnActive: Boolean = true,
    val accessibilityActive: Boolean = true,
    val uninstallProtectionActive: Boolean = true,
    val recoveryKitReady: Boolean = false,
)

data class HelpAnswer(
    val title: String,
    val body: String,
    val actionLabel: String? = null,
    val action: HelpAction? = null,
)

object AppHelpAssistant {
    fun suggestions(context: HelpContext): List<String> {
        val priority =
            buildList {
                if (context.possibleUninstall) {
                    add("¿Qué significa alerta máxima?")
                    add("¿La app se reinstala sola?")
                }
                if (!context.vpnActive) add("¿Cómo activo la protección web?")
                if (!context.accessibilityActive) add("¿Cómo activo el bloqueo de apps?")
                if (!context.uninstallProtectionActive) add("¿Cómo protejo la desinstalación?")
                if (context.offline) add("¿Qué puedo hacer sin Internet?")
                if (!context.recoveryKitReady && context.audience == HelpAudience.Admin) {
                    add("¿Cómo preparo la recuperación offline?")
                }
                add("¿Cómo funcionan Apps y Web?")
                add("¿Cómo pido ayuda al administrador?")
            }
        return priority.distinct().take(MaxSuggestions)
    }

    fun answer(
        question: String,
        context: HelpContext,
        previousAction: HelpAction? = null,
    ): HelpAnswer {
        val normalized = question.normalized()
        if (normalized.isBlank()) return welcome(context)
        return when {
            normalized.hasAny(
                "alerta maxima",
                "desinstalo",
                "desinstalada",
                "reinstala sola",
                "restablece sola",
            ) ->
                uninstallAnswer(context)
            normalized.hasAny(
                "sin internet",
                "sin conexion",
                "offline",
                "codigo",
                "recuperacion",
                "cuantas veces",
                "vuelve la conexion",
            ) ->
                recoveryAnswer(context)
            normalized.hasAny("vpn", "proteccion web", "internet", "reglas funcionan", "dag") ->
                HelpAnswer(
                    title = "Protección web",
                    body =
                        if (context.vpnActive) {
                            "La VPN de protección está activa. Las reglas Web se aplican localmente incluso durante cortes breves de Internet."
                        } else {
                            "La VPN está apagada. Abrí Web o Seguridad y activala; Android puede pedir una confirmación."
                        },
                    actionLabel = "Abrir Web",
                    action = HelpAction.Web,
                )
            normalized.hasAny("accesibilidad", "bloqueo de apps", "aplicaciones", "apps", "limites") ->
                HelpAnswer(
                    title = "Protección de aplicaciones",
                    body =
                        if (context.accessibilityActive) {
                            "El servicio de accesibilidad está activo. Los permisos, límites y horarios configurados en Apps pueden aplicarse."
                        } else {
                            "El bloqueo de apps necesita Accesibilidad activa. Abrí Seguridad y seguí el acceso directo a los ajustes de Android."
                        },
                    actionLabel = if (context.audience == HelpAudience.Admin) "Abrir Apps" else "Ver estado",
                    action = if (context.audience == HelpAudience.Admin) HelpAction.Apps else HelpAction.Security,
                )
            normalized.hasAny("seguridad", "proteccion", "barrera", "desinstalacion") ->
                HelpAnswer(
                    title = "Seguridad",
                    body =
                        if (context.protectionNeedsAttention) {
                            "Hay al menos un componente por revisar. Comprobá VPN, Accesibilidad y protección contra desinstalación en Seguridad."
                        } else {
                            "Los componentes principales informan un estado correcto. Seguridad reúne mantenimiento, recuperación y reenlace."
                        },
                    actionLabel = "Abrir Seguridad",
                    action = HelpAction.Security,
                )
            normalized.hasAny("administrador", "pedir ayuda", "solicitud", "tiempo") ->
                HelpAnswer(
                    title = "Ayuda del administrador",
                    body =
                        if (context.audience == HelpAudience.User) {
                            "Usá Solicitudes para pedir tiempo o acceso. Para reinstalar o reenlazar App Usuario necesitás un token generado por el administrador."
                        } else {
                            "Revisá Solicitudes para permisos y tiempo. Los códigos de recuperación y reenlace están separados dentro de Seguridad."
                        },
                    actionLabel = "Ver ajustes",
                    action = HelpAction.Settings,
                )
            previousAction != null && normalized.isContextualFollowUp() -> contextualFollowUp(previousAction, context)
            else -> outOfScopeAnswer()
        }
    }

    fun followUpSuggestions(
        answer: HelpAnswer,
        context: HelpContext,
    ): List<String> =
        when (answer.action) {
            HelpAction.Security ->
                listOf(
                    "¿Se restablece sola la protección?",
                    "¿Qué hago si la app fue desinstalada?",
                    "¿Cómo preparo códigos offline?",
                )
            HelpAction.Recovery ->
                listOf(
                    "¿El código funciona sin Internet?",
                    "¿Cuántas veces se puede usar?",
                    "¿Qué pasa cuando vuelve la conexión?",
                )
            HelpAction.Web ->
                listOf(
                    "¿Qué pasa si la VPN está apagada?",
                    "¿Las reglas funcionan sin Internet?",
                    "¿Qué es DAG?",
                )
            HelpAction.Apps ->
                listOf(
                    "¿Cómo funcionan los límites?",
                    "¿Por qué una app no se bloquea?",
                    "¿Qué necesita Accesibilidad?",
                )
            HelpAction.Settings,
            null,
            -> suggestions(context)
        }

    fun welcome(context: HelpContext): HelpAnswer =
        HelpAnswer(
            title = "¿En qué te ayudo?",
            body =
                if (context.protectionNeedsAttention || context.possibleUninstall) {
                    "Detecté un estado que requiere atención. Elegí una sugerencia para ver pasos concretos."
                } else {
                    "Puedo explicar funciones y guiarte usando el estado actual de la app."
                },
        )

    private fun uninstallAnswer(context: HelpContext): HelpAnswer =
        HelpAnswer(
            title = if (context.possibleUninstall) "Alerta máxima" else "Desinstalación y recuperación",
            body =
                "La app no se reinstala sola. Primero comprobá si App Usuario sigue en el teléfono. " +
                    "Si no está, reinstalá el APK oficial, generá un token de reenlace y volvé a activar VPN, " +
                    "Accesibilidad y protección contra desinstalación. Si sólo estuvo apagado o sin red, el estado se corrige al volver a reportar.",
            actionLabel = "Ver Seguridad",
            action = HelpAction.Security,
        )

    private fun recoveryAnswer(context: HelpContext): HelpAnswer =
        HelpAnswer(
            title = "Recuperación sin conexión",
            body =
                when {
                    context.audience == HelpAudience.Admin && context.recoveryKitReady ->
                        "El kit offline está preparado. Podés revelar el próximo código desde Seguridad aunque no haya Internet. Cada código se usa una sola vez."
                    context.audience == HelpAudience.Admin ->
                        "Prepará el kit mientras ambos teléfonos tengan conexión. Luego los códigos funcionarán sin Internet y se conciliarán al reconectar."
                    else ->
                        "Ingresá el código que te indique el administrador. La validación ocurre en este teléfono y no necesita Internet si el kit fue preparado antes."
                },
            actionLabel = "Abrir recuperación",
            action = HelpAction.Recovery,
        )

    private fun contextualFollowUp(
        previousAction: HelpAction,
        context: HelpContext,
    ): HelpAnswer =
        when (previousAction) {
            HelpAction.Security -> uninstallAnswer(context)
            HelpAction.Recovery -> recoveryAnswer(context)
            HelpAction.Apps -> answer("apps", context)
            HelpAction.Web -> answer("web", context)
            HelpAction.Settings -> outOfScopeAnswer()
        }

    private fun outOfScopeAnswer(): HelpAnswer =
        HelpAnswer(
            title = "Sólo puedo ayudar con Content Filter",
            body =
                "Preguntame sobre Apps, Web, DAG, protección, solicitudes, actualizaciones, instalación, reenlace o recuperación offline.",
        )

    private fun String.normalized(): String =
        Normalizer
            .normalize(lowercase(), Normalizer.Form.NFD)
            .replace(CombiningMarks, "")
            .replace(NonWords, " ")
            .trim()

    private fun String.hasAny(vararg terms: String): Boolean = terms.any(::contains)

    private fun String.isContextualFollowUp(): Boolean =
        split(' ').size <= 8 && hasAny("y si", "entonces", "eso", "cuando", "como", "por que", "que pasa", "puede")

    private val CombiningMarks = Regex("\\p{M}+")
    private val NonWords = Regex("[^a-z0-9]+")
    private const val MaxSuggestions = 5
}
