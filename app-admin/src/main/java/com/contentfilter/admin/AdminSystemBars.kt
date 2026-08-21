package com.contentfilter.admin

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.contentfilter.core.ui.GloshColors

@Composable
internal fun AdminSystemBars(darkHeader: Boolean) {
    val activity = LocalContext.current.findComponentActivity() ?: return
    LaunchedEffect(activity, darkHeader) {
        val statusColor = if (darkHeader) GloshColors.Bone else Color.White
        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(statusColor.toArgb(), statusColor.toArgb()),
            navigationBarStyle = SystemBarStyle.light(Color.White.toArgb(), Color.White.toArgb()),
        )
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }

internal val AdminHomeStatusBarColor = GloshColors.Bone
