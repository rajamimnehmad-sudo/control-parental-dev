package com.contentfilter.core.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Immutable
object GloshColors {
    val Bone = Color(0xFFF7F6F2)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceMuted = Color(0xFFF0EFEA)
    val Graphite = Color(0xFF171A18)
    val GraphiteSoft = Color(0xFF343832)
    val Muted = Color(0xFF747970)
    val Line = Color(0xFFE5E3DC)

    val Lime = Color(0xFFC8F31D)
    val LimeSoft = Color(0xFFF0F8C8)

    val Positive = Color(0xFF247A4B)
    val PositiveSoft = Color(0xFFE5F4EA)
    val Warning = Color(0xFF946900)
    val WarningSoft = Color(0xFFFFF3D8)
    val Danger = Color(0xFFB42318)
    val DangerSoft = Color(0xFFFFE9E6)
}

@Immutable
object GloshSpacing {
    val PageHorizontal = 20.dp
    val PageVertical = 18.dp
    val Section = 18.dp
    val Card = 16.dp
    val Compact = 10.dp
}

@Immutable
object GloshShapes {
    val Small = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(18.dp)
    val LargeCard = RoundedCornerShape(22.dp)
    val Pill = RoundedCornerShape(999.dp)
}
