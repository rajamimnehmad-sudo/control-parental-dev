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

enum class HelpReportCategory(
    val wireValue: String,
) {
    DagImages("dag-images"),
    DagNavigation("dag-navigation"),
    WebProtection("web-protection"),
    AppProtection("app-protection"),
    Accessibility("accessibility"),
    Updates("updates"),
    Activation("activation"),
    UninstallProtection("uninstall-protection"),
    Sync("sync"),
    Unclassified("unclassified"),
}

data class HelpReportDraft(
    val category: HelpReportCategory,
    val safeSummary: String,
)

data class HelpContext(
    val audience: HelpAudience,
    val offline: Boolean = false,
    val possibleUninstall: Boolean = false,
    val protectionNeedsAttention: Boolean = false,
    val vpnActive: Boolean = true,
    val accessibilityActive: Boolean = true,
    val uninstallProtectionActive: Boolean = true,
    val recoveryKitReady: Boolean = false,
    val dagInstalled: Boolean = true,
)

data class HelpAnswer(
    val title: String,
    val body: String,
    val actionLabel: String? = null,
    val action: HelpAction? = null,
    val report: HelpReportDraft? = null,
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
        val reportsFailure = normalized.reportsFailure()
        return when {
            normalized.isGreeting() ->
                HelpAnswer(
                    title = "¡Hola!",
                    body =
                        if (context.protectionNeedsAttention || context.possibleUninstall) {
                            "Soy la ayuda privada de Content Filter. Veo un estado que necesita atención; preguntame qué revisar o elegí una sugerencia."
                        } else {
                            "Soy la ayuda privada de Content Filter. La protección principal figura activa. ¿Qué querés revisar?"
                        },
                )
            normalized.isCapabilitiesQuestion() ->
                HelpAnswer(
                    title = "Cómo puedo ayudarte",
                    body =
                        "Puedo explicar DAG, Apps, Web, seguridad, solicitudes, activación, actualizaciones y recuperación offline. " +
                            "También uso el estado visible de la app para darte pasos concretos y preparar reportes sin copiar tu conversación.",
                )
            normalized.isThanks() ->
                HelpAnswer(
                    title = "¡De nada!",
                    body = "Cuando quieras, seguimos con otra duda sobre Content Filter o DAG.",
                )
            normalized.isAcknowledgement() ->
                HelpAnswer(
                    title = "Perfecto",
                    body = "Decime qué querés revisar y te guío paso a paso.",
                )
            normalized.hasAny(
                "dag",
                "gloshia",
                "foto",
                "fotos",
                "imagen",
                "imagenes",
                "difuminada",
                "transparente",
                "pagina",
                "paginas",
                "sitio",
                "sitios",
                "navegador",
            ) ->
                HelpAnswer(
                    title = "Navegación protegida DAG",
                    body =
                        when {
                            !context.dagInstalled ->
                                "DAG no está instalado en este teléfono. Abrí Ajustes para instalarlo y después confirmalo como navegador predeterminado."
                            normalized.hasAny("foto", "fotos", "imagen", "imagenes", "difuminada", "transparente") ->
                                "DAG mantiene cada imagen oculta mientras GloshIA la analiza. Si queda difuminada fue filtrada; si figura como no disponible hubo un formato o una carga que no pudo validarse."
                            else ->
                                "DAG es el navegador protegido. Si una página no abre, comprobá primero conexión, versión instalada y que DAG continúe como navegador predeterminado."
                        },
                    actionLabel = if (context.dagInstalled) "Abrir Web" else "Abrir Ajustes",
                    action = if (context.dagInstalled) HelpAction.Web else HelpAction.Settings,
                    report =
                        if (reportsFailure) {
                            HelpReportDraft(
                                category =
                                    if (normalized.hasAny(
                                            "foto",
                                            "fotos",
                                            "imagen",
                                            "imagenes",
                                            "difuminada",
                                            "transparente",
                                        )
                                    ) {
                                        HelpReportCategory.DagImages
                                    } else {
                                        HelpReportCategory.DagNavigation
                                    },
                                safeSummary =
                                    if (normalized.hasAny(
                                            "foto",
                                            "fotos",
                                            "imagen",
                                            "imagenes",
                                            "difuminada",
                                            "transparente",
                                        )
                                    ) {
                                        "DAG presentó un problema al cargar, analizar o mostrar imágenes."
                                    } else {
                                        "DAG presentó un problema de navegación o apertura."
                                    },
                            )
                        } else {
                            null
                        },
                )
            normalized.hasAny(
                "alerta maxima",
                "desinstalo",
                "desinstalada",
                "reinstala sola",
                "restablece sola",
            ) ->
                uninstallAnswer(context).withFailureReport(
                    reportsFailure,
                    HelpReportCategory.UninstallProtection,
                    "La protección contra desinstalación o la presencia de la app requiere revisión.",
                )
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
            normalized.hasAny("vpn", "proteccion web", "internet", "reglas funcionan") ->
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
                    report =
                        reportsFailure.report(
                            HelpReportCategory.WebProtection,
                            "La protección web o la VPN presentó un problema.",
                        ),
                )
            normalized.hasAny("accesibilidad") ->
                HelpAnswer(
                    title = "Accesibilidad",
                    body =
                        if (context.accessibilityActive) {
                            "Accesibilidad está activa. Si una app no responde a la regla, revisá su permiso y el horario configurado."
                        } else {
                            "Accesibilidad está apagada. Abrí Seguridad y activá “Usar Content Filter” en los ajustes de Android."
                        },
                    actionLabel = "Ver estado",
                    action = HelpAction.Security,
                    report =
                        reportsFailure.report(
                            HelpReportCategory.Accessibility,
                            "Accesibilidad o el bloqueo asociado presentó un problema.",
                        ),
                )
            normalized.hasAny("bloqueo de apps", "aplicaciones", "apps", "limites") ->
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
                    report =
                        reportsFailure.report(
                            HelpReportCategory.AppProtection,
                            "Una regla, límite o bloqueo de aplicaciones no funcionó como se esperaba.",
                        ),
                )
            normalized.hasAny("actualizacion", "actualizar", "version", "instalar apk", "descarga") ->
                HelpAnswer(
                    title = "Actualizaciones",
                    body = "Abrí Ajustes para comprobar la versión. Android puede pedir permiso para instalar y una confirmación final.",
                    actionLabel = "Abrir Ajustes",
                    action = HelpAction.Settings,
                    report =
                        reportsFailure.report(
                            HelpReportCategory.Updates,
                            "La descarga, instalación o comprobación de una actualización presentó un problema.",
                        ),
                )
            normalized.hasAny("activar", "activacion", "enlace", "token", "licencia") ->
                HelpAnswer(
                    title = "Activación y enlace",
                    body = "La activación necesita un token vigente del administrador. La licencia y el enlace se actualizan al sincronizar.",
                    actionLabel = "Abrir Ajustes",
                    action = HelpAction.Settings,
                    report =
                        reportsFailure.report(
                            HelpReportCategory.Activation,
                            "La activación, licencia o enlace del dispositivo presentó un problema.",
                        ),
                )
            normalized.hasAny("sincroniza", "sincronizacion", "no llega", "no aparece") ->
                HelpAnswer(
                    title = "Sincronización",
                    body =
                        if (context.offline) {
                            "El teléfono está sin sincronización. La protección local continúa y los cambios remotos se retomarán cuando vuelva la conexión."
                        } else {
                            "La sincronización informa un estado activo. Si un cambio no aparece, abrí nuevamente la pantalla para forzar una actualización."
                        },
                    report =
                        reportsFailure.report(
                            HelpReportCategory.Sync,
                            "Un cambio o estado no se sincronizó como se esperaba.",
                        ),
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
            else ->
                outOfScopeAnswer().copy(
                    report =
                        if (reportsFailure) {
                            HelpReportDraft(
                                HelpReportCategory.Unclassified,
                                "El asistente recibió un problema de Content Filter que no pudo clasificar.",
                            )
                        } else {
                            null
                        },
                )
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
                    "¿Cómo funciona la navegación protegida?",
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
                "Preguntame sobre Apps, Web, protección, solicitudes, actualizaciones, instalación, reenlace o recuperación offline.",
        )

    private fun HelpAnswer.withFailureReport(
        reportsFailure: Boolean,
        category: HelpReportCategory,
        safeSummary: String,
    ): HelpAnswer = copy(report = reportsFailure.report(category, safeSummary))

    private fun Boolean.report(
        category: HelpReportCategory,
        safeSummary: String,
    ): HelpReportDraft? = if (this) HelpReportDraft(category, safeSummary) else null

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

    private fun String.isCapabilitiesQuestion(): Boolean =
        hasAny(
            "que podes hacer",
            "en que ayudas",
            "como me ayudas",
            "quien sos",
            "para que servis",
        )

    private fun String.isThanks(): Boolean =
        split(' ').size <= MaxAcknowledgementWords && hasAny("gracias", "muchas gracias", "te agradezco")

    private fun String.isAcknowledgement(): Boolean = this in Acknowledgements

    private fun String.isContextualFollowUp(): Boolean =
        split(' ').size <= 8 && hasAny("y si", "entonces", "eso", "cuando", "como", "por que", "que pasa", "puede")

    private fun String.reportsFailure(): Boolean =
        hasAny(
            "error",
            "falla",
            "fallo",
            "no funciona",
            "no abre",
            "no me abre",
            "no carga",
            "no aparece",
            "no muestra",
            "se cierra",
            "se traba",
            "queda transparente",
            "problema",
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
    private const val MaxGreetingWords = 5
    private const val MaxAcknowledgementWords = 5
    private const val MaxSuggestions = 5
}
