package com.contentfilter.dagbrowser

/** Keeps browser chrome and video authority transitions idempotent across duplicate Gecko callbacks. */
internal object DagFullscreenTransitionPolicy {
    enum class Action {
        Ignore,
        UpdateChrome,
        CoverAndRearm,
    }

    fun decide(
        currentFullscreen: Boolean,
        requestedFullscreen: Boolean,
        protectedVideoActive: Boolean,
    ): Action =
        when {
            currentFullscreen == requestedFullscreen -> Action.Ignore
            protectedVideoActive -> Action.CoverAndRearm
            else -> Action.UpdateChrome
        }

    fun rearmReason(fullscreen: Boolean): String =
        if (fullscreen) "fullscreen_transition" else "fullscreen_exit_transition"
}
