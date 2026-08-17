package com.contentfilter.dagbrowser

import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.ProgressBar

/** Owns browser chrome and system-bar visibility without coupling it to video policy. */
internal class DagFullscreenChromeController(
    private val window: Window,
    private val root: View,
    private val toolbar: View,
    private val progress: ProgressBar,
) {
    var active: Boolean = false
        private set

    private var progressVisible = false
    private var progressValue = 0

    fun setFullscreen(fullscreen: Boolean) {
        if (active == fullscreen) return
        active = fullscreen
        toolbar.visibility = if (fullscreen) View.GONE else View.VISIBLE
        applySystemBars(fullscreen)
        renderProgress(progressVisible, progressValue)
        root.requestApplyInsets()
    }

    fun renderProgress(
        visible: Boolean,
        value: Int,
    ) {
        progressVisible = visible
        progressValue = value
        progress.visibility = if (visible && !active) View.VISIBLE else View.GONE
        if (visible) progress.setProgress(value, true)
    }

    private fun applySystemBars(fullscreen: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                if (fullscreen) {
                    systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsets.Type.systemBars())
                } else {
                    show(WindowInsets.Type.systemBars())
                }
            }
            return
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility =
            if (fullscreen) {
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            } else {
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            }
    }
}
