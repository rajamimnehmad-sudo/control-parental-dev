package com.contentfilter.feature.accessibility.chromevisual

import com.glosh.visual.GloshiaVisualDecision
import com.glosh.visual.GloshiaVisualPolicyContract
import kotlinx.coroutines.CancellationException
import java.util.concurrent.atomic.AtomicBoolean

/** DEV-lab one-shot fault. Its only public caller is the DUMP-protected dev receiver. */
internal class ChromeVisualShieldAnalyzerFault {
    private val armed = AtomicBoolean(false)

    fun armOnce(): Boolean = armed.compareAndSet(false, true)

    fun consume(): Boolean = armed.compareAndSet(true, false)
}

/** Shared by production analyzer execution and the controlled failure gate. */
internal object ChromeVisualShieldAnalyzerExecution {
    fun decide(
        identity: ChromeVisualShieldIdentity,
        fault: ChromeVisualShieldAnalyzerFault,
        analyze: () -> GloshiaVisualDecision,
    ): ChromeVisualShieldGloshiaDecision =
        try {
            if (fault.consume()) throw ControlledAnalyzerFailure
            ChromeVisualShieldGloshiaDecisionPolicy.map(identity, analyze())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            ChromeVisualShieldGloshiaDecision.FailClosed(
                identity = identity,
                reason = GloshiaVisualPolicyContract.ModelExecutionFailedReason,
            )
        }

    private object ControlledAnalyzerFailure : IllegalStateException("controlled analyzer failure")
}
