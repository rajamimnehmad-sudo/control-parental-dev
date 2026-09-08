// Diagnostic only. No timings from this debugger run are used as performance evidence.
const tabs=await(await fetch('http://127.0.0.1:19226/json/list')).json();const tab=tabs.find(t=>t.id===process.argv[2]);
const ws=new WebSocket(tab.webSocketDebuggerUrl);let id=0;const pending=new Map();
const call=(method,params={})=>new Promise(resolve=>{const n=++id;pending.set(n,resolve);ws.send(JSON.stringify({id:n,method,params}))});
ws.onopen=async()=>{await call('Debugger.enable');await call('Page.navigate',{url:'https://www.fravega.com/?astra=form-target'})};
ws.onmessage=async e=>{const r=JSON.parse(e.data);if(r.id){pending.get(r.id)?.(r);pending.delete(r.id);return}
 if(r.method==='Debugger.scriptParsed'&&r.params.length>50000){const p=r.params,s=(await call('Debugger.getScriptSource',{scriptId:p.scriptId})).result?.scriptSource||'';
 for(const [needle,offset,condition] of [["if(oneOf(tag,['a','area','form','base'])&&key==='target')",0,"tag==='form'&&key==='target'"],["set:function(){invoke(entry.set,this,['_self'])}",15,"this.tagName==='FORM'"]]){
 const n=s.indexOf(needle);if(n<0)continue;console.log(JSON.stringify({found:p.scriptId,kind:offset}));const prefix=s.slice(0,n).split('\n');await call('Debugger.setBreakpoint',{location:{scriptId:p.scriptId,lineNumber:p.startLine+prefix.length-1,columnNumber:(prefix.length===1?p.startColumn:0)+prefix.at(-1).length+offset},condition})}}

 if(r.method==='Debugger.paused'){
 const f=r.params.callFrames[0];const v=await call('Debugger.evaluateOnCallFrame',{callFrameId:f.callFrameId,expression:'JSON.stringify({tag:this?.tagName,requested:typeof attributeValue!=="undefined"?attributeValue:arguments[0],action:this?.action?new URL(this.action).origin+new URL(this.action).pathname:null})',returnByValue:true});console.log(JSON.stringify({state:v.result?.result,frames:r.params.callFrames.slice(0,5).map(f=>({name:f.functionName,location:f.location}))}));await call('Debugger.resume');
 }
};setTimeout(async()=>{await call('Debugger.resume');await call('Debugger.disable');ws.close()},45000);
