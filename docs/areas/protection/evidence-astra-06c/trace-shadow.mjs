// Bounded diagnostic breakpoint at Glosh's own deny(); every pause is resumed.
const tabs=await(await fetch('http://127.0.0.1:19226/json/list')).json();
const tab=tabs.find(t=>t.id==='685');
const ws=new WebSocket(tab.webSocketDebuggerUrl);let id=0;const pending=new Map();
const call=(method,params={})=>new Promise(resolve=>{const n=++id;pending.set(n,resolve);ws.send(JSON.stringify({id:n,method,params}))});
ws.onopen=async()=>{await call('Debugger.enable');await call('Page.reload')};
ws.onmessage=async e=>{const r=JSON.parse(e.data);if(r.id){pending.get(r.id)?.(r);pending.delete(r.id);return}
 if(r.method==='Debugger.scriptParsed'&&r.params.url.startsWith('https://www.mimo.com.ar')){
  const p=r.params, s=(await call('Debugger.getScriptSource',{scriptId:p.scriptId})).result?.scriptSource||'';
  let n=s.indexOf("reportBootstrapFailure(stage,reason);mutationDisconnect");if(n<0)n=s.indexOf("let replaced=false;try{A(O,D,[])");if(n<0)return;
  const prefix=s.slice(0,n).split('\n');const line=p.startLine+prefix.length-1;const column=(prefix.length===1?p.startColumn:0)+prefix.at(-1).length;
  console.log(JSON.stringify({breakpointScript:p.scriptId,line,column}));await call('Debugger.setBreakpoint',{location:{scriptId:p.scriptId,lineNumber:line,columnNumber:column}});
 }
 if(r.method==='Debugger.paused'){
  console.log(JSON.stringify({frames:r.params.callFrames.slice(0,7).map(f=>({name:f.functionName,location:f.location,url:f.url}))}));
  const f=r.params.callFrames[1];if(f){const v=await call('Debugger.evaluateOnCallFrame',{callFrameId:f.callFrameId,expression:'JSON.stringify({connected:this?.isConnected,tag:this?.tagName,hasRoot:typeof root!=="undefined",sheet:typeof shadowSheet!=="undefined"?!!shadowSheet:null})',returnByValue:true});console.log(JSON.stringify(v.result||v.error))}
  await call('Debugger.resume');
 }
};
setTimeout(async()=>{await call('Debugger.resume');await call('Debugger.disable');ws.close()},40000);
