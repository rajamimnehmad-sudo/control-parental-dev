package reference.autopilot

sealed interface PairingCodeResult {
    data class Unique(val code: String): PairingCodeResult
    data class Rejected(val reason: String): PairingCodeResult
}

class PairingCodeDetector {
    private val re = Regex("(?<!\\d)(\\d{6})(?!\\d)")

    fun detect(snapshot: TrustedSettingsSnapshot, classified: ClassifiedScreen): PairingCodeResult {
        if (classified.screen != Screen.WIRELESS_DEBUGGING && classified.screen != Screen.PAIRING_DIALOG) {
            return PairingCodeResult.Rejected("wrong_screen")
        }
        val context = snapshot.nodes.any {
            listOfNotNull(it.text, it.contentDescription).any { s ->
                val n = s.lowercase()
                n.contains("código") || n.contains("code") || n.contains("vincul")
            }
        }
        if (!context) return PairingCodeResult.Rejected("missing_pairing_context")
        val codes = snapshot.nodes.flatMap { n ->
            listOfNotNull(n.text, n.contentDescription).flatMap { s ->
                re.findAll(s).map { it.groupValues[1] }.toList()
            }
        }.distinct()
        return if (codes.size == 1) PairingCodeResult.Unique(codes.single())
        else PairingCodeResult.Rejected(if (codes.isEmpty()) "no_code" else "ambiguous_codes")
    }
}
