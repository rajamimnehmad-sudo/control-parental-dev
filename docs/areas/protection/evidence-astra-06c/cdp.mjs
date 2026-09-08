// Bounded A23 CDP helper. Does not change browser settings, intercept traffic, or capture pixels.
import fs from 'node:fs';
const tabs = await (await fetch('http://127.0.0.1:19226/json/list')).json();
const tab = tabs.find(t => t.type === 'page' && (!process.env.GLOSH_TAB_ID || t.id === process.env.GLOSH_TAB_ID) && (!process.env.GLOSH_TAB_URL || t.url.startsWith(process.env.GLOSH_TAB_URL)));
if (!tab) throw Error('No matching existing page');
const ws = new WebSocket(tab.webSocketDebuggerUrl);
const timer = setTimeout(() => { ws.close(); console.error('CDP timed out'); process.exit(1); }, 10000);
const input = fs.readFileSync(0, 'utf8');
ws.onopen = () => ws.send(JSON.stringify({id:1,method:'Runtime.evaluate',params:{expression:input,returnByValue:true,awaitPromise:true,timeout:7000}}));
ws.onmessage = e => { const r=JSON.parse(e.data); if(r.id!==1)return; clearTimeout(timer); console.log(JSON.stringify(r)); ws.close(); };
