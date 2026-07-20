package com.contentfilter.core.update.model

import kotlin.test.Test
import kotlin.test.assertSame

class UpdateCheckResultTest {
    @Test
    fun `up to date result keeps manifest for installed release notes`() {
        val manifest =
            UpdateManifest(
                versionCode = 264,
                versionName = "264",
                apkUrl = "https://example.invalid/app.apk",
                apkSha256 = "checksum",
                releaseNotes = "Cambios de la versión instalada",
            )

        val result = UpdateCheckResult.UpToDate(manifest)

        assertSame(manifest, result.manifest)
    }
}
