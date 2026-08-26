package com.contentfilter.feature.accessibility.chromevisual

import android.graphics.Bitmap
import java.security.MessageDigest

/** Hashes canonical RGBA rows in RAM; temporary pixel buffers are wiped before return. */
internal object ChromeVisualShieldCropEvidenceFactory {
    fun from(bitmap: Bitmap): ChromeVisualShieldCropEvidence {
        val digest = MessageDigest.getInstance("SHA-256")
        val pixels = IntArray(bitmap.width)
        val rgba = ByteArray(bitmap.width * 4)
        try {
            repeat(bitmap.height) { y ->
                bitmap.getPixels(pixels, 0, bitmap.width, 0, y, bitmap.width, 1)
                pixels.forEachIndexed { index, pixel ->
                    val offset = index * 4
                    rgba[offset] = (pixel ushr 16).toByte()
                    rgba[offset + 1] = (pixel ushr 8).toByte()
                    rgba[offset + 2] = pixel.toByte()
                    rgba[offset + 3] = (pixel ushr 24).toByte()
                }
                digest.update(rgba)
            }
            return ChromeVisualShieldCropEvidence(
                width = bitmap.width,
                height = bitmap.height,
                rgbaSha256 = digest.digest().toHex(),
            )
        } finally {
            pixels.fill(0)
            rgba.fill(0)
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
