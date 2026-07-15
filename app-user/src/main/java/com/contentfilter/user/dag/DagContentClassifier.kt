package com.contentfilter.user.dag

import com.contentfilter.feature.vpn.domainlist.DynamicDomainBlocklist
import java.net.URI
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

class DagContentClassifier
    @Inject
    constructor(
        private val domainBlocklist: DynamicDomainBlocklist,
        private val semanticClassifier: DagSemanticTextClassifier,
    ) {
        @Inject
        lateinit var neuralClassifier: DagNeuralTextClassifier

        fun classifyQuery(text: String): DagClassificationResult = classify(text, directUrl = false)

        fun classifyResult(
            title: String,
            description: String,
            url: String,
        ): DagClassificationResult {
            val domain = domainFrom(url)
            blockedVisualPlatform(domain)?.let { return it }
            domainBlocklist.categoryFor(domain)?.let { category ->
                return DagClassificationResult(
                    decision = DagClassification.Blocked,
                    category = category,
                    confidence = 1f,
                    modelVersion = ModelVersion,
                )
            }
            return classify("$domain $title $description", directUrl = false)
        }

        internal fun classifyPage(
            url: String,
            title: String,
            text: String,
            images: DagImagePageSummary = DagImagePageSummary(0, 0, 0),
        ): DagClassificationResult {
            val domain = domainFrom(url)
            blockedVisualPlatform(domain)?.let { return it }
            domainBlocklist.categoryFor(domain)?.let { category ->
                return DagClassificationResult(
                    decision = DagClassification.Blocked,
                    category = category,
                    confidence = 1f,
                    modelVersion = ModelVersion,
                )
            }
            val textResult = classify("$domain $title ${text.take(MaxPageCharacters)}", directUrl = false)
            if (textResult.decision != DagClassification.Allowed) return textResult
            return when {
                images.blocked >= MinimumRiskyImages && images.blocked * 2 >= images.classified ->
                    blocked("unsafe_images", confidence = 0.95f)
                else -> textResult
            }
        }

        fun classifyDirectUrl(url: String): DagClassificationResult {
            if (url.isSearchPortal()) return blocked("search_portal", confidence = 1f)
            val domain = domainFrom(url)
            blockedVisualPlatform(domain)?.let { return it }
            domainBlocklist.categoryFor(domain)?.let { category ->
                return DagClassificationResult(
                    decision = DagClassification.Blocked,
                    category = category,
                    confidence = 1f,
                    modelVersion = ModelVersion,
                )
            }
            return classify(domain, directUrl = true)
        }

        private fun blockedVisualPlatform(domain: String): DagClassificationResult? =
            NonOverridableVisualDomains
                .firstOrNull { domain == it || domain.endsWith(".$it") }
                ?.let { blocked("unsafe_visual_platform", confidence = 1f) }

        private fun classify(
            rawText: String,
            directUrl: Boolean,
        ): DagClassificationResult {
            val text = rawText.normalizedForDag()
            if (text.isBlank()) return uncertain("empty", confidence = 1f)
            val explicit =
                ExplicitCategories.firstOrNull {
                        (_, terms) ->
                    terms.any { term -> term.matches(text) }
                }
            val contextPresent = ContextTerms.any { term -> term.matches(text) }
            val ambiguous =
                AmbiguousCategories.firstOrNull {
                        (_, terms) ->
                    terms.any { term -> term.matches(text) }
                }
            return when {
                explicit != null && contextPresent -> uncertain("${explicit.first}_context", confidence = 0.72f)
                explicit != null -> blocked(explicit.first, confidence = 0.97f)
                ambiguous != null -> uncertain(ambiguous.first, confidence = 0.68f)
                directUrl && !text.contains('.') -> uncertain("invalid_domain", confidence = 1f)
                directUrl -> allowed()
                else -> classifySemantically(rawText)
            }
        }

        private fun classifySemantically(rawText: String): DagClassificationResult {
            val semantic =
                neuralClassifierOrNull()?.classify(rawText.take(MaxNeuralCharacters))
                    ?: semanticClassifier.classify(rawText.take(MaxSemanticCharacters))
            return when (dagSemanticDecision(semantic)) {
                DagClassification.Blocked ->
                    blocked(
                        "semantic_${semantic?.category}",
                        semantic?.confidence ?: 1f,
                        semantic?.modelVersion ?: ModelVersion,
                    )
                DagClassification.Uncertain ->
                    uncertain(
                        if (semantic == null) "model_unavailable" else "semantic_${semantic.category}",
                        semantic?.confidence ?: 1f,
                        semantic?.modelVersion ?: ModelVersion,
                    )
                DagClassification.Allowed -> allowed(semantic?.modelVersion ?: ModelVersion)
            }
        }

        private fun neuralClassifierOrNull(): DagNeuralTextClassifier? =
            if (::neuralClassifier.isInitialized) neuralClassifier else null

        private fun String.isSearchPortal(): Boolean =
            runCatching {
                val uri = URI(this)
                val domain = uri.host.orEmpty().lowercase(Locale.ROOT).removePrefix("www.")
                domain.isSearchPortalDomain() &&
                    (uri.path.isNullOrBlank() || uri.path == "/" || uri.path.startsWith("/search"))
            }.getOrDefault(false)

        private fun String.isSearchPortalDomain(): Boolean =
            this in SearchPortalDomains ||
                startsWith("google.") ||
                (startsWith("search.") && substringAfter("search.") in SearchPortalDomains)

        private fun allowed(modelVersion: String = ModelVersion) =
            DagClassificationResult(
                decision = DagClassification.Allowed,
                category = "general",
                confidence = 0.82f,
                modelVersion = modelVersion,
            )

        private fun blocked(
            category: String,
            confidence: Float,
            modelVersion: String = ModelVersion,
        ) = DagClassificationResult(DagClassification.Blocked, category, confidence, modelVersion)

        private fun uncertain(
            category: String,
            confidence: Float,
            modelVersion: String = ModelVersion,
        ) = DagClassificationResult(DagClassification.Uncertain, category, confidence, modelVersion)

        companion object {
            const val ModelVersion = "dag-local-text-2"
            const val MaxPageCharacters = 24_000
            private const val MaxSemanticCharacters = 4_000
            private const val MaxNeuralCharacters = 2_000
            private const val MinimumRiskyImages = 3
            val NonOverridableCategories = setOf("unsafe_visual_platform", "search_portal")
            private val NonOverridableVisualDomains = setOf("imgsrc.ru")
            private val SearchPortalDomains =
                setOf(
                    "google.com",
                    "bing.com",
                    "yahoo.com",
                    "duckduckgo.com",
                    "search.brave.com",
                    "yandex.com",
                    "yandex.ru",
                    "ecosia.org",
                    "startpage.com",
                    "qwant.com",
                    "swisscows.com",
                    "mojeek.com",
                    "aol.com",
                    "ask.com",
                    "baidu.com",
                )

            private val CombiningMarksPattern = Regex("\\p{M}+")
            private val InvalidCharactersPattern = Regex("[^\\p{L}\\p{N}.]+")

            fun domainFrom(url: String): String =
                runCatching { URI(url).host.orEmpty() }
                    .getOrDefault("")
                    .lowercase(Locale.ROOT)
                    .removePrefix("www.")
                    .removeSuffix(".")

            private val ExplicitCategories =
                listOf(
                    "sexual" to
                        setOf(
                            "porn", "porno", "pornografia", "pornography", "xxx", "nude", "nudes", "nudity",
                            "desnudo", "desnuda", "desnudez", "explicit sex", "sexo explicito", "escort", "prostitucion",
                            "פורנו", "פורנוגרפיה", "עירום", "זנות", "מין מפורש",
                        ).toMatchers(),
                    "dating" to
                        setOf(
                            "dating app", "hookup", "casual dating", "sitio de citas", "app de citas", "encuentros sexuales",
                            "אפליקציית הכרויות", "הכרויות מזדמנות",
                        ).toMatchers(),
                    "gambling" to
                        setOf(
                            "online casino", "casino online", "sports betting", "apuestas deportivas", "betting odds",
                            "poker por dinero", "הימורים", "קזינו", "הימורי ספורט",
                        ).toMatchers(),
                    "drugs" to
                        setOf(
                            "buy cocaine", "comprar cocaina", "buy marijuana", "comprar marihuana", "recreational drugs",
                            "drogas recreativas", "לקנות קוקאין", "סמים למכירה", "סמי פנאי",
                        ).toMatchers(),
                    "violence" to
                        setOf(
                            "gore video", "graphic killing", "torture video", "video gore", "asesinato explicito",
                            "video de tortura", "סרטון רצח", "אלימות גרפית", "סרטון עינויים",
                        ).toMatchers(),
                )

            private val AmbiguousCategories =
                listOf(
                    "sensitive_health" to
                        setOf(
                            "sex", "sexo", "sexual", "sexualidad", "naked", "desnudos", "מין", "מיני", "עירומים",
                        ).toMatchers(),
                    "relationships" to
                        setOf("dating", "citas", "pareja", "relationship advice", "relaciones", "הכרויות", "זוגיות")
                            .toMatchers(),
                    "substances" to
                        setOf("cannabis", "marijuana", "cocaina", "cocaine", "drugs", "drogas", "קנאביס", "סמים")
                            .toMatchers(),
                    "violence" to
                        setOf("violence", "violent", "violencia", "murder", "asesinato", "אלימות", "רצח")
                            .toMatchers(),
                )

            private val ContextTerms =
                setOf(
                    "medical", "medicine", "health", "doctor", "educational", "education", "biology", "history",
                    "medico", "medica", "medicina", "salud", "doctor", "doctora", "educativo", "educacion", "biologia",
                    "halacha", "torah", "talmud", "judaism", "rabi", "rabbi", "halaja", "tora", "judaismo",
                    "רפואה", "בריאות", "רופא", "חינוך", "לימוד", "ביולוגיה", "הלכה", "תורה", "תלמוד", "יהדות", "רב",
                ).toMatchers()

            private fun Set<String>.toMatchers(): List<TermMatcher> =
                map { term ->
                    val normalizedTerm = term.normalizedForDag()
                    val wordPattern =
                        if (' ' in normalizedTerm || normalizedTerm.any { it.code > 127 }) {
                            null
                        } else {
                            Regex("(^|[^a-z0-9])${Regex.escape(normalizedTerm)}([^a-z0-9]|$)")
                        }
                    TermMatcher(normalizedTerm, wordPattern)
                }

            private fun String.normalizedForDag(): String =
                Normalizer
                    .normalize(this, Normalizer.Form.NFKD)
                    .lowercase(Locale.ROOT)
                    .replace(CombiningMarksPattern, "")
                    .replace('0', 'o')
                    .replace('1', 'i')
                    .replace('3', 'e')
                    .replace('4', 'a')
                    .replace('5', 's')
                    .replace('7', 't')
                    .replace(InvalidCharactersPattern, " ")
                    .trim()

            private data class TermMatcher(
                val normalizedTerm: String,
                val wordPattern: Regex?,
            ) {
                fun matches(text: String): Boolean = wordPattern?.containsMatchIn(text) ?: text.contains(normalizedTerm)
            }
        }
    }
