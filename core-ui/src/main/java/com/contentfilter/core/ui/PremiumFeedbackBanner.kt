package com.contentfilter.core.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@Composable
fun PremiumFeedbackBanner(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    if (text.isBlank()) return
    val mood = rememberBannerFishMood(text = text, isError = isError)
    val patrol = rememberInfiniteTransition(label = "banner-fish-patrol")
    val patrolX by patrol.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 9200
                        0f at 0 using FastOutSlowInEasing
                        -46f at 900 using FastOutSlowInEasing
                        -210f at 2100 using FastOutSlowInEasing
                        -230f at 2700 using LinearEasing
                        28f at 3650 using FastOutSlowInEasing
                        44f at 4050 using FastOutSlowInEasing
                        -72f at 5250 using FastOutSlowInEasing
                        -32f at 6500 using FastOutSlowInEasing
                        0f at 9200 using FastOutSlowInEasing
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "banner-fish-patrol-x",
    )
    val patrolY by patrol.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 2400
                        0f at 0 using FastOutSlowInEasing
                        -4f at 450 using FastOutSlowInEasing
                        3f at 1150 using FastOutSlowInEasing
                        -2f at 1850 using FastOutSlowInEasing
                        0f at 2400 using FastOutSlowInEasing
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "banner-fish-patrol-y",
    )
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(
                    brush =
                        Brush.linearGradient(
                            colors = listOf(Color(0xFF18C7B7), Color(0xFF3A7DFF), Color(0xFF1E2A4A)),
                        ),
                    shape = RoundedCornerShape(14.dp),
                )
                .padding(start = 10.dp, top = 5.dp, end = 10.dp, bottom = 5.dp),
    ) {
        BannerFishMascot(
            mood = mood,
            modifier =
                Modifier
                    .align(Alignment.CenterEnd)
                    .size(46.dp)
                    .graphicsLayer {
                        alpha = 0.66f
                        translationX = patrolX.dp.toPx()
                        translationY = patrolY.dp.toPx()
                    },
        )
        Crossfade(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 4.dp, end = 58.dp)
                    .align(Alignment.CenterStart),
            targetState = text,
            animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            label = "feedback-banner-text",
        ) { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
    }
}

@Composable
fun PremiumFishMascot(modifier: Modifier = Modifier) {
    BannerFishMascot(mood = BannerFishMood.Idle, modifier = modifier)
}

private enum class BannerFishMood {
    Idle,
    Busy,
    Success,
    Error,
}

private fun rememberBannerFishMood(
    text: String,
    isError: Boolean,
): BannerFishMood {
    val normalized = text.lowercase()
    return when {
        isError || normalized.contains("no se pudo") || normalized.contains("sin conexion") || normalized.contains("sin conexión") ->
            BannerFishMood.Error
        normalized.contains("guardando") ||
            normalized.contains("sincronizando") ||
            normalized.contains("cargando") ||
            normalized.contains("actualizando") ||
            normalized.contains("descargando") ||
            normalized.contains("reseteando") ->
            BannerFishMood.Busy
        normalized.contains("guardado") ||
            normalized.contains("guardada") ||
            normalized.contains("permitida") ||
            normalized.contains("bloqueada") ||
            normalized.contains("listo") ||
            normalized.contains("actualizado") ||
            normalized.contains("actualizada") ||
            normalized.contains("generado") ->
            BannerFishMood.Success
        else -> BannerFishMood.Idle
    }
}

@Composable
private fun BannerFishMascot(
    mood: BannerFishMood,
    modifier: Modifier = Modifier,
) {
    val loop = rememberInfiniteTransition(label = "banner-fish-loop")
    val floatY by loop.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 3600
                        0f at 0 using FastOutSlowInEasing
                        -3.5f at 800 using FastOutSlowInEasing
                        2.5f at 1700 using FastOutSlowInEasing
                        -1.5f at 2650 using FastOutSlowInEasing
                        0f at 3600 using FastOutSlowInEasing
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "banner-fish-float-y",
    )
    val driftX by loop.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 5200
                        0f at 0 using FastOutSlowInEasing
                        2.5f at 950 using FastOutSlowInEasing
                        -2f at 2100 using FastOutSlowInEasing
                        3f at 3550 using FastOutSlowInEasing
                        0f at 5200 using FastOutSlowInEasing
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "banner-fish-drift-x",
    )
    val knock by loop.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 6800
                        0f at 0 using LinearEasing
                        0f at 820 using LinearEasing
                        1f at 940 using FastOutSlowInEasing
                        -0.35f at 1080 using FastOutSlowInEasing
                        0f at 1240 using FastOutSlowInEasing
                        0f at 4100 using LinearEasing
                        0.85f at 4220 using FastOutSlowInEasing
                        -0.25f at 4380 using FastOutSlowInEasing
                        0f at 4560 using FastOutSlowInEasing
                        0f at 6800 using LinearEasing
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "banner-fish-knock",
    )
    val finSway by loop.animateFloat(
        initialValue = -18f,
        targetValue = 19f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    tween(
                        durationMillis = if (mood == BannerFishMood.Busy) 210 else 310,
                        easing = FastOutSlowInEasing,
                    ),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "banner-fish-fin",
    )
    val tailSway by loop.animateFloat(
        initialValue = -17f,
        targetValue = 17f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    tween(
                        durationMillis = if (mood == BannerFishMood.Busy) 190 else 280,
                        easing = FastOutSlowInEasing,
                    ),
                repeatMode = RepeatMode.Reverse,
            ),
        label = "banner-fish-tail",
    )
    val blink by loop.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec =
            infiniteRepeatable(
                animation =
                    keyframes {
                        durationMillis = 4300
                        0f at 0
                        0f at 1240
                        1f at 1300 using FastOutSlowInEasing
                        1f at 1400
                        0f at 1480 using FastOutSlowInEasing
                        0f at 3120
                        1f at 3180 using FastOutSlowInEasing
                        0f at 3280 using FastOutSlowInEasing
                        0f at 4300
                    },
                repeatMode = RepeatMode.Restart,
            ),
        label = "banner-fish-blink",
    )
    val bubblePulse by loop.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 1300, easing = LinearEasing)),
        label = "banner-fish-bubbles",
    )
    val moodTilt =
        when (mood) {
            BannerFishMood.Busy -> -3f
            BannerFishMood.Success -> 4f
            BannerFishMood.Error -> -7f
            BannerFishMood.Idle -> 0f
        }
    val moodScale =
        when (mood) {
            BannerFishMood.Success -> 1.08f
            BannerFishMood.Error -> 0.98f
            else -> 1f
        }
    Canvas(
        modifier =
            modifier.graphicsLayer {
                translationX = driftX + knock * 5f
                translationY = floatY - knock * 1.5f
                rotationZ = moodTilt + knock * 7f
                scaleX = moodScale
                scaleY = moodScale
            },
    ) {
        drawBannerFish(
            tailSway = tailSway + knock * 8f,
            finSway = finSway + knock * 10f,
            blink = blink,
            bubblePulse = bubblePulse,
            mood = mood,
        )
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBannerFish(
    tailSway: Float,
    finSway: Float,
    blink: Float,
    bubblePulse: Float,
    mood: BannerFishMood,
) {
    val w = size.width
    val h = size.height
    fun sx(value: Float) = w * value / 1024f
    fun sy(value: Float) = h * value / 1024f
    fun point(x: Float, y: Float) = Offset(sx(x), sy(y))
    fun ovalSize(rx: Float, ry: Float) = Size(sx(rx * 2f), sy(ry * 2f))

    val finBrush =
        Brush.linearGradient(
            colors = listOf(Color(0xFF47DADB), Color(0xFF169AAE)),
            start = Offset.Zero,
            end = Offset(w, h),
        )
    val bodyBrush =
        Brush.linearGradient(
            colors = listOf(Color(0xFFE2FFF7), Color(0xFFB8F7E8), Color(0xFF7DE7D8), Color(0xFF46C8C8)),
            start = point(250f, 230f),
            end = point(730f, 760f),
        )
    val eyeBrush =
        Brush.linearGradient(
            colors = listOf(Color(0xFF1C3657), Color(0xFF0C1E35)),
            start = point(0f, 420f),
            end = point(0f, 560f),
        )
    val finShadow = Color(0xFF0C8194)
    val bodyColor = Color(0xFF8FEBDD)
    val ink = Color(0xFF071329)

    if (mood == BannerFishMood.Busy || mood == BannerFishMood.Success) {
        val alpha = if (mood == BannerFishMood.Busy) 0.42f else 0.30f
        drawCircle(
            color = Color.White.copy(alpha = alpha * (1f - bubblePulse)),
            radius = sx(36f + bubblePulse * 28f),
            center = point(132f, 315f - bubblePulse * 96f),
            style = Stroke(width = sx(10f)),
        )
        drawCircle(
            color = Color.White.copy(alpha = alpha * (1f - bubblePulse)),
            radius = sx(20f + bubblePulse * 18f),
            center = point(95f, 455f - bubblePulse * 120f),
            style = Stroke(width = sx(8f)),
        )
    }

    rotate(tailSway, pivot = point(778f, 516f)) {
        val tail = Path().apply {
            moveTo(sx(760f), sy(514f))
            cubicTo(sx(826f), sy(384f), sx(972f), sy(386f), sx(970f), sy(488f))
            cubicTo(sx(969f), sy(533f), sx(928f), sy(551f), sx(880f), sy(558f))
            cubicTo(sx(925f), sy(577f), sx(970f), sy(640f), sx(927f), sy(700f))
            cubicTo(sx(873f), sy(776f), sx(792f), sy(668f), sx(760f), sy(514f))
            close()
        }
        drawPath(path = tail, brush = finBrush)
        drawPath(
            color = finShadow,
            path =
                Path().apply {
                    moveTo(sx(790f), sy(512f))
                    cubicTo(sx(838f), sy(503f), sx(890f), sy(470f), sx(930f), sy(425f))
                },
            alpha = 0.34f,
            style = Stroke(width = sx(14f), cap = StrokeCap.Round),
        )
        drawPath(
            color = finShadow,
            path =
                Path().apply {
                    moveTo(sx(794f), sy(540f))
                    cubicTo(sx(850f), sy(566f), sx(892f), sy(604f), sx(915f), sy(656f))
                },
            alpha = 0.24f,
            style = Stroke(width = sx(14f), cap = StrokeCap.Round),
        )
    }

    rotate(-finSway * 0.34f, pivot = point(548f, 286f)) {
        val topFin = Path().apply {
            moveTo(sx(426f), sy(318f))
            cubicTo(sx(468f), sy(170f), sx(650f), sy(127f), sx(744f), sy(210f))
            cubicTo(sx(798f), sy(258f), sx(811f), sy(347f), sx(768f), sy(392f))
            cubicTo(sx(672f), sy(339f), sx(544f), sy(307f), sx(426f), sy(318f))
            close()
        }
        drawPath(path = topFin, brush = finBrush)
        drawPath(
            color = finShadow,
            path =
                Path().apply {
                    moveTo(sx(516f), sy(276f))
                    cubicTo(sx(548f), sy(220f), sx(598f), sy(186f), sx(654f), sy(170f))
                },
            alpha = 0.32f,
            style = Stroke(width = sx(14f), cap = StrokeCap.Round),
        )
    }

    rotate(-10f - finSway * 0.82f, pivot = point(543f, 760f)) {
        val bottomFin = Path().apply {
            moveTo(sx(486f), sy(748f))
            cubicTo(sx(552f), sy(766f), sx(607f), sy(817f), sx(590f), sy(862f))
            cubicTo(sx(570f), sy(915f), sx(467f), sy(871f), sx(450f), sy(791f))
            cubicTo(sx(445f), sy(761f), sx(459f), sy(748f), sx(486f), sy(748f))
            close()
        }
        drawPath(path = bottomFin, brush = finBrush)
    }

    rotate(-finSway * 0.95f, pivot = point(170f, 575f)) {
        val leftFin = Path().apply {
            moveTo(sx(157f), sy(563f))
            cubicTo(sx(77f), sy(557f), sx(55f), sy(621f), sx(104f), sy(661f))
            cubicTo(sx(155f), sy(701f), sx(224f), sy(651f), sx(235f), sy(594f))
            cubicTo(sx(241f), sy(561f), sx(201f), sy(552f), sx(157f), sy(563f))
            close()
        }
        drawPath(path = leftFin, brush = finBrush)
        drawPath(
            color = finShadow,
            path =
                Path().apply {
                    moveTo(sx(120f), sy(608f))
                    cubicTo(sx(157f), sy(612f), sx(196f), sy(600f), sx(226f), sy(579f))
                },
            alpha = 0.27f,
            style = Stroke(width = sx(11f), cap = StrokeCap.Round),
        )
    }

    val body = Path().apply {
        moveTo(sx(150f), sy(536f))
        cubicTo(sx(150f), sy(344f), sx(301f), sy(248f), sx(516f), sy(260f))
        cubicTo(sx(701f), sy(271f), sx(839f), sy(374f), sx(853f), sy(538f))
        cubicTo(sx(870f), sy(724f), sx(718f), sy(817f), sx(487f), sy(804f))
        cubicTo(sx(275f), sy(792f), sx(150f), sy(710f), sx(150f), sy(536f))
        close()
    }
    drawPath(path = body, brush = bodyBrush)
    drawPath(
        color = Color(0xFF38BEB8),
        alpha = 0.18f,
        path =
            Path().apply {
                moveTo(sx(196f), sy(631f))
                cubicTo(sx(268f), sy(759f), sx(495f), sy(813f), sx(683f), sy(758f))
                cubicTo(sx(557f), sy(844f), sx(253f), sy(813f), sx(174f), sy(647f))
                close()
            },
    )
    drawPath(
        color = Color.White.copy(alpha = 0.28f),
        path =
            Path().apply {
                moveTo(sx(235f), sy(407f))
                cubicTo(sx(288f), sy(351f), sx(370f), sy(316f), sx(452f), sy(307f))
            },
        style = Stroke(width = sx(42f), cap = StrokeCap.Round),
    )
    drawOval(color = Color.White.copy(alpha = 0.10f), topLeft = point(759f, 447f), size = ovalSize(20f, 14f))

    drawCircle(color = Color(0xFF4ACBBF).copy(alpha = 0.46f), radius = sx(24f), center = point(632f, 361f))
    drawCircle(color = Color(0xFF4ACBBF).copy(alpha = 0.36f), radius = sx(20f), center = point(698f, 382f))
    drawCircle(color = Color(0xFF4ACBBF).copy(alpha = 0.28f), radius = sx(15f), center = point(773f, 532f))
    drawOval(color = Color(0xFF6EDDD0).copy(alpha = 0.46f), topLeft = point(235f, 576f), size = ovalSize(41f, 24f))
    drawOval(color = Color(0xFF6EDDD0).copy(alpha = 0.42f), topLeft = point(538f, 578f), size = ovalSize(42f, 25f))

    rotate(finSway * 1.1f, pivot = point(651f, 592f)) {
        val rightFin = Path().apply {
            moveTo(sx(630f), sy(578f))
            cubicTo(sx(717f), sy(537f), sx(798f), sy(573f), sx(793f), sy(635f))
            cubicTo(sx(788f), sy(700f), sx(685f), sy(713f), sx(626f), sy(655f))
            cubicTo(sx(593f), sy(621f), sx(596f), sy(594f), sx(630f), sy(578f))
            close()
        }
        drawPath(path = rightFin, brush = finBrush)
        drawPath(
            color = finShadow,
            path =
                Path().apply {
                    moveTo(sx(644f), sy(606f))
                    cubicTo(sx(696f), sy(603f), sx(744f), sy(621f), sx(779f), sy(648f))
                },
            alpha = 0.27f,
            style = Stroke(width = sx(12f), cap = StrokeCap.Round),
        )
    }

    val eyeOffset =
        when (mood) {
            BannerFishMood.Busy -> Offset(sx(10f), sy(-4f))
            BannerFishMood.Error -> Offset(sx(-10f), sy(-2f))
            BannerFishMood.Success -> Offset(sx(4f), sy(-8f))
            BannerFishMood.Idle -> Offset.Zero
        }
    val leftEye = point(310f, 486f)
    val rightEye = point(500f, 486f)
    drawOval(color = Color(0xFFF9FFFF), topLeft = point(240f, 402f), size = ovalSize(70f, 84f))
    drawOval(color = Color(0xFFF9FFFF), topLeft = point(430f, 402f), size = ovalSize(70f, 84f))
    drawOval(brush = eyeBrush, topLeft = point(261f, 430f), size = ovalSize(51f, 62f))
    drawOval(brush = eyeBrush, topLeft = point(451f, 430f), size = ovalSize(51f, 62f))
    drawOval(color = Color(0xFF091528), topLeft = point(291f, 480f) + eyeOffset, size = ovalSize(22f, 27f))
    drawOval(color = Color(0xFF091528), topLeft = point(481f, 480f) + eyeOffset, size = ovalSize(22f, 27f))
    drawCircle(color = Color.White, radius = sx(16f), center = point(291f, 462f) + eyeOffset)
    drawCircle(color = Color.White.copy(alpha = 0.65f), radius = sx(7f), center = point(323f, 483f) + eyeOffset)
    drawCircle(color = Color.White, radius = sx(16f), center = point(481f, 462f) + eyeOffset)
    drawCircle(color = Color.White.copy(alpha = 0.65f), radius = sx(7f), center = point(513f, 483f) + eyeOffset)

    if (blink > 0.02f || mood == BannerFishMood.Error) {
        val effectiveBlink = if (mood == BannerFishMood.Error) blink.coerceAtLeast(0.35f) else blink
        drawPath(
            color = bodyColor.copy(alpha = effectiveBlink),
            path =
                Path().apply {
                    moveTo(sx(240f), sy(486f))
                    cubicTo(sx(250f), sy(422f), sx(281f), sy(390f), sx(310f), sy(390f))
                    cubicTo(sx(345f), sy(390f), sx(373f), sy(419f), sx(380f), sy(486f))
                    cubicTo(sx(350f), sy(450f), sx(272f), sy(450f), sx(240f), sy(486f))
                    close()
                },
        )
        drawPath(
            color = bodyColor.copy(alpha = effectiveBlink),
            path =
                Path().apply {
                    moveTo(sx(430f), sy(486f))
                    cubicTo(sx(440f), sy(422f), sx(471f), sy(390f), sx(500f), sy(390f))
                    cubicTo(sx(535f), sy(390f), sx(563f), sy(419f), sx(570f), sy(486f))
                    cubicTo(sx(540f), sy(450f), sx(462f), sy(450f), sx(430f), sy(486f))
                    close()
                },
        )
    }

    val browLift = if (mood == BannerFishMood.Error) -18f else 0f
    drawPath(
        color = Color(0xFF129097),
        path =
            Path().apply {
                moveTo(sx(255f), sy(392f + browLift))
                cubicTo(sx(278f), sy(373f + browLift), sx(311f), sy(371f + browLift), sx(335f), sy(390f + browLift))
            },
        style = Stroke(width = sx(17f), cap = StrokeCap.Round),
    )
    drawPath(
        color = Color(0xFF129097),
        path =
            Path().apply {
                moveTo(sx(445f), sy(389f + browLift))
                cubicTo(sx(468f), sy(371f + browLift), sx(501f), sy(373f + browLift), sx(524f), sy(392f + browLift))
            },
        style = Stroke(width = sx(17f), cap = StrokeCap.Round),
    )
    val mouthHeight = if (mood == BannerFishMood.Success) 98f else 82f
    drawArc(
        color = ink,
        startAngle = 12f,
        sweepAngle = if (mood == BannerFishMood.Error) 118f else 150f,
        useCenter = false,
        topLeft = point(352f, if (mood == BannerFishMood.Error) 570f else 560f),
        size = Size(sx(108f), sy(mouthHeight)),
        style = Stroke(width = sx(16f), cap = StrokeCap.Round),
    )
}
