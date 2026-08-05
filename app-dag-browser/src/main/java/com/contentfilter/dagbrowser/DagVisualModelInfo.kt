package com.contentfilter.dagbrowser

/** The single public contract for the model bundled by DAG. */
internal object DagVisualModelInfo {
    const val PublicName = "GloshIA Visual"
    const val FunctionalVersion = "R3.1"
    const val ModelAssetPath = "dag-model/tinyclip-r3-head-hybrid-int8.onnx"
    const val ModelSha256 =
        "c8b64af8092d3718c58736a511c996d0d443dacf3eaa74620b1e5af439a3cd48"
    const val FallbackModelAssetPath = "dag-model/tinyclip-bounded-finetune-r1-int8.onnx"
    const val FallbackModelSha256 =
        "2d52bd9e5eb4cd448cb0d64a784b2ee6f761ad20e890c57b898fd7991d29a9ee"
    const val Runtime = "ONNX Runtime Android 1.27.0"
    const val PolicyVersion = "dag-36"
    const val ShortSha256 = "c8b64af8…a3cd48"
}
