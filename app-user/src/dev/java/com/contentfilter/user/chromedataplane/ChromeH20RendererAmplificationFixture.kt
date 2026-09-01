package com.contentfilter.user.chromedataplane

import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal enum class ChromeH20RendererAmplificationMode { AttrStress, ChildStress, SvgStress, ShadowStress, Mixed }

/** Bounded renderer workload for attributing H20 observer and scan amplification. */
internal class ChromeH20RendererAmplificationFixture {
    private val documents = AtomicLong()
    private val scripts = AtomicLong()
    private val reports = AtomicLong()
    private val lastReport = AtomicReference("not_run")

    fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse? {
        val path = request.target.substringBefore('?').substringBefore('#')
        return when (path) {
            PagePath -> {
                documents.incrementAndGet()
                html("h20-renderer-amplification", document(mode(request.target)))
            }
            ScriptPath -> {
                scripts.incrementAndGet()
                response("h20-renderer-amplification-script", JavaScript, script(mode(request.target)).toByteArray())
            }
            ReportPath -> acceptReport(request)
            StatePath -> response("h20-renderer-amplification-state", PlainText, state().toByteArray())
            else -> null
        }
    }

    fun state(): String =
        "DOCUMENTS=${documents.get()},SCRIPTS=${scripts.get()},REPORTS=${reports.get()},LAST=${lastReport.get()}"

    internal fun document(mode: ChromeH20RendererAmplificationMode): String =
        "<!doctype html><html><head><meta charset=\"utf-8\"><title>H20 RENDERER ${mode.name}</title>" +
            "<script defer src=\"$ScriptPath?mode=${mode.wireName()}\"></script></head><body>" +
            "<h1>H20 renderer amplification ${mode.name}</h1><main id=\"fixture-root\"></main></body></html>"

    internal fun script(mode: ChromeH20RendererAmplificationMode): String =
        ChromeMediaShieldBootstrap.selfShieldOriginalScriptStartedScript() +
            """
            ;(()=>{'use strict';const MODE='${mode.wireName()}',ROOT=document.getElementById('fixture-root');let siteMutations=0;
            const build=(owner,count)=>{const fragment=document.createDocumentFragment();for(let i=0;i<count;i+=1){const card=document.createElement('article');
            card.className='card';const text=document.createElement('span');text.textContent='item-'+i;card.append(text);if(i%4===0){const image=document.createElement('img');
            image.src='/safe-a.png?renderer='+i;card.append(image)}if(i%25===0){const svg=document.createElementNS('http://www.w3.org/2000/svg','svg');svg.setAttribute('width','24');svg.setAttribute('height','24');
            const path=document.createElementNS('http://www.w3.org/2000/svg','path');path.setAttribute('d','M2 12L12 2l10 10-10 10z');svg.append(path);card.append(svg)}fragment.append(card)}owner.append(fragment)};
            build(ROOT,600);const attr=()=>{for(let i=0;i<160;i+=1){ROOT.setAttribute('style','--glosh-fixture-tick:'+i);siteMutations+=1}};
            const child=()=>{for(let i=0;i<240;i+=1){const node=document.createElement('span');node.textContent='dynamic-'+i;ROOT.append(node);node.remove();siteMutations+=2}};
            const svg=()=>{for(let i=0;i<80;i+=1){const icon=document.createElementNS('http://www.w3.org/2000/svg','svg');icon.setAttribute('width','24');icon.setAttribute('height','24');
            const path=document.createElementNS('http://www.w3.org/2000/svg','path');path.setAttribute('d','M2 12L12 2l10 10-10 10z');icon.append(path);ROOT.append(icon);siteMutations+=4}};
            const shadow=()=>{const host=document.createElement('div');ROOT.append(host);const root=host.attachShadow({mode:'open'}),container=document.createElement('section');root.append(container);build(container,240);
            for(let i=0;i<120;i+=1){container.setAttribute('style','--glosh-shadow-tick:'+i);siteMutations+=1}};
            if(MODE==='ATTR_STRESS')attr();else if(MODE==='CHILD_STRESS')child();else if(MODE==='SVG_STRESS')svg();else if(MODE==='SHADOW_STRESS')shadow();else{attr();child();svg();shadow()}
            queueMicrotask(async()=>{document.dispatchEvent(new Event('${ChromeMediaShieldRendererMetricsScript.SnapshotEvent}'));try{await fetch('$ReportPath',{method:'POST',headers:{'Content-Type':'text/plain'},body:'MODE='+MODE+',SITE_MUTATIONS='+siteMutations})}catch(_){}})})();
            """.trimIndent().replace("\n", "")

    private fun acceptReport(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse {
        if (request.method != "POST") return response("h20-renderer-report-method", PlainText, ByteArray(0), 405)
        val report = request.body.toString(Charsets.US_ASCII)
        val valid = report.matches(ReportPattern)
        if (valid) {
            reports.incrementAndGet()
            lastReport.set(report)
        }
        return response("h20-renderer-report", PlainText, ByteArray(0), if (valid) 204 else 400)
    }

    private fun mode(target: String): ChromeH20RendererAmplificationMode {
        val value = target.substringAfter("mode=", "").substringBefore('&')
        return ChromeH20RendererAmplificationMode.entries.firstOrNull { it.wireName() == value }
            ?: ChromeH20RendererAmplificationMode.Mixed
    }

    private fun ChromeH20RendererAmplificationMode.wireName(): String =
        name.replace(Regex("([a-z])([A-Z])"), "${'$'}1_${'$'}2").uppercase()

    private fun html(
        id: String,
        body: String,
    ) = response(id, Html, body.toByteArray())

    private fun response(
        id: String,
        contentType: String,
        bytes: ByteArray,
        status: Int = 200,
    ) = ChromePhotosFixtureResponse(id, contentType, bytes, statusCode = status)

    companion object {
        const val PagePath = "/webh20/renderer-amplification"
        const val ScriptPath = "/webh20/renderer-amplification.js"
        const val ReportPath = "/webh20/renderer-amplification-report"
        const val StatePath = "/webh20/renderer-amplification-state"
        private const val Html = "text/html; charset=utf-8"
        private const val JavaScript = "application/javascript; charset=utf-8"
        private const val PlainText = "text/plain; charset=utf-8"
        private val ReportPattern = Regex("MODE=(ATTR|CHILD|SVG|SHADOW|MIXED)_STRESS,SITE_MUTATIONS=[0-9]{1,7}")
    }
}
