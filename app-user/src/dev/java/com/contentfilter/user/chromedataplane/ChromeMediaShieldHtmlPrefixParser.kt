package com.contentfilter.user.chromedataplane

import java.util.Locale

internal data class ChromeMediaShieldInsertionPoint(
    val offset: Int,
    val prefix: String,
    val suffix: String,
)

/** Conservative, bounded parser for the only prefix in which H19 installs its parser-first shield. */
internal object ChromeMediaShieldHtmlPrefixParser {
    fun insertionPoint(
        source: String,
        maximumPrefixBytes: Int,
    ): ChromeMediaShieldInsertionPoint? {
        val limit = minOf(source.length, maximumPrefixBytes)
        var offset = source.skipBomAndTrivia(0, limit) ?: return null
        val doctypeEnd = source.doctypeEndOrNull(offset, limit) ?: return null
        offset = source.skipTrivia(doctypeEnd, limit) ?: return null
        val htmlEnd = source.startTagEndOrNull(offset, limit, "html") ?: return null
        offset = source.skipTrivia(htmlEnd, limit) ?: return null
        val headEnd = source.startTagEndOrNull(offset, limit, "head")
        return if (headEnd != null) {
            ChromeMediaShieldInsertionPoint(headEnd, prefix = "", suffix = "")
        } else if (source.hasStartTagNameAt(offset, "head")) {
            // A present but unsafe/malformed head must not be mistaken for an omitted head.
            null
        } else {
            ChromeMediaShieldInsertionPoint(htmlEnd, prefix = "<head>", suffix = "</head>")
        }
    }

    private fun String.hasStartTagNameAt(
        start: Int,
        expectedName: String,
    ): Boolean {
        if (getOrNull(start) != '<') return false
        val nameStart = start + 1
        if (!regionMatches(nameStart, expectedName, 0, expectedName.length, ignoreCase = true)) return false
        val afterName = getOrNull(nameStart + expectedName.length)
        return afterName == null || afterName.isHtmlSpace() || afterName in setOf('>', '/')
    }

    private fun String.skipBomAndTrivia(
        start: Int,
        limit: Int,
    ): Int? {
        var offset = start
        if (startsWith(Utf8BomAsLatin1, offset)) offset += Utf8BomAsLatin1.length
        if (getOrNull(offset) == '\ufeff') offset += 1
        return skipTrivia(offset, limit)
    }

    private fun String.skipTrivia(
        start: Int,
        limit: Int,
    ): Int? {
        var offset = start
        while (offset < limit) {
            while (offset < limit && this[offset].isHtmlSpace()) offset += 1
            if (!startsWith("<!--", offset)) return offset
            val end = indexOf("-->", offset + 4)
            if (end < 0 || end + 3 > limit) return null
            offset = end + 3
        }
        return offset
    }

    private fun String.doctypeEndOrNull(
        start: Int,
        limit: Int,
    ): Int? {
        if (!regionMatches(start, DoctypeStart, 0, DoctypeStart.length, ignoreCase = true)) return null
        var offset = start + DoctypeStart.length
        if (getOrNull(offset)?.isHtmlSpace() != true) return null
        while (offset < limit && this[offset].isHtmlSpace()) offset += 1
        val nameStart = offset
        while (offset < limit && this[offset].isHtmlNameCharacter()) offset += 1
        if (!substring(nameStart, offset).equals("html", ignoreCase = true)) return null
        return tagEndOrNull(offset, limit)
    }

    private fun String.startTagEndOrNull(
        start: Int,
        limit: Int,
        expectedName: String,
    ): Int? {
        if (getOrNull(start) != '<' || getOrNull(start + 1) in setOf('/', '!', '?', null)) return null
        var offset = start + 1
        val nameStart = offset
        while (offset < limit && this[offset].isHtmlNameCharacter()) offset += 1
        if (!substring(nameStart, offset).lowercase(Locale.US).equals(expectedName)) return null
        if (getOrNull(offset)?.let { !it.isHtmlSpace() && it !in setOf('>', '/') } != false) return null
        val end = tagEndOrNull(offset, limit) ?: return null
        return end.takeIf { !hasExecutableEventAttribute(offset, end) }
    }

    /** The html/head prefix is parsed before our injection, so inline event handlers are unsafe. */
    private fun String.hasExecutableEventAttribute(
        attributesStart: Int,
        tagEnd: Int,
    ): Boolean {
        var offset = attributesStart
        while (offset < tagEnd - 1) {
            while (offset < tagEnd - 1 && this[offset].isHtmlSpace()) offset += 1
            // html/head self-closing syntax is neither useful nor safe here. HTML may reconsume
            // attributes after a stray solidus, so treating '/' as the end of attributes can hide
            // an executable on* handler from this conservative prefix gate.
            if (offset >= tagEnd - 1) break
            if (this[offset] == '/') return true
            val nameStart = offset
            while (
                offset < tagEnd - 1 &&
                !this[offset].isHtmlSpace() &&
                this[offset] !in setOf('=', '>', '/', '\u0000')
            ) {
                offset += 1
            }
            if (offset == nameStart) return true
            val name = substring(nameStart, offset).lowercase(Locale.US)
            if (name.length > 2 && name.startsWith("on")) return true
            while (offset < tagEnd - 1 && this[offset].isHtmlSpace()) offset += 1
            if (getOrNull(offset) != '=') continue
            offset += 1
            while (offset < tagEnd - 1 && this[offset].isHtmlSpace()) offset += 1
            val quote = getOrNull(offset)
            if (quote == '\'' || quote == '"') {
                offset += 1
                while (offset < tagEnd - 1 && this[offset] != quote) offset += 1
                if (offset >= tagEnd - 1) return true
                offset += 1
            } else {
                while (offset < tagEnd - 1 && !this[offset].isHtmlSpace() && this[offset] != '>') offset += 1
            }
        }
        return false
    }

    private fun String.tagEndOrNull(
        start: Int,
        limit: Int,
    ): Int? {
        var quote: Char? = null
        var offset = start
        while (offset < limit) {
            val character = this[offset]
            when {
                quote != null && character == quote -> quote = null
                quote == null && character in setOf('\'', '"') -> quote = character
                quote == null && character == '>' -> return offset + 1
            }
            offset += 1
        }
        return null
    }

    private fun Char.isHtmlNameCharacter(): Boolean = isLetterOrDigit() || this in setOf('-', ':', '_')

    private fun Char.isHtmlSpace(): Boolean = this in HtmlSpaces

    private const val DoctypeStart = "<!doctype"
    private const val Utf8BomAsLatin1 = "\u00ef\u00bb\u00bf"
    private val HtmlSpaces = setOf('\t', '\n', '\u000c', '\r', ' ')
}
