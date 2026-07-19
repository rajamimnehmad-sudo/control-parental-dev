package com.contentfilter.user.dag

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseDetection
import com.google.mlkit.vision.pose.PoseDetector
import com.google.mlkit.vision.pose.PoseLandmark
import com.google.mlkit.vision.pose.defaults.PoseDetectorOptions
import java.io.Closeable
import java.util.concurrent.TimeUnit
import kotlin.math.hypot
import kotlin.math.sqrt

internal data class DagTzniutPoseScores(
    val sleevesAboveElbow: Float = 0f,
    val hemAboveKnee: Float = 0f,
)

internal class DagTzniutPoseClassifier : Closeable {
    private val detectorDelegate =
        lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            val options =
                PoseDetectorOptions.Builder()
                    .setDetectorMode(PoseDetectorOptions.SINGLE_IMAGE_MODE)
                    .setPreferredHardwareConfigs(PoseDetectorOptions.CPU)
                    .build()
            PoseDetection.getClient(options)
        }
    private val detector: PoseDetector by detectorDelegate

    fun classify(bitmap: Bitmap): DagTzniutPoseScores {
        val startedAt = SystemClock.elapsedRealtime()
        val scores =
            runCatching {
                val pose =
                    Tasks.await(
                        detector.process(InputImage.fromBitmap(bitmap, 0)),
                        TimeoutSeconds,
                        TimeUnit.SECONDS,
                    )
                DagTzniutPoseScores(
                    sleevesAboveElbow = maxOf(pose.armExposure(bitmap, true), pose.armExposure(bitmap, false)),
                    hemAboveKnee = maxOf(pose.legExposure(bitmap, true), pose.legExposure(bitmap, false)),
                )
            }.getOrDefault(DagTzniutPoseScores())
        Log.d(
            MetricsTag,
            "pose_elapsed_ms=${SystemClock.elapsedRealtime() - startedAt} sleeves=${scores.sleevesAboveElbow} " +
                "hem=${scores.hemAboveKnee}",
        )
        return scores
    }

    override fun close() {
        if (detectorDelegate.isInitialized()) detector.close()
    }

    private fun Pose.armExposure(
        bitmap: Bitmap,
        left: Boolean,
    ): Float {
        val shoulder = confident(if (left) PoseLandmark.LEFT_SHOULDER else PoseLandmark.RIGHT_SHOULDER) ?: return 0f
        val elbow = confident(if (left) PoseLandmark.LEFT_ELBOW else PoseLandmark.RIGHT_ELBOW) ?: return 0f
        val wrist = confident(if (left) PoseLandmark.LEFT_WRIST else PoseLandmark.RIGHT_WRIST) ?: return 0f
        val radius = maxOf(MinimumSampleRadius, shoulder.distanceTo(elbow) * SampleRadiusRatio)
        val upperArm = bitmap.skinRatio(shoulder.point(), elbow.point(), 0.35f, 0.88f, radius)
        val forearm = bitmap.skinRatio(elbow.point(), wrist.point(), 0.12f, 0.55f, radius)
        return exposureScore(upperArm, forearm, shoulder.likelihood(), elbow.likelihood(), wrist.likelihood())
    }

    private fun Pose.legExposure(
        bitmap: Bitmap,
        left: Boolean,
    ): Float {
        val hip = confident(if (left) PoseLandmark.LEFT_HIP else PoseLandmark.RIGHT_HIP) ?: return 0f
        val knee = confident(if (left) PoseLandmark.LEFT_KNEE else PoseLandmark.RIGHT_KNEE) ?: return 0f
        val ankle = confident(if (left) PoseLandmark.LEFT_ANKLE else PoseLandmark.RIGHT_ANKLE) ?: return 0f
        val radius = maxOf(MinimumSampleRadius, hip.distanceTo(knee) * SampleRadiusRatio)
        val thigh = bitmap.skinRatio(hip.point(), knee.point(), 0.55f, 0.92f, radius)
        val lowerLeg = bitmap.skinRatio(knee.point(), ankle.point(), 0.10f, 0.45f, radius)
        return exposureScore(thigh, lowerLeg, hip.likelihood(), knee.likelihood(), ankle.likelihood())
    }

    private fun Pose.confident(type: Int): PoseLandmark? =
        getPoseLandmark(type)?.takeIf { it.inFrameLikelihood >= MinimumLandmarkLikelihood }

    private fun exposureScore(
        proximalSkin: Float,
        distalSkin: Float,
        vararg likelihoods: Float,
    ): Float {
        if (proximalSkin < MinimumSkinRatio || distalSkin < MinimumSkinRatio) return 0f
        val skinConfidence = sqrt(proximalSkin * distalSkin)
        return (skinConfidence * (likelihoods.minOrNull() ?: 0f)).coerceIn(0f, 1f)
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

    private fun PoseLandmark.point() = Point(position.x, position.y)

    private fun PoseLandmark.likelihood(): Float = inFrameLikelihood.coerceIn(0f, 1f)

    private fun PoseLandmark.distanceTo(other: PoseLandmark): Float =
        hypot(position.x - other.position.x, position.y - other.position.y)

    private data class Point(val x: Float, val y: Float)

    private companion object {
        const val TimeoutSeconds = 2L
        const val MinimumLandmarkLikelihood = 0.72f
        const val MinimumSkinRatio = 0.58f
        const val SegmentSamples = 7
        const val MinimumSampleRadius = 2f
        const val SampleRadiusRatio = 0.08f
        const val MetricsTag = "DagImageMetrics"
    }
}
