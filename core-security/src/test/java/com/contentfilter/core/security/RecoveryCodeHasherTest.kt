package com.contentfilter.core.security

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class RecoveryCodeHasherTest {
    @Test
    fun generatedCodeMatchesVerifier() {
        val hasher = RecoveryCodeHasher()
        val material = hasher.generate()

        assertTrue(hasher.matches(material.code, material.salt, material.verifier))
        assertTrue(hasher.matches(material.code.replace("-", "").lowercase(), material.salt, material.verifier))
        assertFalse(hasher.matches("2222-2222-2222-2222", material.salt, material.verifier))
    }

    @Test
    fun generatedCodeHasSixteenUnambiguousCharacters() {
        val material = RecoveryCodeHasher().generate()
        val compact = material.code.replace("-", "")

        assertTrue(compact.length == 16)
        assertFalse(compact.any { it in "01IO" })
    }
}
