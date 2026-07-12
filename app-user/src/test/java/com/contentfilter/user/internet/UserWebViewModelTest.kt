package com.contentfilter.user.internet

import com.contentfilter.feature.vpn.domainlist.WebDomainListState
import com.contentfilter.feature.vpn.domainlist.WebDomainListStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserWebViewModelTest {
    @Test
    fun `domain list UI exposes the installed version and active canary`() {
        val state =
            WebDomainListStatus(
                version = 1234L,
                installedAtEpochMillis = 5678L,
                state = WebDomainListState.Active,
                lastCheckAtEpochMillis = 6789L,
                lastCheckResult = "Nueva version instalada",
                canaryIncluded = true,
                lastError = null,
            ).toUiState(isChecking = false)

        assertEquals(1234L, state.version)
        assertEquals("Activa", state.status)
        assertTrue(state.canaryIncluded)
        assertEquals("Nueva version instalada", state.lastCheckResult)
    }
}
