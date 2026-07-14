package com.contentfilter.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.inject.Inject

data class RecoveryCodeMaterial(
    val code: String,
    val salt: String,
    val verifier: String,
)

class RecoveryCodeHasher
    @Inject
    constructor() {
        fun generate(): RecoveryCodeMaterial {
            val random = SecureRandom()
            val code =
                buildString(CodeLength) {
                    repeat(CodeLength) { append(Alphabet[random.nextInt(Alphabet.length)]) }
                }
            val saltBytes = ByteArray(SaltLength).also(random::nextBytes)
            return RecoveryCodeMaterial(
                code = code.chunked(4).joinToString("-"),
                salt = saltBytes.base64(),
                verifier = derive(code, saltBytes).base64(),
            )
        }

        fun matches(
            rawCode: String,
            salt: String,
            expectedVerifier: String,
        ): Boolean =
            runCatching {
                val normalized = rawCode.normalizeCode()
                if (normalized.length != CodeLength) return false
                val actual = derive(normalized, Base64.getDecoder().decode(salt))
                val expected = Base64.getDecoder().decode(expectedVerifier)
                MessageDigest.isEqual(actual, expected)
            }.getOrDefault(false)

        private fun derive(
            normalizedCode: String,
            salt: ByteArray,
        ): ByteArray {
            val spec = PBEKeySpec(normalizedCode.toCharArray(), salt, Iterations, KeyLengthBits)
            return try {
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        }

        private fun String.normalizeCode(): String = uppercase().filter { it in Alphabet }

        private fun ByteArray.base64(): String = Base64.getEncoder().withoutPadding().encodeToString(this)

        private companion object {
            const val Alphabet = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
            const val CodeLength = 16
            const val SaltLength = 16
            const val Iterations = 120_000
            const val KeyLengthBits = 256
        }
    }
