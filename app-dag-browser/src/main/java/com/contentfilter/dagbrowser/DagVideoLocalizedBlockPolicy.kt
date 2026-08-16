package com.contentfilter.dagbrowser

/** Keeps only the affected video covered when the page itself remains safe and usable. */
internal object DagVideoLocalizedBlockPolicy {
    fun supports(reason: String?): Boolean =
        reason == "frame_blocked" ||
            reason == "bootstrap_no_backing_timeout" ||
            reason == "bootstrap_play_rejected" ||
            reason == "bootstrap_unavailable"
}
