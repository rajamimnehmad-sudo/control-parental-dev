package com.contentfilter.user.chromeextension

import org.json.JSONArray
import org.json.JSONTokener

internal object ChromeExtensionPolicyContract {
    const val ChromePackage = "com.android.chrome"
    const val ExtensionInstallForcelist = "ExtensionInstallForcelist"

    data class Mutation(
        val previousValue: Any?,
        val appliedValue: String,
        val legacyTypeDetected: Boolean,
    )

    fun mutation(
        previousValue: Any?,
        extensionId: String,
        updateUrl: String,
    ): Mutation {
        require(extensionId.matches(ExtensionIdPattern)) { "invalid extension id" }
        require(updateUrl.startsWith(AllowedUpdatePrefix)) { "update URL must use loopback HTTP" }
        val previous = decode(previousValue)
        val entry = "$extensionId;$updateUrl"
        val entries = previous.entries.filterNot { it.substringBefore(';') == extensionId }.plus(entry)
        return Mutation(
            previousValue = previousValue,
            appliedValue = JSONArray(entries).toString(),
            legacyTypeDetected = previous.legacyTypeDetected,
        )
    }

    fun entries(policyValue: String): List<String> = decodeString(policyValue)

    private fun decode(value: Any?): Decoded =
        when (value) {
            null -> Decoded(emptyList(), legacyTypeDetected = false)
            is String -> Decoded(decodeString(value), legacyTypeDetected = false)
            is Array<*> -> {
                require(value.all { it is String }) { "legacy ExtensionInstallForcelist must contain strings" }
                Decoded(value.map { it as String }, legacyTypeDetected = true)
            }
            else -> throw IllegalArgumentException(
                "ExtensionInstallForcelist must be an Android JSON string, was ${value.javaClass.name}",
            )
        }

    private fun decodeString(value: String): List<String> {
        try {
            val tokener = JSONTokener(value)
            val parsed = tokener.nextValue()
            require(parsed is JSONArray) { "ExtensionInstallForcelist must be a JSON array" }
            require(tokener.nextClean().code == 0) { "unexpected content after ExtensionInstallForcelist" }
            return buildList(parsed.length()) {
                repeat(parsed.length()) { index ->
                    val entry = parsed.get(index)
                    require(entry is String) { "ExtensionInstallForcelist[$index] must be a string" }
                    add(entry)
                }
            }
        } catch (error: IllegalArgumentException) {
            throw error
        } catch (error: Exception) {
            throw IllegalArgumentException("invalid ExtensionInstallForcelist JSON", error)
        }
    }

    private data class Decoded(
        val entries: List<String>,
        val legacyTypeDetected: Boolean,
    )

    private val ExtensionIdPattern = Regex("[a-p]{32}")
    private const val AllowedUpdatePrefix = "http://127.0.0.1:"
}
