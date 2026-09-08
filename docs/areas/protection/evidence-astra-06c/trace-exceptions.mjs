// Diagnostic only: resumes every exception immediately; not latency evidence.
const tabs=await(await fetch('http://127.0.0.1:19226/json/list')).json();
const tab=tabs.find(t=>t.url.startsWith('https://www.fravega.com')&&t.title);
const ws=new WebSocket(tab.webSocketDebuggerUrl);let id=10;
const send=(method,params={})=>ws.send(JSON.stringify({id:++id,method,params}));
ws.onopen=()=>{send('Debugger.enable');send('Debugger.setPauseOnExceptions',{state:'all'});send('Page.reload')};
ws.onmessage=e=>{const r=JSON.parse(e.data);if(r.method==='Debugger.paused'){
 const p=r.params; if((p.data?.description||'').includes('Blocked by Glosh'))console.log(JSON.stringify({reason:p.reason,data:p.data?.description,frames:p.callFrames.slice(0,8).map(f=>({name:f.functionName,url:f.url,location:f.location}))}));
 send('Debugger.resume');
}};
setTimeout(()=>{send('Debugger.setPauseOnExceptions',{state:'none'});send('Debugger.resume');send('Debugger.disable');setTimeout(()=>ws.close(),300)},25000);
