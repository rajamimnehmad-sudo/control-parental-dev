package com.contentfilter.user.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MyAppsScreenStateTest {
    @Test
    fun `refreshing an empty inventory shows progress instead of empty result`() {
        assertEquals(
            "Buscando aplicaciones instaladas…",
            myAppsEmptyStateMessage(appsEmpty = true, refreshing = true),
        )
    }

    @Test
    fun `loaded inventory does not show an empty state while refreshing`() {
        assertNull(myAppsEmptyStateMessage(appsEmpty = false, refreshing = true))
    }

    @Test
    fun `finished empty inventory shows the actual empty result`() {
        assertEquals(
            "No hay apps detectadas todavía.",
            myAppsEmptyStateMessage(appsEmpty = true, refreshing = false),
        )
    }
}
