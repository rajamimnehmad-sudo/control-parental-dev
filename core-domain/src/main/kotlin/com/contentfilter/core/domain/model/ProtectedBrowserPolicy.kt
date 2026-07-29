package com.contentfilter.core.domain.model

/**
 * Canonical remote preference for requiring DAG Browser on a protected device.
 *
 * It deliberately travels with the existing Web policy snapshot, so enabling DAG does not create
 * a second configuration channel that could drift out of sync with Accessibility.
 */
object ProtectedBrowserPolicy {
    const val RuleTarget = "__dag_browser_required__"
    const val RulePriority = 5_100
    const val DevPackageName = "com.contentfilter.dagbrowser.dev"
    const val ProductionPackageName = "com.contentfilter.dagbrowser"

    val ProtectedBrowserPackages =
        setOf(
            DevPackageName,
            ProductionPackageName,
        )

    val KnownAlternativeBrowserPackages =
        setOf(
            "com.android.chrome",
            "com.sec.android.app.sbrowser",
            "org.mozilla.firefox",
            "org.mozilla.firefox_beta",
            "com.microsoft.emmx",
            "com.brave.browser",
            "com.opera.browser",
            "com.opera.mini.native",
            "com.duckduckgo.mobile.android",
            "com.vivaldi.browser",
            "com.kiwibrowser.browser",
            "com.UCMobile.intl",
            "mark.via.gp",
        )

    val SearchAppPackages = setOf("com.google.android.googlequicksearchbox")

    fun isProtectedBrowser(packageName: String): Boolean = packageName in ProtectedBrowserPackages

    fun isKnownAlternativeBrowser(packageName: String): Boolean =
        packageName in KnownAlternativeBrowserPackages || packageName in SearchAppPackages
}

fun Iterable<PolicyRule>.protectedBrowserRequired(): Boolean =
    any {
        it.enabled &&
            it.scope == RuleScope.Domain &&
            it.action == RuleAction.Allow &&
            it.target == ProtectedBrowserPolicy.RuleTarget
    }
