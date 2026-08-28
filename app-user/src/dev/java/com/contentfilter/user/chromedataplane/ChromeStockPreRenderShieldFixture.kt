package com.contentfilter.user.chromedataplane

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Hostile DEV fixture for the H18 feasibility gate. Its observations never grant release authority. */
internal class ChromeStockPreRenderShieldFixture(
    private val networkSentinelBytes: ByteArray,
    auditPlaceholderBytes: ByteArray,
    private val transformer: ChromePreRenderDocumentTransformer = ChromePreRenderDocumentTransformer(),
) {
    private val eventSequence = AtomicLong()
    private val compatibleDocuments = AtomicLong()
    private val strictDocuments = AtomicLong()
    private val stylesheetRequests = AtomicLong()
    private val scriptRequests = AtomicLong()
    private val dataRequests = AtomicLong()
    private val networkSentinelRequests = AtomicLong()
    private val reports = AtomicLong()
    private val report = AtomicReference(NotRun)
    private val lastDocument = AtomicReference("none")
    private val networkSentinelSha256 = sha256(networkSentinelBytes)
    private val auditPlaceholderSha256 = sha256(auditPlaceholderBytes)

    fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse? {
        val path = request.target.substringBefore('?').substringBefore('#')
        return when (path) {
            CompatibleStaticPath -> document(ChromePreRenderShieldProfile.Compatible, dynamic = false)
            CompatibleDynamicPath -> document(ChromePreRenderShieldProfile.Compatible, dynamic = true)
            StrictPath -> document(ChromePreRenderShieldProfile.Strict, dynamic = true)
            StylesheetPath -> {
                stylesheetRequests.incrementAndGet()
                event("STYLE_REQUEST")
                response("h18-site-css", "text/css; charset=utf-8", siteCss().toByteArray(Charsets.UTF_8))
            }
            ScriptPath -> {
                scriptRequests.incrementAndGet()
                event("SCRIPT_REQUEST")
                response("h18-site-js", "application/javascript; charset=utf-8", siteScript().toByteArray(Charsets.UTF_8))
            }
            DataPath -> {
                dataRequests.incrementAndGet()
                event("DATA_REQUEST")
                response("h18-site-data", "application/json; charset=utf-8", "{\"normal\":true}".toByteArray())
            }
            NetworkSentinelPath -> {
                networkSentinelRequests.incrementAndGet()
                event("NETWORK_SENTINEL_REQUEST")
                response("h18-network-sentinel", "image/png", networkSentinelBytes)
            }
            ReportPath -> acceptReport(request)
            StatePath -> response("h18-state", "text/plain; charset=utf-8", state().toByteArray(Charsets.US_ASCII))
            else -> null
        }
    }

    fun state(): String =
        "REPORT=${report.get()},EVENT_SEQUENCE=${eventSequence.get()}," +
            "COMPAT_DOCS=${compatibleDocuments.get()},STRICT_DOCS=${strictDocuments.get()}," +
            "STYLE_REQ=${stylesheetRequests.get()},SCRIPT_REQ=${scriptRequests.get()},DATA_REQ=${dataRequests.get()}," +
            "NETWORK_REQ=${networkSentinelRequests.get()},REPORTS=${reports.get()},LAST_DOC=${lastDocument.get()}," +
            "NETWORK_SHA=$networkSentinelSha256,AUDIT_SHA=$auditPlaceholderSha256"

    internal fun sourceDocument(
        profile: ChromePreRenderShieldProfile,
        dynamic: Boolean,
    ): String {
        val mode = if (dynamic) "dynamic" else "static"
        val rasterClass = if (dynamic) "h18-raster dynamic" else "h18-raster static"
        val originalScript = if (dynamic) "<script src=\"$ScriptPath\" defer></script>" else ""
        return """
            <!doctype html>
            <html lang="en">
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1,maximum-scale=1">
              <title>H18 ${profile.wireName} $mode</title>
              <link rel="stylesheet" href="$StylesheetPath">
            </head>
            <body data-h18-profile="${profile.wireName}" data-h18-mode="$mode">
              <h1>STOCK CHROME PRE-RENDER SHIELD 18</h1>
              <section class="h18-network"><h2>NETWORK BYTE GATE</h2>
                <img id="network-control" src="$NetworkSentinelPath?case=${profile.wireName}-$mode" alt="network control">
              </section>
              <section><h2>ORDINARY DOM CSS RASTER</h2>
                <div id="dom-raster" class="$rasterClass">${rasterCells()}</div>
              </section>
              <section id="normal-control"><h2>NORMALITY CONTROL</h2><p id="normal-js">SITE_JS_NOT_RUN</p>
                <form><label>Text <input name="text" value="normal"></label><button type="button">Control</button></form>
              </section>
              $originalScript
            </body>
            </html>
        """.trimIndent()
    }

    internal fun siteCss(): String =
        """
        :root{font-family:sans-serif;color:#13212d;background:#eef2f5}body{margin:0;padding:8px}h1,h2{margin:4px 0;font-size:16px}
        section{background:#fff;margin:6px 0;padding:6px;border-radius:6px}.h18-network img{display:block;width:256px;height:144px;object-fit:fill;background:#374151}
        .h18-raster{display:grid;grid-template-columns:repeat(8,32px);grid-template-rows:repeat(8,32px);width:256px;height:256px;border:8px solid #ffee00;box-sizing:content-box}
        .h18-raster .pixel{display:block;width:32px;height:32px}.h18-raster.static .b0,.h18-raster.dynamic[data-live='true'] .b0{background-color:#000000}
        .h18-raster.static .b1,.h18-raster.dynamic[data-live='true'] .b1{background-color:#dc1430}.h18-raster.dynamic .pixel{background-color:#6c757d}
        #normal-control{font-size:14px}input,button{font:inherit}
        """.trimIndent().replace("\n", "")

    internal fun siteScript(): String =
        """
        (()=>{'use strict';
        const raster=document.getElementById('dom-raster');raster.dataset.live='true';
        fetch('$DataPath',{cache:'no-store'}).then(response=>response.json()).then(data=>{
        document.getElementById('normal-js').textContent=data.normal?'SITE_JS_FETCH_PASS':'SITE_JS_FETCH_FAIL';
        const xhr=new XMLHttpRequest();xhr.open('POST','$ReportPath',false);xhr.setRequestHeader('Content-Type','text/plain');
        xhr.send('SITE_JS_NORMAL_DYNAMIC_CSS');});})();
        """.trimIndent().replace("\n", "")

    private fun document(
        profile: ChromePreRenderShieldProfile,
        dynamic: Boolean,
    ): ChromePhotosFixtureResponse {
        val source = sourceDocument(profile, dynamic).toByteArray(Charsets.UTF_8)
        val transformed = transformer.transform(source, emptyList(), profile)
        if (profile == ChromePreRenderShieldProfile.Strict) strictDocuments.incrementAndGet() else compatibleDocuments.incrementAndGet()
        val mode = if (dynamic) "dynamic" else "static"
        val event = event("DOCUMENT_TRANSFORMED")
        lastDocument.set("${profile.wireName}:$mode:${transformed.documentSequence}:$event")
        return ChromePhotosFixtureResponse(
            resourceId = "h18-document-${profile.wireName}-$mode",
            contentType = "text/html; charset=utf-8",
            originalBytes = transformed.bytes,
            headers = transformed.headers.filterNot { it.name.equals("Content-Type", ignoreCase = true) },
        )
    }

    private fun acceptReport(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse {
        if (request.method != "POST") return response("h18-report-method", "text/plain", "POST required".toByteArray(), 405)
        val candidate = request.body.toString(Charsets.US_ASCII).take(MaximumReportBytes)
        val accepted = candidate.takeIf { it.isNotBlank() && it.all(::isSafeReportCharacter) } ?: Invalid
        val event = event("REPORT")
        reports.incrementAndGet()
        report.updateAndGet { previous ->
            val next = "E$event:$accepted"
            if (previous == NotRun) next else "$previous;$next"
        }
        return response("h18-report", "text/plain; charset=utf-8", "accepted".toByteArray())
    }

    private fun event(label: String): Long = eventSequence.incrementAndGet().also { sequence ->
        if (label.length > MaximumEventLabelLength) error("h18_event_label_too_long")
    }

    private fun response(
        id: String,
        contentType: String,
        bytes: ByteArray,
        statusCode: Int = 200,
    ) = ChromePhotosFixtureResponse(
        resourceId = id,
        contentType = contentType,
        originalBytes = bytes,
        headers = listOf(ChromeHttpHeader("Cache-Control", "no-store")),
        statusCode = statusCode,
        statusText = if (statusCode == 200) "OK" else "Method Not Allowed",
    )

    private fun rasterCells(): String =
        buildString(CellCount * 30) {
            repeat(CellCount) { index ->
                val bit = (RasterBitMask shr (CellCount - 1 - index)) and 1UL
                append("<span class=\"pixel b$bit\" aria-hidden=\"true\"></span>")
            }
        }

    private companion object {
        const val CompatibleStaticPath = "/web18/compatible-static"
        const val CompatibleDynamicPath = "/web18/compatible-dynamic"
        const val StrictPath = "/web18/strict"
        const val StylesheetPath = "/web18/site.css"
        const val ScriptPath = "/web18/site.js"
        const val DataPath = "/web18/data.json"
        const val NetworkSentinelPath = "/web18/raw-sentinel.png"
        const val ReportPath = "/web18/report"
        const val StatePath = "/web18/state"
        const val CellCount = 64
        const val RasterBitMask = 0xD3A5C69E5A3C96E1UL
        const val MaximumReportBytes = 512
        const val MaximumEventLabelLength = 32
        const val NotRun = "not_run"
        const val Invalid = "invalid"

        fun isSafeReportCharacter(value: Char): Boolean =
            value.isLetterOrDigit() || value in "_-:"
    }
}
