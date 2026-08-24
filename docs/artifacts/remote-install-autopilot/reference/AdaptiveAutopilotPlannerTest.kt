package reference.autopilot

private val SAFE = SnapshotAuthority(true, 2, true, true, true)
private fun c(key: String, confidence: Confidence = Confidence.HIGH, clickable: Boolean = true, unique: Boolean = true, margin: Boolean = true, fresh: Boolean = true) = Candidate(key, confidence, clickable, unique, margin, fresh)
private fun o(vararg x: Pair<String, Any?>): Observation {
    val m = x.toMap()
    @Suppress("UNCHECKED_CAST")
    return Observation(
        androidApi = m["androidApi"] as? Int ?: 34,
        oem = m["oem"] as? String ?: "Samsung",
        accessibilityEnabled = m["accessibilityEnabled"] as? Boolean ?: true,
        adbConnected = m["adbConnected"] as? Boolean ?: false,
        supportConnected = m["supportConnected"] as? Boolean ?: false,
        restrictedSettingsRequired = m["restrictedSettingsRequired"] as? Boolean ?: false,
        wifiReady = m["wifiReady"] as? Boolean ?: true,
        wirelessPolicyBlocked = m["wirelessPolicyBlocked"] as? Boolean ?: false,
        previousPairingKnown = m["previousPairingKnown"] as? Boolean ?: false,
        reconnectAttempted = m["reconnectAttempted"] as? Boolean ?: false,
        screen = m["screen"] as? Screen ?: Screen.UNKNOWN,
        authority = if (m.containsKey("authority")) m["authority"] as SnapshotAuthority? else SAFE,
        candidate = m["candidate"] as Candidate?,
        wirelessEnabled = m["wirelessEnabled"] as Boolean?,
        buildTapsDone = m["buildTapsDone"] as? Int ?: 0,
        pairCodeCandidates = m["pairCodeCandidates"] as? List<String> ?: emptyList(),
        pairingContextHigh = m["pairingContextHigh"] as? Boolean ?: false,
        requestActive = m["requestActive"] as? Boolean ?: true,
        directDevProbeAttempted = m["directDevProbeAttempted"] as? Boolean ?: false,
        directDevScreenRecognized = m["directDevScreenRecognized"] as? Boolean ?: false,
    )
}

fun main() {
    val e = AdaptiveAutopilotPlanner()
    var n = 0
    fun expect(action: Action, obs: Observation) { check(e.decide(obs).action == action) { "expected $action got ${e.decide(obs)}" }; n++ }
    expect(Action.DONE, o("supportConnected" to true))
    expect(Action.CONNECT_SUPPORT, o("adbConnected" to true))
    expect(Action.UNSUPPORTED_ANDROID, o("androidApi" to 29))
    expect(Action.ASK_ENABLE_ACCESSIBILITY, o("accessibilityEnabled" to false))
    expect(Action.OPEN_DEVELOPER_SETTINGS, o("screen" to Screen.APP))
    expect(Action.OPEN_DEVICE_INFO_SETTINGS, o("screen" to Screen.SETTINGS_HOME, "directDevProbeAttempted" to true))
    expect(Action.FALLBACK_GUIDE, o("oem" to "Xiaomi", "screen" to Screen.SETTINGS_HOME, "directDevProbeAttempted" to true))
    expect(Action.CLICK_SOFTWARE_INFO, o("screen" to Screen.ABOUT_PHONE, "candidate" to c("software_info")))
    for (i in 0..6) expect(Action.CLICK_BUILD_NUMBER, o("screen" to Screen.SOFTWARE_INFO, "candidate" to c("build_number"), "buildTapsDone" to i))
    expect(Action.OPEN_DEVELOPER_SETTINGS, o("screen" to Screen.SOFTWARE_INFO, "candidate" to c("build_number"), "buildTapsDone" to 7))
    expect(Action.WAIT_USER_CREDENTIAL, o("screen" to Screen.CREDENTIAL_PROMPT))
    expect(Action.CLICK_WIRELESS_DEBUGGING, o("screen" to Screen.DEVELOPER_OPTIONS, "candidate" to c("wireless_debugging")))
    expect(Action.ENABLE_WIRELESS_DEBUGGING, o("screen" to Screen.WIRELESS_DEBUGGING, "candidate" to c("wireless_debugging_toggle"), "wirelessEnabled" to false))
    expect(Action.CLICK_PAIR_WITH_CODE, o("screen" to Screen.WIRELESS_DEBUGGING, "candidate" to c("pair_with_code"), "wirelessEnabled" to true))
    expect(Action.ACCEPT_NETWORK_CONFIRMATION, o("screen" to Screen.NETWORK_CONFIRMATION, "candidate" to c("network_confirm_positive")))
    expect(Action.AUTO_PAIR_WITH_CODE, o("screen" to Screen.PAIRING_DIALOG, "pairCodeCandidates" to listOf("123456"), "pairingContextHigh" to true))
    expect(Action.SHOW_MANUAL_PAIR_CODE, o("screen" to Screen.PAIRING_DIALOG, "pairCodeCandidates" to listOf("123456","654321"), "pairingContextHigh" to true))
    expect(Action.SHOW_MANUAL_PAIR_CODE, o("screen" to Screen.PAIRING_DIALOG, "pairCodeCandidates" to listOf("123456"), "pairingContextHigh" to false))
    expect(Action.SHOW_MANUAL_PAIR_CODE, o("screen" to Screen.PAIRING_DIALOG, "pairCodeCandidates" to listOf("123456"), "pairingContextHigh" to true, "requestActive" to false))
    expect(Action.FALLBACK_GUIDE, o("screen" to Screen.ABOUT_PHONE, "authority" to SnapshotAuthority(true,1,true,true,true), "candidate" to c("software_info")))
    expect(Action.FALLBACK_GUIDE, o("screen" to Screen.ABOUT_PHONE, "authority" to SnapshotAuthority(false,2,true,true,true), "candidate" to c("software_info")))
    expect(Action.FALLBACK_GUIDE, o("screen" to Screen.ABOUT_PHONE, "authority" to SnapshotAuthority(true,2,true,true,true,true), "candidate" to c("software_info")))
    expect(Action.FALLBACK_GUIDE, o("screen" to Screen.ABOUT_PHONE, "authority" to SnapshotAuthority(true,2,false,true,true), "candidate" to c("software_info")))
    expect(Action.FALLBACK_GUIDE, o("screen" to Screen.ABOUT_PHONE, "candidate" to c("software_info", Confidence.MEDIUM)))
    expect(Action.FALLBACK_GUIDE, o("screen" to Screen.ABOUT_PHONE, "candidate" to c("software_info", unique=false)))
    expect(Action.FALLBACK_GUIDE, o("screen" to Screen.ABOUT_PHONE, "candidate" to c("software_info", margin=false)))
    expect(Action.FALLBACK_GUIDE, o("screen" to Screen.ABOUT_PHONE, "candidate" to c("software_info", fresh=false)))
    expect(Action.WAIT_STABLE, o("screen" to Screen.WIRELESS_DEBUGGING, "candidate" to c("pair_with_code")))
    expect(Action.WAIT_STABLE, o("screen" to Screen.PAIRING_DIALOG, "authority" to SnapshotAuthority(true,1,true,true,true), "pairCodeCandidates" to listOf("123456"), "pairingContextHigh" to true))
    expect(Action.ASK_ALLOW_RESTRICTED_SETTINGS, o("accessibilityEnabled" to false, "restrictedSettingsRequired" to true))
    expect(Action.ASK_CONNECT_WIFI, o("wifiReady" to false))
    expect(Action.POLICY_BLOCKED, o("wirelessPolicyBlocked" to true))
    expect(Action.TRY_ADB_RECONNECT, o("previousPairingKnown" to true, "reconnectAttempted" to false))
    expect(Action.OPEN_DEVELOPER_SETTINGS, o("previousPairingKnown" to true, "reconnectAttempted" to true, "screen" to Screen.APP))
    println("PASS $n Kotlin reference checks")
}
