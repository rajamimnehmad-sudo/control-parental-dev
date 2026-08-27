package com.contentfilter.user.chromedataplane

import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPOutputStream

/** Deterministic in-memory controlled fixture router. It never persists source media. */
internal class ChromeImageAuthorityFixture(
    private val safeBytes: ByteArray,
    private val placeholderBytes: ByteArray,
) {
    private val report = AtomicReference(NotRun)
    private val normalizedImageRequestObserved = AtomicBoolean(false)
    private val provenanceFixture = ChromePixelProvenanceFixture()

    fun report(): String = report.get()

    fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse? {
        provenanceFixture.responseFor(request)?.let { return it }
        val path = request.target.substringBefore('?').substringBefore('#')
        return when (path) {
            RunnerPath -> html("web11b-runner", runnerHtml())
            NormalizedImagePath -> normalizedImage(request)
            StatePath -> text("web11b-state", if (normalizedImageRequestObserved.get()) NormalizationPass else NormalizationFail)
            ReportPath -> acceptReport(request)
            "/web11b/safe.png" -> image("web11b-safe", "image/png", safeBytes)
            "/web11b/octet-image" -> image("web11b-octet", "application/octet-stream", safeBytes)
            "/web11b/text-image" -> image("web11b-text", "text/plain", safeBytes)
            "/web11b/mismatch-image" -> image("web11b-mismatch", "image/jpeg", safeBytes)
            "/web11b/html-as-image" -> image("web11b-html", "image/png", HtmlBytes)
            "/web11b/vector.svg" -> image("web11b-svg", "image/svg+xml", SvgBytes)
            "/web11b/animated.gif" -> image("web11b-gif", "image/gif", AnimatedGifBytes)
            "/web11b/animated.png" -> image("web11b-apng", "image/png", AnimatedPngBytes)
            "/web11b/encoded.png" ->
                image(
                    id = "web11b-encoded",
                    contentType = "image/png",
                    bytes = gzip(safeBytes),
                    headers = listOf(ChromeHttpHeader("Content-Encoding", "gzip")),
                )
            "/web11b/partial.png" ->
                image(
                    id = "web11b-partial",
                    contentType = "image/png",
                    bytes = safeBytes.copyOf(minOf(safeBytes.size, PartialBytes)),
                    headers = listOf(ChromeHttpHeader("Content-Range", "bytes 0-${minOf(safeBytes.lastIndex, PartialBytes - 1)}/${safeBytes.size}")),
                    statusCode = 206,
                    statusText = "Partial Content",
                )
            "/web11b/not-modified.png" ->
                image(
                    id = "web11b-not-modified",
                    contentType = "image/png",
                    bytes = ByteArray(0),
                    headers = listOf(ChromeHttpHeader("ETag", ImageEtag)),
                    statusCode = 304,
                    statusText = "Not Modified",
                )
            "/web11b/oversized.png" -> image("web11b-oversized", "image/png", oversizedPngBytes())
            else -> null
        }
    }

    private fun normalizedImage(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse {
        val normalized =
            request.headerValues("Accept-Encoding") == listOf("identity") &&
                request.headerValues("Range").isEmpty() &&
                request.headerValues("If-Range").isEmpty() &&
                request.headerValues("If-None-Match").isEmpty() &&
                request.headerValues("If-Modified-Since").isEmpty()
        normalizedImageRequestObserved.set(normalized)
        return image(
            id = "web11b-normalization-${if (normalized) "pass" else "fail"}",
            contentType = "image/png",
            bytes = safeBytes,
        )
    }

    private fun acceptReport(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse {
        val candidate = request.body.toString(Charsets.US_ASCII).take(MaximumReportBytes)
        val accepted = candidate.takeIf { it.isNotBlank() && it.all(::isReportCharacter) } ?: Invalid
        report.set(accepted)
        return text("web11b-report", "accepted")
    }

    private fun runnerHtml(): String {
        val safeHash = sha256(safeBytes)
        val placeholderHash = sha256(placeholderBytes)
        return """
            <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>GLOSH11B_RUNNING</title></head><body><h1>Glosh 11B image authority</h1>
            <img id="normalized" src="$NormalizedImagePath?nonce=${System.nanoTime()}" alt="normalized image">
            <pre id="result">RUNNING</pre><script>
            (async()=>{const out=[];const check=(n,v)=>out.push(n+':'+(v?'PASS':'FAIL'));
            const hex=b=>Array.from(new Uint8Array(b)).map(x=>x.toString(16).padStart(2,'0')).join('');
            const inspect=async path=>{const r=await fetch(path,{cache:'no-store'});const b=await r.arrayBuffer();return {r,b,h:hex(await crypto.subtle.digest('SHA-256',b))};};
            await new Promise(resolve=>{const image=document.getElementById('normalized');if(image.complete)resolve();else{image.onload=resolve;image.onerror=resolve;}});
            check('NORMALIZATION',(await (await fetch('$StatePath',{cache:'no-store'})).text())==='$NormalizationPass');
            const safe=await inspect('/web11b/safe.png');check('SAFE',safe.h==='$safeHash'&&safe.r.headers.get('content-type')==='image/png'&&safe.r.headers.get('cache-control')==='no-store'&&safe.r.headers.get('x-content-type-options')==='nosniff');
            for(const path of ['/web11b/octet-image','/web11b/text-image','/web11b/mismatch-image']){const x=await inspect(path);check('MISLABELED',x.h==='$safeHash'&&x.r.headers.get('content-type')==='image/png');}
            for(const path of ['/web11b/html-as-image','/web11b/vector.svg','/web11b/animated.gif','/web11b/animated.png','/web11b/encoded.png','/web11b/partial.png','/web11b/not-modified.png','/web11b/oversized.png']){const x=await inspect(path);check('FAIL_CLOSED',x.h==='$placeholderHash'&&x.r.status===200);}
            check('GZIP',(await (await fetch('/web11a/gzip')).text())==='gzip-pass');check('CHUNKED',(await (await fetch('/web11a/chunked')).text())==='chunked-pass');
            const range=await fetch('/web11a/range',{headers:{Range:'bytes=10-31'}});check('RANGE',range.status===206&&(await range.arrayBuffer()).byteLength===22);
            const first=await fetch('/web11a/etag',{cache:'no-store'});const etag=first.headers.get('etag');const second=await fetch('/web11a/etag',{headers:{'If-None-Match':etag}});check('ETAG',second.status===304);
            const download=await fetch('/web11a/download');check('DOWNLOAD',(await download.arrayBuffer()).byteLength===262144);
            const value=out.join(',');await fetch('$ReportPath',{method:'POST',headers:{'Content-Type':'text/plain'},body:value});document.getElementById('result').textContent=out.join('\n');document.title=out.every(x=>x.endsWith('PASS'))?'GLOSH11B_PASS':'GLOSH11B_FAIL';
            })().catch(e=>{document.getElementById('result').textContent='ERROR:'+e.name;document.title='GLOSH11B_FAIL'});
            </script>${ChromePhotosFixtureLeaseContract.ScriptTag}</body></html>
        """.trimIndent()
    }

    private fun image(
        id: String,
        contentType: String,
        bytes: ByteArray,
        headers: List<ChromeHttpHeader> = emptyList(),
        statusCode: Int = 200,
        statusText: String = "OK",
    ) = ChromePhotosFixtureResponse(id, contentType, bytes, headers, statusCode = statusCode, statusText = statusText)

    private fun html(
        id: String,
        value: String,
    ) = ChromePhotosFixtureResponse(id, "text/html; charset=utf-8", value.toByteArray())

    private fun text(
        id: String,
        value: String,
    ) = ChromePhotosFixtureResponse(id, "text/plain; charset=utf-8", value.toByteArray())

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(bytes) }
            output.toByteArray()
        }

    private fun oversizedPngBytes(): ByteArray =
        byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) +
            ByteArray(MaximumImageBytes)

    private companion object {
        const val RunnerPath = "/web11b"
        const val NormalizedImagePath = "/web11b/normalized.png"
        const val StatePath = "/web11b/state"
        const val ReportPath = "/web11b/report"
        const val NormalizationPass = "NORMALIZATION_PASS"
        const val NormalizationFail = "NORMALIZATION_FAIL"
        const val NotRun = "not_run"
        const val Invalid = "invalid"
        const val MaximumReportBytes = 2_048
        const val PartialBytes = 32
        const val ImageEtag = "\"glosh-11b-image-v1\""
        const val MaximumImageBytes = ChromePhotosRealUpstream.DefaultMaximumBodyBytes
        val HtmlBytes = "<html><body>not an image</body></html>".toByteArray()
        val SvgBytes = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>".toByteArray()
        val AnimatedGifBytes = "GIF89a-animated".toByteArray()
        val AnimatedPngBytes =
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a) +
                "acTL-animation".toByteArray()
        fun isReportCharacter(value: Char): Boolean = value.isLetterOrDigit() || value in "_-:,"
    }
}
