package com.contentfilter.user.chromedataplane

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.contentfilter.feature.accessibility.chromevisual.ChromeVisualShieldLabControl

class ChromeVisualShieldLabReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (!context.packageName.endsWith(".dev")) return
        val result =
            when (intent.action) {
                ActionStart -> ChromeVisualShieldLabControl.start()
                ActionStop ->
                    ChromeVisualShieldLabControl.stop().also {
                        ChromeVisualShieldFixtureSampleStore.clear()
                    }
                ActionRelease ->
                    ChromeVisualShieldLabControl.release().also {
                        ChromeVisualShieldFixtureSampleStore.clear()
                    }
                ActionStatus -> ChromeVisualShieldLabControl.status()
                ActionInjectStale -> ChromeVisualShieldLabControl.injectStale()
                ActionCancelStress -> ChromeVisualShieldLabControl.cancelStress()
                ActionArmAnalyzerFailure -> ChromeVisualShieldLabControl.armAnalyzerFailure()
                ActionFixtureReset -> fixtureReset(intent)
                ActionFixtureAppend -> fixtureAppend(intent)
                ActionFixtureCommit -> fixtureCommit(intent)
                else -> "result=unknown_action"
            }
        setResultData(result)
        Log.i(LogTag, "action=${intent.action?.substringAfterLast('.')} $result")
    }

    private fun fixtureReset(intent: Intent): String {
        val sample = fixtureSample(intent) ?: return "result=fixture_unknown_sample"
        return ChromeVisualShieldFixtureSampleStore.reset(sample)
    }

    private fun fixtureAppend(intent: Intent): String {
        val sample = fixtureSample(intent) ?: return "result=fixture_unknown_sample"
        val chunk = intent.getStringExtra(ExtraFixtureChunk) ?: return "result=fixture_missing_chunk"
        return ChromeVisualShieldFixtureSampleStore.append(sample, chunk)
    }

    private fun fixtureCommit(intent: Intent): String {
        val sample = fixtureSample(intent) ?: return "result=fixture_unknown_sample"
        return ChromeVisualShieldFixtureSampleStore.commit(sample)
    }

    private fun fixtureSample(intent: Intent): ChromeVisualShieldFixtureSample? =
        ChromeVisualShieldFixtureSample.fromWireName(intent.getStringExtra(ExtraFixtureSample))

    companion object {
        const val ActionStart = "com.contentfilter.user.chromevisualshield.command.START"
        const val ActionStop = "com.contentfilter.user.chromevisualshield.command.STOP"
        const val ActionRelease = "com.contentfilter.user.chromevisualshield.command.RELEASE"
        const val ActionStatus = "com.contentfilter.user.chromevisualshield.command.STATUS"
        const val ActionInjectStale =
            "com.contentfilter.user.chromevisualshield.command.INJECT_STALE"
        const val ActionCancelStress =
            "com.contentfilter.user.chromevisualshield.command.CANCEL_STRESS"
        const val ActionArmAnalyzerFailure =
            "com.contentfilter.user.chromevisualshield.command.ARM_ANALYZER_FAILURE"
        const val ActionFixtureReset =
            "com.contentfilter.user.chromevisualshield.command.FIXTURE_RESET"
        const val ActionFixtureAppend =
            "com.contentfilter.user.chromevisualshield.command.FIXTURE_APPEND"
        const val ActionFixtureCommit =
            "com.contentfilter.user.chromevisualshield.command.FIXTURE_COMMIT"
        const val ExtraFixtureSample = "fixture_sample"
        const val ExtraFixtureChunk = "fixture_chunk_base64"
        private const val LogTag = "GloshVisualShieldLab"
    }
}
