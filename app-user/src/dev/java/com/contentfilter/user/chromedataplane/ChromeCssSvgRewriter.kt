package com.contentfilter.user.chromedataplane

import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.Locale

internal data class ChromeCssSvgRewriteResult(
    val css: String,
    val rewritten: Int,
    val rejected: Int,
)

/** Tokenizes CSS strings/comments/url() and replaces only admitted SVG data URL tokens. */
internal class ChromeCssSvgRewriter(
    private val registry: ChromeOriginalUiSvgRegistry,
    private val maximumCssChars: Int = 2 * 1024 * 1024,
) {
    fun rewriteDataUri(value: String): String? = decodeSvgDataUri(value)?.let(registry::register)

    fun rewrite(css: String): ChromeCssSvgRewriteResult {
        if (css.length > maximumCssChars || !css.contains("data:", ignoreCase = true)) {
            return ChromeCssSvgRewriteResult(css, 0, 0)
        }
        val output = StringBuilder(css.length)
        var index = 0
        var rewritten = 0
        var rejected = 0
        while (index < css.length) {
            when {
                css.startsWith("/*", index) -> {
                    val end = css.indexOf("*/", index + 2).let { if (it < 0) css.length else it + 2 }
                    output.append(css, index, end)
                    index = end
                }
                css[index] == '\'' || css[index] == '"' -> {
                    val end = css.stringEnd(index)
                    output.append(css, index, end)
                    index = end
                }
                css.regionMatches(index, "url", 0, 3, ignoreCase = true) && css.urlBoundary(index) -> {
                    val token = css.urlTokenOrNull(index)
                    if (token == null) {
                        output.append(css[index++])
                    } else {
                        val url = rewriteDataUri(token.value)
                        if (url == null) {
                            output.append(css, index, token.end)
                            if (token.value.trim().startsWith("data:image/svg+xml", ignoreCase = true)) rejected += 1
                        } else {
                            output.append("url(\"").append(url).append("\")")
                            rewritten += 1
                        }
                        index = token.end
                    }
                }
                else -> output.append(css[index++])
            }
        }
        return ChromeCssSvgRewriteResult(output.toString(), rewritten, rejected)
    }

    private fun decodeSvgDataUri(raw: String): ByteArray? {
        val value = cssUnescape(raw.trim().trim('"', '\'')) ?: return null
        if (!value.startsWith(DataPrefix, ignoreCase = true)) return null
        val comma = value.indexOf(',')
        if (comma < 0) return null
        val metadata = value.substring(5, comma).split(';')
        if (!metadata.first().equals(SvgMimeType, ignoreCase = true)) return null
        val parameters = metadata.drop(1).map { it.trim().lowercase(Locale.US) }.filter(String::isNotEmpty)
        if (parameters.any { it !in AllowedDataParameters } || parameters.size != parameters.distinct().size) {
            return null
        }
        if ("base64" in parameters && parameters.any { it != "base64" }) return null
        if ("utf8" in parameters && parameters.any { it != "utf8" }) return null
        val encoded = value.substring(comma + 1)
        return if (parameters.any { it.equals("base64", true) }) {
            runCatching { Base64.getDecoder().decode(encoded.filterNot(Char::isWhitespace)) }.getOrNull()
        } else {
            strictPercentDecode(encoded)
        }
    }

    private fun strictPercentDecode(value: String): ByteArray? {
        val output = ByteArrayOutputStream(value.length)
        var index = 0
        while (index < value.length) {
            if (value[index] == '%') {
                if (index + 2 >= value.length) return null
                val byte = value.substring(index + 1, index + 3).toIntOrNull(16) ?: return null
                output.write(byte)
                index += 3
            } else {
                val codePoint = value.codePointAt(index)
                output.write(String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8))
                index += Character.charCount(codePoint)
            }
        }
        return output.toByteArray()
    }

    private fun cssUnescape(value: String): String? {
        val output = StringBuilder(value.length)
        var index = 0
        while (index < value.length) {
            val character = value[index++]
            if (character != '\\') {
                output.append(character)
                continue
            }
            if (index >= value.length) return null
            if (value[index] == '\n' || value[index] == '\r' || value[index] == '\u000c') return null
            var end = index
            while (end < value.length && end - index < 6 && value[end].digitToIntOrNull(16) != null) end += 1
            if (end > index) {
                val codePoint = value.substring(index, end).toInt(16)
                if (codePoint == 0 || codePoint > Character.MAX_CODE_POINT) return null
                output.appendCodePoint(codePoint)
                index = end
                if (index < value.length && value[index].isWhitespace()) index += 1
            } else {
                output.append(value[index++])
            }
        }
        return output.toString()
    }

    private fun String.urlBoundary(index: Int): Boolean =
        (index == 0 || !this[index - 1].isCssIdentifier()) &&
            drop(index + 3).indexOfFirst { !it.isWhitespace() }.let { it >= 0 && get(index + 3 + it) == '(' }

    private fun String.urlTokenOrNull(start: Int): UrlToken? {
        var index = start + 3
        while (index < length && this[index].isWhitespace()) index += 1
        if (getOrNull(index++) != '(') return null
        while (index < length && this[index].isWhitespace()) index += 1
        val quote = getOrNull(index).takeIf { it == '\'' || it == '"' }
        if (quote != null) index += 1
        val valueStart = index
        var escaped = false
        while (index < length) {
            val character = this[index]
            if (escaped) {
                escaped = false
                index += 1
                continue
            }
            if (character == '\\') {
                escaped = true
                index += 1
                continue
            }
            if (quote != null && character == quote) {
                val value = substring(valueStart, index)
                index += 1
                while (index < length && this[index].isWhitespace()) index += 1
                return if (getOrNull(index) == ')') UrlToken(value, index + 1) else null
            }
            if (quote == null && character == ')') return UrlToken(substring(valueStart, index).trimEnd(), index + 1)
            if (quote == null && (character == '\'' || character == '"' || character == '(' || character.isISOControl())) return null
            index += 1
        }
        return null
    }

    private fun String.stringEnd(start: Int): Int {
        val quote = this[start]
        var index = start + 1
        var escaped = false
        while (index < length) {
            val character = this[index++]
            if (escaped) {
                escaped = false
            } else if (character == '\\') {
                escaped = true
            } else if (character == quote) {
                return index
            }
        }
        return length
    }

    private fun Char.isCssIdentifier(): Boolean = isLetterOrDigit() || this == '_' || this == '-'

    private data class UrlToken(val value: String, val end: Int)

    private companion object {
        const val DataPrefix = "data:"
        const val SvgMimeType = "image/svg+xml"
        val AllowedDataParameters = setOf("base64", "utf8", "charset=utf-8", "charset=us-ascii")
    }
}
