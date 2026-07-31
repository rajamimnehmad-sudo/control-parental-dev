package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagTabThumbnailKeyPolicyTest {
    @Test
    fun `only fixed lowercase hexadecimal keys can address preview files`() {
        assertTrue(DagTabThumbnailKeyPolicy.isValid("0123456789abcdef0123456789abcdef"))
        assertFalse(DagTabThumbnailKeyPolicy.isValid("../preview"))
        assertFalse(DagTabThumbnailKeyPolicy.isValid("0123456789ABCDEF0123456789ABCDEF"))
        assertFalse(DagTabThumbnailKeyPolicy.isValid("abc"))
    }
}
