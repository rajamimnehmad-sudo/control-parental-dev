package reference.autopilot

class SamsungSettingsClassifier {
    private val softwareInfo = setOf("información de software", "software information")
    private val buildNumber = setOf("número de compilación", "build number")
    private val developerOptions = setOf("opciones de desarrollador", "developer options")
    private val wireless = setOf("depuración inalámbrica", "wireless debugging")
    private val pairWithCode = setOf(
        "vincular dispositivo con código de vinculación",
        "vincular dispositivo con código",
        "pair device with pairing code"
    )
    private val softwareScreenHints = setOf("información de software", "software information")
    private val developerScreenHints = setOf("opciones de desarrollador", "developer options")
    private val wirelessScreenHints = setOf("depuración inalámbrica", "wireless debugging")

    fun classify(s: TrustedSettingsSnapshot): ClassifiedScreen {
        val texts = s.nodes.flatMap { nodeStrings(it) }.map(::norm)
        val screen = when {
            texts.any { it in wirelessScreenHints } && s.nodes.any { exact(it, pairWithCode) } -> Screen.WIRELESS_DEBUGGING
            texts.any { it in developerScreenHints } && s.nodes.any { exact(it, wireless) } -> Screen.DEVELOPER_OPTIONS
            texts.any { it in softwareScreenHints } && s.nodes.any { exact(it, buildNumber) } -> Screen.SOFTWARE_INFO
            s.nodes.any { exact(it, softwareInfo) } -> Screen.ABOUT_PHONE
            else -> Screen.UNKNOWN
        }

        val targets = mutableMapOf<TargetKey, MatchedTarget>()
        match(s, TargetKey.SOFTWARE_INFO, softwareInfo, expected = screen == Screen.ABOUT_PHONE)?.let { targets[it.key]=it }
        match(s, TargetKey.BUILD_NUMBER, buildNumber, expected = screen == Screen.SOFTWARE_INFO)?.let { targets[it.key]=it }
        match(s, TargetKey.WIRELESS_DEBUGGING, wireless, expected = screen == Screen.DEVELOPER_OPTIONS)?.let { targets[it.key]=it }
        match(s, TargetKey.WIRELESS_DEBUGGING_TOGGLE, wireless, expected = screen == Screen.WIRELESS_DEBUGGING, preferCheckable = true)?.let { targets[it.key]=it }
        match(s, TargetKey.PAIR_WITH_CODE, pairWithCode, expected = screen == Screen.WIRELESS_DEBUGGING)?.let { targets[it.key]=it }

        val confidence = if (screen == Screen.UNKNOWN) Confidence.LOW else Confidence.HIGH
        return ClassifiedScreen(screen, confidence, targets)
    }

    private fun match(
        s: TrustedSettingsSnapshot,
        key: TargetKey,
        aliases: Set<String>,
        expected: Boolean,
        preferCheckable: Boolean = false,
    ): MatchedTarget? {
        if (!expected) return null
        val scored = s.nodes.mapNotNull { n ->
            val vals = nodeStrings(n).map(::norm)
            if (vals.none { it in aliases }) null
            else {
                var score = 70
                if (n.clickable) score += 15
                if (n.enabled) score += 5
                if (n.viewId?.contains("title", ignoreCase=true) == true) score += 3
                if (preferCheckable && n.checkable) score += 7
                n to score
            }
        }.sortedByDescending { it.second }
        if (scored.isEmpty()) return null
        val top = scored[0]
        val second = scored.getOrNull(1)?.second
        val unique = scored.count { it.second == top.second } == 1
        val marginOk = second == null || top.second - second >= 8
        val conf = if (top.second >= 85 && unique && marginOk) Confidence.HIGH else Confidence.MEDIUM
        return MatchedTarget(key, top.first.path, conf, top.second, second, top.first.clickable || (preferCheckable && top.first.checkable), unique, marginOk)
    }

    private fun exact(n: UiNodeSnapshot, aliases: Set<String>): Boolean =
        nodeStrings(n).map(::norm).any { it in aliases }

    private fun nodeStrings(n: UiNodeSnapshot): List<String> =
        listOfNotNull(n.text, n.contentDescription) + n.ancestorTexts

    private fun norm(s: String): String =
        s.trim().lowercase().replace(Regex("\\s+"), " ")
}
