(async()=>{
 if(location.origin!=='https://glosh-photos.test')throw Error('Controlled fixture only');
 const seed=new Uint8Array(await(await fetch('/safe-a.png')).arrayBuffer());
 if(String.fromCharCode(...seed.slice(-8,-4))!=='IEND')throw Error('PNG fixture required');
 const crc=bytes=>{let c=0xffffffff;for(const b of bytes){c^=b;for(let j=0;j<8;j++)c=(c>>>1)^((c&1)?0xedb88320:0)}return(c^0xffffffff)>>>0};
 const variant=n=>{const payload=new TextEncoder().encode('tEXtGloshProbe\0'+performance.timeOrigin+'-'+performance.now()+'-'+n),chunk=new Uint8Array(payload.length+8),v=new DataView(chunk.buffer);v.setUint32(0,payload.length-4);chunk.set(payload,4);v.setUint32(chunk.length-4,crc(payload));const out=new Uint8Array(seed.length+chunk.length);out.set(seed.slice(0,-12));out.set(chunk,seed.length-12);out.set(seed.slice(-12),seed.length-12+chunk.length);return 'data:image/png;base64,'+btoa(String.fromCharCode(...out))};
 document.getElementById('local-priority-burst')?.remove();
 const root=document.createElement('div');root.id='local-priority-burst';document.body.append(root);scrollTo(0,0);
 const sourceWidth=new DataView(seed.buffer).getUint32(16),sourceHeight=new DataView(seed.buffer).getUint32(20);
 const rows=[],started=performance.now();let done;const complete=new Promise(r=>done=r);
 for(let i=0;i<12;i++){const img=document.createElement('img');img.width=90;img.height=90;img.style.cssText='position:absolute;left:8px;top:'+(i<8?2000+i*110:80+(i-8)*100)+'px;width:90px;height:90px';root.append(img);img.onload=()=>{rows.push({index:i,ms:performance.now()-started,decoded:img.naturalWidth>0,originalDimensions:img.naturalWidth===sourceWidth&&img.naturalHeight===sourceHeight});if(rows.length===12)done()};img.src=variant(i)}
 await Promise.race([complete,new Promise(r=>setTimeout(r,6000))]);
 return {rows,complete:rows.length===12,aboveFoldCompletion:Math.max(0,...rows.filter(r=>r.index>=8).map(r=>r.ms)),elapsed:performance.now()-started};
})()
