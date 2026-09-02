package com.contentfilter.user.chromedataplane

/** Compact DEV-only renderer counters injected into the protected bootstrap closure. */
internal object ChromeMediaShieldRendererMetricsScript {
    const val SnapshotEvent = "glosh-h20-renderer-metrics-snapshot"
    const val FieldCount = 120
    const val ApiCallStart = 38
    const val ApiScanStart = 64
    const val FamilyCallStart = 90
    const val FamilyScanStart = 93
    const val FamilyDirectSanitizeStart = 96
    const val FamilyRootStart = 99
    const val FamilyScanNodesStart = 111
    const val FamilyMicrosStart = 114
    const val FamilyMaxMicrosStart = 117

    val apiNames =
        listOf(
            "createElement",
            "createElementNS",
            "appendChild",
            "insertBefore",
            "replaceChild",
            "append",
            "prepend",
            "replaceChildren",
            "before",
            "after",
            "replaceWith",
            "adjacentElement",
            "moveBefore",
            "rangeInsert",
            "rangeSurround",
            "adoptNode",
            "cloneNode",
            "importNode",
            "safeMarkup",
            "innerHTML",
            "outerHTML",
            "insertAdjacentHTML",
            "setHTML",
            "shadowInnerHTML",
            "execCommandInsertHTML",
            "mappedTextContent",
        )

    val familyNames = listOf("factory", "domGuard", "markup")

    val declarations: String =
        "const RENDERER_METRICS=new NativeArray($FieldCount),NativeArrayJoin=NativeArray.prototype.join,NativePerformance=SELF.performance," +
            "NativePerformanceNow=NativePerformance&&NativePerformance.now,NativeMath=Math,NativeMathFloor=NativeMath.floor,rendererNow=NativePerformanceNow?" +
            "method(NativePerformanceNow):null;for(let rmIndex=0;rmIndex<$FieldCount;rmIndex+=1)RENDERER_METRICS[rmIndex]=0;" +
            "const RM_OBSERVER_CHILD=11,RM_OBSERVER_ATTRIBUTE=12," +
            "RM_GUARDED=13,RM_MARKUP=14,RM_SHADOW=15,RM_INITIAL=16,RM_OTHER=17;" +
            "const RMF_FACTORY=0,RMF_DOM_GUARD=1,RMF_MARKUP=2," +
            "RM_API_CALLS=$ApiCallStart,RM_API_SCANS=$ApiScanStart,RM_FAMILY_CALLS=$FamilyCallStart," +
            "RM_FAMILY_SCANS=$FamilyScanStart,RM_FAMILY_DIRECT=$FamilyDirectSanitizeStart," +
            "RM_FAMILY_ROOTS=$FamilyRootStart,RM_FAMILY_NODES=$FamilyScanNodesStart," +
            "RM_FAMILY_MICROS=$FamilyMicrosStart,RM_FAMILY_MAX_MICROS=$FamilyMaxMicrosStart;" +
            "const RMA_CREATE=0,RMA_CREATE_NS=1,RMA_APPEND_CHILD=2,RMA_INSERT_BEFORE=3,RMA_REPLACE_CHILD=4," +
            "RMA_APPEND=5,RMA_PREPEND=6,RMA_REPLACE_CHILDREN=7,RMA_BEFORE=8,RMA_AFTER=9,RMA_REPLACE_WITH=10," +
            "RMA_ADJACENT_ELEMENT=11,RMA_MOVE_BEFORE=12,RMA_RANGE_INSERT=13,RMA_RANGE_SURROUND=14,RMA_ADOPT=15," +
            "RMA_CLONE=16,RMA_IMPORT=17,RMA_SAFE_MARKUP=18,RMA_INNER_HTML=19,RMA_OUTER_HTML=20," +
            "RMA_INSERT_HTML=21,RMA_SET_HTML=22,RMA_SHADOW_INNER=23,RMA_EXEC_INSERT_HTML=24,RMA_MAPPED_TEXT=25;" +
            "const rendererMetric=(index,value=1)=>{const current=RENDERER_METRICS[index];" +
            "RENDERER_METRICS[index]=current>=9007199254740991-value?9007199254740991:current+value};" +
            "const rendererMax=(index,value)=>{if(value>RENDERER_METRICS[index])RENDERER_METRICS[index]=value};" +
            "const rendererClock=()=>rendererNow?rendererNow.call(NativePerformance):0;" +
            "const rendererApiCall=(api,family)=>{rendererMetric(RM_API_CALLS+api);rendererMetric(RM_FAMILY_CALLS+family)};" +
            "const rendererFamilyRoot=(family,root)=>{if(family<0)return;const type=nodeTypeOf(root),offset=type===1?0:(type===11?1:(type===3?2:3));" +
            "rendererMetric(RM_FAMILY_ROOTS+family*4+offset)};" +
            "const rendererFamilyStarted=(family,root)=>{if(family<0)return 0;rendererFamilyRoot(family,root);return rendererClock()};" +
            "const rendererFamilyDone=(family,count,started)=>{if(family<0)return;rendererMetric(RM_FAMILY_NODES+family,count);" +
            "const micros=rendererNow?invoke(NativeMathFloor,NativeMath,[(rendererClock()-started)*1000]):0;" +
            "rendererMetric(RM_FAMILY_MICROS+family,micros>0?micros:0);rendererMax(RM_FAMILY_MAX_MICROS+family,micros>0?micros:0)};" +
            "const rendererDirectSanitized=(family)=>{if(family>=0)rendererMetric(RM_FAMILY_DIRECT+family)};" +
            "const rendererScanStarted=(origin,root,api=-1,family=-1)=>{rendererMetric(5);rendererMetric(origin);" +
            "if(api>=0)rendererMetric(RM_API_SCANS+api);if(family>=0){rendererMetric(RM_FAMILY_SCANS+family);rendererFamilyRoot(family,root)};" +
            "if(root===documentElement())rendererMetric(6)};const rendererScanDone=(count,started,family=-1)=>{" +
            "rendererMetric(7,count);rendererMax(8,count);" +
            "const micros=rendererNow?invoke(NativeMathFloor,NativeMath,[(rendererClock()-started)*1000]):0;" +
            "rendererMetric(9,micros>0?micros:0);rendererMax(10,micros>0?micros:0);" +
            "if(family>=0){rendererMetric(RM_FAMILY_NODES+family,count);rendererMetric(RM_FAMILY_MICROS+family,micros>0?micros:0);" +
            "rendererMax(RM_FAMILY_MAX_MICROS+family,micros>0?micros:0)}};" +
            "const rendererSanitized=(element)=>{rendererMetric(18);const tag=localNameOf(element);" +
            "if(tag==='img')rendererMetric(20);else if(tag==='source')rendererMetric(21);" +
            "else if(tag==='svg')rendererMetric(22);else if(tag==='iframe')rendererMetric(23);" +
            "else if(tag==='canvas')rendererMetric(24);else if(tag==='video')rendererMetric(25)};"

    val reporting: String =
        "let rendererMetricsSent=false;const reportRendererMetrics=()=>{if(rendererMetricsSent||!SELF_SHIELD||" +
            "!selfReadyAccepted||!NativeXMLHttpRequest)return false;try{" +
            "const xhr=new NativeXMLHttpRequest();xhrOpen.call(xhr,'POST',RENDERER_METRICS_URL,false);" +
            "xhrSetHeader.call(xhr,'Content-Type','text/plain;charset=UTF-8');" +
            "xhrSend.call(xhr,'v1|RENDERER_METRICS|'+READY+'|'+selfShieldIdentity+'|'+invoke(NativeArrayJoin,RENDERER_METRICS,[',']));" +
            "const accepted=read(xhrResponseUrlProperty,xhr)===RENDERER_METRICS_RESPONSE_URL&&read(xhrStatusProperty,xhr)===204;" +
            "if(accepted)rendererMetricsSent=true;return accepted" +
            "}catch(_){return false}};nativeAddEvent.call(DOC,'$SnapshotEvent',reportRendererMetrics,true);" +
            "const reportRendererMetricsWhenHidden=()=>{if(visibilityState()==='hidden')reportRendererMetrics()};" +
            "nativeAddEvent.call(DOC,'visibilitychange',reportRendererMetricsWhenHidden,true);" +
            "nativeAddEvent.call(SELF,'pagehide',reportRendererMetrics,true);"
}
