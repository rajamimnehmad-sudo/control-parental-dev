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
        if (!ChromeVisualShieldLabReceiverContract.accepts(context.packageName)) return
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
                ActionRenderProbe -> renderProbe(intent)
                ActionExactDrawOracleProbe -> exactDrawOracleProbe(intent)
                ActionRegionDiscoveryProbe -> regionDiscoveryProbe(intent)
                ActionRegionSetAuthorityProbe -> regionSetAuthorityProbe(intent)
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

    private fun renderProbe(intent: Intent): String {
        val sample = fixtureSample(intent) ?: return "result=fixture_unknown_sample"
        val renderIdentityToken =
            ChromeVisualShieldLabControl.currentRenderIdentityToken()
                ?: return "result=render_identity_unavailable sample=${sample.wireName}"
        val attestation =
            ChromeVisualShieldRenderAttestationStore.consume(sample, renderIdentityToken)
                ?: return "result=render_not_attested sample=${sample.wireName}"
        val result =
            ChromeVisualShieldLabControl.renderProbe(
                sampleId = sample.wireName,
                sourceSha256 = sample.expectedSha256,
                renderContract = ChromeVisualShieldContainContract.Version,
            )
        return "$result renderAttested=true source=${attestation.sourceWidth}x${attestation.sourceHeight} " +
            "canvas=${attestation.canvasWidth}x${attestation.canvasHeight} draw=${attestation.draw}"
    }

    private fun exactDrawOracleProbe(intent: Intent): String {
        val sample = fixtureSample(intent) ?: return "result=fixture_unknown_sample"
        if (!ChromeVisualShieldFixtureSampleStore.isReady(sample)) {
            return "result=fixture_not_ready sample=${sample.wireName}"
        }
        return ChromeVisualShieldLabControl.exactDrawOracleProbe(
            sampleId = sample.wireName,
            sourceSha256 = sample.expectedSha256,
            renderContract = ChromeVisualShieldContainContract.Version,
        )
    }

    private fun regionDiscoveryProbe(intent: Intent): String {
        val scenario =
            ChromeVisualShieldRegionDiscoveryScenario.fromWireName(
                intent.getStringExtra(ExtraDiscoveryScenario),
            ) ?: return "result=fixture_unknown_discovery_scenario"
        if (scenario.samples.any { !ChromeVisualShieldFixtureSampleStore.isReady(it) }) {
            return "result=fixture_not_ready scenario=${scenario.wireName}"
        }
        return ChromeVisualShieldLabControl.regionDiscoveryProbe(
            scenarioId = scenario.wireName,
            sourceSha256s = scenario.samples.map { it.expectedSha256 },
            renderContract = ChromeVisualShieldRegionDiscoveryLayoutContract.Version,
        )
    }

    private fun regionSetAuthorityProbe(intent: Intent): String {
        val scenario =
            ChromeVisualShieldRegionDiscoveryScenario.fromWireName(intent.getStringExtra(ExtraDiscoveryScenario))
                ?: return "result=unknown_region_set_authority_scenario"
        if (scenario.samples.any { !ChromeVisualShieldFixtureSampleStore.isReady(it) }) {
            return "result=fixture_not_ready scenario=${scenario.wireName}"
        }
        return ChromeVisualShieldLabControl.regionSetAuthorityProbe(
            scenarioId = scenario.wireName,
            sourceSha256s = scenario.samples.map { it.expectedSha256 },
            renderContract = ChromeVisualShieldRegionDiscoveryLayoutContract.Version,
        )
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
        const val ActionRenderProbe =
            "com.contentfilter.user.chromevisualshield.command.RENDER_PROBE"
        const val ActionExactDrawOracleProbe =
            "com.contentfilter.user.chromevisualshield.command.EXACT_DRAW_ORACLE_PROBE"
        const val ActionRegionDiscoveryProbe =
            "com.contentfilter.user.chromevisualshield.command.REGION_DISCOVERY_PROBE"
        const val ActionRegionSetAuthorityProbe =
            "com.contentfilter.user.chromevisualshield.command.REGION_SET_AUTHORITY_PROBE"
        const val ActionFixtureReset =
            "com.contentfilter.user.chromevisualshield.command.FIXTURE_RESET"
        const val ActionFixtureAppend =
            "com.contentfilter.user.chromevisualshield.command.FIXTURE_APPEND"
        const val ActionFixtureCommit =
            "com.contentfilter.user.chromevisualshield.command.FIXTURE_COMMIT"
        const val ExtraFixtureSample = "fixture_sample"
        const val ExtraFixtureChunk = "fixture_chunk_base64"
        const val ExtraDiscoveryScenario = "discovery_scenario"
        private const val LogTag = "GloshVisualShieldLab"
    }
}

internal object ChromeVisualShieldLabReceiverContract {
    const val RequiredManifestPermission = "android.permission.DUMP"

    fun accepts(packageName: String): Boolean = packageName.endsWith(".dev")
}
