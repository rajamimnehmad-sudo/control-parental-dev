package com.contentfilter.core.update.install

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompanionArchiveTrustTest {
    @Test
    fun `accepts exact admin package signed like current app`() {
        assertTrue(
            isTrustedCompanionArchive(
                archivePackageName = "com.contentfilter.admin.dev",
                expectedPackageName = "com.contentfilter.admin.dev",
                archiveSigners = setOf("content-filter-signer"),
                currentSigners = setOf("content-filter-signer"),
            ),
        )
    }

    @Test
    fun `rejects wrong package or signer`() {
        assertFalse(
            isTrustedCompanionArchive(
                archivePackageName = "com.example.fake",
                expectedPackageName = "com.contentfilter.admin.dev",
                archiveSigners = setOf("content-filter-signer"),
                currentSigners = setOf("content-filter-signer"),
            ),
        )
        assertFalse(
            isTrustedCompanionArchive(
                archivePackageName = "com.contentfilter.admin.dev",
                expectedPackageName = "com.contentfilter.admin.dev",
                archiveSigners = setOf("other-signer"),
                currentSigners = setOf("content-filter-signer"),
            ),
        )
    }
}
