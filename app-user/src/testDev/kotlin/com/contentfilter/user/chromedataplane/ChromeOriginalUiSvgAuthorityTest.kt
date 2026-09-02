package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import com.contentfilter.core.domain.chrome.ChromeMediaShieldSelfReadyIdentity
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ChromeOriginalUiSvgAuthorityTest {
    private val safeSvg =
        """<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24"><path fill="currentColor" d="M2 3h20v18H2z"/></svg>"""
            .toByteArray()

    @After
    fun tearDown() = ChromeMediaShieldDocumentAuthorityRegistry.clear()

    @Test
    fun validatorPreservesAuthorizedOriginalBytes() {
        val result = ChromeOriginalUiSvgValidator().validate(safeSvg, "image/svg+xml")
        assertTrue(result is ChromeOriginalUiSvgValidation.Valid)
        assertArrayEquals(safeSvg, (result as ChromeOriginalUiSvgValidation.Valid).bytes)
    }

    @Test
    fun validatorRejectsActiveExternalRasterAndPathologicalInputs() {
        val invalid =
            listOf(
                "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1 1'><script/></svg>",
                "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1 1' onload='x'><path d='M0 0'/></svg>",
                "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1 1'><foreignObject/></svg>",
                "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1 1'><image href='data:image/png;base64,AA=='/></svg>",
                "<!DOCTYPE svg [<!ENTITY x SYSTEM 'https://evil.test/x'>]><svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1 1'/>",
                " ".repeat(8192) +
                    "<!DOCTYPE svg><svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1 1'/>",
                "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1 1'><use href='#missing'/></svg>",
            )
        invalid.forEach { svg ->
            assertTrue(
                ChromeOriginalUiSvgValidator().validate(svg.toByteArray(), "image/svg+xml") is
                    ChromeOriginalUiSvgValidation.Invalid,
            )
        }
        assertTrue(
            ChromeOriginalUiSvgValidator().validate(safeSvg, "image/svg+xml; charset=utf-8") is
                ChromeOriginalUiSvgValidation.Invalid,
        )
    }

    @Test
    fun registryRequiresExactGenerationCapabilityDigestAndCleansUp() {
        val registry = ChromeOriginalUiSvgRegistry(randomBytes = { size -> ByteArray(size) { 7 } })
        val url = checkNotNull(registry.register(safeSvg))
        assertTrue(url.startsWith(ChromePhotosDataPlaneLabContract.OriginalUiSvgOrigin))
        val path = url.substringAfter(ChromePhotosDataPlaneLabContract.OriginalUiSvgOrigin)
        assertArrayEquals(safeSvg, registry.resolve(path)?.bytes)
        assertNull(registry.resolve(path.replace(Regex("[0-9a-f](?=[0-9a-f]{63}\\.svg$)"), "f")))
        assertNull(registry.resolve(path.replace("/.well-known/", "/wrong/")))
        registry.close()
        assertNull(registry.resolve(path))
        assertEquals(0, registry.size())
    }

    @Test
    fun cssTokenizerRewritesPercentBase64AndMultipleTokensButNotStringsOrRaster() {
        val registry = ChromeOriginalUiSvgRegistry(randomBytes = { size -> ByteArray(size) { 3 } })
        val encoded = safeSvg.toString(Charsets.UTF_8).encodePercent()
        val base64 = Base64.getEncoder().encodeToString(safeSvg)
        val css =
            """/*url(data:image/svg+xml,bad)*/ .a{mask:url('data:image/svg+xml,$encoded');background:url(data:image/svg+xml;base64,$base64),url(data:image/png;base64,AA==);content:"url(data:image/svg+xml,bad)"}"""
        val result = ChromeCssSvgRewriter(registry).rewrite(css)
        assertEquals(result.css, 2, result.rewritten)
        assertEquals(0, result.rejected)
        assertEquals(1, registry.size())
        assertTrue(result.css.contains("data:image/png"))
        assertTrue(result.css.contains("content:\"url(data:image/svg+xml,bad)\""))
        assertTrue(result.css.contains("/*url(data:image/svg+xml,bad)*/"))
    }

    @Test
    fun cssTokenizerAcceptsBoundedUtf8MarkerButRejectsAmbiguousOrUnsupportedParameters() {
        val registry = ChromeOriginalUiSvgRegistry(randomBytes = { size -> ByteArray(size) { 4 } })
        val encoded = safeSvg.toString(Charsets.UTF_8).encodePercent()
        val css =
            ".ok{mask:url(data:image/svg+xml;utf8,$encoded)}" +
                ".ambiguous{mask:url(data:image/svg+xml;utf8;charset=utf-8,$encoded)}" +
                ".charset{mask:url(data:image/svg+xml;charset=utf-16,$encoded)}"

        val result = ChromeCssSvgRewriter(registry).rewrite(css)

        assertEquals(1, result.rewritten)
        assertEquals(2, result.rejected)
        assertEquals(1, registry.size())
    }

    @Test
    fun malformedAndUnsafeSvgDataUrisRemainFailClosedAndUnregistered() {
        val registry = ChromeOriginalUiSvgRegistry(randomBytes = { size -> ByteArray(size) { 5 } })
        val unsafe = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1 1'><script/></svg>".encodePercent()
        val css = ".a{mask:url(data:image/svg+xml,$unsafe)}.b{mask:url(data:image/svg+xml,%ZZ)}"
        val result = ChromeCssSvgRewriter(registry).rewrite(css)
        assertEquals(css, result.css)
        assertEquals(0, result.rewritten)
        assertEquals(2, result.rejected)
        assertEquals(0, registry.size())
    }

    @Test
    fun networkSvgPreservesAuthorizedBytesAndFailsClosedForActiveSvg() {
        val placeholder = "placeholder".toByteArray()
        val authority = ChromeOriginalUiSvgAuthority(placeholder)
        val request = request(destination = "image")

        val accepted = checkNotNull(authority.processNetworkSvg(request, upstream("image/svg+xml", safeSvg)))
        assertEquals(ChromePhotosResourceDecision.Passthrough, accepted.decision)
        assertArrayEquals(safeSvg, accepted.bytes)

        val active = "<svg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 1 1'><script/></svg>".toByteArray()
        val rejected = checkNotNull(authority.processNetworkSvg(request, upstream("image/svg+xml", active)))
        assertEquals(ChromePhotosResourceDecision.Unknown, rejected.decision)
        assertArrayEquals(placeholder, rejected.bytes)
        assertEquals("image/png", rejected.headers.single { it.name.equals("Content-Type", true) }.value)
    }

    @Test
    fun onlyBrowserStylesheetResponsesAreRewrittenAndMadeNoStore() {
        val authority = ChromeOriginalUiSvgAuthority("placeholder".toByteArray())
        val encoded = safeSvg.toString(Charsets.UTF_8).encodePercent()
        val css = ".icon{mask:url(data:image/svg+xml,$encoded)}"

        val transformed =
            checkNotNull(
                authority.processStylesheet(
                    request(destination = "style"),
                    upstream("text/css; charset=utf-8", css.toByteArray()),
                ),
            )
        assertFalse(transformed.bytes.toString(Charsets.UTF_8).contains("data:image/svg+xml"))
        assertTrue(
            transformed.bytes.toString(Charsets.UTF_8)
                .contains(ChromePhotosDataPlaneLabContract.OriginalUiSvgOrigin),
        )
        assertEquals("no-store", transformed.headers.single { it.name.equals("Cache-Control", true) }.value)

        assertNull(
            authority.processStylesheet(
                request(destination = "empty"),
                upstream("text/css", css.toByteArray()),
            ),
        )
        assertNull(
            authority.processStylesheet(
                request(destination = "style"),
                upstream("application/json", css.toByteArray()),
            ),
        )
    }

    @Test
    fun registryCapacityExhaustionFailsClosedWithoutEvictingAuthorizedAsset() {
        val registry =
            ChromeOriginalUiSvgRegistry(
                maximumEntries = 1,
                maximumTotalBytes = safeSvg.size * 2,
                randomBytes = { size -> ByteArray(size) { 9 } },
            )
        val first = checkNotNull(registry.register(safeSvg))
        val secondSvg = safeSvg.toString(Charsets.UTF_8).replace("M2 3", "M3 4").toByteArray()
        assertNull(registry.register(secondSvg))
        assertArrayEquals(
            safeSvg,
            registry.resolve(first.substringAfter(ChromePhotosDataPlaneLabContract.OriginalUiSvgOrigin))?.bytes,
        )
        assertEquals(1, registry.size())
    }

    @Test
    fun dynamicRewriteEndpointRequiresAnExactAlreadyClaimedDocumentCapability() {
        val authority = ChromeOriginalUiSvgAuthority("placeholder".toByteArray())
        val endpoint = ChromeOriginalUiSvgRewriteEndpoint(authority)
        val token = "CCCCCCCCCCCCCCCCCCCCCC"
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession("session-svg", 7L)
        val issued = checkNotNull(ChromeMediaShieldDocumentAuthorityRegistry.issue("session-svg", 7L, token, true))
        val identity =
            ChromeMediaShieldSelfReadyIdentity(
                protectionSessionId = issued.protectionSessionId,
                policyEpoch = issued.policyEpoch,
                navigationSequence = issued.navigationSequence,
                documentSequence = issued.documentSequence,
                lifecycleSequence = 1L,
                topLevel = true,
            )
        ChromeMediaShieldDocumentAuthorityRegistry.claimSelfReady(token, identity)
        val encoded = safeSvg.toString(Charsets.UTF_8).encodePercent()
        val css = ".icon{mask:url(data:image/svg+xml,$encoded)}"
        val body = "v1|SVG_REWRITE|$token|session-svg|7|${issued.navigationSequence}|${issued.documentSequence}|T\n$css"
        val accepted = checkNotNull(endpoint.handle(rewriteRequest(body)))
        assertEquals(200, accepted.statusCode)
        assertTrue(
            accepted.bytes.toString(Charsets.UTF_8)
                .contains(ChromePhotosDataPlaneLabContract.OriginalUiSvgOrigin),
        )

        assertEquals(403, endpoint.handle(rewriteRequest(body.replace("|7|", "|8|")))?.statusCode)
        assertEquals(400, endpoint.handle(rewriteRequest(body, method = "GET"))?.statusCode)
        assertNull(endpoint.handle(rewriteRequest(body).copy(target = "/ordinary")))
    }

    private fun request(destination: String): ChromePhotosProxyRequest =
        ChromePhotosProxyRequest(
            method = "GET",
            target = "/asset",
            headers = listOf(ChromeHttpHeader("Sec-Fetch-Dest", destination)),
        )

    private fun upstream(
        contentType: String,
        bytes: ByteArray,
    ): ChromePhotosUpstreamResponse =
        ChromePhotosUpstreamResponse(
            host = "example.test",
            statusCode = 200,
            statusText = "OK",
            headers = listOf(ChromeHttpHeader("Content-Type", contentType)),
            body = bytes.inputStream(),
            bodyLength = bytes.size.toLong(),
            protocol = "h2",
        )

    private fun rewriteRequest(
        body: String,
        method: String = "POST",
    ): ChromePhotosProxyRequest =
        ChromePhotosProxyRequest(
            method = method,
            target = ChromePhotosDataPlaneLabContract.OriginalUiSvgRewritePath,
            headers = listOf(ChromeHttpHeader("Content-Type", "text/plain; charset=utf-8")),
            body = body.toByteArray(),
        )

    private fun String.encodePercent(): String =
        toByteArray().joinToString("") { byte ->
            val value = byte.toInt() and 0xff
            if (value.toChar().isLetterOrDigit() || value.toChar() in "-._~") {
                value.toChar().toString()
            } else {
                "%%%02X".format(value)
            }
        }
}
