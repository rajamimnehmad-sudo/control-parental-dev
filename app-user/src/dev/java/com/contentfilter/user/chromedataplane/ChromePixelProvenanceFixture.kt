package com.contentfilter.user.chromedataplane

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal enum class ChromePixelProvenanceSource {
    RendererLocal,
    NetworkCarrier,
    BrowserStorage,
}

internal enum class ChromePixelProvenanceVector(
    val reportKey: String,
    val source: ChromePixelProvenanceSource,
) {
    DataUrl("DATA_URL", ChromePixelProvenanceSource.RendererLocal),
    BlobUrl("BLOB_URL", ChromePixelProvenanceSource.RendererLocal),
    Canvas2d("CANVAS_2D", ChromePixelProvenanceSource.RendererLocal),
    WebGl("WEBGL", ChromePixelProvenanceSource.RendererLocal),
    InlineSvg("INLINE_SVG", ChromePixelProvenanceSource.RendererLocal),
    JavaScript("JAVASCRIPT", ChromePixelProvenanceSource.NetworkCarrier),
    Json("JSON", ChromePixelProvenanceSource.NetworkCarrier),
    WebAssembly("WASM", ChromePixelProvenanceSource.NetworkCarrier),
    ServiceWorker("SERVICE_WORKER", ChromePixelProvenanceSource.BrowserStorage),
    CacheStorage("CACHE_STORAGE", ChromePixelProvenanceSource.BrowserStorage),
}

/**
 * Deterministic 13A origin used to observe pixel provenance that is not represented by a direct
 * image response. It records behavior only; it does not label any renderer-local vector as safe.
 */
internal class ChromePixelProvenanceFixture {
    private val pageReport = AtomicReference(NotRun)
    private val externalJavaScriptRequests = AtomicLong()
    private val jsonRequests = AtomicLong()
    private val wasmRequests = AtomicLong()
    private val serviceWorkerScriptRequests = AtomicLong()
    private val serviceWorkerOriginFallbacks = AtomicLong()
    private val cacheStorageOriginFallbacks = AtomicLong()

    fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse? {
        val path = request.target.substringBefore('?').substringBefore('#')
        return when (path) {
            RunnerPathWithoutSlash ->
                ChromePhotosFixtureResponse(
                    resourceId = "web13a-runner-redirect",
                    contentType = TextContentType,
                    originalBytes = ByteArray(0),
                    headers = NoStoreHeaders + ChromeHttpHeader("Location", RunnerPath),
                    statusCode = 308,
                    statusText = "Permanent Redirect",
                )
            RunnerPath -> htmlResponse("web13a-runner", runnerHtml())
            ExternalJavaScriptPath -> {
                externalJavaScriptRequests.incrementAndGet()
                bytesResponse(
                    id = "web13a-external-js",
                    contentType = JavaScriptContentType,
                    bytes = externalJavaScript().toByteArray(Charsets.UTF_8),
                )
            }
            JsonPath -> {
                jsonRequests.incrementAndGet()
                bytesResponse(
                    id = "web13a-json",
                    contentType = JsonContentType,
                    bytes = JsonBody.toByteArray(Charsets.UTF_8),
                )
            }
            WasmPath -> {
                wasmRequests.incrementAndGet()
                bytesResponse(
                    id = "web13a-wasm",
                    contentType = WasmContentType,
                    bytes = EmptyWasmModule,
                )
            }
            ServiceWorkerPath -> {
                serviceWorkerScriptRequests.incrementAndGet()
                bytesResponse(
                    id = "web13a-service-worker",
                    contentType = JavaScriptContentType,
                    bytes = serviceWorkerScript().toByteArray(Charsets.UTF_8),
                    headers = NoStoreHeaders + ChromeHttpHeader("Service-Worker-Allowed", RunnerPath),
                )
            }
            ServiceWorkerSyntheticPath -> originFallback("web13a-sw-origin-fallback", serviceWorkerOriginFallbacks)
            CacheStoragePath -> originFallback("web13a-cache-origin-fallback", cacheStorageOriginFallbacks)
            ReportPath -> recordReport(request)
            StatePath -> textResponse("web13a-state", report())
            else -> null
        }
    }

    fun report(): String =
        buildString {
            append("PAGE=")
            append(pageReport.get())
            append(",JS_REQ=")
            append(externalJavaScriptRequests.get())
            append(",JSON_REQ=")
            append(jsonRequests.get())
            append(",WASM_REQ=")
            append(wasmRequests.get())
            append(",SW_SCRIPT_REQ=")
            append(serviceWorkerScriptRequests.get())
            append(",SW_ORIGIN_FALLBACK=")
            append(serviceWorkerOriginFallbacks.get())
            append(",CACHE_ORIGIN_FALLBACK=")
            append(cacheStorageOriginFallbacks.get())
        }

    private fun recordReport(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse {
        if (request.method != "POST") {
            return textResponse(
                id = "web13a-report-method-rejected",
                body = "POST required",
                statusCode = 405,
                statusText = "Method Not Allowed",
            )
        }
        val candidate = request.body.toString(Charsets.US_ASCII).take(MaximumReportBytes)
        val accepted =
            candidate.takeIf { report ->
                report.isNotBlank() &&
                    report.all { character -> character.isLetterOrDigit() || character in SafeReportPunctuation }
            } ?: InvalidReport
        pageReport.set(accepted)
        return textResponse("web13a-report", "accepted")
    }

    private fun originFallback(
        id: String,
        counter: AtomicLong,
    ): ChromePhotosFixtureResponse {
        counter.incrementAndGet()
        return textResponse(
            id = id,
            body = "renderer storage interception missing",
            statusCode = 409,
            statusText = "Conflict",
        )
    }

    private fun runnerHtml(): String =
        """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width,initial-scale=1">
          <title>GLOSH13A_RUNNING</title>
          <style>
            body{font-family:sans-serif;margin:0;padding:18px;background:#eef2f5;color:#17212b}
            h1{font-size:22px}.grid{display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px}
            .card{background:white;border-radius:12px;padding:10px;box-shadow:0 2px 8px #0002}
            img,canvas,svg{display:block;width:100%;aspect-ratio:16/9;border-radius:8px;background:#707982}
            pre{white-space:pre-wrap;background:#17212b;color:white;border-radius:10px;padding:12px}
          </style>
          <script defer src="$ExternalJavaScriptPath"></script>
        </head>
        <body>
          <h1>CHROME-PROVENANCE-GAP-13A</h1>
          <p>Observation fixture. RENDERED means the browser produced pixels; it is not a safety decision.</p>
          <div class="grid">
            <article class="card"><h2>data:</h2><img id="data-url" src="data:image/png;base64,$SentinelPngBase64"></article>
            <article class="card"><h2>blob:</h2><img id="blob-url"></article>
            <article class="card"><h2>Canvas 2D</h2><canvas id="canvas-2d" width="320" height="180"></canvas></article>
            <article class="card"><h2>WebGL</h2><canvas id="webgl" width="320" height="180"></canvas></article>
            <article class="card"><h2>Inline SVG</h2><svg id="inline-svg" viewBox="0 0 320 180"><rect width="320" height="180" fill="#dc1430"/><path d="M0 0h40v180H0zm80 0h40v180H80zm80 0h40v180h-40zm80 0h40v180h-40z" fill="#000"/></svg></article>
            <article class="card"><h2>External JavaScript</h2><canvas id="external-js" width="320" height="180"></canvas></article>
            <article class="card"><h2>JSON instructions</h2><canvas id="json" width="320" height="180"></canvas></article>
            <article class="card"><h2>WebAssembly control</h2><canvas id="wasm" width="320" height="180"></canvas></article>
            <article class="card"><h2>Service Worker synthetic</h2><img id="service-worker"></article>
            <article class="card"><h2>CacheStorage synthetic</h2><img id="cache-storage"></article>
          </div>
          <pre id="result">RUNNING</pre>
          <script>
          (()=>{
            const results=[];
            const mark=(key,value)=>{results.push(key+':'+value);};
            const decode=(value)=>Uint8Array.from(atob(value),character=>character.charCodeAt(0));
            const sleep=(millis)=>new Promise(resolve=>setTimeout(resolve,millis));
            const bounded=(promise,millis)=>Promise.race([promise,sleep(millis).then(()=>{throw new Error('timeout')})]);
            const imageResult=(image)=>new Promise(resolve=>{
              const done=()=>resolve(image.naturalWidth>0&&image.naturalHeight>0?'RENDERED':'BLOCKED');
              if(image.complete){done();return;}
              image.addEventListener('load',done,{once:true});
              image.addEventListener('error',()=>resolve('BLOCKED'),{once:true});
            });
            const paint=(canvas,background,stripe)=>{
              const context=canvas.getContext('2d');
              if(!context)return false;
              context.fillStyle=background;context.fillRect(0,0,320,180);context.fillStyle=stripe;
              for(let index=0;index<8;index+=2)context.fillRect(index*40,0,40,180);
              const pixel=context.getImageData(60,90,1,1).data;
              return pixel[0]>150;
            };
            const paintWebGl=(canvas)=>{
              const gl=canvas.getContext('webgl')||canvas.getContext('experimental-webgl');
              if(!gl)return 'UNAVAILABLE';
              gl.clearColor(0.86,0.08,0.19,1);gl.clear(gl.COLOR_BUFFER_BIT);
              const pixel=new Uint8Array(4);gl.readPixels(1,1,1,1,gl.RGBA,gl.UNSIGNED_BYTE,pixel);
              return pixel[0]>150?'RENDERED':'ERROR';
            };
            const waitForController=async()=>{
              if(navigator.serviceWorker.controller)return true;
              await bounded(new Promise(resolve=>navigator.serviceWorker.addEventListener('controllerchange',resolve,{once:true})),5000);
              return navigator.serviceWorker.controller!==null;
            };
            const run=async()=>{
              if(document.readyState!=='complete')await new Promise(resolve=>window.addEventListener('load',resolve,{once:true}));
              mark('DATA_URL',await bounded(imageResult(document.getElementById('data-url')),5000));
              const blobImage=document.getElementById('blob-url');
              blobImage.src=URL.createObjectURL(new Blob([decode('$SentinelPngBase64')],{type:'image/png'}));
              mark('BLOB_URL',await bounded(imageResult(blobImage),5000));
              mark('CANVAS_2D',paint(document.getElementById('canvas-2d'),'#dc1430','#000')?'RENDERED':'ERROR');
              mark('WEBGL',paintWebGl(document.getElementById('webgl')));
              const svg=document.getElementById('inline-svg').getBoundingClientRect();
              mark('INLINE_SVG',svg.width>0&&svg.height>0?'RENDERED':'ERROR');
              mark('JAVASCRIPT',window.__glosh13aExternalJs==='RENDERED'?'RENDERED':'ERROR');
              try{
                const config=await (await fetch('$JsonPath',{cache:'no-store'})).json();
                mark('JSON',paint(document.getElementById('json'),config.background,config.stripe)?'RENDERED':'ERROR');
              }catch(error){mark('JSON','ERROR');}
              try{
                await WebAssembly.instantiateStreaming(fetch('$WasmPath',{cache:'no-store'}));
                mark('WASM',paint(document.getElementById('wasm'),'#dc1430','#000')?'RENDERED':'ERROR');
              }catch(error){mark('WASM','BLOCKED');}
              if(!('serviceWorker' in navigator)){
                mark('SERVICE_WORKER','UNAVAILABLE');mark('CACHE_STORAGE','UNAVAILABLE');
              }else{
                try{
                  await navigator.serviceWorker.register('$ServiceWorkerPath?run='+Date.now(),{scope:'$RunnerPath',updateViaCache:'none'});
                  await navigator.serviceWorker.ready;
                  await waitForController();
                  const swImage=document.getElementById('service-worker');swImage.src='$ServiceWorkerSyntheticPath?nonce='+Date.now();
                  mark('SERVICE_WORKER',await bounded(imageResult(swImage),5000));
                  const cacheImage=document.getElementById('cache-storage');cacheImage.src='$CacheStoragePath?nonce='+Date.now();
                  mark('CACHE_STORAGE',await bounded(imageResult(cacheImage),5000));
                }catch(error){mark('SERVICE_WORKER','ERROR');mark('CACHE_STORAGE','ERROR');}
              }
              const report=results.join(',');
              await fetch('$ReportPath',{method:'POST',headers:{'Content-Type':'text/plain'},body:report});
              document.getElementById('result').textContent=report.split(',').join('\n');
              document.title='GLOSH13A_COMPLETE';
            };
            run().catch(error=>{document.getElementById('result').textContent='RUNNER:ERROR';document.title='GLOSH13A_ERROR';});
          })();
          </script>
          ${ChromePhotosFixtureLeaseContract.ScriptTag}
        </body>
        </html>
        """.trimIndent()

    private fun externalJavaScript(): String =
        """
        (()=>{
          const canvas=document.getElementById('external-js');
          const context=canvas&&canvas.getContext('2d');
          if(!context){window.__glosh13aExternalJs='ERROR';return;}
          context.fillStyle='#dc1430';context.fillRect(0,0,320,180);context.fillStyle='#000';
          for(let index=0;index<8;index+=2)context.fillRect(index*40,0,40,180);
          window.__glosh13aExternalJs='RENDERED';
        })();
        """.trimIndent()

    private fun serviceWorkerScript(): String =
        """
        const CACHE_NAME='glosh-13a-v1';
        const CACHE_PATH='$CacheStoragePath';
        const SYNTHETIC_PATH='$ServiceWorkerSyntheticPath';
        const IMAGE_BASE64='$SentinelPngBase64';
        const bytes=()=>Uint8Array.from(atob(IMAGE_BASE64),character=>character.charCodeAt(0));
        const imageResponse=()=>new Response(bytes(),{status:200,headers:{'Content-Type':'image/png','Cache-Control':'no-store'}});
        self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE_NAME).then(cache=>cache.put(CACHE_PATH,imageResponse())).then(()=>self.skipWaiting())));
        self.addEventListener('activate',event=>event.waitUntil(self.clients.claim()));
        self.addEventListener('fetch',event=>{
          const path=new URL(event.request.url).pathname;
          if(path===SYNTHETIC_PATH){event.respondWith(Promise.resolve(imageResponse()));return;}
          if(path===CACHE_PATH){event.respondWith(caches.open(CACHE_NAME).then(cache=>cache.match(CACHE_PATH)).then(response=>response||new Response('',{status:503})));}
        });
        """.trimIndent()

    private fun htmlResponse(
        id: String,
        body: String,
    ): ChromePhotosFixtureResponse =
        bytesResponse(id, HtmlContentType, body.toByteArray(Charsets.UTF_8))

    private fun textResponse(
        id: String,
        body: String,
        statusCode: Int = 200,
        statusText: String = "OK",
    ): ChromePhotosFixtureResponse =
        ChromePhotosFixtureResponse(
            resourceId = id,
            contentType = TextContentType,
            originalBytes = body.toByteArray(Charsets.UTF_8),
            headers = NoStoreHeaders,
            statusCode = statusCode,
            statusText = statusText,
        )

    private fun bytesResponse(
        id: String,
        contentType: String,
        bytes: ByteArray,
        headers: List<ChromeHttpHeader> = NoStoreHeaders,
    ): ChromePhotosFixtureResponse =
        ChromePhotosFixtureResponse(
            resourceId = id,
            contentType = contentType,
            originalBytes = bytes,
            headers = headers,
        )

    private companion object {
        const val RunnerPathWithoutSlash = "/web13a"
        const val RunnerPath = "/web13a/"
        const val ExternalJavaScriptPath = "/web13a/external.js"
        const val JsonPath = "/web13a/instructions.json"
        const val WasmPath = "/web13a/control.wasm"
        const val ServiceWorkerPath = "/web13a/sw.js"
        const val ServiceWorkerSyntheticPath = "/web13a/sw-synthetic.png"
        const val CacheStoragePath = "/web13a/cache-only.png"
        const val ReportPath = "/web13a/report"
        const val StatePath = "/web13a/state"
        const val HtmlContentType = "text/html; charset=utf-8"
        const val TextContentType = "text/plain; charset=utf-8"
        const val JavaScriptContentType = "application/javascript; charset=utf-8"
        const val JsonContentType = "application/json; charset=utf-8"
        const val WasmContentType = "application/wasm"
        const val MaximumReportBytes = 2048
        const val SafeReportPunctuation = "_:-,"
        const val NotRun = "not_run"
        const val InvalidReport = "invalid"
        const val JsonBody = "{\"background\":\"#dc1430\",\"stripe\":\"#000000\"}"
        const val SentinelPngBase64 =
            "iVBORw0KGgoAAAANSUhEUgAAAUAAAAC0CAIAAABqhmJGAAACOklEQVR4nO3TQQ2EABAEweM0IAEB+FeBACQgAhOETYcqA5N59PIbcq77yO52HSO7/r7ja3//I6vAIwQMYQKGMAFDmIAhTMAQJmAIEzCECRjCBAxhAoYwAUOYgCFMwBAmYAgTMIQJGMIEDGEChjABQ5iAIUzAECZgCBMwhAkYwgQMYQKGMAFDmIAhTMAQJmAIEzCECRjCBAxhAoYwAUOYgCFMwBAmYAgTMIQJGMIEDGEChjABQ5iAIUzAECZgCBMwhAkYwgQMYQKGMAFDmIAhTMAQJmAIEzCECRjCBAxhAoYwAUOYgCFMwBAmYAgTMIQJGMIEDGEChjABQ5iAIUzAECZgCBMwhAkYwgQMYQKGMAFDmIAhTMAQJmAIEzCECRjCBAxhAoYwAUOYgCFMwBAmYAgTMIQJGMIEDGEChjABQ5iAIUzAECZgCBMwhAkYwgQMYQKGMAFDmIAhTMAQJmAIEzCECRjCBAxhAoYwAUOYgCFMwBAmYAgTMIQJGMIEDGEChjABQ5iAIUzAECZgCBMwhAkYwgQMYQKGMAFDmIAhTMAQJmAIEzCECRjCBAxhAoYwAUOYgCFMwBAmYAgTMIQJGMIEDGEChjABQ5iAIUzAECZgCBMwhAkYwgQMYQKGMAFDmIAhTMAQJmAIEzCECRjCBAxhAoYwAUOYgCFMwBAmYAgTMIQJGMIEDGEChjABQ5iAIUzAECZgCBMwhAkYwgQMYQKGMAFDmIAhTMAQJmAIEzCECRjCBAxhAoYwAUOYgCFMwBAmYAgTMIQJGMIEDGEChjABQ5iAIUzAECZgCBMwhAkYwgQMYQKGMAFDmIAhTMAQJmAIEzCECRjCBAxhAoYwAUOYgCFMwBAmYAgTMIQJGMIEDGEChjABQ5iAIUzAECZgCBMwhAkYwgQMYQKGMAFDmIAhTMAQJmAIEzCECRjCBAxhAoYwAUOYgCFMwBAmYAgTMIQJGMIEDGEChjABQ5iAIUzAECZgCBMwhAkYwgQMYQKGMAFDmIAhTMAQJmAIEzCECRjCBAxhAoawG9j7C4i/UFSBAAAAAElFTkSuQmCC"
        val NoStoreHeaders =
            listOf(
                ChromeHttpHeader("Cache-Control", "no-store, max-age=0"),
                ChromeHttpHeader("Pragma", "no-cache"),
            )
        val EmptyWasmModule = byteArrayOf(0x00, 0x61, 0x73, 0x6d, 0x01, 0x00, 0x00, 0x00)
    }
}
