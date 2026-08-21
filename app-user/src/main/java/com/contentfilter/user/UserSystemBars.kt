package com.contentfilter.user

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.contentfilter.core.ui.GloshColors

@Composable
internal fun UserSystemBars(darkHeader: Boolean) {
    val activity = LocalContext.current.findComponentActivity() ?: return
    LaunchedEffect(activity, darkHeader) {
        val light = SystemBarStyle.light(GloshColors.Bone.toArgb(), GloshColors.Bone.toArgb())
        activity.enableEdgeToEdge(
            statusBarStyle = light,
            navigationBarStyle = light,
        )
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? =
    when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findComponentActivity()
        else -> null
    }
