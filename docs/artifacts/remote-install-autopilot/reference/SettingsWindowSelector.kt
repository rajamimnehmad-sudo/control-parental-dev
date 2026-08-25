package reference.autopilot

import java.security.MessageDigest

class SettingsWindowSelector(
    private val trustedPackages: Set<String> = setOf("com.android.settings")
) {
    fun select(windows: List<UiWindowSnapshot>): WindowSelection {
        val candidates = windows.filter {
            it.kind == WindowKind.APPLICATION &&
            it.packageName != null &&
            it.packageName in trustedPackages
        }
        if (candidates.size != 1) {
            return WindowSelection.Rejected(
                if (candidates.isEmpty()) "no_trusted_settings_application_window"
                else "ambiguous_trusted_settings_application_windows"
            )
        }
        val w = candidates.single()
        val packageName = requireNotNull(w.packageName)
        return WindowSelection.Selected(
            TrustedSettingsSnapshot(w.id, packageName, w.nodes.filter { it.visible }, fingerprint(w))
        )
    }

    private fun fingerprint(w: UiWindowSnapshot): String {
        val canonical = buildString {
            append(w.id).append('|').append(w.packageName).append('|').append(w.kind)
            for (n in w.nodes.filter { it.visible }.sortedBy { it.path }) {
                append('\n').append(n.path)
                    .append('|').append(norm(n.text))
                    .append('|').append(norm(n.contentDescription))
                    .append('|').append(n.viewId.orEmpty())
                    .append('|').append(n.className.orEmpty())
                    .append('|').append(n.clickable)
                    .append('|').append(n.checkable)
                    .append('|').append(n.checked)
                    .append('|').append(n.enabled)
                    .append('|').append(n.bounds.left).append(',')
                    .append(n.bounds.top).append(',')
                    .append(n.bounds.right).append(',')
                    .append(n.bounds.bottom)
            }
        }
        val bytes = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun norm(s:String?): String = s.orEmpty().trim().lowercase()
}
