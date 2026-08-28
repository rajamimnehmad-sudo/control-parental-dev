package com.contentfilter.user.chromedataplane

import java.util.Base64
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** Controlled audit fixture. Its mechanism report is laboratory ground truth, never release authority. */
internal class ChromeProvenanceCoverageFixture(
    private val safeImageBytes: ByteArray,
) {
    private val report = AtomicReference(NotRun)
    private val imageRequests = AtomicLong()
    private val svgRequests = AtomicLong()

    fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse? {
        val path = request.target.substringBefore('?').substringBefore('#')
        return when (path) {
            RunnerPath -> html("coverage17-runner", runnerHtml())
            ImagePath -> {
                imageRequests.incrementAndGet()
                ChromePhotosFixtureResponse("coverage17-image", "image/png", safeImageBytes)
            }
            ExternalSvgPath -> {
                svgRequests.incrementAndGet()
                ChromePhotosFixtureResponse(
                    resourceId = "coverage17-external-svg",
                    contentType = "image/svg+xml",
                    originalBytes = ExternalSvg.toByteArray(Charsets.UTF_8),
                )
            }
            ReportPath -> acceptReport(request)
            StatePath -> text("coverage17-state", state())
            else -> null
        }
    }

    fun state(): String =
        "REPORT=${report.get()},IMAGE_REQ=${imageRequests.get()},SVG_REQ=${svgRequests.get()}"

    private fun acceptReport(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse {
        if (request.method != "POST") return text("coverage17-report-method", "POST required", 405)
        val candidate = request.body.toString(Charsets.US_ASCII).take(MaximumReportBytes)
        val accepted =
            candidate.takeIf { value ->
                value.isNotBlank() && value.all { character -> character.isLetterOrDigit() || character in SafePunctuation }
            } ?: Invalid
        report.set(accepted)
        return text("coverage17-report", "accepted")
    }

    private fun runnerHtml(): String {
        val imageBase64 = Base64.getEncoder().encodeToString(safeImageBytes)
        return """
            <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>GLOSH17_RUNNING</title><style>
            body{font-family:sans-serif;margin:0;padding:16px;background:#eef2f5;color:#17212b}
            .grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:10px}.card{background:white;padding:8px}
            img,canvas,.background,svg{display:block;width:100%;aspect-ratio:16/9;background:#59636d;object-fit:contain}
            .background{background-image:url('$ImagePath?id=css');background-size:contain;background-repeat:no-repeat;background-position:center}
            </style></head><body><h1>CHROME-REAL-WEB-PROVENANCE-COVERAGE-17</h1><div class="grid">
            <article class="card"><h2>HTTPS IMG</h2><img id="normal" src="$ImagePath?id=normal"></article>
            <article class="card"><h2>CSS background</h2><div id="css" class="background"></div></article>
            <article class="card"><h2>srcset</h2><img id="srcset" src="$ImagePath?id=srcset-fallback" srcset="$ImagePath?id=srcset-1 1x,$ImagePath?id=srcset-2 2x"></article>
            <article class="card"><h2>picture</h2><picture><source media="(min-width:1px)" srcset="$ImagePath?id=picture-source"><img id="picture" src="$ImagePath?id=picture-fallback"></picture></article>
            <article class="card"><h2>external SVG</h2><img id="external-svg" src="$ExternalSvgPath"></article>
            <article class="card"><h2>inline SVG</h2><svg id="inline-svg" viewBox="0 0 320 180"><rect width="320" height="180" fill="#dc1430"/></svg></article>
            <article class="card"><h2>data URL</h2><img id="data" src="data:image/png;base64,$imageBase64"></article>
            <article class="card"><h2>blob URL</h2><img id="blob"></article>
            <article class="card"><h2>Canvas</h2><canvas id="canvas" width="320" height="180"></canvas></article>
            <article class="card"><h2>same body A</h2><img id="repeat-a" src="$ImagePath?id=repeat"></article>
            <article class="card"><h2>same body B</h2><img id="repeat-b" src="$ImagePath?id=repeat"></article>
            <article class="card"><h2>dynamic replacement</h2><img id="dynamic" src="$ImagePath?id=dynamic-a"></article>
            </div><pre id="result">RUNNING</pre><script>
            (()=>{const decoded=Uint8Array.from(atob('$imageBase64'),value=>value.charCodeAt(0));
            const blob=document.getElementById('blob');blob.src=URL.createObjectURL(new Blob([decoded],{type:'image/png'}));
            const context=document.getElementById('canvas').getContext('2d');context.fillStyle='#dc1430';context.fillRect(0,0,320,180);
            const visible=element=>{const rect=element.getBoundingClientRect();return rect.width>0&&rect.height>0};
            const complete=image=>image.complete&&image.naturalWidth>0;
            const run=async()=>{await new Promise(resolve=>window.addEventListener('load',resolve,{once:true}));
            const dynamic=document.getElementById('dynamic');await new Promise(resolve=>{dynamic.onload=resolve;dynamic.onerror=resolve;dynamic.src='$ImagePath?id=dynamic-b';});
            const entries=[
            ['NORMAL_IMG',complete(document.getElementById('normal'))],['CSS_BACKGROUND',visible(document.getElementById('css'))],
            ['SRCSET',complete(document.getElementById('srcset'))],['PICTURE',complete(document.getElementById('picture'))],
            ['EXTERNAL_SVG',complete(document.getElementById('external-svg'))],['INLINE_SVG',visible(document.getElementById('inline-svg'))],
            ['DATA_URL',complete(document.getElementById('data'))],['BLOB_URL',complete(blob)],['CANVAS',visible(document.getElementById('canvas'))],
            ['REPEAT_A',complete(document.getElementById('repeat-a'))],['REPEAT_B',complete(document.getElementById('repeat-b'))],
            ['DYNAMIC_REPLACE',complete(dynamic)]];
            const value=entries.map(entry=>entry[0]+':'+(entry[1]?'VISIBLE':'MISSING')).join(',');
            await fetch('$ReportPath',{method:'POST',headers:{'Content-Type':'text/plain'},body:value});
            const state=await(await fetch('$StatePath',{cache:'no-store'})).text();document.getElementById('result').textContent=value+'\n'+state;
            document.title=entries.every(entry=>entry[1])?'GLOSH17_COMPLETE':'GLOSH17_INCOMPLETE';URL.revokeObjectURL(blob.src);};run();})();
            </script>${ChromePhotosFixtureLeaseContract.ScriptTag}</body></html>
        """.trimIndent()
    }

    private fun html(
        id: String,
        body: String,
    ) = ChromePhotosFixtureResponse(id, "text/html; charset=utf-8", body.toByteArray(Charsets.UTF_8))

    private fun text(
        id: String,
        body: String,
        statusCode: Int = 200,
    ) = ChromePhotosFixtureResponse(
        resourceId = id,
        contentType = "text/plain; charset=utf-8",
        originalBytes = body.toByteArray(Charsets.UTF_8),
        statusCode = statusCode,
        statusText = if (statusCode == 200) "OK" else "Method Not Allowed",
    )

    private companion object {
        const val RunnerPath = "/web17"
        const val ImagePath = "/web17/media.png"
        const val ExternalSvgPath = "/web17/external.svg"
        const val ReportPath = "/web17/report"
        const val StatePath = "/web17/state"
        const val MaximumReportBytes = 2_048
        const val NotRun = "not_run"
        const val Invalid = "invalid"
        const val SafePunctuation = "_-:,"
        const val ExternalSvg =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 320 180\"><rect width=\"320\" height=\"180\" fill=\"#dc1430\"/></svg>"
    }
}
