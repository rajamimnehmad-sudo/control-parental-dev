package com.contentfilter.user.chromedataplane

/** Reproduces background form submissions without third-party requests or personal information. */
internal object ChromeFormTargetFixture {
    fun page(): String =
        """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>Form target compatibility</title></head>
        <body><h1>Form target compatibility</h1><pre id="result">RUNNING</pre>
        <form id="static-form" target="background-frame" action="/svg06a/forms?escaped=1"></form>
        <script>(async()=>{const result=document.getElementById('result');if(location.search.includes('escaped=1')){result.textContent='FORM_TARGET_ESCAPE:FAIL';return}
        const checks=[],check=(name,value)=>checks.push(name+':'+(value?'PASS':'FAIL')),wait=()=>new Promise(r=>setTimeout(r,40));
        const make=()=>{const f=document.createElement('form');f.action='/svg06a/forms?escaped=1';document.body.appendChild(f);return f};
        document.getElementById('static-form').submit();await wait();check('STATIC_NAMED_NO_NAVIGATION',true);
        for(const mode of ['property','attribute','namespace','markup','clone']){let f=make();
        if(mode==='property')f.target='background-frame';if(mode==='attribute')f.setAttribute('target','background-frame');
        if(mode==='namespace')f.setAttributeNS(null,'target','background-frame');
        if(mode==='markup'){f.remove();const box=document.createElement('div');box.innerHTML='<form target="background-frame" action="/svg06a/forms?escaped=1"></form>';document.body.appendChild(box);f=box.firstElementChild}
        if(mode==='clone'){f.target='background-frame';const cloned=f.cloneNode(true);f.remove();document.body.appendChild(cloned);f=cloned}
        f.submit();await wait();check(mode.toUpperCase()+'_NO_NAVIGATION',true);f.remove()}
        const requested=make();requested.target='background-frame';requested.requestSubmit();await wait();check('REQUEST_SUBMIT_NO_NAVIGATION',true);
        const clicked=document.createElement('button');clicked.type='submit';requested.appendChild(clicked);clicked.click();await wait();check('CLICK_NO_NAVIGATION',true);
        const override=make(),submitter=document.createElement('button');submitter.type='submit';submitter.formTarget='background-frame';override.appendChild(submitter);
        override.requestSubmit(submitter);await wait();check('SUBMITTER_NO_NAVIGATION',true);
        let normal=0;const current=make();current.addEventListener('submit',e=>{e.preventDefault();normal++});current.requestSubmit();check('DEFAULT_FORM_EVENT',normal===1);
        current.target='background-frame';current.target='_self';current.requestSubmit();check('EXPLICIT_SELF_RESTORES',normal===2);
        current.target='background-frame';current.removeAttribute('target');current.requestSubmit();check('REMOVE_TARGET_RESTORES',normal===3);
        const button=document.createElement('button');button.type='submit';button.formTarget='_self';requested.appendChild(button);let restored=0;
        requested.addEventListener('submit',e=>{e.preventDefault();restored++});requested.requestSubmit(button);check('SELF_SUBMITTER_OVERRIDE',restored===1);
        result.textContent=checks.join('\n');document.title=checks.every(x=>x.endsWith(':PASS'))?'FORM_TARGET_PASS':'FORM_TARGET_FAIL';
        })();</script></body></html>
        """.trimIndent()
}
