package com.contentfilter.user.chromeextension

internal object ChromeExtensionPolicyContract {
    const val ChromePackage = "com.android.chrome"
    const val ExtensionInstallForcelist = "ExtensionInstallForcelist"

    fun forceList(
        previous: Array<String>?,
        extensionId: String,
        updateUrl: String,
    ): Array<String> {
        require(extensionId.matches(ExtensionIdPattern)) { "invalid extension id" }
        require(updateUrl.startsWith(AllowedUpdatePrefix)) { "update URL must use loopback HTTP" }
        val entry = "$extensionId;$updateUrl"
        return previous.orEmpty()
            .filterNot { it.substringBefore(';') == extensionId }
            .plus(entry)
            .distinct()
            .toTypedArray()
    }

    private val ExtensionIdPattern = Regex("[a-p]{32}")
    private const val AllowedUpdatePrefix = "http://127.0.0.1:"
}
