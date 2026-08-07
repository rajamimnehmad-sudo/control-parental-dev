package com.contentfilter.dagbrowser

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class DagPartialRedactionDeviceTest {
    @Test
    fun strongFrostedReplacementIsGeneratedOnDevice() {
        val source = Bitmap.createBitmap(1_000, 300, Bitmap.Config.ARGB_8888)
        try {
            Canvas(source).drawColor(Color.rgb(120, 140, 160))
            val bytes =
                ByteArrayOutputStream().use { stream ->
                    assertTrue(source.compress(Bitmap.CompressFormat.PNG, 100, stream))
                    stream.toByteArray()
                }
            val replacement =
                DagStrongFrostedRedaction.renderBase64(
                    bytes = bytes,
                    sourceWidth = 1_000,
                    sourceHeight = 300,
                    cropPlans = DagRegionalCropPlanner.plan(1_000, 300).take(1),
                )
            assertNotNull(replacement)
            val png = Base64.decode(requireNotNull(replacement), Base64.DEFAULT)
            assertTrue(png.size <= 256 * 1024)
            assertTrue(png.copyOfRange(0, 8).contentEquals(PngSignature))
        } finally {
            source.recycle()
        }
    }

    private companion object {
        val PngSignature = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10)
    }
}
