package com.contentfilter.feature.accessibility.chromevisual

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Region
import android.hardware.display.DisplayManager
import android.os.Binder
import android.os.Build
import android.util.Log
import android.view.Display
import android.view.SurfaceControl
import android.view.SurfaceControlViewHost
import android.view.View
import android.view.ViewTreeObserver
import android.view.WindowManager
import androidx.annotation.RequiresApi

internal enum class ChromePhotosProtectedSurfaceHostResult {
    Ready,
    Pending,
    Failed,
}

internal interface ChromePhotosProtectedSurfaceHost : AutoCloseable {
    val windowId: Int
    val extent: Int

    fun ensureWindowAndExtent(
        targetWindowId: Int,
        viewport: ChromeVisualViewport,
    ): ChromePhotosProtectedSurfaceHostResult
}

internal object ChromePhotosProtectedSurfaceHostFactory {
    fun visualContext(service: AccessibilityService): Context? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return ChromePhotosWindowAttachedSurfaceHost.visualContext(service)
    }

    fun create(
        service: AccessibilityService,
        windowId: Int,
        viewport: ChromeVisualViewport,
        view: View,
        onPublicationChanged: () -> Unit,
    ): ChromePhotosProtectedSurfaceHost? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return ChromePhotosWindowAttachedSurfaceHost.create(
            service,
            windowId,
            viewport,
            view,
            onPublicationChanged,
        )
    }
}

/** API-34 implementation kept out of the always-loaded Accessibility controller path. */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
private class ChromePhotosWindowAttachedSurfaceHost private constructor(
    private val service: AccessibilityService,
    private val viewHost: SurfaceControlViewHost,
    private val surfacePackage: SurfaceControlViewHost.SurfacePackage,
    private val surfaceControl: SurfaceControl,
    private val displayWidth: Int,
    private val displayHeight: Int,
    private val onPublicationChanged: () -> Unit,
    override var windowId: Int,
    override var extent: Int,
) : ChromePhotosProtectedSurfaceHost {
    private var publicationResult = ChromePhotosProtectedSurfaceHostResult.Pending
    private var requestedWindowId = windowId
    private var pendingDrawListener: ViewTreeObserver.OnDrawListener? = null
    private var closed = false

    override fun ensureWindowAndExtent(
        targetWindowId: Int,
        viewport: ChromeVisualViewport,
    ): ChromePhotosProtectedSurfaceHostResult {
        if (closed || publicationResult == ChromePhotosProtectedSurfaceHostResult.Failed) {
            return ChromePhotosProtectedSurfaceHostResult.Failed
        }
        requestedWindowId = targetWindowId
        val requiredExtent = requiredExtent(viewport, displayWidth, displayHeight)
        if (requiredExtent > extent) {
            val resized =
                runCatching {
                    viewHost.relayout(requiredExtent, requiredExtent)
                    true
                }.getOrDefault(false)
            if (!resized) return ChromePhotosProtectedSurfaceHostResult.Failed
            extent = requiredExtent
        }
        if (publicationResult == ChromePhotosProtectedSurfaceHostResult.Pending) {
            return ChromePhotosProtectedSurfaceHostResult.Pending
        }
        if (targetWindowId != windowId) {
            val transferred =
                runCatching {
                    service.attachAccessibilityOverlayToWindow(targetWindowId, surfaceControl)
                    true
                }.getOrDefault(false)
            if (!transferred) return ChromePhotosProtectedSurfaceHostResult.Failed
            windowId = targetWindowId
        }
        return ChromePhotosProtectedSurfaceHostResult.Ready
    }

    override fun close() {
        closed = true
        pendingDrawListener?.let { listener ->
            viewHost.view?.viewTreeObserver?.takeIf { it.isAlive }?.removeOnDrawListener(listener)
        }
        pendingDrawListener = null
        val transaction = SurfaceControl.Transaction()
        try {
            transaction.reparent(surfaceControl, null).apply()
        } catch (_: RuntimeException) {
            // Releasing the owning host below is the final cleanup path.
        } finally {
            transaction.close()
        }
        runCatching { surfacePackage.release() }
        runCatching { viewHost.release() }
    }

    companion object {
        fun create(
            service: AccessibilityService,
            windowId: Int,
            viewport: ChromeVisualViewport,
            view: View,
            onPublicationChanged: () -> Unit,
        ): ChromePhotosWindowAttachedSurfaceHost? {
            val display = defaultDisplay(service) ?: return null
            val mode = display.mode
            val requiredExtent =
                requiredExtent(viewport, mode.physicalWidth, mode.physicalHeight)
            val host =
                runCatching {
                    @Suppress("DEPRECATION")
                    SurfaceControlViewHost(view.context, display, Binder())
                }.getOrElse {
                    Log.w(LogTag, "phase=host_create stage=view_host result=failed", it)
                    return null
                }
            val packageAndControl =
                runCatching {
                    host.setView(view, requiredExtent, requiredExtent)
                    val hostedPackage = requireNotNull(host.surfacePackage)
                    hostedPackage to hostedPackage.surfaceControl
                }.getOrElse {
                    Log.w(LogTag, "phase=host_create stage=set_view result=failed", it)
                    runCatching { host.release() }
                    return null
                }
            val hostedPackage = packageAndControl.first
            val hostedControl = packageAndControl.second
            val attachedHost =
                ChromePhotosWindowAttachedSurfaceHost(
                    service = service,
                    viewHost = host,
                    surfacePackage = hostedPackage,
                    surfaceControl = hostedControl,
                    displayWidth = mode.physicalWidth,
                    displayHeight = mode.physicalHeight,
                    onPublicationChanged = onPublicationChanged,
                    windowId = windowId,
                    extent = requiredExtent,
                )
            attachedHost.beginPublication(view)
            return attachedHost
        }

        fun visualContext(service: AccessibilityService): Context? {
            val display = defaultDisplay(service) ?: return null
            return service.createDisplayContext(display)
        }

        private fun defaultDisplay(service: AccessibilityService): Display? =
            service
                .getSystemService(DisplayManager::class.java)
                ?.getDisplay(Display.DEFAULT_DISPLAY)

        private fun requiredExtent(
            viewport: ChromeVisualViewport,
            displayWidth: Int,
            displayHeight: Int,
        ): Int =
            ChromePhotosProtectedSurfaceHostPolicy.requiredExtent(
                viewport = viewport,
                displayWidth = displayWidth,
                displayHeight = displayHeight,
            )

        private fun applyPublishedSurfaceProperties(control: SurfaceControl) {
            val transaction = SurfaceControl.Transaction()
            try {
                transaction
                    .setLayer(control, 1)
                    .setVisibility(control, true)
                    .apply()
            } finally {
                transaction.close()
            }
        }

        private const val LogTag = "ChromePhotosSurfaceHost"
    }

    private fun beginPublication(view: View) {
        afterNextDraw(view) {
            if (closed) return@afterNextDraw
            val prepared =
                runCatching {
                    val params = requireNotNull(view.layoutParams as? WindowManager.LayoutParams)
                    params.flags =
                        params.flags or
                        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                    view.layoutParams = params
                    requireNotNull(view.rootSurfaceControl).setTouchableRegion(Region())
                    true
                }.getOrElse {
                    failPublication("input", it)
                    false
                }
            if (!prepared) return@afterNextDraw
            // Wait for the relayout/draw that applies the non-touchable input metadata. Chrome is
            // not the parent yet, so the intermediate local host cannot intercept device input.
            afterNextDraw(view) { publish() }
        }
    }

    private fun publish() {
        if (closed) return
        val published =
            runCatching {
                applyPublishedSurfaceProperties(surfaceControl)
                service.attachAccessibilityOverlayToWindow(requestedWindowId, surfaceControl)
                true
            }.getOrElse {
                failPublication("attach", it)
                false
            }
        if (!published || closed) return
        windowId = requestedWindowId
        publicationResult = ChromePhotosProtectedSurfaceHostResult.Ready
        onPublicationChanged()
    }

    private fun afterNextDraw(
        view: View,
        action: () -> Unit,
    ) {
        lateinit var listener: ViewTreeObserver.OnDrawListener
        listener =
            ViewTreeObserver.OnDrawListener {
                view.post {
                    if (pendingDrawListener === listener) pendingDrawListener = null
                    view.viewTreeObserver
                        .takeIf { it.isAlive }
                        ?.removeOnDrawListener(listener)
                    action()
                }
            }
        pendingDrawListener = listener
        view.viewTreeObserver.addOnDrawListener(listener)
        view.invalidate()
    }

    private fun failPublication(
        stage: String,
        error: Throwable,
    ) {
        if (closed) return
        publicationResult = ChromePhotosProtectedSurfaceHostResult.Failed
        Log.w(LogTag, "phase=host_publish stage=$stage result=failed", error)
        onPublicationChanged()
    }
}
