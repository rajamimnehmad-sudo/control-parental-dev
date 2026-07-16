package com.contentfilter.user.dag

import java.security.MessageDigest

internal fun dagPageFingerprint(
    url: String,
    title: String,
    text: String,
    images: DagImagePageSummary,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    listOf(
        url,
        title,
        text.take(DagContentClassifier.MaxPageCharacters),
        images.allowed.toString(),
        images.blocked.toString(),
        images.uncertain.toString(),
    ).forEach { part ->
        val bytes = part.toByteArray(Charsets.UTF_8)
        digest.update(bytes.size.toString().toByteArray(Charsets.US_ASCII))
        digest.update(0.toByte())
        digest.update(bytes)
        digest.update(0.toByte())
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}
