package com.contentfilter.admin.requests

import kotlin.test.Test
import kotlin.test.assertEquals

class AdminRequestsRefreshStatusTest {
    @Test
    fun `success stays visible with relative time`() {
        val refreshedAt = 1_000_000L
        val state = AdminRequestsUiState(lastRefreshedAtEpochMillis = refreshedAt, offlineMode = false)

        assertEquals("Actualizado ahora", state.refreshStatusText(refreshedAt + 10_000L))
        assertEquals("Actualizado hace 3 min", state.refreshStatusText(refreshedAt + 180_000L))
    }

    @Test
    fun `loading and failure take precedence`() {
        assertEquals(
            "Actualizando…",
            AdminRequestsUiState(isLoading = true, lastSyncMessage = "No se pudo actualizar.")
                .refreshStatusText(0L),
        )
        assertEquals(
            "No se pudo actualizar",
            AdminRequestsUiState(lastSyncMessage = "No se pudo actualizar.")
                .refreshStatusText(0L),
        )
    }
}
