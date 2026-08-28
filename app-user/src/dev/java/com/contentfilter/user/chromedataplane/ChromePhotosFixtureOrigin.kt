package com.contentfilter.user.chromedataplane

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicReference
import java.util.zip.GZIPOutputStream

internal data class ChromePhotosFixtureResponse(
    val resourceId: String,
    val contentType: String,
    val originalBytes: ByteArray,
    val headers: List<ChromeHttpHeader> = emptyList(),
    val chunked: Boolean = false,
    val statusCode: Int = 200,
    val statusText: String = "OK",
)

internal interface ChromePhotosFixtureSource {
    val safeImageBytes: ByteArray
    val sentinelImageBytes: ByteArray
    val placeholderImageBytes: ByteArray

    fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse

    fun webSemanticsReport(): String = "not_run"

    fun imageAuthorityReport(): String = "not_run"

    fun preRenderShieldReport(): String = "not_run"
}

/** In-memory controlled origin. No intercepted bytes are written to disk. */
internal class ChromePhotosFixtureOrigin(
    safeImageOverride: ByteArray? = null,
    sentinelImageOverride: ByteArray? = null,
    placeholderImageOverride: ByteArray? = null,
    auditPlaceholderOverride: ByteArray? = null,
) : ChromePhotosFixtureSource {
    override val safeImageBytes: ByteArray = safeImageOverride ?: createImage(VisualKind.Safe)
    override val sentinelImageBytes: ByteArray = sentinelImageOverride ?: createImage(VisualKind.Sentinel)
    override val placeholderImageBytes: ByteArray = placeholderImageOverride ?: createImage(VisualKind.Placeholder)
    val auditPlaceholderImageBytes: ByteArray =
        auditPlaceholderOverride ?: placeholderImageOverride?.let { "audit-placeholder".toByteArray() }
            ?: createImage(VisualKind.AuditPlaceholder)
    private val report = AtomicReference("not_run")
    private val imageAuthorityFixture = ChromeImageAuthorityFixture(safeImageBytes, placeholderImageBytes)
    private val preRenderShieldFixture =
        ChromeStockPreRenderShieldFixture(
            networkSentinelBytes = sentinelImageBytes,
            auditPlaceholderBytes = auditPlaceholderImageBytes,
        )

    override fun webSemanticsReport(): String = report.get()

    override fun imageAuthorityReport(): String = imageAuthorityFixture.report()

    override fun preRenderShieldReport(): String = preRenderShieldFixture.state()

    override fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse {
        ChromeVisualShieldFixture.responseFor(request)?.let { return it }
        preRenderShieldFixture.responseFor(request)?.let { return it }
        imageAuthorityFixture.responseFor(request)?.let { return it }
        val requestTarget = request.target
        val path = requestTarget.substringBefore('?').substringBefore('#')
        return when (path) {
            "/", "/index.html" -> htmlResponse("fixture-index", fixtureHtml())
            "/second" -> htmlResponse("fixture-second", secondPageHtml())
            "/fixture-lease.js" ->
                ChromePhotosFixtureResponse(
                    resourceId = "fixture-lease-script",
                    contentType = "application/javascript; charset=utf-8",
                    originalBytes = ChromePhotosFixtureLeaseContract.script.toByteArray(Charsets.UTF_8),
                )
            ChromePhotosFixtureLeaseContract.HeartbeatPath ->
                ChromePhotosFixtureResponse(
                    resourceId = "fixture-heartbeat",
                    contentType = "text/plain; charset=utf-8",
                    originalBytes = ByteArray(0),
                    statusCode = 204,
                    statusText = "No Content",
                )
            "/safe-a.png" -> imageResponse("safe-a", safeImageBytes)
            "/sentinel-block.png" -> imageResponse("sentinel-block", sentinelImageBytes)
            "/lazy-sentinel.png" -> imageResponse("lazy-sentinel", sentinelImageBytes)
            "/web11a" -> htmlResponse("web11a-runner", webSemanticsHtml())
            "/web11a/echo" ->
                ChromePhotosFixtureResponse(
                    resourceId = "web11a-echo",
                    contentType = request.firstHeader("Content-Type") ?: "application/octet-stream",
                    originalBytes = request.body,
                    headers =
                        listOf(
                            ChromeHttpHeader("X-Glosh-Method", request.method),
                            ChromeHttpHeader("X-Glosh-Body-Sha256", sha256(request.body)),
                            ChromeHttpHeader(
                                "Access-Control-Allow-Origin",
                                "https://${com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract.FixtureHost}",
                            ),
                            ChromeHttpHeader("Access-Control-Allow-Credentials", "true"),
                        ),
                    statusCode = if (request.method == "POST") 201 else 200,
                    statusText = if (request.method == "POST") "Created" else "OK",
                )
            "/web11a/set-cookies" ->
                textResponse(
                    id = "web11a-set-cookies",
                    body = "cookies-set",
                    headers =
                        listOf(
                            ChromeHttpHeader("Set-Cookie", "gloshA=alpha; Secure; HttpOnly; SameSite=Lax; Path=/"),
                            ChromeHttpHeader("Set-Cookie", "gloshB=beta; Secure; SameSite=Strict; Path=/"),
                        ),
                )
            "/web11a/cookies" ->
                textResponse(
                    id = "web11a-cookies",
                    body = if (request.firstHeader("Cookie")?.contains("gloshA=alpha") == true) "cookie-pass" else "cookie-missing",
                )
            "/web11a/auth" ->
                if (request.firstHeader("Authorization") == "Bearer glosh-fixture-token") {
                    textResponse("web11a-auth", "auth-pass")
                } else {
                    textResponse(
                        id = "web11a-auth",
                        body = "auth-required",
                        headers = listOf(ChromeHttpHeader("WWW-Authenticate", "Bearer realm=\"glosh-fixture\"")),
                        statusCode = 401,
                        statusText = "Unauthorized",
                    )
                }
            "/web11a/redirect" -> {
                val code = requestTarget.queryParameter("code")?.toIntOrNull()?.takeIf { it in RedirectFixtureCodes } ?: 302
                ChromePhotosFixtureResponse(
                    resourceId = "web11a-redirect-$code",
                    contentType = "text/plain; charset=utf-8",
                    originalBytes = ByteArray(0),
                    headers = listOf(ChromeHttpHeader("Location", "/web11a/final?from=$code")),
                    statusCode = code,
                    statusText = redirectReason(code),
                )
            }
            "/web11a/final" ->
                textResponse(
                    id = "web11a-redirect-final",
                    body = "redirect-pass",
                    headers = listOf(ChromeHttpHeader("X-Glosh-Method", request.method)),
                )
            "/web11a/gzip" ->
                ChromePhotosFixtureResponse(
                    resourceId = "web11a-gzip",
                    contentType = "text/html; charset=utf-8",
                    originalBytes = gzip("gzip-pass".toByteArray()),
                    headers =
                        listOf(
                            ChromeHttpHeader("Content-Encoding", "gzip"),
                            ChromeHttpHeader("Vary", "Accept-Encoding"),
                        ),
                )
            "/web11a/chunked" ->
                ChromePhotosFixtureResponse(
                    resourceId = "web11a-chunked",
                    contentType = "text/plain; charset=utf-8",
                    originalBytes = "chunked-pass".toByteArray(),
                    chunked = true,
                )
            "/web11a/range" -> rangeResponse(request)
            "/web11a/etag" -> etagResponse(request)
            "/web11a/headers" ->
                textResponse(
                    id = "web11a-headers",
                    body = "headers-pass",
                    headers =
                        listOf(
                            ChromeHttpHeader("Content-Security-Policy", "default-src 'self'"),
                            ChromeHttpHeader("Cross-Origin-Resource-Policy", "same-origin"),
                            ChromeHttpHeader("Cross-Origin-Opener-Policy", "same-origin"),
                            ChromeHttpHeader("Cross-Origin-Embedder-Policy", "require-corp"),
                            ChromeHttpHeader("Access-Control-Allow-Origin", "*"),
                        ),
                )
            "/web11a/download" ->
                ChromePhotosFixtureResponse(
                    resourceId = "web11a-download",
                    contentType = "application/octet-stream",
                    originalBytes = ByteArray(DownloadBytes) { index -> (index % 251).toByte() },
                    headers = listOf(ChromeHttpHeader("Content-Disposition", "attachment; filename=glosh-11a.bin")),
                )
            "/web11a/large" ->
                ChromePhotosFixtureResponse(
                    resourceId = "web11a-large",
                    contentType = "application/octet-stream",
                    originalBytes = ByteArray(LargeFixtureBytes) { index -> (index % 239).toByte() },
                    chunked = true,
                )
            "/web11a/report" -> {
                val candidate = request.body.toString(Charsets.US_ASCII).take(MaximumReportBytes)
                val accepted = candidate.takeIf { it.isNotBlank() && it.all(::isReportCharacter) } ?: "invalid"
                report.set(accepted)
                textResponse("web11a-report", "accepted")
            }
            else ->
                ChromePhotosFixtureResponse(
                    resourceId = "unknown",
                    contentType = "text/plain; charset=utf-8",
                    originalBytes = "Not found".toByteArray(),
                    statusCode = 404,
                    statusText = "Not Found",
                )
        }
    }

    private fun htmlResponse(
        id: String,
        body: String,
    ): ChromePhotosFixtureResponse =
        ChromePhotosFixtureResponse(
            resourceId = id,
            contentType = "text/html; charset=utf-8",
            originalBytes = body.toByteArray(Charsets.UTF_8),
        )

    private fun imageResponse(
        id: String,
        bytes: ByteArray,
    ): ChromePhotosFixtureResponse =
        ChromePhotosFixtureResponse(
            resourceId = id,
            contentType = "image/png",
            originalBytes = bytes,
        )

    private fun textResponse(
        id: String,
        body: String,
        headers: List<ChromeHttpHeader> = emptyList(),
        statusCode: Int = 200,
        statusText: String = "OK",
    ) = ChromePhotosFixtureResponse(
        resourceId = id,
        contentType = "text/plain; charset=utf-8",
        originalBytes = body.toByteArray(),
        headers = headers,
        statusCode = statusCode,
        statusText = statusText,
    )

    private fun rangeResponse(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse {
        val bytes = RangeFixtureBytes
        val range = request.firstHeader("Range")
        if (range == null) {
            return ChromePhotosFixtureResponse(
                resourceId = "web11a-range-full",
                contentType = "application/octet-stream",
                originalBytes = bytes,
                headers = listOf(ChromeHttpHeader("Accept-Ranges", "bytes"), ChromeHttpHeader("ETag", RangeEtag)),
            )
        }
        val match =
            RangePattern.matchEntire(range)
                ?: return textResponse(
                    "web11a-range-invalid",
                    "invalid range",
                    statusCode = 416,
                    statusText = "Range Not Satisfiable",
                )
        val start = match.groupValues[1].toIntOrNull() ?: 0
        val end = match.groupValues[2].toIntOrNull() ?: (bytes.lastIndex)
        if (start !in bytes.indices || end !in start..bytes.lastIndex) {
            return textResponse(
                "web11a-range-invalid",
                "invalid range",
                statusCode = 416,
                statusText = "Range Not Satisfiable",
            )
        }
        return ChromePhotosFixtureResponse(
            resourceId = "web11a-range-partial",
            contentType = "application/octet-stream",
            originalBytes = bytes.copyOfRange(start, end + 1),
            headers =
                listOf(
                    ChromeHttpHeader("Accept-Ranges", "bytes"),
                    ChromeHttpHeader("Content-Range", "bytes $start-$end/${bytes.size}"),
                    ChromeHttpHeader("ETag", RangeEtag),
                ),
            statusCode = 206,
            statusText = "Partial Content",
        )
    }

    private fun etagResponse(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse =
        if (request.firstHeader("If-None-Match") == EtagValue) {
            ChromePhotosFixtureResponse(
                resourceId = "web11a-etag-not-modified",
                contentType = "text/plain; charset=utf-8",
                originalBytes = ByteArray(0),
                headers = listOf(ChromeHttpHeader("ETag", EtagValue), ChromeHttpHeader("Cache-Control", "max-age=60")),
                statusCode = 304,
                statusText = "Not Modified",
            )
        } else {
            textResponse(
                id = "web11a-etag-current",
                body = "etag-pass",
                headers =
                    listOf(
                        ChromeHttpHeader("ETag", EtagValue),
                        ChromeHttpHeader("Last-Modified", "Sun, 24 Aug 2026 12:00:00 GMT"),
                        ChromeHttpHeader("Cache-Control", "max-age=60"),
                    ),
            )
        }

    private fun webSemanticsHtml(): String =
        """
        <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Glosh 11A Web Semantics</title><style>body{font-family:sans-serif;padding:24px}pre{white-space:pre-wrap}</style></head>
        <body><h1>Glosh 11A</h1><pre id="result">RUNNING</pre><script>
        (async()=>{const out=[];const check=(n,v)=>out.push(n+':'+(v?'PASS':'FAIL'));
        for(const method of ['GET','HEAD','POST','PUT','PATCH','DELETE','OPTIONS']){const body=['POST','PUT','PATCH'].includes(method)?JSON.stringify({method}):undefined;const r=await fetch('/web11a/echo',{method,body,headers:body?{'Content-Type':'application/json'}:{}});check(method,r.headers.get('x-glosh-method')===method);}
        const form=new URLSearchParams({alpha:'one',beta:'two'});check('FORM',(await (await fetch('/web11a/echo',{method:'POST',body:form})).text())===form.toString());
        const multipart=new FormData();multipart.append('alpha','one');const multipartResponse=await fetch('/web11a/echo',{method:'POST',body:multipart});check('MULTIPART',(await multipartResponse.arrayBuffer()).byteLength>0&&multipartResponse.headers.get('content-type').startsWith('multipart/form-data'));
        const binary=new Uint8Array([0,1,2,127,128,255]);const binaryResponse=new Uint8Array(await (await fetch('/web11a/echo',{method:'POST',headers:{'Content-Type':'application/octet-stream'},body:binary})).arrayBuffer());check('BINARY',binaryResponse.length===binary.length&&binary.every((v,i)=>v===binaryResponse[i]));
        await fetch('/web11a/set-cookies',{credentials:'include'});check('COOKIE',(await (await fetch('/web11a/cookies',{credentials:'include'})).text())==='cookie-pass');
        check('AUTH',(await (await fetch('/web11a/auth',{headers:{Authorization:'Bearer glosh-fixture-token'}})).text())==='auth-pass');
        for(const code of [301,302,303,307,308]){const r=await fetch('/web11a/redirect?code='+code,{method:'POST',body:'redirect-body'});const expected=code>=307?'POST':'GET';check('REDIRECT'+code,r.redirected&&(await r.text())==='redirect-pass'&&r.headers.get('x-glosh-method')===expected);}
        check('GZIP',(await (await fetch('/web11a/gzip')).text())==='gzip-pass');check('CHUNKED',(await (await fetch('/web11a/chunked')).text())==='chunked-pass');
        const range=await fetch('/web11a/range',{headers:{Range:'bytes=10-31'}});check('RANGE',range.status===206&&(await range.arrayBuffer()).byteLength===22);
        const first=await fetch('/web11a/etag',{cache:'no-store'});const etag=first.headers.get('etag');const second=await fetch('/web11a/etag',{headers:{'If-None-Match':etag}});check('ETAG',second.status===304);
        const download=await fetch('/web11a/download');check('DOWNLOAD',download.headers.get('content-disposition').includes('glosh-11a.bin')&&(await download.arrayBuffer()).byteLength===$DownloadBytes);
        const security=await fetch('/web11a/headers');check('CSP_CORS',security.headers.get('content-security-policy')==="default-src 'self'"&&security.headers.get('access-control-allow-origin')==='*');
        const large=await (await fetch('/web11a/large')).arrayBuffer();check('LARGE',large.byteLength===$LargeFixtureBytes);
        await fetch('/web11a/report',{method:'POST',headers:{'Content-Type':'text/plain'},body:out.join(',')});document.getElementById('result').textContent=out.join('\n');document.title=out.every(x=>x.endsWith('PASS'))?'GLOSH11A_PASS':'GLOSH11A_FAIL';
        })().catch(e=>{document.getElementById('result').textContent='ERROR:'+e.name;document.title='GLOSH11A_FAIL'});
        </script>${ChromePhotosFixtureLeaseContract.ScriptTag}</body></html>
        """.trimIndent()

    private fun gzip(bytes: ByteArray): ByteArray =
        ByteArrayOutputStream().use { output ->
            GZIPOutputStream(output).use { it.write(bytes) }
            output.toByteArray()
        }

    private fun String.queryParameter(name: String): String? =
        substringAfter('?', "")
            .split('&')
            .mapNotNull { entry -> entry.split('=', limit = 2).takeIf { it.size == 2 } }
            .firstOrNull { it[0] == name }
            ?.get(1)

    private fun redirectReason(code: Int): String =
        when (code) {
            301 -> "Moved Permanently"
            302 -> "Found"
            303 -> "See Other"
            307 -> "Temporary Redirect"
            308 -> "Permanent Redirect"
            else -> "Found"
        }

    private fun fixtureHtml(): String =
        """
        <!doctype html>
        <html lang="es">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <title>Glosh Chrome Photos Data Plane</title>
          <style>
            :root { color-scheme: light; font-family: sans-serif; background:#f4f6f8; color:#18202a; }
            body { margin:0; padding:20px; }
            h1 { font-size:24px; margin:12px 0; }
            .status { background:#13263a; color:white; border-radius:14px; padding:16px; }
            .grid { display:grid; grid-template-columns:1fr; gap:18px; margin-top:18px; }
            .card { background:white; border-radius:14px; padding:12px; box-shadow:0 2px 8px #0002; }
            img { display:block; width:100%; aspect-ratio:16/9; object-fit:cover; border-radius:10px; background:#77808a; }
            .spacer { height:145vh; display:grid; place-items:center; color:#506070; }
            a { display:inline-block; padding:12px 16px; background:#075ea8; color:white; border-radius:10px; text-decoration:none; }
          </style>
        </head>
        <body>
          <section class="status" id="fixture-marker">
            <h1>CHROME-PHOTOS-GLOSHIA-REAL-WEB-BATCH-02</h1>
            <p>HTTPS real · GloshIA Visual R3.1 local · fail closed</p>
          </section>
          <main class="grid">
            <article class="card"><h2>SAFE PNG real</h2><img src="${ChromePhotosRealWebLabConfig.SafePngUrl}" alt="SAFE PNG"></article>
            <article class="card"><h2>BLOCK WebP real</h2><img src="${ChromePhotosRealWebLabConfig.BlockWebpUrl}" alt="BLOCK WebP"></article>
            <article class="card"><h2>UNKNOWN JPEG</h2><img src="${ChromePhotosRealWebLabConfig.UnknownJpegUrl}" alt="UNKNOWN JPEG"></article>
            <article class="card"><h2>UNKNOWN WebP</h2><img src="${ChromePhotosRealWebLabConfig.UnknownWebpUrl}" alt="UNKNOWN WebP"></article>
            <article class="card"><h2>BLOCK repetida</h2><img src="${ChromePhotosRealWebLabConfig.BlockWebpUrl}" alt="BLOCK repeat"></article>
            <article class="card"><h2>AVIF redirect permitido</h2><img src="${ChromePhotosRealWebLabConfig.AllowedRedirectUrl}" alt="AVIF redirect"></article>
            <article class="card"><h2>UNKNOWN AVIF directa</h2><img src="${ChromePhotosRealWebLabConfig.UnknownAvifUrl}" alt="UNKNOWN AVIF"></article>
            ${chromePhotosGloshiaPublicMatrixCards()}
            <div class="spacer">Deslizá para activar lazy-load</div>
            <article class="card" id="lazy-card"><h2>LAZY BLOCK real</h2><img loading="lazy" src="${ChromePhotosRealWebLabConfig.BlockWebpUrl}" alt="Lazy block"></article>
            <article class="card"><a href="${ChromePhotosRealWebLabConfig.PublicHtmlUrl}">HTTPS remoto real</a></article>
            <article class="card"><a href="${ChromePhotosRealWebLabConfig.DisallowedRedirectUrl}">Redirect no permitido</a></article>
            <article class="card"><a href="/second">Probar adelante / atrás</a></article>
          </main>
          ${ChromePhotosFixtureLeaseContract.ScriptTag}
        </body>
        </html>
        """.trimIndent()

    private fun secondPageHtml(): String =
        """
        <!doctype html><html lang="es"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Glosh second</title><style>body{font-family:sans-serif;padding:28px;background:#eef2f5;color:#18202a}a{font-size:20px}</style></head>
        <body><h1>Segunda página segura</h1><p>Contenido HTML pasado sin modificación.</p><a href="/">Volver a la fixture</a>
        ${ChromePhotosFixtureLeaseContract.ScriptTag}</body></html>
        """.trimIndent()

    private fun createImage(kind: VisualKind): ByteArray {
        val bitmap = Bitmap.createBitmap(ImageWidth, ImageHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        when (kind) {
            VisualKind.Safe -> {
                canvas.drawColor(Color.rgb(70, 155, 210))
                paint.color = Color.rgb(37, 120, 64)
                canvas.drawRect(0f, 190f, ImageWidth.toFloat(), ImageHeight.toFloat(), paint)
                paint.color = Color.rgb(235, 210, 96)
                canvas.drawCircle(270f, 88f, 42f, paint)
            }
            VisualKind.Sentinel -> {
                canvas.drawColor(Color.rgb(220, 20, 48))
                paint.color = Color.BLACK
                repeat(8) { index ->
                    if (index % 2 == 0) canvas.drawRect(index * 40f, 0f, (index + 1) * 40f, 180f, paint)
                }
            }
            VisualKind.Placeholder -> canvas.drawColor(Color.rgb(92, 100, 108))
            VisualKind.AuditPlaceholder -> {
                canvas.drawColor(Color.rgb(55, 65, 81))
                paint.color = Color.rgb(0, 200, 255)
                repeat(8) { index ->
                    if (index % 2 == 0) canvas.drawRect(index * 40f, 0f, (index + 1) * 40f, 180f, paint)
                }
            }
        }
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.typeface = Typeface.DEFAULT_BOLD
        paint.textSize = 30f
        canvas.drawText(kind.label, ImageWidth / 2f, ImageHeight / 2f + 10f, paint)
        return ByteArrayOutputStream().use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, PngQuality, output))
            bitmap.recycle()
            output.toByteArray()
        }
    }

    private enum class VisualKind(
        val label: String,
    ) {
        Safe("SAFE-A ORIGINAL"),
        Sentinel("SENTINEL ORIGINAL"),
        Placeholder("BLOQUEADA POR GLOSH"),
        AuditPlaceholder("REPLACE ALL AUDIT"),
    }

    private companion object {
        const val ImageWidth = 320
        const val ImageHeight = 180
        const val PngQuality = 100
        const val DownloadBytes = 256 * 1024
        const val LargeFixtureBytes = 4 * 1024 * 1024
        const val MaximumReportBytes = 1024
        const val EtagValue = "\"glosh-11a-v1\""
        const val RangeEtag = "\"glosh-range-v1\""
        val RedirectFixtureCodes = setOf(301, 302, 303, 307, 308)
        val RangePattern = Regex("bytes=(\\d*)-(\\d*)")
        val RangeFixtureBytes = ByteArray(4096) { index -> (index % 251).toByte() }

        fun isReportCharacter(value: Char): Boolean = value.isLetterOrDigit() || value in "_-:,"
    }
}

internal fun chromePhotosGloshiaPublicMatrixCards(): String =
    buildString {
        ChromePhotosRealWebLabConfig.gloshiaPublicJpegUrls.forEachIndexed { index, url ->
            val lazy = if (index >= EagerGloshiaImages) " loading=\"lazy\"" else ""
            append("<article class=\"card\"><h2>GloshIA JPEG ${index + 1}</h2>")
            append("<img$lazy src=\"$url\" alt=\"Public GloshIA vector ${index + 1}\"></article>\n")
        }
        val repeated = ChromePhotosRealWebLabConfig.gloshiaPublicJpegUrls.first()
        append("<article class=\"card\"><h2>GloshIA JPEG repetida</h2>")
        append("<img loading=\"lazy\" src=\"$repeated\" alt=\"Repeated public vector\"></article>")
    }

private const val EagerGloshiaImages = 4

internal object ChromePhotosFixtureLeaseContract {
    const val HeartbeatPath = "/__glosh_lease"
    const val ScriptTag = "<script src=\"/fixture-lease.js\"></script>"

    val script: String =
        """
        (() => {
          const beat = () => {
            if (document.visibilityState === 'visible') {
              fetch('$HeartbeatPath', { cache:'no-store', credentials:'omit' }).catch(() => {});
            }
          };
          document.addEventListener('visibilitychange', beat);
          window.setInterval(beat, 250);
          beat();
        })();
        """.trimIndent()

    fun isHeartbeatTarget(target: String): Boolean = target.substringBefore('?') == HeartbeatPath
}
