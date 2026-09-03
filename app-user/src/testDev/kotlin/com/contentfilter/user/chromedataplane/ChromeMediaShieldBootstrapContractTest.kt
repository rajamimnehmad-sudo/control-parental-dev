package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromeMediaShieldDocumentIdentity
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import org.junit.Assume.assumeNoException
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChromeMediaShieldBootstrapContractTest {
    private val script = ChromeMediaShieldBootstrap.script(ReadyToken, StyleNonce)

    @Test
    fun `generated bootstrap remains valid JavaScript`() {
        val process =
            try {
                ProcessBuilder("node", "--check", "-").start()
            } catch (missingNode: Exception) {
                assumeNoException(missingNode)
                return
            }
        process.outputStream.bufferedWriter().use { it.write(script) }
        assertTrue(process.waitFor(10, TimeUnit.SECONDS))
        assertEquals("", process.errorStream.bufferedReader().readText())
        assertEquals(0, process.exitValue())
    }

    @Test
    fun `renderer observer revalidates attribute targets without rescanning their subtrees`() {
        val selfShield =
            ChromeMediaShieldBootstrap.script(
                ReadyToken,
                StyleNonce,
                selfShieldIdentity = identity(),
            )
        assertContains(selfShield, ChromePhotosDataPlaneLabContract.MediaShieldRendererMetricsPath)
        assertContains(
            selfShield,
            "const RENDERER_METRICS=new NativeArray(${ChromeMediaShieldRendererMetricsScript.FieldCount})",
        )
        assertContains(selfShield, "rendererApiCall(RMA_CREATE,RMF_FACTORY)")
        assertContains(selfShield, "scan(templateContent(template),RM_MARKUP,RMA_SAFE_MARKUP,RMF_MARKUP)")
        assertContains(selfShield, "scan(node,RM_GUARDED,RMA_APPEND_CHILD,RMF_DOM_GUARD)")
        assertContains(selfShield, "rendererMetric(0);rendererMetric(1,records.length)")
        assertContains(selfShield, "sanitizeContainer(target)")
        assertFalse(selfShield.contains("scan(target,RM_OBSERVER_ATTRIBUTE)"))
        assertContains(selfShield, "scan(added[addedIndex],RM_OBSERVER_CHILD)")
        assertContains(selfShield, "scan(added[addedIndex],RM_SHADOW)")
        assertFalse(selfShield.contains("scan(target,RM_SHADOW)"))
        assertContains(selfShield, "rendererScanStarted(origin,root,api,family)")
        assertContains(selfShield, ChromeMediaShieldRendererMetricsScript.SnapshotEvent)
        assertContains(selfShield, "if(rendererMetricsSent||!SELF_SHIELD||!selfReadyAccepted")
        assertFalse(selfShield.contains("setInterval"))
    }

    @Test
    fun `renderer metrics retries failures and seals one shot only after native 204`() {
        val reporting = ChromeMediaShieldRendererMetricsScript.reporting

        assertFalse(reporting.contains("rendererMetricsSent=true;try"))
        assertContains(reporting, "catch(_){return false}")
        assertContains(
            reporting,
            "const accepted=read(xhrResponseUrlProperty,xhr)===RENDERER_METRICS_RESPONSE_URL&&" +
                "read(xhrStatusProperty,xhr)===204;if(accepted)rendererMetricsSent=true;return accepted",
        )
        assertTrue(reporting.indexOf("xhrSend.call") < reporting.indexOf("const accepted="))
        assertTrue(reporting.indexOf("const accepted=") < reporting.indexOf("rendererMetricsSent=true"))
        assertEquals(1, Regex(Regex.escape("rendererMetricsSent=true")).findAll(reporting).count())
    }

    @Test
    fun `renderer metrics snapshots when hidden and keeps explicit and pagehide fallbacks`() {
        val reporting = ChromeMediaShieldRendererMetricsScript.reporting

        assertContains(
            reporting,
            "nativeAddEvent.call(DOC,'${ChromeMediaShieldRendererMetricsScript.SnapshotEvent}'," +
                "reportRendererMetrics,true)",
        )
        assertContains(
            reporting,
            "const reportRendererMetricsWhenHidden=()=>{if(visibilityState()==='hidden')reportRendererMetrics()}",
        )
        assertContains(
            reporting,
            "nativeAddEvent.call(DOC,'visibilitychange',reportRendererMetricsWhenHidden,true)",
        )
        assertContains(reporting, "nativeAddEvent.call(SELF,'pagehide',reportRendererMetrics,true)")
        assertTrue(reporting.indexOf("'visibilitychange'") < reporting.indexOf("'pagehide'"))
    }

    @Test
    fun `boot is parser-first and active document handshake keeps one barrier at every phase`() {
        assertFalse(script.contains("setTimeout"))
        assertFalse(script.contains("setInterval"))
        assertFalse(script.contains("requestAnimationFrame"))
        assertContains(script, "NativeHasFocusFunction=Document.prototype.hasFocus")
        assertContains(script, "nativeHasFocus=NativeHasFocusFunction?method(NativeHasFocusFunction):null")
        assertContains(script, "NativeElementFocusFunction=SELF.HTMLElement&&HTMLElement.prototype.focus")
        assertContains(script, "nativeElementFocus=NativeElementFocusFunction?method(NativeElementFocusFunction):null")
        assertContains(script, "NativeLocationReloadFunction=NativeLocation&&NativeLocation.reload")
        assertContains(
            script,
            "nativeLocationReload=NativeLocationReloadFunction?method(NativeLocationReloadFunction):null",
        )
        assertContains(script, "IS_TOP_LEVEL=SELF===SELF.top")
        assertContains(
            script,
            "const activeDocument=()=>TOP_LEVEL&&IS_TOP_LEVEL&&visibilityState()==='visible'&&hasNativeFocus()",
        )
        assertContains(
            script,
            "const acquireActiveDocument=()=>{if(!showCurtain()||!curtainLayer)return false;if(activeDocument())return true",
        )
        assertContains(script, "nativeElementFocus.call(curtainLayer,{preventScroll:true})")
        assertContains(script, "return activeDocument()}")
        assertFalse(script.contains("activeElement"))
        assertFalse(script.contains("new NativePageTransitionEvent"))
        assertContains(script, "propertyDescriptor(NativePageTransitionEvent.prototype,'persisted')")
        assertFalse(script.contains("readyMarker"))
        assertFalse(script.contains("readyHost"))
        assertContains(script, "xhrOpen.call(xhr,'POST',READY_URL,false)")
        assertContains(script, "requestReady('v2|HELLO|'+READY+'|'+currentLifecycle)")
        assertContains(script, "const prefix='v2|CHALLENGE|'")
        assertContains(script, "requestReady('v2|PROVE|'+READY+'|'+currentLifecycle+'|'+challenge)")
        assertContains(script, "requestReady('v2|PRESENT|'+READY+'|'+currentLifecycle+'|'+challenge)")
        assertContains(script, "requestReady('v2|REVOKE|'+READY+'|'+oldLifecycle+'|'+oldChallenge)")
        assertContains(script, "read(xhrResponseUrlProperty,xhr)!==READY_URL")
        assertTrue(
            script.indexOf("requestReady('v2|HELLO|'+READY+'|'+currentLifecycle)") <
                script.indexOf("requestReady('v2|PROVE|'+READY+'|'+currentLifecycle+'|'+challenge)"),
        )
        assertTrue(
            script.indexOf("requestReady('v2|PROVE|'+READY+'|'+currentLifecycle+'|'+challenge)") <
                script.indexOf("if(!hideCurtain()||!activeDocument()){rejectCurrent();return}activePhase='present'"),
        )
        assertTrue(
            script.indexOf("if(!hideCurtain()||!activeDocument()){rejectCurrent();return}activePhase='present'") <
                script.indexOf("requestReady('v2|PRESENT|'+READY+'|'+currentLifecycle+'|'+challenge)"),
        )
        assertTrue(
            script.indexOf("requestReady('v2|PRESENT|'+READY+'|'+currentLifecycle+'|'+challenge)") <
                script.indexOf("if(!present||present[0]!==204||!activeDocument()){rejectCurrent();return}"),
        )
        assertContains(script, "if(!present||present[0]!==204||!activeDocument()){rejectCurrent();return}")
        assertTrue(
            script.substringBefore("requestReady('v2|PRESENT|'+READY+'|'+currentLifecycle+'|'+challenge)")
                .contains("hideCurtain()"),
        )
        assertContains(
            script,
            "const rejectCurrent=()=>{showCurtain();const oldLifecycle=currentLifecycle,oldChallenge=challenge;" +
                "activePhase='rejected';challenge='';remoteRevoke(oldLifecycle,oldChallenge);parkDocument()}",
        )
        assertContains(script, "catch(_){rejectCurrent()}")
        assertContains(script, "const revokeReady=()=>{if(!authorityArmed)return;showCurtain()")
        assertContains(script, "catch(_){rejectCurrent()}")
        assertContains(
            script,
            "const parkDocument=()=>{activePhase='parked';challenge='';showCurtain();failClosedDocument()",
        )
        assertContains(script, "const parserBarrierCommit=(ready)=>")
        assertContains(script, "read(scriptSrcProperty,script)===BARRIER_URL")
        assertContains(script, "beginReadyLifecycle();firstAuthorityComplete=activePhase==='released'")
        assertContains(script, "const parserBarrierGuard=()=>")
        assertContains(script, "!retireScript(script)||!firstAuthorityComplete")
        assertContains(script, "nativeLocationReload.call(NativeLocation)")
        assertContains(script, "nativeSet.call(curtainLayer,'tabindex','-1')")
        assertTrue(
            script.indexOf("showCurtain()||!curtainLayer") <
                script.indexOf("nativeElementFocus.call(curtainLayer,{preventScroll:true})"),
        )
        assertTrue(
            script.indexOf("nativeElementFocus.call(curtainLayer,{preventScroll:true})") <
                script.indexOf("if(!acquireActiveDocument()){authorityArmed=true;parkDocument();return}"),
        )
        assertTrue(
            script.indexOf("nativeElementFocus.call(curtainLayer,{preventScroll:true})") <
                script.indexOf("authorityArmed=true;beginReadyLifecycle()"),
        )
        assertEquals(
            1,
            Regex(Regex.escape("nativeElementFocus.call(curtainLayer,{preventScroll:true})")).findAll(script).count(),
        )
        assertTrue(
            script.indexOf("const parserBarrierCommit=(ready)=>") >
                script.indexOf("nativeAddEvent.call(DOC,'visibilitychange'"),
        )
        assertContains(script, "nativeAddEvent.call(SELF,'beforeunload',revokeReady,true)")
        assertContains(script, "nativeAddEvent.call(SELF,'pagehide',revokeReady,true)")
        assertContains(script, "nativeAddEvent.call(DOC,'freeze',revokeReady,true)")
        assertContains(script, "nativeAddEvent.call(SELF,'focus'")
        assertContains(script, "activePhase==='rejected'||activePhase==='revoked'")
        assertContains(script, "nativeAddEvent.call(SELF,'orientationchange'")
        assertContains(script, "nativeAddEvent.call(DOC,'visibilitychange'")
        assertContains(
            script,
            "if(dialogClosedByProperty&&dialogClosedByProperty.set){invoke(dialogClosedByProperty.set,curtainLayer,['none'])",
        )
        assertContains(script, "nativeGet.call(curtainLayer,'closedby')!=='none'")
        assertContains(
            script,
            "nativeGet.call(curtainLayer,'closedby')==='none'&&read(dialogClosedByProperty,curtainLayer)==='none'",
        )
        assertTrue(
            script.indexOf("invoke(dialogClosedByProperty.set,curtainLayer,['none'])") <
                script.indexOf("nativeDialogShowModal.call(curtainLayer)"),
        )
        assertContains(script, "if(!installed){failClosedDocument('INSTALL',installFailure);return}")
        assertContains(script, "nativeDocOpen.call(DOC)")
        assertContains(script, "Glosh protected this document.")
        assertContains(script, "retireBootstrapSecrets()")
        assertContains(
            ChromeMediaShieldBootstrap.curtainCss,
            "html:not([${ChromeMediaShieldBootstrap.CurtainReleaseAttribute}='1']) body>*{" +
                "visibility:hidden!important;opacity:0!important}",
        )
        assertContains(ChromeMediaShieldBootstrap.curtainCss, "body{background:#ffffff!important}")
        assertContains(script, "['background','#ffffff'],['color','#000000']")
        assertContains(script, "StringCharCodeAt=String.prototype.charCodeAt")
        assertContains(script, "const code=charCode(value,index)")
        assertFalse(script.contains("value.charCodeAt(index)"))
        assertContains(script, "CURTAIN_LAYER_ID='${ChromeMediaShieldBootstrap.CurtainElementId}'")
        assertContains(script, "curtainLayer=USE_MODAL_CURTAIN?nativeCreateElement.call(DOC,'dialog'):null")
        assertContains(script, "nativeStyleSet.call(layerStyle,rule[0],value,'important')")
        assertContains(script, "['z-index','2147483647']")
        assertContains(script, "['pointer-events','auto']")
        assertContains(script, "['max-width','none']")
        assertContains(script, "['transition','none']")
        assertContains(script, "['animation','none']")
        assertContains(script, "['zoom','1']")
        assertContains(script, "['scale','1']")
        assertContains(script, "['rotate','none']")
        assertContains(script, "['translate','none']")
        assertContains(script, "['transform-origin','50% 50%']")
        assertContains(script, "['offset-path','none']")
        assertContains(script, "['offset-distance','0']")
        assertContains(script, "['offset-position','normal']")
        assertContains(script, "['offset-anchor','auto']")
        assertContains(script, "['offset-rotate','auto']")
        assertContains(script, "['border-radius','0']")
        assertContains(script, "invoke(WeakSetAdd,protectedNodes,[curtainLayer])")
        assertContains(
            script,
            "if(curtainRequired&&!curtainOpen()){nodeAppend.call(documentElement(),curtainLayer)",
        )
        assertContains(
            script,
            "nodeAppend.call(documentElement(),curtainLayer);nativeDialogShowModal.call(curtainLayer)",
        )
        assertContains(script, "else if(!curtainRequired&&curtainOpen())nativeDialogClose.call(curtainLayer)")
        assertContains(script, "curtainOpen()===curtainRequired")
        assertContains(script, "const hideCurtain=()=>{curtainRequired=false;return ensureCurtain()}")
        assertFalse(script.contains("nodeRemove.call(parent,curtainStyle)"))
        assertContains(script, "CURTAIN_RELEASE_ATTRIBUTE='${ChromeMediaShieldBootstrap.CurtainReleaseAttribute}'")
        assertContains(script, "nativeSet.call(root,CURTAIN_RELEASE_ATTRIBUTE,'1')")
        assertContains(script, "nativeRemove.call(root,CURTAIN_RELEASE_ATTRIBUTE)")
        assertFalse(script.contains("nativeSet.call(curtainStyle,'media','not all')"))
    }

    @Test
    fun `H20 self shield installs before one shot ACK and releases only its own curtain`() {
        val identity =
            ChromeMediaShieldDocumentIdentity(
                protectionSessionId = "session-h20",
                policyEpoch = 20L,
                navigationSequence = 7L,
                documentSequence = 9L,
                tokenDigest = "a".repeat(64),
                topLevel = true,
            )
        val selfShield =
            ChromeMediaShieldBootstrap.script(
                readyToken = ReadyToken,
                styleNonce = StyleNonce,
                selfShieldIdentity = identity,
            )

        assertContains(selfShield, "SELF_SHIELD=true")
        assertContains(selfShield, "HAS_CURTAIN=TOP_LEVEL||SELF_SHIELD")
        assertContains(selfShield, "USE_MODAL_CURTAIN=HAS_CURTAIN&&!SELF_SHIELD")
        assertContains(selfShield, "curtainLayer=USE_MODAL_CURTAIN?nativeCreateElement.call(DOC,'dialog'):null")
        assertContains(selfShield, "v3|SELF_READY|")
        assertContains(selfShield, "v1|SELF_SHIELD_TRACE|")
        assertContains(selfShield, "v1|BOOTSTRAP_FAIL|")
        assertContains(selfShield, "SESSION='session-h20',POLICY_EPOCH=20,NAVIGATION_SEQUENCE=7,DOCUMENT_SEQUENCE=9")
        assertContains(selfShield, "xhrOpen.call(xhr,'POST',SELF_READY_URL,false)")
        assertContains(selfShield, "SELF_READY_RESPONSE_URL=resolvedEndpoint(SELF_READY_URL)")
        assertContains(selfShield, "read(xhrResponseUrlProperty,xhr)!==SELF_READY_RESPONSE_URL")
        assertContains(selfShield, "read(xhrStatusProperty,xhr)!==204")
        assertContains(selfShield, "curtainRequired=false;if(!ensureCurtain())")
        assertContains(selfShield, "selfShieldStage=1")
        assertContains(selfShield, "selfShieldStage=2")
        assertContains(selfShield, "selfShieldStage=3")
        assertContains(selfShield, "selfShieldStage=4")
        assertContains(selfShield, "selfShieldStage=5")
        assertContains(selfShield, "selfShieldStage=6")
        assertContains(selfShield, "selfShieldStage=7")
        assertContains(selfShield, "requestSelfShieldTrace('RELEASE_COMPLETED')")
        assertContains(selfShield, "requestSelfShieldTrace('PARSER_CONTINUED')")
        assertContains(selfShield, "requestSelfShieldTrace('ORIGINAL_SCRIPT_STARTED')")
        assertContains(selfShield, "__gloshH20ParserContinuation__")
        assertContains(selfShield, "__gloshH20OriginalScriptStarted__")
        assertTrue(selfShield.indexOf("selfShieldStage=1") < selfShield.indexOf("selfShieldStage=2"))
        assertTrue(selfShield.indexOf("selfShieldStage=2") < selfShield.indexOf("selfShieldStage=3"))
        assertTrue(selfShield.indexOf("selfShieldStage=3") < selfShield.indexOf("selfShieldStage=4"))
        assertContains(selfShield, "const expectedCurtainCss=CURTAIN_CSS,root=documentElement()")
        assertFalse(selfShield.contains("invoke(nodeText.set,curtainStyle,[expectedCurtainCss])"))
        assertContains(selfShield, "read(nodeText,curtainStyle)===expectedCurtainCss")
        assertContains(
            selfShield,
            "const currentCurtainSheet=read(styleSheetProperty,curtainStyle);" +
                "registerProtectedSheet(currentCurtainSheet)",
        )
        assertContains(
            selfShield,
            "ensureProtectedStyle(curtainStyle,currentCurtainSheet,'')",
        )
        assertContains(
            selfShield,
            "curtainRequired?!nativeHas.call(root,CURTAIN_RELEASE_ATTRIBUTE):" +
                "nativeGet.call(root,CURTAIN_RELEASE_ATTRIBUTE)==='1'",
        )
        assertTrue(
            selfShield.indexOf("if(!installed){failClosedDocument('INSTALL',installFailure);return}") <
                selfShield.indexOf("v3|SELF_READY|"),
        )
        assertTrue(selfShield.indexOf("v3|SELF_READY|") < selfShield.indexOf("curtainRequired=false"))
        assertTrue(selfShield.indexOf("if(SELF_SHIELD){") < selfShield.indexOf("if(!TOP_LEVEL){"))
        assertContains(selfShield, "selfShieldOperation='SELF_READY_SEND';xhrSend.call(xhr,selfReadyBody)")
        assertContains(
            selfShield,
            "catch(_){failClosedDocument('SELF_SHIELD_SEQUENCE',selfShieldOperation)}return}if(!TOP_LEVEL){",
        )
    }

    @Test
    fun `subdocuments install the shield without exposing a foreground authority marker`() {
        val subdocumentScript = ChromeMediaShieldBootstrap.script(ReadyToken, StyleNonce, topLevel = false)

        assertContains(subdocumentScript, "TOP_LEVEL=false")
        assertContains(subdocumentScript, "if(!TOP_LEVEL){")
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
    fun `internal curtain owns the top layer before a site can create competing presentation`() {
        assertContains(script, "nativeDialogShowModal=SELF.HTMLDialogElement")
        assertContains(script, "nativeDialogClose=SELF.HTMLDialogElement")
        assertContains(script, "nativeAddEvent.call(curtainLayer,'cancel'")
        assertContains(script, "seal(HTMLDialogElement.prototype,'showModal'")
        assertContains(script, "seal(HTMLDialogElement.prototype,'show'")
        assertContains(script, "seal(HTMLDialogElement.prototype,'close'")
        assertContains(script, "HTMLDialogElement.prototype.requestClose")
        assertContains(script, "seal(HTMLDialogElement.prototype,'requestClose'")
        assertContains(script, "seal(HTMLDialogElement.prototype,'showModal',deny)")
        assertContains(script, "ObjectDefine(HTMLDialogElement.prototype,'open'")
        assertContains(script, "propertyDescriptor(HTMLElement.prototype,'hidden')")
        assertContains(script, "curtainVisibleByAttribute()")
        assertContains(script, "invoke(htmlHiddenProperty.set,curtainLayer,[false])")
        assertContains(
            script,
            "guardAccessorProperty(HTMLElement.prototype,'hidden',htmlHiddenProperty,protectedNode,false)",
        )
        assertContains(script, "const guardedSet=function(value){if(blocked(this))deny()")
        assertContains(script, "propertyDescriptor(HTMLElement.prototype,'title')")
        assertContains(script, "ensureProtectedStyle(shieldStyle,shieldSheet,'')")
        assertContains(
            script,
            "ensureProtectedStyle(curtainStyle,currentCurtainSheet,'')",
        )
        assertContains(script, "const protectedCurtainAttribute=(element,key)=>")
        assertContains(script, "protectedCurtainAttribute(this,key))deny()")
        assertContains(script, "'ry',CURTAIN_RELEASE_ATTRIBUTE]")
        assertEquals(5, Regex("protectedCurtainAttribute\\(this,key\\)\\)deny\\(\\)").findAll(script).count())
        assertContains(script, "invoke(htmlTitleProperty.set,style,[''])")
        assertContains(script, "nativeRemove.call(style,'title')")
        assertContains(
            script,
            "guardAccessorProperty(HTMLElement.prototype,'title',htmlTitleProperty,protectedNode,false)",
        )
        assertContains(script, "propertyDescriptor(HTMLDialogElement.prototype,'closedBy')")
        assertContains(script, "invoke(dialogClosedByProperty.set,curtainLayer,['none'])")
        assertContains(
            script,
            "guardAccessorProperty(HTMLDialogElement.prototype,'closedBy',dialogClosedByProperty,protectedNode,false)",
        )
        assertContains(script, "seal(HTMLElement.prototype,'showPopover',deny)")
        assertContains(script, "seal(HTMLElement.prototype,'togglePopover',deny)")
        assertContains(script, "denyPropertySetter(HTMLElement.prototype,'popover')")
        assertContains(script, "denyPropertySetter(owner,'popoverTargetElement')")
        assertContains(script, "denyPropertySetter(owner,'popoverTargetAction')")
        assertContains(script, "denyPropertySetter(HTMLButtonElement.prototype,'commandForElement')")
        assertContains(script, "denyPropertySetter(HTMLButtonElement.prototype,'command')")
        assertContains(script, "TOP_LAYER_ATTRIBUTES=new Set(TOP_LAYER_ATTRIBUTE_NAMES)")
        assertContains(script, "removeDeclarativeTopLayer(element)")
        assertContains(script, "[popover],[popovertarget],[popovertargetaction],[commandfor],[command]")
        assertContains(script, "const protectedInlineStyle=(element)=>protectedNode(element)")
        assertContains(script, "WeakSetDelete=WeakSet.prototype.delete")
        assertContains(script, "invoke(WeakSetAdd,protectedMediaNodes,[element])")
        assertContains(script, "invoke(WeakSetDelete,protectedMediaNodes,[element])")
        assertContains(script, "invoke(WeakSetHas,protectedMediaNodes,[element])")
        assertContains(script, "guardInlineStyleOwner(prototype)")
        assertContains(script, "SELF.HTMLElement&&HTMLElement.prototype")
        assertContains(script, "SELF.SVGElement&&SVGElement.prototype")
        assertContains(script, "SELF.MathMLElement&&MathMLElement.prototype")
        assertContains(script, "if(protectedInlineStyle(this))deny()")
        assertContains(script, "const guardedSet=function(value){if(protectedInlineStyle(this))deny()")
        assertContains(script, "ObjectDefine(owner,'style',{get:guardedGet")
        assertContains(script, "seal(EventTarget.prototype,'dispatchEvent'")
        assertContains(script, "if(protectedNode(this))deny();return nativeDispatchEvent.call(this,event)")
        assertContains(script, "seal(Element.prototype,'requestFullscreen',deny)")
        assertContains(script, "seal(Document.prototype,'startViewTransition',deny)")
        assertContains(script, "NativePageSwapEvent=SELF.PageSwapEvent")
        assertContains(script, "NativePageRevealEvent=SELF.PageRevealEvent")
        assertContains(script, "nativeSkipViewTransition=NativeViewTransition")
        assertContains(script, "nativeAddEvent.call(SELF,'pageswap'")
        assertContains(script, "stopCrossDocumentTransition(event,pageSwapTransitionProperty)")
        assertContains(script, "nativeAddEvent.call(SELF,'pagereveal'")
        assertContains(script, "stopCrossDocumentTransition(event,pageRevealTransitionProperty)")
        assertContains(script, "if(protectedNode(this))deny();const root=invoke(originalAttach,this,[init])")
    }

    @Test
    fun `every ordinary insertion seam sanitizes before a fresh iframe realm becomes usable`() {
        assertContains(script, "for(const owner of [Element.prototype,Document.prototype,DocumentFragment.prototype])")
        assertContains(script, "for(const name of ['append','prepend'])")
        assertContains(script, "sealInsertion(owner,'replaceChildren',true)")
        assertFalse(script.contains("scan(this,RM_GUARDED,api,RMF_DOM_GUARD)"))
        assertContains(
            script,
            "function(node){rendererApiCall(RMA_APPEND_CHILD,RMF_DOM_GUARD);if(insideProtected(this))deny();" +
                "rejectProtectedMove(node,this)",
        )
        assertContains(script, "for(const name of ['before','after'])")
        assertContains(script, "sealInsertion(owner,'replaceWith',false,true)")
        assertContains(script, "removesTarget?containsProtected(this)")
        assertContains(script, "seal(Element.prototype,'insertAdjacentElement'")
        assertContains(script, "seal(Element.prototype,'insertAdjacentText'")
        assertContains(script, "seal(Range.prototype,'insertNode'")
        assertContains(
            script,
            "const container=rangeElement(ancestor);if(container)sanitizeContainer(container,RMF_DOM_GUARD)",
        )
        assertTrue(
            script.indexOf("const result=invoke(rangeInsert,this,[node])") <
                script.indexOf(
                    "const container=rangeElement(ancestor);if(container)sanitizeContainer(container,RMF_DOM_GUARD)",
                ),
        )
        val surroundStart = script.indexOf("seal(Range.prototype,'surroundContents'")
        val surroundMutation = script.indexOf("const result=invoke(surround,this,[node])", surroundStart)
        val surroundPostScan =
            script.indexOf("scan(node,RM_GUARDED,RMA_RANGE_SURROUND,RMF_DOM_GUARD);", surroundMutation)
        assertTrue(surroundStart >= 0)
        assertTrue(surroundMutation > surroundStart)
        assertTrue(surroundPostScan > surroundMutation)
        assertTrue(
            surroundPostScan <
                script.indexOf(
                    "const container=rangeElement(ancestor);if(container)sanitizeContainer(container,RMF_DOM_GUARD)",
                    surroundStart,
                ),
        )
        assertContains(script, "owner.moveBefore")
        assertContains(script, "ObjectDefine(ShadowRoot.prototype,'innerHTML'")
        assertContains(script, "if(!mappedProtected(this))deny()")
        assertContains(
            script,
            "restoreMappedProtected(this);scan(this,RM_MARKUP,RMA_SHADOW_INNER,RMF_MARKUP)",
        )
        assertContains(script, "invoke(outer.set,this,[safeMarkup(value)])")
        assertContains(script, "put(args,1,safeMarkup")
        assertContains(
            script,
            "if(nativeGet.call(element,'sandbox')!==FRAME_SANDBOX)" +
                "nativeSet.call(element,'sandbox',FRAME_SANDBOX)",
        )
        assertContains(
            script,
            "if(nativeGet.call(element,'src')!=='about:blank')nativeSet.call(element,'src','about:blank')",
        )
        assertContains(script, "const guardFrameSandbox=()=>")
        assertContains(script, "const guardedSet=function(){invoke(entry.set,this,[FRAME_SANDBOX])")
        assertContains(script, "ObjectDefine(owner,'sandbox',{get:guardedGet,set:guardedSet")
        assertContains(script, "installed=guardFrameSandbox()&&installed")
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
        assertContains(script, "NativeObject=Object,NativeReflect=Reflect")
        assertContains(script, "ReflectApply=NativeReflect.apply")
        assertContains(script, "const invoke=(fn,owner,args)=>ReflectApply(fn,owner,args)")
        assertContains(script, "method=(fn)=>({call:(owner,...args)=>invoke(fn,owner,args)")
        assertContains(script, "NativeString=String")
        assertContains(script, "NativeURL=URL")
        assertContains(script, "StringLower=String.prototype.toLowerCase")
        assertContains(script, "StringSlice=String.prototype.slice")
        assertContains(script, "nativeSet=method(Element.prototype.setAttribute)")
        assertContains(script, "nativeCreateElement=method(Document.prototype.createElement)")
        assertContains(script, "const create=nativeCreateElement")
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
    fun `tree scans dispatch query selector by exact node receiver type`() {
        assertContains(script, "documentQueryAll=method(Document.prototype.querySelectorAll)")
        assertContains(
            script,
            "const descendantQuery=(type)=>type===1?elementQueryAll:type===9?documentQueryAll:type===11?fragmentQueryAll:null",
        )
        assertContains(script, "const query=descendantQuery(type)")
        assertFalse(script.contains("const query=type===1?elementQueryAll:fragmentQueryAll"))
    }

    @Test
    fun `node text guard reads element identity only for element receivers`() {
        assertContains(
            script,
            "const type=nodeTypeOf(this);if(type===2&&routeAttributeValue(this,value))return;" +
                "const admitted=type===1&&localNameOf(this)==='style'?rewriteCssCandidate(value):value",
        )
        assertFalse(script.contains("const type=nodeTypeOf(this);const admitted=localNameOf(this)==='style'"))
    }

    @Test
    fun `common stale DOM patches are absorbed without releasing mutation guards`() {
        assertContains(script, "const sealCompatibleMethod=(owner,name,value)=>")
        assertContains(
            script,
            "sealCompatibleMethod(Node.prototype,'insertBefore',function(node,ref)",
        )
        assertContains(script, "if(ref!==null&&parentOf(ref)!==this)return node")
        assertContains(script, "sealCompatibleMethod(Node.prototype,'removeChild',function(node)")
        assertContains(script, "if(parentOf(node)!==this)return node")
    }

    @Test
    fun `bounded framework metadata does not hide original inline SVG icons`() {
        assertContains(script, "'data-component','data-view-component','data-skapa'")
        assertContains(script, "'transform','display','overflow','fill'")
        assertContains(script, "'clip-rule'")
        assertContains(script, "'aria-labelledby'")
        assertContains(script, "'xml:space'")
        assertContains(script, "'data-skapa'")
        assertContains(script, "unhide(svg);if(nativeGet.call(svg,'data-glosh-icon-safe')!=='1'")
        assertContains(script, "documentGetById=method(Document.prototype.getElementById)")
        assertContains(script, "!iconDimensions(svg)&&!elementQuery.call(svg,'symbol')")
        assertContains(script, "localNameOf(target)!=='symbol'")
        assertContains(script, "nativeGet.call(owner,'data-glosh-icon-safe')!=='1'")
        assertContains(script, "name==='xmlns:xlink'&&value!=='http://www.w3.org/1999/xlink'")
        assertContains(script, "iconDependentsByOwner=new WeakMap()")
        assertContains(script, "const failIcon=(svg)=>")
        assertContains(script, "invoke(SetForEach,dependents")
    }

    @Test
    fun `admitted inline SVG remains style compatible while every mutation is revalidated`() {
        assertContains(
            script,
            "const protectedInlineStyle=(element)=>protectedNode(element)||" +
                "invoke(WeakSetHas,protectedMediaNodes,[element])||" +
                "nativeGet.call(element,'data-glosh-media-blocked')==='1'",
        )
        assertContains(
            script,
            "const protectedDeclaration=(value)=>{const owner=invoke(WeakMapGet,styleOwners,[value]);" +
                "if(owner&&(protectedNode(owner)||invoke(WeakSetHas,protectedMediaNodes,[owner])||" +
                "nativeGet.call(owner,'data-glosh-media-blocked')==='1'))return true",
        )
        assertFalse(script.contains("nativeGet.call(owner,'data-glosh-icon-safe')==='1'"))
        assertContains(
            script,
            "invoke(entry.set,this,[rewriteCssCandidate(value)]);" +
                "const owner=invoke(WeakMapGet,styleOwners,[this]);if(owner)sanitizeContainer(owner)",
        )
    }

    @Test
    fun `reading protected inline style remains compatible while mutation stays denied`() {
        assertContains(
            script,
            "const guardedGet=function(){const value=invoke(entry.get,this,[]);" +
                "invoke(WeakMapSet,styleOwners,[value,this]);return value}",
        )
        assertFalse(script.contains("guardedGet=function(){if(protectedInlineStyle(this))deny()"))
        assertContains(script, "if(protectedDeclaration(this))deny()")
        assertContains(script, "const guardedSet=function(value){if(protectedInlineStyle(this))deny()")
    }

    @Test
    fun `dynamic style text insertion paths route SVG data through native rewriter`() {
        assertContains(script, "const styleTextOwner=(value)=>")
        assertContains(script, "const rewriteStyleTextNode=(owner,node)=>")
        assertContains(script, "rewriteStyleTextNode(this,node);scan(node,RM_GUARDED,RMA_APPEND_CHILD")
        assertContains(script, "else if(styleTextOwner(textOwner))put(args,index,rewriteCssCandidate(value))")
        assertContains(script, "owner=place==='beforebegin'||place==='afterend'?parentOf(this):this")
        assertContains(script, "const admitted=styleTextOwner(parentOf(this))?rewriteCssCandidate(value):value")
        assertContains(script, "if(styleTextOwner(parentOf(this))){const source=stringOf(read(nodeValue,this)||'')")
        assertContains(script, "const admitted=localNameOf(this)==='style'?rewriteCssCandidate(value):value")
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
    fun `bootstrap source and secrets stay in closure and leave no DOM beacon`() {
        assertContains(script, "BOOTSTRAP_SCRIPT=DOC.currentScript")
        assertContains(script, "if(!BOOTSTRAP_SCRIPT)failInstall('CURRENT_SCRIPT_MISSING')")
        assertContains(script, "const retireScript=(script)=>")
        assertContains(script, "nodeRemove.call(parent,script);return !connected(script)")
        assertContains(
            script,
            "const clearStyleNonce=(style)=>{try{invoke(styleNonceProperty.set,style,['']);nativeRemove.call(style,'nonce')",
        )
        assertContains(script, "return read(styleNonceProperty,style)===''&&!nativeHas.call(style,'nonce')")
        assertContains(
            script,
            "const retireBootstrapSecrets=()=>clearStyleNonce(shieldStyle)&&(!curtainStyle||clearStyleNonce(curtainStyle))&&retireScript(BOOTSTRAP_SCRIPT)",
        )
        assertContains(
            script,
            "if(!installed){failClosedDocument('INSTALL',installFailure);return}" +
                "if(!retireBootstrapSecrets()){failClosedDocument('BOOTSTRAP_SECRET_RETIREMENT','RETIRE_FAILED');return}",
        )
        assertContains(script, "if(!clearStyleNonce(style)){nodeRemove.call(root,style);failClosedDocument();deny()}")
        assertEquals(1, Regex(Regex.escape(ReadyToken)).findAll(script).count())
        assertEquals(1, Regex(Regex.escape(StyleNonce)).findAll(script).count())
        assertFalse(script.contains("data-glosh-ready-token"))
        assertFalse(script.contains("internalsAriaLabelProperty"))
        assertFalse(script.contains("glosh-shield-ready:"))
        assertFalse(script.contains("READY_ID") || script.contains("READY_HOST_ID"))
        assertContains(script, "ReflectDelete(SELF,'${ChromeMediaShieldBootstrap.ParserBarrierCallbackName}')")
        assertContains(script, "ReflectDelete(SELF,'${ChromeMediaShieldBootstrap.ParserBarrierGuardName}')")
        assertContains(script, "authorityArmed=false")
        assertContains(script, "if(!authorityArmed||activePhase!=='idle'||!activeDocument())return")
        val callbackFocusGate =
            "if(!acquireActiveDocument()){authorityArmed=true;parkDocument();return}authorityArmed=true;beginReadyLifecycle()"
        assertContains(script, callbackFocusGate)
        assertEquals(
            1,
            Regex(Regex.escape(callbackFocusGate)).findAll(script).count(),
        )
        val guard = ChromeMediaShieldBootstrap.parserBarrierGuardScript()
        val fallback = ChromeMediaShieldBootstrap.parserBarrierFailClosedInstallerScript()
        assertContains(guard, "f()===true")
        assertContains(guard, ChromeMediaShieldBootstrap.ParserBarrierFailClosedName)
        assertContains(guard, "fail.retire()!==true")
        assertContains(guard, "!delete S.${ChromeMediaShieldBootstrap.ParserBarrierFailClosedName}")
        assertContains(guard, "Object.getOwnPropertyDescriptor")
        assertContains(fallback, "SCRIPT=D.currentScript")
        assertContains(fallback, "Element.prototype.removeAttribute")
        assertContains(fallback, "Node.prototype.removeChild")
        assertContains(fallback, "Object.defineProperty(fail,'retire'")
        assertContains(fallback, "SCRIPT.isConnected===false")
        assertContains(fallback, "Document.prototype")
        assertContains(fallback, "Reflect.apply")
        assertContains(fallback, "documentElement")
        assertContains(script, "const parserFailClosed=SELF.__gloshH19ParserBarrierFailClosed__")
        assertContains(script, "typeof parserFailClosedRetire.value!=='function'")
        assertFalse(guard.contains(ReadyToken))
        assertFalse(fallback.contains(ReadyToken))
    }

    @Test
    fun `subdocument completes bootstrap through one-shot inline guard and otherwise fails closed`() {
        val subdocument = ChromeMediaShieldBootstrap.script(ReadyToken, StyleNonce, topLevel = false)
        val guard = ChromeMediaShieldBootstrap.subdocumentGuardScript()

        assertContains(subdocument, "let subdocumentGuardConsumed=false")
        assertContains(subdocument, "if(subdocumentGuardConsumed)return false")
        assertContains(subdocument, "ReflectDelete(SELF,'${ChromeMediaShieldBootstrap.SubdocumentGuardName}')")
        assertContains(subdocument, "read(scriptSrcProperty,guardScript)!==''")
        assertContains(subdocument, "!retireScript(guardScript)")
        assertTrue(
            subdocument.indexOf("if(!retireBootstrapSecrets()){failClosedDocument();return}") <
                subdocument.indexOf("ObjectDefine(SELF,'${ChromeMediaShieldBootstrap.SubdocumentGuardName}'"),
        )
        assertContains(guard, "const f=S.${ChromeMediaShieldBootstrap.SubdocumentGuardName}")
        assertContains(guard, "f()===true")
        assertContains(guard, "fail.retire()!==true")
        assertContains(guard, "try{if(typeof fail==='function')fail()}")
        assertFalse(guard.contains(ReadyToken))
        assertFalse(guard.contains(ChromePhotosDataPlaneLabContract.MediaShieldParserBarrierUrl))
    }

    @Test
    fun `shield and curtain cannot be moved or rewritten through ordinary APIs`() {
        assertContains(script, "rejectProtectedMove")
        assertContains(script, "if(containsProtected(node))deny()")
        assertContains(script, "if(protectedSheet(this))deny()")
        assertContains(script, "if(containsProtected(this))deny()")
        assertContains(script, "ObjectDefine(HTMLStyleElement.prototype,name")
        assertContains(script, "ObjectDefine(CharacterData.prototype,'data'")
        assertContains(script, "for(const name of ['deleteContents','extractContents'])")
        assertContains(script, "seal(Selection.prototype,'deleteFromDocument'")
        assertContains(script, "ObjectDefine(HTMLElement.prototype,'innerText'")
        assertContains(script, "ObjectDefine(HTMLElement.prototype,'outerText'")
        assertContains(script, "ObjectDefine(CSSStyleRule.prototype,'selectorText'")
        assertContains(
            script,
            "guardAccessorProperty(StyleSheet.prototype,'disabled',styleSheetDisabledProperty,protectedSheet,false)",
        )
        assertContains(
            script,
            "guardAccessorProperty(StyleSheet.prototype,'media',styleSheetMediaProperty,protectedSheet,true)",
        )
        assertContains(
            script,
            "guardAccessorProperty(CSSStyleRule.prototype,'style',ruleStyleProperty,protectedRule,true)",
        )
        assertContains(script, "sealProtectedRuleMethod(CSSStyleRule.prototype,name)")
        assertContains(script, "sealProtectedRuleMethod(CSSGroupingRule.prototype,name)")
        assertContains(script, "propertyOwner(prototype,name)")
        assertContains(script, "seal(MediaList.prototype,name")
        assertContains(script, "ObjectDefine(MediaList.prototype,'mediaText'")
        assertContains(script, "seal(StylePropertyMap.prototype,name")
        assertContains(script, "attributeStyleMapProperty")
        assertContains(script, "styleMapOwners")
        assertContains(script, "protectedStyleMap(this)")
        assertContains(script, "const protectedReflectiveTarget=(value)=>!!value&&(")
        assertContains(script, "installed=seal(NativeObject,'defineProperty'")
        assertContains(script, "installed=seal(NativeObject,'defineProperties'")
        assertContains(script, "installed=seal(NativeObject,'assign'")
        assertContains(script, "installed=seal(NativeObject,'setPrototypeOf'")
        assertContains(script, "installed=seal(NativeReflect,'defineProperty'")
        assertContains(script, "installed=seal(NativeReflect,'deleteProperty'")
        assertContains(script, "installed=seal(NativeReflect,'set'")
        assertContains(script, "installed=seal(NativeReflect,'setPrototypeOf'")
        assertContains(script, "denyReflectiveTarget(this);return invoke(ObjectDefineSetter")
        assertContains(script, "ObjectDefine(ObjectPrototype,'__proto__'")
        assertContains(
            script,
            "nativeRemove.call(element,'src');nativeRemove.call(element,'srcset');hide(element)",
        )
        assertContains(script, "registerProtectedSheet(shadowSheet)")
        assertContains(script, "restoreMappedProtected(root)")
        assertContains(script, "protectedDescendants")
        assertContains(script, "oneOf(lower(canonical),['object','embed','frame','fencedframe'])")
        assertContains(
            script,
            "const showCurtain=()=>{curtainRequired=true;if(ensureCurtain())return true;failClosedDocument();return false}",
        )
        assertFalse(script.contains("data-glosh-curtain-released"))
    }

    @Test
    fun `media SVG is hidden unless it satisfies a bounded inert icon grammar`() {
        assertContains(script, "const ICON_TAGS=new Set")
        assertContains(script, "!invoke(SetHas,ICON_TAGS")
        assertContains(script, "'lineargradient','radialgradient'")
        assertContains(script, "const internalReference=")
        assertContains(script, "if(name==='href'||name==='xlink:href')")
        assertContains(script, "ICON_PATH_CHARS")
        assertContains(script, "const safeIconStyle=")
        assertContains(script, "nativeSet.call(svg,'data-glosh-icon-safe','1')")
        assertContains(script, "unhide(svg);if(nativeGet.call(svg,'data-glosh-icon-safe')!=='1'")
        assertFalse(script.contains("rootRules=[['all','initial']"))
        assertFalse(script.contains("['d','path(\"'+d+'\")']"))
        assertFalse(script.contains("nativeStylePriority.call(rootStyle,'all')==='important'"))
        assertFalse(script.contains("let child=firstChildOf(element);while(child){nodeRemove.call(element,child)"))
        assertContains(script, "const WATCHED_ATTRIBUTES=")
        assertContains(script, "'d','points','viewBox','width','height','fill','stroke','transform'")
        assertContains(script, "elementClosest.call(target,'svg'))rendererMetric(29);sanitizeContainer(target)")
        assertFalse(script.contains("scan(target,RM_OBSERVER_ATTRIBUTE)"))
        assertContains(ChromeMediaShieldBootstrap.css, "svg:not([data-glosh-icon-safe='1'])")
    }

    @Test
    fun `normal CSS and generic workers remain available within the documented product boundary`() {
        assertFalse(ChromeMediaShieldBootstrap.css.contains("div"))
        assertFalse(ChromeMediaShieldBootstrap.css.contains("span"))
        assertFalse(ChromeMediaShieldBootstrap.css.contains("background-color"))
        assertFalse(script.contains("self.Worker"))
        assertFalse(script.contains("SharedWorker.prototype"))
        assertContains(
            script,
            "removesChildren?(!mapped&&containsProtected(this)):insideProtected(this)",
        )
    }

    private fun identity() =
        ChromeMediaShieldDocumentIdentity(
            protectionSessionId = "session",
            policyEpoch = 20,
            navigationSequence = 1,
            documentSequence = 1,
            tokenDigest = "a".repeat(64),
            topLevel = true,
        )

    private companion object {
        const val ReadyToken = "AAAAAAAAAAAAAAAAAAAAAA"
        const val StyleNonce = "BBBBBBBBBBBBBBBBBBBBBB"
    }
}
