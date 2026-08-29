package com.contentfilter.user.chromedataplane

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ChromeMediaShieldBootstrapContractTest {
    private val script = ChromeMediaShieldBootstrap.script(ReadyToken, StyleNonce)

    @Test
    fun `boot is synchronous parser-first and ready marker is private and lifecycle bound`() {
        assertFalse(script.contains("setTimeout"))
        assertFalse(script.contains("setInterval"))
        assertFalse(script.contains("requestAnimationFrame"))
        assertContains(script, "const readyRoot=invoke(originalAttach,readyHost,[{mode:'closed'}])")
        assertContains(script, "nativeSet.call(marker,'aria-label','glosh-shield-ready:'+READY+':'+currentLifecycle)")
        assertContains(script, "xhrOpen.call(xhr,'POST',READY_URL,false)")
        assertContains(script, "xhrSend.call(xhr,'v1|'+READY+'|'+currentLifecycle)")
        assertContains(script, "read(xhrStatusProperty,xhr)!==204")
        assertContains(script, "read(xhrResponseUrlProperty,xhr)!==READY_URL")
        assertContains(script, "showCurtain();detachMarker()")
        assertContains(script, "nodeAppend.call(documentElement(),readyHost);if(!hideCurtain())revokeReady()")
        assertContains(script, "nativeAddEvent.call(SELF,'beforeunload',revokeReady,true)")
        assertContains(script, "nativeAddEvent.call(SELF,'pagehide',revokeReady,true)")
        assertContains(script, "nativeAddEvent.call(DOC,'visibilitychange'")
        assertContains(script, "if(!installed){failClosedDocument();return}")
        assertContains(script, "nativeDocOpen.call(DOC)")
        assertContains(script, "Glosh protected this document.")
        assertContains(script, "retireBootstrapSecrets()")
        assertContains(
            ChromeMediaShieldBootstrap.curtainCss,
            "body>*{visibility:hidden!important;opacity:0!important}",
        )
        assertContains(script, "const hideCurtain=()=>{curtainRequired=false;return ensureCurtain()}")
        assertFalse(script.contains("nodeRemove.call(parent,curtainStyle)"))
        assertContains(script, "nativeSet.call(curtainStyle,'media','not all')")
    }

    @Test
    fun `subdocuments install the shield without exposing a foreground authority marker`() {
        val subdocumentScript = ChromeMediaShieldBootstrap.script(ReadyToken, StyleNonce, topLevel = false)

        assertContains(subdocumentScript, "TOP_LEVEL=false")
        assertContains(subdocumentScript, "if(!TOP_LEVEL)return")
    }

    @Test
    fun `local raster sinks and prototype bypasses are fail closed`() {
        assertContains(script, "seal(HTMLCanvasElement.prototype,'getContext',()=>null)")
        assertContains(script, "seal(HTMLCanvasElement.prototype,'transferControlToOffscreen',deny)")
        assertContains(script, "seal(OffscreenCanvas.prototype,'getContext',()=>null)")
        assertContains(script, "seal(imageBitmapOwner,'createImageBitmap',deny)")
        assertContains(script, "seal(serviceWorkerOwner,'register',deny)")
        assertContains(script, "seal(workletOwner,'addModule',deny)")
        assertContains(script, "seal(HTMLVideoElement.prototype,'requestPictureInPicture',deny)")
        assertContains(script, "seal(documentPipOwner,'requestWindow',deny)")
        assertContains(script, "seal(openOwner,'open'")
        assertContains(script, "'noopener=yes,noreferrer=yes'")
        assertFalse(script.contains("seal(self,'createImageBitmap'"))
        assertFalse(script.contains("seal(navigator.serviceWorker,'register'"))
    }

    @Test
    fun `every ordinary insertion seam sanitizes before a fresh iframe realm becomes usable`() {
        assertContains(script, "for(const owner of [Element.prototype,Document.prototype,DocumentFragment.prototype])")
        assertContains(script, "for(const name of ['append','prepend'])")
        assertContains(script, "sealInsertion(owner,'replaceChildren',true)")
        assertContains(script, "function(node){if(insideProtected(this))deny();rejectProtectedMove(node,this)")
        assertContains(script, "for(const name of ['before','after','replaceWith'])")
        assertContains(script, "seal(Element.prototype,'insertAdjacentElement'")
        assertContains(script, "seal(Element.prototype,'insertAdjacentText'")
        assertContains(script, "seal(Range.prototype,'insertNode'")
        assertContains(script, "owner.moveBefore")
        assertContains(script, "ObjectDefine(ShadowRoot.prototype,'innerHTML'")
        assertContains(script, "if(!mappedProtected(this))deny()")
        assertContains(script, "restoreMappedProtected(this);scan(this)")
        assertContains(script, "invoke(outer.set,this,[safeMarkup(value)])")
        assertContains(script, "put(args,1,safeMarkup")
        assertContains(script, "nativeSet.call(element,'sandbox',FRAME_SANDBOX)")
        assertFalse(script.contains("allow-same-origin"))
    }

    @Test
    fun `attribute object and namespace mutation seams remain fail closed`() {
        assertContains(script, "seal(Element.prototype,'setAttributeNS'")
        assertContains(script, "seal(Element.prototype,'removeAttributeNS'")
        assertContains(script, "['setAttributeNode','setAttributeNodeNS']")
        assertContains(script, "seal(Element.prototype,'removeAttributeNode'")
        assertContains(script, "ObjectDefine(Attr.prototype,'value'")
        assertContains(script, "ObjectDefine(Element.prototype,'attributes'")
        assertContains(script, "['setNamedItem','setNamedItemNS']")
        assertContains(script, "['removeNamedItem','removeNamedItemNS']")
        assertContains(script, "nodeTypeOf(this)===2&&routeAttributeValue")
    }

    @Test
    fun `dynamic security paths use captured primordials rather than mutable page prototypes`() {
        assertContains(script, "ReflectApply=Reflect.apply")
        assertContains(script, "const invoke=(fn,owner,args)=>ReflectApply(fn,owner,args)")
        assertContains(script, "method=(fn)=>({call:(owner,...args)=>invoke(fn,owner,args)")
        assertContains(script, "NativeString=String")
        assertContains(script, "NativeURL=URL")
        assertContains(script, "StringLower=String.prototype.toLowerCase")
        assertContains(script, "StringSlice=String.prototype.slice")
        assertContains(script, "nativeSet=method(Element.prototype.setAttribute)")
        assertContains(script, "create=method(Document.prototype.createElement)")
        assertContains(script, "return invoke(original,this,args)")
        assertFalse(script.contains("String(name).toLowerCase()"))
        assertFalse(script.contains("(this.localName||'').toLowerCase()"))
        assertFalse(script.contains(".localName"))
        assertFalse(script.contains(".nodeType"))
        assertFalse(script.contains("event.target"))
        assertFalse(script.contains("record.addedNodes"))
        assertFalse(script.contains("Object.prototype.hasOwnProperty.call"))
        assertFalse(script.contains("Function.prototype.call"))
        assertFalse(script.contains("Function.prototype.apply"))
        assertFalse(script.contains("original.apply(this,args)"))
        assertFalse(script.contains("Array.from("))
        assertFalse(script.contains(".some("))
        assertFalse(script.contains(".reduce("))
        assertFalse(script.contains(".forEach("))
        assertFalse(script.contains("new URL("))
        assertFalse(script.contains("attributeName.slice("))
        assertFalse(script.contains("lockedSandboxes.add("))
        assertFalse(script.contains("lockedSandboxes.has("))
        assertFalse(script.contains("styleOwners.get("))
        assertFalse(script.contains("styleOwners.set("))
        assertFalse(script.contains("records.forEach("))
        assertFalse(script.contains("addedNodes.forEach("))
    }

    @Test
    fun `sanitizers read native node and element identity after prototype tampering`() {
        assertContains(script, "nodeTypeProperty=propertyDescriptor(DOC,'nodeType')")
        assertContains(script, "localNameProperty=propertyDescriptor(DOC.documentElement,'localName')")
        assertContains(script, "const nodeTypeOf=(value)=>read(nodeTypeProperty,value)")
        assertContains(script, "localNameOf=(value)=>lower(read(localNameProperty,value)||'')")
        assertContains(
            script,
            "const safeElementName=(name)=>{const canonical=stringOf(name);return oneOf(lower(canonical),['object','embed','frame','fencedframe'])?'template':canonical}",
        )
        assertContains(script, "const initial=eventTarget(event),target=initial&&nodeTypeOf(initial)===1")
        assertContains(script, "const added=copyList(read(mutationAddedProperty,record),nodeListLength)")
        assertContains(
            script,
            "const put=(array,index,value)=>{ObjectDefine(array,index,{value,writable:true,enumerable:true,configurable:true})",
        )
        assertContains(script, "put(output,index,value[index])")
        assertFalse(script.contains("output[index]=value[index]"))
    }

    @Test
    fun `bootstrap source and complete ready token leave the light DOM before site scripts`() {
        assertContains(script, "BOOTSTRAP_SCRIPT=DOC.currentScript")
        assertContains(script, "if(!BOOTSTRAP_SCRIPT)installed=false")
        assertContains(
            script,
            "const removeCurrentScript=()=>{try{const parent=BOOTSTRAP_SCRIPT&&parentOf(BOOTSTRAP_SCRIPT);if(!parent)return false",
        )
        assertContains(script, "nodeRemove.call(parent,BOOTSTRAP_SCRIPT)")
        assertContains(
            script,
            "const clearStyleNonce=(style)=>{try{invoke(styleNonceProperty.set,style,['']);nativeRemove.call(style,'nonce')",
        )
        assertContains(script, "return read(styleNonceProperty,style)===''&&!nativeHas.call(style,'nonce')")
        assertContains(
            script,
            "const retireBootstrapSecrets=()=>clearStyleNonce(shieldStyle)&&(!curtainStyle||clearStyleNonce(curtainStyle))&&removeCurrentScript()",
        )
        assertContains(
            script,
            "if(!installed){failClosedDocument();return}if(!retireBootstrapSecrets()){failClosedDocument();return}",
        )
        assertContains(script, "if(!clearStyleNonce(style)){nodeRemove.call(root,style);failClosedDocument();deny()}")
        assertEquals(1, Regex(Regex.escape(ReadyToken)).findAll(script).count())
        assertEquals(1, Regex(Regex.escape(StyleNonce)).findAll(script).count())
        assertFalse(script.contains("data-glosh-ready-token"))
        assertFalse(script.contains("nativeSet.call(readyHost,'aria-label'"))
    }

    @Test
    fun `ready host and shield style cannot be moved or rewritten through ordinary APIs`() {
        assertContains(script, "readyHost=create.call(DOC,'span')")
        assertContains(script, "rejectProtectedMove")
        assertContains(script, "if(containsProtected(node))deny()")
        assertContains(script, "if(invoke(WeakSetHas,protectedSheets,[this]))deny()")
        assertContains(script, "if(containsProtected(this))deny()")
        assertContains(script, "ObjectDefine(HTMLStyleElement.prototype,name")
        assertContains(script, "ObjectDefine(CharacterData.prototype,'data'")
        assertContains(script, "for(const name of ['deleteContents','extractContents'])")
        assertContains(script, "seal(Selection.prototype,'deleteFromDocument'")
        assertContains(script, "ObjectDefine(HTMLElement.prototype,'innerText'")
        assertContains(script, "ObjectDefine(HTMLElement.prototype,'outerText'")
        assertContains(script, "ObjectDefine(CSSStyleRule.prototype,'selectorText'")
        assertContains(script, "seal(MediaList.prototype,name")
        assertContains(script, "ObjectDefine(MediaList.prototype,'mediaText'")
        assertContains(script, "seal(StylePropertyMap.prototype,name")
        assertContains(script, "attributeStyleMapProperty")
        assertContains(script, "styleMapOwners")
        assertContains(script, "protectedStyleMap(this)")
        assertContains(script, "registerProtectedSheet(shadowSheet)")
        assertContains(script, "restoreMappedProtected(root)")
        assertContains(script, "protectedDescendants")
        assertContains(script, "oneOf(lower(canonical),['object','embed','frame','fencedframe'])")
        assertContains(script, "const showCurtain=()=>{curtainRequired=true;return ensureCurtain()}")
        assertFalse(script.contains("data-glosh-curtain-released"))
    }

    @Test
    fun `media SVG is hidden unless it satisfies a bounded inert icon grammar`() {
        assertContains(script, "const ICON_TAGS=new Set")
        assertContains(script, "!invoke(SetHas,ICON_TAGS")
        assertContains(script, "['path','title','desc']")
        assertContains(script, "[href],[xlink\\\\:href],[filter],[mask],[clip-path]")
        assertContains(script, "ICON_PATH_CHARS")
        assertContains(script, "protectedIconNodes")
        assertContains(script, "['all','initial']")
        assertContains(script, "['d','path(\"'+d+'\")']")
        assertContains(script, "['zoom','1']")
        assertContains(script, "['scale','none']")
        assertContains(script, "nativeStylePriority.call(rootStyle,'all')==='important'")
        assertContains(script, "let child=firstChildOf(element);while(child){nodeRemove.call(element,child)")
        assertContains(script, "const WATCHED_ATTRIBUTES=")
        assertContains(script, "'d','points','viewBox','width','height','fill','stroke','transform'")
        assertContains(script, "if(target){sanitizeContainer(target);scan(target)}")
        assertContains(ChromeMediaShieldBootstrap.css, "svg:not([data-glosh-icon-safe='1'])")
    }

    @Test
    fun `normal CSS and generic workers remain available within the documented product boundary`() {
        assertFalse(ChromeMediaShieldBootstrap.css.contains("div"))
        assertFalse(ChromeMediaShieldBootstrap.css.contains("span"))
        assertFalse(ChromeMediaShieldBootstrap.css.contains("background-color"))
        assertFalse(script.contains("self.Worker"))
        assertFalse(script.contains("SharedWorker.prototype"))
        assertContains(script, "if(removesChildren?(!mapped&&containsProtected(this)):insideProtected(this))deny()")
    }

    private companion object {
        const val ReadyToken = "AAAAAAAAAAAAAAAAAAAAAA"
        const val StyleNonce = "BBBBBBBBBBBBBBBBBBBBBB"
    }
}
