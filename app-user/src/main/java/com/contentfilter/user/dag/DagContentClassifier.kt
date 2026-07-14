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
    ) {
        fun classifyQuery(text: String): DagClassificationResult = classify(text, directUrl = false)

        fun classifyResult(
            title: String,
            description: String,
            url: String,
        ): DagClassificationResult {
            val domain = domainFrom(url)
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

        fun classifyPage(
            url: String,
            title: String,
            text: String,
        ): DagClassificationResult {
            val domain = domainFrom(url)
            domainBlocklist.categoryFor(domain)?.let { category ->
                return DagClassificationResult(
                    decision = DagClassification.Blocked,
                    category = category,
                    confidence = 1f,
                    modelVersion = ModelVersion,
                )
            }
            return classify("$domain $title ${text.take(MaxPageCharacters)}", directUrl = false)
        }

        fun classifyDirectUrl(url: String): DagClassificationResult {
            val domain = domainFrom(url)
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

        private fun classify(
            rawText: String,
            directUrl: Boolean,
        ): DagClassificationResult {
            val text = rawText.normalizedForDag()
            if (text.isBlank()) return uncertain("empty", confidence = 1f)
            val explicit =
                ExplicitCategories.firstOrNull {
                        (_, terms) ->
                    terms.any { term -> text.containsTerm(term) }
                }
            val contextPresent = ContextTerms.any { term -> text.containsTerm(term) }
            val ambiguous =
                AmbiguousCategories.firstOrNull {
                        (_, terms) ->
                    terms.any { term -> text.containsTerm(term) }
                }
            return when {
                explicit != null && contextPresent -> uncertain("${explicit.first}_context", confidence = 0.72f)
                explicit != null -> blocked(explicit.first, confidence = 0.97f)
                ambiguous != null -> uncertain(ambiguous.first, confidence = 0.68f)
                directUrl && !text.contains('.') -> uncertain("invalid_domain", confidence = 1f)
                else ->
                    DagClassificationResult(
                        decision = DagClassification.Allowed,
                        category = "general",
                        confidence = 0.82f,
                        modelVersion = ModelVersion,
                    )
            }
        }

        private fun blocked(
            category: String,
            confidence: Float,
        ) = DagClassificationResult(DagClassification.Blocked, category, confidence, ModelVersion)

        private fun uncertain(
            category: String,
            confidence: Float,
        ) = DagClassificationResult(DagClassification.Uncertain, category, confidence, ModelVersion)

        companion object {
            const val ModelVersion = "dag-local-text-1"
            const val MaxPageCharacters = 24_000

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
                        ),
                    "dating" to
                        setOf(
                            "dating app", "hookup", "casual dating", "sitio de citas", "app de citas", "encuentros sexuales",
                            "אפליקציית הכרויות", "הכרויות מזדמנות",
                        ),
                    "gambling" to
                        setOf(
                            "online casino", "casino online", "sports betting", "apuestas deportivas", "betting odds",
                            "poker por dinero", "הימורים", "קזינו", "הימורי ספורט",
                        ),
                    "drugs" to
                        setOf(
                            "buy cocaine", "comprar cocaina", "buy marijuana", "comprar marihuana", "recreational drugs",
                            "drogas recreativas", "לקנות קוקאין", "סמים למכירה", "סמי פנאי",
                        ),
                    "violence" to
                        setOf(
                            "gore video", "graphic killing", "torture video", "video gore", "asesinato explicito",
                            "video de tortura", "סרטון רצח", "אלימות גרפית", "סרטון עינויים",
                        ),
                )

            private val AmbiguousCategories =
                listOf(
                    "sensitive_health" to
                        setOf(
                            "sex", "sexo", "sexual", "sexualidad", "naked", "desnudos", "מין", "מיני", "עירומים",
                        ),
                    "relationships" to
                        setOf("dating", "citas", "pareja", "relationship advice", "relaciones", "הכרויות", "זוגיות"),
                    "substances" to
                        setOf("cannabis", "marijuana", "cocaina", "cocaine", "drugs", "drogas", "קנאביס", "סמים"),
                    "violence" to
                        setOf("violence", "violent", "violencia", "murder", "asesinato", "אלימות", "רצח"),
                )

            private val ContextTerms =
                setOf(
                    "medical", "medicine", "health", "doctor", "educational", "education", "biology", "history",
                    "medico", "medica", "medicina", "salud", "doctor", "doctora", "educativo", "educacion", "biologia",
                    "halacha", "torah", "talmud", "judaism", "rabi", "rabbi", "halaja", "tora", "judaismo",
                    "רפואה", "בריאות", "רופא", "חינוך", "לימוד", "ביולוגיה", "הלכה", "תורה", "תלמוד", "יהדות", "רב",
                )

            private fun String.containsTerm(term: String): Boolean {
                val normalizedTerm = term.normalizedForDag()
                if (' ' in normalizedTerm || normalizedTerm.any { it.code > 127 }) return contains(normalizedTerm)
                return Regex("(^|[^a-z0-9])${Regex.escape(normalizedTerm)}([^a-z0-9]|$)").containsMatchIn(this)
            }

            private fun String.normalizedForDag(): String =
                Normalizer
                    .normalize(this, Normalizer.Form.NFKD)
                    .lowercase(Locale.ROOT)
                    .replace(Regex("\\p{M}+"), "")
                    .replace('0', 'o')
                    .replace('1', 'i')
                    .replace('3', 'e')
                    .replace('4', 'a')
                    .replace('5', 's')
                    .replace('7', 't')
                    .replace(Regex("[^\\p{L}\\p{N}.]+"), " ")
                    .trim()
        }
    }
