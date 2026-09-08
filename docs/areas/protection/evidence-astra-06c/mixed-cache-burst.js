(async()=>{
 if(location.hostname!=='glosh-photos.test'||location.pathname!=='/')throw Error('Controlled fixture required');
 const primary=[...document.images].find(i=>i.alt==='SAFE PNG');
 if(!primary?.complete||!primary.naturalWidth)throw Error('Prime image not ready');
 const cold=[...document.images].filter(i=>i.loading==='lazy'&&!i.complete);
 const base=new URL(primary.getAttribute('src'),location.href);
 const started=performance.now();
 cold.forEach(i=>i.loading='eager');
 await new Promise(r=>setTimeout(r,400));
 const samples=await Promise.all(Array.from({length:10},(_,index)=>new Promise(resolve=>{
   const img=new Image(),url=new URL(base);url.searchParams.set('glosh_cache_probe',String(index));
   img.width=20;img.height=20;img.loading='eager';document.body.appendChild(img);
   const begin=performance.now();let settled=false;
   const done=event=>{if(settled)return;settled=true;resolve({index,event,ms:performance.now()-begin,decoded:img.naturalWidth>0});};
   img.onload=()=>done('load');img.onerror=()=>done('error');setTimeout(()=>done('timeout'),5500);img.src=url.href;
 })));
 return {coldActivated:cold.length,elapsed:performance.now()-started,samples};
})()
