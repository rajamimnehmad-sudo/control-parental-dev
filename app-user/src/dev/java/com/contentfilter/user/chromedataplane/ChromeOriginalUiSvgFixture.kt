package com.contentfilter.user.chromedataplane

import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

/** Controlled end-to-end authority and fidelity fixture; public sites remain compatibility evidence. */
internal class ChromeOriginalUiSvgFixture {
    private val report = AtomicReference("not_run")

    fun state(): String = report.get()

    fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse? {
        val path = request.target.substringBefore('?').substringBefore('#')
        return when (path) {
            PagePath -> response("ui-svg-page", "text/html; charset=utf-8", page().toByteArray())
            CssPath ->
                response(
                    "ui-svg-css",
                    "text/css; charset=utf-8",
                    ".external-icon{mask-image:url(data:image/svg+xml;base64,$safeSvgBase64)}".toByteArray(),
                    headers = listOf(ChromeHttpHeader("Cache-Control", "public, max-age=3600")),
                )
            NetworkSvgPath -> response("ui-svg-network", SvgMimeType, SafeSvg)
            UnsafeNetworkSvgPath -> response("ui-svg-network-unsafe", SvgMimeType, UnsafeSvg)
            ReportPath -> {
                val candidate = request.body.toString(Charsets.US_ASCII).take(MaximumReportBytes)
                report.set(candidate.takeIf { it.matches(ReportPattern) } ?: "invalid")
                response("ui-svg-report", "text/plain; charset=utf-8", "accepted".toByteArray())
            }
            else -> null
        }
    }

    private fun page(): String =
        """
        <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>SVG06A_RUNNING</title><link rel="icon" href="data:image/svg+xml;base64,$safeSvgBase64"><link rel="stylesheet" href="$CssPath?case=authority-v1">
        <style>body{font:16px sans-serif;padding:20px}.row{display:flex;gap:16px;align-items:center;flex-wrap:wrap}.icon{width:32px;height:32px;background:#146c43}.static-icon{mask:url(data:image/svg+xml;base64,$safeSvgBase64)}.bad{background-image:url(data:image/png;base64,AAAA)}</style></head>
        <body><h1>ORIGINAL UI SVG AUTHORITY 06A</h1><div class="row">
        <button id="inline-button"><svg id="inline" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" width="32" height="32"><path fill="#123456" stroke="#654321" d="M2 3h20v18H2z"/></svg>Inline</button>
        <span id="static" class="icon static-icon"></span><span id="external" class="icon external-icon"></span>
        <span id="attribute" class="icon" style="mask-image:url(data:image/svg+xml;base64,$safeSvgBase64)"></span>
        <img id="data-image" width="32" height="32" src="data:image/svg+xml;base64,$safeSvgBase64">
        <img id="network-image" width="32" height="32" src="$NetworkSvgPath"><img id="unsafe-network" width="32" height="32" src="$UnsafeNetworkSvgPath"><img id="raster-negative" src="data:image/png;base64,AAAA">
        <svg id="unsafe-inline" xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1 1"><foreignObject><div xmlns="http://www.w3.org/1999/xhtml">bad</div></foreignObject></svg>
        </div><pre id="result">RUNNING</pre><script>
        (()=>{const checks=[];const check=(name,value)=>checks.push(name+':'+(value?'PASS':'FAIL'));let clicks=0;
        document.getElementById('inline-button').addEventListener('click',()=>clicks++);document.getElementById('inline-button').click();
        const dynamic=document.createElement('span');dynamic.id='dynamic';dynamic.className='icon';dynamic.style.setProperty('mask-image','url(data:image/svg+xml;base64,$safeSvgBase64)');document.body.appendChild(dynamic);
        const style=document.createElement('style');style.textContent='.dynamic-rule{background-image:url(data:image/svg+xml;base64,$safeSvgBase64)}';document.head.appendChild(style);dynamic.classList.add('dynamic-rule');
        const sheet=new CSSStyleSheet();sheet.replaceSync('.constructed{mask:url(data:image/svg+xml;base64,$safeSvgBase64)}');document.adoptedStyleSheets=[...document.adoptedStyleSheets,sheet];dynamic.classList.add('constructed');
        const internal='${com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract.OriginalUiSvgOrigin}';const attr=(id,name)=>document.getElementById(id).getAttribute(name)||'';const computed=id=>getComputedStyle(document.getElementById(id));
        setTimeout(()=>{const inline=document.getElementById('inline'),path=inline.querySelector('path');
        check('INLINE_ORIGINAL',path.getAttribute('d')==='M2 3h20v18H2z'&&path.getAttribute('fill')==='#123456'&&path.getAttribute('stroke')==='#654321'&&inline.getAttribute('data-glosh-icon-safe')==='1');check('INLINE_CLICK',clicks===1);
        check('STATIC_CSS',computed('static').maskImage.includes(internal));check('EXTERNAL_CSS',computed('external').maskImage.includes(internal));check('STYLE_ATTRIBUTE',attr('attribute','style').includes(internal));
        check('DATA_IMAGE',attr('data-image','src').startsWith(internal)&&document.getElementById('data-image').naturalWidth>0);check('FAVICON',document.querySelector('link[rel~=icon]').href.startsWith(internal));
        check('NETWORK_SVG',document.getElementById('network-image').naturalWidth===24);check('UNSAFE_NETWORK_FAIL_CLOSED',document.getElementById('unsafe-network').naturalWidth>24);check('DYNAMIC_CSS',computed('dynamic').maskImage.includes(internal)&&computed('dynamic').backgroundImage.includes(internal));
        check('RASTER_FAIL_CLOSED',document.getElementById('raster-negative').getAttribute('data-glosh-media-blocked')==='1');check('UNSAFE_INLINE_FAIL_CLOSED',document.getElementById('unsafe-inline').getAttribute('data-glosh-media-blocked')==='1');
        const value=checks.join(',');document.getElementById('result').textContent=checks.join('\n');document.title=checks.every(x=>x.endsWith('PASS'))?'SVG06A_PASS':'SVG06A_FAIL';fetch('$ReportPath',{method:'POST',headers:{'Content-Type':'text/plain'},body:value});},1200);})();
        </script>${ChromePhotosFixtureLeaseContract.ScriptTag}</body></html>
        """.trimIndent()

    private fun response(
        id: String,
        contentType: String,
        bytes: ByteArray,
        headers: List<ChromeHttpHeader> = emptyList(),
    ) = ChromePhotosFixtureResponse(id, contentType, bytes, headers)

    private companion object {
        const val PagePath = "/svg06a"
        const val CssPath = "/svg06a/external.css"
        const val NetworkSvgPath = "/svg06a/network.svg"
        const val UnsafeNetworkSvgPath = "/svg06a/unsafe.svg"
        const val ReportPath = "/svg06a/report"
        const val SvgMimeType = "image/svg+xml"
        const val MaximumReportBytes = 2048
        val ReportPattern = Regex("[A-Z_]+:(?:PASS|FAIL)(?:,[A-Z_]+:(?:PASS|FAIL))*")
        val SafeSvg =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" width=\"24\" height=\"24\"><path fill=\"currentColor\" d=\"M2 3h20v18H2z\"/></svg>".toByteArray()
        val UnsafeSvg =
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 1 1\"><script/></svg>".toByteArray()
        val safeSvgBase64: String = Base64.getEncoder().encodeToString(SafeSvg)
    }
}
