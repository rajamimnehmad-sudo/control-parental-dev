package com.contentfilter.user.dag2

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagV2SafeSvgValidatorTest {
    @Test
    fun `closed static svg is accepted`() {
        val svg =
            """<svg xmlns="http://www.w3.org/2000/svg"><rect width="8" height="8" fill="#eee"/></svg>"""
                .encodeToByteArray()

        assertTrue(DagV2SafeSvgValidator.isSafe(svg))
    }

    @Test
    fun `scripts events foreign objects images and external urls fail closed`() {
        val unsafe =
            listOf(
                """<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>""",
                """<svg xmlns="http://www.w3.org/2000/svg" onload="alert(1)"></svg>""",
                """<svg xmlns="http://www.w3.org/2000/svg"><foreignObject/></svg>""",
                """<svg xmlns="http://www.w3.org/2000/svg"><image href="https://example.com/a.png"/></svg>""",
                """<svg xmlns="http://www.w3.org/2000/svg"><rect fill="url(https://example.com/a)"/></svg>""",
            )

        unsafe.forEach { assertFalse(DagV2SafeSvgValidator.isSafe(it.encodeToByteArray()), it) }
    }
}
