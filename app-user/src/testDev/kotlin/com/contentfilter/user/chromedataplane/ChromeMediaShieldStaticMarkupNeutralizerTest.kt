package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class ChromeMediaShieldStaticMarkupNeutralizerTest {
    @Test
    fun `rewrites only real iframe and declarative shadow tags`() {
        val source =
            "<template shadowrootmode=\"closed\"><canvas></canvas></template>" +
                "<iframe srcdoc=\"&lt;canvas&gt;\" src=\"https://example.test/f\"></iframe>"

        val output = ChromeMediaShieldStaticMarkupNeutralizer.neutralize(source)

        assertContains(output, "data-glosh-blocked-shadowrootmode=\"closed\"")
        assertContains(output, "data-glosh-blocked-srcdoc=\"&lt;canvas&gt;\"")
        assertContains(output, "sandbox=\"allow-scripts allow-forms allow-popups-to-escape-sandbox\"")
        assertFalse(output.contains("<template shadowrootmode"))
    }

    @Test
    fun `does not rewrite script style comments text or quoted attribute values`() {
        val source =
            "<script>const a='<iframe src=x>';const b='shadowrootmode=';</script>" +
                "<style>.x::after{content:'<iframe shadowrootmode='}</style>" +
                "<!-- <iframe shadowrootmode=open> --><p title=\"shadowrootmode=\">&lt;iframe</p>"

        assertEquals(source, ChromeMediaShieldStaticMarkupNeutralizer.neutralize(source))
    }

    @Test
    fun `replaces an existing iframe sandbox and preserves quoted greater-than signs`() {
        val source =
            "<iframe title=\"a > b\" sandbox=\"allow-scripts allow-same-origin\" " +
                "src=\"https://example.test\"></iframe>"

        val output = ChromeMediaShieldStaticMarkupNeutralizer.neutralize(source)

        assertContains(output, "title=\"a > b\"")
        assertContains(output, "sandbox=\"allow-scripts allow-forms allow-popups-to-escape-sandbox\"")
        assertFalse(output.contains("allow-same-origin"))
    }

    @Test
    fun `duplicate srcdoc and declarative shadow attributes are all neutralized`() {
        val source =
            "<template shadowrootmode=open SHADOWROOTMODE=closed><canvas></canvas></template>" +
                "<iframe srcdoc='<canvas></canvas>' SRCDOC='<svg></svg>'></iframe>"

        val output = ChromeMediaShieldStaticMarkupNeutralizer.neutralize(source)

        assertEquals(2, "data-glosh-blocked-shadowrootmode".toRegex().findAll(output).count())
        assertEquals(2, "data-glosh-blocked-srcdoc".toRegex().findAll(output).count())
        assertFalse(output.contains(" shadowrootmode=", ignoreCase = true))
        assertFalse(output.contains(" srcdoc=", ignoreCase = true))
    }

    @Test
    fun `popup targets are forced into the transformed current browsing context`() {
        val source =
            "<base target='_blank'><a target=popup href='/a'>A</a>" +
                "<area TARGET='_blank' href='/b'><form target='_blank'>" +
                "<button formtarget='_blank'></button><input formtarget=popup></form>"

        val output = ChromeMediaShieldStaticMarkupNeutralizer.neutralize(source)

        assertEquals(4, "\\starget=\"_self\"".toRegex().findAll(output).count())
        assertEquals(2, "\\sformtarget=\"_self\"".toRegex().findAll(output).count())
        assertFalse(output.contains("_blank"))
        assertFalse(output.contains("target=popup"))
    }

    @Test
    fun `author markup cannot forge reserved Glosh authority attributes`() {
        val source =
            "<svg data-glosh-icon-safe='1' DATA-GLOSH-MEDIA-BLOCKED=0><foreignObject></foreignObject></svg>" +
                "<iframe data-glosh-network-frame=1 src='about:blank'></iframe>" +
                "<div data-glosh-h19-ready=true></div>"

        val output = ChromeMediaShieldStaticMarkupNeutralizer.neutralize(source)

        assertFalse(output.contains("data-glosh-icon-safe", ignoreCase = true))
        assertFalse(output.contains("data-glosh-media-blocked=0", ignoreCase = true))
        assertEquals(2, "data-glosh-media-blocked=\"1\"".toRegex().findAll(output).count())
        assertFalse(output.contains("data-glosh-network-frame", ignoreCase = true))
        assertFalse(output.contains("data-glosh-h19-ready", ignoreCase = true))
        assertContains(output, "sandbox=\"allow-scripts allow-forms allow-popups-to-escape-sandbox\"")
    }

    @Test
    fun `browsing-context object and embed markup becomes inert templates`() {
        val source =
            "<object data='about:blank' type='text/html'><span>fallback</span></object>" +
                "<embed src='about:blank' type='text/html'>"

        val output = ChromeMediaShieldStaticMarkupNeutralizer.neutralize(source)

        assertContains(output, "<template data-glosh-blocked-element=\"object\">")
        assertContains(output, "<span>fallback</span></template>")
        assertContains(output, "<template data-glosh-blocked-element=\"embed\"></template>")
        assertFalse(output.contains("<object", ignoreCase = true))
        assertFalse(output.contains("<embed", ignoreCase = true))
    }

    @Test
    fun `parser created raster sinks are inline protected before their first paint`() {
        val source =
            "<canvas style='width:20px&amp;'></canvas>" +
                "<svg width='24' height='24'><path d='M0 0'/></svg>" +
                "<iframe src='https://example.test/frame'></iframe>"

        val output = ChromeMediaShieldStaticMarkupNeutralizer.neutralize(source)

        assertEquals(3, "data-glosh-media-blocked=\"1\"".toRegex().findAll(output).count())
        assertContains(output, "width:20px&amp;;visibility:hidden!important;opacity:0!important")
        assertContains(output, "sandbox=\"allow-scripts allow-forms allow-popups-to-escape-sandbox\"")
    }

    @Test
    fun `local picture source cannot be selected before bootstrap sanitization`() {
        val source =
            "<picture><source srcset='data:image/png;base64,AAAA 1x, https://example.test/a.png 2x'>" +
                "<img src='https://example.test/fallback.png'></picture>"

        val output = ChromeMediaShieldStaticMarkupNeutralizer.neutralize(source)

        assertFalse(output.contains("<source srcset", ignoreCase = true))
        assertContains(output, "data-glosh-blocked-srcset=\"1\"")
        assertContains(output, "data-glosh-media-blocked=\"1\"")
        assertContains(output, "<img src='https://example.test/fallback.png'>")
    }

    @Test
    fun `local img input and CSS URL are preprotected without changing ordinary network media`() {
        val source =
            "<img src='data:image/png;base64,AAAA'><input type=image src=blob:https://example.test/id>" +
                "<div style=background-image:url(data:image/png;base64,AAAA)></div>" +
                "<img src='https://example.test/ordinary.png'>"

        val output = ChromeMediaShieldStaticMarkupNeutralizer.neutralize(source)

        assertEquals(3, "data-glosh-media-blocked=\"1\"".toRegex().findAll(output).count())
        assertContains(output, "<img src='https://example.test/ordinary.png'>")
    }

    @Test
    fun `self closing object becomes a balanced inert template without swallowing the suffix`() {
        val source =
            "<object data='https://example.test/object/' />" +
                "<p>ordinary suffix</p>"

        val output = ChromeMediaShieldStaticMarkupNeutralizer.neutralize(source)

        assertEquals(
            "<template data-glosh-blocked-element=\"object\"></template><p>ordinary suffix</p>",
            output,
        )
    }

    @Test
    fun `slash inside an object attribute does not make a paired object self closing`() {
        val quotedSource =
            "<object data='https://example.test/object/'><span>quoted</span></object>"
        val unquotedSource =
            "<object data=https://example.test/object/><span>unquoted</span></object>"

        val quotedOutput = ChromeMediaShieldStaticMarkupNeutralizer.neutralize(quotedSource)
        val unquotedOutput = ChromeMediaShieldStaticMarkupNeutralizer.neutralize(unquotedSource)

        assertEquals(
            "<template data-glosh-blocked-element=\"object\">" +
                "<span>quoted</span></template>",
            quotedOutput,
        )
        assertEquals(
            "<template data-glosh-blocked-element=\"object\">" +
                "<span>unquoted</span></template>",
            unquotedOutput,
        )
    }

    @Test
    fun `malformed markup cannot make the structural neutralizer silently skip a media suffix`() {
        assertFailsWith<ChromeMediaShieldStaticMarkupException> {
            ChromeMediaShieldStaticMarkupNeutralizer.neutralize(
                "<div title='unterminated><iframe srcdoc='<canvas></canvas>'></iframe>",
            )
        }
        assertFailsWith<ChromeMediaShieldStaticMarkupException> {
            ChromeMediaShieldStaticMarkupNeutralizer.neutralize(
                "<script>const escaped = document.createElement('iframe')",
            )
        }
    }

    @Test
    fun `non HTML Unicode whitespace cannot split tag or authority attributes`() {
        val verticalTab = '\u000b'
        val nonBreakingSpace = '\u00a0'
        val source = "<canvas${verticalTab}id=x></canvas><iframe${nonBreakingSpace}srcdoc=x></iframe>"

        assertEquals(source, ChromeMediaShieldStaticMarkupNeutralizer.neutralize(source))
    }

    @Test
    fun `meta CSP is rewritten through the bounded policy callback and ambiguity fails closed`() {
        val source =
            "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'self'; connect-src 'none'\">"
        val output =
            ChromeMediaShieldStaticMarkupNeutralizer.neutralize(source) { policy ->
                "$policy; connect-src https://glosh-photos.test"
            }

        assertContains(output, "connect-src https://glosh-photos.test")
        assertFailsWith<ChromeMediaShieldStaticMarkupException> {
            ChromeMediaShieldStaticMarkupNeutralizer.neutralize(
                "<meta http-equiv='Content-Security-Policy' content='a' CONTENT='b'>",
            ) { it }
        }
    }
}
