package com.contentfilter.user.chromedataplane

import java.util.Base64

internal class ChromeLocalPhotoFixture(private val safe: ByteArray) {
    fun responseFor(request: ChromePhotosProxyRequest): ChromePhotosFixtureResponse? {
        if (request.target != "/local-photos") return null
        val data = "data:image/png;base64," + Base64.getEncoder().encodeToString(safe)
        val html =
            """
            <!doctype html><meta name="viewport" content="width=device-width,initial-scale=1">
            <title>LOCAL_PHOTO_RUNNING</title><style>body{font:16px sans-serif}img,input{width:96px;height:72px;object-fit:contain}#report{white-space:pre-wrap}</style>
            <h1>Static local photos</h1><pre id="report">RUNNING</pre>
            <img id="static" src="$data"><img id="dynamic"><img id="set" srcset="$data 1x, $data 2x">
            <picture><source id="picture-source" srcset="$data"><img id="picture"></picture>
            <input id="input" type="image" src="$data"><img id="stale"><img id="removed"><div id="markup"></div><div id="shadow"></div>
            <script>
            const data='$data';document.getElementById('dynamic').src=data;
            document.getElementById('markup').innerHTML='<img id="inserted" src="'+data+'">';
            const root=document.getElementById('shadow').attachShadow({mode:'open'});root.innerHTML='<img id="shadow-image" src="'+data+'">';
            const clone=document.getElementById('dynamic').cloneNode(true);clone.id='clone';document.body.append(clone);
            const stale=document.getElementById('stale');stale.src=data;stale.src='/safe-a.png';
            const removed=document.getElementById('removed');removed.src=data;removed.removeAttribute('src');
            async function check(){const rows=[];let ok=true;
              for(const id of ['static','dynamic','set','picture','input','inserted','clone']){const e=document.getElementById(id),v=e.complete&&e.naturalWidth>0||id==='input'&&e.src.includes('/__glosh/local-photo/asset/');rows.push(id+':'+(v?'PASS':'FAIL'));ok=ok&&!!v}
              const sh=root.querySelector('img'),sv=sh.complete&&sh.naturalWidth>0;rows.push('shadow:'+(sv?'PASS':'FAIL'));ok=ok&&sv;
              const clear=!removed.getAttribute('src');rows.push('removed:'+(clear?'PASS':'FAIL'));ok=ok&&clear;
              const current=stale.getAttribute('src')==='/safe-a.png';rows.push('stale:'+(current?'PASS':'FAIL'));ok=ok&&current;
              try{const bytes=new Uint8Array(await(await fetch(document.getElementById('static').currentSrc)).arrayBuffer());const source=Uint8Array.from(atob(data.split(',')[1]),c=>c.charCodeAt(0));const same=bytes.length===source.length&&bytes.every((b,i)=>b===source[i]);rows.push('byte_identity:'+(same?'PASS':'FAIL'));ok=ok&&same}catch(e){rows.push('byte_identity:FAIL');ok=false}
              document.getElementById('report').textContent=rows.join('\n');document.title=ok?'LOCAL_PHOTO_PASS':'LOCAL_PHOTO_FAIL';
            }
            setTimeout(check,8000);
            </script>
            """.trimIndent()
        return ChromePhotosFixtureResponse("local-photos", "text/html; charset=utf-8", html.toByteArray())
    }
}
