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
        private val canonicalTextPolicy: DagV2CanonicalTextPolicy,
    ) : DagV2SearchPolicy {
        override fun evaluateQuery(query: String): DagV2PolicyResult =
            when {
                query.isBlank() -> blocked("La consulta está vacía.")
                canonicalTextPolicy.classifyQuery(query).decision != DagV2CanonicalTextDecision.Allowed ->
                    blocked("La consulta no fue aprobada por la política textual canónica.")
                else -> allowed("La política textual canónica permite buscar y filtrar resultados.")
            }

        suspend fun evaluateNavigation(url: String): DagV2PolicyResult {
            val parsed = parseHttps(url) ?: return blocked("Sólo se permiten direcciones HTTPS válidas.")
            val domain = parsed.host.normalizedDomain()
            val category =
                runCatching { domainBlocklist.categoryFor(domain) }
                    .getOrElse { return blocked("No se pudo comprobar la lista local de dominios.") }
            if (category == WebDomainList.CategoryAdult || category == WebDomainList.CategoryMixedAdult) {
                return blocked("El dominio está prohibido por la lista adulta no reemplazable.")
            }
            val canonicalNavigation = canonicalTextPolicy.classifyNavigation(url)
            val canonicalDecisionComesFromDomainList =
                category != null &&
                    canonicalNavigation.decision == DagV2CanonicalTextDecision.Blocked &&
                    canonicalNavigation.category == category
            if (
                canonicalNavigation.decision != DagV2CanonicalTextDecision.Allowed &&
                !canonicalDecisionComesFromDomainList
            ) {
                return blocked("La dirección no fue aprobada por la política textual canónica.")
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
            if (category != null) return blocked("Dominio bloqueado por la lista local: $category.")
            return allowed("Navegación permitida.")
        }

        override suspend fun evaluateResult(result: DagV2SearchResult): DagV2PolicyResult {
            val navigation = evaluateNavigation(result.url)
            if (navigation.decision == DagV2SiteDecision.Block) return navigation
            return if (
                canonicalTextPolicy.classifyResult(result).decision !=
                DagV2CanonicalTextDecision.Allowed
            ) {
                blocked("El resultado no fue aprobado por la política textual canónica.")
            } else {
                allowed("Resultado permitido.")
            }
        }

        fun evaluateDocument(
            url: String,
            title: String,
            visibleText: String,
        ): DagV2PolicyResult {
            if (title.isBlank() && visibleText.isBlank()) {
                return blocked("El documento no aportó texto suficiente para aprobarlo.")
            }
            val result =
                canonicalTextPolicy.classifyPage(
                    url = url,
                    title = title,
                    visibleText = visibleText.take(MaxDocumentCharacters),
                )
            return if (result.decision == DagV2CanonicalTextDecision.Allowed) {
                allowed("Documento permitido por la política textual canónica.")
            } else {
                blocked("El documento no alcanzó una aprobación textual canónica.")
            }
        }

        fun evaluateSpaRoute(url: String): DagV2PolicyResult {
            if (parseHttps(url) == null) return blocked("La ruta SPA dejó de ser HTTPS.")
            return if (
                canonicalTextPolicy.classifyNavigation(url).decision ==
                DagV2CanonicalTextDecision.Allowed
            ) {
                allowed("Ruta SPA permitida sin repetir el análisis completo.")
            } else {
                blocked("La ruta SPA fue bloqueada por la política textual canónica.")
            }
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
            private const val MaxDocumentCharacters = 24_000

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
