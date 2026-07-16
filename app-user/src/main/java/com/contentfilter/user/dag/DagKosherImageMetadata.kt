package com.contentfilter.user.dag

import java.text.Normalizer
import java.util.Locale

internal const val DagIntimateImageJavaScriptPattern =
    "\\b(lingerie|lenceria|ropa\\s+(interior|intima)|women'?s?\\s+underwear|female\\s+underwear|" +
        "intimate\\s+apparel|bra(s|lette)?|pant(y|ies)|thong|bikini|swimwear|traje\\s+de\\s+bano|" +
        "malla\\s+de\\s+mujer|corpin(o|os)|bombacha(s)?|bodysuit)\\b"

internal fun isIntimateImageMetadata(value: String): Boolean =
    Regex(DagIntimateImageJavaScriptPattern, RegexOption.IGNORE_CASE).containsMatchIn(value.normalizedImageMetadata())

private fun String.normalizedImageMetadata(): String =
    Normalizer
        .normalize(lowercase(Locale.ROOT), Normalizer.Form.NFD)
        .replace("\\p{M}+".toRegex(), "")
