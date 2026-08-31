package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChromeMediaShieldHtmlPrefixParserTest {
    @Test
    fun `finds an explicit head while respecting comments and quoted greater-than signs`() {
        val source =
            "\u00ef\u00bb\u00bf <!-- before --><!doctype html><!-- middle -->" +
                "<html data-value=\"a > b\"><head data-value='c > d'><title>x</title></head>"

        val point = ChromeMediaShieldHtmlPrefixParser.insertionPoint(source, 32 * 1024)

        val expected = source.indexOf("<head") + "<head data-value='c > d'>".length
        assertEquals(expected, point?.offset)
        assertEquals("", point?.prefix)
    }

    @Test
    fun `creates an implicit head immediately after the real html tag`() {
        val source = "<!doctype html><html lang=en><!-- body follows --><body></body></html>"

        val point = ChromeMediaShieldHtmlPrefixParser.insertionPoint(source, 32 * 1024)

        assertEquals(source.indexOf('>') + 1 + "<html lang=en>".length, point?.offset)
        assertEquals("<head>", point?.prefix)
        assertEquals("</head>", point?.suffix)
    }

    @Test
    fun `doctype and head text inside a comment never form an insertion point`() {
        val source = "<!-- <!doctype html><html><head> --><script>bad()</script><html><head></head>"

        assertNull(ChromeMediaShieldHtmlPrefixParser.insertionPoint(source, 32 * 1024))
    }

    @Test
    fun `executable or malformed prefix and an unclosed quoted tag fail closed`() {
        assertNull(
            ChromeMediaShieldHtmlPrefixParser.insertionPoint(
                "<!doctype html><script>bad()</script><html><head></head>",
                32 * 1024,
            ),
        )
        assertNull(
            ChromeMediaShieldHtmlPrefixParser.insertionPoint(
                "<!doctype html><html data-x=\"unterminated><head></head>",
                32 * 1024,
            ),
        )
        assertNull(
            ChromeMediaShieldHtmlPrefixParser.insertionPoint(
                "<!doctype html><html onfocus=\"bad()\"><head></head>",
                32 * 1024,
            ),
        )
        assertNull(
            ChromeMediaShieldHtmlPrefixParser.insertionPoint(
                "<!doctype html><html><head ONLOAD='bad()'></head>",
                32 * 1024,
            ),
        )
        assertNull(
            ChromeMediaShieldHtmlPrefixParser.insertionPoint(
                "<!doctype html><html / onfocus=bad()><head></head>",
                32 * 1024,
            ),
        )
        assertNull(
            ChromeMediaShieldHtmlPrefixParser.insertionPoint(
                "<!doctype html><html><head / onload=bad()></head>",
                32 * 1024,
            ),
        )
    }

    @Test
    fun `only HTML ASCII whitespace separates structural tokens`() {
        val formFeed = "<!doctype\u000chtml><html\u000c><head>"

        assertEquals(formFeed.length, ChromeMediaShieldHtmlPrefixParser.insertionPoint(formFeed, 32 * 1024)?.offset)
        assertNull(
            ChromeMediaShieldHtmlPrefixParser.insertionPoint(
                "<!doctype\u000bhtml><html><head>",
                32 * 1024,
            ),
        )
        assertNull(
            ChromeMediaShieldHtmlPrefixParser.insertionPoint(
                "<!doctype\u00a0html><html><head>",
                32 * 1024,
            ),
        )
        assertNull(
            ChromeMediaShieldHtmlPrefixParser.insertionPoint(
                "<!doctype html><html\u000b><head>",
                32 * 1024,
            ),
        )
    }
}
