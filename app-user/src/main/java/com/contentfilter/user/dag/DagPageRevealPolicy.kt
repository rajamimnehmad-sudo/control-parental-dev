package com.contentfilter.user.dag

import java.text.Normalizer
import java.util.Locale

internal fun dagFastRevealCategory(
    url: String,
    title: String,
    text: String,
    classification: DagClassificationResult,
): String? {
    if (classification.decision != DagClassification.Allowed) return null

    val domain = DagContentClassifier.domainFrom(url)
    if (GovernmentDomainSuffixes.any { domain.endsWith(it) }) return "government"

    val content = "$domain $title ${text.take(MaxCategoryCharacters)}".normalizedForPageCategory()
    return CategorySignals.entries.firstOrNull { (_, signals) ->
        signals.count { signal -> content.containsWholeSignal(signal) } >= MinimumSignals
    }?.key
}

private fun String.containsWholeSignal(signal: String): Boolean = " $this ".contains(" $signal ")

private fun String.normalizedForPageCategory(): String =
    Normalizer
        .normalize(this, Normalizer.Form.NFKD)
        .lowercase(Locale.ROOT)
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()

private const val MaxCategoryCharacters = 8_000
private const val MinimumSignals = 2

private val GovernmentDomainSuffixes =
    setOf(
        ".gob.ar",
        ".gov.ar",
        ".gov",
    )

private val CategorySignals =
    linkedMapOf(
        "electronics" to
            setOf(
                "electronica",
                "tecnologia",
                "smartphone",
                "smartphones",
                "celular",
                "celulares",
                "notebook",
                "notebooks",
                "computadora",
                "computadoras",
                "tablet",
                "tablets",
                "televisor",
                "televisores",
                "monitor",
                "monitores",
                "procesador",
                "auriculares",
                "electrodomesticos",
                "mobile",
                "tv audio",
            ),
        "finance" to
            setOf(
                "banco",
                "bank",
                "cuenta bancaria",
                "tarjeta de credito",
                "transferencia bancaria",
                "home banking",
            ),
        "documentation" to
            setOf(
                "documentacion",
                "documentation",
                "referencia api",
                "api reference",
                "developer guide",
                "manual tecnico",
                "codigo fuente",
            ),
    )
