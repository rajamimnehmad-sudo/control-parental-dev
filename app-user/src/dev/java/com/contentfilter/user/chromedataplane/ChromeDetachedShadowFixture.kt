package com.contentfilter.user.chromedataplane

/** Strict CSP and synchronous CSSOM attacks before any MutationObserver can run. */
internal object ChromeDetachedShadowFixture {
    fun page(): String = """
        <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>SHADOW_RUNNING</title></head>
        <body><h1>Detached shadow lifecycle</h1><pre id="result">RUNNING</pre><script>
        (()=>{const out=[],check=(n,v)=>out.push(n+':'+(v?'PASS':'FAIL')),denied=f=>{try{f();return false}catch(_){return true}};
        for(const mode of ['open','closed']){const prefix=mode.toUpperCase(),host=document.createElement('div');let root;
        try{root=host.attachShadow({mode});check(prefix+'_DETACHED',!host.isConnected&&!!root)}catch(_){check(prefix+'_DETACHED',false);continue}
        const paragraph=document.createElement('p');paragraph.textContent='Original text '+mode;root.append(paragraph);document.body.append(host);
        const style=root.querySelector('style'),sheet=style&&style.sheet;
        check(prefix+'_CSP_SHEET',!!sheet&&style.nonce===''&&!style.hasAttribute('nonce'));
        check(prefix+'_TEXT_VISIBLE',paragraph.textContent==='Original text '+mode&&getComputedStyle(paragraph).visibility==='visible');
        if(sheet){const alias=root.styleSheets?root.styleSheets[0]:sheet;
        check(prefix+'_CSSOM_LOCKED',denied(()=>alias.deleteRule(0))&&denied(()=>{alias.disabled=true})&&denied(()=>{alias.media.mediaText='print'})&&denied(()=>alias.cssRules[0].styleMap.clear())&&denied(()=>{alias.rules[0].style.cssText=''})&&denied(()=>{style.textContent=''}));}
        else check(prefix+'_CSSOM_LOCKED',false);
        const canvas=document.createElement('canvas');root.append(canvas);check(prefix+'_CANVAS_FAIL_CLOSED',canvas.getContext('2d')===null||getComputedStyle(canvas).visibility==='hidden');
        host.remove();document.body.append(host);const again=style.sheet;check(prefix+'_REATTACH',!!again&&denied(()=>again.deleteRule(0))&&getComputedStyle(paragraph).visibility==='visible');
        }
        document.getElementById('result').textContent=out.join('\n');document.title=out.every(x=>x.endsWith('PASS'))?'SHADOW_PASS':'SHADOW_FAIL';})();
        </script>${ChromePhotosFixtureLeaseContract.ScriptTag}</body></html>
    """.trimIndent()
}
