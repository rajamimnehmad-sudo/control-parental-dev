package reference.autopilot

data class IntRect(val left:Int,val top:Int,val right:Int,val bottom:Int) {
    val area: Long get() = (right-left).coerceAtLeast(0).toLong() * (bottom-top).coerceAtLeast(0).toLong()
}

enum class WindowKind { APPLICATION, ACCESSIBILITY_OVERLAY, INPUT_METHOD, OTHER }

data class UiNodeSnapshot(
    val path: String,
    val text: String? = null,
    val contentDescription: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val clickable: Boolean = false,
    val checkable: Boolean = false,
    val checked: Boolean? = null,
    val visible: Boolean = true,
    val enabled: Boolean = true,
    val bounds: IntRect = IntRect(0,0,0,0),
    val ancestorTexts: List<String> = emptyList(),
)

data class UiWindowSnapshot(
    val id: Int,
    val packageName: String?,
    val kind: WindowKind,
    val focused: Boolean,
    val nodes: List<UiNodeSnapshot>,
)

data class TrustedSettingsSnapshot(
    val windowId: Int,
    val packageName: String,
    val nodes: List<UiNodeSnapshot>,
    val fingerprint: String,
)

sealed interface WindowSelection {
    data class Selected(val snapshot: TrustedSettingsSnapshot): WindowSelection
    data class Rejected(val reason: String): WindowSelection
}

enum class TargetKey {
    SOFTWARE_INFO,
    BUILD_NUMBER,
    WIRELESS_DEBUGGING,
    WIRELESS_DEBUGGING_TOGGLE,
    PAIR_WITH_CODE,
    NETWORK_CONFIRM_POSITIVE,
}

data class MatchedTarget(
    val key: TargetKey,
    val nodePath: String,
    val confidence: Confidence,
    val score: Int,
    val runnerUpScore: Int?,
    val clickable: Boolean,
    val unique: Boolean,
    val marginOk: Boolean,
)

data class ClassifiedScreen(
    val screen: Screen,
    val confidence: Confidence,
    val targets: Map<TargetKey, MatchedTarget>,
)
