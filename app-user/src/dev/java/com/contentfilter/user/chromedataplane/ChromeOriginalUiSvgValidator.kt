package com.contentfilter.user.chromedataplane

import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

internal sealed interface ChromeOriginalUiSvgValidation {
    data class Valid(val bytes: ByteArray) : ChromeOriginalUiSvgValidation

    data class Invalid(val reason: String) : ChromeOriginalUiSvgValidation
}

/** Fail-closed structural authority. Validation never rewrites the bytes it authorizes. */
internal class ChromeOriginalUiSvgValidator(
    private val limits: Limits = Limits(),
) {
    data class Limits(
        val maximumBytes: Int = 96 * 1024,
        val maximumNodes: Int = 512,
        val maximumDepth: Int = 24,
        val maximumPathBytes: Int = 48 * 1024,
        val maximumAttributeBytes: Int = 64 * 1024,
    )

    fun validate(
        bytes: ByteArray,
        contentType: String,
    ): ChromeOriginalUiSvgValidation {
        if (contentType.trim().lowercase(Locale.US) != SvgMimeType) return invalid("mime")
        if (bytes.isEmpty() || bytes.size > limits.maximumBytes || bytes.any { it == 0.toByte() }) {
            return invalid("size")
        }
        // Android's platform DocumentBuilderFactory does not expose the Xerces feature switches
        // available on the host JDK. Reject active XML over the complete bounded input before the
        // parser sees it, then retain an entity resolver as a second fail-closed boundary.
        val asciiDocument = bytes.toString(Charsets.ISO_8859_1)
        if (ForbiddenXml.containsMatchIn(asciiDocument)) return invalid("active_xml")
        val document =
            runCatching {
                val factory = DocumentBuilderFactory.newInstance()
                factory.isNamespaceAware = true
                factory.setExpandEntityReferences(false)
                factory.newDocumentBuilder().apply {
                    setEntityResolver { _, _ -> throw IllegalArgumentException("external_entity") }
                }.parse(ByteArrayInputStream(bytes))
            }.getOrElse { return invalid("xml") }
        if (document.doctype != null) return invalid("doctype")
        val root = document.documentElement ?: return invalid("root")
        if (root.localName != "svg" || root.namespaceURI != SvgNamespace) return invalid("namespace")
        val state = State()
        if (!visit(root, 1, state)) return invalid(state.reason)
        if (!dimensionsValid(root)) return invalid("dimensions")
        if (!state.references.all(state.ids::contains)) return invalid("reference")
        return ChromeOriginalUiSvgValidation.Valid(bytes.copyOf())
    }

    private fun visit(
        element: Element,
        depth: Int,
        state: State,
    ): Boolean {
        if (depth > limits.maximumDepth || ++state.nodes > limits.maximumNodes) return state.fail("complexity")
        val name = element.localName ?: return state.fail("element")
        if (element.namespaceURI != SvgNamespace || name !in AllowedElements) return state.fail("element")
        val attributes = element.attributes
        for (index in 0 until attributes.length) {
            val attribute = attributes.item(index)
            val attributeName = attribute.localName ?: attribute.nodeName
            val qualifiedName = attribute.nodeName.lowercase(Locale.US)
            val value = attribute.nodeValue ?: ""
            state.attributeBytes += qualifiedName.length + value.length
            if (state.attributeBytes > limits.maximumAttributeBytes) return state.fail("attributes")
            if (qualifiedName.startsWith("on")) return state.fail("active_attribute")
            if (qualifiedName == "xmlns:xlink") {
                if (value != XlinkNamespace) return state.fail("namespace")
                continue
            }
            if (!attributeAllowed(name, qualifiedName)) return state.fail("attribute")
            if (attributeName == "id") {
                if (!SafeId.matches(value) || !state.ids.add(value)) return state.fail("id")
            }
            if (qualifiedName == "href" || qualifiedName == "xlink:href") {
                if (!value.startsWith('#') || !SafeId.matches(value.drop(1))) return state.fail("href")
                state.references += value.drop(1)
            }
            if (qualifiedName in UrlReferenceAttributes) {
                if (!collectInternalUrlReferences(value, state.references)) return state.fail("url")
            }
            if (qualifiedName == "style" && !safeStyle(value, state.references)) return state.fail("style")
            if (qualifiedName == "d") {
                if (!PathCharacters.matches(value)) return state.fail("path")
                state.pathBytes += value.length
                if (state.pathBytes > limits.maximumPathBytes) return state.fail("path_complexity")
            }
            if (value.contains(ForbiddenScheme, ignoreCase = true)) return state.fail("external_resource")
        }
        var child = element.firstChild
        while (child != null) {
            when (child.nodeType) {
                Node.ELEMENT_NODE -> if (!visit(child as Element, depth + 1, state)) return false
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    if (name != "title" && name != "desc" && child.nodeValue.orEmpty().isNotBlank()) {
                        return state.fail("text")
                    }
                }
                Node.COMMENT_NODE -> Unit
                Node.PROCESSING_INSTRUCTION_NODE, Node.ENTITY_REFERENCE_NODE -> return state.fail("active_node")
            }
            child = child.nextSibling
        }
        return true
    }

    private fun attributeAllowed(
        element: String,
        name: String,
    ): Boolean = name in GlobalAttributes || name in ElementAttributes[element].orEmpty()

    private fun dimensionsValid(root: Element): Boolean {
        val viewBox = root.getAttribute("viewBox").ifBlank { root.getAttribute("viewbox") }
        val viewBoxValid =
            if (viewBox.isBlank()) {
                false
            } else {
                val values =
                    viewBox.trim().split(
                        NumberSeparators,
                    ).filter(String::isNotBlank).mapNotNull(String::toDoubleOrNull)
                values.size == 4 && values.all(Double::isFinite) && values[2] > 0 && values[3] > 0 &&
                    values[2] <= MaximumDimension && values[3] <= MaximumDimension
            }
        val width = root.getAttribute("width").dimensionOrNull()
        val height = root.getAttribute("height").dimensionOrNull()
        return viewBoxValid || (width != null && height != null)
    }

    private fun String.dimensionOrNull(): Double? {
        val match = Dimension.matchEntire(trim()) ?: return null
        return match.groupValues[1].toDoubleOrNull()?.takeIf { it > 0 && it <= MaximumDimension }
    }

    private fun safeStyle(
        value: String,
        references: MutableSet<String>,
    ): Boolean {
        if (value.length > MaximumStyleBytes || CssActive.containsMatchIn(value)) return false
        var index = 0
        while (true) {
            val start = value.indexOf("url(", index, ignoreCase = true)
            if (start < 0) return true
            val end = value.indexOf(')', start + 4)
            if (end < 0) return false
            val target = value.substring(start + 4, end).trim().trim('"', '\'')
            if (!target.startsWith('#') || !SafeId.matches(target.drop(1))) return false
            references += target.drop(1)
            index = end + 1
        }
    }

    private fun collectInternalUrlReferences(
        value: String,
        references: MutableSet<String>,
    ): Boolean {
        val trimmed = value.trim()
        if (trimmed.equals("none", true) || trimmed.equals("currentColor", true) || !trimmed.contains("url(", true)) {
            return !trimmed.contains(':')
        }
        return safeStyle(trimmed, references)
    }

    private fun invalid(reason: String) = ChromeOriginalUiSvgValidation.Invalid(reason)

    private class State {
        var nodes = 0
        var pathBytes = 0
        var attributeBytes = 0
        var reason = "invalid"
        val ids = linkedSetOf<String>()
        val references = linkedSetOf<String>()

        fun fail(value: String): Boolean {
            reason = value
            return false
        }
    }

    private companion object {
        const val SvgMimeType = "image/svg+xml"
        const val SvgNamespace = "http://www.w3.org/2000/svg"
        const val XlinkNamespace = "http://www.w3.org/1999/xlink"
        const val MaximumDimension = 16_384.0
        const val MaximumStyleBytes = 8192
        const val ForbiddenScheme = "javascript:"
        val ForbiddenXml = Regex("(?is)<!DOCTYPE|<!ENTITY|<\\?xml-stylesheet|<\\?(?!xml(?:\\s|\\?>))")
        val CssActive =
            Regex("(?is)@import|expression\\s*\\(|-moz-binding|behavior\\s*:|https?\\s*:|data\\s*:|blob\\s*:")
        val SafeId = Regex("[A-Za-z_][A-Za-z0-9_.:-]{0,127}")
        val PathCharacters = Regex("[MmZzLlHhVvCcSsQqTtAaEe0-9+.,\\-\\s]*")
        val NumberSeparators = Regex("[\\s,]+")
        val Dimension = Regex("([0-9]+(?:\\.[0-9]+)?)(?:px)?", RegexOption.IGNORE_CASE)
        val AllowedElements =
            setOf(
                "svg", "g", "defs", "symbol", "use", "path", "rect", "circle", "ellipse", "line",
                "polyline", "polygon", "title", "desc", "clipPath", "mask", "linearGradient",
                "radialGradient", "stop", "marker",
            )
        val GlobalAttributes =
            setOf(
                "id", "class", "style", "transform", "fill", "fill-opacity", "fill-rule", "stroke",
                "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit", "stroke-dasharray",
                "stroke-dashoffset", "stroke-opacity", "opacity", "clip-path", "mask", "role", "aria-label",
                "aria-hidden", "focusable", "tabindex", "pointer-events", "vector-effect",
            )
        val UrlReferenceAttributes =
            setOf("fill", "stroke", "clip-path", "mask", "marker-start", "marker-mid", "marker-end")
        val ElementAttributes =
            mapOf(
                "svg" to setOf("xmlns", "width", "height", "viewbox", "viewBox", "preserveaspectratio", "preserveAspectRatio", "x", "y"),
                "use" to setOf("href", "xlink:href", "x", "y", "width", "height"),
                "path" to setOf("d", "pathlength", "pathLength"),
                "rect" to setOf("x", "y", "width", "height", "rx", "ry"),
                "circle" to setOf("cx", "cy", "r"),
                "ellipse" to setOf("cx", "cy", "rx", "ry"),
                "line" to setOf("x1", "y1", "x2", "y2"),
                "polyline" to setOf("points"),
                "polygon" to setOf("points"),
                "linearGradient" to setOf("x1", "y1", "x2", "y2", "gradientunits", "gradientUnits", "gradienttransform", "gradientTransform", "spreadmethod", "spreadMethod", "href", "xlink:href"),
                "radialGradient" to setOf("cx", "cy", "r", "fx", "fy", "fr", "gradientunits", "gradientUnits", "gradienttransform", "gradientTransform", "spreadmethod", "spreadMethod", "href", "xlink:href"),
                "stop" to setOf("offset", "stop-color", "stop-opacity"),
                "marker" to setOf("markerwidth", "markerWidth", "markerheight", "markerHeight", "refx", "refX", "refy", "refY", "orient", "markerunits", "markerUnits", "viewbox", "viewBox", "preserveaspectratio", "preserveAspectRatio"),
            )
    }
}
