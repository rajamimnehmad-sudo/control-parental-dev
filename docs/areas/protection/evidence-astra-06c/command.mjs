// One bounded CDP operation on an explicitly observed tab; no background protocol overrides.
const [tabId,method,params='{}']=process.argv.slice(2);
const tabs=await(await fetch('http://127.0.0.1:19226/json/list')).json();const tab=tabs.find(t=>t.id===tabId);if(!tab)throw Error('Observed tab missing');
const ws=new WebSocket(tab.webSocketDebuggerUrl),timer=setTimeout(()=>{ws.close();process.exit(1)},10000);
ws.onopen=()=>ws.send(JSON.stringify({id:1,method,params:JSON.parse(params)}));ws.onmessage=e=>{const r=JSON.parse(e.data);if(r.id===1){console.log(JSON.stringify(r));clearTimeout(timer);ws.close()}};
