package com.contentfilter.user.chromedataplane

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import java.io.ByteArrayOutputStream

internal data class ChromePhotosFixtureResponse(
    val resourceId: String,
    val contentType: String,
    val originalBytes: ByteArray,
    val statusCode: Int = 200,
    val statusText: String = "OK",
)

/** In-memory controlled origin. No intercepted bytes are written to disk. */
internal class ChromePhotosFixtureOrigin {
    val safeImageBytes: ByteArray = createImage(VisualKind.Safe)
    val sentinelImageBytes: ByteArray = createImage(VisualKind.Sentinel)
    val placeholderImageBytes: ByteArray = createImage(VisualKind.Placeholder)

    fun responseFor(requestTarget: String): ChromePhotosFixtureResponse {
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
            <h1>CHROME-PHOTOS-DATA-PLANE-00</h1>
            <p>HTTPS controlado · recursos individuales · decisión por SHA-256</p>
          </section>
          <main class="grid">
            <article class="card"><h2>SAFE-A</h2><img src="/safe-a.png?copy=1" alt="SAFE-A"></article>
            <article class="card"><h2>SENTINEL-BLOCK</h2><img src="/sentinel-block.png" alt="SENTINEL-BLOCK"></article>
            <article class="card"><h2>SAFE repetida 2</h2><img src="/safe-a.png?copy=2" alt="SAFE repeat 2"></article>
            <article class="card"><h2>SAFE repetida 3</h2><img src="/safe-a.png?copy=3" alt="SAFE repeat 3"></article>
            <div class="spacer">Deslizá para activar lazy-load</div>
            <article class="card" id="lazy-card"><h2>LAZY SENTINEL</h2><img loading="lazy" src="/lazy-sentinel.png" alt="Lazy sentinel"></article>
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
    }

    private companion object {
        const val ImageWidth = 320
        const val ImageHeight = 180
        const val PngQuality = 100
    }
}

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
