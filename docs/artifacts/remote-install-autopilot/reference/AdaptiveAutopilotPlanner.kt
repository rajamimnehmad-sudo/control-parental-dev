package reference.autopilot

enum class Confidence { LOW, MEDIUM, HIGH }
enum class Screen { UNKNOWN, APP, SETTINGS_HOME, ABOUT_PHONE, SOFTWARE_INFO, DEVELOPER_OPTIONS, WIRELESS_DEBUGGING, NETWORK_CONFIRMATION, PAIRING_DIALOG, CREDENTIAL_PROMPT }
enum class Action { WAIT_STABLE, ASK_ALLOW_RESTRICTED_SETTINGS, ASK_ENABLE_ACCESSIBILITY, ASK_CONNECT_WIFI, TRY_ADB_RECONNECT, POLICY_BLOCKED, UNSUPPORTED_ANDROID, OPEN_DEVELOPER_SETTINGS, OPEN_DEVICE_INFO_SETTINGS, CLICK_SOFTWARE_INFO, CLICK_BUILD_NUMBER, WAIT_USER_CREDENTIAL, CLICK_WIRELESS_DEBUGGING, ENABLE_WIRELESS_DEBUGGING, ACCEPT_NETWORK_CONFIRMATION, CLICK_PAIR_WITH_CODE, AUTO_PAIR_WITH_CODE, SHOW_MANUAL_PAIR_CODE, CONNECT_SUPPORT, FALLBACK_GUIDE, DONE }

data class SnapshotAuthority(
    val trustedSettingsWindow: Boolean,
    val stableSnapshots: Int,
    val generationCurrent: Boolean,
    val windowIdCurrent: Boolean,
    val fingerprintCurrent: Boolean,
    val ambiguousWindow: Boolean = false,
) {
    fun safe(): Boolean = trustedSettingsWindow && stableSnapshots >= 2 && generationCurrent && windowIdCurrent && fingerprintCurrent && !ambiguousWindow
}

data class Candidate(
    val key: String,
    val confidence: Confidence,
    val clickable: Boolean,
    val unique: Boolean,
    val marginOk: Boolean,
    val freshReacquired: Boolean,
) {
    fun safeToClick(): Boolean = confidence == Confidence.HIGH && clickable && unique && marginOk && freshReacquired
}

data class Observation(
    val androidApi: Int,
    val oem: String,
    val accessibilityEnabled: Boolean,
    val adbConnected: Boolean = false,
    val supportConnected: Boolean = false,
    val restrictedSettingsRequired: Boolean = false,
    val wifiReady: Boolean = true,
    val wirelessPolicyBlocked: Boolean = false,
    val previousPairingKnown: Boolean = false,
    val reconnectAttempted: Boolean = false,
    val screen: Screen = Screen.UNKNOWN,
    val authority: SnapshotAuthority? = null,
    val candidate: Candidate? = null,
    val wirelessEnabled: Boolean? = null,
    val buildTapsDone: Int = 0,
    val pairCodeCandidates: List<String> = emptyList(),
    val pairingContextHigh: Boolean = false,
    val requestActive: Boolean = true,
    val directDevProbeAttempted: Boolean = false,
    val directDevScreenRecognized: Boolean = false,
)

data class Decision(val action: Action, val target: String? = null, val reason: String = "")

class AdaptiveAutopilotPlanner {
    companion object { const val MIN_WIRELESS_ADB_API = 30 }

    fun decide(o: Observation): Decision {
        if (o.supportConnected) return Decision(Action.DONE, reason = "support already connected")
        if (o.adbConnected) return Decision(Action.CONNECT_SUPPORT, reason = "ADB already valid; skip Settings/pairing")
        if (o.androidApi < MIN_WIRELESS_ADB_API) return Decision(Action.UNSUPPORTED_ANDROID, reason = "Android <11 standard wireless path unsupported")
        if (!o.accessibilityEnabled && o.restrictedSettingsRequired) return Decision(Action.ASK_ALLOW_RESTRICTED_SETTINGS, reason = "sideloaded Accessibility is restricted by OS")
        if (!o.accessibilityEnabled) return Decision(Action.ASK_ENABLE_ACCESSIBILITY, reason = "manual bootstrap prerequisite")
        if (!o.wifiReady) return Decision(Action.ASK_CONNECT_WIFI, reason = "Wireless Debugging needs a usable Wi-Fi network")
        if (o.wirelessPolicyBlocked) return Decision(Action.POLICY_BLOCKED, reason = "Wireless Debugging disabled by device/admin policy")
        if (o.previousPairingKnown && !o.reconnectAttempted) return Decision(Action.TRY_ADB_RECONNECT, reason = "reuse previous pairing before opening Settings")
        if (o.screen == Screen.CREDENTIAL_PROMPT) return Decision(Action.WAIT_USER_CREDENTIAL, reason = "never automate device credential")

        if (o.screen == Screen.PAIRING_DIALOG) {
            if (!safeObservation(o)) return Decision(Action.WAIT_STABLE, reason = "pairing dialog not stable/trusted")
            val codes = o.pairCodeCandidates.filter { it.length == 6 && it.all(Char::isDigit) }
            if (o.requestActive && o.pairingContextHigh && codes.size == 1) {
                return Decision(Action.AUTO_PAIR_WITH_CODE, target = codes.single(), reason = "unique contextual code")
            }
            return Decision(Action.SHOW_MANUAL_PAIR_CODE, reason = "PIN not unambiguous")
        }

        if (o.screen == Screen.NETWORK_CONFIRMATION) {
            return if (safeClick(o, "network_confirm_positive")) Decision(Action.ACCEPT_NETWORK_CONFIRMATION, "network_confirm_positive")
            else Decision(Action.FALLBACK_GUIDE, reason = "unsafe confirmation")
        }

        if (o.screen == Screen.WIRELESS_DEBUGGING) {
            return when (o.wirelessEnabled) {
                false -> if (safeClick(o, "wireless_debugging_toggle")) Decision(Action.ENABLE_WIRELESS_DEBUGGING, "wireless_debugging_toggle") else Decision(Action.FALLBACK_GUIDE, reason = "unsafe toggle")
                true -> if (safeClick(o, "pair_with_code")) Decision(Action.CLICK_PAIR_WITH_CODE, "pair_with_code") else Decision(Action.FALLBACK_GUIDE, reason = "unsafe pair target")
                null -> Decision(Action.WAIT_STABLE, reason = "wireless state unknown")
            }
        }

        if (o.screen == Screen.DEVELOPER_OPTIONS) {
            return if (safeClick(o, "wireless_debugging")) Decision(Action.CLICK_WIRELESS_DEBUGGING, "wireless_debugging")
            else Decision(Action.FALLBACK_GUIDE, reason = "wireless row unsafe")
        }

        if (o.screen == Screen.SOFTWARE_INFO) {
            if (o.buildTapsDone < 7) {
                return if (safeClick(o, "build_number")) Decision(Action.CLICK_BUILD_NUMBER, "build_number", "tap ${o.buildTapsDone + 1}/7")
                else Decision(Action.FALLBACK_GUIDE, reason = "build number unsafe")
            }
            return Decision(Action.OPEN_DEVELOPER_SETTINGS, reason = "re-probe after seven taps")
        }

        if (o.screen == Screen.ABOUT_PHONE) {
            return if (safeClick(o, "software_info")) Decision(Action.CLICK_SOFTWARE_INFO, "software_info")
            else Decision(Action.FALLBACK_GUIDE, reason = "software info unsafe")
        }

        if (!o.directDevProbeAttempted) return Decision(Action.OPEN_DEVELOPER_SETTINGS, reason = "fast path")

        if (o.directDevProbeAttempted && !o.directDevScreenRecognized) {
            if (o.oem.equals("Samsung", ignoreCase = true)) return Decision(Action.OPEN_DEVICE_INFO_SETTINGS, reason = "Samsung enable-development fallback")
            return Decision(Action.FALLBACK_GUIDE, reason = "OEM recipe unavailable")
        }

        return Decision(Action.FALLBACK_GUIDE, reason = "unrecognized state")
    }

    private fun safeObservation(o: Observation): Boolean = o.authority?.safe() == true
    private fun safeClick(o: Observation, key: String): Boolean = safeObservation(o) && o.candidate?.let { it.key == key && it.safeToClick() } == true
}
