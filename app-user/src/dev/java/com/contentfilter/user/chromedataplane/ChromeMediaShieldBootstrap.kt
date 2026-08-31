package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract

internal object ChromeMediaShieldBootstrap {
    const val StyleElementId = "glosh-h19-media-shield"
    const val CurtainStyleElementId = "glosh-h19-document-curtain"
    const val CurtainElementId = "glosh-h19-document-curtain-layer"
    const val ParserBarrierCallbackName = "__gloshH19ParserBarrierCommit__"
    const val ParserBarrierGuardName = "__gloshH19ParserBarrierGuard__"
    const val ParserBarrierFailClosedName = "__gloshH19ParserBarrierFailClosed__"
    const val SubdocumentGuardName = "__gloshH19SubdocumentGuard__"

    val css: String =
        "canvas,video,object,embed,frame,fencedframe,[srcdoc],img[src^='data:' i],img[src^='blob:' i]," +
            "img[srcset*='data:' i],img[srcset*='blob:' i],source[src^='data:' i],source[src^='blob:' i]," +
            "source[srcset*='data:' i],source[srcset*='blob:' i],input[type='image' i][src^='data:' i]," +
            "input[type='image' i][src^='blob:' i],iframe:not([data-glosh-network-frame='1'])," +
            "svg:not([data-glosh-icon-safe='1'])," +
            "[data-glosh-media-blocked='1']{visibility:hidden!important;opacity:0!important}" +
            "svg[data-glosh-icon-safe='1']{max-width:96px!important;max-height:96px!important;overflow:hidden!important;" +
            "transform:none!important;filter:none!important;mask:none!important;clip-path:none!important}"

    val curtainCss: String =
        "html,body{background:#202124!important}" +
            "body>*{visibility:hidden!important;opacity:0!important}"

    fun script(
        readyToken: String,
        styleNonce: String,
        topLevel: Boolean = true,
    ): String =
        ScriptTemplate
            .replace(ReadyTokenPlaceholder, readyToken)
            .replace(NoncePlaceholder, styleNonce)
            .replace(ShieldCssPlaceholder, jsString(css))
            .replace(ReadyUrlPlaceholder, ChromePhotosDataPlaneLabContract.MediaShieldReadyUrl)
            .replace(ParserBarrierUrlPlaceholder, ChromePhotosDataPlaneLabContract.MediaShieldParserBarrierUrl)
            .replace(TopLevelPlaceholder, topLevel.toString())

    fun parserBarrierGuardScript(): String = completionGuardScript(ParserBarrierGuardName)

    fun subdocumentGuardScript(): String = completionGuardScript(SubdocumentGuardName)

    private fun completionGuardScript(callbackName: String): String =
        "(()=>{'use strict';const S=self;let fail=null,ok=false;try{fail=S.$ParserBarrierFailClosedName}catch(_){}" +
            "try{const f=S.$callbackName;ok=typeof f==='function'&&f()===true}catch(_){}if(ok){try{" +
            "if(typeof fail!=='function'||typeof fail.retire!=='function'||fail.retire()!==true)throw 0;" +
            "if(!delete S.$ParserBarrierFailClosedName||Object.getOwnPropertyDescriptor(S,'$ParserBarrierFailClosedName'))throw 0;" +
            "return}catch(_){}}try{if(typeof fail==='function')fail()}catch(_){}})();"

    /** Independent early fallback that survives a partial/runtime failure in the main prelude. */
    fun parserBarrierFailClosedInstallerScript(): String =
        "(()=>{'use strict';const S=self,D=document,SCRIPT=D.currentScript,P=Document.prototype,A=Reflect.apply," +
            "O=P.open,W=P.write,C=P.close,T=S.stop,RA=Element.prototype.removeAttribute," +
            "RC=Node.prototype.removeChild,HA=Element.prototype.hasAttribute,B='<!doctype html><html><head><meta charset=\"utf-8\">" +
            "<style>html,body{margin:0;background:#202124;color:#fff;font:16px sans-serif}</style></head>" +
            "<body>Glosh protected this document.</body></html>';const fail=()=>{let replaced=false;" +
            "try{A(O,D,[]);A(W,D,[B]);A(C,D,[]);replaced=true}catch(_){}try{A(T,S,[])}catch(_){}" +
            "if(!replaced){try{const R=D.documentElement;R.textContent='';R.setAttribute('data-glosh-h19-fail-closed','true')}catch(_){}}" +
            "return false};const retire=()=>{try{if(!SCRIPT)return false;A(RA,SCRIPT,['nonce']);const parent=SCRIPT.parentNode;" +
            "if(!parent)return false;A(RC,parent,[SCRIPT]);return SCRIPT.isConnected===false&&!A(HA,SCRIPT,['nonce'])}catch(_){return false}};" +
            "try{Object.defineProperty(fail,'retire',{value:retire,writable:false,enumerable:false,configurable:false});" +
            "Object.defineProperty(S,'$ParserBarrierFailClosedName'," +
            "{value:fail,writable:false,enumerable:false,configurable:true})}catch(_){fail()}})();"

    private fun jsString(value: String): String =
        buildString(value.length + 16) {
            value.forEach { character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '\'' -> append("\\'")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    else -> append(character)
                }
            }
        }

    private const val ReadyTokenPlaceholder = "__GLOSH_READY_TOKEN__"
    private const val NoncePlaceholder = "__GLOSH_NONCE__"
    private const val ShieldCssPlaceholder = "__GLOSH_SHIELD_CSS__"
    private const val ReadyUrlPlaceholder = "__GLOSH_READY_URL__"
    private const val ParserBarrierUrlPlaceholder = "__GLOSH_PARSER_BARRIER_URL__"
    private const val TopLevelPlaceholder = "__GLOSH_TOP_LEVEL__"
    private val ScriptTemplate =
        """
        (()=>{'use strict';
        const READY='__GLOSH_READY_TOKEN__',NONCE='__GLOSH_NONCE__',TOP_LEVEL=__GLOSH_TOP_LEVEL__,READY_URL='__GLOSH_READY_URL__',BARRIER_URL='__GLOSH_PARSER_BARRIER_URL__';
        const STYLE_ID='glosh-h19-media-shield',CURTAIN_ID='glosh-h19-document-curtain',CURTAIN_LAYER_ID='glosh-h19-document-curtain-layer';
        const CSS='__GLOSH_SHIELD_CSS__',FRAME_SANDBOX='allow-scripts allow-forms allow-popups-to-escape-sandbox';let installed=true;
        const SELF=self,DOC=document,NAV=navigator,IS_TOP_LEVEL=SELF===SELF.top,BOOTSTRAP_SCRIPT=DOC.currentScript,NativeObject=Object,NativeReflect=Reflect;
        const ReflectApply=NativeReflect.apply,ReflectDefine=NativeReflect.defineProperty,ReflectDelete=NativeReflect.deleteProperty,ReflectSet=NativeReflect.set,ReflectSetPrototype=NativeReflect.setPrototypeOf,ReflectPreventExtensions=NativeReflect.preventExtensions;
        const ObjectDefine=NativeObject.defineProperty,ObjectDefineProperties=NativeObject.defineProperties,ObjectAssign=NativeObject.assign,ObjectDescribe=NativeObject.getOwnPropertyDescriptor;
        const ObjectSetPrototype=NativeObject.setPrototypeOf,ObjectPreventExtensions=NativeObject.preventExtensions,ObjectSeal=NativeObject.seal,ObjectFreeze=NativeObject.freeze;
        const ObjectPrototype=NativeObject.prototype,ObjectGetPrototype=NativeObject.getPrototypeOf,ObjectHasOwn=ObjectPrototype.hasOwnProperty;
        const ObjectDefineGetter=ObjectPrototype.__defineGetter__,ObjectDefineSetter=ObjectPrototype.__defineSetter__;
        const NativeString=String,NativeNumber=Number,NativeURL=URL,NativeDOMException=DOMException,NativeArray=Array,NativeEvent=Event,NativePageTransitionEvent=SELF.PageTransitionEvent;
        const NativePageSwapEvent=SELF.PageSwapEvent,NativePageRevealEvent=SELF.PageRevealEvent,NativeViewTransition=SELF.ViewTransition,NativeMutationObserver=MutationObserver;
        const NativeLocation=SELF.location,NativeLocationReloadFunction=NativeLocation&&NativeLocation.reload;
        const NativeXMLHttpRequest=SELF.XMLHttpRequest,NativeHasFocusFunction=Document.prototype.hasFocus,NativeElementFocusFunction=SELF.HTMLElement&&HTMLElement.prototype.focus;
        const NumberIsSafeInteger=Number.isSafeInteger;
        const StringLower=String.prototype.toLowerCase,StringIncludes=String.prototype.includes,StringTrim=String.prototype.trim,StringLastIndex=String.prototype.lastIndexOf,StringSlice=String.prototype.slice,StringCharCodeAt=String.prototype.charCodeAt;
        const SetHas=Set.prototype.has,SetAdd=Set.prototype.add,WeakSetHas=WeakSet.prototype.has,WeakSetAdd=WeakSet.prototype.add,WeakSetDelete=WeakSet.prototype.delete;
        const WeakMapGet=WeakMap.prototype.get,WeakMapSet=WeakMap.prototype.set,EventPrevent=Event.prototype.preventDefault,EventStopImmediate=Event.prototype.stopImmediatePropagation;
        const invoke=(fn,owner,args)=>ReflectApply(fn,owner,args),method=(fn)=>({call:(owner,...args)=>invoke(fn,owner,args),apply:(owner,args)=>invoke(fn,owner,args)});
        const nativeLocationReload=NativeLocationReloadFunction?method(NativeLocationReloadFunction):null;
        const nativeHasFocus=NativeHasFocusFunction?method(NativeHasFocusFunction):null;
        const nativeElementFocus=NativeElementFocusFunction?method(NativeElementFocusFunction):null;
        const nativeDialogShowModal=SELF.HTMLDialogElement&&HTMLDialogElement.prototype.showModal?method(HTMLDialogElement.prototype.showModal):null;
        const nativeDialogShow=SELF.HTMLDialogElement&&HTMLDialogElement.prototype.show?method(HTMLDialogElement.prototype.show):null;
        const nativeDialogClose=SELF.HTMLDialogElement&&HTMLDialogElement.prototype.close?method(HTMLDialogElement.prototype.close):null;
        const nativeSkipViewTransition=NativeViewTransition&&NativeViewTransition.prototype.skipTransition?method(NativeViewTransition.prototype.skipTransition):null;
        const xhrOpen=NativeXMLHttpRequest?method(NativeXMLHttpRequest.prototype.open):null;
        const xhrSend=NativeXMLHttpRequest?method(NativeXMLHttpRequest.prototype.send):null,xhrSetHeader=NativeXMLHttpRequest?method(NativeXMLHttpRequest.prototype.setRequestHeader):null;
        const descriptor=(owner,name)=>ObjectDescribe(owner,name),propertyOwner=(value,name)=>{let owner=value;while(owner&&!invoke(ObjectHasOwn,owner,[name]))owner=ObjectGetPrototype(owner);return owner};
        const propertyDescriptor=(value,name)=>{const owner=propertyOwner(value,name);return owner?descriptor(owner,name):null};
        const read=(entry,value)=>entry&&entry.get?invoke(entry.get,value,[]):undefined,stringOf=(value)=>NativeString(value),lower=(value)=>invoke(StringLower,stringOf(value),[]);
        const includes=(value,needle)=>invoke(StringIncludes,stringOf(value),[needle]),trim=(value)=>invoke(StringTrim,stringOf(value),[]),lastIndex=(value,needle)=>invoke(StringLastIndex,stringOf(value),[needle]);
        const slice=(value,start,end)=>invoke(StringSlice,stringOf(value),end===undefined?[start]:[start,end]),charCode=(value,index)=>invoke(StringCharCodeAt,stringOf(value),[index]);
        const put=(array,index,value)=>{ObjectDefine(array,index,{value,writable:true,enumerable:true,configurable:true});return value};
        const append=(array,value)=>put(array,array.length,value);
        const whitespaceParts=(value)=>{const text=trim(value),parts=[];let current='';for(let index=0;index<text.length;index+=1){const character=text[index];
        if(character===' '||character==='\t'||character==='\n'||character==='\r'||character==='\f'){if(current){append(parts,current);current=''}}else current+=character}
        if(current)append(parts,current);return parts};
        const oneOf=(value,choices)=>{for(let index=0;index<choices.length;index+=1)if(value===choices[index])return true;return false};
        const copyList=(value,lengthGetter)=>{const output=[];if(!value)return output;const length=lengthGetter?read(lengthGetter,value):value.length;
        if(!NumberIsSafeInteger(length)||length<0||length>10000)deny();for(let index=0;index<length;index+=1)put(output,index,value[index]);return output};
        const deny=()=>{throw new NativeDOMException('Blocked by Glosh','SecurityError')};
        const seal=(owner,name,value)=>{try{ObjectDefine(owner,name,{value,writable:false,configurable:false});
        const d=descriptor(owner,name);return !!d&&d.value===value&&!d.configurable}catch(_){return false}};
        const nativeSet=method(Element.prototype.setAttribute),nativeGet=method(Element.prototype.getAttribute),nativeHas=method(Element.prototype.hasAttribute);
        const nativeRemove=method(Element.prototype.removeAttribute),nativeToggle=method(Element.prototype.toggleAttribute);
        const nativeGetNode=method(Element.prototype.getAttributeNode),nativeGetNodeNS=method(Element.prototype.getAttributeNodeNS);
        const elementQuery=method(Element.prototype.querySelector),elementQueryAll=method(Element.prototype.querySelectorAll),elementClosest=method(Element.prototype.closest);
        const fragmentQueryAll=method(DocumentFragment.prototype.querySelectorAll),nativeAddEvent=method(EventTarget.prototype.addEventListener),nativeDispatchEvent=method(EventTarget.prototype.dispatchEvent);
        const nodeAppend=method(Node.prototype.appendChild),nodeInsert=method(Node.prototype.insertBefore),nodeReplace=method(Node.prototype.replaceChild),nodeRemove=method(Node.prototype.removeChild);
        const nativeCreateElement=method(Document.prototype.createElement);
        const nodeContains=method(Node.prototype.contains),nodeValue=descriptor(Node.prototype,'nodeValue');
        const nativeDocOpen=method(Document.prototype.open),nativeDocWrite=method(Document.prototype.write),nativeDocClose=method(Document.prototype.close);
        const nativeStyleSet=method(CSSStyleDeclaration.prototype.setProperty),nativeStyleRemove=method(CSSStyleDeclaration.prototype.removeProperty);
        const nativeStyleGet=method(CSSStyleDeclaration.prototype.getPropertyValue),nativeStylePriority=method(CSSStyleDeclaration.prototype.getPropertyPriority);
        const mutationObserve=method(MutationObserver.prototype.observe),mutationDisconnect=method(MutationObserver.prototype.disconnect);
        const nodeText=descriptor(Node.prototype,'textContent'),nodeTypeProperty=propertyDescriptor(DOC,'nodeType'),localNameProperty=propertyDescriptor(DOC.documentElement,'localName');
        const parentNodeProperty=propertyDescriptor(DOC.documentElement,'parentNode'),firstChildProperty=propertyDescriptor(DOC.documentElement,'firstChild');
        const isConnectedProperty=propertyDescriptor(DOC.documentElement,'isConnected'),baseUriProperty=propertyDescriptor(DOC,'baseURI');
        const styleProperty=propertyDescriptor(DOC.documentElement,'style'),elementAttributesProperty=propertyDescriptor(DOC.documentElement,'attributes');
        const documentElementProperty=propertyDescriptor(DOC,'documentElement'),documentHeadProperty=propertyDescriptor(DOC,'head');
        const currentScriptProperty=propertyDescriptor(DOC,'currentScript'),scriptSrcProperty=self.HTMLScriptElement?propertyDescriptor(HTMLScriptElement.prototype,'src'):null;
        const visibilityProperty=propertyDescriptor(DOC,'visibilityState'),eventTargetProperty=propertyDescriptor(new NativeEvent('glosh'),'target');
        const eventTrustedProperty=propertyDescriptor(new NativeEvent('glosh'),'isTrusted');
        const pagePersistedProperty=NativePageTransitionEvent?propertyDescriptor(NativePageTransitionEvent.prototype,'persisted'):null;
        const pageSwapTransitionProperty=NativePageSwapEvent?propertyDescriptor(NativePageSwapEvent.prototype,'viewTransition'):null;
        const pageRevealTransitionProperty=NativePageRevealEvent?propertyDescriptor(NativePageRevealEvent.prototype,'viewTransition'):null;
        const xhrStatusProperty=NativeXMLHttpRequest?propertyDescriptor(NativeXMLHttpRequest.prototype,'status'):null;
        const xhrResponseUrlProperty=NativeXMLHttpRequest?propertyDescriptor(NativeXMLHttpRequest.prototype,'responseURL'):null;
        const xhrResponseTextProperty=NativeXMLHttpRequest?propertyDescriptor(NativeXMLHttpRequest.prototype,'responseText'):null;
        const templateContentProperty=self.HTMLTemplateElement?propertyDescriptor(HTMLTemplateElement.prototype,'content'):null,nativeStop=SELF.stop;
        const mutationTypeProperty=self.MutationRecord?propertyDescriptor(MutationRecord.prototype,'type'):null;
        const mutationTargetProperty=self.MutationRecord?propertyDescriptor(MutationRecord.prototype,'target'):null;
        const mutationAddedProperty=self.MutationRecord?propertyDescriptor(MutationRecord.prototype,'addedNodes'):null;
        const nodeListLength=self.NodeList?propertyDescriptor(NodeList.prototype,'length'):null,namedMapLength=self.NamedNodeMap?propertyDescriptor(NamedNodeMap.prototype,'length'):null;
        const attrNameProperty=self.Attr?propertyDescriptor(Attr.prototype,'name'):null,attrValueProperty=self.Attr?propertyDescriptor(Attr.prototype,'value'):null;
        const attrNamespaceProperty=self.Attr?propertyDescriptor(Attr.prototype,'namespaceURI'):null,attrLocalNameProperty=self.Attr?propertyDescriptor(Attr.prototype,'localName'):null;
        const attrOwnerProperty=self.Attr?propertyDescriptor(Attr.prototype,'ownerElement'):null,urlProtocolProperty=propertyDescriptor(new NativeURL('https://glosh.invalid/'),'protocol');
        const iframeSandboxProperty=self.HTMLIFrameElement?propertyDescriptor(HTMLIFrameElement.prototype,'sandbox'):null;
        const htmlHiddenProperty=SELF.HTMLElement?propertyDescriptor(HTMLElement.prototype,'hidden'):null;
        const htmlTitleProperty=SELF.HTMLElement?propertyDescriptor(HTMLElement.prototype,'title'):null;
        const dialogOpenProperty=SELF.HTMLDialogElement?propertyDescriptor(HTMLDialogElement.prototype,'open'):null;
        const dialogClosedByProperty=SELF.HTMLDialogElement?propertyDescriptor(HTMLDialogElement.prototype,'closedBy'):null;
        const attributeStyleMapProperty=self.StylePropertyMap?propertyDescriptor(DOC.documentElement,'attributeStyleMap'):null;
        const requiredPrimordials=[nodeText,nodeValue,nodeTypeProperty,localNameProperty,parentNodeProperty,firstChildProperty,isConnectedProperty,baseUriProperty,styleProperty,
        elementAttributesProperty,documentElementProperty,documentHeadProperty,visibilityProperty,eventTargetProperty,eventTrustedProperty,mutationTypeProperty,mutationTargetProperty,mutationAddedProperty,
        nodeListLength,namedMapLength,urlProtocolProperty,templateContentProperty,htmlHiddenProperty,htmlTitleProperty,currentScriptProperty,scriptSrcProperty];
        if(TOP_LEVEL&&(!NativeXMLHttpRequest||!xhrOpen||!xhrSend||!xhrSetHeader||!xhrStatusProperty||!xhrResponseUrlProperty||!xhrResponseTextProperty||!nativeHasFocus||!nativeElementFocus||!nativeLocationReload||!nativeDialogShowModal||!nativeDialogShow||!nativeDialogClose||!htmlHiddenProperty||!dialogOpenProperty))installed=false;
        for(let index=0;index<requiredPrimordials.length;index+=1)if(!requiredPrimordials[index])installed=false;
        if(self.Attr&&(!attrNameProperty||!attrValueProperty||!attrNamespaceProperty||!attrLocalNameProperty||!attrOwnerProperty))installed=false;
        if(NativePageTransitionEvent&&!pagePersistedProperty)installed=false;
        if((NativePageSwapEvent&&(!pageSwapTransitionProperty||!nativeSkipViewTransition))||(NativePageRevealEvent&&(!pageRevealTransitionProperty||!nativeSkipViewTransition)))installed=false;
        if(self.HTMLIFrameElement&&!iframeSandboxProperty)installed=false;if(self.StylePropertyMap&&!attributeStyleMapProperty)installed=false;
        const nodeTypeOf=(value)=>read(nodeTypeProperty,value),localNameOf=(value)=>lower(read(localNameProperty,value)||'');
        const parentOf=(value)=>read(parentNodeProperty,value),firstChildOf=(value)=>read(firstChildProperty,value),connected=(value)=>read(isConnectedProperty,value)===true;
        const styleOf=(value)=>read(styleProperty,value),attributesOf=(value)=>copyList(read(elementAttributesProperty,value),namedMapLength);
        const documentElement=()=>read(documentElementProperty,DOC),documentHead=()=>read(documentHeadProperty,DOC);
        const templateContent=(value)=>read(templateContentProperty,value);
        const visibilityState=()=>read(visibilityProperty,DOC),hasNativeFocus=()=>nativeHasFocus&&nativeHasFocus.call(DOC)===true;
        const activeDocument=()=>TOP_LEVEL&&IS_TOP_LEVEL&&visibilityState()==='visible'&&hasNativeFocus();
        const eventTarget=(event)=>read(eventTargetProperty,event),trustedEvent=(event)=>read(eventTrustedProperty,event)===true;
        const persistedPage=(event)=>pagePersistedProperty&&read(pagePersistedProperty,event)===true;
        const attrName=(attribute)=>stringOf(read(attrNameProperty,attribute)||''),attrValueOf=(attribute)=>stringOf(read(attrValueProperty,attribute)||'');
        const attrNamespace=(attribute)=>read(attrNamespaceProperty,attribute),attrLocalName=(attribute)=>stringOf(read(attrLocalNameProperty,attribute)||'');
        const attrOwner=(attribute)=>read(attrOwnerProperty,attribute),sandboxOf=(frame)=>read(iframeSandboxProperty,frame);
        const lockedSandboxes=new WeakSet(),protectedNodes=new WeakSet(),protectedIconNodes=new WeakSet(),protectedMediaNodes=new WeakSet(),protectedStyleMaps=new WeakSet(),protectedSheets=new WeakSet(),protectedMedias=new WeakSet(),guardedStyleOwners=new WeakSet(),protectedDescendants=new WeakMap();
        const styleOwners=new WeakMap(),styleMapOwners=new WeakMap(),attributeOwners=new WeakMap(),guardedRuleMethodOwners=new WeakMap();
        if(!BOOTSTRAP_SCRIPT)installed=false;
        const watchStyle=(element)=>{try{invoke(WeakMapSet,styleOwners,[styleOf(element),element]);if(attributeStyleMapProperty){const map=read(attributeStyleMapProperty,element);
        if(map)invoke(WeakMapSet,styleMapOwners,[map,element])}}catch(_){}};
        const hide=(element)=>{if(!element||nodeTypeOf(element)!==1)return;invoke(WeakSetAdd,protectedMediaNodes,[element]);const style=styleOf(element);watchStyle(element);if(nativeGet.call(element,'data-glosh-media-blocked')!=='1')nativeSet.call(element,'data-glosh-media-blocked','1');
        if(nativeStyleGet.call(style,'visibility')!=='hidden'||nativeStylePriority.call(style,'visibility')!=='important')nativeStyleSet.call(style,'visibility','hidden','important');
        if(nativeStyleGet.call(style,'opacity')!=='0'||nativeStylePriority.call(style,'opacity')!=='important')nativeStyleSet.call(style,'opacity','0','important')};
        const unhide=(element)=>{if(nativeGet.call(element,'data-glosh-media-blocked')==='1'){nativeRemove.call(element,'data-glosh-media-blocked');
        const style=styleOf(element);nativeStyleRemove.call(style,'visibility');nativeStyleRemove.call(style,'opacity')}invoke(WeakSetDelete,protectedMediaNodes,[element])};
        const networkUrl=(value)=>{if(!value)return false;try{const u=new NativeURL(stringOf(value),read(baseUriProperty,DOC));const protocol=read(urlProtocolProperty,u);
        return protocol==='https:'||protocol==='http:'}catch(_){return false}};
        const TOP_LAYER_ATTRIBUTE_NAMES=['popover','popovertarget','popovertargetaction','commandfor','command'],TOP_LAYER_ATTRIBUTES=new Set(TOP_LAYER_ATTRIBUTE_NAMES);
        const ICON_TAGS=new Set(['path','title','desc']),ICON_ROOT_ATTRS=new Set(['xmlns','width','height','viewbox','preserveaspectratio','role','aria-hidden','aria-label','focusable','id','class','data-glosh-icon-safe','data-glosh-media-blocked','style']);
        const ICON_PATH_ATTRS=new Set(['d','fill-rule','id','class','style']),ICON_TEXT_ATTRS=new Set(['id','class']),ICON_PATH_CHARS='MmLlHhVvCcSsQqTtAaZz0123456789+-.eE, \t\n\r\f';
        const iconAttributeAllowed=(node,name,svg)=>{const tag=localNameOf(node);if(tag==='svg')return invoke(SetHas,ICON_ROOT_ATTRS,[name])&&(name!=='style'||
        nativeGet.call(svg,'data-glosh-media-blocked')==='1'||invoke(WeakSetHas,protectedIconNodes,[node]));if(tag==='path')return invoke(SetHas,ICON_PATH_ATTRS,[name])&&
        (name!=='style'||invoke(WeakSetHas,protectedIconNodes,[node]));return invoke(SetHas,ICON_TEXT_ATTRS,[name])};
        const validIconPath=(value)=>{if(!value||value.length>1024)return false;for(let index=0;index<value.length;index+=1)if(!includes(ICON_PATH_CHARS,value[index]))return false;return true};
        const iconDimensions=(svg)=>{let width=NativeNumber(nativeGet.call(svg,'width')),height=NativeNumber(nativeGet.call(svg,'height'));
        if(!(width>0&&height>0)){const viewBox=whitespaceParts(nativeGet.call(svg,'viewBox')||'');if(viewBox.length===4){width=NativeNumber(viewBox[2]);height=NativeNumber(viewBox[3])}}
        return width>0&&height>0&&width<=96&&height<=96?[width,height]:null};
        const safeIcon=(svg)=>{try{const descendants=copyList(elementQueryAll.call(svg,'*'),nodeListLength);for(let index=0;index<descendants.length;index+=1)
        if(!invoke(SetHas,ICON_TAGS,[localNameOf(descendants[index])]))return false;const nodes=[svg];for(let index=0;index<descendants.length;index+=1)append(nodes,descendants[index]);
        let attributeBytes=0,pathBytes=0,pathCount=0;for(let index=0;index<nodes.length;index+=1){const node=nodes[index],attrs=attributesOf(node);for(let attributeIndex=0;attributeIndex<attrs.length;attributeIndex+=1){
        const name=lower(attrName(attrs[attributeIndex]));if(!iconAttributeAllowed(node,name,svg))return false;attributeBytes+=name.length+attrValueOf(attrs[attributeIndex]).length}
        if(localNameOf(node)==='path'){pathCount+=1;const d=nativeGet.call(node,'d')||'';if(!validIconPath(d))return false;pathBytes+=d.length}}
        if(descendants.length>18||attributeBytes>4096||pathCount<1||pathCount>16||pathBytes>2048||!iconDimensions(svg))return false;
        return !elementQuery.call(svg,'[href],[xlink\\:href],[filter],[mask],[clip-path],[fill^="url(" i],[stroke^="url(" i]')}
        catch(_){return false}};
        const lockIconGeometry=(svg)=>{try{const dimensions=iconDimensions(svg);if(!dimensions)return false;const nodes=[svg],descendants=copyList(elementQueryAll.call(svg,'*'),nodeListLength);
        for(let index=0;index<descendants.length;index+=1)append(nodes,descendants[index]);for(let index=0;index<nodes.length;index+=1)invoke(WeakSetAdd,protectedIconNodes,[nodes[index]]);
        nativeRemove.call(svg,'style');nativeRemove.call(svg,'data-glosh-media-blocked');const rootStyle=styleOf(svg);watchStyle(svg);const width=stringOf(dimensions[0])+'px',height=stringOf(dimensions[1])+'px';
        const rootRules=[['all','initial'],['display','inline-block'],['box-sizing','border-box'],['width',width],['height',height],['min-width','0'],['min-height','0'],['max-width',width],['max-height',height],
        ['margin','0'],['padding','0'],['position','static'],['inset','auto'],['zoom','1'],['scale','none'],['rotate','none'],['translate','none'],['transform','none'],['transform-origin','center'],
        ['filter','none'],['mask','none'],['clip-path','none'],['animation','none'],['transition','none'],['background','none'],['box-shadow','none'],['border','0'],['overflow','hidden'],['contain','paint'],
        ['opacity','1'],['visibility','visible'],['pointer-events','none'],['color','#000'],['direction','ltr'],['unicode-bidi','normal']];for(let index=0;index<rootRules.length;index+=1)
        nativeStyleSet.call(rootStyle,rootRules[index][0],rootRules[index][1],'important');for(let index=0;index<descendants.length;index+=1){const node=descendants[index],tag=localNameOf(node),style=styleOf(node);watchStyle(node);nativeRemove.call(node,'style');
        if(tag==='path'){const d=nativeGet.call(node,'d')||'',fillRule=lower(nativeGet.call(node,'fill-rule')||'nonzero');const rules=[['all','initial'],['display','inline'],['d','path("'+d+'")'],['fill','currentcolor'],['stroke','none'],
        ['fill-rule',fillRule==='evenodd'?'evenodd':'nonzero'],['opacity','1'],['transform','none'],['filter','none'],['clip-path','none'],['mask','none'],['animation','none'],['transition','none']];for(let ruleIndex=0;ruleIndex<rules.length;ruleIndex+=1)
        nativeStyleSet.call(style,rules[ruleIndex][0],rules[ruleIndex][1],'important')}else{nativeStyleSet.call(style,'all','initial','important');nativeStyleSet.call(style,'display','none','important')}}
        nativeSet.call(svg,'data-glosh-icon-safe','1');return nativeStylePriority.call(rootStyle,'all')==='important'&&nativeStyleGet.call(rootStyle,'width')===width&&nativeStylePriority.call(rootStyle,'width')==='important'}
        catch(_){return false}};
        const removeDeclarativeTopLayer=(element)=>{for(let index=0;index<TOP_LAYER_ATTRIBUTE_NAMES.length;index+=1){const attribute=TOP_LAYER_ATTRIBUTE_NAMES[index];
        if(nativeHas.call(element,attribute))nativeRemove.call(element,attribute)}};
        const sanitizeElement=(element)=>{removeDeclarativeTopLayer(element);const tag=localNameOf(element);
        if(tag==='canvas'||tag==='video'){hide(element);return}
        if(tag==='object'||tag==='embed'){hide(element);nativeRemove.call(element,'data');nativeRemove.call(element,'src');nativeRemove.call(element,'type');
        const parent=parentOf(element);if(parent)nodeRemove.call(parent,element);return}
        if(tag==='frame'||tag==='fencedframe'){hide(element);nativeRemove.call(element,'src');const parent=parentOf(element);if(parent)nodeRemove.call(parent,element);return}
        if(tag==='iframe'){nativeSet.call(element,'sandbox',FRAME_SANDBOX);invoke(WeakSetAdd,lockedSandboxes,[sandboxOf(element)]);
        if(nativeHas.call(element,'srcdoc'))nativeRemove.call(element,'srcdoc');
        if(!networkUrl(nativeGet.call(element,'src'))){nativeRemove.call(element,'data-glosh-network-frame');nativeSet.call(element,'src','about:blank');hide(element)}
        else{nativeSet.call(element,'data-glosh-network-frame','1');unhide(element)}return}
        if(tag==='img'||tag==='source'||(tag==='input'&&lower(nativeGet.call(element,'type')||'')==='image')){const values=lower((nativeGet.call(element,'src')||'')+' '+(nativeGet.call(element,'srcset')||''));
        const structurallyBlocked=tag==='source'&&(nativeHas.call(element,'data-glosh-blocked-src')||nativeHas.call(element,'data-glosh-blocked-srcset'));
        if(structurallyBlocked||includes(values,'data:')||includes(values,'blob:')){nativeRemove.call(element,'src');nativeRemove.call(element,'srcset');hide(element)}else unhide(element)}
        const style=lower(nativeGet.call(element,'style')||'');if(includes(style,'url(data:')||includes(style,'url(blob:'))hide(element);
        if(tag==='svg'){if(safeIcon(element)&&lockIconGeometry(element)){}
        else{nativeRemove.call(element,'data-glosh-icon-safe');let child=firstChildOf(element);while(child){nodeRemove.call(element,child);child=firstChildOf(element)}hide(element)}}};
        const scan=(root)=>{if(!root)return;const type=nodeTypeOf(root);if(type===1)sanitizeElement(root);const query=type===1?elementQueryAll:fragmentQueryAll;
        if(query){const nodes=copyList(query.call(root,'canvas,video,object,embed,frame,fencedframe,iframe,img,source,input[type="image" i],svg,[srcdoc],[style],[popover],[popovertarget],[popovertargetaction],[commandfor],[command]'),nodeListLength);
        for(let index=0;index<nodes.length;index+=1)sanitizeElement(nodes[index])}};
        const sanitizeContainer=(node)=>{if(!node||nodeTypeOf(node)!==1)return;sanitizeElement(node);const svg=elementClosest.call(node,'svg');if(svg&&svg!==node)sanitizeElement(svg)};
        const shieldStyle=DOC.getElementById(STYLE_ID),curtainStyle=TOP_LEVEL?DOC.getElementById(CURTAIN_ID):null,curtainLayer=TOP_LEVEL?nativeCreateElement.call(DOC,'dialog'):null;
        if(curtainLayer){nativeSet.call(curtainLayer,'id',CURTAIN_LAYER_ID);nativeSet.call(curtainLayer,'tabindex','-1');nodeInsert.call(documentElement(),curtainLayer,firstChildOf(documentElement()))}
        if(!shieldStyle||(TOP_LEVEL&&(!curtainStyle||!curtainLayer)))installed=false;else{watchStyle(shieldStyle);if(curtainStyle)watchStyle(curtainStyle);if(curtainLayer)watchStyle(curtainLayer)}
        const styleSheetProperty=shieldStyle?propertyDescriptor(shieldStyle,'sheet'):null,styleNonceProperty=shieldStyle?propertyDescriptor(shieldStyle,'nonce'):null;
        const styleElementDisabledProperty=shieldStyle?propertyDescriptor(shieldStyle,'disabled'):null,styleElementMediaProperty=shieldStyle?propertyDescriptor(shieldStyle,'media'):null;
        const styleElementTypeProperty=shieldStyle?propertyDescriptor(shieldStyle,'type'):null;
        const cssParentRuleProperty=propertyDescriptor(CSSStyleDeclaration.prototype,'parentRule');
        const cssParentSheetProperty=self.CSSRule?propertyDescriptor(CSSRule.prototype,'parentStyleSheet'):null;
        const styleSheetDisabledProperty=self.StyleSheet?propertyDescriptor(StyleSheet.prototype,'disabled'):null;
        const styleSheetMediaProperty=self.StyleSheet?propertyDescriptor(StyleSheet.prototype,'media'):null;
        const mediaTextProperty=self.MediaList?propertyDescriptor(MediaList.prototype,'mediaText'):null;
        const cssRulesProperty=self.CSSStyleSheet?propertyDescriptor(CSSStyleSheet.prototype,'cssRules'):null;
        const cssRuleListLength=self.CSSRuleList?propertyDescriptor(CSSRuleList.prototype,'length'):null;
        const ruleStyleProperty=self.CSSStyleRule?propertyDescriptor(CSSStyleRule.prototype,'style'):null;
        const ruleStyleMapProperty=self.CSSStyleRule?propertyDescriptor(CSSStyleRule.prototype,'styleMap'):null;
        const rangeAncestorProperty=self.Range?propertyDescriptor(Range.prototype,'commonAncestorContainer'):null;
        const selectionRangeCountProperty=self.Selection?propertyDescriptor(Selection.prototype,'rangeCount'):null;
        const selectionGetRangeAt=self.Selection&&Selection.prototype.getRangeAt?method(Selection.prototype.getRangeAt):null;
        const documentGetSelection=Document.prototype.getSelection?method(Document.prototype.getSelection):null;
        const shieldSheet=shieldStyle?read(styleSheetProperty,shieldStyle):null,curtainSheet=curtainStyle?read(styleSheetProperty,curtainStyle):null;
        const registerProtectedSheet=(sheet)=>{if(!sheet)return;invoke(WeakSetAdd,protectedSheets,[sheet]);if(styleSheetMediaProperty){const media=read(styleSheetMediaProperty,sheet);
        if(media)invoke(WeakSetAdd,protectedMedias,[media])}if(!cssRulesProperty||!cssRuleListLength||!ruleStyleMapProperty)return;
        const rules=copyList(read(cssRulesProperty,sheet),cssRuleListLength);for(let index=0;index<rules.length;index+=1){const map=read(ruleStyleMapProperty,rules[index]);
        if(map)invoke(WeakSetAdd,protectedStyleMaps,[map])}};
        if(!styleSheetProperty||!shieldSheet||(TOP_LEVEL&&!curtainSheet)||!styleNonceProperty||!styleNonceProperty.get||!styleNonceProperty.set||
        !styleElementDisabledProperty||!styleElementMediaProperty||!styleElementTypeProperty||!cssParentRuleProperty||!cssParentSheetProperty||
        !styleSheetDisabledProperty||!styleSheetMediaProperty||!mediaTextProperty||(self.CSSStyleRule&&!ruleStyleProperty)||(self.Range&&!rangeAncestorProperty))installed=false;
        const clearStyleNonce=(style)=>{try{invoke(styleNonceProperty.set,style,['']);nativeRemove.call(style,'nonce');
        return read(styleNonceProperty,style)===''&&!nativeHas.call(style,'nonce')}catch(_){return false}};
        let curtainRequired=TOP_LEVEL;
        const protectedNode=(node)=>!!node&&invoke(WeakSetHas,protectedNodes,[node]);
        const protectedInlineStyle=(element)=>protectedNode(element)||invoke(WeakSetHas,protectedIconNodes,[element])||invoke(WeakSetHas,protectedMediaNodes,[element])||nativeGet.call(element,'data-glosh-media-blocked')==='1'||nativeGet.call(element,'data-glosh-icon-safe')==='1';
        const guardInlineStyleOwner=(prototype)=>{if(!prototype)return true;const owner=propertyOwner(prototype,'style');if(!owner)return false;
        if(invoke(WeakSetHas,guardedStyleOwners,[owner]))return true;const entry=descriptor(owner,'style');if(!entry||!entry.get)return false;
        try{const guardedGet=function(){if(protectedInlineStyle(this))deny();return invoke(entry.get,this,[])};
        const guardedSet=function(value){if(protectedInlineStyle(this))deny();return invoke(entry.set,this,[value])};
        ObjectDefine(owner,'style',{get:guardedGet,set:guardedSet,enumerable:entry.enumerable,configurable:false});invoke(WeakSetAdd,guardedStyleOwners,[owner]);
        const sealedEntry=descriptor(owner,'style');return !!sealedEntry&&!sealedEntry.configurable&&sealedEntry.get===guardedGet&&sealedEntry.set===guardedSet}catch(_){return false}};
        const insideProtected=(node)=>{let current=node;for(let depth=0;current&&depth<128;depth+=1){if(protectedNode(current))return true;current=parentOf(current)}return false};
        const mappedProtected=(node)=>node?invoke(WeakMapGet,protectedDescendants,[node]):null;
        const containsProtected=(node)=>{if(!node)return false;const mapped=mappedProtected(node);return insideProtected(node)||
        !!((shieldStyle&&nodeContains.call(node,shieldStyle))||(curtainStyle&&nodeContains.call(node,curtainStyle))||(curtainLayer&&nodeContains.call(node,curtainLayer))||
        (mapped&&nodeContains.call(node,mapped)))};
        const restoreMappedProtected=(node)=>{const mapped=mappedProtected(node);if(!mapped)return null;if(parentOf(mapped)!==node)nodeInsert.call(node,mapped,firstChildOf(node));
        watchStyle(mapped);registerProtectedSheet(read(styleSheetProperty,mapped));return mapped};
        const rejectProtectedMove=(node,parent)=>{if(containsProtected(node))deny()};
        const ensureProtectedStyle=(style,sheet,expectedMedia)=>{if(!style||!sheet)return false;try{if(read(htmlTitleProperty,style)!=='')invoke(htmlTitleProperty.set,style,['']);
        if(nativeHas.call(style,'title'))nativeRemove.call(style,'title');if(nativeHas.call(style,'type'))nativeRemove.call(style,'type');
        if(expectedMedia){if(nativeGet.call(style,'media')!==expectedMedia)nativeSet.call(style,'media',expectedMedia)}else if(nativeHas.call(style,'media'))nativeRemove.call(style,'media');
        if(read(styleElementDisabledProperty,style)!==false)invoke(styleElementDisabledProperty.set,style,[false]);
        if(read(styleSheetDisabledProperty,sheet)!==false)invoke(styleSheetDisabledProperty.set,sheet,[false]);const media=read(styleSheetMediaProperty,sheet);
        return read(htmlTitleProperty,style)===''&&!nativeHas.call(style,'title')&&read(styleElementTypeProperty,style)===''&&!nativeHas.call(style,'type')&&
        read(styleElementMediaProperty,style)===expectedMedia&&!!media&&read(mediaTextProperty,media)===expectedMedia&&read(styleSheetDisabledProperty,sheet)===false}catch(_){return false}};
        const ensureStyle=()=>{if(!shieldStyle)return false;if(!connected(shieldStyle)){const parent=documentHead()||documentElement();
        nodeInsert.call(parent,shieldStyle,firstChildOf(parent))}if(read(nodeText,shieldStyle)!==CSS&&nodeText&&nodeText.set)invoke(nodeText.set,shieldStyle,[CSS]);return ensureProtectedStyle(shieldStyle,shieldSheet,'')};
        const CURTAIN_RULES=[['all','initial'],['position','fixed'],['inset','0'],['display',curtainRequired?'block':'none'],['visibility','visible'],['opacity','1'],
        ['width','100vw'],['height','100vh'],['max-width','none'],['max-height','none'],['box-sizing','border-box'],['margin','0'],['padding','0'],['border','0'],
        ['background','#202124'],['color','#ffffff'],['z-index','2147483647'],['pointer-events','auto'],['zoom','1'],['scale','1'],['rotate','none'],['translate','none'],
        ['transform','none'],['transform-origin','50% 50%'],['transform-box','border-box'],['offset-path','none'],['offset-distance','0'],['offset-position','normal'],
        ['offset-anchor','auto'],['offset-rotate','auto'],['filter','none'],['clip','auto'],['clip-path','none'],['border-radius','0'],['mask','none'],
        ['mix-blend-mode','normal'],['animation','none'],['transition','none'],['contain','strict'],['overflow','hidden']];
        const curtainOpen=()=>!!curtainLayer&&read(dialogOpenProperty,curtainLayer)===true;
        const curtainVisibleByAttribute=()=>!!curtainLayer&&read(htmlHiddenProperty,curtainLayer)===false&&!nativeHas.call(curtainLayer,'hidden');
        const ensureCurtain=()=>{if(!TOP_LEVEL)return true;if(!curtainStyle||!curtainLayer||!connected(curtainStyle)||!connected(curtainLayer))return false;
        try{if(!curtainVisibleByAttribute())invoke(htmlHiddenProperty.set,curtainLayer,[false]);
        if(dialogClosedByProperty&&dialogClosedByProperty.set){invoke(dialogClosedByProperty.set,curtainLayer,['none']);
        if(nativeGet.call(curtainLayer,'closedby')!=='none'||read(dialogClosedByProperty,curtainLayer)!=='none')return false}}catch(_){return false}
        if(curtainRequired){if(nativeHas.call(curtainStyle,'media'))nativeRemove.call(curtainStyle,'media')}
        else if(nativeGet.call(curtainStyle,'media')!=='not all')nativeSet.call(curtainStyle,'media','not all');
        if(!ensureProtectedStyle(curtainStyle,curtainSheet,curtainRequired?'':'not all'))return false;const layerStyle=styleOf(curtainLayer);watchStyle(curtainLayer);
        for(let index=0;index<CURTAIN_RULES.length;index+=1){const rule=CURTAIN_RULES[index],value=rule[0]==='display'?(curtainRequired?'block':'none'):rule[1];
        if(nativeStyleGet.call(layerStyle,rule[0])!==value||nativeStylePriority.call(layerStyle,rule[0])!=='important')nativeStyleSet.call(layerStyle,rule[0],value,'important')}
        try{if(curtainRequired&&!curtainOpen()){nodeAppend.call(documentElement(),curtainLayer);nativeDialogShowModal.call(curtainLayer)}
        else if(!curtainRequired&&curtainOpen())nativeDialogClose.call(curtainLayer)}catch(_){return false}
        return connected(curtainStyle)&&connected(curtainLayer)&&(curtainRequired?!nativeHas.call(curtainStyle,'media'):nativeGet.call(curtainStyle,'media')==='not all')&&
        curtainOpen()===curtainRequired&&curtainVisibleByAttribute()&&(!dialogClosedByProperty||(nativeGet.call(curtainLayer,'closedby')==='none'&&read(dialogClosedByProperty,curtainLayer)==='none'))&&
        nativeStyleGet.call(layerStyle,'display')===(curtainRequired?'block':'none')&&nativeStylePriority.call(layerStyle,'display')==='important'};
        if(shieldStyle)invoke(WeakSetAdd,protectedNodes,[shieldStyle]);if(curtainStyle)invoke(WeakSetAdd,protectedNodes,[curtainStyle]);if(curtainLayer)invoke(WeakSetAdd,protectedNodes,[curtainLayer]);
        const guardAccessorProperty=(prototype,name,entry,blocked,denyRead)=>{const owner=prototype&&propertyOwner(prototype,name);
        if(!owner||!entry||!entry.get||!entry.set)return false;try{const guardedGet=function(){if(denyRead&&blocked(this))deny();return invoke(entry.get,this,[])};
        const guardedSet=function(value){if(blocked(this))deny();return invoke(entry.set,this,[value])};
        ObjectDefine(owner,name,{get:guardedGet,set:guardedSet,enumerable:entry.enumerable,configurable:false});const sealedEntry=descriptor(owner,name);
        return !!sealedEntry&&!sealedEntry.configurable&&sealedEntry.get===guardedGet&&sealedEntry.set===guardedSet}catch(_){return false}};
        installed=guardAccessorProperty(HTMLElement.prototype,'hidden',htmlHiddenProperty,protectedNode,false)&&installed;
        installed=guardAccessorProperty(HTMLElement.prototype,'title',htmlTitleProperty,protectedNode,false)&&installed;
        if(dialogClosedByProperty)installed=guardAccessorProperty(HTMLDialogElement.prototype,'closedBy',dialogClosedByProperty,protectedNode,false)&&installed;
        for(const prototype of [SELF.HTMLElement&&HTMLElement.prototype,SELF.SVGElement&&SVGElement.prototype,SELF.MathMLElement&&MathMLElement.prototype])
        installed=guardInlineStyleOwner(prototype)&&installed;
        installed=seal(EventTarget.prototype,'dispatchEvent',function(event){if(protectedNode(this))deny();return nativeDispatchEvent.call(this,event)})&&installed;
        if(curtainLayer)nativeAddEvent.call(curtainLayer,'cancel',event=>{invoke(EventPrevent,event,[]);invoke(EventStopImmediate,event,[])},true);
        registerProtectedSheet(shieldSheet);registerProtectedSheet(curtainSheet);
        installed=ensureStyle()&&ensureCurtain()&&installed;
        installed=seal(Node.prototype,'appendChild',function(node){if(insideProtected(this))deny();rejectProtectedMove(node,this);scan(node);const result=nodeAppend.call(this,node);sanitizeContainer(this);return result})&&installed;
        installed=seal(Node.prototype,'insertBefore',function(node,ref){if(insideProtected(this))deny();rejectProtectedMove(node,this);scan(node);const result=nodeInsert.call(this,node,ref);sanitizeContainer(this);return result})&&installed;
        installed=seal(Node.prototype,'replaceChild',function(node,old){if(insideProtected(this)||containsProtected(old))deny();rejectProtectedMove(node,this);scan(node);const result=nodeReplace.call(this,node,old);sanitizeContainer(this);return result})&&installed;
        installed=seal(Node.prototype,'removeChild',function(node){if(containsProtected(node))deny();return nodeRemove.call(this,node)})&&installed;
        const sealInsertion=(owner,name,removesChildren,removesTarget)=>{if(!owner||!owner[name])return true;const original=owner[name];return seal(owner,name,function(...args){
        const mapped=removesChildren?mappedProtected(this):null;if(removesTarget?containsProtected(this):(removesChildren?(!mapped&&containsProtected(this)):insideProtected(this)))deny();
        for(let index=0;index<args.length;index+=1){const value=args[index];
        if(value&&nodeTypeOf(value)){rejectProtectedMove(value,this);scan(value)}}const parent=parentOf(this);const result=invoke(original,this,args);scan(this);
        if(mapped)restoreMappedProtected(this);if(parent)sanitizeContainer(parent);return result})};
        for(const owner of [Element.prototype,Document.prototype,DocumentFragment.prototype])for(const name of ['append','prepend'])
        installed=sealInsertion(owner,name,false)&&installed;
        for(const owner of [Element.prototype,Document.prototype,DocumentFragment.prototype])installed=sealInsertion(owner,'replaceChildren',true)&&installed;
        for(const owner of [Element.prototype,self.CharacterData&&CharacterData.prototype,self.DocumentType&&DocumentType.prototype])
        for(const name of ['before','after'])installed=sealInsertion(owner,name)&&installed;
        for(const owner of [Element.prototype,self.CharacterData&&CharacterData.prototype,self.DocumentType&&DocumentType.prototype])
        installed=sealInsertion(owner,'replaceWith',false,true)&&installed;
        for(const owner of [Element.prototype,self.CharacterData&&CharacterData.prototype,self.DocumentType&&DocumentType.prototype])if(owner&&owner.remove){const remove=owner.remove;
        installed=seal(owner,'remove',function(){if(containsProtected(this))deny();return invoke(remove,this,[])})&&installed}
        if(Element.prototype.insertAdjacentElement){const adjacent=Element.prototype.insertAdjacentElement;
        installed=seal(Element.prototype,'insertAdjacentElement',function(position,element){if(insideProtected(this))deny();rejectProtectedMove(element,this);scan(element);const result=invoke(adjacent,this,[position,element]);
        sanitizeContainer(this);return result})&&installed}
        if(Element.prototype.insertAdjacentText){const adjacentText=Element.prototype.insertAdjacentText;
        installed=seal(Element.prototype,'insertAdjacentText',function(position,text){if(insideProtected(this))deny();return invoke(adjacentText,this,[position,text])})&&installed}
        for(const owner of [Node.prototype,Element.prototype,DocumentFragment.prototype])if(owner&&owner.moveBefore){const moveBefore=owner.moveBefore;
        installed=seal(owner,'moveBefore',function(node,reference){if(insideProtected(this))deny();rejectProtectedMove(node,this);scan(node);const result=invoke(moveBefore,this,[node,reference]);
        sanitizeContainer(this);return result})&&installed}
        const rangeElement=(ancestor)=>{if(!ancestor)return null;if(nodeTypeOf(ancestor)===1)return ancestor;const parent=parentOf(ancestor);
        return parent&&nodeTypeOf(parent)===1?parent:null};
        if(self.Range&&Range.prototype.insertNode){const rangeInsert=Range.prototype.insertNode;installed=seal(Range.prototype,'insertNode',function(node){
        const ancestor=read(rangeAncestorProperty,this);if(insideProtected(ancestor))deny();rejectProtectedMove(node,ancestor);scan(node);const result=invoke(rangeInsert,this,[node]);
        const container=rangeElement(ancestor);if(container)sanitizeContainer(container);return result})&&installed}
        if(self.Range&&Range.prototype.surroundContents){const surround=Range.prototype.surroundContents;installed=seal(Range.prototype,'surroundContents',function(node){
        const ancestor=read(rangeAncestorProperty,this);if(containsProtected(ancestor))deny();rejectProtectedMove(node,ancestor);scan(node);const result=invoke(surround,this,[node]);scan(node);
        const container=rangeElement(ancestor);if(container)sanitizeContainer(container);return result})&&installed}
        if(self.Range){for(const name of ['deleteContents','extractContents']){const original=Range.prototype[name];if(original)installed=seal(Range.prototype,name,function(...args){
        if(containsProtected(read(rangeAncestorProperty,this)))deny();return invoke(original,this,args)})&&installed}}
        const selectionTouchesProtected=(selection)=>{if(!selection||!selectionRangeCountProperty||!selectionGetRangeAt)return true;const count=read(selectionRangeCountProperty,selection);
        if(!NumberIsSafeInteger(count)||count<0||count>64)return true;for(let index=0;index<count;index+=1){const range=selectionGetRangeAt.call(selection,index);
        if(containsProtected(read(rangeAncestorProperty,range)))return true}return false};
        if(self.Selection&&Selection.prototype.deleteFromDocument){const deleteFromDocument=Selection.prototype.deleteFromDocument;
        installed=seal(Selection.prototype,'deleteFromDocument',function(){if(selectionTouchesProtected(this))deny();return invoke(deleteFromDocument,this,[])})&&installed}
        const adopt=Document.prototype.adoptNode;installed=seal(Document.prototype,'adoptNode',function(node){if(containsProtected(node))deny();const result=invoke(adopt,this,[node]);scan(result);return result})&&installed;
        installed=seal(Element.prototype,'setAttribute',function(name,value){const attributeName=stringOf(name),attributeValue=stringOf(value),key=lower(attributeName),tag=localNameOf(this);
        if(protectedNode(this)||invoke(WeakSetHas,protectedIconNodes,[this])||invoke(SetHas,TOP_LAYER_ATTRIBUTES,[key]))deny();if(oneOf(tag,['a','area','form','base'])&&key==='target')return nativeSet.call(this,'target','_self');
        if(oneOf(tag,['button','input'])&&key==='formtarget')return nativeSet.call(this,'formtarget','_self');
        if(tag==='iframe'&&key==='sandbox'){const result=nativeSet.call(this,'sandbox',FRAME_SANDBOX);invoke(WeakSetAdd,lockedSandboxes,[sandboxOf(this)]);return result}
        if(tag==='iframe'&&key==='srcdoc'){hide(this);return}
        if(tag==='iframe'&&key==='src'&&!networkUrl(attributeValue)){nativeSet.call(this,'sandbox',FRAME_SANDBOX);invoke(WeakSetAdd,lockedSandboxes,[sandboxOf(this)]);nativeSet.call(this,'src','about:blank');hide(this);return}
        const result=nativeSet.call(this,attributeName,attributeValue);sanitizeContainer(this);return result})&&installed;
        installed=seal(Element.prototype,'removeAttribute',function(name){const attributeName=stringOf(name),key=lower(attributeName),tag=localNameOf(this);
        if(protectedNode(this)||invoke(WeakSetHas,protectedIconNodes,[this]))deny();
        if(tag==='iframe'&&key==='sandbox'){nativeSet.call(this,'sandbox',FRAME_SANDBOX);invoke(WeakSetAdd,lockedSandboxes,[sandboxOf(this)]);return}
        const result=nativeRemove.call(this,attributeName);sanitizeContainer(this);return result})&&installed;
        installed=seal(Element.prototype,'toggleAttribute',function(name,force){const attributeName=stringOf(name),key=lower(attributeName),tag=localNameOf(this);
        if(protectedNode(this)||invoke(WeakSetHas,protectedIconNodes,[this])||invoke(SetHas,TOP_LAYER_ATTRIBUTES,[key]))deny();
        if(tag==='iframe'&&(key==='sandbox'||key==='srcdoc')){sanitizeElement(this);return nativeHas.call(this,key)}
        const result=nativeToggle.call(this,attributeName,force);sanitizeContainer(this);return result})&&installed;
        const nativeSetNS=method(Element.prototype.setAttributeNS),nativeRemoveNS=method(Element.prototype.removeAttributeNS);
        installed=seal(Element.prototype,'setAttributeNS',function(namespace,name,value){const namespaceValue=namespace===null?null:stringOf(namespace),attributeName=stringOf(name),attributeValue=stringOf(value);
        const colon=lastIndex(attributeName,':'),key=lower(colon>=0?slice(attributeName,colon+1):attributeName),tag=localNameOf(this);
        if(protectedNode(this)||invoke(WeakSetHas,protectedIconNodes,[this])||invoke(SetHas,TOP_LAYER_ATTRIBUTES,[key]))deny();if(oneOf(tag,['a','area','form','base'])&&key==='target')return nativeSet.call(this,'target','_self');
        if(oneOf(tag,['button','input'])&&key==='formtarget')return nativeSet.call(this,'formtarget','_self');
        if(tag==='iframe'&&key==='sandbox'){const result=nativeSet.call(this,'sandbox',FRAME_SANDBOX);invoke(WeakSetAdd,lockedSandboxes,[sandboxOf(this)]);return result}
        if(tag==='iframe'&&key==='srcdoc'){hide(this);return}
        if(tag==='iframe'&&key==='src'&&!networkUrl(attributeValue)){nativeSet.call(this,'sandbox',FRAME_SANDBOX);invoke(WeakSetAdd,lockedSandboxes,[sandboxOf(this)]);
        nativeSet.call(this,'src','about:blank');hide(this);return}const result=nativeSetNS.call(this,namespaceValue,attributeName,attributeValue);sanitizeContainer(this);return result})&&installed;
        installed=seal(Element.prototype,'removeAttributeNS',function(namespace,name){const namespaceValue=namespace===null?null:stringOf(namespace),attributeName=stringOf(name),key=lower(attributeName),tag=localNameOf(this);
        if(protectedNode(this)||invoke(WeakSetHas,protectedIconNodes,[this]))deny();if(tag==='iframe'&&key==='sandbox'){nativeSet.call(this,'sandbox',FRAME_SANDBOX);invoke(WeakSetAdd,lockedSandboxes,[sandboxOf(this)]);return}
        const result=nativeRemoveNS.call(this,namespaceValue,attributeName);sanitizeContainer(this);return result})&&installed;
        const guardedSetAttribute=Element.prototype.setAttribute,guardedSetAttributeNS=Element.prototype.setAttributeNS;
        const guardedRemoveAttribute=Element.prototype.removeAttribute,guardedRemoveAttributeNS=Element.prototype.removeAttributeNS;
        for(const name of ['setAttributeNode','setAttributeNodeNS']){const original=Element.prototype[name];if(original)installed=seal(Element.prototype,name,function(attribute){
        if(!attribute)deny();const namespace=attrNamespace(attribute),attributeName=attrName(attribute),local=attrLocalName(attribute),value=attrValueOf(attribute);
        const previous=namespace?nativeGetNodeNS.call(this,namespace,local):nativeGetNode.call(this,attributeName);
        if(namespace)invoke(guardedSetAttributeNS,this,[namespace,attributeName,value]);
        else invoke(guardedSetAttribute,this,[attributeName,value]);return previous})&&installed}
        if(Element.prototype.removeAttributeNode){const removeAttributeNode=Element.prototype.removeAttributeNode;installed=seal(Element.prototype,'removeAttributeNode',function(attribute){
        if(!attribute||attrOwner(attribute)!==this)return invoke(removeAttributeNode,this,[attribute]);const namespace=attrNamespace(attribute);
        if(namespace)invoke(guardedRemoveAttributeNS,this,[namespace,attrLocalName(attribute)]);else invoke(guardedRemoveAttribute,this,[attrName(attribute)]);return attribute})&&installed}
        const routeAttributeValue=(attribute,value)=>{const owner=attribute&&attrOwner(attribute);if(!owner)return false;const namespace=attrNamespace(attribute);
        if(namespace)invoke(guardedSetAttributeNS,owner,[namespace,attrName(attribute),value]);
        else invoke(guardedSetAttribute,owner,[attrName(attribute),value]);return true};
        if(self.Attr){const attrValueDescriptor=descriptor(Attr.prototype,'value');if(attrValueDescriptor&&attrValueDescriptor.get&&attrValueDescriptor.set){try{
        ObjectDefine(Attr.prototype,'value',{get:attrValueDescriptor.get,set:function(value){if(!routeAttributeValue(this,value))invoke(attrValueDescriptor.set,this,[value])},configurable:false})
        }catch(_){installed=false}}else installed=false}
        if(elementAttributesProperty&&elementAttributesProperty.get){try{
        ObjectDefine(Element.prototype,'attributes',{get:function(){const value=invoke(elementAttributesProperty.get,this,[]);invoke(WeakMapSet,attributeOwners,[value,this]);return value},configurable:false})
        }catch(_){installed=false}}else installed=false;
        if(self.NamedNodeMap){for(const name of ['setNamedItem','setNamedItemNS']){const original=NamedNodeMap.prototype[name];if(original)
        installed=seal(NamedNodeMap.prototype,name,function(attribute){const owner=invoke(WeakMapGet,attributeOwners,[this]);if(!owner||!attribute)deny();
        const namespace=attrNamespace(attribute),attributeName=attrName(attribute),local=attrLocalName(attribute),value=attrValueOf(attribute);
        const previous=namespace?nativeGetNodeNS.call(owner,namespace,local):nativeGetNode.call(owner,attributeName);
        if(namespace)invoke(guardedSetAttributeNS,owner,[namespace,attributeName,value]);
        else invoke(guardedSetAttribute,owner,[attributeName,value]);return previous})&&installed}
        for(const name of ['removeNamedItem','removeNamedItemNS']){const original=NamedNodeMap.prototype[name];if(original)
        installed=seal(NamedNodeMap.prototype,name,function(nameValue){const owner=invoke(WeakMapGet,attributeOwners,[this]);if(!owner)deny();
        const previous=name==='removeNamedItemNS'?nativeGetNodeNS.call(owner,arguments[0],arguments[1]):nativeGetNode.call(owner,nameValue);if(!previous)deny();
        if(name==='removeNamedItemNS')invoke(guardedRemoveAttributeNS,owner,[arguments[0],arguments[1]]);else invoke(guardedRemoveAttribute,owner,[nameValue]);return previous})&&installed}}
        if(self.DOMTokenList){for(const name of ['add','remove','toggle','replace']){const original=DOMTokenList.prototype[name];if(original)installed=seal(DOMTokenList.prototype,name,function(...args){
        if(invoke(WeakSetHas,lockedSandboxes,[this]))return false;return invoke(original,this,args)})&&installed}const tokenValue=descriptor(DOMTokenList.prototype,'value');
        if(tokenValue&&tokenValue.get&&tokenValue.set){try{ObjectDefine(DOMTokenList.prototype,'value',{get:tokenValue.get,set:function(value){
        if(invoke(WeakSetHas,lockedSandboxes,[this]))return;invoke(tokenValue.set,this,[value])},configurable:false})}catch(_){installed=false}}}
        const guardFrameSandbox=()=>{const owner=propertyOwner(HTMLIFrameElement.prototype,'sandbox'),entry=iframeSandboxProperty;
        if(!owner||!entry||!entry.get||!entry.set)return false;try{const guardedGet=function(){const tokens=invoke(entry.get,this,[]);
        if(tokens)invoke(WeakSetAdd,lockedSandboxes,[tokens]);return tokens};const guardedSet=function(){invoke(entry.set,this,[FRAME_SANDBOX]);const tokens=invoke(entry.get,this,[]);
        if(tokens)invoke(WeakSetAdd,lockedSandboxes,[tokens])};ObjectDefine(owner,'sandbox',{get:guardedGet,set:guardedSet,enumerable:entry.enumerable,configurable:false});
        const sealedEntry=descriptor(owner,'sandbox');return !!sealedEntry&&!sealedEntry.configurable&&sealedEntry.get===guardedGet&&sealedEntry.set===guardedSet}catch(_){return false}};
        if(self.HTMLIFrameElement)installed=guardFrameSandbox()&&installed;
        const forceSelfTarget=(owner,name)=>{if(!owner)return true;const entry=descriptor(owner,name);if(!entry||!entry.get||!entry.set)return false;
        try{ObjectDefine(owner,name,{get:entry.get,set:function(){invoke(entry.set,this,['_self'])},configurable:false});return true}catch(_){return false}};
        for(const pair of [[self.HTMLAnchorElement&&HTMLAnchorElement.prototype,'target'],[self.HTMLAreaElement&&HTMLAreaElement.prototype,'target'],
        [self.HTMLFormElement&&HTMLFormElement.prototype,'target'],[self.HTMLBaseElement&&HTMLBaseElement.prototype,'target'],
        [self.HTMLButtonElement&&HTMLButtonElement.prototype,'formTarget'],[self.HTMLInputElement&&HTMLInputElement.prototype,'formTarget']])
        installed=forceSelfTarget(pair[0],pair[1])&&installed;
        const protectedSheet=(value)=>!!value&&invoke(WeakSetHas,protectedSheets,[value]);
        const protectedRule=(value)=>{try{return !!value&&protectedSheet(read(cssParentSheetProperty,value))}catch(_){return false}};
        const protectedDeclaration=(value)=>{const owner=invoke(WeakMapGet,styleOwners,[value]);if(owner&&(protectedNode(owner)||invoke(WeakSetHas,protectedIconNodes,[owner])||invoke(WeakSetHas,protectedMediaNodes,[owner])||nativeGet.call(owner,'data-glosh-media-blocked')==='1'||
        nativeGet.call(owner,'data-glosh-icon-safe')==='1'))return true;try{const rule=read(cssParentRuleProperty,value);return !!rule&&protectedRule(rule)}catch(_){return false}};
        installed=seal(CSSStyleDeclaration.prototype,'setProperty',function(...args){if(protectedDeclaration(this))deny();return nativeStyleSet.apply(this,args)})&&installed;
        installed=seal(CSSStyleDeclaration.prototype,'removeProperty',function(...args){if(protectedDeclaration(this))deny();return nativeStyleRemove.apply(this,args)})&&installed;
        for(const name of ['all','display','boxSizing','width','height','minWidth','minHeight','maxWidth','maxHeight','margin','padding','visibility','opacity','zoom','scale','rotate','translate','transform','transformOrigin',
        'filter','mask','clipPath','animation','transition','position','inset','background','boxShadow','border','overflow','contain','pointerEvents','zIndex','mixBlendMode','color','direction','unicodeBidi','d','fill','stroke','fillRule']){const entry=descriptor(CSSStyleDeclaration.prototype,name);
        if(entry&&entry.get&&entry.set){try{ObjectDefine(CSSStyleDeclaration.prototype,name,{get:entry.get,set:function(value){
        if(protectedDeclaration(this))deny();invoke(entry.set,this,[value])},configurable:false})}catch(_){installed=false}}}
        const cssText=descriptor(CSSStyleDeclaration.prototype,'cssText');if(cssText&&cssText.get&&cssText.set){try{
        ObjectDefine(CSSStyleDeclaration.prototype,'cssText',{get:cssText.get,set:function(value){if(protectedDeclaration(this))deny();invoke(cssText.set,this,[value])},configurable:false})
        }catch(_){installed=false}}else installed=false;
        if(self.CSSStyleSheet){for(const name of ['insertRule','deleteRule','addRule','removeRule','replace','replaceSync']){const original=CSSStyleSheet.prototype[name];if(original)
        installed=seal(CSSStyleSheet.prototype,name,function(...args){if(protectedSheet(this))deny();return invoke(original,this,args)})&&installed}}
        installed=guardAccessorProperty(StyleSheet.prototype,'disabled',styleSheetDisabledProperty,protectedSheet,false)&&installed;
        installed=guardAccessorProperty(StyleSheet.prototype,'media',styleSheetMediaProperty,protectedSheet,true)&&installed;
        if(self.MediaList){for(const name of ['appendMedium','deleteMedium']){const original=MediaList.prototype[name];if(original)
        installed=seal(MediaList.prototype,name,function(...args){if(invoke(WeakSetHas,protectedMedias,[this]))deny();return invoke(original,this,args)})&&installed}
        if(mediaTextProperty&&mediaTextProperty.get&&mediaTextProperty.set){try{
        ObjectDefine(MediaList.prototype,'mediaText',{get:mediaTextProperty.get,set:function(value){if(invoke(WeakSetHas,protectedMedias,[this]))deny();invoke(mediaTextProperty.set,this,[value])},configurable:false})
        }catch(_){installed=false}}}
        const protectedStyleMap=(value)=>{if(invoke(WeakSetHas,protectedStyleMaps,[value]))return true;const owner=invoke(WeakMapGet,styleMapOwners,[value]);
        return !!owner&&(protectedNode(owner)||invoke(WeakSetHas,protectedIconNodes,[owner])||invoke(WeakSetHas,protectedMediaNodes,[owner])||nativeGet.call(owner,'data-glosh-media-blocked')==='1'||nativeGet.call(owner,'data-glosh-icon-safe')==='1')};
        if(self.StylePropertyMap){for(const name of ['set','append','delete','clear']){const original=StylePropertyMap.prototype[name];if(original)
        installed=seal(StylePropertyMap.prototype,name,function(...args){if(protectedStyleMap(this))deny();return invoke(original,this,args)})&&installed}}
        const protectedReflectiveTarget=(value)=>!!value&&(protectedNode(value)||invoke(WeakSetHas,protectedIconNodes,[value])||invoke(WeakSetHas,protectedMediaNodes,[value])||
        invoke(WeakSetHas,protectedMedias,[value])||invoke(WeakSetHas,lockedSandboxes,[value])||protectedDeclaration(value)||protectedSheet(value)||protectedRule(value)||protectedStyleMap(value)||!!mappedProtected(value));
        const denyReflectiveTarget=(value)=>{if(protectedReflectiveTarget(value))deny();return value};
        installed=seal(NativeObject,'defineProperty',function(target,name,entry){denyReflectiveTarget(target);return invoke(ObjectDefine,NativeObject,[target,name,entry])})&&installed;
        installed=seal(NativeObject,'defineProperties',function(target,entries){denyReflectiveTarget(target);return invoke(ObjectDefineProperties,NativeObject,[target,entries])})&&installed;
        installed=seal(NativeObject,'assign',function(target,...sources){denyReflectiveTarget(target);const args=[target];for(let index=0;index<sources.length;index+=1)append(args,sources[index]);return invoke(ObjectAssign,NativeObject,args)})&&installed;
        installed=seal(NativeObject,'setPrototypeOf',function(target,prototype){denyReflectiveTarget(target);return invoke(ObjectSetPrototype,NativeObject,[target,prototype])})&&installed;
        installed=seal(NativeObject,'preventExtensions',function(target){denyReflectiveTarget(target);return invoke(ObjectPreventExtensions,NativeObject,[target])})&&installed;
        installed=seal(NativeObject,'seal',function(target){denyReflectiveTarget(target);return invoke(ObjectSeal,NativeObject,[target])})&&installed;
        installed=seal(NativeObject,'freeze',function(target){denyReflectiveTarget(target);return invoke(ObjectFreeze,NativeObject,[target])})&&installed;
        installed=seal(NativeReflect,'defineProperty',function(target,name,entry){denyReflectiveTarget(target);return invoke(ReflectDefine,NativeReflect,[target,name,entry])})&&installed;
        installed=seal(NativeReflect,'deleteProperty',function(target,name){denyReflectiveTarget(target);return invoke(ReflectDelete,NativeReflect,[target,name])})&&installed;
        installed=seal(NativeReflect,'set',function(...args){denyReflectiveTarget(args[0]);return invoke(ReflectSet,NativeReflect,args)})&&installed;
        installed=seal(NativeReflect,'setPrototypeOf',function(target,prototype){denyReflectiveTarget(target);return invoke(ReflectSetPrototype,NativeReflect,[target,prototype])})&&installed;
        installed=seal(NativeReflect,'preventExtensions',function(target){denyReflectiveTarget(target);return invoke(ReflectPreventExtensions,NativeReflect,[target])})&&installed;
        if(ObjectDefineGetter)installed=seal(ObjectPrototype,'__defineGetter__',function(name,getter){denyReflectiveTarget(this);return invoke(ObjectDefineGetter,this,[name,getter])})&&installed;
        if(ObjectDefineSetter)installed=seal(ObjectPrototype,'__defineSetter__',function(name,setter){denyReflectiveTarget(this);return invoke(ObjectDefineSetter,this,[name,setter])})&&installed;
        const objectProtoProperty=descriptor(ObjectPrototype,'__proto__');if(objectProtoProperty&&objectProtoProperty.get&&objectProtoProperty.set){try{
        ObjectDefine(ObjectPrototype,'__proto__',{get:objectProtoProperty.get,set:function(value){denyReflectiveTarget(this);invoke(objectProtoProperty.set,this,[value])},configurable:false})
        }catch(_){installed=false}}
        const sealProtectedRuleMethod=(prototype,name)=>{const owner=prototype&&propertyOwner(prototype,name);if(!owner)return true;let names=invoke(WeakMapGet,guardedRuleMethodOwners,[owner]);
        if(!names){names=new Set();invoke(WeakMapSet,guardedRuleMethodOwners,[owner,names])}if(invoke(SetHas,names,[name]))return true;const original=owner[name];
        if(!original)return true;const guarded=function(...args){if(protectedRule(this))deny();return invoke(original,this,args)};const sealed=seal(owner,name,guarded);
        if(sealed)invoke(SetAdd,names,[name]);return sealed};
        if(self.CSSStyleRule){const selectorText=propertyDescriptor(CSSStyleRule.prototype,'selectorText');if(selectorText&&selectorText.get&&selectorText.set){try{
        ObjectDefine(CSSStyleRule.prototype,'selectorText',{get:selectorText.get,set:function(value){if(protectedRule(this))deny();invoke(selectorText.set,this,[value])},configurable:false})
        }catch(_){installed=false}}installed=guardAccessorProperty(CSSStyleRule.prototype,'style',ruleStyleProperty,protectedRule,true)&&installed;
        for(const name of ['insertRule','deleteRule'])installed=sealProtectedRuleMethod(CSSStyleRule.prototype,name)&&installed}
        if(self.CSSGroupingRule)for(const name of ['insertRule','deleteRule'])installed=sealProtectedRuleMethod(CSSGroupingRule.prototype,name)&&installed;
        if(self.HTMLStyleElement){for(const name of ['disabled','media','type']){const entry=propertyDescriptor(HTMLStyleElement.prototype,name);if(entry&&entry.get&&entry.set){try{
        ObjectDefine(HTMLStyleElement.prototype,name,{get:entry.get,set:function(value){if(protectedNode(this))deny();invoke(entry.set,this,[value])},configurable:false})
        }catch(_){installed=false}}else installed=false}}
        if(nodeText&&nodeText.get&&nodeText.set){try{ObjectDefine(Node.prototype,'textContent',{get:nodeText.get,set:function(value){
        const mapped=mappedProtected(this);if(!mapped&&containsProtected(this))deny();if(nodeTypeOf(this)===2&&routeAttributeValue(this,value))return;invoke(nodeText.set,this,[value]);
        if(mapped){restoreMappedProtected(this);scan(this)}},configurable:false})
        }catch(_){installed=false}}else installed=false;
        if(nodeValue&&nodeValue.get&&nodeValue.set){try{ObjectDefine(Node.prototype,'nodeValue',{get:nodeValue.get,set:function(value){
        if(containsProtected(this))deny();if(nodeTypeOf(this)===2&&routeAttributeValue(this,value))return;invoke(nodeValue.set,this,[value])},configurable:false})
        }catch(_){installed=false}}else installed=false;
        if(self.CharacterData){const characterData=descriptor(CharacterData.prototype,'data');if(characterData&&characterData.get&&characterData.set){try{
        ObjectDefine(CharacterData.prototype,'data',{get:characterData.get,set:function(value){if(containsProtected(this))deny();invoke(characterData.set,this,[value])},configurable:false})
        }catch(_){installed=false}}else installed=false;for(const name of ['appendData','deleteData','insertData','replaceData']){const original=CharacterData.prototype[name];
        if(original)installed=seal(CharacterData.prototype,name,function(...args){if(containsProtected(this))deny();return invoke(original,this,args)})&&installed}}
        if(self.Text&&Text.prototype.splitText){const splitText=Text.prototype.splitText;installed=seal(Text.prototype,'splitText',function(offset){
        if(containsProtected(this))deny();return invoke(splitText,this,[offset])})&&installed}
        if(self.HTMLElement){const innerText=propertyDescriptor(HTMLElement.prototype,'innerText');if(innerText&&innerText.get&&innerText.set){try{
        ObjectDefine(HTMLElement.prototype,'innerText',{get:innerText.get,set:function(value){if(containsProtected(this))deny();invoke(innerText.set,this,[value])},configurable:false})
        }catch(_){installed=false}}const outerText=propertyDescriptor(HTMLElement.prototype,'outerText');if(outerText&&outerText.get&&outerText.set){try{
        ObjectDefine(HTMLElement.prototype,'outerText',{get:outerText.get,set:function(value){if(containsProtected(this))deny();invoke(outerText.set,this,[value])},configurable:false})
        }catch(_){installed=false}}}
        const create=nativeCreateElement,createNS=method(Document.prototype.createElementNS),importNode=method(Document.prototype.importNode);
        const safeElementName=(name)=>{const canonical=stringOf(name);return oneOf(lower(canonical),['object','embed','frame','fencedframe'])?'template':canonical};
        installed=seal(Document.prototype,'createElement',function(name,options){const node=create.call(this,safeElementName(name),options);sanitizeElement(node);return node})&&installed;
        installed=seal(Document.prototype,'createElementNS',function(ns,name,options){const namespace=ns===null?null:stringOf(ns),node=createNS.call(this,namespace,safeElementName(name),options);sanitizeElement(node);return node})&&installed;
        installed=seal(Document.prototype,'importNode',function(node,deep){const copy=importNode.call(this,node,deep);scan(copy);return copy})&&installed;
        const clone=method(Node.prototype.cloneNode);installed=seal(Node.prototype,'cloneNode',function(deep){const copy=clone.call(this,deep);scan(copy);return copy})&&installed;
        const inner=descriptor(Element.prototype,'innerHTML');
        const safeMarkup=(value)=>{if(!inner||!inner.set||!inner.get)deny();const template=create.call(DOC,'template');invoke(inner.set,template,[stringOf(value)]);
        scan(templateContent(template));return invoke(inner.get,template,[])};
        const insertHtml=Element.prototype.insertAdjacentHTML;installed=seal(Element.prototype,'insertAdjacentHTML',function(position,text){
        if(containsProtected(this))deny();const result=invoke(insertHtml,this,[position,safeMarkup(text)]);scan(this);return result})&&installed;
        if(inner&&inner.set&&inner.get){try{
        ObjectDefine(Element.prototype,'innerHTML',{get:inner.get,set:function(value){if(containsProtected(this))deny();invoke(inner.set,this,[safeMarkup(value)]);scan(this)},configurable:false});
        installed=descriptor(Element.prototype,'innerHTML').configurable===false&&installed}catch(_){installed=false}}else installed=false;
        const outer=descriptor(Element.prototype,'outerHTML');if(outer&&outer.set&&outer.get){try{
        ObjectDefine(Element.prototype,'outerHTML',{get:outer.get,set:function(value){if(containsProtected(this))deny();const parent=parentOf(this);
        invoke(outer.set,this,[safeMarkup(value)]);scan(parent)},configurable:false});
        installed=descriptor(Element.prototype,'outerHTML').configurable===false&&installed}catch(_){installed=false}}else installed=false;
        for(const owner of [Element.prototype,self.ShadowRoot&&ShadowRoot.prototype])if(owner&&owner.setHTML){const setHTML=owner.setHTML;
        installed=seal(owner,'setHTML',function(value,...args){const mapped=mappedProtected(this);if(!mapped&&containsProtected(this))deny();const all=[safeMarkup(value)];for(let index=0;index<args.length;index+=1)append(all,args[index]);
        const result=invoke(setHTML,this,all);if(mapped)restoreMappedProtected(this);scan(this);return result})&&installed}
        if(Element.prototype.setHTMLUnsafe)installed=seal(Element.prototype,'setHTMLUnsafe',deny)&&installed;
        if(self.ShadowRoot&&ShadowRoot.prototype.setHTMLUnsafe)installed=seal(ShadowRoot.prototype,'setHTMLUnsafe',deny)&&installed;
        if(Document.parseHTMLUnsafe)installed=seal(Document,'parseHTMLUnsafe',deny)&&installed;
        const originalAttach=Element.prototype.attachShadow;installed=seal(Element.prototype,'attachShadow',function(init){if(protectedNode(this))deny();const root=invoke(originalAttach,this,[init]);
        const style=create.call(DOC,'style');invoke(WeakSetAdd,protectedNodes,[style]);invoke(WeakMapSet,protectedDescendants,[root,style]);
        nativeSet.call(style,'nonce',NONCE);invoke(nodeText.set,style,[CSS]);nodeAppend.call(root,style);
        if(!clearStyleNonce(style)){nodeRemove.call(root,style);failClosedDocument();deny()}
        watchStyle(style);const shadowSheet=read(styleSheetProperty,style);if(!shadowSheet){nodeRemove.call(root,style);failClosedDocument();deny()}registerProtectedSheet(shadowSheet);
        const shadowObserver=new NativeMutationObserver(records=>{for(let index=0;index<records.length;index+=1){const record=records[index],type=read(mutationTypeProperty,record);
        if(type==='childList'){const added=copyList(read(mutationAddedProperty,record),nodeListLength);for(let addedIndex=0;addedIndex<added.length;addedIndex+=1)scan(added[addedIndex])}
        else{const target=read(mutationTargetProperty,record);if(target){sanitizeContainer(target);scan(target)}}}restoreMappedProtected(root)});
        mutationObserve.call(shadowObserver,root,{childList:true,subtree:true,attributes:true,attributeFilter:WATCHED_ATTRIBUTES});scan(root);return root})&&installed;
        if(self.ShadowRoot){const shadowInner=descriptor(ShadowRoot.prototype,'innerHTML');if(shadowInner&&shadowInner.get&&shadowInner.set){try{
        ObjectDefine(ShadowRoot.prototype,'innerHTML',{get:shadowInner.get,set:function(value){if(!mappedProtected(this))deny();invoke(shadowInner.set,this,[safeMarkup(value)]);
        restoreMappedProtected(this);scan(this)},configurable:false})
        }catch(_){installed=false}}}
        installed=seal(Document.prototype,'write',deny)&&installed;installed=seal(Document.prototype,'writeln',deny)&&installed;
        installed=seal(Document.prototype,'open',deny)&&installed;
        if(Document.prototype.execCommand){const execCommand=Document.prototype.execCommand;installed=seal(Document.prototype,'execCommand',function(command,...args){
        const commandValue=stringOf(command),commandName=lower(commandValue);if(oneOf(commandName,['delete','forwarddelete','cut','inserttext','inserthtml'])&&
        selectionTouchesProtected(documentGetSelection?documentGetSelection.call(DOC):null))deny();if(commandName==='inserthtml')put(args,1,safeMarkup(args.length>1?args[1]:''));const all=[commandValue];
        for(let index=0;index<args.length;index+=1)append(all,args[index]);const result=invoke(execCommand,this,all);
        if(commandName==='inserthtml')scan(documentElement());return result})&&installed}
        const openOwner=propertyOwner(self,'open'),originalOpen=openOwner&&openOwner.open;if(originalOpen)installed=seal(openOwner,'open',function(url,target,features){
        const urlValue=stringOf(url);if(!networkUrl(urlValue))return null;invoke(originalOpen,this,[urlValue,target?stringOf(target):'_blank','noopener=yes,noreferrer=yes']);return null})&&installed;else installed=false;
        const guardNavigation=(event)=>{const initial=eventTarget(event),target=initial&&nodeTypeOf(initial)===1?elementClosest.call(initial,'a[href],area[href],form[action]'):null;
        if(!target)return;const value=nativeGet.call(target,'href')||nativeGet.call(target,'action');if(!networkUrl(value)){invoke(EventPrevent,event,[]);invoke(EventStopImmediate,event,[]);return}
        nativeSet.call(target,'target','_self');if(localNameOf(target)!=='form')nativeSet.call(target,'rel','noopener')};
        nativeAddEvent.call(DOC,'click',guardNavigation,true);nativeAddEvent.call(DOC,'submit',guardNavigation,true);
        if(self.HTMLIFrameElement){const frameSrc=descriptor(HTMLIFrameElement.prototype,'src');
        if(frameSrc&&frameSrc.get&&frameSrc.set){try{ObjectDefine(HTMLIFrameElement.prototype,'src',{get:frameSrc.get,set:function(value){const source=stringOf(value);
        nativeSet.call(this,'sandbox',FRAME_SANDBOX);invoke(WeakSetAdd,lockedSandboxes,[sandboxOf(this)]);if(!networkUrl(source)){invoke(frameSrc.set,this,['about:blank']);hide(this)}else{invoke(frameSrc.set,this,[source]);unhide(this)}},configurable:false})
        }catch(_){installed=false}}else installed=false;const frameSrcdoc=descriptor(HTMLIFrameElement.prototype,'srcdoc');
        if(frameSrcdoc&&frameSrcdoc.get&&frameSrcdoc.set){try{ObjectDefine(HTMLIFrameElement.prototype,'srcdoc',{get:frameSrcdoc.get,set:function(){hide(this)},configurable:false})
        }catch(_){installed=false}}else installed=false}
        if(self.HTMLCanvasElement){installed=seal(HTMLCanvasElement.prototype,'getContext',()=>null)&&installed;
        if(HTMLCanvasElement.prototype.transferControlToOffscreen)installed=seal(HTMLCanvasElement.prototype,'transferControlToOffscreen',deny)&&installed}
        const denyPropertySetter=(owner,name)=>{if(!owner)return true;const entry=descriptor(owner,name);if(!entry)return true;if(!entry.get||!entry.set)return false;
        try{ObjectDefine(owner,name,{get:entry.get,set:deny,configurable:false});const sealedEntry=descriptor(owner,name);return !!sealedEntry&&!sealedEntry.configurable&&sealedEntry.set===deny}catch(_){return false}};
        if(SELF.HTMLDialogElement){installed=seal(HTMLDialogElement.prototype,'showModal',deny)&&installed;
        installed=seal(HTMLDialogElement.prototype,'show',function(){if(curtainRequired||protectedNode(this))deny();return nativeDialogShow.call(this)})&&installed;
        installed=seal(HTMLDialogElement.prototype,'close',function(...args){if(protectedNode(this))deny();return nativeDialogClose.apply(this,args)})&&installed;
        if(HTMLDialogElement.prototype.requestClose){const nativeRequestClose=method(HTMLDialogElement.prototype.requestClose);
        installed=seal(HTMLDialogElement.prototype,'requestClose',function(...args){if(protectedNode(this))deny();return nativeRequestClose.apply(this,args)})&&installed}
        if(dialogOpenProperty&&dialogOpenProperty.get&&dialogOpenProperty.set){try{ObjectDefine(HTMLDialogElement.prototype,'open',{get:dialogOpenProperty.get,set:function(value){if(protectedNode(this))deny();invoke(dialogOpenProperty.set,this,[value])},configurable:false})
        }catch(_){installed=false}}else installed=false}
        if(SELF.HTMLElement){if(HTMLElement.prototype.showPopover)installed=seal(HTMLElement.prototype,'showPopover',deny)&&installed;
        if(HTMLElement.prototype.togglePopover)installed=seal(HTMLElement.prototype,'togglePopover',deny)&&installed;
        installed=denyPropertySetter(HTMLElement.prototype,'popover')&&installed}
        for(const owner of [SELF.HTMLButtonElement&&HTMLButtonElement.prototype,SELF.HTMLInputElement&&HTMLInputElement.prototype]){
        installed=denyPropertySetter(owner,'popoverTargetElement')&&installed;installed=denyPropertySetter(owner,'popoverTargetAction')&&installed}
        if(SELF.HTMLButtonElement){installed=denyPropertySetter(HTMLButtonElement.prototype,'commandForElement')&&installed;
        installed=denyPropertySetter(HTMLButtonElement.prototype,'command')&&installed}
        if(Element.prototype.requestFullscreen)installed=seal(Element.prototype,'requestFullscreen',deny)&&installed;
        if(Document.prototype.startViewTransition)installed=seal(Document.prototype,'startViewTransition',deny)&&installed;
        if(self.HTMLVideoElement&&HTMLVideoElement.prototype.requestPictureInPicture)
        installed=seal(HTMLVideoElement.prototype,'requestPictureInPicture',deny)&&installed;
        if(SELF.documentPictureInPicture&&SELF.documentPictureInPicture.requestWindow){const documentPipOwner=propertyOwner(SELF.documentPictureInPicture,'requestWindow');
        installed=!!documentPipOwner&&seal(documentPipOwner,'requestWindow',deny)&&installed}
        if(self.OffscreenCanvas){installed=seal(OffscreenCanvas.prototype,'getContext',()=>null)&&installed;
        if(OffscreenCanvas.prototype.transferToImageBitmap)installed=seal(OffscreenCanvas.prototype,'transferToImageBitmap',deny)&&installed}
        if(self.URL&&NativeURL.createObjectURL)installed=seal(NativeURL,'createObjectURL',deny)&&installed;
        const imageBitmapOwner=propertyOwner(self,'createImageBitmap');if(imageBitmapOwner&&imageBitmapOwner.createImageBitmap)
        installed=seal(imageBitmapOwner,'createImageBitmap',deny)&&installed;
        if(SELF.CSS&&SELF.CSS.paintWorklet&&SELF.CSS.paintWorklet.addModule){const workletOwner=propertyOwner(SELF.CSS.paintWorklet,'addModule');
        installed=!!workletOwner&&seal(workletOwner,'addModule',deny)&&installed}
        if(NAV.serviceWorker&&NAV.serviceWorker.register){const serviceWorkerOwner=propertyOwner(NAV.serviceWorker,'register');
        installed=!!serviceWorkerOwner&&seal(serviceWorkerOwner,'register',deny)&&installed}
        const WATCHED_ATTRIBUTES=['src','srcset','style','href','xlink:href','srcdoc','sandbox','target','formtarget','popover','popovertarget','popovertargetaction','commandfor','command','data-glosh-media-blocked','data-glosh-icon-safe',
        'd','points','viewBox','width','height','fill','stroke','transform','filter','mask','clip-path','x','y','x1','y1','x2','y2','cx','cy','r','rx','ry'];
        const observer=new NativeMutationObserver(records=>{ensureStyle();ensureCurtain();for(let index=0;index<records.length;index+=1){const record=records[index],type=read(mutationTypeProperty,record);
        if(type==='childList'){const added=copyList(read(mutationAddedProperty,record),nodeListLength);for(let addedIndex=0;addedIndex<added.length;addedIndex+=1)scan(added[addedIndex])}
        else{const target=read(mutationTargetProperty,record);if(target){sanitizeContainer(target);scan(target)}}}});
        mutationObserve.call(observer,documentElement(),{childList:true,subtree:true,attributes:true,attributeFilter:WATCHED_ATTRIBUTES});scan(documentElement());
        const failClosedDocument=()=>{mutationDisconnect.call(observer);let replaced=false;try{nativeDocOpen.call(DOC);nativeDocWrite.call(DOC,
        '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"><style>html,body{margin:0;background:#202124;color:#fff;font:16px sans-serif}</style></head><body>Glosh protected this document.</body></html>');
        nativeDocClose.call(DOC);replaced=true}catch(_){}try{invoke(nativeStop,SELF,[])}catch(_){}if(!replaced){try{const root=documentElement();let child=firstChildOf(root);
        while(child){nodeRemove.call(root,child);child=firstChildOf(root)}nativeSet.call(root,'data-glosh-h19-fail-closed','true')}catch(_){}}};
        const retireScript=(script)=>{try{if(!script)return false;nativeRemove.call(script,'nonce');const parent=parentOf(script);if(!parent)return false;
        nodeRemove.call(parent,script);return !connected(script)}catch(_){return false}};
        const retireBootstrapSecrets=()=>clearStyleNonce(shieldStyle)&&(!curtainStyle||clearStyleNonce(curtainStyle))&&retireScript(BOOTSTRAP_SCRIPT);
        let subdocumentGuardConsumed=false;const subdocumentGuard=()=>{if(subdocumentGuardConsumed)return false;subdocumentGuardConsumed=true;
        const guardScript=read(currentScriptProperty,DOC),callbackDeleted=ReflectDelete(SELF,'__gloshH19SubdocumentGuard__')&&!descriptor(SELF,'__gloshH19SubdocumentGuard__');
        if(!callbackDeleted||!guardScript||read(scriptSrcProperty,guardScript)!==''||!retireScript(guardScript)){failClosedDocument();return false}return true};
        if(!installed){failClosedDocument();return}if(!retireBootstrapSecrets()){failClosedDocument();return}if(!TOP_LEVEL){
        const parserFailClosed=SELF.__gloshH19ParserBarrierFailClosed__,parserFailClosedRetire=parserFailClosed&&descriptor(parserFailClosed,'retire');
        try{if(typeof parserFailClosed!=='function'||!parserFailClosedRetire||typeof parserFailClosedRetire.value!=='function'||descriptor(SELF,'__gloshH19SubdocumentGuard__')){failClosedDocument();return}
        ObjectDefine(SELF,'__gloshH19SubdocumentGuard__',{value:subdocumentGuard,writable:false,enumerable:false,configurable:true})}catch(_){failClosedDocument()}return}
        let lifecycle=0,currentLifecycle=0,activePhase='idle',challenge='',authorityArmed=false;
        const showCurtain=()=>{curtainRequired=true;if(ensureCurtain())return true;failClosedDocument();return false};
        const hideCurtain=()=>{curtainRequired=false;return ensureCurtain()};
        const acquireActiveDocument=()=>{if(!showCurtain()||!curtainLayer)return false;if(activeDocument())return true;try{
        nativeElementFocus.call(curtainLayer,{preventScroll:true})}catch(_){return false}return activeDocument()};
        const stopCrossDocumentTransition=(event,entry)=>{if(!entry)return true;try{const transition=read(entry,event);if(transition)nativeSkipViewTransition.call(transition);return true}
        catch(_){showCurtain();failClosedDocument();return false}};
        const validChallenge=(value)=>{if(value.length<22||value.length>128)return false;for(let index=0;index<value.length;index+=1){const code=charCode(value,index);
        if(!((code>=48&&code<=57)||(code>=65&&code<=90)||(code>=97&&code<=122)||code===45||code===95))return false}return true};
        const requestReady=(body)=>{const xhr=new NativeXMLHttpRequest();xhrOpen.call(xhr,'POST',READY_URL,false);xhrSetHeader.call(xhr,'Content-Type','text/plain;charset=UTF-8');
        xhrSend.call(xhr,body);if(read(xhrResponseUrlProperty,xhr)!==READY_URL)return null;return [read(xhrStatusProperty,xhr),stringOf(read(xhrResponseTextProperty,xhr)||'')]};
        const reloadParkedDocument=(event)=>{if(!authorityArmed||activePhase!=='parked'||!trustedEvent(event)||!activeDocument())return;activePhase='reloading';showCurtain();
        try{nativeLocationReload.call(NativeLocation)}catch(_){activePhase='parked';failClosedDocument()}};
        const parkDocument=()=>{activePhase='parked';challenge='';showCurtain();failClosedDocument();
        nativeAddEvent.call(SELF,'focus',reloadParkedDocument,true);nativeAddEvent.call(DOC,'visibilitychange',reloadParkedDocument,true)};
        const remoteRevoke=(oldLifecycle,oldChallenge)=>{if(!oldChallenge)return;try{requestReady('v2|REVOKE|'+READY+'|'+oldLifecycle+'|'+oldChallenge)}catch(_){}};
        const revokeReady=()=>{if(!authorityArmed)return;showCurtain();const oldLifecycle=currentLifecycle,oldChallenge=challenge;activePhase='revoked';challenge='';remoteRevoke(oldLifecycle,oldChallenge)};
        const rejectCurrent=()=>{showCurtain();const oldLifecycle=currentLifecycle,oldChallenge=challenge;activePhase='rejected';challenge='';remoteRevoke(oldLifecycle,oldChallenge);parkDocument()};
        const beginReadyLifecycle=()=>{if(!authorityArmed||activePhase!=='idle'||!activeDocument())return;activePhase='hello';lifecycle+=1;currentLifecycle=lifecycle;
        if(!showCurtain()){rejectCurrent();return}try{const hello=requestReady('v2|HELLO|'+READY+'|'+currentLifecycle);if(!hello||hello[0]!==200||!activeDocument()){rejectCurrent();return}
        const prefix='v2|CHALLENGE|',payload=hello[1];if(slice(payload,0,prefix.length)!==prefix){rejectCurrent();return}challenge=slice(payload,prefix.length);
        if(!validChallenge(challenge)||!activeDocument()){rejectCurrent();return}activePhase='prove';
        const proof=requestReady('v2|PROVE|'+READY+'|'+currentLifecycle+'|'+challenge);if(!proof||proof[0]!==204||!activeDocument()){rejectCurrent();return}
        if(!hideCurtain()||!activeDocument()){rejectCurrent();return}activePhase='present';
        const present=requestReady('v2|PRESENT|'+READY+'|'+currentLifecycle+'|'+challenge);
        if(!present||present[0]!==204||!activeDocument()){rejectCurrent();return}activePhase='released'}catch(_){rejectCurrent()}};
        const beginNewLifecycle=()=>{if(!authorityArmed||activePhase==='hello'||activePhase==='prove'||activePhase==='present')return;revokeReady();activePhase='idle';beginReadyLifecycle()};
        nativeAddEvent.call(SELF,'beforeunload',revokeReady,true);nativeAddEvent.call(SELF,'pagehide',revokeReady,true);
        if(NativePageSwapEvent)nativeAddEvent.call(SELF,'pageswap',event=>{if(!trustedEvent(event))return;if(!stopCrossDocumentTransition(event,pageSwapTransitionProperty))return;revokeReady()},true);
        if(NativePageRevealEvent)nativeAddEvent.call(SELF,'pagereveal',event=>{if(!trustedEvent(event)||!read(pageRevealTransitionProperty,event))return;
        if(!stopCrossDocumentTransition(event,pageRevealTransitionProperty))return;beginNewLifecycle()},true);
        nativeAddEvent.call(DOC,'freeze',revokeReady,true);nativeAddEvent.call(SELF,'focus',event=>{if(!trustedEvent(event)||!activeDocument())return;
        if(activePhase==='idle')beginReadyLifecycle();else if(activePhase==='rejected'||activePhase==='revoked')beginNewLifecycle()},true);
        nativeAddEvent.call(SELF,'pageshow',event=>{if(trustedEvent(event)&&persistedPage(event))beginNewLifecycle()},true);
        nativeAddEvent.call(SELF,'orientationchange',event=>{if(trustedEvent(event)&&activeDocument())beginNewLifecycle()},true);
        nativeAddEvent.call(DOC,'visibilitychange',event=>{if(visibilityState()==='visible'){if(trustedEvent(event))beginNewLifecycle()}else revokeReady()},true);
        let firstAuthorityComplete=false,parserBarrierConsumed=false,parserGuardConsumed=false;
        const parserBarrierCommit=(ready)=>{if(parserBarrierConsumed)return;parserBarrierConsumed=true;const script=read(currentScriptProperty,DOC);
        const exactScript=!!script&&read(scriptSrcProperty,script)===BARRIER_URL;const callbackDeleted=ReflectDelete(SELF,'__gloshH19ParserBarrierCommit__')&&!descriptor(SELF,'__gloshH19ParserBarrierCommit__');
        if(!callbackDeleted||!exactScript||ready!==true||!retireScript(script)){failClosedDocument();return}if(!acquireActiveDocument()){authorityArmed=true;parkDocument();return}
        authorityArmed=true;beginReadyLifecycle();firstAuthorityComplete=activePhase==='released';if(!firstAuthorityComplete)failClosedDocument()};
        const parserBarrierGuard=()=>{if(parserGuardConsumed)return false;parserGuardConsumed=true;const script=read(currentScriptProperty,DOC);
        const callbackDeleted=ReflectDelete(SELF,'__gloshH19ParserBarrierGuard__')&&!descriptor(SELF,'__gloshH19ParserBarrierGuard__');
        if(!callbackDeleted||!script||read(scriptSrcProperty,script)!==''||!retireScript(script)||!firstAuthorityComplete){failClosedDocument();return false}return true};
        const parserFailClosed=SELF.__gloshH19ParserBarrierFailClosed__,parserFailClosedRetire=parserFailClosed&&descriptor(parserFailClosed,'retire');
        try{if(typeof parserFailClosed!=='function'||!parserFailClosedRetire||typeof parserFailClosedRetire.value!=='function'||descriptor(SELF,'__gloshH19ParserBarrierCommit__')||descriptor(SELF,'__gloshH19ParserBarrierGuard__')){failClosedDocument();return}
        ObjectDefine(SELF,'__gloshH19ParserBarrierCommit__',{value:parserBarrierCommit,writable:false,enumerable:false,configurable:true});
        ObjectDefine(SELF,'__gloshH19ParserBarrierGuard__',{value:parserBarrierGuard,writable:false,enumerable:false,configurable:true})}catch(_){failClosedDocument();return}})();
        """.trimIndent().replace("\n", "")
}
