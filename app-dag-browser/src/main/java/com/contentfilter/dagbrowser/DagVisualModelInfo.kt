package com.contentfilter.dagbrowser

import com.glosh.visual.GloshiaVisualModelInfo

/** Compatibility facade; the shared engine is the single owner of model metadata. */
internal object DagVisualModelInfo {
    const val PublicName = GloshiaVisualModelInfo.PublicName
    const val FunctionalVersion = GloshiaVisualModelInfo.FunctionalVersion
    const val ModelAssetPath = GloshiaVisualModelInfo.ModelAssetPath
    const val ModelSha256 = GloshiaVisualModelInfo.ModelSha256
    const val Runtime = GloshiaVisualModelInfo.Runtime
    const val PolicyVersion = GloshiaVisualModelInfo.PolicyVersion
    const val ShortSha256 = GloshiaVisualModelInfo.ShortSha256
}
