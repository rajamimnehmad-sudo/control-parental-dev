package com.contentfilter.user.dag

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DagLauncherControllerTest {
    @Test
    fun `launcher is visible only for an activated device with DAG enabled`() {
        assertTrue(dagLauncherShouldBeEnabled(hasActivation = true, dagEnabled = true))
        assertFalse(dagLauncherShouldBeEnabled(hasActivation = false, dagEnabled = true))
        assertFalse(dagLauncherShouldBeEnabled(hasActivation = true, dagEnabled = false))
        assertFalse(dagLauncherShouldBeEnabled(hasActivation = false, dagEnabled = false))
        assertFalse(
            dagLauncherShouldBeEnabled(
                hasActivation = true,
                dagEnabled = true,
                dagEntitled = false,
            ),
        )
    }
}
