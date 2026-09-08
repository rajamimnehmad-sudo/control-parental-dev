(()=>{
 const nav=performance.getEntriesByType('navigation')[0];
 const images=[...document.images].map((i,index)=>{const r=i.getBoundingClientRect();const resources=performance.getEntriesByName(i.currentSrc);return {index,complete:i.complete,width:i.naturalWidth,height:i.naturalHeight,loading:i.loading,visible:r.width>0&&r.height>0,viewport:r.bottom>0&&r.top<innerHeight&&r.right>0&&r.left<innerWidth,rect:{x:r.x,y:r.y,width:r.width,height:r.height},resourceEnd:resources.at(-1)?.responseEnd??null}});
 return {url:location.origin+location.pathname,title:document.title,ready:document.readyState,viewport:{width:innerWidth,height:innerHeight},scroll:{x:scrollX,y:scrollY,max:document.documentElement.scrollHeight-innerHeight},timing:nav?{ttfb:nav.responseStart,dcl:nav.domContentLoadedEventEnd,load:nav.loadEventEnd}:null,images,fixture:location.hostname==='glosh-photos.test'?document.body.innerText:null};
})()
