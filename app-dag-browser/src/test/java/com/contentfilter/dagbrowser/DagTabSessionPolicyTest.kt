package com.contentfilter.dagbrowser

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DagTabSessionPolicyTest {
    @Test
    fun `keeps active tab and two most recent warm sessions`() {
        val tabs =
            listOf(
                session(1, sequence = 1),
                session(2, sequence = 8),
                session(3, active = true, sequence = 3),
                session(4, sequence = 9),
                session(5, sequence = 2),
            )

        assertEquals(setOf(1L, 5L), DagTabSessionPolicy.sessionsToHibernate(tabs))
    }

    @Test
    fun `closed sessions never consume warm capacity`() {
        val tabs =
            listOf(
                session(1, active = true, sequence = 1),
                session(2, sequence = 2),
                session(3, sequence = 3, open = false),
            )

        assertTrue(DagTabSessionPolicy.sessionsToHibernate(tabs).isEmpty())
    }

    private fun session(
        id: Long,
        active: Boolean = false,
        sequence: Long,
        open: Boolean = true,
    ) = DagOpenTabSession(
        tabId = id,
        active = active,
        open = open,
        lastActivatedSequence = sequence,
    )
}
