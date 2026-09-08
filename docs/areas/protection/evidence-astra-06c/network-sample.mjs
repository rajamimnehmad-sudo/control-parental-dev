// Passive CDP network timing. No cache/header overrides; no URLs, cookies, or bodies persisted.
import fs from 'node:fs';
import {createHash} from 'node:crypto';
const [id,path]=process.argv.slice(2);
const tabs=await(await fetch('http://127.0.0.1:19226/json/list')).json();
const tab=tabs.find(t=>t.id===id);if(!tab)throw Error('Observed tab missing');
const out=fs.createWriteStream(path,{flags:'wx'});
const ws=new WebSocket(tab.webSocketDebuggerUrl);
const digest=s=>createHash('sha256').update(s).digest('hex').slice(0,16);
ws.onopen=()=>ws.send(JSON.stringify({id:1,method:'Network.enable'}));
ws.onmessage=e=>{
 const r=JSON.parse(e.data),p=r.params;if(!p)return;
 const base={event:r.method,id:p.requestId,t:p.timestamp};let row;
 if(r.method==='Network.requestWillBeSent'){
  const u=new URL(p.request.url);row={...base,wall:p.wallTime,hostHash:digest(u.hostname),resourceHash:digest(p.request.url),type:p.type,method:p.request.method};
 }else if(r.method==='Network.responseReceived'){
  const s=p.response;row={...base,type:p.type,status:s.status,mime:s.mimeType,protocol:s.protocol,diskCache:!!s.fromDiskCache,serviceWorker:!!s.fromServiceWorker,timing:s.timing};
 }else if(r.method==='Network.loadingFinished')row={...base,bytes:p.encodedDataLength};
 else if(r.method==='Network.loadingFailed')row={...base,type:p.type,canceled:p.canceled,error:p.errorText,blockedReason:p.blockedReason};
 if(row)out.write(JSON.stringify(row)+'\n');
};
const close=()=>{ws.close();out.end(()=>process.exit(0));};
process.on('SIGTERM',close);process.on('SIGINT',close);
ws.onclose=()=>out.end(()=>process.exit(0));
