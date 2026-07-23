package com.contentfilter.user.dag

import com.contentfilter.feature.vpn.domainlist.DynamicDomainBlocklist
import kotlinx.coroutines.CancellationException
import java.net.URI
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject

internal data class DagSearchClassificationInput(
    val title: String,
    val description: String,
    val url: String,
)

class DagContentClassifier
    @Inject
    constructor(
        private val domainBlocklist: DynamicDomainBlocklist,
        private val semanticClassifier: DagSemanticTextClassifier,
    ) {
        @Inject
        lateinit var neuralTextClassifier: DagNeuralTextClassifier

        internal var neuralTextStageOverride: DagNeuralTextStage? = null

        suspend fun classifyQueryStaged(text: String): DagClassificationResult {
            if (text.isClearlySafeCocaColaQuery() || text.isClearlySafeYeshurunQuery()) return allowed()
            return resolveNeuralStages(
                listOf(classifyCandidate(text, directUrl = false, reviewSingleAmbiguousTerm = true)),
            ).single()
        }

        internal suspend fun classifyResultsWithReasonStaged(
            inputs: List<DagSearchClassificationInput>,
        ): List<DagClassifiedSearchResult> {
            val candidates =
                inputs.map { input ->
                    classifyResultCandidateWithReason(
                        title = input.title,
                        description = input.description,
                        url = input.url,
                    )
                }
            val resolved = resolveNeuralStages(candidates.map(DagStagedSearchDecision::candidate))
            return candidates.zip(resolved) { candidate, classification ->
                DagClassifiedSearchResult(
                    classification = classification,
                    reason = candidate.reasonFor(classification),
                )
            }
        }

        internal suspend fun classifyPageStaged(
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
            val candidate = classifyPageCandidate(domain, title, text)
            return resolveNeuralStages(listOf(candidate)).single()
        }

        fun classifyQuery(text: String): DagClassificationResult =
            if (text.isClearlySafeCocaColaQuery() || text.isClearlySafeYeshurunQuery()) {
                allowed()
            } else {
                classify(text, directUrl = false, reviewSingleAmbiguousTerm = true)
            }

        fun classifyResult(
            title: String,
            description: String,
            url: String,
        ): DagClassificationResult = classifyResultWithReason(title, description, url).classification

        internal fun classifyResultWithReason(
            title: String,
            description: String,
            url: String,
        ): DagClassifiedSearchResult {
            val staged = classifyResultCandidateWithReason(title, description, url)
            return DagClassifiedSearchResult(
                classification = staged.candidate.classification,
                reason = staged.reasonFor(staged.candidate.classification),
            )
        }

        private fun classifyResultCandidateWithReason(
            title: String,
            description: String,
            url: String,
        ): DagStagedSearchDecision {
            val domain = domainFrom(url)
            blockedVisualPlatform(domain)?.let {
                return DagStagedSearchDecision(
                    candidate = DagStagedTextDecision(it),
                    fixedReason = DagSearchDecisionReason.PlatformBlock,
                )
            }
            domainBlocklist.categoryFor(domain)?.let { category ->
                return DagStagedSearchDecision(
                    candidate =
                        DagStagedTextDecision(
                            DagClassificationResult(
                                decision = DagClassification.Blocked,
                                category = category,
                                confidence = 1f,
                                modelVersion = ModelVersion,
                            ),
                        ),
                    fixedReason = DagSearchDecisionReason.DomainListBlock,
                )
            }
            return DagStagedSearchDecision(
                candidate =
                    classifyCandidate(
                        "$domain $title $description".withoutIntimateApparelTerms(),
                        directUrl = false,
                        reviewSingleAmbiguousTerm = true,
                    ),
            )
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
            return classifyPageCandidate(domain, title, text).classification
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
            return classify(domain, directUrl = true, reviewSingleAmbiguousTerm = false)
        }

        private fun blockedVisualPlatform(domain: String): DagClassificationResult? =
            NonOverridableVisualDomains
                .firstOrNull { domain == it || domain.endsWith(".$it") }
                ?.let { blocked("unsafe_visual_platform", confidence = 1f) }

        private fun classify(
            rawText: String,
            directUrl: Boolean,
            reviewSingleAmbiguousTerm: Boolean,
        ): DagClassificationResult = classifyCandidate(rawText, directUrl, reviewSingleAmbiguousTerm).classification

        private fun classifyPageCandidate(
            domain: String,
            title: String,
            text: String,
        ): DagStagedTextDecision {
            val visiblePageText =
                text
                    .take(MaxPageCharacters)
                    .withoutIntimateApparelTerms()
            val pageText =
                "$domain $title $visiblePageText"
                    .withoutIntimateApparelTerms()
            classifyPolicyCandidate(
                rawText = pageText,
                directUrl = false,
                reviewSingleAmbiguousTerm = false,
            )?.let { return it }

            val segmentCandidates =
                listOf(
                    domain.withoutIntimateApparelTerms(),
                    title.withoutIntimateApparelTerms(),
                    pageText,
                ).filter(String::isNotBlank)
                    .map(::classifySemantically)
            val compact =
                segmentCandidates
                    .map(DagStagedTextDecision::classification)
                    .reduce(::dagStrictestClassification)
            return DagStagedTextDecision(
                classification = compact,
                // One neural inference over the bounded complete sample keeps
                // latency stable. Compact host/title decisions were already
                // fused monotonically with the complete page, so this stage can
                // raise risk but never let body boilerplate dilute the title.
                neuralText =
                    pageText
                        .take(MaxNeuralCharacters)
                        .takeIf {
                            compact.decision == DagClassification.Uncertain ||
                                segmentCandidates.last().neuralText != null
                        },
            )
        }

        private fun classifyCandidate(
            rawText: String,
            directUrl: Boolean,
            reviewSingleAmbiguousTerm: Boolean,
        ): DagStagedTextDecision {
            classifyPolicyCandidate(rawText, directUrl, reviewSingleAmbiguousTerm)?.let { return it }
            return classifySemantically(rawText)
        }

        private fun classifyPolicyCandidate(
            rawText: String,
            directUrl: Boolean,
            reviewSingleAmbiguousTerm: Boolean,
        ): DagStagedTextDecision? {
            val text = rawText.normalizedForDag()
            if (text.isBlank()) return DagStagedTextDecision(uncertain("empty", confidence = 1f))
            val explicit =
                ExplicitCategories.firstOrNull {
                        (_, terms) ->
                    terms.any { term -> term.matches(text) }
                }
            val ambiguous =
                AmbiguousCategories.firstOrNull {
                        (_, terms) ->
                    terms.any { term -> term.matches(text) }
                }
            return when {
                explicit != null ->
                    DagStagedTextDecision(blocked(explicit.first, confidence = 0.97f))
                ambiguous != null && reviewSingleAmbiguousTerm ->
                    DagStagedTextDecision(
                        classification = uncertain(ambiguous.first, confidence = 0.68f),
                        neuralText = rawText,
                    )
                directUrl && !text.contains('.') ->
                    DagStagedTextDecision(uncertain("invalid_domain", confidence = 1f))
                directUrl -> DagStagedTextDecision(allowed())
                else -> null
            }
        }

        private fun classifySemantically(rawText: String): DagStagedTextDecision {
            val semantic = semanticClassifier.classify(rawText.take(MaxSemanticCharacters))
            val classification = semanticResult(semantic, compactModelVersion(semantic))
            return DagStagedTextDecision(
                classification = classification,
                neuralText =
                    rawText
                        .take(MaxNeuralCharacters)
                        .takeIf {
                            classification.decision != DagClassification.Blocked &&
                                dagNeedsNeuralStage(semantic)
                        },
            )
        }

        private suspend fun resolveNeuralStages(
            candidates: List<DagStagedTextDecision>,
        ): List<DagClassificationResult> {
            val staged =
                candidates.withIndex().filter { (_, candidate) ->
                    candidate.neuralText != null
                }
            if (staged.isEmpty()) return candidates.map(DagStagedTextDecision::classification)
            val predictions =
                try {
                    neuralTextStageOrNull()?.classifyBatch(staged.map { it.value.neuralText.orEmpty() })
                        ?: List(staged.size) { null }
                } catch (exception: CancellationException) {
                    throw exception
                } catch (_: Exception) {
                    List(staged.size) { null }
                }
            val byIndex =
                staged.mapIndexed { predictionIndex, indexedCandidate ->
                    indexedCandidate.index to predictions.getOrNull(predictionIndex)
                }.toMap()
            return candidates.mapIndexed { index, candidate ->
                if (index !in byIndex) {
                    candidate.classification
                } else {
                    resolveNeuralPrediction(candidate, byIndex[index])
                }
            }
        }

        private fun neuralTextStageOrNull(): DagNeuralTextStage? =
            neuralTextStageOverride ?: if (::neuralTextClassifier.isInitialized) neuralTextClassifier else null

        private fun resolveNeuralPrediction(
            candidate: DagStagedTextDecision,
            neural: DagSemanticPrediction?,
        ): DagClassificationResult {
            val compact = candidate.classification
            if (neural == null) return compact
            val neuralResult = semanticResult(neural, neuralModelVersion(neural))
            return when {
                compact.decision == DagClassification.Blocked -> compact
                neuralResult.decision == DagClassification.Blocked -> neuralResult
                compact.decision == DagClassification.Allowed &&
                    neuralResult.decision == DagClassification.Uncertain -> neuralResult
                compact.decision == DagClassification.Uncertain ->
                    uncertain(
                        category = "semantic_model_uncertain",
                        confidence = maxOf(compact.confidence, neuralResult.confidence),
                        modelVersion = neuralModelVersion(neural),
                    )
                else -> compact
            }
        }

        private fun semanticResult(
            semantic: DagSemanticPrediction?,
            modelVersion: String,
        ): DagClassificationResult =
            when (dagSemanticDecision(semantic)) {
                DagClassification.Blocked ->
                    blocked(
                        "semantic_${semantic?.category}",
                        semantic?.confidence ?: 1f,
                        modelVersion,
                    )
                DagClassification.Uncertain ->
                    uncertain(
                        if (semantic == null) "model_unavailable" else "semantic_${semantic.category}",
                        semantic?.confidence ?: 1f,
                        modelVersion,
                    )
                DagClassification.Allowed ->
                    allowed(modelVersion).copy(confidence = semantic?.confidence ?: 0.82f)
            }

        private fun compactModelVersion(semantic: DagSemanticPrediction?): String =
            "$ModelVersion/compact:${semantic?.modelVersion ?: "unavailable"}"

        private fun neuralModelVersion(semantic: DagSemanticPrediction): String =
            "$ModelVersion/neural:${semantic.modelVersion}"

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

        private data class DagStagedTextDecision(
            val classification: DagClassificationResult,
            val neuralText: String? = null,
        )

        private data class DagStagedSearchDecision(
            val candidate: DagStagedTextDecision,
            val fixedReason: DagSearchDecisionReason? = null,
        ) {
            fun reasonFor(classification: DagClassificationResult): DagSearchDecisionReason =
                fixedReason
                    ?: when (classification.decision) {
                        DagClassification.Allowed -> DagSearchDecisionReason.Allowed
                        DagClassification.Uncertain -> DagSearchDecisionReason.Uncertain
                        DagClassification.Blocked -> DagSearchDecisionReason.LocalClassifierBlock
                    }
        }

        companion object {
            const val ModelVersion = "dag-local-text-12-staged-minilm-2"
            const val MaxPageCharacters = 24_000
            private const val MaxSemanticCharacters = 4_000
            private const val MaxNeuralCharacters = 2_000
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

            private fun String.isClearlySafeCocaColaQuery(): Boolean {
                val words = normalizedForDag().split(' ').filter(String::isNotBlank)
                if (words.size < 2 || !words.contains("coca") || !words.contains("cola")) return false
                return words.all(SafeCocaColaQueryWords::contains)
            }

            private fun String.isClearlySafeYeshurunQuery(): Boolean {
                val words = normalizedForDag().split(' ').filter(String::isNotBlank)
                if (words.none { it == "yeshrun" || it == "yeshurun" }) return false
                return words.all(SafeYeshurunQueryWords::contains)
            }

            private val SafeCocaColaQueryWords =
                setOf(
                    "coca", "cola", "comprar", "compra", "precio", "precios", "oferta", "ofertas",
                    "supermercado", "bebida", "gaseosa", "lata", "botella", "argentina", "cerca", "donde",
                    "conseguir", "delivery", "zero", "light", "sin", "azucar", "en", "de", "una", "la", "quiero",
                )

            private val SafeYeshurunQueryWords =
                setOf(
                    "yeshrun", "yeshurun", "instagram", "facebook", "oficial", "comunidad", "tora", "torah",
                    "yeshiva", "buenos", "aires", "argentina", "sitio", "pagina", "buscar", "en", "de", "la",
                )

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
                            "nsfw", "desnudo", "desnuda", "desnudez", "explicit sex", "sexo explicito", "escort",
                            "prostitucion", "adult photo gallery", "nsfw photo gallery", "sex toy", "sex toys",
                            "adult store", "adult shop", "sex shop", "juguete sexual", "juguetes sexuales",
                            "tienda erotica", "tienda para adultos", "productos eroticos",
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
                            "sportsbook", "poker por dinero", "הימורים", "קזינו", "הימורי ספורט",
                        ).toMatchers(),
                    "drugs" to
                        setOf(
                            "buy cocaine", "comprar cocaina", "buy marijuana", "comprar marihuana", "recreational drugs",
                            "drogas recreativas", "לקנות קוקאין", "סמים למכירה", "סמי פנאי",
                        ).toMatchers(),
                    "violence" to
                        setOf(
                            "gore video", "graphic killing", "torture video", "video gore", "asesinato explicito",
                            "video de tortura", "ver como matan", "watch people being killed",
                            "סרטון רצח", "אלימות גרפית", "סרטון עינויים",
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

            private val IntimateApparelTerms =
                setOf(
                    "lenceria", "lenceria intima", "ropa interior", "ropa intima", "corpiño", "bombacha",
                    "lingerie", "intimate apparel", "women underwear", "womens underwear", "female underwear",
                    "bra and panties", "bralette", "bikini", "swimwear", "traje de baño",
                ).toMatchers()

            private fun String.withoutIntimateApparelTerms(): String =
                IntimateApparelTerms.fold(normalizedForDag()) { text, term -> term.removeFrom(text) }

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

                fun removeFrom(text: String): String =
                    wordPattern?.replace(text) { match ->
                        "${match.groupValues[1]} ${match.groupValues[2]}"
                    } ?: text.replace(normalizedTerm, " ")
            }
        }
    }

internal fun dagNeedsNeuralStage(prediction: DagSemanticPrediction?): Boolean =
    prediction == null ||
        prediction.category != "general" ||
        prediction.confidence < ClearCompactSafeConfidence ||
        prediction.margin < ClearCompactSafeMargin

internal fun dagStrictestClassification(
    first: DagClassificationResult,
    second: DagClassificationResult,
): DagClassificationResult {
    val firstRank = first.decision.riskRank()
    val secondRank = second.decision.riskRank()
    return when {
        firstRank > secondRank -> first
        secondRank > firstRank -> second
        first.confidence >= second.confidence -> first
        else -> second
    }
}

private fun DagClassification.riskRank(): Int =
    when (this) {
        DagClassification.Allowed -> 0
        DagClassification.Uncertain -> 1
        DagClassification.Blocked -> 2
    }

private const val ClearCompactSafeConfidence = 0.50f
private const val ClearCompactSafeMargin = 0.20f

internal enum class DagAdaptivePageDecision {
    Allowed,
    Protected,
    Blocked,
}

internal fun dagAdaptivePageDecision(result: DagClassificationResult): DagAdaptivePageDecision =
    when (result.decision) {
        DagClassification.Allowed -> DagAdaptivePageDecision.Allowed
        DagClassification.Uncertain -> DagAdaptivePageDecision.Protected
        // The staged classifier has already applied the review threshold and
        // neural disagreement handling. Relaxing a remaining Blocked decision
        // here could reveal the very content the semantic gate identified.
        DagClassification.Blocked -> DagAdaptivePageDecision.Blocked
    }
