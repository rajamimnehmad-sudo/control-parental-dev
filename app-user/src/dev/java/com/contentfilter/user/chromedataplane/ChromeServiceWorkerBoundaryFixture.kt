package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/** DEV-only adversarial fixture. Its observations never grant document or presentation authority. */
internal class ChromeServiceWorkerBoundaryFixture {
    private val installDocuments = AtomicLong()
    private val workerScripts = AtomicLong()
    private val workerProbeScripts = AtomicLong()
    private val probeDocuments = AtomicLong()
    private val cleanupDocuments = AtomicLong()
    private val reportsAccepted = AtomicLong()
    private val reportsRejected = AtomicLong()
    private val controllerPresent = AtomicLong()
    private val controllerMissing = AtomicLong()
    private val navigationFetches = AtomicLong()
    private val selfReadyFetches = AtomicLong()
    private val passThroughSelfReady = AtomicLong()
    private val syntheticSelfReady = AtomicLong()
    private val syntheticNavigation = AtomicLong()
    private val resetBaselineClean = AtomicLong()
    private val resetBaselineDirty = AtomicLong()
    private val registerBlocked = AtomicLong()
    private val registerSucceeded = AtomicLong()
    private val workerRegisterBlocked = AtomicLong()
    private val workerRegisterSucceeded = AtomicLong()
    private val workerRegisterUnsupported = AtomicLong()
    private val workerRegisterError = AtomicLong()
    private val lastCase = AtomicReference(None)
    private val lastEvent = AtomicReference(None)
    private val lastValue = AtomicReference(None)

    fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse? {
        val path = request.target.substringBefore('?').substringBefore('#')
        return when (path) {
            InstallPath -> counted(installDocuments) { html("h20-sw-install", installDocument()) }
            WorkerPath ->
                if (ChromeServiceWorkerScriptGate.blocks(request)) {
                    response("h20-sw-worker-blocked", PlainText, ByteArray(0), 403)
                } else {
                    counted(workerScripts) {
                        response(
                            id = "h20-sw-worker",
                            contentType = JavaScript,
                            bytes = workerScript().toByteArray(),
                            headers = listOf(ChromeHttpHeader("Service-Worker-Allowed", "/")),
                        )
                    }
                }
            WorkerRegisterProbePath ->
                counted(workerProbeScripts) {
                    response(
                        id = "h20-sw-worker-register-probe",
                        contentType = JavaScript,
                        bytes = workerRegisterProbeScript().toByteArray(),
                    )
                }
            ProbePath -> counted(probeDocuments) { html("h20-sw-probe", probeDocument()) }
            FirstOriginalScriptPath ->
                response(
                    id = "h20-sw-first-original",
                    contentType = JavaScript,
                    bytes = ChromeMediaShieldBootstrap.selfShieldOriginalScriptStartedScript().toByteArray(),
                )
            CleanupPath -> counted(cleanupDocuments) { html("h20-sw-cleanup", cleanupDocument()) }
            EventPath -> acceptEvent(request)
            StatePath -> response("h20-sw-state", PlainText, state().toByteArray())
            else -> null
        }
    }

    fun state(): String =
        "INSTALL_DOCS=${installDocuments.get()},WORKER_SCRIPTS=${workerScripts.get()}," +
            "WORKER_PROBE_SCRIPTS=${workerProbeScripts.get()},PROBE_DOCS=${probeDocuments.get()}," +
            "CLEANUP_DOCS=${cleanupDocuments.get()},REPORTS_ACCEPTED=${reportsAccepted.get()}," +
            "REPORTS_REJECTED=${reportsRejected.get()},CONTROLLER_PRESENT=${controllerPresent.get()}," +
            "CONTROLLER_MISSING=${controllerMissing.get()},NAV_FETCHES=${navigationFetches.get()}," +
            "SELF_READY_FETCHES=${selfReadyFetches.get()},SELF_READY_PASS_THROUGH=${passThroughSelfReady.get()}," +
            "SELF_READY_SYNTHETIC=${syntheticSelfReady.get()},NAV_SYNTHETIC=${syntheticNavigation.get()}," +
            "RESET_BASELINE_CLEAN=${resetBaselineClean.get()},RESET_BASELINE_DIRTY=${resetBaselineDirty.get()}," +
            "REGISTER_BLOCKED=${registerBlocked.get()},REGISTER_SUCCEEDED=${registerSucceeded.get()}," +
            "WORKER_REGISTER_BLOCKED=${workerRegisterBlocked.get()}," +
            "WORKER_REGISTER_SUCCEEDED=${workerRegisterSucceeded.get()}," +
            "WORKER_REGISTER_UNSUPPORTED=${workerRegisterUnsupported.get()}," +
            "WORKER_REGISTER_ERROR=${workerRegisterError.get()},LAST_CASE=${lastCase.get()}," +
            "LAST_EVENT=${lastEvent.get()},LAST_VALUE=${lastValue.get()}"

    internal fun workerScript(): String =
        """
        'use strict';
        const CACHE='$ModeCache',MODE_KEY='$ModeKey',REPORT='$EventPath',SELF_READY='${ChromePhotosDataPlaneLabContract.MediaShieldSelfReadyPath}';
        const VALID=new Set(['PASS_THROUGH','SYNTHETIC_SELF_READY','SYNTHETIC_NAVIGATION']);
        const report=(caseId,event,value)=>fetch(REPORT,{method:'POST',cache:'no-store',credentials:'same-origin',headers:{'Content-Type':'text/plain'},body:'v1|'+caseId+'|'+event+'|'+value}).catch(()=>{});
        const writeMode=async mode=>{const cache=await caches.open(CACHE);await cache.put(MODE_KEY,new Response(mode,{headers:{'Cache-Control':'no-store'}}));return mode};
        const readMode=async()=>{const cache=await caches.open(CACHE),response=await cache.match(MODE_KEY);return response?response.text():'PASS_THROUGH'};
        self.addEventListener('install',event=>event.waitUntil(self.skipWaiting()));
        self.addEventListener('activate',event=>event.waitUntil((async()=>{await self.clients.claim();await report('PROVISION','ACTIVATED','YES')})()));
        self.addEventListener('fetch',event=>{
          const url=new URL(event.request.url);if(url.origin!==self.location.origin||url.pathname===REPORT)return;
          if(event.request.mode==='navigate'&&url.pathname==='$ProbePath'){
            event.respondWith((async()=>{const requested=url.searchParams.get('sw_case'),caseId=VALID.has(requested)?requested:'PASS_THROUGH';await writeMode(caseId);
            await report(caseId,'FETCH_NAVIGATION',caseId==='SYNTHETIC_NAVIGATION'?'SYNTHETIC':'PASSTHROUGH');
            if(caseId==='SYNTHETIC_NAVIGATION')return new Response(`$SyntheticNavigationDocument`,{status:200,headers:{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store'}});
            return fetch(event.request)})());return}
          if(url.pathname===SELF_READY){
            event.respondWith((async()=>{const caseId=await readMode(),synthetic=caseId==='SYNTHETIC_SELF_READY';await report(caseId,'FETCH_SELF_READY',synthetic?'SYNTHETIC':'PASSTHROUGH');
            return synthetic?new Response(null,{status:204,headers:{'Cache-Control':'no-store'}}):fetch(event.request)})());return}
        });
        """.trimIndent().replace("\n", "")

    internal fun workerRegisterProbeScript(): String =
        """
        'use strict';
        self.onmessage=async()=>{let result='UNSUPPORTED';try{const container=navigator.serviceWorker;
        if(container&&typeof container.register==='function'){try{await container.register('$WorkerPath',{scope:'/',updateViaCache:'none'});result='SUCCEEDED'}catch(_){result='BLOCKED'}}}
        catch(_){result='ERROR'}self.postMessage(result)};
        """.trimIndent().replace("\n", "")

    internal fun canonicalEventOrNull(bytes: ByteArray): String? {
        if (bytes.isEmpty() || bytes.size > MaximumEventBytes || bytes.any { it.toInt() !in PrintableAscii }) return null
        val parts = bytes.toString(Charsets.US_ASCII).split('|')
        if (parts.size != 4 || parts[0] != EventVersion) return null
        val caseId = parts[1]
        val event = parts[2]
        val value = parts[3]
        if (caseId !in EventCases || event !in EventTypes || value !in EventValues.getValue(event)) return null
        return "$caseId|$event|$value"
    }

    private fun acceptEvent(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse {
        if (request.method != "POST") return response("h20-sw-event-method", PlainText, ByteArray(0), 405)
        val canonical = canonicalEventOrNull(request.body)
        request.body.fill(0)
        if (canonical == null) {
            reportsRejected.incrementAndGet()
            return response("h20-sw-event-rejected", PlainText, "rejected".toByteArray(), 400)
        }
        val (caseId, event, value) = canonical.split('|')
        lastCase.set(caseId)
        lastEvent.set(event)
        lastValue.set(value)
        reportsAccepted.incrementAndGet()
        when (event) {
            "CLIENT_CONTROLLER" -> if (value == "YES") controllerPresent.incrementAndGet() else controllerMissing.incrementAndGet()
            "FETCH_NAVIGATION" -> {
                navigationFetches.incrementAndGet()
                if (value == "SYNTHETIC") syntheticNavigation.incrementAndGet()
            }
            "FETCH_SELF_READY" -> {
                selfReadyFetches.incrementAndGet()
                if (value == "SYNTHETIC") syntheticSelfReady.incrementAndGet() else passThroughSelfReady.incrementAndGet()
            }
            "RESET_BASELINE" -> if (value == "CLEAN") resetBaselineClean.incrementAndGet() else resetBaselineDirty.incrementAndGet()
            "REGISTER_RESULT" -> if (value == "BLOCKED") registerBlocked.incrementAndGet() else registerSucceeded.incrementAndGet()
            "WORKER_REGISTER_RESULT" ->
                when (value) {
                    "BLOCKED" -> workerRegisterBlocked.incrementAndGet()
                    "SUCCEEDED" -> workerRegisterSucceeded.incrementAndGet()
                    "UNSUPPORTED" -> workerRegisterUnsupported.incrementAndGet()
                    else -> workerRegisterError.incrementAndGet()
                }
        }
        return response("h20-sw-event", PlainText, ByteArray(0), 204)
    }

    private fun installDocument(): String =
        """
        <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>H20 SW INSTALL</title></head>
        <body><h1 id="status">SW_INSTALLING</h1><script>
        (async()=>{const report=body=>fetch('$EventPath',{method:'POST',cache:'no-store',headers:{'Content-Type':'text/plain'},body});
        const existing=await navigator.serviceWorker.getRegistrations(),controlledBefore=!!navigator.serviceWorker.controller,clean=existing.length===0&&!controlledBefore;
        await report('v1|RESET_VERIFY|RESET_BASELINE|'+(clean?'CLEAN':'DIRTY'));
        let windowResult='BLOCKED',controlled=false;try{const registration=await navigator.serviceWorker.register('$WorkerPath',{scope:'/',updateViaCache:'none'});windowResult='SUCCEEDED';await navigator.serviceWorker.ready;
        if(!navigator.serviceWorker.controller)await new Promise(resolve=>navigator.serviceWorker.addEventListener('controllerchange',resolve,{once:true}));controlled=!!navigator.serviceWorker.controller;
        await report('v1|PROVISION|CLIENT_CONTROLLER|'+(controlled?'YES':'NO'))}catch(_){}await report('v1|RESET_VERIFY|REGISTER_RESULT|'+windowResult);
        const workerResult=await new Promise(resolve=>{let settled=false,worker=null,timer=0;const finish=value=>{if(settled)return;settled=true;if(timer)clearTimeout(timer);try{if(worker)worker.terminate()}catch(_){}resolve(value)};
        try{worker=new Worker('$WorkerRegisterProbePath');worker.onmessage=event=>finish(['BLOCKED','SUCCEEDED','UNSUPPORTED','ERROR'].includes(event.data)?event.data:'ERROR');worker.onerror=()=>finish('ERROR');worker.postMessage('REGISTER');timer=setTimeout(()=>finish('ERROR'),4000)}
        catch(_){finish('ERROR')}});
        await report('v1|RESET_VERIFY|WORKER_REGISTER_RESULT|'+workerResult);
        const prefix=windowResult==='BLOCKED'?'SW_REGISTER_BLOCKED':(controlled?'SW_CONTROLLER=YES':'SW_CONTROLLER=NO');document.getElementById('status').textContent=prefix+' WORKER_REGISTER='+workerResult})();
        </script></body></html>
        """.trimIndent()

    private fun probeDocument(): String =
        """
        <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <script src="$FirstOriginalScriptPath"></script><title>H20 SW PROBE</title></head><body><h1>H20 SW BOUNDARY PROBE</h1><p id="controller">pending</p>
        <script>(async()=>{const controlled=!!navigator.serviceWorker.controller;document.getElementById('controller').textContent=controlled?'SW_CONTROLLER=YES':'SW_CONTROLLER=NO';
        try{await fetch('$EventPath',{method:'POST',cache:'no-store',headers:{'Content-Type':'text/plain'},body:'v1|'+(new URL(location.href).searchParams.get('sw_case')||'PASS_THROUGH')+'|CLIENT_CONTROLLER|'+(controlled?'YES':'NO')})}catch(_){}})();</script>
        </body></html>
        """.trimIndent()

    private fun cleanupDocument(): String =
        """
        <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>H20 SW CLEANUP</title></head>
        <body><h1 id="status">SW_CLEANUP</h1><script>(async()=>{let clean=true;try{for(const registration of await navigator.serviceWorker.getRegistrations())clean=(await registration.unregister())&&clean;
        clean=(await caches.delete('$ModeCache'))&&clean}catch(_){clean=false}document.getElementById('status').textContent=clean?'SW_CLEANUP=YES':'SW_CLEANUP=NO'})();</script></body></html>
        """.trimIndent()

    private fun html(
        id: String,
        source: String,
    ) = response(id, Html, source.toByteArray())

    private fun response(
        id: String,
        contentType: String,
        bytes: ByteArray,
        statusCode: Int = 200,
        headers: List<ChromeHttpHeader> = emptyList(),
    ) = ChromePhotosFixtureResponse(
        resourceId = id,
        contentType = contentType,
        originalBytes = bytes,
        headers = listOf(ChromeHttpHeader("Cache-Control", "no-store")) + headers,
        statusCode = statusCode,
        statusText =
            when (statusCode) {
                200 -> "OK"
                204 -> "No Content"
                400 -> "Bad Request"
                403 -> "Forbidden"
                else -> "Method Not Allowed"
            },
    )

    private fun <T> counted(
        counter: AtomicLong,
        block: () -> T,
    ): T {
        counter.incrementAndGet()
        return block()
    }

    private companion object {
        const val InstallPath = "/web20sw/install"
        const val WorkerPath = "/web20sw/sw.js"
        const val WorkerRegisterProbePath = "/web20sw/worker-register-probe.js"
        const val ProbePath = "/web20sw/probe"
        const val CleanupPath = "/web20sw/cleanup"
        const val FirstOriginalScriptPath = "/web20sw/first-original.js"
        const val EventPath = "/web20sw/event"
        const val StatePath = "/web20sw/state"
        const val ModeCache = "glosh-h20-sw-boundary-v1"
        const val ModeKey = "https://glosh-photos.test/web20sw/mode-state"
        const val EventVersion = "v1"
        const val None = "none"
        const val Html = "text/html; charset=utf-8"
        const val JavaScript = "application/javascript; charset=utf-8"
        const val PlainText = "text/plain; charset=utf-8"
        const val MaximumEventBytes = 128
        val PrintableAscii = 0x20..0x7e
        val EventCases = setOf("PROVISION", "RESET_VERIFY", "PASS_THROUGH", "SYNTHETIC_SELF_READY", "SYNTHETIC_NAVIGATION")
        val EventTypes =
            setOf(
                "ACTIVATED",
                "CLIENT_CONTROLLER",
                "FETCH_NAVIGATION",
                "FETCH_SELF_READY",
                "RESET_BASELINE",
                "REGISTER_RESULT",
                "WORKER_REGISTER_RESULT",
            )
        val EventValues =
            mapOf(
                "ACTIVATED" to setOf("YES"),
                "CLIENT_CONTROLLER" to setOf("YES", "NO"),
                "FETCH_NAVIGATION" to setOf("PASSTHROUGH", "SYNTHETIC"),
                "FETCH_SELF_READY" to setOf("PASSTHROUGH", "SYNTHETIC"),
                "RESET_BASELINE" to setOf("CLEAN", "DIRTY"),
                "REGISTER_RESULT" to setOf("BLOCKED", "SUCCEEDED"),
                "WORKER_REGISTER_RESULT" to setOf("BLOCKED", "SUCCEEDED", "UNSUPPORTED", "ERROR"),
            )
        val SyntheticNavigationDocument =
            "<!doctype html><html><head><meta charset=utf-8><meta name=viewport content='width=device-width,initial-scale=1'>" +
                "<title>SW SYNTHETIC NAVIGATION</title><style>html,body{margin:0;background:#fff}#sentinel{width:100vw;height:80vh;" +
                "background:repeating-linear-gradient(45deg,#dc143c 0 24px,#000 24px 48px);color:#fff;font:700 32px sans-serif;" +
                "display:grid;place-items:center}</style></head><body><div id=sentinel>SW SYNTHETIC DOCUMENT SENTINEL</div>" +
                "<p id=controller>pending</p><script>(async()=>{const c=!!navigator.serviceWorker.controller;document.getElementById('controller').textContent=" +
                "c?'SW_CONTROLLER=YES':'SW_CONTROLLER=NO';try{await fetch('$EventPath',{method:'POST',cache:'no-store',headers:{'Content-Type':'text/plain'}," +
                "body:'v1|SYNTHETIC_NAVIGATION|CLIENT_CONTROLLER|'+(c?'YES':'NO')})}catch(_){}})();</script></body></html>"
    }
}
