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
    fun `new pairing token accepts thirty two characters`() {
        assertEquals(
            "A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5D6",
            " a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6 ".normalizedUserPairingCodeOrNull(),
        )
    }

    @Test
    fun `unsupported token lengths remain invalid`() {
        assertNull("A1B2C3D".normalizedUserPairingCodeOrNull())
        assertNull("A1B2C3D4E".normalizedUserPairingCodeOrNull())
        assertNull("A1B2C3D4E5F6A7B8C9D0E1F2A3B4C5".normalizedUserPairingCodeOrNull())
    }
}
