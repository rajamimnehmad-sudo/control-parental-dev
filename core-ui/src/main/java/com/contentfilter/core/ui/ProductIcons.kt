package com.contentfilter.core.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

enum class ProductIcon {
    Back,
    ChevronRight,
    Home,
    People,
    Settings,
    UserPlus,
    Search,
    Refresh,
    Bell,
    ShieldCheck,
    ShieldAlert,
    Panel,
    Update,
    Web,
    Apps,
    Requests,
}

@Composable
fun ProductGlyph(
    icon: ProductIcon,
    color: Color,
    modifier: Modifier = Modifier,
) {
    icon.materialVector()?.let { vector ->
        Icon(imageVector = vector, contentDescription = null, tint = color, modifier = modifier)
        return
    }
    Canvas(modifier = modifier) {
        val stroke = 2.2.dp.toPx()
        when (icon) {
            ProductIcon.Apps -> {
                val cellSize = size.minDimension * 0.22f
                val gap = size.minDimension * 0.12f
                val startX = (size.width - cellSize * 2f - gap) / 2f
                val startY = (size.height - cellSize * 2f - gap) / 2f
                repeat(2) { row ->
                    repeat(2) { column ->
                        drawRoundRect(
                            color = color,
                            topLeft = Offset(startX + column * (cellSize + gap), startY + row * (cellSize + gap)),
                            size = Size(cellSize, cellSize),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                            style = Stroke(width = stroke),
                        )
                    }
                }
            }
            ProductIcon.Requests -> {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(size.width * 0.22f, size.height * 0.18f),
                    size = Size(size.width * 0.56f, size.height * 0.66f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    style = Stroke(width = stroke),
                )
                listOf(0.40f, 0.58f).forEach { y ->
                    drawCircle(color, 1.4.dp.toPx(), Offset(size.width * 0.34f, size.height * y))
                    drawLine(
                        color,
                        Offset(size.width * 0.43f, size.height * y),
                        Offset(size.width * 0.67f, size.height * y),
                        stroke,
                        cap = StrokeCap.Round,
                    )
                }
            }
            ProductIcon.Web -> {
                val center = Offset(size.width * 0.50f, size.height * 0.50f)
                val radius = size.minDimension * 0.34f
                drawCircle(color, radius, center, style = Stroke(width = stroke))
                drawLine(
                    color,
                    Offset(center.x - radius, center.y),
                    Offset(center.x + radius, center.y),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(center.x, center.y - radius),
                    Offset(center.x, center.y + radius),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawArc(
                    color,
                    90f,
                    180f,
                    false,
                    Offset(center.x - radius * 0.45f, center.y - radius),
                    Size(radius * 0.90f, radius * 2f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawArc(
                    color,
                    -90f,
                    180f,
                    false,
                    Offset(center.x - radius * 0.45f, center.y - radius),
                    Size(radius * 0.90f, radius * 2f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            ProductIcon.Back -> {
                drawLine(
                    color,
                    Offset(size.width * 0.64f, size.height * 0.20f),
                    Offset(size.width * 0.34f, size.height * 0.50f),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.34f, size.height * 0.50f),
                    Offset(size.width * 0.64f, size.height * 0.80f),
                    stroke,
                    cap = StrokeCap.Round,
                )
            }
            ProductIcon.ChevronRight -> {
                drawLine(
                    color,
                    Offset(size.width * 0.38f, size.height * 0.22f),
                    Offset(size.width * 0.64f, size.height * 0.50f),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.64f, size.height * 0.50f),
                    Offset(size.width * 0.38f, size.height * 0.78f),
                    stroke,
                    cap = StrokeCap.Round,
                )
            }
            ProductIcon.Home -> {
                val roofTop = Offset(size.width * 0.50f, size.height * 0.16f)
                drawLine(color, roofTop, Offset(size.width * 0.18f, size.height * 0.44f), stroke, cap = StrokeCap.Round)
                drawLine(color, roofTop, Offset(size.width * 0.82f, size.height * 0.44f), stroke, cap = StrokeCap.Round)
                drawLine(
                    color,
                    Offset(size.width * 0.26f, size.height * 0.42f),
                    Offset(size.width * 0.26f, size.height * 0.82f),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.74f, size.height * 0.42f),
                    Offset(size.width * 0.74f, size.height * 0.82f),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.26f, size.height * 0.82f),
                    Offset(size.width * 0.74f, size.height * 0.82f),
                    stroke,
                    cap = StrokeCap.Round,
                )
            }
            ProductIcon.People -> {
                drawCircle(
                    color,
                    4.dp.toPx(),
                    Offset(size.width * 0.42f, size.height * 0.34f),
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawArc(
                    color,
                    200f,
                    140f,
                    false,
                    Offset(size.width * 0.20f, size.height * 0.54f),
                    Size(size.width * 0.46f, size.height * 0.30f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
                drawCircle(
                    color,
                    3.dp.toPx(),
                    Offset(size.width * 0.68f, size.height * 0.40f),
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawArc(
                    color,
                    215f,
                    100f,
                    false,
                    Offset(size.width * 0.56f, size.height * 0.58f),
                    Size(size.width * 0.30f, size.height * 0.22f),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            ProductIcon.Settings -> {
                val center = Offset(size.width / 2f, size.height / 2f)
                drawCircle(color, 5.2.dp.toPx(), center, style = Stroke(width = stroke))
                drawCircle(color, 1.8.dp.toPx(), center)
                listOf(0f, 60f, 120f, 180f, 240f, 300f).forEach { degrees ->
                    val radians = Math.toRadians(degrees.toDouble())
                    val start =
                        Offset(
                            center.x + kotlin.math.cos(radians).toFloat() * 7.dp.toPx(),
                            center.y + kotlin.math.sin(radians).toFloat() * 7.dp.toPx(),
                        )
                    val end =
                        Offset(
                            center.x + kotlin.math.cos(radians).toFloat() * 9.dp.toPx(),
                            center.y + kotlin.math.sin(radians).toFloat() * 9.dp.toPx(),
                        )
                    drawLine(color, start, end, stroke, cap = StrokeCap.Round)
                }
            }
            ProductIcon.UserPlus -> {
                drawCircle(
                    color,
                    5.2.dp.toPx(),
                    Offset(size.width * 0.38f, size.height * 0.32f),
                    style = Stroke(width = stroke),
                )
                drawArc(
                    color,
                    205f,
                    130f,
                    false,
                    Offset(size.width * 0.14f, size.height * 0.50f),
                    Size(size.width * 0.48f, size.height * 0.34f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                val plusX = size.width * 0.75f
                val plusY = size.height * 0.37f
                drawLine(
                    color,
                    Offset(plusX - 5.dp.toPx(), plusY),
                    Offset(plusX + 5.dp.toPx(), plusY),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(plusX, plusY - 5.dp.toPx()),
                    Offset(plusX, plusY + 5.dp.toPx()),
                    stroke,
                    cap = StrokeCap.Round,
                )
            }
            ProductIcon.Search -> {
                drawCircle(
                    color,
                    6.dp.toPx(),
                    Offset(size.width * 0.42f, size.height * 0.42f),
                    style = Stroke(width = 2.dp.toPx()),
                )
                drawLine(
                    color,
                    Offset(size.width * 0.67f, size.height * 0.67f),
                    Offset(size.width * 0.88f, size.height * 0.88f),
                    2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            ProductIcon.Refresh -> {
                drawArc(
                    color,
                    35f,
                    280f,
                    false,
                    Offset(3.dp.toPx(), 3.dp.toPx()),
                    Size(size.width - 6.dp.toPx(), size.height - 6.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                )
                drawLine(
                    color,
                    Offset(size.width * 0.76f, size.height * 0.20f),
                    Offset(size.width * 0.90f, size.height * 0.22f),
                    2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.76f, size.height * 0.20f),
                    Offset(size.width * 0.80f, size.height * 0.34f),
                    2.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
            ProductIcon.Bell -> {
                val bell =
                    Path().apply {
                        moveTo(size.width * 0.24f, size.height * 0.69f)
                        cubicTo(
                            size.width * 0.34f,
                            size.height * 0.58f,
                            size.width * 0.29f,
                            size.height * 0.42f,
                            size.width * 0.36f,
                            size.height * 0.30f,
                        )
                        cubicTo(
                            size.width * 0.42f,
                            size.height * 0.20f,
                            size.width * 0.58f,
                            size.height * 0.20f,
                            size.width * 0.64f,
                            size.height * 0.30f,
                        )
                        cubicTo(
                            size.width * 0.71f,
                            size.height * 0.42f,
                            size.width * 0.66f,
                            size.height * 0.58f,
                            size.width * 0.76f,
                            size.height * 0.69f,
                        )
                        close()
                    }
                drawPath(path = bell, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
                drawArc(
                    color = color,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(size.width * 0.43f, size.height * 0.68f),
                    size = Size(size.width * 0.14f, size.height * 0.13f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
            ProductIcon.ShieldCheck,
            ProductIcon.ShieldAlert,
            -> {
                val shield =
                    Path().apply {
                        moveTo(size.width * 0.50f, size.height * 0.12f)
                        lineTo(size.width * 0.78f, size.height * 0.24f)
                        lineTo(size.width * 0.75f, size.height * 0.56f)
                        cubicTo(
                            size.width * 0.72f,
                            size.height * 0.73f,
                            size.width * 0.61f,
                            size.height * 0.84f,
                            size.width * 0.50f,
                            size.height * 0.90f,
                        )
                        cubicTo(
                            size.width * 0.39f,
                            size.height * 0.84f,
                            size.width * 0.28f,
                            size.height * 0.73f,
                            size.width * 0.25f,
                            size.height * 0.56f,
                        )
                        lineTo(size.width * 0.22f, size.height * 0.24f)
                        close()
                    }
                drawPath(path = shield, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
                if (icon == ProductIcon.ShieldCheck) {
                    drawLine(
                        color,
                        Offset(size.width * 0.36f, size.height * 0.50f),
                        Offset(size.width * 0.46f, size.height * 0.61f),
                        stroke,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color,
                        Offset(size.width * 0.46f, size.height * 0.61f),
                        Offset(size.width * 0.65f, size.height * 0.39f),
                        stroke,
                        cap = StrokeCap.Round,
                    )
                } else {
                    drawLine(
                        color,
                        Offset(size.width * 0.50f, size.height * 0.34f),
                        Offset(size.width * 0.50f, size.height * 0.57f),
                        stroke,
                        cap = StrokeCap.Round,
                    )
                    drawCircle(color, 1.5.dp.toPx(), Offset(size.width * 0.50f, size.height * 0.69f))
                }
            }
            ProductIcon.Panel -> {
                drawRoundRect(
                    color,
                    Offset(size.width * 0.20f, size.height * 0.22f),
                    Size(size.width * 0.60f, size.height * 0.56f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                    style = Stroke(width = stroke),
                )
                drawLine(
                    color,
                    Offset(size.width * 0.32f, size.height * 0.42f),
                    Offset(size.width * 0.68f, size.height * 0.42f),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.32f, size.height * 0.58f),
                    Offset(size.width * 0.56f, size.height * 0.58f),
                    stroke,
                    cap = StrokeCap.Round,
                )
            }
            ProductIcon.Update -> {
                drawArc(
                    color,
                    30f,
                    250f,
                    false,
                    Offset(size.width * 0.18f, size.height * 0.18f),
                    Size(size.width * 0.64f, size.height * 0.64f),
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
                drawLine(
                    color,
                    Offset(size.width * 0.70f, size.height * 0.20f),
                    Offset(size.width * 0.84f, size.height * 0.22f),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.70f, size.height * 0.20f),
                    Offset(size.width * 0.74f, size.height * 0.34f),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.50f, size.height * 0.36f),
                    Offset(size.width * 0.50f, size.height * 0.66f),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.38f, size.height * 0.54f),
                    Offset(size.width * 0.50f, size.height * 0.68f),
                    stroke,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color,
                    Offset(size.width * 0.62f, size.height * 0.54f),
                    Offset(size.width * 0.50f, size.height * 0.68f),
                    stroke,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

private fun ProductIcon.materialVector(): ImageVector? =
    when (this) {
        ProductIcon.Back -> Icons.AutoMirrored.Filled.ArrowBack
        ProductIcon.ChevronRight -> Icons.AutoMirrored.Filled.KeyboardArrowRight
        ProductIcon.Home -> Icons.Filled.Home
        ProductIcon.People -> Icons.Filled.Person
        ProductIcon.Settings -> Icons.Filled.Settings
        ProductIcon.UserPlus -> Icons.Filled.Add
        ProductIcon.Search -> Icons.Filled.Search
        ProductIcon.Refresh -> Icons.Filled.Refresh
        ProductIcon.Bell -> null
        ProductIcon.ShieldCheck -> null
        ProductIcon.ShieldAlert -> null
        ProductIcon.Panel -> Icons.Filled.List
        ProductIcon.Update -> Icons.Filled.Refresh
        ProductIcon.Web -> null
        ProductIcon.Apps -> null
        ProductIcon.Requests -> null
    }
