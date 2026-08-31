package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentAuthorityRegistry
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChromeStockMediaAuthorityFixtureTest {
    private val sentinel = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 1, 2, 3)
    private val fixture = ChromeStockMediaAuthorityFixture(sentinel)

    @Test
    fun `matrix has stable unique scenarios in every contract category`() {
        val scenarios = fixture.scenarios

        assertEquals(scenarios.size, scenarios.map { it.id }.toSet().size)
        assertEquals(50, scenarios.size)
        assertEquals(
            ChromeStockMediaScenarioCategory.entries.toSet(),
            scenarios.mapTo(linkedSetOf()) { it.category },
        )
        assertEquals(17, scenarios.count { it.category == ChromeStockMediaScenarioCategory.Network })
        assertEquals(25, scenarios.count { it.category == ChromeStockMediaScenarioCategory.Local })
        assertEquals(7, scenarios.count { it.category == ChromeStockMediaScenarioCategory.Normality })
        assertEquals(1, scenarios.count { it.category == ChromeStockMediaScenarioCategory.OutOfScope })
    }

    @Test
    fun `controlled matrix includes network local normality and explicit out of scope cases`() {
        val html = fixture.responseFor(request("GET", "/web19/controlled"))!!.text()
        val firstOriginal = fixture.responseFor(request("GET", "/web20/first-original.js"))!!.text()
        val script = fixture.responseFor(request("GET", "/web19/site.js"))!!.text()

        assertTrue(html.indexOf("/web20/first-original.js") < html.indexOf("GLOSH H19 CONTROLLED"))
        assertTrue(firstOriginal.contains(ChromeMediaShieldBootstrap.SelfShieldOriginalScriptStartedName))
        assertTrue(fixture.state().contains("FIRST_ORIGINAL_SCRIPTS=1"))

        listOf(
            "picture-srcset",
            "same-url-first",
            "same-body-first",
            "dynamic-replace",
            "data-image",
            "blob-image",
            "canvas-webgl",
            "svg-foreign",
            "open-shadow",
            "network-frame",
            "normal-form",
            "css-synthesis",
        ).forEach { assertTrue(html.contains(it), it) }
        listOf(
            "createImageBitmap",
            "OffscreenCanvas",
            "getContext('webgpu')",
            "serviceWorker.register",
            "serviceWorker.getRegistrations",
            "serviceWorker.controller===null",
            "history.pushState",
            "local-hostile-prototype-fresh-frame",
            "local-protected-primordial-bypass",
            "Define(String.prototype,'toLowerCase'",
            "Define(Array.prototype,'0'",
            "allow-scripts allow-same-origin",
            "shield.style='display:none!important'",
            "descriptorChain(CSSStyleSheet.prototype,'disabled',true)",
            "descriptorChain(CSSStyleSheet.prototype,'media',true)",
            "descriptorChain(CSSStyleRule.prototype,'style',true)",
            "descriptorChain(CSSStyleRule.prototype,'insertRule',false)",
            "descriptorChain(CSSStyleRule.prototype,'deleteRule',false)",
            "descriptorChain(HTMLIFrameElement.prototype,'sandbox',true)",
            "error.name==='SecurityError'",
            "scratchSheet.disabled=true",
            "scratchRule.style='color:rgb(7,8,9)'",
            "Define(retainedStyle,'opacity'",
            "DefineReflect(retainedStyle,'visibility'",
            "SetReflect(retainedStyle,'display','block')",
            "Assign(retainedStyle,{opacity:'1'})",
            "SetPrototype(retainedStyle,null)",
            "protectedMediaRead=securityDenied(()=>sheet.media)",
            "retainedNeutral=call(ElementGet,retained,['src'])===null",
            "out-of-scope-css-synthesis",
        ).forEach { assertTrue(script.contains(it), it) }
        val style = fixture.responseFor(request("GET", "/web19/site.css"))!!.text()
        assertTrue(style.contains("#icon-safe{zoom:20!important;scale:20!important"))
        assertTrue(style.contains("#icon-safe path{d:path('M0 0H96V96H0Z')!important"))
        fixture.scenarios.forEach { assertTrue(script.contains(it.id), it.id) }
        assertTrue(script.contains("await Promise.allSettled(tasks)"))
        assertFalse(script.contains("record(id,'PASS')"))
        assertFalse(script.contains("record(id,'LOADED')"))
        assertFalse(script.contains("record(id,'VISIBLE')"))
        assertFalse(script.contains("if(!results.has(id))results.set(id,'SKIP')"))
    }

    @Test
    fun `typed diagnostic endpoints preserve bytes and same url changes body`() {
        val mislabeled = fixture.responseFor(request("GET", "/web19/mislabeled-image"))!!
        val octet = fixture.responseFor(request("GET", "/web19/octet-image"))!!
        val sameUrlFirst = fixture.responseFor(request("GET", "/web19/same-url.png?revision=1"))!!
        val sameUrlSecond = fixture.responseFor(request("GET", "/web19/same-url.png?revision=2"))!!
        val sameBodyFirst = fixture.responseFor(request("GET", "/web19/same-body.png?request=1"))!!
        val sameBodySecond = fixture.responseFor(request("GET", "/web19/same-body.png?request=2"))!!

        assertEquals("text/plain", mislabeled.contentType)
        assertEquals("application/octet-stream", octet.contentType)
        assertContentEquals(sentinel, mislabeled.originalBytes)
        assertContentEquals(sentinel, octet.originalBytes)
        assertNotEquals(sameUrlFirst.originalBytes.toList(), sameUrlSecond.originalBytes.toList())
        assertContentEquals(sameBodyFirst.originalBytes, sameBodySecond.originalBytes)
    }

    @Test
    fun `transformed frame probes local sinks and reports a nonce bound complete result`() {
        val frame = fixture.responseFor(request("GET", "/web19/frame"))!!.text()
        val challenge = frameChallenge(frame)
        fixture.responseFor(request("GET", "/web19/worker.js"))
        fixture.responseFor(request("GET", "/web19/service-worker.js"))
        listOf(
            "data:image/png;base64",
            "URL.createObjectURL",
            "getContext('2d')",
            "serviceWorker.register",
            "attachShadow({mode:'closed'})",
        ).forEach { assertTrue(frame.contains(it), it) }

        val response =
            fixture.responseFor(
                request(
                    "POST",
                    "/web19/frame-report",
                    frameReport(challenge, FrameScenarioIds.reversed()),
                ),
            )!!

        val state = fixture.state()
        assertEquals(200, response.statusCode)
        assertTrue(state.contains("FRAMES=1"))
        assertTrue(state.contains("WORKERS=1"))
        assertTrue(state.contains("SERVICE_WORKERS=1"))
        assertTrue(state.contains("FRAME_REPORTS=1"))
        assertTrue(state.contains("FRAME_REPORT_REJECTS=0"))
        assertTrue(state.contains("FRAME_REPORT=${canonicalFrameReport()}"))
        assertTrue(state.contains("FRAME_REPORT_SHA=${sha256(canonicalFrameReport().toByteArray())}"))
        assertTrue(Regex("FRAME_CHALLENGE_SHA=[0-9a-f]{64}").containsMatchIn(state))
        assertTrue(Regex("FRAME_ACCEPTED_CHALLENGE_SHA=[0-9a-f]{64}").containsMatchIn(state))
        assertTrue(Regex("FRAME_REPORT_BINDING_SHA=[0-9a-f]{64}").containsMatchIn(state))
        assertFalse(state.contains(challenge))
        assertFalse(state.contains(sentinel.toString(Charsets.ISO_8859_1)))
    }

    @Test
    fun `new frame generation clears old report and binds only its accepted challenge`() {
        val firstFrame = fixture.responseFor(request("GET", "/web19/frame"))!!.text()
        val firstChallenge = frameChallenge(firstFrame)
        fixture.responseFor(request("POST", "/web19/frame-report", frameReport(firstChallenge)))
        val accepted = fixture.state()

        val secondFrame = fixture.responseFor(request("GET", "/web19/frame"))!!.text()
        val secondChallenge = frameChallenge(secondFrame)
        val pending = fixture.state()

        assertNotEquals(firstChallenge, secondChallenge)
        assertTrue(accepted.contains("FRAME_REPORTS=1"))
        assertTrue(pending.contains("FRAME_REPORT=not_run"))
        assertTrue(pending.contains("FRAME_REPORT_SHA=not_run"))
        assertTrue(pending.contains("FRAME_ACCEPTED_CHALLENGE_SHA=not_run"))
        assertTrue(pending.contains("FRAME_REPORT_BINDING_SHA=not_run"))
        assertTrue(pending.contains("FRAME_GENERATION=2"))
        assertEquals(
            400,
            fixture.responseFor(request("POST", "/web19/frame-report", frameReport(firstChallenge)))!!.statusCode,
        )
        assertEquals(
            200,
            fixture.responseFor(request("POST", "/web19/frame-report", frameReport(secondChallenge)))!!.statusCode,
        )
        assertTrue(fixture.state().contains("FRAME_REPORTS=2"))
    }

    @Test
    fun `subdocument transformer installs shield before every frame probe`() {
        val source = fixture.responseFor(request("GET", "/web19/frame"))!!
        ChromeMediaShieldDocumentAuthorityRegistry.beginSession("fixture-frame-session", 19L)
        try {
            var randomCall = 0
            val transformer =
                ChromeMediaShieldDocumentTransformer("fixture-frame-session", 19L) { size ->
                    randomCall += 1
                    ByteArray(size) { index -> (randomCall + index).toByte() }
                }
            val transformed =
                assertIs<ChromeMediaShieldDocumentResult.Transformed>(
                    transformer.transform(
                        sourceBytes = source.originalBytes,
                        sourceHeaders = listOf(ChromeHttpHeader("Content-Type", source.contentType)),
                        disposition =
                            ChromeMediaShieldDocumentDisposition.Transform(
                                ChromeMediaShieldDocumentKind.Subdocument,
                                "utf-8",
                            ),
                    ),
                ).document
            val html = transformed.bytes.toString(Charsets.ISO_8859_1)
            val bootstrapAt = html.indexOf("const READY=")
            val firstProbeAt = html.indexOf("const CHALLENGE=")

            assertTrue(bootstrapAt >= 0)
            assertTrue(firstProbeAt > bootstrapAt)
            assertTrue(html.contains("TOP_LEVEL=false"))
            assertFalse(transformed.identity.topLevel)
            assertFalse(html.contains("<canvas"))
            assertTrue(html.contains("document.createElement('canvas')"))
        } finally {
            ChromeMediaShieldDocumentAuthorityRegistry.clear()
        }
    }

    @Test
    fun `frame report rejects incomplete forged and replayed observations fail closed`() {
        val frame = fixture.responseFor(request("GET", "/web19/frame"))!!.text()
        val challenge = frameChallenge(frame)
        val complete = frameReport(challenge)
        val first = FrameScenarioIds.first()

        assertNull(fixture.canonicalFrameReportOrNull("v1|$challenge|$first=BLOCKED".toByteArray()))
        assertNull(fixture.canonicalFrameReportOrNull(("$complete,$first=BLOCKED").toByteArray()))
        assertNull(fixture.canonicalFrameReportOrNull(complete.replace(first, "frame-unknown").toByteArray()))
        assertNull(fixture.canonicalFrameReportOrNull(complete.replace("=BLOCKED", "=PASS").toByteArray()))
        assertNull(fixture.canonicalFrameReportOrNull(ByteArray(769) { 'A'.code.toByte() }))

        val forgedChallenge = "0".repeat(32).let { if (it == challenge) "1".repeat(32) else it }
        val forged = fixture.responseFor(request("POST", "/web19/frame-report", frameReport(forgedChallenge)))!!
        val accepted = fixture.responseFor(request("POST", "/web19/frame-report", complete))!!
        val replayed = fixture.responseFor(request("POST", "/web19/frame-report", complete))!!

        assertEquals(400, forged.statusCode)
        assertEquals(200, accepted.statusCode)
        assertEquals(400, replayed.statusCode)
        assertTrue(fixture.state().contains("FRAME_REPORTS=1"))
        assertTrue(fixture.state().contains("FRAME_REPORT_REJECTS=2"))
        assertTrue(fixture.state().contains("FRAME_REPORT=${canonicalFrameReport()}"))
    }

    @Test
    fun `complete report is canonicalized by scenario id`() {
        val reverse =
            fixture.scenarios.reversed().joinToString(",") {
                if (it.category == ChromeStockMediaScenarioCategory.OutOfScope) {
                    "${it.id}=OUT_OF_SCOPE_VISIBLE"
                } else {
                    "${it.id}=SAFE"
                }
            }
        val response = fixture.responseFor(request("POST", "/web19/report", reverse))!!

        assertEquals(200, response.statusCode)
        val expected =
            fixture.scenarios
                .sortedBy { it.id }
                .joinToString(",") {
                    if (it.category == ChromeStockMediaScenarioCategory.OutOfScope) {
                        "${it.id}=OUT_OF_SCOPE_VISIBLE"
                    } else {
                        "${it.id}=SAFE"
                    }
                }
        assertTrue(fixture.state().contains("REPORT=$expected"))
        assertTrue(fixture.state().endsWith("REPORTS=1"))
    }

    @Test
    fun `report rejects incomplete duplicate unknown unsafe and oversized input`() {
        val complete = fixture.scenarios.joinToString(",") { "${it.id}=SAFE" }
        val first = fixture.scenarios.first().id

        assertNull(fixture.canonicalReportOrNull("$first=SAFE".toByteArray()))
        assertNull(fixture.canonicalReportOrNull("$complete,$first=SAFE".toByteArray()))
        assertNull(fixture.canonicalReportOrNull(complete.replace("$first=SAFE", "unknown=SAFE").toByteArray()))
        assertNull(fixture.canonicalReportOrNull(complete.replace("$first=SAFE", "$first=PASS").toByteArray()))
        assertNull(fixture.canonicalReportOrNull(complete.replace("$first=SAFE", "$first=RAW").toByteArray()))
        assertNull(fixture.canonicalReportOrNull(complete.replace("$first=SAFE", "$first=<raw>").toByteArray()))
        assertNull(fixture.canonicalReportOrNull(ByteArray(4097) { 'A'.code.toByte() }))
    }

    @Test
    fun `wrong report methods fail without accepting observations`() {
        val response = fixture.responseFor(request("GET", "/web19/report"))!!

        assertEquals(405, response.statusCode)
        assertTrue(fixture.state().contains("REPORT=not_run"))
        assertTrue(fixture.state().endsWith("REPORTS=0"))
    }

    private fun request(
        method: String,
        target: String,
        body: String = "",
    ) = ChromePhotosProxyRequest(method = method, target = target, body = body.toByteArray())

    private fun frameChallenge(frame: String): String =
        requireNotNull(Regex("const CHALLENGE='([0-9a-f]{32})'").find(frame)).groupValues[1]

    private fun frameReport(
        challenge: String,
        ids: List<String> = FrameScenarioIds,
    ): String = "v1|$challenge|${ids.joinToString(",") { "$it=BLOCKED" }}"

    private fun canonicalFrameReport(): String = FrameScenarioIds.sorted().joinToString(",") { "$it=BLOCKED" }

    private fun ChromePhotosFixtureResponse.text(): String = originalBytes.toString(Charsets.UTF_8)

    private companion object {
        val FrameScenarioIds =
            listOf(
                "frame-data-img",
                "frame-blob-img",
                "frame-canvas",
                "frame-service-worker",
                "frame-closed-shadow",
            )
    }
}
