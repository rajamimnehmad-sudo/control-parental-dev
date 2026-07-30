package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

class DagDownloadPolicyTest {
    @Test
    fun `allows a bounded PDF from the clicked HTTPS response`() {
        val decision =
            DagDownloadPolicy.decide(
                gesture = gesture(),
                candidate = candidate(),
            )

        val allowed = assertIs<DagDownloadDecision.Allow>(decision).download
        assertEquals("guia.pdf", allowed.fileName)
        assertEquals("docs.example", allowed.host)
    }

    @Test
    fun `blocks automatic download without a user gesture`() {
        val decision = DagDownloadPolicy.decide(gesture = null, candidate = candidate())

        assertEquals("missing_user_gesture", assertIs<DagDownloadDecision.Block>(decision).reason)
    }

    @Test
    fun `blocks an expired gesture and a response after the page changed`() {
        val expired =
            DagDownloadPolicy.decide(
                gesture(),
                candidate().copy(nowMillis = 11_001),
            )
        val changed =
            DagDownloadPolicy.decide(
                gesture(),
                candidate().copy(currentTabRevision = 10),
            )

        assertEquals("expired_user_gesture", assertIs<DagDownloadDecision.Block>(expired).reason)
        assertEquals("page_changed", assertIs<DagDownloadDecision.Block>(changed).reason)
    }

    @Test
    fun `requires PDF extension even when MIME says PDF`() {
        val decision =
            DagDownloadPolicy.decide(
                gesture(),
                candidate().copy(suggestedFileName = "guia.apk"),
            )

        assertEquals("blocked_extension", assertIs<DagDownloadDecision.Block>(decision).reason)
    }

    @Test
    fun `blocks unknown or excessive sizes`() {
        val unknown =
            DagDownloadPolicy.decide(
                gesture(),
                candidate().copy(declaredBytes = null),
            )
        val excessive =
            DagDownloadPolicy.decide(
                gesture(),
                candidate().copy(declaredBytes = DagDownloadPolicy.MaxBytes + 1),
            )

        assertEquals("unknown_size", assertIs<DagDownloadDecision.Block>(unknown).reason)
        assertEquals("blocked_size", assertIs<DagDownloadDecision.Block>(excessive).reason)
    }

    @Test
    fun `blocks executable MIME and renamed APK`() {
        val executable =
            DagDownloadPolicy.decide(
                gesture(),
                candidate().copy(mimeType = "application/vnd.android.package-archive"),
            )
        val renamed =
            DagDownloadPolicy.looksLikePdf(
                header = "PK\u0003\u0004fake.apk".toByteArray(),
                tail = "content".toByteArray(),
            )

        assertEquals("blocked_mime", assertIs<DagDownloadDecision.Block>(executable).reason)
        assertEquals(false, renamed)
    }

    @Test
    fun `blocks cross-origin redirects but allows same-origin redirect`() {
        val crossOrigin =
            DagDownloadPolicy.decide(
                gesture(),
                candidate().copy(
                    responseUrl = "https://cdn.example/guia.pdf",
                    redirected = true,
                ),
            )
        val sameOrigin =
            DagDownloadPolicy.decide(
                gesture(),
                candidate().copy(
                    responseUrl = "https://docs.example/files/guia.pdf",
                    redirected = true,
                ),
            )

        assertEquals("blocked_redirect", assertIs<DagDownloadDecision.Block>(crossOrigin).reason)
        assertIs<DagDownloadDecision.Allow>(sameOrigin)
    }

    @Test
    fun `records gestures only from the visible top-level HTTPS page`() {
        val valid =
            DagDownloadPolicy.recordGesture(
                requestUrl = "https://docs.example/guia.pdf",
                triggerUrl = "https://docs.example/home",
                currentPageUrl = "https://docs.example/home",
                tabRevision = 4,
                pageVisible = true,
                hasUserGesture = true,
                opensNewWindow = false,
                nowMillis = 100,
            )
        val automatic =
            DagDownloadPolicy.recordGesture(
                requestUrl = "https://docs.example/guia.pdf",
                triggerUrl = "https://docs.example/home",
                currentPageUrl = "https://docs.example/home",
                tabRevision = 4,
                pageVisible = true,
                hasUserGesture = false,
                opensNewWindow = false,
                nowMillis = 100,
            )

        assertIs<DagDownloadGesture>(valid)
        assertNull(automatic)
    }

    @Test
    fun `parses headers case-insensitively and sanitizes the name`() {
        val headers =
            mapOf(
                "content-length" to "2048",
                "CONTENT-DISPOSITION" to "attachment; filename*=UTF-8''gu%C3%ADa%20final.pdf",
            )

        assertEquals(2048, DagDownloadPolicy.declaredLength(headers))
        assertEquals(
            "gu_a final.pdf",
            DagDownloadPolicy.safePdfFileName(DagDownloadPolicy.suggestedFileName(headers, "https://x.test/a")),
        )
    }

    @Test
    fun `accepts PDF signature and end marker`() {
        assertEquals(
            true,
            DagDownloadPolicy.looksLikePdf(
                header = "%PDF-1.7".toByteArray(),
                tail = "trailer\n%%EOF\n".toByteArray(),
            ),
        )
    }

    private fun gesture() =
        DagDownloadGesture(
            targetUrl = "https://docs.example/guia.pdf",
            pageUrl = "https://docs.example/home",
            tabRevision = 9,
            createdAtMillis = 1_000,
        )

    private fun candidate() =
        DagDownloadCandidate(
            responseUrl = "https://docs.example/guia.pdf",
            currentPageUrl = "https://docs.example/home",
            currentTabRevision = 9,
            secure = true,
            redirected = false,
            statusCode = 200,
            mimeType = "application/pdf; charset=binary",
            declaredBytes = 2_048,
            suggestedFileName = "guia.pdf",
            nowMillis = 1_500,
        )
}
