package com.contentfilter.user.chromedataplane

import java.util.Base64
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal enum class ChromeStockMediaScenarioCategory { Network, Local, Normality, OutOfScope }

internal data class ChromeStockMediaScenario(
    val id: String,
    val category: ChromeStockMediaScenarioCategory,
)

/** Controlled H19 matrix. Observations are evidence only and never grant presentation authority. */
internal class ChromeStockMediaAuthorityFixture(
    private val localSentinelBytes: ByteArray,
) {
    private val documents = AtomicLong()
    private val frames = AtomicLong()
    private val scripts = AtomicLong()
    private val styles = AtomicLong()
    private val workers = AtomicLong()
    private val serviceWorkers = AtomicLong()
    private val sameUrlBodies = AtomicLong()
    private val reports = AtomicLong()
    private val frameReports = AtomicLong()
    private val frameReportRejects = AtomicLong()
    private val frameGenerations = AtomicLong()
    private val report = AtomicReference(NotRun)
    private val frameReport = AtomicReference(NotRun)
    private val frameReportSha256 = AtomicReference(NotRun)
    private val frameReportBindingSha256 = AtomicReference(NotRun)
    private val frameAcceptedChallengeSha256 = AtomicReference(NotRun)
    private val frameChallenge = AtomicReference("")
    private val frameChallengeSha256 = AtomicReference(NotRun)

    internal val scenarios: List<ChromeStockMediaScenario> = Scenarios

    fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse? {
        val path = request.target.substringBefore('?').substringBefore('#')
        return when (path) {
            ControlledPath -> counted(documents) { html("h19-controlled", controlledDocument()) }
            FramePath -> counted(frames) { html("h19-frame", frameDocument()) }
            ScriptPath -> counted(scripts) { response("h19-script", JavaScript, siteScript().toByteArray()) }
            StylePath -> counted(styles) { response("h19-style", Css, siteStyle().toByteArray()) }
            WorkerPath -> counted(workers) { response("h19-worker", JavaScript, WorkerScript.toByteArray()) }
            ServiceWorkerPath ->
                counted(
                    serviceWorkers,
                ) { response("h19-service-worker", JavaScript, ServiceWorkerScript.toByteArray()) }
            ExternalSvgPath -> response("h19-external-svg", "image/svg+xml", ExternalSvg.toByteArray())
            MislabeledPath -> response("h19-mislabeled", "text/plain", localSentinelBytes)
            OctetPath -> response("h19-octet", "application/octet-stream", localSentinelBytes)
            SameUrlPath -> sameUrlResponse()
            SameBodyPath -> response("h19-same-body", "image/png", localSentinelBytes)
            FrameReportPath -> acceptFrameReport(request)
            ReportPath -> acceptReport(request)
            StatePath -> response("h19-state", PlainText, state().toByteArray())
            else -> null
        }
    }

    fun state(): String =
        "REPORT=${report.get()},DOCUMENTS=${documents.get()},FRAMES=${frames.get()}," +
            "SCRIPTS=${scripts.get()},STYLES=${styles.get()},WORKERS=${workers.get()}," +
            "SERVICE_WORKERS=${serviceWorkers.get()},FRAME_REPORTS=${frameReports.get()}," +
            "FRAME_REPORT_REJECTS=${frameReportRejects.get()},FRAME_REPORT=${frameReport.get()}," +
            "FRAME_REPORT_SHA=${frameReportSha256.get()},FRAME_CHALLENGE_SHA=${frameChallengeSha256.get()}," +
            "FRAME_GENERATION=${frameGenerations.get()}," +
            "FRAME_ACCEPTED_CHALLENGE_SHA=${frameAcceptedChallengeSha256.get()}," +
            "FRAME_REPORT_BINDING_SHA=${frameReportBindingSha256.get()}," +
            "SAME_URL_BODIES=${sameUrlBodies.get()},REPORTS=${reports.get()}"

    internal fun controlledDocument(): String =
        """
        <!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>GLOSH H19 CONTROLLED</title><link rel="stylesheet" href="$StylePath"></head><body><h1>CHROME STOCK MEDIA AUTHORITY 19</h1>
        <section id="network"><h2>Network authority</h2>
        <img id="safe-png" src="${ChromePhotosRealWebLabConfig.SafePngUrl}"><img id="block-webp" src="${ChromePhotosRealWebLabConfig.BlockWebpUrl}"><img id="block-jpeg" src="${ChromeVisualShieldFixtureSample.Block.sourceUrl}">
        <img id="jpeg-candidate" src="${ChromePhotosRealWebLabConfig.UnknownJpegUrl}"><img id="webp-candidate" src="${ChromePhotosRealWebLabConfig.UnknownWebpUrl}"><img id="avif-candidate" src="${ChromePhotosRealWebLabConfig.UnknownAvifUrl}">
        <picture><source srcset="${ChromePhotosRealWebLabConfig.SafePngUrl}"><img id="picture-srcset" src="${ChromePhotosRealWebLabConfig.SafePngUrl}"></picture><div id="css-url" class="css-network"></div>
        <img id="mislabeled" src="$MislabeledPath"><img id="octet-magic" src="$OctetPath"><img id="same-url-first" src="$SameUrlPath?revision=1"><img id="same-url-second" src="$SameUrlPath?revision=2">
        <img id="same-body-first" src="$SameBodyPath?request=1"><img id="same-body-second" src="$SameBodyPath?request=2"><img id="dynamic-replace" src="${ChromePhotosRealWebLabConfig.SafePngUrl}"><img id="lazy-network" loading="lazy" src="${ChromePhotosRealWebLabConfig.SafePngUrl}"><div id="network-shadow"></div></section>
        <section id="local"><h2>Local media fail close</h2><img id="data-image" src="data:image/png;base64,${encodedSentinel()}"><img id="blob-image"><img id="spa-data-replace"><div id="css-data"></div><div id="css-blob"></div>
        <canvas id="canvas-2d" width="128" height="96"></canvas><canvas id="canvas-webgl" width="128" height="96"></canvas><canvas id="canvas-webgpu" width="128" height="96"></canvas>
        <svg id="svg-image" width="160" height="90"><image href="data:image/png;base64,${encodedSentinel()}" width="160" height="90"/></svg><svg id="svg-foreign" width="160" height="90"><foreignObject width="160" height="90"><div xmlns="http://www.w3.org/1999/xhtml" class="sentinel"></div></foreignObject></svg>
        <svg id="svg-feimage" width="160" height="90"><filter id="f"><feImage href="data:image/png;base64,${encodedSentinel()}"/></filter><rect width="160" height="90" filter="url(#f)"/></svg><svg id="icon-safe" width="24" height="24"><path d="M2 12L12 2l10 10-10 10z"/></svg><svg id="svg-media" width="320" height="180"><rect width="320" height="180" fill="#dc1430"/></svg><img id="external-svg" src="$ExternalSvgPath">
        <div id="open-shadow"></div><div id="closed-shadow"></div><iframe id="network-frame" src="$FramePath"></iframe><iframe id="srcdoc-frame" srcdoc="<canvas width=80 height=80></canvas>"></iframe></section>
        <section id="normal"><form id="normal-form"><input value="normal"></form><div id="normal-layout"><span>CSS</span><span>JS</span><span>XHR</span></div><div id="normal-shadow"></div><p id="normal-js">pending</p></section>
        <section id="out-of-scope"><div id="css-synthesis" class="css-raster">${rasterCells()}</div></section><script src="$ScriptPath"></script></body></html>
        """.trimIndent()

    internal fun siteStyle(): String =
        """
        :root{font-family:sans-serif;background:#eef2f5;color:#14202a}body{margin:0;padding:12px}section{background:white;margin:8px 0;padding:8px}
        img,.css-network,canvas,svg,iframe{display:block;width:240px;min-height:80px;margin:6px 0;background:#374151}.css-network{background-image:url('${ChromePhotosRealWebLabConfig.BlockWebpUrl}');background-size:cover}
        #css-data{background-image:url('data:image/png;base64,${encodedSentinel()}')}#normal-layout{display:grid;grid-template-columns:repeat(3,1fr);gap:4px}.sentinel{width:160px;height:90px;background:#dc1430}
        #icon-safe{zoom:20!important;scale:20!important;width:100vw!important;height:100vh!important}#icon-safe path{d:path('M0 0H96V96H0Z')!important;transform:scale(20)!important}
        .css-raster{display:grid;grid-template-columns:repeat(8,12px);width:96px}.css-raster span{width:12px;height:12px;background:#dc1430}.css-raster span:nth-child(2n){background:#000}
        """.trimIndent().replace("\n", "")

    internal fun siteScript(): String {
        val ids = scenarios.joinToString(",") { "'${it.id}'" }
        val encoded = encodedSentinel()
        // SAFE is a completed browser-level outcome, not proof that a network response was raw.
        // Byte-gate counters/digests and the physical sentinel recording provide that separate evidence.
        return """
            (async()=>{'use strict';
            const expected=[$ids],allowed=new Set(['BLOCKED','HIDDEN','SAFE','OUT_OF_SCOPE_VISIBLE','ERROR']),results=new Map(),tasks=[];
            const Apply=Reflect.apply,Define=Object.defineProperty,Describe=Object.getOwnPropertyDescriptor,Delete=Reflect.deleteProperty,GetComputedStyle=getComputedStyle,SetTimer=setTimeout,ClearTimer=clearTimeout;
            const ElementGet=Element.prototype.getAttribute,ElementAppend=Element.prototype.append,ElementRemove=Element.prototype.remove,AddEvent=EventTarget.prototype.addEventListener;
            const LocalName=Describe(Element.prototype,'localName'),NaturalWidth=Describe(HTMLImageElement.prototype,'naturalWidth'),NaturalHeight=Describe(HTMLImageElement.prototype,'naturalHeight');
            const call=(fn,owner,args)=>Apply(fn,owner,args),read=(descriptor,owner)=>descriptor&&descriptor.get?call(descriptor.get,owner,[]):undefined;
            const record=(id,value)=>{if(expected.indexOf(id)>=0&&!results.has(id))results.set(id,allowed.has(value)?value:'ERROR')};
            const run=(id,probe)=>tasks.push((async()=>{try{record(id,await probe())}catch(_){record(id,'ERROR')}})());
            const frame=()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
            const hidden=(element)=>{if(!element)return false;const shield=call(ElementGet,element,['data-glosh-media-blocked'])==='1',style=GetComputedStyle(element);
            return shield&&(style.display==='none'||style.visibility==='hidden'||style.opacity==='0')};
            const visible=(element)=>{if(!element||hidden(element))return false;const style=GetComputedStyle(element),rect=element.getBoundingClientRect();
            return style.display!=='none'&&style.visibility!=='hidden'&&style.opacity!=='0'&&rect.width>0&&rect.height>0};
            const awaitImage=async(image)=>{if(!image)return false;if(image.complete)return read(NaturalWidth,image)>0&&read(NaturalHeight,image)>0;
            return new Promise(resolve=>{let done=false,timer=0;const finish=value=>{if(done)return;done=true;ClearTimer(timer);resolve(value)};
            call(AddEvent,image,['load',()=>finish(true),{once:true}]);call(AddEvent,image,['error',()=>finish(false),{once:true}]);timer=SetTimer(()=>finish(false),4000);
            if(image.complete)finish(read(NaturalWidth,image)>0&&read(NaturalHeight,image)>0)})};
            const networkImage=async(id)=>{const image=document.getElementById(id),loaded=await awaitImage(image);await frame();return loaded&&visible(image)?'SAFE':'ERROR'};
            const localImage=async(element,operation)=>{let rejected=false;try{await operation()}catch(_){rejected=true}await frame();if(hidden(element))return 'HIDDEN';return rejected?'BLOCKED':'ERROR'};
            const localSink=async(element,operation)=>{let rejected=false,value;try{value=await operation()}catch(_){rejected=true}await frame();if(element&&hidden(element))return 'HIDDEN';return rejected||value===null?'BLOCKED':'ERROR'};
            const svgHidden=async(id)=>{const element=document.getElementById(id);await frame();return hidden(element)&&element.childElementCount===0?'HIDDEN':'ERROR'};
            const bytes=Uint8Array.from(atob('$encoded'),character=>character.charCodeAt(0));

            for(const pair of [['network-safe-png','safe-png'],['network-block-webp','block-webp'],['network-block-jpeg','block-jpeg'],
            ['network-jpeg-candidate','jpeg-candidate'],['network-webp-candidate','webp-candidate'],['network-avif-candidate','avif-candidate'],
            ['network-picture-srcset','picture-srcset'],['network-mislabeled','mislabeled'],['network-octet-magic','octet-magic'],
            ['network-same-url-first','same-url-first'],['network-same-url-second','same-url-second'],['network-same-body-first','same-body-first'],
            ['network-same-body-second','same-body-second']])run(pair[0],()=>networkImage(pair[1]));
            run('network-lazy',async()=>{const image=document.getElementById('lazy-network');image.scrollIntoView({block:'center'});const loaded=await awaitImage(image);await frame();return loaded&&visible(image)?'SAFE':'ERROR'});
            run('network-css-url',async()=>{const probe=new Image();probe.src='${ChromePhotosRealWebLabConfig.BlockWebpUrl}';const loaded=await awaitImage(probe);await frame();return loaded&&visible(document.getElementById('css-url'))?'SAFE':'ERROR'});
            run('network-dynamic-replace',async()=>{const image=document.getElementById('dynamic-replace');image.src='${ChromePhotosRealWebLabConfig.BlockWebpUrl}';const loaded=await awaitImage(image);await frame();return loaded&&visible(image)?'SAFE':'ERROR'});
            run('network-shadow',async()=>{const root=document.getElementById('network-shadow').attachShadow({mode:'open'}),image=document.createElement('img');image.src='${ChromePhotosRealWebLabConfig.SafePngUrl}';root.append(image);const loaded=await awaitImage(image);await frame();return loaded&&visible(image)?'SAFE':'ERROR'});

            run('local-data-img',()=>localImage(document.getElementById('data-image'),()=>document.getElementById('data-image').decode()));
            run('local-blob-img',()=>localImage(document.getElementById('blob-image'),()=>{const image=document.getElementById('blob-image'),url=URL.createObjectURL(new Blob([bytes],{type:'image/png'}));image.src=url;return image.decode().finally(()=>URL.revokeObjectURL(url))}));
            run('local-css-data',async()=>{const element=document.getElementById('css-data');element.style.backgroundImage="url('data:image/png;base64,$encoded')";await frame();return hidden(element)?'HIDDEN':'ERROR'});
            run('local-css-blob',async()=>{const element=document.getElementById('css-blob');let rejected=false,url='';try{url=URL.createObjectURL(new Blob([bytes],{type:'image/png'}));element.style.backgroundImage='url('+url+')'}catch(_){rejected=true}
            await frame();if(url)try{URL.revokeObjectURL(url)}catch(_){}if(hidden(element))return 'HIDDEN';return rejected?'BLOCKED':'ERROR'});
            run('local-canvas-2d',()=>localSink(document.getElementById('canvas-2d'),()=>{const context=document.getElementById('canvas-2d').getContext('2d');if(context){context.fillStyle='#dc1430';context.fillRect(0,0,128,96)}return context}));
            run('local-put-image-data',()=>localSink(document.getElementById('canvas-2d'),()=>{const context=document.getElementById('canvas-2d').getContext('2d');if(!context)return null;context.putImageData(new ImageData(1,1),0,0);return context}));
            run('local-canvas-draw-image',()=>localSink(document.getElementById('canvas-2d'),()=>{const context=document.getElementById('canvas-2d').getContext('2d');if(!context)return null;const image=new Image();image.src='data:image/png;base64,$encoded';context.drawImage(image,0,0);return context}));
            run('local-image-bitmap',async()=>{try{const bitmap=await createImageBitmap(new Blob([bytes]));if(bitmap&&bitmap.close)bitmap.close();return 'ERROR'}catch(_){return 'BLOCKED'}});
            run('local-offscreen-canvas',()=>localSink(null,()=>new OffscreenCanvas(16,16).getContext('2d')));
            run('local-webgl',()=>localSink(document.getElementById('canvas-webgl'),()=>document.getElementById('canvas-webgl').getContext('webgl')));
            run('local-webgpu',()=>localSink(document.getElementById('canvas-webgpu'),()=>document.getElementById('canvas-webgpu').getContext('webgpu')));
            for(const id of ['svg-image','svg-foreign','svg-feimage','svg-media'])run('local-'+(id==='svg-foreign'?'svg-foreign-object':id==='svg-feimage'?'svg-fe-image':id),()=>svgHidden(id));
            run('local-svg-external',()=>networkImage('external-svg'));
            run('local-svg-icon-safe',async()=>{const icon=document.getElementById('icon-safe');await frame();const rect=icon.getBoundingClientRect();return call(ElementGet,icon,['data-glosh-icon-safe'])==='1'&&visible(icon)&&rect.width<=96&&rect.height<=96?'SAFE':'ERROR'});
            for(const pair of [['local-shadow-open','open'],['local-shadow-closed','closed']])run(pair[0],async()=>{const root=document.getElementById(pair[1]+'-shadow').attachShadow({mode:pair[1]}),canvas=document.createElement('canvas');root.append(canvas);await frame();return hidden(canvas)?'HIDDEN':canvas.getContext('2d')===null?'BLOCKED':'ERROR'});
            run('local-iframe-transformed',async()=>{const iframe=document.getElementById('network-frame'),loaded=await new Promise(resolve=>{if(iframe.contentDocument&&iframe.contentDocument.readyState==='complete')return resolve(true);let done=false,timer=0;const finish=value=>{if(done)return;done=true;ClearTimer(timer);resolve(value)};call(AddEvent,iframe,['load',()=>finish(true),{once:true}]);timer=SetTimer(()=>finish(false),4000);
            if(iframe.contentDocument&&iframe.contentDocument.readyState==='complete')finish(true)});await frame();
            const sandbox=call(ElementGet,iframe,['sandbox'])||'';return loaded&&visible(iframe)&&sandbox.indexOf('allow-same-origin')<0&&sandbox.indexOf('allow-scripts')>=0?'SAFE':'ERROR'});
            run('local-iframe-srcdoc',async()=>{const iframe=document.getElementById('srcdoc-frame');await frame();return hidden(iframe)&&!iframe.hasAttribute('srcdoc')?'HIDDEN':'ERROR'});
            run('local-service-worker',async()=>{if(!navigator.serviceWorker)return 'BLOCKED';let before=[],after=[],escaped=false;
            try{before=await navigator.serviceWorker.getRegistrations();const registration=await navigator.serviceWorker.register('$ServiceWorkerPath');escaped=true;await registration.unregister();after=await navigator.serviceWorker.getRegistrations()}
            catch(_){try{after=await navigator.serviceWorker.getRegistrations()}catch(_){return 'BLOCKED'}}
            return !escaped&&before.length===0&&after.length===0&&navigator.serviceWorker.controller===null?'BLOCKED':'ERROR'});
            run('local-spa-data-replace',async()=>{history.pushState({},'',location.pathname+'#spa');const image=document.getElementById('spa-data-replace');image.src='data:image/png;base64,$encoded';await frame();return hidden(image)?'HIDDEN':'ERROR'});
            const prototypeProbe=async()=>{const originalString=Describe(String.prototype,'toLowerCase'),originalArray=Describe(Array.prototype,'0');let tampered=false,host=null,iframe=null,escaped=false;
            try{Define(String.prototype,'toLowerCase',{value:()=> 'div',writable:true,configurable:true});Define(Array.prototype,'0',{set:()=>{},configurable:true});tampered=true;host=document.createElement('div');
            host.innerHTML='<iframe data-glosh-network-frame="1" sandbox="allow-scripts allow-same-origin" src="about:blank"></iframe>';document.body.append(host);iframe=host.querySelector('iframe');await frame();
            const actual=iframe?read(LocalName,iframe):'';if(iframe&&actual!=='iframe')escaped=true;try{const realm=iframe&&iframe.contentWindow;if(realm&&realm.document){const canvas=realm.document.createElement('canvas');if(canvas.getContext('2d')!==null)escaped=true;
            if(realm.navigator.serviceWorker&&realm.navigator.serviceWorker.register)try{const registration=await realm.navigator.serviceWorker.register('$ServiceWorkerPath');escaped=true;await registration.unregister()}catch(_){}}}
            catch(_){} }catch(_){return 'BLOCKED'}finally{if(originalString)try{Define(String.prototype,'toLowerCase',originalString)}catch(_){}if(originalArray)try{Define(Array.prototype,'0',originalArray)}catch(_){}
            else try{Delete(Array.prototype,'0')}catch(_){} }
            await frame();const sandbox=iframe?call(ElementGet,iframe,['sandbox'])||'':'';const outcome=iframe&&hidden(iframe)&&sandbox.indexOf('allow-same-origin')<0&&!escaped?'HIDDEN':tampered&&!escaped?'BLOCKED':'ERROR';if(host)try{call(ElementRemove,host,[])}catch(_){}return outcome};

            run('normal-css',async()=>{await frame();return GetComputedStyle(document.body).fontFamily&&GetComputedStyle(document.body).backgroundColor!=='rgba(0, 0, 0, 0)'?'SAFE':'ERROR'});
            run('normal-layout',async()=>{await frame();return GetComputedStyle(document.getElementById('normal-layout')).display==='grid'?'SAFE':'ERROR'});
            run('normal-form',async()=>{await frame();const input=document.querySelector('#normal-form input');return input&&input.value==='normal'&&visible(input)?'SAFE':'ERROR'});
            run('normal-js',async()=>{document.getElementById('normal-js').textContent='SITE_JS_SAFE';await frame();return document.getElementById('normal-js').textContent==='SITE_JS_SAFE'?'SAFE':'ERROR'});
            run('normal-fetch',async()=>{try{const response=await fetch('$StatePath',{cache:'no-store'});const text=await response.text();return response.ok&&text.indexOf('REPORT=')>=0?'SAFE':'ERROR'}catch(_){return 'ERROR'}});
            run('normal-worker',()=>new Promise(resolve=>{let worker;try{worker=new Worker('$WorkerPath')}catch(_){resolve('ERROR');return}let done=false;const finish=value=>{if(done)return;done=true;ClearTimer(timer);worker.terminate();resolve(value)};worker.onmessage=event=>finish(event.data==='ready'?'SAFE':'ERROR');worker.onerror=()=>finish('ERROR');const timer=SetTimer(()=>finish('ERROR'),4000)}));
            run('normal-shadow',async()=>{const root=document.getElementById('normal-shadow').attachShadow({mode:'open'});root.innerHTML='<p>normal</p>';await frame();const paragraph=root.querySelector('p');return paragraph&&visible(paragraph)?'SAFE':'ERROR'});
            run('out-of-scope-css-synthesis',async()=>{const raster=document.getElementById('css-synthesis');await frame();return visible(raster)&&raster.querySelectorAll('span').length===64?'OUT_OF_SCOPE_VISIBLE':'ERROR'});

            await Promise.allSettled(tasks);try{record('local-hostile-prototype-fresh-frame',await prototypeProbe())}catch(_){record('local-hostile-prototype-fresh-frame','ERROR')}
            for(const id of expected)if(!results.has(id))results.set(id,'ERROR');
            const body=[...results].sort((a,b)=>a[0].localeCompare(b[0])).map(entry=>entry[0]+'='+entry[1]).join(',');
            try{await fetch('$ReportPath',{method:'POST',headers:{'Content-Type':'text/plain'},body})}catch(_){}})();
            """.trimIndent()
    }

    internal fun canonicalReportOrNull(bytes: ByteArray): String? {
        if (bytes.isEmpty() || bytes.size > MaximumReportBytes) return null
        val raw = bytes.toString(Charsets.US_ASCII)
        if (raw.any { it.code !in PrintableAscii }) return null
        val entries = raw.split(',')
        if (entries.size != scenarios.size) return null
        val values = linkedMapOf<String, String>()
        for (entry in entries) {
            val id = entry.substringBefore('=', "")
            val value = entry.substringAfter('=', "")
            if (id !in ScenarioIds || value !in AllowedValues || values.put(id, value) != null) return null
        }
        if (values.keys != ScenarioIds) return null
        return values.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }
    }

    private fun sameUrlResponse(): ChromePhotosFixtureResponse {
        val sequence = sameUrlBodies.incrementAndGet()
        val bytes = if (sequence % 2L == 1L) localSentinelBytes else localSentinelBytes + byteArrayOf(0)
        return response("h19-same-url-$sequence", "image/png", bytes)
    }

    private fun frameDocument(): String {
        val challenge = UUID.randomUUID().toString().replace("-", "")
        val scenarioIds = FrameScenarioIds.joinToString(prefix = "[", postfix = "]") { "'$it'" }
        frameGenerations.incrementAndGet()
        frameReport.set(NotRun)
        frameReportSha256.set(NotRun)
        frameReportBindingSha256.set(NotRun)
        frameAcceptedChallengeSha256.set(NotRun)
        frameChallenge.set(challenge)
        frameChallengeSha256.set(sha256(challenge.toByteArray(Charsets.US_ASCII)))
        return """
            <!doctype html><html><head><meta charset="utf-8"></head><body><script>
            (async()=>{'use strict';
            const CHALLENGE='$challenge',REPORT='$FrameReportPath',SENTINEL='${encodedSentinel()}';
            const results=new Map(),frame=()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));
            const hidden=element=>{if(!element)return false;const style=getComputedStyle(element);return element.getAttribute('data-glosh-media-blocked')==='1'&&
            (style.display==='none'||style.visibility==='hidden'||style.opacity==='0')};
            const record=(id,blocked)=>results.set(id,blocked?'BLOCKED':'ESCAPED');
            const probes=[];
            probes.push((async()=>{const image=document.createElement('img');let rejected=false;try{image.src='data:image/png;base64,'+SENTINEL;document.body.append(image);await frame()}catch(_){rejected=true}
            record('frame-data-img',rejected||hidden(image))})());
            probes.push((async()=>{const image=document.createElement('img');let rejected=false,url='';try{url=URL.createObjectURL(new Blob([Uint8Array.from(atob(SENTINEL),value=>value.charCodeAt(0))],{type:'image/png'}));
            image.src=url;document.body.append(image);await frame()}catch(_){rejected=true}finally{if(url)try{URL.revokeObjectURL(url)}catch(_){}}
            record('frame-blob-img',rejected||hidden(image))})());
            probes.push((async()=>{const canvas=document.createElement('canvas');let context=null,rejected=false;try{document.body.append(canvas);context=canvas.getContext('2d');if(context)context.fillRect(0,0,16,16);await frame()}catch(_){rejected=true}
            record('frame-canvas',rejected||context===null||hidden(canvas))})());
            probes.push((async()=>{if(!navigator.serviceWorker){record('frame-service-worker',true);return}let before=[],after=[],escaped=false,blocked=false;
            try{before=await navigator.serviceWorker.getRegistrations();const registration=await navigator.serviceWorker.register('$ServiceWorkerPath');escaped=true;await registration.unregister();after=await navigator.serviceWorker.getRegistrations();
            blocked=!escaped&&before.length===0&&after.length===0&&navigator.serviceWorker.controller===null}catch(_){blocked=true}record('frame-service-worker',blocked)})());
            probes.push((async()=>{const host=document.createElement('div');let blocked=false;try{const root=host.attachShadow({mode:'closed'}),canvas=document.createElement('canvas');root.append(canvas);document.body.append(host);await frame();blocked=canvas.getContext('2d')===null||hidden(canvas)}catch(_){blocked=true}
            record('frame-closed-shadow',blocked)})());
            await Promise.allSettled(probes);
            for(const id of $scenarioIds)if(!results.has(id))results.set(id,'ERROR');
            const canonical=[...results].sort((left,right)=>left[0].localeCompare(right[0])).map(entry=>entry[0]+'='+entry[1]).join(',');
            try{await fetch(REPORT,{method:'POST',mode:'no-cors',credentials:'omit',cache:'no-store',headers:{'Content-Type':'text/plain'},body:'v1|'+CHALLENGE+'|'+canonical})}catch(_){}})();
            </script></body></html>
            """.trimIndent()
    }

    private fun acceptFrameReport(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse {
        if (request.method != "POST") return methodNotAllowed("h19-frame-report-method")
        val parsed = parseFrameReport(request.body)
        val expectedChallenge = frameChallenge.get()
        if (
            parsed == null ||
            expectedChallenge.isEmpty() ||
            parsed.challenge != expectedChallenge ||
            !frameChallenge.compareAndSet(expectedChallenge, "")
        ) {
            frameReportRejects.incrementAndGet()
            return response("h19-frame-report-rejected", PlainText, "rejected".toByteArray(), 400)
        }
        frameReport.set(parsed.canonicalResults)
        frameReportSha256.set(sha256(parsed.canonicalResults.toByteArray(Charsets.US_ASCII)))
        frameAcceptedChallengeSha256.set(sha256(parsed.challenge.toByteArray(Charsets.US_ASCII)))
        frameReportBindingSha256.set(
            sha256("$FrameReportVersion|${parsed.challenge}|${parsed.canonicalResults}".toByteArray(Charsets.US_ASCII)),
        )
        frameReports.incrementAndGet()
        return response("h19-frame-report", PlainText, "accepted".toByteArray())
    }

    internal fun canonicalFrameReportOrNull(bytes: ByteArray): String? = parseFrameReport(bytes)?.canonicalResults

    private fun parseFrameReport(bytes: ByteArray): ParsedFrameReport? {
        if (bytes.isEmpty() || bytes.size > MaximumFrameReportBytes || bytes.any { it.toInt() !in PrintableAscii }) return null
        val raw = bytes.toString(Charsets.US_ASCII)
        val parts = raw.split('|', limit = 3)
        if (parts.size != 3 || parts[0] != FrameReportVersion || !parts[1].matches(FrameChallengePattern)) return null
        val entries = parts[2].split(',')
        if (entries.size != FrameScenarioIds.size) return null
        val values = linkedMapOf<String, String>()
        for (entry in entries) {
            val id = entry.substringBefore('=', "")
            val value = entry.substringAfter('=', "")
            if (id !in FrameScenarioIdSet || value !in AllowedFrameValues || values.put(id, value) != null) return null
        }
        if (values.keys != FrameScenarioIdSet) return null
        val canonical = values.entries.sortedBy { it.key }.joinToString(",") { "${it.key}=${it.value}" }
        return ParsedFrameReport(parts[1], canonical)
    }

    private fun acceptReport(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse {
        if (request.method != "POST") return methodNotAllowed("h19-report-method")
        report.set(canonicalReportOrNull(request.body) ?: Invalid)
        reports.incrementAndGet()
        return response("h19-report", PlainText, "accepted".toByteArray())
    }

    private fun <T> counted(
        counter: AtomicLong,
        block: () -> T,
    ): T {
        counter.incrementAndGet()
        return block()
    }

    private fun html(
        id: String,
        source: String,
    ) = response(id, Html, source.toByteArray())

    private fun methodNotAllowed(id: String) = response(id, "text/plain", ByteArray(0), 405)

    private fun encodedSentinel() = Base64.getEncoder().encodeToString(localSentinelBytes)

    private fun rasterCells() = buildString { repeat(64) { append("<span></span>") } }

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
        statusText =
            when (statusCode) {
                200 -> "OK"
                400 -> "Bad Request"
                else -> "Method Not Allowed"
            },
    )

    private data class ParsedFrameReport(
        val challenge: String,
        val canonicalResults: String,
    )

    private companion object {
        const val ControlledPath = "/web19/controlled"
        const val FramePath = "/web19/frame"
        const val ScriptPath = "/web19/site.js"
        const val StylePath = "/web19/site.css"
        const val WorkerPath = "/web19/worker.js"
        const val ServiceWorkerPath = "/web19/service-worker.js"
        const val ExternalSvgPath = "/web19/media.svg"
        const val MislabeledPath = "/web19/mislabeled-image"
        const val OctetPath = "/web19/octet-image"
        const val SameUrlPath = "/web19/same-url.png"
        const val SameBodyPath = "/web19/same-body.png"
        const val FrameReportPath = "/web19/frame-report"
        const val ReportPath = "/web19/report"
        const val StatePath = "/web19/state"
        const val MaximumReportBytes = 4096
        const val MaximumFrameReportBytes = 768
        const val FrameReportVersion = "v1"
        const val NotRun = "not_run"
        const val Invalid = "invalid"
        const val Html = "text/html; charset=utf-8"
        const val Css = "text/css; charset=utf-8"
        const val JavaScript = "application/javascript; charset=utf-8"
        const val PlainText = "text/plain; charset=utf-8"
        val PrintableAscii = 0x20..0x7e
        val AllowedValues = setOf("BLOCKED", "HIDDEN", "SAFE", "OUT_OF_SCOPE_VISIBLE", "ERROR")
        val AllowedFrameValues = setOf("BLOCKED", "ESCAPED", "ERROR")
        val FrameScenarioIds =
            listOf(
                "frame-data-img",
                "frame-blob-img",
                "frame-canvas",
                "frame-service-worker",
                "frame-closed-shadow",
            )
        val FrameScenarioIdSet = FrameScenarioIds.toSet()
        val FrameChallengePattern = Regex("[0-9a-f]{32}")
        val Scenarios =
            buildList {
                fun group(
                    category: ChromeStockMediaScenarioCategory,
                    vararg ids: String,
                ) = ids.forEach { add(ChromeStockMediaScenario(it, category)) }
                group(ChromeStockMediaScenarioCategory.Network, "network-safe-png", "network-block-webp", "network-block-jpeg", "network-jpeg-candidate", "network-webp-candidate", "network-avif-candidate", "network-picture-srcset", "network-css-url", "network-mislabeled", "network-octet-magic", "network-same-url-first", "network-same-url-second", "network-same-body-first", "network-same-body-second", "network-dynamic-replace", "network-lazy", "network-shadow")
                group(ChromeStockMediaScenarioCategory.Local, "local-data-img", "local-blob-img", "local-css-data", "local-css-blob", "local-canvas-2d", "local-put-image-data", "local-canvas-draw-image", "local-image-bitmap", "local-offscreen-canvas", "local-webgl", "local-webgpu", "local-svg-image", "local-svg-foreign-object", "local-svg-fe-image", "local-svg-external", "local-svg-icon-safe", "local-svg-media", "local-shadow-open", "local-shadow-closed", "local-iframe-transformed", "local-iframe-srcdoc", "local-service-worker", "local-spa-data-replace", "local-hostile-prototype-fresh-frame")
                group(
                    ChromeStockMediaScenarioCategory.Normality,
                    "normal-css",
                    "normal-layout",
                    "normal-form",
                    "normal-js",
                    "normal-fetch",
                    "normal-worker",
                    "normal-shadow",
                )
                group(ChromeStockMediaScenarioCategory.OutOfScope, "out-of-scope-css-synthesis")
            }
        val ScenarioIds = Scenarios.mapTo(linkedSetOf()) { it.id }
        const val WorkerScript = "postMessage('ready')"
        const val ServiceWorkerScript = "self.addEventListener('install',()=>self.skipWaiting());self.addEventListener('fetch',()=>{})"
        const val ExternalSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"320\" height=\"180\"><rect width=\"320\" height=\"180\" fill=\"#dc1430\"/></svg>"
    }
}
