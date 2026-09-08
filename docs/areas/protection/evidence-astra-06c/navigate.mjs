// Select an already observed physical tab, activate it, and await navigation acknowledgement.
const [id,url]=process.argv.slice(2);
const tabs=await(await fetch('http://127.0.0.1:19226/json/list')).json();
const tab=tabs.find(t=>t.id===id);if(!tab)throw Error('Observed tab missing');
const ws=new WebSocket(tab.webSocketDebuggerUrl);
const timer=setTimeout(()=>process.exit(1),15000);
ws.onopen=()=>ws.send(JSON.stringify({id:1,method:'Page.bringToFront'}));
ws.onmessage=e=>{const r=JSON.parse(e.data);if(r.id===1)ws.send(JSON.stringify({id:2,method:'Page.navigate',params:{url}}));if(r.id===2){console.log(JSON.stringify(r));clearTimeout(timer);ws.close()}};
