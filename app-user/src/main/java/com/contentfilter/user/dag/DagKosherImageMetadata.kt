package com.contentfilter.user.dag

import java.text.Normalizer
import java.util.Locale

internal const val DagIntimateImageJavaScriptPattern =
    "\\b(lingerie|lenceria|ropa\\s+(interior|intima)|women'?s?\\s+underwear|female\\s+underwear|" +
        "intimate\\s+apparel|bra(s|lette)?|pant(y|ies)|thong|" +
        "malla\\s+de\\s+mujer|corpin(o|os)|bombacha(s)?|bodysuit)\\b"

internal fun isIntimateImageMetadata(value: String): Boolean =
    Regex(DagIntimateImageJavaScriptPattern, RegexOption.IGNORE_CASE).containsMatchIn(value.normalizedImageMetadata())

internal fun isFemaleIntimateCategoryUrl(value: String): Boolean {
    val normalized = value.normalizedImageMetadata().replace('-', ' ').replace('_', ' ')
    val female = Regex("\\b(mujer(es)?|women|woman|dama(s)?|female)\\b").containsMatchIn(normalized)
    val intimate =
        Regex(
            "\\b(ropa\\s+(interior|intima)|underwear|lingerie|lenceria|intimates|" +
                "corpin(es|os)?|bombacha(s)?|bralette(s)?|pant(y|ies))\\b",
        ).containsMatchIn(normalized)
    return female && intimate
}

private fun String.normalizedImageMetadata(): String =
    Normalizer
        .normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
