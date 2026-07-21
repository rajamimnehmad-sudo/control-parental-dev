package com.contentfilter.user

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

@Composable
internal fun UserSystemBars(darkHeader: Boolean) {
    val activity = LocalContext.current.findComponentActivity() ?: return
    LaunchedEffect(activity, darkHeader) {
        val statusStyle =
            if (darkHeader) {
                SystemBarStyle.dark(UserHomeHeaderTop.toArgb())
            } else {
                SystemBarStyle.light(Color.White.toArgb(), Color.White.toArgb())
            }
        activity.enableEdgeToEdge(
            statusBarStyle = statusStyle,
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
