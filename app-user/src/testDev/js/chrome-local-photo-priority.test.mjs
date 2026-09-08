import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const source=fs.readFileSync('app-user/src/dev/java/com/contentfilter/user/chromedataplane/ChromeLocalPhotoScript.kt','utf8');
new vm.Script(source.split('\"\"\"')[1]);
const selection=source.slice(source.indexOf('const localPhotoVisible='),source.indexOf('const localPhotoPump='));
const realm=vm.createContext({SELF:{innerHeight:800,innerWidth:400},invoke:(f,o,a)=>Reflect.apply(f,o,a),
 localPhotoRect:function(){if(this.broken)throw Error('detached');return this.rect},
 elementQuery:function(){return this.image},parentOf:e=>e.parentElement,localNameOf:e=>e.localName,localPhotoCurrent:j=>!j.cancelled});
vm.runInContext('let localPhotoQueue=[],localPhotoOvertakes=0;'+selection+';globalThis.choose=()=>localPhotoQueue.splice(localPhotoSelect(),1)[0];globalThis.reset=q=>{localPhotoQueue=q;localPhotoOvertakes=0}',realm);
const job=(id,top=0)=>({id,element:{localName:'img',rect:{top,bottom:top+80,left:0,right:80,width:80,height:80}}});
const old=job('old',900),visible=Array.from({length:8},(_,i)=>job('visible'+i));
realm.reset([old,...visible]);
assert.deepEqual(Array.from({length:5},()=>realm.choose().id),['visible0','visible1','visible2','visible3','old']);
realm.reset([job('first'),job('second')]);assert.equal(realm.choose().id,'first');
const cancelled=job('cancelled');cancelled.cancelled=true;realm.reset([job('offscreen',900),cancelled,job('visible')]);assert.equal(realm.choose().id,'visible');
const sourceJob={id:'picture',element:{localName:'source',parentElement:{image:job('image').element}}};realm.reset([job('offscreen',900),sourceJob]);assert.equal(realm.choose().id,'picture');
const broken=job('broken');broken.element.broken=true;realm.reset([broken,job('visible')]);assert.equal(realm.choose().id,'visible');assert.equal(realm.choose().id,'broken');
console.log('PASS: visible priority, bounded overtaking, FIFO, cancelled work, picture geometry and geometry failure fallback');
