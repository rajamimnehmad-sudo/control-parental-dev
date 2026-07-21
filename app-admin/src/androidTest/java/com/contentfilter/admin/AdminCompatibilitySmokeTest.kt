package com.contentfilter.admin

import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AdminCompatibilitySmokeTest {
    @Test
    fun mainActivity_launchesAndRecreatesWithoutCrash() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.recreate()
            scenario.moveToState(Lifecycle.State.RESUMED)
        }
    }

    @Test
    fun testDevice_capabilitiesAreCapturedWithoutPersonalIdentifiers() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metrics = context.resources.displayMetrics
        val summary =
            listOf(
                "manufacturer=${Build.MANUFACTURER}",
                "brand=${Build.BRAND}",
                "model=${Build.MODEL}",
                "device=${Build.DEVICE}",
                "api=${Build.VERSION.SDK_INT}",
                "abis=${Build.SUPPORTED_ABIS.joinToString()}",
                "display=${metrics.widthPixels}x${metrics.heightPixels}@${metrics.densityDpi}",
                "fontScale=${context.resources.configuration.fontScale}",
                "locale=${context.resources.configuration.locales[0]}",
            ).joinToString(separator = ";")

        Log.i("CompatibilitySmoke", summary)
        assertTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        assertTrue(metrics.widthPixels > 0 && metrics.heightPixels > 0)
    }

    @Test
    fun mainActivity_launchesWithLargeFontWithoutCrash() {
        try {
            runShell("settings put system font_scale 1.3")
            assertEquals("1.3", runShell("settings get system font_scale"))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.moveToState(Lifecycle.State.RESUMED)
            }
        } finally {
            runShell("settings put system font_scale 1.0")
        }
    }

    private fun runShell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText().trim() }
}
