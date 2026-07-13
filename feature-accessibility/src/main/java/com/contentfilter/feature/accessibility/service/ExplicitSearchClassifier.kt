package com.contentfilter.feature.accessibility.service

import java.text.Normalizer
import kotlin.math.exp

enum class ExplicitSearchDecision {
    Allow,
    BlockExplicit,
    Uncertain,
}

/** In-memory classifier. Queries are never persisted, logged or transmitted. */
class ExplicitSearchClassifier {
    fun classify(query: CharSequence): ExplicitSearchDecision {
        val normalized = query.normalizedQuery()
        if (normalized.length < MinimumQueryLength) return ExplicitSearchDecision.Allow
        if (AllowedContexts.any(normalized::contains)) return ExplicitSearchDecision.Allow
        if (BlockedPhrases.any(normalized::contains)) return ExplicitSearchDecision.BlockExplicit
        val tokens = normalized.split(' ').filter(String::isNotBlank)
        val score =
            ModelBias +
                tokens.sumOf {
                        token ->
                    ExplicitWeights.entries.firstOrNull { token.startsWith(it.key) }?.value ?: 0.0
                } +
                tokens.sumOf { token -> ContextWeights[token] ?: 0.0 }
        val probability = 1.0 / (1.0 + exp(-score))
        return when {
            probability >= BlockThreshold -> ExplicitSearchDecision.BlockExplicit
            probability <= AllowThreshold -> ExplicitSearchDecision.Allow
            else -> ExplicitSearchDecision.Uncertain
        }
    }

    private companion object {
        const val MinimumQueryLength = 3
        const val ModelBias = -3.2
        const val BlockThreshold = 0.86
        const val AllowThreshold = 0.25
        val BlockedPhrases =
            setOf("contenido adulto", "videos porno", "porn videos", "sexo explicito", "explicit sex", "nude videos")
        val AllowedContexts =
            setOf("educacion sexual", "salud sexual", "sexual education", "sexual health", "prevencion de abuso")
        val ExplicitWeights =
            mapOf(
                "porn" to 5.4,
                "porno" to 5.4,
                "xxx" to 5.1,
                "hentai" to 4.7,
                "desnud" to 3.8,
                "nude" to 3.8,
                "erotic" to 3.5,
            )
        val ContextWeights = mapOf("video" to 0.5, "videos" to 0.5, "foto" to 0.4, "fotos" to 0.4, "gratis" to 0.3)
    }
}

private fun CharSequence.normalizedQuery(): String =
    Normalizer.normalize(toString(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}+"), "")
        .lowercase()
        .replace(Regex("[^a-z0-9 ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
