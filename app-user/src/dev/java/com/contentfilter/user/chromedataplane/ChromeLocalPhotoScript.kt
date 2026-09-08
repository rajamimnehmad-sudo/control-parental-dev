package com.contentfilter.user.chromedataplane

/** Asynchronous, bounded local-raster submission; all visible results still come from the Byte Gate. */
internal object ChromeLocalPhotoScript {
    val declarations =
        """
        const localPhotoTimer=SELF.setTimeout,localPhotoAbort=NativeXMLHttpRequest.prototype.abort,localPhotoExec=RegExp.prototype.exec,localPhotoRect=Element.prototype.getBoundingClientRect;
        const localPhotoStates=new WeakMap(),localPhotoQueue=[];let localPhotoActive=null,localPhotoBytes=0,localPhotoScheduled=false,localPhotoDocumentActive=true,localPhotoOvertakes=0;
        const LOCAL_PHOTO_URL=stringOf(new NativeURL('/__glosh/local-photo/submit',NativeLocation.href)),LOCAL_PHOTO_ASSET='https://glosh-photos.test/__glosh/local-photo/asset/';
        const localPhotoPrefix='v1|LOCAL_PHOTO|'+READY+'|'+SESSION+'|'+POLICY_EPOCH+'|'+NAVIGATION_SEQUENCE+'|'+DOCUMENT_SEQUENCE+'|'+(TOP_LEVEL?'T':'S')+'\n';
        const localPhotoSlots=(element)=>{let slots=invoke(WeakMapGet,localPhotoStates,[element]);if(!slots){slots={src:null,srcset:null};invoke(WeakMapSet,localPhotoStates,[element,slots])}return slots};
        const localPhotoCancel=(element,key)=>{if(key!=='src'&&key!=='srcset')return;const slots=invoke(WeakMapGet,localPhotoStates,[element]),job=slots&&slots[key];if(!job)return;slots[key]=null;nativeRemove.call(element,'data-glosh-local-'+key);job.cancelled=true;
        if(job.xhr){try{invoke(localPhotoAbort,job.xhr,[])}catch(_){}}};
        const localPhotoSchedule=()=>{if(localPhotoScheduled||!localPhotoDocumentActive||!selfReadyAccepted)return;localPhotoScheduled=true;invoke(localPhotoTimer,SELF,[()=>{localPhotoScheduled=false;localPhotoPump()},0])};
        const localPhotoFinish=(job)=>{if(job.finished)return;job.finished=true;if(localPhotoDocumentActive&&localPhotoSlots(job.element)[job.key]===job){localPhotoSlots(job.element)[job.key]=null;nativeRemove.call(job.element,'data-glosh-local-'+job.key)}localPhotoBytes-=job.value.length;job.value='';job.items.length=0;job.output='';job.xhr=null;job.element=null;if(localPhotoActive===job)localPhotoActive=null;localPhotoSchedule()};
        const localPhotoCurrent=(job)=>localPhotoDocumentActive&&!job.finished&&!job.cancelled&&localPhotoSlots(job.element)[job.key]===job&&!(nativeGet.call(job.element,job.key)||'');
        // A visible pending photo may overtake FIFO work, but every fifth overtake serves the oldest job.
        // Geometry affects scheduling only; the same byte authority still decides every result.
        const localPhotoVisible=(job)=>{try{let element=job.element;const parent=parentOf(element);if(localNameOf(element)==='source'&&parent)element=elementQuery.call(parent,'img')||element;
        const rect=invoke(localPhotoRect,element,[]);return rect.width>0&&rect.height>0&&rect.bottom>0&&rect.right>0&&rect.top<SELF.innerHeight&&rect.left<SELF.innerWidth}catch(_){return false}};
        const localPhotoSelect=()=>{let selected=0;if(localPhotoOvertakes<4){for(let i=0;i<localPhotoQueue.length;i+=1){const candidate=localPhotoQueue[i];if(localPhotoCurrent(candidate)&&localPhotoVisible(candidate)){selected=i;break}}}
        if(selected>0)localPhotoOvertakes+=1;else localPhotoOvertakes=0;return selected};
        const localPhotoPump=()=>{if(localPhotoActive||!localPhotoDocumentActive||!selfReadyAccepted)return;let job=null;
        while(localPhotoQueue.length){const selected=localPhotoSelect();job=localPhotoQueue[selected];for(let i=selected+1;i<localPhotoQueue.length;i+=1)localPhotoQueue[i-1]=localPhotoQueue[i];localPhotoQueue.length-=1;if(localPhotoCurrent(job))break;localPhotoFinish(job);job=null}
        if(!job)return;localPhotoActive=job;localPhotoSend(job)};
        const localPhotoSend=(job)=>{if(!localPhotoCurrent(job)){localPhotoFinish(job);return}const item=job.items[job.index];let xhr=null;
        try{xhr=new NativeXMLHttpRequest();job.xhr=xhr;xhrOpen.call(xhr,'POST',LOCAL_PHOTO_URL,true);xhrSetHeader.call(xhr,'Content-Type','text/plain;charset=UTF-8');
        nativeAddEvent.call(xhr,'loadend',()=>{job.xhr=null;if(!localPhotoCurrent(job)){localPhotoFinish(job);return}
        const value=stringOf(read(xhrResponseTextProperty,xhr)||'');if(read(xhrStatusProperty,xhr)!==200||read(xhrResponseUrlProperty,xhr)!==LOCAL_PHOTO_URL||slice(value,0,LOCAL_PHOTO_ASSET.length)!==LOCAL_PHOTO_ASSET||value.length!==LOCAL_PHOTO_ASSET.length+48){localPhotoFinish(job);return}
        job.output+=slice(job.value,job.offset,item.start)+value;job.offset=item.end;job.index+=1;
        if(job.index<job.items.length){localPhotoSend(job);return}
        const approved=job.output+slice(job.value,job.offset);nativeRemove.call(job.element,'data-glosh-local-'+job.key);nativeSet.call(job.element,job.key,approved);localPhotoSlots(job.element)[job.key]=null;sanitizeElement(job.element);localPhotoFinish(job)},false);
        xhrSend.call(xhr,localPhotoPrefix+item.value);
        invoke(localPhotoTimer,SELF,[()=>{if(!job.finished&&job.xhr===xhr){job.cancelled=true;try{invoke(localPhotoAbort,xhr,[])}catch(_){}localPhotoFinish(job)}},15000]);
        }catch(_){localPhotoFinish(job)}};
        const localPhotoRewrite=(element,key)=>{if(!SELF_SHIELD)return false;let value=nativeGet.call(element,key)||'',inert=nativeGet.call(element,'data-glosh-local-'+key);
        const slots=localPhotoSlots(element),old=slots[key];if(!value&&old&&!old.finished&&inert===old.value)return true;if(!value&&inert)value=inert;
        if(old&&value)localPhotoCancel(element,key);
        if(!value)return !!slots[key];if(!includes(lower(value),'data:'))return false;
        nativeRemove.call(element,key);hide(element);
        if(value.length>1400000||localPhotoBytes+value.length>8*1024*1024||localPhotoQueue.length>=128)return true;
        const pattern=/data:image\/(?:jpeg|png|webp|avif);base64,[A-Za-z0-9+\/%=]+/gi,items=[];let match=null;
        while((match=invoke(localPhotoExec,pattern,[value]))!==null){items[items.length]={start:match.index,end:match.index+match[0].length,value:match[0]};if(items.length>8)return true}
        if(!items.length||(key==='src'&&(items.length!==1||items[0].start!==0||items[0].end!==value.length)))return true;
        const job={element:element,key:key,value:value,items:items,index:0,offset:0,output:'',xhr:null,cancelled:false,finished:false};slots[key]=job;nativeSet.call(element,'data-glosh-local-'+key,value);localPhotoBytes+=value.length;localPhotoQueue[localPhotoQueue.length]=job;localPhotoSchedule();return true};
        nativeAddEvent.call(SELF,'pagehide',()=>{localPhotoDocumentActive=false;if(localPhotoActive){const active=localPhotoActive;active.cancelled=true;try{if(active.xhr)invoke(localPhotoAbort,active.xhr,[])}catch(_){}localPhotoFinish(active)}
        for(let i=0;i<localPhotoQueue.length;i+=1){localPhotoQueue[i].cancelled=true;localPhotoFinish(localPhotoQueue[i])}localPhotoQueue.length=0},true);
        nativeAddEvent.call(SELF,'pageshow',()=>{localPhotoDocumentActive=true;localPhotoSchedule()},true);
        """.trimIndent()
}
