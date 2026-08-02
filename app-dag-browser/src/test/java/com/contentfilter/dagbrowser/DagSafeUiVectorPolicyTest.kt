package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagSafeUiVectorPolicyTest {
    @Test
    fun `passive bounded favorite icon is allowed`() {
        assertTrue(
            safe(
                """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24">
                  <path fill="none" stroke="currentColor"
                    d="M12 21s-8-4.5-8-11a4 4 0 0 1 7-2.6A4 4 0 0 1 20 10c0 6.5-8 11-8 11z"/>
                </svg>
                """,
            ),
        )
    }

    @Test
    fun `passive logo with an internal clip path is allowed`() {
        assertTrue(
            safe(
                """
                <svg width="145" height="20" viewBox="0 0 145 20" fill="none"
                  xmlns="http://www.w3.org/2000/svg">
                  <g clip-path="url(#clip0)">
                    <path d="M0 0h145v20H0z" fill="#3B3E3D"/>
                  </g>
                  <defs>
                    <clipPath id="clip0"><rect width="145" height="20" fill="white"/></clipPath>
                  </defs>
                </svg>
                """,
            ),
        )
    }

    @Test
    fun `small vector with active content stays blocked`() {
        assertFalse(
            safe(
                """
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24">
                  <script>alert(1)</script>
                  <path d="M0 0h24v24z"/>
                </svg>
                """,
            ),
        )
        assertFalse(
            safe(
                """
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24">
                  <image href="https://example.test/photo.jpg" width="24" height="24"/>
                </svg>
                """,
            ),
        )
    }

    @Test
    fun `external references and event handlers stay blocked`() {
        assertFalse(
            safe(
                """
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24">
                  <use href="https://example.test/sprite.svg#heart"/>
                </svg>
                """,
            ),
        )
        assertFalse(
            safe(
                """
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24"
                  onload="alert(1)"><path d="M0 0h24v24z"/></svg>
                """,
            ),
        )
    }

    @Test
    fun `vector art outside interface budget stays blocked`() {
        assertFalse(
            safe(
                """
                <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 800 600">
                  <path d="M0 0h800v600z"/>
                </svg>
                """,
            ),
        )
    }

    @Test
    fun `doctype and malformed utf8 stay blocked`() {
        assertFalse(
            safe(
                """
                <!DOCTYPE svg [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <svg xmlns="http://www.w3.org/2000/svg" width="24" height="24">
                  <text>&xxe;</text>
                </svg>
                """,
            ),
        )
        assertFalse(DagSafeUiVectorPolicy.isSafe(byteArrayOf(0xC3.toByte(), 0x28)))
    }

    private fun safe(svg: String) = DagSafeUiVectorPolicy.isSafe(svg.trimIndent().toByteArray())
}
