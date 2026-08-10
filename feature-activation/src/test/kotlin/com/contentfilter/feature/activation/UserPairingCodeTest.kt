package com.contentfilter.feature.activation

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserPairingCodeTest {
    @Test
    fun `normal activation token accepts six characters`() {
        assertEquals("A1B2C3", " a1b2c3 ".normalizedUserPairingCodeOrNull())
    }

    @Test
    fun `relink token accepts eight characters`() {
        assertEquals("A1B2C3D4", "a1b2c3d4".normalizedUserPairingCodeOrNull())
    }

    @Test
    fun `unsupported token lengths remain invalid`() {
        assertNull("A1B2C3D".normalizedUserPairingCodeOrNull())
        assertNull("A1B2C3D4E".normalizedUserPairingCodeOrNull())
    }
}
