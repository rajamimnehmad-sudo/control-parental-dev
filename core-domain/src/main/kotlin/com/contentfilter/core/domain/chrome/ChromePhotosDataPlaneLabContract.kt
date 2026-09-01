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
    const val KeyRealWebScopeConfirmed = "real_web_scope_confirmed"
    const val KeyQuicAttempts = "quic_attempts"
    const val KeyDirectTcpAttempts = "direct_tcp_attempts"
    const val KeyLastFailure = "last_failure"
    const val KeyResolvedRouteAddresses = "resolved_route_addresses"
    const val KeyInstalledCaDer = "installed_ca_der"
    const val KeyCaFingerprint = "ca_fingerprint"
    const val KeyTrustedBootstrapResetGeneration = "trusted_bootstrap_reset_generation"
    const val KeyTrustedBootstrapCompleteGeneration = "trusted_bootstrap_complete_generation"
    const val KeyTrustedBootstrapResetCount = "trusted_bootstrap_reset_count"
    const val KeyUdpFixtureGateEnabled = "udp_fixture_gate_enabled"
    const val KeyUdpFixtureAddress = "udp_fixture_address"
    const val KeyUdpFixturePort = "udp_fixture_port"
    const val KeyUdpFixtureMalformedProbeEnabled = "udp_fixture_malformed_probe_enabled"
    const val KeyStockMediaAuthorityEnabled = "stock_media_authority_enabled"
    const val KeyRequestedStockMediaAuthorityEnabled = "requested_stock_media_authority_enabled"
    const val KeyRequestedFullTunnelDevGateEnabled = "requested_full_tunnel_dev_gate_enabled"
    const val KeyRequestedReplaceAllNetworkVisuals = "requested_replace_all_network_visuals"
    const val KeyDocumentSelfShieldEnabled = "document_self_shield_enabled"
    const val KeyRequestedDocumentSelfShieldEnabled = "requested_document_self_shield_enabled"

    const val ActionActiveDocumentReplay =
        "com.contentfilter.user.chromedataplane.command.ACTIVE_DOCUMENT_REPLAY"

    const val TrustedBootstrapGeneration = 2
    const val StockMediaPolicyEpoch = 19L
    const val DocumentSelfShieldPolicyEpoch = 20L

    const val FixtureHost = "glosh-photos.test"
    const val FixtureUrl = "https://glosh-photos.test/"
    const val MediaShieldReadyPath = "/.well-known/glosh-h19-ready"
    const val MediaShieldReadyUrl = "https://glosh-photos.test/.well-known/glosh-h19-ready"
    const val MediaShieldSelfReadyPath = "/.well-known/glosh-h20-self-ready"
    const val MediaShieldSelfReadyUrl = "https://glosh-photos.test/.well-known/glosh-h20-self-ready"
    const val MediaShieldSelfShieldTracePath = "/.well-known/glosh-h20-self-shield-trace"
    const val MediaShieldSelfShieldTraceUrl =
        "https://glosh-photos.test/.well-known/glosh-h20-self-shield-trace"
    const val MediaShieldBootstrapDiagnosticPath = "/.well-known/glosh-h20-bootstrap-diagnostic"
    const val MediaShieldBootstrapDiagnosticUrl =
        "https://glosh-photos.test/.well-known/glosh-h20-bootstrap-diagnostic"
    const val MediaShieldRendererMetricsPath = "/.well-known/glosh-h20-renderer-metrics"
    const val MediaShieldRendererMetricsUrl =
        "https://glosh-photos.test/.well-known/glosh-h20-renderer-metrics"
    const val MediaShieldParserBarrierPath = "/.well-known/glosh-h19-parser-barrier.js"
    const val MediaShieldParserBarrierUrl =
        "https://glosh-photos.test/.well-known/glosh-h19-parser-barrier.js"
    const val FixtureIpv4 = "198.18.0.1"
    const val ProxyHost = "127.0.0.1"
    const val ProxyPort = 8877

    const val ChromePackage = "com.android.chrome"
    const val UdpFixturePackage = "com.glosh.vpnudpfixture"
}
