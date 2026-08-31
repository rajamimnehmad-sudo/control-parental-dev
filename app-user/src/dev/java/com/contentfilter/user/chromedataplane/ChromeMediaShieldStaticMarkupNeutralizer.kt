package com.contentfilter.user.chromedataplane

import java.util.Locale

/**
 * Rewrites only actual HTML start tags. Raw text inside script/style and ordinary text are copied
 * byte-for-byte so the DEV document transformer never edits JavaScript or CSS string literals.
 */
internal object ChromeMediaShieldStaticMarkupNeutralizer {
    fun neutralize(
        source: String,
        metaCspRewriter: ((String) -> String?)? = null,
    ): String {
        val output = StringBuilder(source.length + 64)
        var offset = 0
        while (offset < source.length) {
            if (source.startsWith("<!--", offset)) {
                val end = source.indexOf("-->", offset + 4).let { if (it < 0) source.length else it + 3 }
                output.append(source, offset, end)
                offset = end
                continue
            }
            if (source[offset] != '<') {
                output.append(source[offset++])
                continue
            }
            val tagEnd =
                source.tagEndOrNull(offset) ?: run {
                    throw ChromeMediaShieldStaticMarkupException("unterminated_start_tag")
                }
            val tag = source.substring(offset, tagEnd)
            val tagName = tag.startTagNameOrNull()
            if (tagName == null) {
                output.append(
                    when (tag.endTagNameOrNull()) {
                        "object" -> "</template>"
                        in BlockedVoidMediaElements -> ""
                        else -> tag
                    },
                )
                offset = tagEnd
                continue
            }
            val authorityNeutralizedTag =
                tag
                    .removeAuthorGloshAttributes()
                    .neutralizeDeclarativeTopLayer()
            output.append(
                when (tagName) {
                    "iframe" -> authorityNeutralizedTag.neutralizeIframe().protectInitialMedia()
                    "template" -> authorityNeutralizedTag.blockDeclarativeShadowRoot()
                    "object" -> blockedTemplate(tagName, paired = !tag.isSelfClosingStartTag())
                    in BlockedVoidMediaElements -> blockedTemplate(tagName, paired = false)
                    "canvas", "video", "svg" -> authorityNeutralizedTag.protectInitialMedia()
                    "img" ->
                        if (authorityNeutralizedTag.hasLocalMediaSource()) {
                            authorityNeutralizedTag.protectInitialMedia()
                        } else {
                            authorityNeutralizedTag
                        }
                    "source" -> authorityNeutralizedTag.neutralizeLocalSourceAttributes()
                    "input" -> {
                        val normalized = authorityNeutralizedTag.forceSameContextFormTarget()
                        if (normalized.isImageInput() && normalized.hasLocalMediaSource()) {
                            normalized.protectInitialMedia()
                        } else {
                            normalized
                        }
                    }
                    "a", "area", "form", "base" -> authorityNeutralizedTag.forceSameContextTarget()
                    "button" -> authorityNeutralizedTag.forceSameContextFormTarget()
                    "meta" -> authorityNeutralizedTag.rewriteMetaCsp(metaCspRewriter)
                    else ->
                        if (authorityNeutralizedTag.hasLocalMediaStyle()) {
                            authorityNeutralizedTag.protectInitialMedia()
                        } else {
                            authorityNeutralizedTag
                        }
                },
            )
            offset = tagEnd
            if (tagName == "script" || tagName == "style") {
                val closing = source.indexOfClosingTag(tagName, offset)
                if (closing < 0) {
                    throw ChromeMediaShieldStaticMarkupException("unterminated_raw_text_$tagName")
                }
                output.append(source, offset, closing)
                offset = closing
            }
        }
        return output.toString()
    }

    private fun String.tagEndOrNull(start: Int): Int? {
        var quote: Char? = null
        var index = start + 1
        while (index < length) {
            val character = this[index]
            when {
                quote == null && character == '<' -> return null
                quote != null && character == quote -> quote = null
                quote == null && (character == '\'' || character == '"') -> quote = character
                quote == null && character == '>' -> return index + 1
            }
            index += 1
        }
        return null
    }

    private fun String.startTagNameOrNull(): String? {
        if (length < 3 || first() != '<' || this[1] in setOf('/', '!', '?')) return null
        var end = 1
        while (end < length && this[end].isHtmlNameCharacter()) end += 1
        if (end == 1) return null
        if (end < length && !this[end].isHtmlSpace() && this[end] !in setOf('>', '/')) return null
        return substring(1, end).lowercase(Locale.US)
    }

    private fun String.isSelfClosingStartTag(): Boolean {
        var index = 1
        while (index < lastIndex && this[index].isHtmlNameCharacter()) index += 1
        while (index < lastIndex) {
            while (index < lastIndex && this[index].isHtmlSpace()) index += 1
            if (getOrNull(index) == '/') {
                index += 1
                while (index < lastIndex && this[index].isHtmlSpace()) index += 1
                return index == lastIndex
            }
            while (
                index < lastIndex &&
                !this[index].isHtmlSpace() &&
                this[index] !in setOf('/', '=', '>')
            ) {
                index += 1
            }
            while (index < lastIndex && this[index].isHtmlSpace()) index += 1
            if (getOrNull(index) != '=') continue
            index += 1
            while (index < lastIndex && this[index].isHtmlSpace()) index += 1
            val quote = getOrNull(index)
            if (quote in setOf('\'', '"')) {
                index += 1
                while (index < lastIndex && this[index] != quote) index += 1
                if (index < lastIndex) index += 1
            } else {
                while (index < lastIndex && !this[index].isHtmlSpace() && this[index] != '>') {
                    index += 1
                }
            }
        }
        return false
    }

    private fun String.endTagNameOrNull(): String? {
        if (length < 4 || !startsWith("</")) return null
        var index = 2
        while (index < length && this[index].isHtmlSpace()) index += 1
        val start = index
        while (index < length && this[index].isHtmlNameCharacter()) index += 1
        if (index == start) return null
        val name = substring(start, index).lowercase(Locale.US)
        while (index < length && this[index].isHtmlSpace()) index += 1
        return name.takeIf { getOrNull(index) == '>' }
    }

    private fun blockedTemplate(
        sourceName: String,
        paired: Boolean,
    ): String = "<template data-glosh-blocked-element=\"$sourceName\">" + if (paired) "" else "</template>"

    private fun String.neutralizeIframe(): String {
        val withoutSrcdoc = renameAllAttributes("srcdoc", "data-glosh-blocked-srcdoc")
        return withoutSrcdoc.ensureIframeSandbox()
    }

    private fun String.neutralizeLocalSourceAttributes(): String {
        val localRanges =
            attributeNameRanges().filter { range ->
                substring(range).lowercase(Locale.US) in MediaSourceAttributes &&
                    attributeValue(range).isLocalMediaValue()
            }
        if (localRanges.isEmpty()) return this
        return localRanges.asReversed().fold(this) { value, range ->
            val name = substring(range).lowercase(Locale.US)
            value.replaceAttribute(range, "data-glosh-blocked-$name=\"1\"")
        }.protectInitialMedia()
    }

    private fun String.protectInitialMedia(): String {
        val styleRanges = attributeNameRanges().filter { range -> substring(range).equals("style", true) }
        if (styleRanges.size > 1) throw ChromeMediaShieldStaticMarkupException("ambiguous_media_style")
        val declarations = "visibility:hidden!important;opacity:0!important"
        val withStyle =
            if (styleRanges.isEmpty()) {
                insertAttribute("style=\"$declarations\"")
            } else {
                appendToAttributeValue(styleRanges.single(), ";$declarations")
            }
        return if (withStyle.attributeNameRanges().any { range ->
                withStyle.substring(range).equals(MediaBlockedAttribute, true)
            }
        ) {
            withStyle
        } else {
            withStyle.insertAttribute("$MediaBlockedAttribute=\"1\"")
        }
    }

    private fun String.insertAttribute(attribute: String): String {
        val tagEnd = lastIndexOf('>').takeIf { it >= 0 } ?: return this
        val slash = substring(0, tagEnd).indexOfLast { !it.isHtmlSpace() }
        val insertion = if (slash >= 0 && this[slash] == '/') slash else tagEnd
        return substring(0, insertion) + " $attribute" + substring(insertion)
    }

    private fun String.appendToAttributeValue(
        nameRange: IntRange,
        suffix: String,
    ): String {
        var index = nameRange.last + 1
        while (index < length && this[index].isHtmlSpace()) index += 1
        if (getOrNull(index) != '=') throw ChromeMediaShieldStaticMarkupException("missing_media_style_value")
        index += 1
        while (index < length && this[index].isHtmlSpace()) index += 1
        val quote = getOrNull(index)
        val insertion =
            if (quote in setOf('\'', '"')) {
                index += 1
                while (index < length && this[index] != quote) index += 1
                if (index >= length) throw ChromeMediaShieldStaticMarkupException("unterminated_media_style")
                index
            } else {
                val start = index
                while (index < length && !this[index].isHtmlSpace() && this[index] != '>') index += 1
                if (index == start) throw ChromeMediaShieldStaticMarkupException("missing_media_style_value")
                index
            }
        return substring(0, insertion) + suffix + substring(insertion)
    }

    private fun String.hasLocalMediaSource(): Boolean =
        attributeNameRanges().any { range ->
            substring(range).lowercase(Locale.US) in MediaSourceAttributes &&
                attributeValue(range).isLocalMediaValue()
        }

    private fun String.hasLocalMediaStyle(): Boolean =
        attributeNameRanges().any { range ->
            substring(range).equals("style", true) && attributeValue(range).isLocalMediaValue()
        }

    private fun String.isImageInput(): Boolean =
        attributeNameRanges().singleOrNull { range -> substring(range).equals("type", true) }
            ?.let { range -> attributeValue(range) }
            ?.trim()
            ?.equals("image", ignoreCase = true) == true

    private fun String?.isLocalMediaValue(): Boolean {
        val value = this?.lowercase(Locale.US) ?: return false
        return "data:" in value || "blob:" in value
    }

    private fun String.ensureIframeSandbox(): String {
        val existing =
            attributeNameRanges().filter { range ->
                substring(range).equals("sandbox", ignoreCase = true)
            }
        if (existing.isNotEmpty()) {
            return existing.asReversed().fold(this) { value, range ->
                value.replaceAttribute(range, "sandbox=\"$IframeSandbox\"")
            }
        }
        val insertion = lastIndexOf('>').takeIf { it >= 0 } ?: return this
        val slash = substring(0, insertion).indexOfLast { !it.isHtmlSpace() }
        val insertionPoint = if (slash >= 0 && this[slash] == '/') slash else insertion
        return substring(0, insertionPoint) +
            " sandbox=\"$IframeSandbox\"" +
            substring(insertionPoint)
    }

    private fun String.replaceAttribute(
        nameRange: IntRange,
        replacement: String,
    ): String {
        var end = nameRange.last + 1
        while (end < length && this[end].isHtmlSpace()) end += 1
        if (getOrNull(end) == '=') {
            end += 1
            while (end < length && this[end].isHtmlSpace()) end += 1
            if (getOrNull(end) in setOf('\'', '"')) {
                val quote = this[end++]
                while (end < length && this[end] != quote) end += 1
                if (end < length) end += 1
            } else {
                while (end < length && !this[end].isHtmlSpace() && this[end] != '>') end += 1
            }
        }
        return replaceRange(nameRange.first, end, replacement)
    }

    private fun String.rewriteMetaCsp(rewriter: ((String) -> String?)?): String {
        if (rewriter == null) return this
        val httpEquiv = attributeNameRanges().filter { range -> substring(range).equals("http-equiv", true) }
        if (httpEquiv.isEmpty()) return this
        if (httpEquiv.size != 1) throw ChromeMediaShieldStaticMarkupException("ambiguous_meta_http_equiv")
        val directive = attributeValue(httpEquiv.single()) ?: return this
        if (!directive.equals("content-security-policy", ignoreCase = true)) return this
        val content = attributeNameRanges().filter { range -> substring(range).equals("content", true) }
        if (content.size != 1) throw ChromeMediaShieldStaticMarkupException("ambiguous_meta_csp_content")
        val sourcePolicy =
            attributeValue(content.single())
                ?: throw ChromeMediaShieldStaticMarkupException("missing_meta_csp_content")
        val rewritten =
            rewriter(sourcePolicy)
                ?: throw ChromeMediaShieldStaticMarkupException("unsupported_meta_csp_content")
        return replaceAttribute(content.single(), "content=\"${rewritten.escapeHtmlAttribute()}\"")
    }

    private fun String.attributeValue(nameRange: IntRange): String? {
        var index = nameRange.last + 1
        while (index < length && this[index].isHtmlSpace()) index += 1
        if (getOrNull(index) != '=') return null
        index += 1
        while (index < length && this[index].isHtmlSpace()) index += 1
        val quote = getOrNull(index)
        if (quote in setOf('\'', '"')) {
            val start = ++index
            while (index < length && this[index] != quote) index += 1
            if (index >= length) throw ChromeMediaShieldStaticMarkupException("unterminated_attribute_value")
            return substring(start, index)
        }
        val start = index
        while (index < length && !this[index].isHtmlSpace() && this[index] != '>') index += 1
        return substring(start, index).takeIf(String::isNotEmpty)
    }

    private fun String.escapeHtmlAttribute(): String =
        replace("&", "&amp;")
            .replace("\"", "&quot;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")

    private fun String.blockDeclarativeShadowRoot(): String {
        return renameAllAttributes("shadowrootmode", "data-glosh-blocked-shadowrootmode")
    }

    private fun String.neutralizeDeclarativeTopLayer(): String =
        DeclarativeTopLayerAttributes.fold(this) { value, attribute ->
            value.renameAllAttributes(attribute, "data-glosh-blocked-$attribute")
        }

    private fun String.removeAuthorGloshAttributes(): String =
        attributeNameRanges()
            .filter { range -> substring(range).startsWith("data-glosh-", ignoreCase = true) }
            .asReversed()
            .fold(this) { value, range -> value.replaceAttribute(range, "") }

    private fun String.forceSameContextTarget(): String {
        val targets =
            attributeNameRanges().filter { range ->
                substring(range).equals("target", ignoreCase = true)
            }
        return targets.asReversed().fold(this) { value, range ->
            value.replaceAttribute(range, "target=\"_self\"")
        }
    }

    private fun String.forceSameContextFormTarget(): String {
        val targets =
            attributeNameRanges().filter { range ->
                substring(range).equals("formtarget", ignoreCase = true)
            }
        return targets.asReversed().fold(this) { value, range ->
            value.replaceAttribute(range, "formtarget=\"_self\"")
        }
    }

    private fun String.renameAllAttributes(
        sourceName: String,
        replacementName: String,
    ): String =
        attributeNameRanges()
            .filter { range -> substring(range).equals(sourceName, ignoreCase = true) }
            .asReversed()
            .fold(this) { value, range -> value.replaceRange(range, replacementName) }

    private fun String.attributeNameRanges(): List<IntRange> {
        val ranges = mutableListOf<IntRange>()
        var index = 1
        while (index < length && this[index].isHtmlNameCharacter()) index += 1
        while (index < length) {
            while (index < length && (this[index].isHtmlSpace() || this[index] == '/')) index += 1
            if (index >= length || this[index] == '>') break
            val start = index
            while (index < length && this[index].isHtmlNameCharacter()) index += 1
            if (index == start) {
                index += 1
                continue
            }
            ranges += start until index
            while (index < length && this[index].isHtmlSpace()) index += 1
            if (index < length && this[index] == '=') {
                index += 1
                while (index < length && this[index].isHtmlSpace()) index += 1
                if (index < length && this[index] in setOf('\'', '"')) {
                    val quote = this[index++]
                    while (index < length && this[index] != quote) index += 1
                    if (index < length) index += 1
                } else {
                    while (index < length && !this[index].isHtmlSpace() && this[index] != '>') index += 1
                }
            }
        }
        return ranges
    }

    private fun String.indexOfClosingTag(
        tagName: String,
        start: Int,
    ): Int {
        var candidate = indexOf("</", start)
        while (candidate >= 0) {
            val nameStart = candidate + 2
            if (
                regionMatches(nameStart, tagName, 0, tagName.length, ignoreCase = true) &&
                getOrNull(nameStart + tagName.length)?.let { it.isHtmlSpace() || it == '>' } == true
            ) {
                return candidate
            }
            candidate = indexOf("</", candidate + 2)
        }
        return -1
    }

    private fun Char.isHtmlNameCharacter(): Boolean = isLetterOrDigit() || this in setOf('-', ':', '_')

    private fun Char.isHtmlSpace(): Boolean = this in HtmlSpaces

    private const val IframeSandbox = "allow-scripts allow-forms allow-popups-to-escape-sandbox"
    private const val MediaBlockedAttribute = "data-glosh-media-blocked"
    private val MediaSourceAttributes = setOf("src", "srcset")
    private val DeclarativeTopLayerAttributes =
        setOf(
            "popover",
            "popovertarget",
            "popovertargetaction",
            "commandfor",
            "command",
        )
    private val BlockedVoidMediaElements = setOf("embed", "frame", "fencedframe")
    private val HtmlSpaces = setOf('\t', '\n', '\u000c', '\r', ' ')
}

internal class ChromeMediaShieldStaticMarkupException(
    reason: String,
) : IllegalArgumentException(reason)
