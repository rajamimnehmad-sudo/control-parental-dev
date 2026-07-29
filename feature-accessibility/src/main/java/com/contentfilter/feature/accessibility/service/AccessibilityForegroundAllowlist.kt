package com.contentfilter.feature.accessibility.service

internal object AccessibilityForegroundAllowlist {
    private val exactPackageNames =
        setOf(
            "android",
            "com.android.contacts",
            "com.android.dialer",
            "com.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.android.phone",
            "com.android.providers.downloads",
            "com.android.settings",
            "com.android.vending",
            "com.contentfilter.admin",
            "com.contentfilter.admin.dev",
            "com.contentfilter.admin.beta",
            "com.contentfilter.dagbrowser",
            "com.contentfilter.dagbrowser.dev",
            "com.contentfilter.dagbrowser.beta",
            "com.contentfilter.user",
            "com.contentfilter.user.dev",
            "com.contentfilter.user.beta",
            "com.google.android.contacts",
            "com.google.android.dialer",
            "com.google.android.gms",
            "com.google.android.gsf",
            "com.google.android.packageinstaller",
            "com.google.android.permissioncontroller",
            "com.google.android.setupwizard",
        )

    private val packagePrefixes =
        listOf(
            "com.android.launcher",
            "com.google.android.apps.nexuslauncher",
            "com.google.android.inputmethod",
            "com.google.android.webview",
        )

    fun contains(packageName: String): Boolean =
        packageName in exactPackageNames ||
            packagePrefixes.any(packageName::startsWith) ||
            packageName.endsWith(".launcher")
}
