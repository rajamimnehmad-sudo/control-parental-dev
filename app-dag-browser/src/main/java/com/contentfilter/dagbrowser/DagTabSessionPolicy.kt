package com.contentfilter.dagbrowser

internal data class DagOpenTabSession(
    val tabId: Long,
    val active: Boolean,
    val open: Boolean,
    val lastActivatedSequence: Long,
)

/** Keeps Gecko decoders and content processes bounded independently from the tab list. */
internal object DagTabSessionPolicy {
    const val MaxOpenSessions = 1

    fun sessionsToHibernate(tabs: List<DagOpenTabSession>): Set<Long> {
        val openTabs = tabs.filter(DagOpenTabSession::open)
        if (openTabs.size <= MaxOpenSessions) return emptySet()
        val keep =
            openTabs
                .sortedWith(
                    compareByDescending<DagOpenTabSession> { it.active }
                        .thenByDescending(DagOpenTabSession::lastActivatedSequence),
                ).take(MaxOpenSessions)
                .mapTo(mutableSetOf(), DagOpenTabSession::tabId)
        return openTabs
            .asSequence()
            .filterNot(DagOpenTabSession::active)
            .map(DagOpenTabSession::tabId)
            .filterNot(keep::contains)
            .toSet()
    }
}
