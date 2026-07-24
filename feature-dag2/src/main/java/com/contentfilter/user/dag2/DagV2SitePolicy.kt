package com.contentfilter.user.dag2

import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.PolicySchedulePolicy.isAllowedWindow
import com.contentfilter.core.domain.model.PolicySnapshot
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.model.TimePolicyContext
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.feature.vpn.domainlist.DynamicDomainBlocklist
import com.contentfilter.feature.vpn.domainlist.WebDomainList
import java.net.URI
import java.text.Normalizer
import java.time.ZonedDateTime
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DagV2SitePolicy
    @Inject
    constructor(
        private val policyRepository: PolicyRepository,
        private val domainBlocklist: DynamicDomainBlocklist,
    ) : DagV2SearchPolicy {
        override fun evaluateQuery(query: String): DagV2PolicyResult =
            when {
                query.isBlank() -> blocked("La consulta está vacía.")
                containsAdultSignal(query) -> blocked("La consulta fue bloqueada por la política adulta.")
                else -> allowed("Consulta local permitida.")
            }

        suspend fun evaluateNavigation(url: String): DagV2PolicyResult {
            val parsed = parseHttps(url) ?: return blocked("Sólo se permiten direcciones HTTPS válidas.")
            val domain = parsed.host.normalizedDomain()
            if (isNonNegotiableAdult(domain, url)) {
                return blocked("El dominio o la dirección están prohibidos por la política adulta.")
            }
            val snapshot =
                runCatching { policyRepository.getActivePolicy() }
                    .getOrElse { return blocked("No se pudo comprobar la política administrativa.") }
            explicitDomainRule(snapshot, domain)?.let { rule ->
                return when (rule.action) {
                    RuleAction.Allow -> allowed("Permitido por una regla administrativa explícita.")
                    RuleAction.Block -> blocked("Bloqueado por una regla administrativa explícita.")
                    RuleAction.Warn ->
                        blocked(
                            "La regla administrativa requiere una advertencia no disponible en el laboratorio.",
                        )
                    RuleAction.RequestAuthorization -> blocked("La regla administrativa requiere autorización.")
                }
            }
            val category =
                runCatching { domainBlocklist.categoryFor(domain) }
                    .getOrElse { return blocked("No se pudo comprobar la lista local de dominios.") }
            if (category != null) return blocked("Dominio bloqueado por la lista local: $category.")
            if (containsAdultSignal(parsed.path.orEmpty()) || containsAdultSignal(parsed.query.orEmpty())) {
                return blocked("La ruta fue bloqueada por la política adulta.")
            }
            return allowed("Navegación permitida.")
        }

        override suspend fun evaluateResult(result: DagV2SearchResult): DagV2PolicyResult {
            val navigation = evaluateNavigation(result.url)
            if (navigation.decision == DagV2SiteDecision.Block) return navigation
            return if (containsAdultSignal("${result.title} ${result.description}")) {
                blocked("El resultado fue bloqueado por su texto.")
            } else {
                allowed("Resultado permitido.")
            }
        }

        fun evaluateDocument(
            url: String,
            title: String,
            visibleText: String,
        ): DagV2PolicyResult {
            val evidence = "$title ${visibleText.take(MaxDocumentCharacters)}".trim()
            return if (evidence.isBlank()) {
                blocked("El documento no aportó texto suficiente para aprobarlo.")
            } else if (containsAdultSignal("$url $evidence")) {
                blocked("La página contiene señales adultas inequívocas.")
            } else {
                allowed("Documento permitido.")
            }
        }

        fun evaluateSpaRoute(url: String): DagV2PolicyResult {
            val parsed = parseHttps(url) ?: return blocked("La ruta SPA dejó de ser HTTPS.")
            val domain = parsed.host.normalizedDomain()
            return if (
                isNonNegotiableAdult(domain, url) ||
                containsAdultSignal(parsed.path.orEmpty()) ||
                containsAdultSignal(parsed.query.orEmpty())
            ) {
                blocked("La ruta SPA fue bloqueada por la política adulta.")
            } else {
                allowed("Ruta SPA permitida sin repetir el análisis completo.")
            }
        }

        private fun isNonNegotiableAdult(
            domain: String,
            rawUrl: String,
        ): Boolean {
            if (domain == ControlledAdultFixtureDomain) return true
            if (AdultDomainLabels.any { label -> domain == label || domain.endsWith(".$label") }) return true
            val category = runCatching { domainBlocklist.categoryFor(domain) }.getOrNull()
            if (category == WebDomainList.CategoryAdult || category == WebDomainList.CategoryMixedAdult) return true
            return containsAdultSignal(rawUrl)
        }

        private fun explicitDomainRule(
            snapshot: PolicySnapshot,
            domain: String,
        ): PolicyRule? {
            val now = ZonedDateTime.now()
            val time =
                TimePolicyContext(
                    evaluatedAtEpochMillis = now.toInstant().toEpochMilli(),
                    minuteOfDay = now.hour * 60 + now.minute,
                    isoDayOfWeek = now.dayOfWeek.value,
                )
            return snapshot.rules
                .asSequence()
                .filter { it.enabled && it.scope == RuleScope.Domain }
                .filterNot { it.isAllowedWindow() }
                .filterNot { it.id.startsWith("safe-default-") }
                .filter { !it.target.startsWith("__") && it.target != "*" }
                .filter { it.activeWindow?.contains(time, it.activeDaysMask) != false }
                .filter { domain.matchesDomain(it.target.normalizedDomain()) }
                .sortedWith(
                    compareByDescending<PolicyRule> { it.level.specificity }
                        .thenByDescending { it.priority },
                ).firstOrNull()
        }

        companion object {
            const val ControlledAdultFixtureDomain = "adult.test"
            private const val MaxDocumentCharacters = 24_000
            private val AdultDomainLabels =
                setOf(
                    "pornhub.com",
                    "xvideos.com",
                    "xnxx.com",
                    "redtube.com",
                    "youporn.com",
                    "imgsrc.ru",
                )
            private val AdultPhrases =
                setOf(
                    "porn",
                    "porno",
                    "pornografia",
                    "pornography",
                    "xxx",
                    "nude",
                    "nudes",
                    "nudity",
                    "desnudo",
                    "desnuda",
                    "desnudez",
                    "sexo explicito",
                    "explicit sex",
                    "adult video",
                    "videos adultos",
                    "escort sexual",
                    "prostitucion",
                    "hookup sexual",
                    "פורנו",
                    "פורנוגרפיה",
                    "עירום",
                )

            fun containsAdultSignal(value: String): Boolean {
                val normalized =
                    Normalizer
                        .normalize(value, Normalizer.Form.NFD)
                        .replace(Regex("\\p{M}+"), "")
                        .lowercase(Locale.ROOT)
                        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                        .trim()
                if (normalized.isBlank()) return false
                val padded = " $normalized "
                return AdultPhrases.any { phrase -> padded.contains(" $phrase ") }
            }

            private fun parseHttps(url: String): URI? =
                runCatching { URI(url) }
                    .getOrNull()
                    ?.takeIf { it.scheme.equals("https", true) && !it.host.isNullOrBlank() }

            private fun String.normalizedDomain(): String =
                lowercase(Locale.ROOT).removePrefix("www.").removeSuffix(".")

            private fun String.matchesDomain(target: String): Boolean = this == target || endsWith(".$target")

            private fun allowed(reason: String) = DagV2PolicyResult(DagV2SiteDecision.Allow, reason)

            private fun blocked(reason: String) = DagV2PolicyResult(DagV2SiteDecision.Block, reason)
        }
    }
