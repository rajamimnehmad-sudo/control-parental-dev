package com.contentfilter.user.chromedataplane

import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract

internal enum class ChromePhotosDataPlanePhase {
    Stopped,
    Starting,
    ProxyReady,
    PresentationReady,
    Failed,
}

internal class ChromePhotosDataPlaneLifecycle {
    private var phase = ChromePhotosDataPlanePhase.Stopped

    @Synchronized
    fun begin(): ChromePhotosDataPlanePhase {
        check(phase == ChromePhotosDataPlanePhase.Stopped || phase == ChromePhotosDataPlanePhase.Failed)
        phase = ChromePhotosDataPlanePhase.Starting
        return phase
    }

    @Synchronized
    fun proxyReady(): ChromePhotosDataPlanePhase {
        check(phase == ChromePhotosDataPlanePhase.Starting)
        phase = ChromePhotosDataPlanePhase.ProxyReady
        return phase
    }

    @Synchronized
    fun presentationReady(): ChromePhotosDataPlanePhase {
        check(phase == ChromePhotosDataPlanePhase.ProxyReady || phase == ChromePhotosDataPlanePhase.PresentationReady)
        phase = ChromePhotosDataPlanePhase.PresentationReady
        return phase
    }

    @Synchronized
    fun fail(): ChromePhotosDataPlanePhase {
        phase = ChromePhotosDataPlanePhase.Failed
        return phase
    }

    @Synchronized
    fun stop(): ChromePhotosDataPlanePhase {
        phase = ChromePhotosDataPlanePhase.Stopped
        return phase
    }

    @Synchronized
    fun current(): ChromePhotosDataPlanePhase = phase
}

internal object ChromePhotosChromePolicy {
    fun proxySettingsJson(): String =
        """{"ProxyMode":"fixed_servers","ProxyServer":"${ChromePhotosDataPlaneLabContract.ProxyHost}:${ChromePhotosDataPlaneLabContract.ProxyPort}","ProxyBypassList":"<-loopback>"}"""
}
