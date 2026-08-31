package com.contentfilter.feature.accessibility.chromevisual

import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentChallenge
import com.contentfilter.core.domain.chrome.ChromeMediaShieldActiveDocumentRequest
import com.contentfilter.core.domain.chrome.ChromeMediaShieldReadyClaim

/** Mutable state owned exclusively by the main-thread active-document coordinator. */
internal data class ChromeMediaShieldActiveDocumentAttempt(
    val sequence: Long,
    val claim: ChromeMediaShieldReadyClaim,
    val binding: ChromeMediaShieldActiveDocumentNativeBinding,
    var pendingCompletion: ChromeMediaShieldActiveDocumentGuardedCompletion?,
    var stage: ChromeMediaShieldActiveDocumentAttemptStage = ChromeMediaShieldActiveDocumentAttemptStage.HelloAccepted,
    var surface: ChromePhotosProtectedSurfaceSnapshot? = null,
    var challenge: ChromeMediaShieldActiveDocumentChallenge? = null,
    var pendingPresentRequest: ChromeMediaShieldActiveDocumentRequest.Present? = null,
    var lease: ChromePhotosDataPlaneLease? = null,
    val holdTimeoutToken: Any = Any(),
)
