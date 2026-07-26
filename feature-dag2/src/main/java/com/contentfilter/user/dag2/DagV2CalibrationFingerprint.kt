package com.contentfilter.user.dag2

import android.graphics.BitmapFactory
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DagV2CalibrationFingerprint
    @Inject
    constructor() {
        fun calculate(normalizedJpeg: ByteArray): DagV2CalibrationFingerprintResult {
            val bitmap =
                requireNotNull(BitmapFactory.decodeByteArray(normalizedJpeg, 0, normalizedJpeg.size)) {
                    "Normalized JPEG could not be decoded"
                }
            return try {
                val pixels = IntArray(HashWidth * HashHeight)
                val scaled =
                    android.graphics.Bitmap.createScaledBitmap(
                        bitmap,
                        HashWidth,
                        HashHeight,
                        true,
                    )
                try {
                    scaled.getPixels(pixels, 0, HashWidth, 0, 0, HashWidth, HashHeight)
                    DagV2CalibrationFingerprintResult(
                        contentSha256 = normalizedJpeg.dagV2Sha256(),
                        perceptualHash = dHash(pixels),
                    )
                } finally {
                    if (scaled !== bitmap) scaled.recycle()
                }
            } finally {
                bitmap.recycle()
            }
        }

        internal fun dHash(argb: IntArray): String {
            require(argb.size == HashWidth * HashHeight)
            var hash = 0UL
            var bit = 0
            for (row in 0 until HashHeight) {
                for (column in 0 until HashWidth - 1) {
                    val left = luminance(argb[row * HashWidth + column])
                    val right = luminance(argb[row * HashWidth + column + 1])
                    if (left > right) hash = hash or (1UL shl bit)
                    bit += 1
                }
            }
            return hash.toString(16).padStart(16, '0')
        }

        private fun luminance(color: Int): Int {
            val red = color shr 16 and 0xff
            val green = color shr 8 and 0xff
            val blue = color and 0xff
            return (red * 299 + green * 587 + blue * 114) / 1_000
        }

        companion object {
            const val NearDuplicateHammingDistance = 5
            private const val HashWidth = 9
            private const val HashHeight = 8

            fun hammingDistance(
                first: String,
                second: String,
            ): Int {
                require(first.matches(Regex("[0-9a-fA-F]{16}")))
                require(second.matches(Regex("[0-9a-fA-F]{16}")))
                return (first.toULong(16) xor second.toULong(16)).countOneBits()
            }
        }
    }
