package com.contentfilter.user.chromedataplane

/** Small inert paint grammar: no resource references, escapes, variables, or global selectors. */
internal object ChromeOriginalUiSvgStylePolicy {
    private const val MaximumBytes = 8192
    private val rule = Regex("([.#][A-Za-z_][A-Za-z0-9_-]*(?:\\s*,\\s*[.#][A-Za-z_][A-Za-z0-9_-]*)*)\\s*\\{([^{}]+)}")
    private val declaration = Regex("([a-z-]+)\\s*:\\s*(.+)", RegexOption.DOT_MATCHES_ALL)
    private val literal = Regex("(?:#[A-Fa-f0-9]{3,8}|[A-Za-z]+|[-+0-9.,%\\s]+(?:px|em|rem)?|(?:rgb|rgba|hsl|hsla)\\([-+0-9.,%\\s]+\\))")
    private val properties = setOf(
        "fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit",
        "stroke-dasharray", "stroke-dashoffset", "opacity", "fill-opacity", "stroke-opacity", "fill-rule", "clip-rule",
    )

    fun accepts(css: String): Boolean {
        if (css.isBlank() || css.length > MaximumBytes) return false
        var offset = 0
        for (match in rule.findAll(css)) {
            if (css.substring(offset, match.range.first).isNotBlank()) return false
            val declarations = match.groupValues[2].split(';').map(String::trim).filter(String::isNotEmpty)
            if (declarations.isEmpty()) return false
            for (text in declarations) {
                val parsed = declaration.matchEntire(text) ?: return false
                if (parsed.groupValues[1] !in properties || !literal.matches(parsed.groupValues[2].trim())) return false
            }
            offset = match.range.last + 1
        }
        return offset > 0 && css.substring(offset).isBlank()
    }
}
