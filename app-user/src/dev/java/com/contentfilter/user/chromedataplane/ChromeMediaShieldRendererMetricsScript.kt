package com.contentfilter.user.chromedataplane

/** Compact DEV-only renderer counters injected into the protected bootstrap closure. */
internal object ChromeMediaShieldRendererMetricsScript {
    const val SnapshotEvent = "glosh-h20-renderer-metrics-snapshot"

    val declarations: String =
        "const RENDERER_METRICS=new NativeArray(38),NativeArrayJoin=NativeArray.prototype.join,NativePerformance=SELF.performance," +
            "NativePerformanceNow=NativePerformance&&NativePerformance.now,NativeMath=Math,NativeMathFloor=NativeMath.floor,rendererNow=NativePerformanceNow?" +
            "method(NativePerformanceNow):null;for(let rmIndex=0;rmIndex<38;rmIndex+=1)RENDERER_METRICS[rmIndex]=0;" +
            "const RM_OBSERVER_CHILD=11,RM_OBSERVER_ATTRIBUTE=12," +
            "RM_GUARDED=13,RM_MARKUP=14,RM_SHADOW=15,RM_INITIAL=16,RM_OTHER=17;" +
            "const rendererMetric=(index,value=1)=>{const current=RENDERER_METRICS[index];" +
            "RENDERER_METRICS[index]=current>=9007199254740991-value?9007199254740991:current+value};" +
            "const rendererMax=(index,value)=>{if(value>RENDERER_METRICS[index])RENDERER_METRICS[index]=value};" +
            "const rendererClock=()=>rendererNow?rendererNow.call(NativePerformance):0;" +
            "const rendererScanStarted=(origin,root)=>{rendererMetric(5);rendererMetric(origin);" +
            "if(root===documentElement())rendererMetric(6)};const rendererScanDone=(count,started)=>{" +
            "rendererMetric(7,count);rendererMax(8,count);" +
            "const micros=rendererNow?invoke(NativeMathFloor,NativeMath,[(rendererClock()-started)*1000]):0;" +
            "rendererMetric(9,micros>0?micros:0);rendererMax(10,micros>0?micros:0)};" +
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
