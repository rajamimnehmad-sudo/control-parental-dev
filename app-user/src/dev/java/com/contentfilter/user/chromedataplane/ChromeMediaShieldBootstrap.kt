package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract

internal object ChromeMediaShieldBootstrap {
    const val StyleElementId = "glosh-h19-media-shield"
    const val CurtainStyleElementId = "glosh-h19-document-curtain"
    const val ReadyElementId = "glosh-h19-ready"

    val css: String =
        "canvas,video,object,embed,frame,fencedframe,[srcdoc],img[src^='data:' i],img[src^='blob:' i]," +
            "img[srcset*='data:' i],img[srcset*='blob:' i],source[src^='data:' i],source[src^='blob:' i]," +
            "source[srcset*='data:' i],source[srcset*='blob:' i],input[type='image' i][src^='data:' i]," +
            "input[type='image' i][src^='blob:' i],iframe:not([data-glosh-network-frame='1'])," +
            "svg:not([data-glosh-icon-safe='1'])," +
            "[data-glosh-media-blocked='1']{visibility:hidden!important;opacity:0!important}" +
            "svg[data-glosh-icon-safe='1']{max-width:96px!important;max-height:96px!important;overflow:hidden!important;" +
            "transform:none!important;filter:none!important;mask:none!important;clip-path:none!important}" +
            "#$ReadyElementId{position:fixed!important;left:0!important;top:0!important;width:1px!important;" +
            "height:1px!important;overflow:hidden!important;opacity:.001!important;pointer-events:none!important}"

    val curtainCss: String =
        "html,body{background:#202124!important}" +
            "body>*{visibility:hidden!important;opacity:0!important}" +
            "#$ReadyElementId{visibility:visible!important;opacity:1!important}"

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
            .replace(TopLevelPlaceholder, topLevel.toString())

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
    private const val TopLevelPlaceholder = "__GLOSH_TOP_LEVEL__"
    private val ScriptTemplate =
        """
        (()=>{'use strict';
        const READY='__GLOSH_READY_TOKEN__',NONCE='__GLOSH_NONCE__',TOP_LEVEL=__GLOSH_TOP_LEVEL__,READY_URL='__GLOSH_READY_URL__';
        const STYLE_ID='glosh-h19-media-shield',CURTAIN_ID='glosh-h19-document-curtain',READY_ID='glosh-h19-ready';
        const CSS='__GLOSH_SHIELD_CSS__',FRAME_SANDBOX='allow-scripts allow-forms allow-popups-to-escape-sandbox';let installed=true;
        const SELF=self,DOC=document,NAV=navigator,BOOTSTRAP_SCRIPT=DOC.currentScript,ReflectApply=Reflect.apply,ObjectDefine=Object.defineProperty,ObjectDescribe=Object.getOwnPropertyDescriptor;
        const ObjectPrototype=Object.prototype,ObjectGetPrototype=Object.getPrototypeOf,ObjectHasOwn=ObjectPrototype.hasOwnProperty;
        const NativeString=String,NativeNumber=Number,NativeURL=URL,NativeDOMException=DOMException,NativeArray=Array,NativeEvent=Event,NativeMutationObserver=MutationObserver;
        const NativeXMLHttpRequest=SELF.XMLHttpRequest,NativeHTMLElement=SELF.HTMLElement,NativeCustomElements=SELF.customElements,NativeElementInternals=SELF.ElementInternals;
        const NumberIsSafeInteger=Number.isSafeInteger;
        const StringLower=String.prototype.toLowerCase,StringIncludes=String.prototype.includes,StringTrim=String.prototype.trim,StringLastIndex=String.prototype.lastIndexOf,StringSlice=String.prototype.slice;
        const SetHas=Set.prototype.has,SetAdd=Set.prototype.add,WeakSetHas=WeakSet.prototype.has,WeakSetAdd=WeakSet.prototype.add;
        const WeakMapGet=WeakMap.prototype.get,WeakMapSet=WeakMap.prototype.set,EventPrevent=Event.prototype.preventDefault,EventStopImmediate=Event.prototype.stopImmediatePropagation;
        const invoke=(fn,owner,args)=>ReflectApply(fn,owner,args),method=(fn)=>({call:(owner,...args)=>invoke(fn,owner,args),apply:(owner,args)=>invoke(fn,owner,args)});
        const xhrOpen=NativeXMLHttpRequest?method(NativeXMLHttpRequest.prototype.open):null;
        const xhrSend=NativeXMLHttpRequest?method(NativeXMLHttpRequest.prototype.send):null,xhrSetHeader=NativeXMLHttpRequest?method(NativeXMLHttpRequest.prototype.setRequestHeader):null;
        const nativeCustomDefine=NativeCustomElements&&SELF.CustomElementRegistry?method(CustomElementRegistry.prototype.define):null;
        const nativeAttachInternals=NativeHTMLElement&&NativeHTMLElement.prototype.attachInternals?method(NativeHTMLElement.prototype.attachInternals):null;
        const descriptor=(owner,name)=>ObjectDescribe(owner,name),propertyOwner=(value,name)=>{let owner=value;while(owner&&!invoke(ObjectHasOwn,owner,[name]))owner=ObjectGetPrototype(owner);return owner};
        const propertyDescriptor=(value,name)=>{const owner=propertyOwner(value,name);return owner?descriptor(owner,name):null};
        const read=(entry,value)=>entry&&entry.get?invoke(entry.get,value,[]):undefined,stringOf=(value)=>NativeString(value),lower=(value)=>invoke(StringLower,stringOf(value),[]);
        const includes=(value,needle)=>invoke(StringIncludes,stringOf(value),[needle]),trim=(value)=>invoke(StringTrim,stringOf(value),[]),lastIndex=(value,needle)=>invoke(StringLastIndex,stringOf(value),[needle]);
        const slice=(value,start,end)=>invoke(StringSlice,stringOf(value),end===undefined?[start]:[start,end]);
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
        const fragmentQueryAll=method(DocumentFragment.prototype.querySelectorAll),nativeAddEvent=method(EventTarget.prototype.addEventListener);
        const nodeAppend=method(Node.prototype.appendChild),nodeInsert=method(Node.prototype.insertBefore),nodeReplace=method(Node.prototype.replaceChild),nodeRemove=method(Node.prototype.removeChild);
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
        const visibilityProperty=propertyDescriptor(DOC,'visibilityState'),eventTargetProperty=propertyDescriptor(new NativeEvent('glosh'),'target');
        const eventTrustedProperty=propertyDescriptor(new NativeEvent('glosh'),'isTrusted');
        const xhrStatusProperty=NativeXMLHttpRequest?propertyDescriptor(NativeXMLHttpRequest.prototype,'status'):null;
        const xhrResponseUrlProperty=NativeXMLHttpRequest?propertyDescriptor(NativeXMLHttpRequest.prototype,'responseURL'):null;
        const templateContentProperty=self.HTMLTemplateElement?propertyDescriptor(HTMLTemplateElement.prototype,'content'):null,nativeStop=SELF.stop;
        const mutationTypeProperty=self.MutationRecord?propertyDescriptor(MutationRecord.prototype,'type'):null;
        const mutationTargetProperty=self.MutationRecord?propertyDescriptor(MutationRecord.prototype,'target'):null;
        const mutationAddedProperty=self.MutationRecord?propertyDescriptor(MutationRecord.prototype,'addedNodes'):null;
        const nodeListLength=self.NodeList?propertyDescriptor(NodeList.prototype,'length'):null,namedMapLength=self.NamedNodeMap?propertyDescriptor(NamedNodeMap.prototype,'length'):null;
        const attrNameProperty=self.Attr?propertyDescriptor(Attr.prototype,'name'):null,attrValueProperty=self.Attr?propertyDescriptor(Attr.prototype,'value'):null;
        const attrNamespaceProperty=self.Attr?propertyDescriptor(Attr.prototype,'namespaceURI'):null,attrLocalNameProperty=self.Attr?propertyDescriptor(Attr.prototype,'localName'):null;
        const attrOwnerProperty=self.Attr?propertyDescriptor(Attr.prototype,'ownerElement'):null,urlProtocolProperty=propertyDescriptor(new NativeURL('https://glosh.invalid/'),'protocol');
        const iframeSandboxProperty=self.HTMLIFrameElement?propertyDescriptor(HTMLIFrameElement.prototype,'sandbox'):null;
        const attributeStyleMapProperty=self.StylePropertyMap?propertyDescriptor(DOC.documentElement,'attributeStyleMap'):null;
        const internalsAriaLabelProperty=NativeElementInternals?propertyDescriptor(NativeElementInternals.prototype,'ariaLabel'):null;
        const requiredPrimordials=[nodeText,nodeValue,nodeTypeProperty,localNameProperty,parentNodeProperty,firstChildProperty,isConnectedProperty,baseUriProperty,styleProperty,
        elementAttributesProperty,documentElementProperty,documentHeadProperty,visibilityProperty,eventTargetProperty,eventTrustedProperty,mutationTypeProperty,mutationTargetProperty,mutationAddedProperty,
        nodeListLength,namedMapLength,urlProtocolProperty,templateContentProperty];
        if(TOP_LEVEL&&(!NativeXMLHttpRequest||!xhrOpen||!xhrSend||!xhrSetHeader||!xhrStatusProperty||!xhrResponseUrlProperty||!NativeHTMLElement||!NativeCustomElements||!nativeCustomDefine||!nativeAttachInternals||!internalsAriaLabelProperty||!internalsAriaLabelProperty.set))installed=false;
        for(let index=0;index<requiredPrimordials.length;index+=1)if(!requiredPrimordials[index])installed=false;
        if(self.Attr&&(!attrNameProperty||!attrValueProperty||!attrNamespaceProperty||!attrLocalNameProperty||!attrOwnerProperty))installed=false;
        if(self.HTMLIFrameElement&&!iframeSandboxProperty)installed=false;if(self.StylePropertyMap&&!attributeStyleMapProperty)installed=false;
        const nodeTypeOf=(value)=>read(nodeTypeProperty,value),localNameOf=(value)=>lower(read(localNameProperty,value)||'');
        const parentOf=(value)=>read(parentNodeProperty,value),firstChildOf=(value)=>read(firstChildProperty,value),connected=(value)=>read(isConnectedProperty,value)===true;
        const styleOf=(value)=>read(styleProperty,value),attributesOf=(value)=>copyList(read(elementAttributesProperty,value),namedMapLength);
        const documentElement=()=>read(documentElementProperty,DOC),documentHead=()=>read(documentHeadProperty,DOC);
        const templateContent=(value)=>read(templateContentProperty,value);
        const visibilityState=()=>read(visibilityProperty,DOC),eventTarget=(event)=>read(eventTargetProperty,event),trustedEvent=(event)=>read(eventTrustedProperty,event)===true;
        const attrName=(attribute)=>stringOf(read(attrNameProperty,attribute)||''),attrValueOf=(attribute)=>stringOf(read(attrValueProperty,attribute)||'');
        const attrNamespace=(attribute)=>read(attrNamespaceProperty,attribute),attrLocalName=(attribute)=>stringOf(read(attrLocalNameProperty,attribute)||'');
        const attrOwner=(attribute)=>read(attrOwnerProperty,attribute),sandboxOf=(frame)=>read(iframeSandboxProperty,frame);
        const lockedSandboxes=new WeakSet(),protectedNodes=new WeakSet(),protectedIconNodes=new WeakSet(),protectedStyleMaps=new WeakSet(),protectedSheets=new WeakSet(),protectedMedias=new WeakSet(),protectedDescendants=new WeakMap();
        const styleOwners=new WeakMap(),styleMapOwners=new WeakMap(),attributeOwners=new WeakMap();let readyHost=null;
        if(!BOOTSTRAP_SCRIPT)installed=false;
        const watchStyle=(element)=>{try{invoke(WeakMapSet,styleOwners,[styleOf(element),element]);if(attributeStyleMapProperty){const map=read(attributeStyleMapProperty,element);
        if(map)invoke(WeakMapSet,styleMapOwners,[map,element])}}catch(_){}};
        const hide=(element)=>{if(!element||nodeTypeOf(element)!==1)return;const style=styleOf(element);watchStyle(element);if(nativeGet.call(element,'data-glosh-media-blocked')!=='1')nativeSet.call(element,'data-glosh-media-blocked','1');
        if(nativeStyleGet.call(style,'visibility')!=='hidden'||nativeStylePriority.call(style,'visibility')!=='important')nativeStyleSet.call(style,'visibility','hidden','important');
        if(nativeStyleGet.call(style,'opacity')!=='0'||nativeStylePriority.call(style,'opacity')!=='important')nativeStyleSet.call(style,'opacity','0','important')};
        const unhide=(element)=>{if(nativeGet.call(element,'data-glosh-media-blocked')!=='1')return;nativeRemove.call(element,'data-glosh-media-blocked');
        const style=styleOf(element);nativeStyleRemove.call(style,'visibility');nativeStyleRemove.call(style,'opacity')};
        const networkUrl=(value)=>{if(!value)return false;try{const u=new NativeURL(stringOf(value),read(baseUriProperty,DOC));const protocol=read(urlProtocolProperty,u);
        return protocol==='https:'||protocol==='http:'}catch(_){return false}};
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
        const sanitizeElement=(element)=>{const tag=localNameOf(element);
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
        if(structurallyBlocked||includes(values,'data:')||includes(values,'blob:')){if(tag==='source'){nativeRemove.call(element,'src');nativeRemove.call(element,'srcset')}hide(element)}else unhide(element)}
        const style=lower(nativeGet.call(element,'style')||'');if(includes(style,'url(data:')||includes(style,'url(blob:'))hide(element);
        if(tag==='svg'){if(safeIcon(element)&&lockIconGeometry(element)){}
        else{nativeRemove.call(element,'data-glosh-icon-safe');let child=firstChildOf(element);while(child){nodeRemove.call(element,child);child=firstChildOf(element)}hide(element)}}};
        const scan=(root)=>{if(!root)return;const type=nodeTypeOf(root);if(type===1)sanitizeElement(root);const query=type===1?elementQueryAll:fragmentQueryAll;
        if(query){const nodes=copyList(query.call(root,'canvas,video,object,embed,frame,fencedframe,iframe,img,source,input[type="image" i],svg,[srcdoc],[style]'),nodeListLength);
        for(let index=0;index<nodes.length;index+=1)sanitizeElement(nodes[index])}};
        const sanitizeContainer=(node)=>{if(!node||nodeTypeOf(node)!==1)return;sanitizeElement(node);const svg=elementClosest.call(node,'svg');if(svg&&svg!==node)sanitizeElement(svg)};
        const shieldStyle=DOC.getElementById(STYLE_ID),curtainStyle=TOP_LEVEL?DOC.getElementById(CURTAIN_ID):null;
        if(!shieldStyle||(TOP_LEVEL&&!curtainStyle))installed=false;else{watchStyle(shieldStyle);if(curtainStyle)watchStyle(curtainStyle)}
        const styleSheetProperty=shieldStyle?propertyDescriptor(shieldStyle,'sheet'):null,styleNonceProperty=shieldStyle?propertyDescriptor(shieldStyle,'nonce'):null;
        const cssParentRuleProperty=propertyDescriptor(CSSStyleDeclaration.prototype,'parentRule');
        const cssParentSheetProperty=self.CSSRule?propertyDescriptor(CSSRule.prototype,'parentStyleSheet'):null;
        const styleSheetMediaProperty=self.StyleSheet?propertyDescriptor(StyleSheet.prototype,'media'):null;
        const cssRulesProperty=self.CSSStyleSheet?propertyDescriptor(CSSStyleSheet.prototype,'cssRules'):null;
        const cssRuleListLength=self.CSSRuleList?propertyDescriptor(CSSRuleList.prototype,'length'):null;
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
        if(!styleSheetProperty||!shieldSheet||(TOP_LEVEL&&!curtainSheet)||!styleNonceProperty||!styleNonceProperty.get||!styleNonceProperty.set||!cssParentRuleProperty||!cssParentSheetProperty||!styleSheetMediaProperty||(self.Range&&!rangeAncestorProperty))installed=false;
        const clearStyleNonce=(style)=>{try{invoke(styleNonceProperty.set,style,['']);nativeRemove.call(style,'nonce');
        return read(styleNonceProperty,style)===''&&!nativeHas.call(style,'nonce')}catch(_){return false}};
        let curtainRequired=TOP_LEVEL;
        const protectedNode=(node)=>!!node&&(invoke(WeakSetHas,protectedNodes,[node])||(readyHost!==null&&node===readyHost));
        const insideProtected=(node)=>{let current=node;for(let depth=0;current&&depth<128;depth+=1){if(protectedNode(current))return true;current=parentOf(current)}return false};
        const mappedProtected=(node)=>node?invoke(WeakMapGet,protectedDescendants,[node]):null;
        const containsProtected=(node)=>{if(!node)return false;const mapped=mappedProtected(node);return insideProtected(node)||
        !!((shieldStyle&&nodeContains.call(node,shieldStyle))||(curtainStyle&&nodeContains.call(node,curtainStyle))||(readyHost&&nodeContains.call(node,readyHost))||
        (mapped&&nodeContains.call(node,mapped)))};
        const restoreMappedProtected=(node)=>{const mapped=mappedProtected(node);if(!mapped)return null;if(parentOf(mapped)!==node)nodeInsert.call(node,mapped,firstChildOf(node));
        watchStyle(mapped);registerProtectedSheet(read(styleSheetProperty,mapped));return mapped};
        const rejectProtectedMove=(node,parent)=>{if(containsProtected(node)&&(node!==readyHost||parent!==documentElement()))deny()};
        const ensureStyle=()=>{if(!shieldStyle)return false;if(!connected(shieldStyle)){const parent=documentHead()||documentElement();
        nodeInsert.call(parent,shieldStyle,firstChildOf(parent))}if(read(nodeText,shieldStyle)!==CSS&&nodeText&&nodeText.set)invoke(nodeText.set,shieldStyle,[CSS]);return true};
        const ensureCurtain=()=>{if(!TOP_LEVEL)return true;if(!curtainStyle||!connected(curtainStyle))return false;
        if(curtainRequired){if(nativeHas.call(curtainStyle,'media'))nativeRemove.call(curtainStyle,'media')}
        else if(nativeGet.call(curtainStyle,'media')!=='not all')nativeSet.call(curtainStyle,'media','not all');return connected(curtainStyle)&&
        (curtainRequired?!nativeHas.call(curtainStyle,'media'):nativeGet.call(curtainStyle,'media')==='not all')};
        if(shieldStyle)invoke(WeakSetAdd,protectedNodes,[shieldStyle]);if(curtainStyle)invoke(WeakSetAdd,protectedNodes,[curtainStyle]);
        registerProtectedSheet(shieldSheet);registerProtectedSheet(curtainSheet);
        installed=ensureStyle()&&ensureCurtain()&&installed;
        installed=seal(Node.prototype,'appendChild',function(node){if(insideProtected(this))deny();rejectProtectedMove(node,this);scan(node);const result=nodeAppend.call(this,node);sanitizeContainer(this);return result})&&installed;
        installed=seal(Node.prototype,'insertBefore',function(node,ref){if(insideProtected(this))deny();rejectProtectedMove(node,this);scan(node);const result=nodeInsert.call(this,node,ref);sanitizeContainer(this);return result})&&installed;
        installed=seal(Node.prototype,'replaceChild',function(node,old){if(insideProtected(this)||containsProtected(old))deny();rejectProtectedMove(node,this);scan(node);const result=nodeReplace.call(this,node,old);sanitizeContainer(this);return result})&&installed;
        installed=seal(Node.prototype,'removeChild',function(node){if(containsProtected(node))deny();return nodeRemove.call(this,node)})&&installed;
        const sealInsertion=(owner,name,removesChildren)=>{if(!owner||!owner[name])return true;const original=owner[name];return seal(owner,name,function(...args){
        const mapped=removesChildren?mappedProtected(this):null;if(removesChildren?(!mapped&&containsProtected(this)):insideProtected(this))deny();for(let index=0;index<args.length;index+=1){const value=args[index];
        if(value&&nodeTypeOf(value)){rejectProtectedMove(value,this);scan(value)}}const parent=parentOf(this);const result=invoke(original,this,args);scan(this);
        if(mapped)restoreMappedProtected(this);if(parent)sanitizeContainer(parent);return result})};
        for(const owner of [Element.prototype,Document.prototype,DocumentFragment.prototype])for(const name of ['append','prepend'])
        installed=sealInsertion(owner,name,false)&&installed;
        for(const owner of [Element.prototype,Document.prototype,DocumentFragment.prototype])installed=sealInsertion(owner,'replaceChildren',true)&&installed;
        for(const owner of [Element.prototype,self.CharacterData&&CharacterData.prototype,self.DocumentType&&DocumentType.prototype])
        for(const name of ['before','after','replaceWith'])installed=sealInsertion(owner,name)&&installed;
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
        if(self.Range&&Range.prototype.insertNode){const rangeInsert=Range.prototype.insertNode;installed=seal(Range.prototype,'insertNode',function(node){
        const ancestor=read(rangeAncestorProperty,this);if(insideProtected(ancestor))deny();rejectProtectedMove(node,ancestor);scan(node);return invoke(rangeInsert,this,[node])})&&installed}
        if(self.Range&&Range.prototype.surroundContents){const surround=Range.prototype.surroundContents;installed=seal(Range.prototype,'surroundContents',function(node){
        const ancestor=read(rangeAncestorProperty,this);if(containsProtected(ancestor))deny();rejectProtectedMove(node,ancestor);scan(node);const result=invoke(surround,this,[node]);scan(node);return result})&&installed}
        if(self.Range){for(const name of ['deleteContents','extractContents']){const original=Range.prototype[name];if(original)installed=seal(Range.prototype,name,function(...args){
        if(containsProtected(read(rangeAncestorProperty,this)))deny();return invoke(original,this,args)})&&installed}}
        const selectionTouchesProtected=(selection)=>{if(!selection||!selectionRangeCountProperty||!selectionGetRangeAt)return true;const count=read(selectionRangeCountProperty,selection);
        if(!NumberIsSafeInteger(count)||count<0||count>64)return true;for(let index=0;index<count;index+=1){const range=selectionGetRangeAt.call(selection,index);
        if(containsProtected(read(rangeAncestorProperty,range)))return true}return false};
        if(self.Selection&&Selection.prototype.deleteFromDocument){const deleteFromDocument=Selection.prototype.deleteFromDocument;
        installed=seal(Selection.prototype,'deleteFromDocument',function(){if(selectionTouchesProtected(this))deny();return invoke(deleteFromDocument,this,[])})&&installed}
        const adopt=Document.prototype.adoptNode;installed=seal(Document.prototype,'adoptNode',function(node){if(containsProtected(node))deny();const result=invoke(adopt,this,[node]);scan(result);return result})&&installed;
        installed=seal(Element.prototype,'setAttribute',function(name,value){const attributeName=stringOf(name),attributeValue=stringOf(value),key=lower(attributeName),tag=localNameOf(this);
        if(protectedNode(this)||invoke(WeakSetHas,protectedIconNodes,[this]))deny();if(oneOf(tag,['a','area','form','base'])&&key==='target')return nativeSet.call(this,'target','_self');
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
        if(protectedNode(this)||invoke(WeakSetHas,protectedIconNodes,[this]))deny();
        if(tag==='iframe'&&(key==='sandbox'||key==='srcdoc')){sanitizeElement(this);return nativeHas.call(this,key)}
        const result=nativeToggle.call(this,attributeName,force);sanitizeContainer(this);return result})&&installed;
        const nativeSetNS=method(Element.prototype.setAttributeNS),nativeRemoveNS=method(Element.prototype.removeAttributeNS);
        installed=seal(Element.prototype,'setAttributeNS',function(namespace,name,value){const namespaceValue=namespace===null?null:stringOf(namespace),attributeName=stringOf(name),attributeValue=stringOf(value);
        const colon=lastIndex(attributeName,':'),key=lower(colon>=0?slice(attributeName,colon+1):attributeName),tag=localNameOf(this);
        if(protectedNode(this)||invoke(WeakSetHas,protectedIconNodes,[this]))deny();if(oneOf(tag,['a','area','form','base'])&&key==='target')return nativeSet.call(this,'target','_self');
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
        const forceSelfTarget=(owner,name)=>{if(!owner)return true;const entry=descriptor(owner,name);if(!entry||!entry.get||!entry.set)return false;
        try{ObjectDefine(owner,name,{get:entry.get,set:function(){invoke(entry.set,this,['_self'])},configurable:false});return true}catch(_){return false}};
        for(const pair of [[self.HTMLAnchorElement&&HTMLAnchorElement.prototype,'target'],[self.HTMLAreaElement&&HTMLAreaElement.prototype,'target'],
        [self.HTMLFormElement&&HTMLFormElement.prototype,'target'],[self.HTMLBaseElement&&HTMLBaseElement.prototype,'target'],
        [self.HTMLButtonElement&&HTMLButtonElement.prototype,'formTarget'],[self.HTMLInputElement&&HTMLInputElement.prototype,'formTarget']])
        installed=forceSelfTarget(pair[0],pair[1])&&installed;
        const protectedDeclaration=(value)=>{const owner=invoke(WeakMapGet,styleOwners,[value]);if(owner&&(protectedNode(owner)||invoke(WeakSetHas,protectedIconNodes,[owner])||nativeGet.call(owner,'data-glosh-media-blocked')==='1'||
        nativeGet.call(owner,'data-glosh-icon-safe')==='1'))return true;try{const rule=read(cssParentRuleProperty,value),sheet=rule&&read(cssParentSheetProperty,rule);
        return !!rule&&invoke(WeakSetHas,protectedSheets,[sheet])}catch(_){return false}};
        installed=seal(CSSStyleDeclaration.prototype,'setProperty',function(...args){if(protectedDeclaration(this))deny();return nativeStyleSet.apply(this,args)})&&installed;
        installed=seal(CSSStyleDeclaration.prototype,'removeProperty',function(...args){if(protectedDeclaration(this))deny();return nativeStyleRemove.apply(this,args)})&&installed;
        for(const name of ['all','display','boxSizing','width','height','minWidth','minHeight','maxWidth','maxHeight','margin','padding','visibility','opacity','zoom','scale','rotate','translate','transform','transformOrigin',
        'filter','mask','clipPath','animation','transition','position','inset','background','boxShadow','border','overflow','contain','pointerEvents','color','direction','unicodeBidi','d','fill','stroke','fillRule']){const entry=descriptor(CSSStyleDeclaration.prototype,name);
        if(entry&&entry.get&&entry.set){try{ObjectDefine(CSSStyleDeclaration.prototype,name,{get:entry.get,set:function(value){
        if(protectedDeclaration(this))deny();invoke(entry.set,this,[value])},configurable:false})}catch(_){installed=false}}}
        const cssText=descriptor(CSSStyleDeclaration.prototype,'cssText');if(cssText&&cssText.get&&cssText.set){try{
        ObjectDefine(CSSStyleDeclaration.prototype,'cssText',{get:cssText.get,set:function(value){if(protectedDeclaration(this))deny();invoke(cssText.set,this,[value])},configurable:false})
        }catch(_){installed=false}}else installed=false;
        if(self.CSSStyleSheet){for(const name of ['insertRule','deleteRule','addRule','removeRule','replace','replaceSync']){const original=CSSStyleSheet.prototype[name];if(original)
        installed=seal(CSSStyleSheet.prototype,name,function(...args){if(invoke(WeakSetHas,protectedSheets,[this]))deny();return invoke(original,this,args)})&&installed}
        const disabled=propertyDescriptor(CSSStyleSheet.prototype,'disabled');if(disabled&&disabled.get&&disabled.set){try{
        ObjectDefine(CSSStyleSheet.prototype,'disabled',{get:disabled.get,set:function(value){if(invoke(WeakSetHas,protectedSheets,[this]))deny();invoke(disabled.set,this,[value])},configurable:false})
        }catch(_){installed=false}}}
        if(self.MediaList){for(const name of ['appendMedium','deleteMedium']){const original=MediaList.prototype[name];if(original)
        installed=seal(MediaList.prototype,name,function(...args){if(invoke(WeakSetHas,protectedMedias,[this]))deny();return invoke(original,this,args)})&&installed}
        const mediaText=propertyDescriptor(MediaList.prototype,'mediaText');if(mediaText&&mediaText.get&&mediaText.set){try{
        ObjectDefine(MediaList.prototype,'mediaText',{get:mediaText.get,set:function(value){if(invoke(WeakSetHas,protectedMedias,[this]))deny();invoke(mediaText.set,this,[value])},configurable:false})
        }catch(_){installed=false}}}
        const protectedStyleMap=(value)=>{if(invoke(WeakSetHas,protectedStyleMaps,[value]))return true;const owner=invoke(WeakMapGet,styleMapOwners,[value]);
        return !!owner&&(protectedNode(owner)||invoke(WeakSetHas,protectedIconNodes,[owner])||nativeGet.call(owner,'data-glosh-media-blocked')==='1'||nativeGet.call(owner,'data-glosh-icon-safe')==='1')};
        if(self.StylePropertyMap){for(const name of ['set','append','delete','clear']){const original=StylePropertyMap.prototype[name];if(original)
        installed=seal(StylePropertyMap.prototype,name,function(...args){if(protectedStyleMap(this))deny();return invoke(original,this,args)})&&installed}}
        if(self.CSSStyleRule){const selectorText=propertyDescriptor(CSSStyleRule.prototype,'selectorText');if(selectorText&&selectorText.get&&selectorText.set){try{
        ObjectDefine(CSSStyleRule.prototype,'selectorText',{get:selectorText.get,set:function(value){if(invoke(WeakSetHas,protectedSheets,[read(cssParentSheetProperty,this)]))deny();
        invoke(selectorText.set,this,[value])},configurable:false})}catch(_){installed=false}}}
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
        const create=method(Document.prototype.createElement),createNS=method(Document.prototype.createElementNS),importNode=method(Document.prototype.importNode);
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
        const originalAttach=Element.prototype.attachShadow;installed=seal(Element.prototype,'attachShadow',function(init){const root=invoke(originalAttach,this,[init]);
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
        const WATCHED_ATTRIBUTES=['src','srcset','style','href','xlink:href','srcdoc','sandbox','target','formtarget','data-glosh-media-blocked','data-glosh-icon-safe',
        'd','points','viewBox','width','height','fill','stroke','transform','filter','mask','clip-path','x','y','x1','y1','x2','y2','cx','cy','r','rx','ry'];
        const observer=new NativeMutationObserver(records=>{ensureStyle();ensureCurtain();for(let index=0;index<records.length;index+=1){const record=records[index],type=read(mutationTypeProperty,record);
        if(type==='childList'){const added=copyList(read(mutationAddedProperty,record),nodeListLength);for(let addedIndex=0;addedIndex<added.length;addedIndex+=1)scan(added[addedIndex])}
        else{const target=read(mutationTargetProperty,record);if(target){sanitizeContainer(target);scan(target)}}}});
        mutationObserve.call(observer,documentElement(),{childList:true,subtree:true,attributes:true,attributeFilter:WATCHED_ATTRIBUTES});scan(documentElement());
        const failClosedDocument=()=>{mutationDisconnect.call(observer);let replaced=false;try{nativeDocOpen.call(DOC);nativeDocWrite.call(DOC,
        '<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width"><style>html,body{margin:0;background:#202124;color:#fff;font:16px sans-serif}</style></head><body>Glosh protected this document.</body></html>');
        nativeDocClose.call(DOC);replaced=true}catch(_){}try{invoke(nativeStop,SELF,[])}catch(_){}if(!replaced){try{const root=documentElement();let child=firstChildOf(root);
        while(child){nodeRemove.call(root,child);child=firstChildOf(root)}nativeSet.call(root,'data-glosh-h19-fail-closed','true')}catch(_){}}};
        const removeCurrentScript=()=>{try{const parent=BOOTSTRAP_SCRIPT&&parentOf(BOOTSTRAP_SCRIPT);if(!parent)return false;
        nodeRemove.call(parent,BOOTSTRAP_SCRIPT);return !connected(BOOTSTRAP_SCRIPT)}catch(_){return false}};
        const retireBootstrapSecrets=()=>clearStyleNonce(shieldStyle)&&(!curtainStyle||clearStyleNonce(curtainStyle))&&removeCurrentScript();
        if(!installed){failClosedDocument();return}if(!retireBootstrapSecrets()){failClosedDocument();return}if(!TOP_LEVEL)return;
        let creatingReadyHost=false,readyInternals=null;const ReadyHostClass=class extends NativeHTMLElement{constructor(){super();if(creatingReadyHost&&readyInternals===null)readyInternals=nativeAttachInternals.call(this)}};
        try{nativeCustomDefine.call(NativeCustomElements,'glosh-h19-ready-host',ReadyHostClass);creatingReadyHost=true;readyHost=create.call(DOC,'glosh-h19-ready-host')}catch(_){installed=false}finally{creatingReadyHost=false}
        if(!installed||!readyHost||!readyInternals){failClosedDocument();return}invoke(WeakSetAdd,protectedNodes,[readyHost]);nativeSet.call(readyHost,'id',READY_ID);nativeSet.call(readyHost,'role','group');nativeSet.call(readyHost,'tabindex','-1');watchStyle(readyHost);
        const readyStyle=styleOf(readyHost);nativeStyleSet.call(readyStyle,'position','fixed','important');nativeStyleSet.call(readyStyle,'left','0','important');
        nativeStyleSet.call(readyStyle,'top','0','important');nativeStyleSet.call(readyStyle,'width','1px','important');nativeStyleSet.call(readyStyle,'height','1px','important');
        nativeStyleSet.call(readyStyle,'overflow','hidden','important');nativeStyleSet.call(readyStyle,'color','transparent','important');
        nativeStyleSet.call(readyStyle,'font-size','1px','important');nativeStyleSet.call(readyStyle,'pointer-events','none','important');
        let lifecycle=0,visibleCycleRequested=false;
        const showCurtain=()=>{curtainRequired=true;return ensureCurtain()};
        const hideCurtain=()=>{curtainRequired=false;return ensureCurtain()};
        const detachMarker=()=>{const parent=parentOf(readyHost);if(connected(readyHost)&&parent)nodeRemove.call(parent,readyHost)};
        const clearReadyLabel=()=>{try{invoke(internalsAriaLabelProperty.set,readyInternals,['']);return true}catch(_){return false}};
        const revokeReady=()=>{showCurtain();clearReadyLabel();detachMarker();visibleCycleRequested=false};
        const beginReadyLifecycle=()=>{if(visibleCycleRequested||visibilityState()!=='visible')return;visibleCycleRequested=true;lifecycle+=1;const currentLifecycle=lifecycle;
        showCurtain();clearReadyLabel();detachMarker();try{const readyValue='glosh-shield-ready:'+READY+':'+currentLifecycle;
        const xhr=new NativeXMLHttpRequest();xhrOpen.call(xhr,'POST',READY_URL,false);xhrSetHeader.call(xhr,'Content-Type','text/plain;charset=UTF-8');
        xhrSend.call(xhr,'v1|'+READY+'|'+currentLifecycle);if(read(xhrStatusProperty,xhr)!==204||read(xhrResponseUrlProperty,xhr)!==READY_URL||
        currentLifecycle!==lifecycle||visibilityState()!=='visible'){revokeReady();return}nodeAppend.call(documentElement(),readyHost);invoke(internalsAriaLabelProperty.set,readyInternals,[readyValue]);
        if(!hideCurtain())revokeReady()}catch(_){revokeReady()}};
        nativeAddEvent.call(SELF,'beforeunload',revokeReady,true);nativeAddEvent.call(SELF,'pagehide',revokeReady,true);
        nativeAddEvent.call(SELF,'pageshow',event=>{if(trustedEvent(event))beginReadyLifecycle()},true);nativeAddEvent.call(DOC,'visibilitychange',event=>{
        if(visibilityState()==='visible'){if(trustedEvent(event))beginReadyLifecycle()}else revokeReady()},true);
        beginReadyLifecycle()})();
        """.trimIndent().replace("\n", "")
}
