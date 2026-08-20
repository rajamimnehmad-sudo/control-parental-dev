package com.contentfilter.feature.accessibility

import android.content.Context
import android.os.Process
import com.glosh.visual.GloshiaVisualAnalyzer
import com.glosh.visual.OnDeviceGloshiaVisualAnalyzer

/** ARM32 remains supported by App Usuario, but the ONNX R3.1 feature is explicitly ARM64-only. */
internal object ChromeVisualGloshiaEngineProvider {
    fun isAvailableInCurrentProcess(): Boolean = Process.is64Bit()

    fun create(context: Context): GloshiaVisualAnalyzer? =
        if (isAvailableInCurrentProcess()) {
            OnDeviceGloshiaVisualAnalyzer.create(context.applicationContext)
        } else {
            null
        }
}
