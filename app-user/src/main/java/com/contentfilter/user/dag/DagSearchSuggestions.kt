package com.contentfilter.user.dag

import java.text.Normalizer
import java.util.Locale

internal fun dagSearchSuggestionCandidates(
    input: String,
    history: List<DagHistoryEntry>,
): List<String> {
    val normalizedInput = input.normalizedSuggestionText()
    if (normalizedInput.isBlank()) return emptyList()

    val contextual =
        when {
            normalizedInput == "coca" || normalizedInput.startsWith("coca ") ->
                listOf(
                    "Coca-Cola gaseosa",
                    "Precio de Coca-Cola",
                    "Coca-Cola en supermercados",
                    "Historia de Coca-Cola",
                )
            else -> emptyList()
        }
    val localHistory =
        history
            .asSequence()
            .flatMap { sequenceOf(it.value, it.title.orEmpty()) }
            .filter { candidate ->
                val normalized = candidate.normalizedSuggestionText()
                normalized.startsWith(normalizedInput) || normalized.contains(" $normalizedInput")
            }
            .toList()
    val liveLocal =
        DagLiveSuggestionCatalog.filter { candidate ->
            val normalized = candidate.normalizedSuggestionText()
            normalized.startsWith(normalizedInput) || normalized.contains(" $normalizedInput")
        }

    return (contextual + localHistory + liveLocal)
        .asSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy(String::normalizedSuggestionText)
        .take(MaximumSuggestions)
        .toList()
}

internal fun dagDidYouMeanSuggestion(input: String): String? {
    val normalized = input.normalizedSuggestionText()
    if (normalized.isBlank()) return null
    val corrected =
        normalized
            .split(" ")
            .joinToString(" ") { word ->
                DagSafeSpellingWords
                    .asSequence()
                    .filter { candidate -> candidate.firstOrNull() == word.firstOrNull() }
                    .map { candidate -> candidate to word.editDistance(candidate) }
                    .filter { (_, distance) -> distance <= maxOf(1, word.length / 4) }
                    .minByOrNull { (_, distance) -> distance }
                    ?.first ?: word
            }
    return corrected.takeIf { it != normalized }
}

private fun String.normalizedSuggestionText(): String =
    Normalizer
        .normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
        .replace("[^a-z0-9א-ת]+".toRegex(), " ")
        .trim()

private fun String.editDistance(other: String): Int {
    var previous = IntArray(other.length + 1) { it }
    forEachIndexed { sourceIndex, source ->
        val current = IntArray(other.length + 1)
        current[0] = sourceIndex + 1
        other.forEachIndexed { targetIndex, target ->
            current[targetIndex + 1] =
                minOf(
                    current[targetIndex] + 1,
                    previous[targetIndex + 1] + 1,
                    previous[targetIndex] + if (source == target) 0 else 1,
                )
        }
        previous = current
    }
    return previous[other.length]
}

private val DagLiveSuggestionCatalog =
    listOf(
        "ANSES trámites",
        "Banco Nación",
        "Cheeky ropa",
        "Clima de hoy",
        "Correo Argentino seguimiento",
        "Frávega electrodomésticos",
        "Mercado Libre Argentina",
        "Mi Argentina",
        "Noticias de Argentina",
        "Recetas fáciles",
        "Zara Argentina",
    )

private val DagSafeSpellingWords =
    setOf(
        "anses",
        "argentina",
        "banco",
        "cheeky",
        "clima",
        "correo",
        "electrodomesticos",
        "fravega",
        "mercado",
        "nacion",
        "noticias",
        "recetas",
        "seguimiento",
        "tramites",
        "zara",
    )

private const val MaximumSuggestions = 5
