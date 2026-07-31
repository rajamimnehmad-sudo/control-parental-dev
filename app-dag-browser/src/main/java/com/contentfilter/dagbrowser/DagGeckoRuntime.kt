package com.contentfilter.dagbrowser

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime
import org.mozilla.geckoview.GeckoRuntimeSettings

internal object DagGeckoRuntime {
    @Volatile
    private var instance: GeckoRuntime? = null

    fun get(context: Context): GeckoRuntime =
        instance ?: synchronized(this) {
            instance
                ?: GeckoRuntime.create(
                    context.applicationContext,
                    GeckoRuntimeSettings
                        .Builder()
                        .preferredColorScheme(GeckoRuntimeSettings.COLOR_SCHEME_LIGHT)
                        .build(),
                ).also { instance = it }
        }
}
