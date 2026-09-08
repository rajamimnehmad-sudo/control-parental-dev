// Bounded diagnostic breakpoint at Glosh's own deny(); every pause is resumed.
const tabs=await(await fetch('http://127.0.0.1:19226/json/list')).json();
const tab=tabs.find(t=>t.url.startsWith('https://www.fravega.com'));
const ws=new WebSocket(tab.webSocketDebuggerUrl);let id=0;const pending=new Map();
const call=(method,params={})=>new Promise(resolve=>{const n=++id;pending.set(n,resolve);ws.send(JSON.stringify({id:n,method,params}))});
ws.onopen=async()=>{await call('Debugger.enable');await call('Page.reload')};
ws.onmessage=async e=>{const r=JSON.parse(e.data);if(r.id){pending.get(r.id)?.(r);pending.delete(r.id);return}
 if(r.method==='Debugger.scriptParsed'&&r.params.url.startsWith('https://www.fravega.com')&&r.params.length>50000){
  const p=r.params, s=(await call('Debugger.getScriptSource',{scriptId:p.scriptId})).result?.scriptSource||'';
  const n=s.indexOf("throw new NativeDOMException('Blocked by Glosh'");if(n<0)return;
  const prefix=s.slice(0,n).split('\n');const line=p.startLine+prefix.length-1;const column=(prefix.length===1?p.startColumn:0)+prefix.at(-1).length;
  console.log(JSON.stringify({breakpointScript:p.scriptId,line,column}));await call('Debugger.setBreakpoint',{location:{scriptId:p.scriptId,lineNumber:line,columnNumber:column}});
 }
 if(r.method==='Debugger.paused'){
  console.log(JSON.stringify({frames:r.params.callFrames.slice(0,7).map(f=>({name:f.functionName,location:f.location,url:f.url}))}));
  const f=r.params.callFrames[1];if(f){const v=await call('Debugger.evaluateOnCallFrame',{callFrameId:f.callFrameId,expression:'JSON.stringify({name:typeof name!=="undefined"?name:null,target:typeof target!=="undefined"?{tag:target?.tagName,id:target?.id}:null,node:typeof node!=="undefined"?{tag:node?.tagName,id:node?.id}:null,self:{tag:this?.tagName,id:this?.id}})',returnByValue:true});console.log(JSON.stringify(v.result||v.error))}
  await call('Debugger.resume');
 }
};
setTimeout(async()=>{await call('Debugger.resume');await call('Debugger.disable');ws.close()},40000);
