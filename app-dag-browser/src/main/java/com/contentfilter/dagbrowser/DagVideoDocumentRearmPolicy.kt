package com.contentfilter.dagbrowser

/** Rearms media analysis only inside the exact surviving Gecko document. */
internal object DagVideoDocumentRearmPolicy {
    private val FreshAuthorityReasons =
        setOf(
            "seek_requested",
            "authority_changed",
            "viewport_changed",
            "source_changed",
            "active_video_mutated",
        )

    fun supports(reason: String): Boolean = reason in FreshAuthorityReasons

    fun allow(
        runtimeEnabled: Boolean,
        activeTab: Boolean,
        attachedSession: Boolean,
        openSession: Boolean,
        exactDocument: Boolean,
    ): Boolean =
        runtimeEnabled &&
            activeTab &&
            attachedSession &&
            openSession &&
            exactDocument
}
