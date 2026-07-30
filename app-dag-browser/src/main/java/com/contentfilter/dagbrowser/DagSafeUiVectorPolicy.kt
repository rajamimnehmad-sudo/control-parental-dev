package com.contentfilter.dagbrowser

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import javax.xml.parsers.DocumentBuilderFactory

internal object DagSafeUiVectorPolicy {
    private val allowedElements =
        setOf(
            "circle",
            "clippath",
            "defs",
            "desc",
            "ellipse",
            "g",
            "line",
            "lineargradient",
            "mask",
            "path",
            "polygon",
            "polyline",
            "radialgradient",
            "rect",
            "stop",
            "svg",
            "symbol",
            "title",
            "use",
        )
    private val referenceAttributes = setOf("href", "xlink:href")
    private val externalCssPattern = Regex("""url\s*\(\s*(['"]?)(?!#)""", RegexOption.IGNORE_CASE)
    private val unsafeCssPattern =
        Regex("""@import|expression\s*\(|javascript\s*:|data\s*:""", RegexOption.IGNORE_CASE)

    fun isSafe(bytes: ByteArray): Boolean {
        if (bytes.isEmpty() || bytes.size > MaxBytes || !looksLikeSvg(bytes)) return false
        val text =
            runCatching {
                Charsets.UTF_8
                    .newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            }.getOrNull() ?: return false
        if (
            !text.trimStart().startsWith("<") ||
            Regex("""<!DOCTYPE|<!ENTITY""", RegexOption.IGNORE_CASE).containsMatchIn(text)
        ) {
            return false
        }
        val document =
            runCatching {
                val factory = DocumentBuilderFactory.newInstance()
                factory.isNamespaceAware = true
                factory.isXIncludeAware = false
                factory.setExpandEntityReferences(false)
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
                factory.setFeature("http://xml.org/sax/features/external-general-entities", false)
                factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false)
                factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalDTD", "")
                factory.setAttribute("http://javax.xml.XMLConstants/property/accessExternalSchema", "")
                factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
            }.getOrNull() ?: return false
        val root = document.documentElement ?: return false
        if (root.localName?.lowercase() != "svg" || !hasSafeDimensions(root)) return false

        var elementCount = 0
        val pending = ArrayDeque<Node>().apply { add(root) }
        while (pending.isNotEmpty()) {
            val node = pending.removeFirst()
            if (node.nodeType != Node.ELEMENT_NODE) continue
            val element = node as Element
            elementCount += 1
            if (elementCount > MaxElements) return false
            val localName = element.localName?.lowercase() ?: element.tagName.lowercase()
            if (localName !in allowedElements) return false
            for (index in 0 until element.attributes.length) {
                val attribute = element.attributes.item(index)
                val name = attribute.nodeName.lowercase()
                val value = attribute.nodeValue.orEmpty().trim()
                if (name.startsWith("on")) return false
                if (name in referenceAttributes && value.isNotEmpty() && !value.startsWith("#")) {
                    return false
                }
                if (
                    externalCssPattern.containsMatchIn(value) ||
                    unsafeCssPattern.containsMatchIn(value)
                ) {
                    return false
                }
            }
            for (index in 0 until element.childNodes.length) {
                pending.add(element.childNodes.item(index))
            }
        }
        return true
    }

    private fun looksLikeSvg(bytes: ByteArray): Boolean {
        val prefix =
            bytes
                .take(MaxSignatureBytes)
                .toByteArray()
                .toString(Charsets.UTF_8)
                .removePrefix("\uFEFF")
                .trimStart()
                .lowercase()
        return prefix.startsWith("<svg") || prefix.startsWith("<?xml")
    }

    private fun hasSafeDimensions(root: Element): Boolean {
        val width = parseLength(root.getAttribute("width"))
        val height = parseLength(root.getAttribute("height"))
        val dimensions =
            if (width != null && height != null) {
                width to height
            } else {
                parseViewBox(root.getAttribute("viewBox")) ?: return false
            }
        return dimensions.first > 0 &&
            dimensions.second > 0 &&
            dimensions.first <= MaxWidth &&
            dimensions.second <= MaxHeight &&
            dimensions.first * dimensions.second <= MaxArea
    }

    private fun parseLength(value: String): Double? {
        val match = Regex("""^\s*(\d+(?:\.\d+)?)\s*(?:px)?\s*$""").matchEntire(value)
        return match?.groupValues?.get(1)?.toDoubleOrNull()
    }

    private fun parseViewBox(value: String): Pair<Double, Double>? {
        val values = value.trim().split(Regex("""[\s,]+""")).mapNotNull(String::toDoubleOrNull)
        if (values.size != 4) return null
        return values[2] to values[3]
    }

    private const val MaxBytes = 256 * 1024
    private const val MaxSignatureBytes = 128
    private const val MaxElements = 256
    private const val MaxWidth = 256.0
    private const val MaxHeight = 160.0
    private const val MaxArea = 40_960.0
}
