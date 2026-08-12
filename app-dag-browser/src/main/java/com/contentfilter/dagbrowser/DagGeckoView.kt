package com.contentfilter.dagbrowser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.util.AttributeSet
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import org.mozilla.geckoview.GeckoView

internal class DagGeckoView(
    context: Context,
    attrs: AttributeSet? = null,
) : GeckoView(context, attrs) {
    fun captureRegion(
        source: Rect,
        targetWidth: Int,
        targetHeight: Int,
        callbackHandler: Handler,
        callback: (Bitmap?, Int) -> Unit,
    ) {
        val surface = findSurfaceView(this)
        if (
            surface == null ||
            !surface.holder.surface.isValid ||
            source.isEmpty ||
            targetWidth <= 0 ||
            targetHeight <= 0
        ) {
            callback(null, PixelCopy.ERROR_SOURCE_INVALID)
            return
        }
        val destination = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        runCatching {
            PixelCopy.request(
                surface,
                source,
                destination,
                { result ->
                    if (result == PixelCopy.SUCCESS) {
                        callback(destination, result)
                    } else {
                        destination.recycle()
                        callback(null, result)
                    }
                },
                callbackHandler,
            )
        }.onFailure {
            destination.recycle()
            callback(null, PixelCopy.ERROR_UNKNOWN)
        }
    }

    private fun findSurfaceView(view: View): SurfaceView? {
        if (view is SurfaceView) return view
        if (view !is ViewGroup) return null
        for (index in 0 until view.childCount) {
            findSurfaceView(view.getChildAt(index))?.let { return it }
        }
        return null
    }
}
