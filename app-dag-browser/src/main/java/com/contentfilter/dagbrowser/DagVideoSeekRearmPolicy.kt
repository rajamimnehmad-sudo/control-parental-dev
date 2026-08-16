package com.contentfilter.dagbrowser

/** Allows a seek to re-enter analysis only in the exact surviving Gecko document. */
internal object DagVideoSeekRearmPolicy {
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
