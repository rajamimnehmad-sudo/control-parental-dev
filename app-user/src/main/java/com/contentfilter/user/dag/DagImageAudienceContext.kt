package com.contentfilter.user.dag

import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.Normalizer

internal enum class DagImageAudienceContext {
    IntimateClothing,
    YoungChild,
    Male,
    FemaleSixPlus,
    Unknown,
}

internal fun dagImageAudienceContext(
    imageUrl: String,
    pageUrl: String?,
): DagImageAudienceContext {
    val words = normalizedAudienceWords(listOfNotNull(pageUrl, imageUrl).joinToString(" "))
    return when {
        IntimateClothingWords.any(words::contains) || ("ropa" in words && "interior" in words) -> {
            DagImageAudienceContext.IntimateClothing
        }
        YoungChildWords.any(words::contains) -> DagImageAudienceContext.YoungChild
        MaleWords.any(words::contains) && FemaleWords.none(words::contains) -> DagImageAudienceContext.Male
        FemaleWords.any(words::contains) -> DagImageAudienceContext.FemaleSixPlus
        else -> DagImageAudienceContext.Unknown
    }
}

private fun normalizedAudienceWords(value: String): Set<String> {
    val decoded = runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)
    val normalized =
        Normalizer.normalize(decoded, Normalizer.Form.NFD)
            .replace(CombiningMarks, "")
            .lowercase()
    return normalized.split(NonWordCharacters).filter(String::isNotBlank).toSet()
}

private val CombiningMarks = Regex("[\\u0300-\\u036f]")
private val NonWordCharacters = Regex("[^a-z0-9]+")
private val YoungChildWords =
    setOf("bebe", "bebes", "baby", "babies", "infant", "infants", "toddler", "toddlers", "newborn", "newborns")
private val IntimateClothingWords =
    setOf(
        "underwear",
        "lingerie",
        "lenceria",
        "intimates",
        "panty",
        "panties",
        "bombacha",
        "bombachas",
        "calzoncillo",
        "calzoncillos",
        "boxer",
        "boxers",
    )
private val MaleWords =
    setOf("hombre", "hombres", "men", "mens", "man", "male", "varon", "varones", "boy", "boys", "nino", "ninos")
private val FemaleWords =
    setOf("mujer", "mujeres", "women", "womens", "woman", "female", "dama", "damas", "girl", "girls", "nina", "ninas")
