package com.contentfilter.core.domain.chrome

/** DEV-only coordination keys shared by the Chrome HTTPS data-plane spike. */
object ChromePhotosDataPlaneLabContract {
    const val PreferencesName = "chrome_photos_data_plane_lab"
    const val KeyActive = "active"
    const val KeyPresentationReady = "presentation_ready"
    const val KeySessionId = "session_id"
    const val KeyProxyHealthy = "proxy_healthy"
    const val KeyPolicyConfirmed = "policy_confirmed"
    const val KeyVpnConfirmed = "vpn_confirmed"
    const val KeyVpnSessionId = "vpn_session_id"
    const val KeyFixtureConfirmed = "fixture_confirmed"
    const val KeyQuicAttempts = "quic_attempts"
    const val KeyDirectTcpAttempts = "direct_tcp_attempts"
    const val KeyLastFailure = "last_failure"
    const val KeyInstalledCaDer = "installed_ca_der"
    const val KeyCaFingerprint = "ca_fingerprint"

    const val FixtureHost = "glosh-photos.test"
    const val FixtureUrl = "https://glosh-photos.test/"
    const val FixtureIpv4 = "198.18.0.1"
    const val ProxyHost = "127.0.0.1"
    const val ProxyPort = 8877

    const val ChromePackage = "com.android.chrome"
}
