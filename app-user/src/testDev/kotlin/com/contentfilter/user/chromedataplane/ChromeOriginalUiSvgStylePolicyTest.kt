package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertIs

class ChromeOriginalUiSvgStylePolicyTest {
    @Test
    fun `inert original icon styles preserve exact bytes`() {
        val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"><defs><style>.st0 { fill: #bb010f; } .a,.b{stroke:#464646;stroke-width:.28px;stroke-linecap:round;}</style></defs><path class="st0" d="M0 0h20v20z"/></svg>""".toByteArray()
        val result =
            assertIs<ChromeOriginalUiSvgValidation.Valid>(ChromeOriginalUiSvgValidator().validate(svg, "image/svg+xml"))
        assertContentEquals(svg, result.bytes)
    }

    @Test
    fun `resource syntax escapes variables active selectors and fragmented styles fail closed`() {
        val styles =
            listOf(
                ".x{fill:url(https://example.com/a.svg)}", ".x{fill:u\\72l(foo)}", ".x{fill:var(--paint)}",
                ".x{background:red}", "@import 'x';", "*{fill:red}", ".x{animation:foo}", ".x{fill:red} trailing",
                ".x{fill:env(foo)}", ".x{fill:expression(foo)}", ".x{fill:red!important}",
                ".x{fill:<![CDATA[u]]><![CDATA[rl(foo)]]>}", ".x{fill:<g/>red}",
            )
        for (style in styles) {
            val svg = """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 20 20"><style>$style</style><path d="M0 0h20v20z"/></svg>""".toByteArray()
            assertIs<ChromeOriginalUiSvgValidation.Invalid>(
                ChromeOriginalUiSvgValidator().validate(svg, "image/svg+xml"),
                style,
            )
        }
    }
}
