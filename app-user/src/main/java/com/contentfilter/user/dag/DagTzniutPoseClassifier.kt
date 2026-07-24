package com.contentfilter.user.dag

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.hypot
import kotlin.math.sqrt

internal data class DagTzniutPoseScores(
    val sleevesAboveElbow: Float = 0f,
    val hemAboveKnee: Float = 0f,
)

internal class DagTzniutPoseClassifier(
    context: Context,
) : Closeable {
    private val applicationContext = context.applicationContext
    private var interpreter: Interpreter? = null

    fun classify(bitmap: Bitmap): DagTzniutPoseScores {
        val startedAt = SystemClock.elapsedRealtime()
        val scores =
            runCatching {
                val inputBitmap = bitmap.resizeWithPadding(InputSize)
                try {
                    val pose = detectPose(inputBitmap)
                    DagTzniutPoseScores(
                        sleevesAboveElbow =
                            maxOf(
                                pose.armExposure(inputBitmap, true),
                                pose.armExposure(inputBitmap, false),
                            ),
                        hemAboveKnee =
                            maxOf(
                                pose.legExposure(inputBitmap, true),
                                pose.legExposure(inputBitmap, false),
                            ),
                    )
                } finally {
                    inputBitmap.recycle()
                }
            }.getOrDefault(DagTzniutPoseScores())
        Log.d(
            MetricsTag,
            "pose_elapsed_ms=${SystemClock.elapsedRealtime() - startedAt} sleeves=${scores.sleevesAboveElbow} " +
                "hem=${scores.hemAboveKnee}",
        )
        return scores
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
    }

    private fun detectPose(bitmap: Bitmap): Pose {
        val input = ByteBuffer.allocateDirect(InputSize * InputSize * 3).order(ByteOrder.nativeOrder())
        val pixels = IntArray(InputSize * InputSize)
        bitmap.getPixels(pixels, 0, InputSize, 0, 0, InputSize, InputSize)
        pixels.forEach { pixel ->
            input.put(Color.red(pixel).toByte())
            input.put(Color.green(pixel).toByte())
            input.put(Color.blue(pixel).toByte())
        }
        input.rewind()
        val output = Array(1) { Array(1) { Array(KeypointCount) { FloatArray(KeypointValues) } } }
        model().run(input, output)
        return Pose(output[0][0].map { values -> Keypoint(values[1], values[0], values[2]) })
    }

    private fun model(): Interpreter =
        interpreter ?: loadModel().also { loaded ->
            check(loaded.getInputTensor(0).shape().contentEquals(intArrayOf(1, InputSize, InputSize, 3)))
            check(loaded.getInputTensor(0).dataType() == DataType.UINT8)
            check(loaded.getOutputTensor(0).shape().contentEquals(intArrayOf(1, 1, KeypointCount, KeypointValues)))
            check(loaded.getOutputTensor(0).dataType() == DataType.FLOAT32)
            interpreter = loaded
        }

    private fun loadModel(): Interpreter {
        val descriptor = applicationContext.assets.openFd(ModelAsset)
        descriptor.use {
            FileInputStream(it.fileDescriptor).channel.use { channel ->
                val mapped = channel.map(FileChannel.MapMode.READ_ONLY, it.startOffset, it.declaredLength)
                return Interpreter(mapped, Interpreter.Options().setNumThreads(ModelThreads))
            }
        }
    }

    private fun Pose.armExposure(
        bitmap: Bitmap,
        left: Boolean,
    ): Float {
        val shoulder = confident(if (left) LeftShoulder else RightShoulder) ?: return 0f
        val elbow = confident(if (left) LeftElbow else RightElbow) ?: return 0f
        val wrist = confident(if (left) LeftWrist else RightWrist) ?: return 0f
        val radius = maxOf(MinimumSampleRadius, shoulder.distanceTo(elbow) * SampleRadiusRatio)
        val upperArm = bitmap.skinRatio(shoulder.point(bitmap), elbow.point(bitmap), 0.35f, 0.88f, radius)
        val forearm = bitmap.skinRatio(elbow.point(bitmap), wrist.point(bitmap), 0.12f, 0.55f, radius)
        return exposureScore(upperArm, forearm, shoulder.score, elbow.score, wrist.score)
    }

    private fun Pose.legExposure(
        bitmap: Bitmap,
        left: Boolean,
    ): Float {
        val hip = confident(if (left) LeftHip else RightHip) ?: return 0f
        val knee = confident(if (left) LeftKnee else RightKnee) ?: return 0f
        val ankle = confident(if (left) LeftAnkle else RightAnkle) ?: return 0f
        val radius = maxOf(MinimumSampleRadius, hip.distanceTo(knee) * SampleRadiusRatio)
        val thigh = bitmap.skinRatio(hip.point(bitmap), knee.point(bitmap), 0.55f, 0.92f, radius)
        val lowerLeg = bitmap.skinRatio(knee.point(bitmap), ankle.point(bitmap), 0.10f, 0.45f, radius)
        return exposureScore(thigh, lowerLeg, hip.score, knee.score, ankle.score)
    }

    private fun Pose.confident(index: Int): Keypoint? =
        keypoints[index].takeIf { it.score >= MinimumKeypointConfidence }

    private fun exposureScore(
        proximalSkin: Float,
        distalSkin: Float,
        vararg confidence: Float,
    ): Float {
        if (proximalSkin < MinimumSkinRatio || distalSkin < MinimumSkinRatio) return 0f
        return (sqrt(proximalSkin * distalSkin) * (confidence.minOrNull() ?: 0f)).coerceIn(0f, 1f)
    }

    private fun Bitmap.skinRatio(
        start: Point,
        end: Point,
        from: Float,
        to: Float,
        radius: Float,
    ): Float {
        var skin = 0
        var sampled = 0
        for (step in 0 until SegmentSamples) {
            val progress = from + (to - from) * step / (SegmentSamples - 1).toFloat()
            val x = start.x + (end.x - start.x) * progress
            val y = start.y + (end.y - start.y) * progress
            val sampleRadius = radius.toInt().coerceAtLeast(1)
            for (offsetX in -sampleRadius..sampleRadius step 2) {
                for (offsetY in -sampleRadius..sampleRadius step 2) {
                    val pixelX = (x + offsetX).toInt()
                    val pixelY = (y + offsetY).toInt()
                    if (pixelX !in 0 until width || pixelY !in 0 until height) continue
                    sampled += 1
                    if (getPixel(pixelX, pixelY).isProbableSkin()) skin += 1
                }
            }
        }
        return if (sampled == 0) 0f else skin.toFloat() / sampled
    }

    private fun Bitmap.resizeWithPadding(size: Int): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val scale = minOf(size.toFloat() / width, size.toFloat() / height)
        val targetWidth = width * scale
        val targetHeight = height * scale
        val left = (size - targetWidth) / 2f
        val top = (size - targetHeight) / 2f
        Canvas(output).apply {
            drawColor(Color.BLACK)
            drawBitmap(
                this@resizeWithPadding,
                null,
                RectF(left, top, left + targetWidth, top + targetHeight),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        }
        return output
    }

    private fun Int.isProbableSkin(): Boolean {
        val red = Color.red(this)
        val green = Color.green(this)
        val blue = Color.blue(this)
        val maximum = maxOf(red, green, blue)
        val minimum = minOf(red, green, blue)
        val rgbRule = red > 70 && green > 35 && blue > 20 && maximum - minimum > 12 && red >= green && red > blue
        val y = 0.299 * red + 0.587 * green + 0.114 * blue
        val cb = 128 - 0.168736 * red - 0.331264 * green + 0.5 * blue
        val cr = 128 + 0.5 * red - 0.418688 * green - 0.081312 * blue
        return rgbRule && y > 45 && cb in 72.0..138.0 && cr in 125.0..180.0
    }

    private fun Keypoint.point(bitmap: Bitmap) = Point(x * bitmap.width, y * bitmap.height)

    private fun Keypoint.distanceTo(other: Keypoint): Float = hypot(x - other.x, y - other.y) * InputSize

    private data class Pose(val keypoints: List<Keypoint>)

    private data class Keypoint(
        val x: Float,
        val y: Float,
        val score: Float,
    )

    private data class Point(val x: Float, val y: Float)

    private companion object {
        const val ModelAsset = "dag/movenet_singlepose_lightning_int8.tflite"
        const val ModelThreads = 2
        const val InputSize = 192
        const val KeypointCount = 17
        const val KeypointValues = 3
        const val LeftShoulder = 5
        const val RightShoulder = 6
        const val LeftElbow = 7
        const val RightElbow = 8
        const val LeftWrist = 9
        const val RightWrist = 10
        const val LeftHip = 11
        const val RightHip = 12
        const val LeftKnee = 13
        const val RightKnee = 14
        const val LeftAnkle = 15
        const val RightAnkle = 16
        const val MinimumKeypointConfidence = 0.48f
        const val MinimumSkinRatio = 0.58f
        const val SegmentSamples = 7
        const val MinimumSampleRadius = 2f
        const val SampleRadiusRatio = 0.08f
        const val MetricsTag = "DagImageMetrics"
    }
}
