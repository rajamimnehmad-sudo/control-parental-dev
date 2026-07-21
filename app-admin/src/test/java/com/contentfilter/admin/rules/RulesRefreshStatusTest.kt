package com.contentfilter.admin.rules

import kotlin.test.Test
import kotlin.test.assertEquals

class RulesRefreshStatusTest {
    @Test
    fun `refresh status remains visible after success`() {
        val refreshedAt = 1_000_000L
        val state =
            RulesUiState(
                devicesLastRefreshedAtEpochMillis = refreshedAt,
                offlineMode = false,
            )

        assertEquals("Actualizado ahora", state.deviceRefreshStatusText(refreshedAt + 30_000L))
        assertEquals("Actualizado hace 2 min", state.deviceRefreshStatusText(refreshedAt + 120_000L))
    }

    @Test
    fun `refresh progress and error take precedence`() {
        assertEquals(
            "Actualizando…",
            RulesUiState(devicesRefreshing = true, devicesRefreshError = "sin red")
                .deviceRefreshStatusText(nowEpochMillis = 0L),
        )
        assertEquals(
            "No se pudo actualizar",
            RulesUiState(devicesRefreshError = "sin red")
                .deviceRefreshStatusText(nowEpochMillis = 0L),
        )
    }
}
