package com.contentfilter.user.chromedataplane

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Bundle
import android.os.Parcel
import android.util.Base64
import android.util.Log
import com.contentfilter.core.domain.chrome.ChromePhotosDataPlaneLabContract
import com.contentfilter.feature.accessibility.service.ProtectionDeviceAdminReceiver

internal data class ChromePhotosLabPolicyResult(
    val caFingerprint: String,
    val proxyPolicy: String,
)

internal data class ChromePhotosLabBatteryBaselineResidualState(
    val labProxyAbsent: Boolean,
    val ephemeralCaAbsent: Boolean,
)

/** Pure, type-sensitive contract used around the Android Bundle/Parcel transaction. */
internal object ChromeRestrictionsSnapshotContract {
    fun merge(
        snapshot: Map<String, Any?>,
        overrides: Map<String, Any?>,
    ): Map<String, Any?> {
        return copyOf(snapshot).toMutableMap().apply { putAll(copyOf(overrides)) }
    }

    fun copyOf(values: Map<String, Any?>): Map<String, Any?> =
        values.mapValuesTo(linkedMapOf()) { (_, value) -> copyValue(value) }

    fun exactMatch(
        expected: Map<String, Any?>,
        actual: Map<String, Any?>,
    ): Boolean = canonical(expected) == canonical(actual)

    fun exactValueMatch(
        expected: Any?,
        actual: Any?,
    ): Boolean = canonicalValue(expected) == canonicalValue(actual)

    fun canonical(values: Map<String, Any?>): String =
        values.toSortedMap().entries.joinToString(prefix = "map{", postfix = "}") { (key, value) ->
            "${canonicalString(key)}=${canonicalValue(value)}"
        }

    private fun copyValue(value: Any?): Any? =
        when (value) {
            null, is String, is Boolean, is Byte, is Short, is Int, is Long, is Float, is Double, is Char -> value
            is Array<*> -> {
                val componentType = requireNotNull(value.javaClass.componentType)
                java.lang.reflect.Array.newInstance(componentType, value.size).also { copy ->
                    value.indices.forEach { index ->
                        java.lang.reflect.Array.set(copy, index, copyValue(value[index]))
                    }
                }
            }
            is ByteArray -> value.copyOf()
            is ShortArray -> value.copyOf()
            is IntArray -> value.copyOf()
            is LongArray -> value.copyOf()
            is FloatArray -> value.copyOf()
            is DoubleArray -> value.copyOf()
            is BooleanArray -> value.copyOf()
            is CharArray -> value.copyOf()
            is Bundle -> Bundle(value)
            is Map<*, *> ->
                value.entries.associateTo(linkedMapOf()) { (key, nestedValue) ->
                    require(key is String) { "Chrome restriction map key must be a String" }
                    key to copyValue(nestedValue)
                }
            else -> error("Unsupported Chrome restriction type ${value.javaClass.name}")
        }

    private fun canonicalValue(value: Any?): String =
        when (value) {
            null -> "null"
            is String -> "string:${canonicalString(value)}"
            is Boolean -> "boolean:$value"
            is Byte -> "byte:$value"
            is Short -> "short:$value"
            is Int -> "int:$value"
            is Long -> "long:$value"
            is Float -> "float:${value.toRawBits()}"
            is Double -> "double:${value.toRawBits()}"
            is Char -> "char:${value.code}"
            is Array<*> ->
                value.joinToString(
                    prefix = "array:${value.javaClass.componentType?.name}:[",
                    postfix = "]",
                ) { canonicalValue(it) }
            is ByteArray -> value.joinToString(prefix = "byteArray:[", postfix = "]")
            is ShortArray -> value.joinToString(prefix = "shortArray:[", postfix = "]")
            is IntArray -> value.joinToString(prefix = "intArray:[", postfix = "]")
            is LongArray -> value.joinToString(prefix = "longArray:[", postfix = "]")
            is FloatArray ->
                value.joinToString(prefix = "floatArray:[", postfix = "]") { it.toRawBits().toString() }
            is DoubleArray ->
                value.joinToString(prefix = "doubleArray:[", postfix = "]") { it.toRawBits().toString() }
            is BooleanArray -> value.joinToString(prefix = "booleanArray:[", postfix = "]")
            is CharArray -> value.joinToString(prefix = "charArray:[", postfix = "]") { it.code.toString() }
            is Bundle -> canonical(value.toRestrictionMap())
            is Map<*, *> ->
                canonical(
                    value.entries.associate { (key, nestedValue) ->
                        require(key is String) { "Chrome restriction map key must be a String" }
                        key to nestedValue
                    },
                )
            else -> error("Unsupported Chrome restriction type ${value.javaClass.name}")
        }

    private fun canonicalString(value: String): String = "${value.length}:$value"
}

/** Applies only a Chrome managed configuration; no global proxy is used. */
internal class ChromePhotosLabPolicyController(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val devicePolicyManager = appContext.getSystemService(DevicePolicyManager::class.java)
    private val admin = ComponentName(appContext, ProtectionDeviceAdminReceiver::class.java)
    private val preferences =
        appContext.getSharedPreferences(
            ChromePhotosDataPlaneLabContract.PreferencesName,
            Context.MODE_PRIVATE,
        )
    private var stockMediaAuthorityEnabled = false

    fun verifyOwnerAndCleanOrphanedState() {
        check(appContext.packageName.endsWith(".dev")) { "DEV package required" }
        check(devicePolicyManager.isDeviceOwnerApp(appContext.packageName)) { "Device Owner required" }
        rollbackOwnedPolicyAndCa()
    }

    fun apply(
        tls: ChromePhotosEphemeralTlsMaterial,
        enableStockMediaAuthority: Boolean = false,
    ): ChromePhotosLabPolicyResult {
        val current =
            devicePolicyManager.getApplicationRestrictions(
                admin,
                ChromePhotosDataPlaneLabContract.ChromePackage,
            )
        snapshotRestrictions(current)
        check(
            preferences.edit()
                .putString(
                    ChromePhotosDataPlaneLabContract.KeyInstalledCaDer,
                    Base64.encodeToString(tls.caCertificateDer, Base64.NO_WRAP),
                )
                .putString(ChromePhotosDataPlaneLabContract.KeyCaFingerprint, tls.caFingerprint)
                .commit(),
        ) { "DEV CA rollback state not persisted" }
        check(devicePolicyManager.installCaCert(admin, tls.caCertificateDer)) { "DEV CA installation failed" }

        val proxySettings = ChromePhotosChromePolicy.proxySettingsJson()
        stockMediaAuthorityEnabled = enableStockMediaAuthority
        val ownedValues =
            linkedMapOf<String, Any?>(ProxySettingsPolicy to proxySettings).apply {
                if (enableStockMediaAuthority) putAll(ChromeStockMediaManagedPolicy.values)
            }
        val expectedMerged =
            ChromeRestrictionsSnapshotContract.merge(
                snapshot = current.toRestrictionMap(),
                overrides = ownedValues,
            )
        val merged =
            Bundle(current).apply {
                putString(ProxySettingsPolicy, proxySettings)
                if (enableStockMediaAuthority) ChromeStockMediaManagedPolicy.applyTo(this)
            }
        check(
            ChromeRestrictionsSnapshotContract.exactMatch(
                expected = expectedMerged,
                actual = merged.toRestrictionMap(),
            ),
        ) { "Chrome restrictions merge changed an unrelated value or runtime type" }
        devicePolicyManager.setApplicationRestrictions(
            admin,
            ChromePhotosDataPlaneLabContract.ChromePackage,
            merged,
        )
        preferences.edit().putBoolean(KeyOwnedPolicyApplied, true).commit()
        val applied =
            devicePolicyManager.getApplicationRestrictions(
                admin,
                ChromePhotosDataPlaneLabContract.ChromePackage,
            )
        check(applied.getString(ProxySettingsPolicy) == proxySettings) { "Chrome proxy policy did not persist" }
        if (enableStockMediaAuthority) {
            check(ChromeStockMediaManagedPolicy.matches(applied)) {
                "Chrome stock-media policies did not persist"
            }
        }
        Log.i(
            LogTag,
            "policy=chrome-only proxy=fixed stockMedia=$enableStockMediaAuthority " +
                "policyTypes=${ChromeStockMediaManagedPolicy.runtimeTypes(applied)} " +
                "ca=${tls.caFingerprint.take(FingerprintLogLength)}",
        )
        return ChromePhotosLabPolicyResult(
            caFingerprint = tls.caFingerprint,
            proxyPolicy = proxySettings,
        )
    }

    fun isApplied(caCertificateDer: ByteArray): Boolean {
        if (!devicePolicyManager.isDeviceOwnerApp(appContext.packageName)) return false
        val proxySettings =
            devicePolicyManager.getApplicationRestrictions(
                admin,
                ChromePhotosDataPlaneLabContract.ChromePackage,
            )
        return proxySettings.getString(ProxySettingsPolicy) == ChromePhotosChromePolicy.proxySettingsJson() &&
            (!stockMediaAuthorityEnabled || ChromeStockMediaManagedPolicy.matches(proxySettings)) &&
            devicePolicyManager.hasCaCertInstalled(admin, caCertificateDer)
    }

    fun batteryBaselineResidualState(): ChromePhotosLabBatteryBaselineResidualState {
        val restrictions =
            devicePolicyManager.getApplicationRestrictions(
                admin,
                ChromePhotosDataPlaneLabContract.ChromePackage,
            )
        val ownsLabState =
            preferences.getBoolean(KeyOwnedPolicyApplied, false) ||
                preferences.contains(KeyRestrictionsSnapshot) ||
                preferences.contains(KeyRestrictionsSnapshotDigest)
        return ChromePhotosLabBatteryBaselineResidualState(
            labProxyAbsent =
                !ownsLabState &&
                    restrictions.getString(ProxySettingsPolicy) != ChromePhotosChromePolicy.proxySettingsJson(),
            ephemeralCaAbsent =
                !preferences.contains(ChromePhotosDataPlaneLabContract.KeyInstalledCaDer) &&
                    !preferences.contains(ChromePhotosDataPlaneLabContract.KeyCaFingerprint),
        )
    }

    fun rollbackOwnedPolicyAndCa() {
        preferences.edit()
            .putBoolean(ChromePhotosDataPlaneLabContract.KeyPresentationReady, false)
            .putBoolean(ChromePhotosDataPlaneLabContract.KeyActive, false)
            .putBoolean(ChromePhotosDataPlaneLabContract.KeyRealWebScopeConfirmed, false)
            .commit()

        if (devicePolicyManager.isDeviceOwnerApp(appContext.packageName)) {
            val current =
                devicePolicyManager.getApplicationRestrictions(
                    admin,
                    ChromePhotosDataPlaneLabContract.ChromePackage,
                )
            val encodedSnapshot = preferences.getString(KeyRestrictionsSnapshot, null)
            val ownsLegacyPolicy = preferences.getBoolean(KeyOwnedPolicyApplied, false)
            if (encodedSnapshot != null) {
                val snapshot = decodeRestrictions(encodedSnapshot)
                val expectedDigest = requireNotNull(preferences.getString(KeyRestrictionsSnapshotDigest, null))
                check(restrictionsDigest(snapshot) == expectedDigest) {
                    "Chrome restrictions snapshot digest mismatch"
                }
                devicePolicyManager.setApplicationRestrictions(
                    admin,
                    ChromePhotosDataPlaneLabContract.ChromePackage,
                    snapshot,
                )
                val restored =
                    devicePolicyManager.getApplicationRestrictions(
                        admin,
                        ChromePhotosDataPlaneLabContract.ChromePackage,
                    )
                check(
                    ChromeRestrictionsSnapshotContract.exactMatch(
                        expected = snapshot.toRestrictionMap(),
                        actual = restored.toRestrictionMap(),
                    ),
                ) {
                    "Chrome restrictions exact restore mismatch"
                }
            } else if (ownsLegacyPolicy && OwnedPolicyKeys.any(current::containsKey)) {
                // Legacy DEV sessions predate exact snapshotting. Remove only keys owned by this lab.
                OwnedPolicyKeys.forEach(current::remove)
                devicePolicyManager.setApplicationRestrictions(
                    admin,
                    ChromePhotosDataPlaneLabContract.ChromePackage,
                    current,
                )
            }
            preferences.getString(ChromePhotosDataPlaneLabContract.KeyInstalledCaDer, null)
                ?.let { encoded ->
                    runCatching {
                        devicePolicyManager.uninstallCaCert(admin, Base64.decode(encoded, Base64.DEFAULT))
                    }.onFailure { error ->
                        Log.w(LogTag, "rollback=ca_failed error=${error.javaClass.simpleName}")
                    }
                }
        }
        preferences.edit()
            .remove(ChromePhotosDataPlaneLabContract.KeyInstalledCaDer)
            .remove(ChromePhotosDataPlaneLabContract.KeyCaFingerprint)
            .remove(ChromePhotosDataPlaneLabContract.KeyResolvedRouteAddresses)
            .remove(ChromePhotosDataPlaneLabContract.KeyUdpFixtureGateEnabled)
            .remove(ChromePhotosDataPlaneLabContract.KeyUdpFixtureAddress)
            .remove(ChromePhotosDataPlaneLabContract.KeyUdpFixturePort)
            .remove(ChromePhotosDataPlaneLabContract.KeyUdpFixtureMalformedProbeEnabled)
            .remove(ChromePhotosDataPlaneLabContract.KeyStockMediaAuthorityEnabled)
            .remove(KeyRestrictionsSnapshot)
            .remove(KeyRestrictionsSnapshotDigest)
            .remove(KeyOwnedPolicyApplied)
            .apply()
        Log.i(LogTag, "rollback=complete proxy=cleared ca=removed")
        stockMediaAuthorityEnabled = false
    }

    private fun snapshotRestrictions(bundle: Bundle) {
        check(!preferences.contains(KeyRestrictionsSnapshot)) { "Chrome restrictions snapshot already active" }
        val encoded = encodeRestrictions(bundle)
        check(
            preferences.edit()
                .putString(KeyRestrictionsSnapshot, encoded)
                .putString(KeyRestrictionsSnapshotDigest, restrictionsDigest(bundle))
                .commit(),
        ) { "Chrome restrictions snapshot not persisted" }
    }

    private fun encodeRestrictions(bundle: Bundle): String {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(bundle)
            val bytes = parcel.marshall()
            try {
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            } finally {
                bytes.fill(0)
            }
        } finally {
            parcel.recycle()
        }
    }

    private fun decodeRestrictions(encoded: String): Bundle {
        val bytes = Base64.decode(encoded, Base64.DEFAULT)
        val parcel = Parcel.obtain()
        return try {
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            requireNotNull(parcel.readBundle(javaClass.classLoader))
        } finally {
            bytes.fill(0)
            parcel.recycle()
        }
    }

    private fun restrictionsDigest(bundle: Bundle): String =
        sha256(
            ChromeRestrictionsSnapshotContract
                .canonical(bundle.toRestrictionMap())
                .toByteArray(Charsets.UTF_8),
        )

    private companion object {
        const val ProxySettingsPolicy = "ProxySettings"
        const val FingerprintLogLength = 16
        const val LogTag = "ChromePhotosDataPlane"
        const val KeyOwnedPolicyApplied = "chrome_photos_owned_policy_applied"
        const val KeyRestrictionsSnapshot = "chrome_photos_restrictions_snapshot"
        const val KeyRestrictionsSnapshotDigest = "chrome_photos_restrictions_snapshot_digest"
        val OwnedPolicyKeys = setOf(ProxySettingsPolicy) + ChromeStockMediaManagedPolicy.keys
    }
}

internal object ChromeStockMediaManagedPolicy {
    val values: Map<String, Any> =
        linkedMapOf(
            "URLBlocklist" to BlockAllNavigations,
            "URLAllowlist" to AllowedStockChromeSurfaces,
            "IncognitoModeAvailability" to 1,
            "NTPContentSuggestionsEnabled" to false,
            "SearchSuggestEnabled" to false,
            "ForceGoogleSafeSearch" to true,
            "BackForwardCacheEnabled" to false,
            "AllowBackForwardCacheForCacheControlNoStorePageEnabled" to false,
            "SearchContentSharingSettings" to 1,
            "AIModeSettings" to 1,
            "FindsSettings" to 2,
            "DataUrlInSvgUseEnabled" to false,
        )
    val keys: Set<String> = values.keys

    fun applyTo(bundle: Bundle) {
        values.forEach { (key, value) ->
            when (value) {
                is String -> bundle.putString(key, value)
                is Int -> bundle.putInt(key, value)
                is Boolean -> bundle.putBoolean(key, value)
                else -> error("Unsupported managed Chrome policy type ${value.javaClass.name}")
            }
        }
    }

    fun matches(bundle: Bundle): Boolean = matchesValues(bundle.toRestrictionMap())

    fun matchesValues(actual: Map<String, Any?>): Boolean =
        values.all { (key, expected) ->
            actual.containsKey(key) &&
                ChromeRestrictionsSnapshotContract.exactValueMatch(expected, actual[key])
        }

    fun runtimeTypes(bundle: Bundle): String =
        keys.sorted().joinToString(",") { key -> "$key:${bundle.get(key)?.javaClass?.simpleName ?: "null"}" }

    private const val BlockAllNavigations = "[\"*\"]"
    private const val AllowedStockChromeSurfaces =
        "[\"http://*\",\"https://*\",\"chrome://newtab\",\"chrome://settings/*\"," +
            "\"chrome://policy\",\"chrome://management\",\"chrome://version\",\"chrome://downloads\"," +
            "\"chrome://history\"]"
}

private fun Bundle.toRestrictionMap(): Map<String, Any?> =
    keySet().associateTo(linkedMapOf()) { key -> key to get(key) }
