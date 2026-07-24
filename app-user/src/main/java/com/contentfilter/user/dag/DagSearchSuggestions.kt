package com.contentfilter.user.dag

import java.text.Normalizer
import java.util.Locale

internal fun dagSearchSuggestionCandidates(
    input: String,
    history: List<DagHistoryEntry>,
    remote: List<String> = emptyList(),
): List<String> {
    val normalizedInput = input.normalizedSuggestionText()
    if (normalizedInput.isBlank()) return emptyList()

    val localHistory =
        history
            .asSequence()
            .flatMap { sequenceOf(it.value, it.title.orEmpty()) }
            .filter { candidate ->
                val normalized = candidate.normalizedSuggestionText()
                normalized.startsWith(normalizedInput) || normalized.contains(" $normalizedInput")
            }
            .toList()

    return (localHistory + remote)
        .asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy(String::normalizedSuggestionText)
        .take(MaximumSuggestions)
        .toList()
}

private fun String.normalizedSuggestionText(): String =
    Normalizer
        .normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .replace("[^a-z0-9א-ת]+".toRegex(), " ")
        .trim()

private const val MaximumSuggestions = 8
