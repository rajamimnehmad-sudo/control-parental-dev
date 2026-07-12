package com.contentfilter.feature.vpn.domainlist

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WebDomainListUpdater
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        httpClient: OkHttpClient,
        private val store: WebDomainListStore,
    ) {
        private val preferences = context.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
        private val client =
            httpClient.newBuilder()
                .connectTimeout(NetworkTimeoutSeconds, TimeUnit.SECONDS)
                .readTimeout(DownloadTimeoutSeconds, TimeUnit.SECONDS)
                .callTimeout(DownloadTimeoutSeconds, TimeUnit.SECONDS)
                .build()

        suspend fun refreshIfDue(force: Boolean = false): DomainListUpdateResult =
            withContext(Dispatchers.IO) {
                val now = System.currentTimeMillis()
                if (!force && now - preferences.getLong(LastCheckKey, 0L) < CheckIntervalMillis) {
                    return@withContext DomainListUpdateResult.NotDue
                }
                val result =
                    runCatching { update() }
                        .onFailure { Log.w(LogTag, "Domain list update failed type=${it.javaClass.simpleName}") }
                        .getOrElse { DomainListUpdateResult.Failed }
                if (result is DomainListUpdateResult.Current || result is DomainListUpdateResult.Installed) {
                    preferences.edit().putLong(LastCheckKey, now).apply()
                }
                result
            }

        private fun update(): DomainListUpdateResult {
            val manifestBytes = fetch(environmentManifestUrl()) ?: return DomainListUpdateResult.Failed
            val manifest = DomainListManifestVerifier().verifyAndParse(manifestBytes)
            if (manifest.environment != environment()) return DomainListUpdateResult.Failed
            if (manifest.version <= store.version) return DomainListUpdateResult.Current
            val data = fetch(manifest.dataUrl) ?: return DomainListUpdateResult.Failed
            if (data.size.toLong() != manifest.sizeBytes) return DomainListUpdateResult.Failed
            if (!data.sha256().equals(manifest.sha256, ignoreCase = true)) return DomainListUpdateResult.Failed
            if (!DomainListManifestVerifier().verifyData(data, manifest.dataSignature)) {
                return DomainListUpdateResult.Failed
            }
            val installed = store.install(data, manifest.version)
            Log.i(
                LogTag,
                "Domain list update result=${if (installed) "installed" else "current"} " +
                    "version=${manifest.version} entries=${manifest.totalCount} environment=${manifest.environment}",
            )
            return if (installed) DomainListUpdateResult.Installed(manifest.version) else DomainListUpdateResult.Current
        }

        private fun fetch(url: String): ByteArray? =
            runCatching {
                client.newCall(Request.Builder().url(url).get().build()).execute().use { response ->
                    if (!response.isSuccessful) return null
                    response.body?.bytes()
                }
            }.getOrNull()

        private fun environment(): String =
            when {
                context.packageName.endsWith(".dev") -> "DEV"
                context.packageName.endsWith(".beta") -> "BETA"
                else -> "PRODUCTION"
            }

        private fun environmentManifestUrl(): String =
            "$StorageBaseUrl/${environment().lowercase()}/current-manifest.json"

        private companion object {
            const val LogTag = "WebDomainListUpdate"
            const val PreferencesName = "web-domain-list-update"
            const val LastCheckKey = "last-check-at"
            const val CheckIntervalMillis = 24 * 60 * 60 * 1_000L
            const val NetworkTimeoutSeconds = 20L
            const val DownloadTimeoutSeconds = 180L
            const val StorageBaseUrl =
                "https://syeycayasyufedwoprea.supabase.co/storage/v1/object/public/dev-updates/web-domain-list"
        }
    }

sealed interface DomainListUpdateResult {
    data object NotDue : DomainListUpdateResult

    data object Current : DomainListUpdateResult

    data object Failed : DomainListUpdateResult

    data class Installed(val version: Long) : DomainListUpdateResult
}

data class WebDomainListManifest(
    val version: Long,
    val environment: String,
    val dataUrl: String,
    val sizeBytes: Long,
    val sha256: String,
    val dataSignature: String,
    val totalCount: Long,
)

class DomainListManifestVerifier(
    publicKeyBase64: String = PublicKeyBase64,
) {
    private val publicKey =
        KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)),
        )

    fun verifyAndParse(manifestBytes: ByteArray): WebDomainListManifest {
        val envelope = JSONObject(manifestBytes.decodeToString())
        val payloadBytes = Base64.getDecoder().decode(envelope.getString("signedPayload"))
        require(verify(payloadBytes, envelope.getString("manifestSignature"))) { "Invalid manifest signature." }
        val payload = JSONObject(payloadBytes.decodeToString())
        require(payload.getInt("formatVersion") == WebDomainList.FormatVersion)
        require(payload.getString("signatureStatus") == "valid")
        return WebDomainListManifest(
            version = payload.getLong("version"),
            environment = payload.getString("environment"),
            dataUrl = payload.getString("dataUrl"),
            sizeBytes = payload.getLong("sizeBytes"),
            sha256 = payload.getString("sha256"),
            dataSignature = payload.getString("dataSignature"),
            totalCount = payload.getLong("totalCount"),
        )
    }

    fun verifyData(
        data: ByteArray,
        signatureBase64: String,
    ): Boolean = verify(data, signatureBase64)

    private fun verify(
        data: ByteArray,
        signatureBase64: String,
    ): Boolean =
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(publicKey)
            update(data)
            verify(Base64.getDecoder().decode(signatureBase64))
        }

    companion object {
        const val PublicKeyBase64 =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEEoTJncb+tUn3p8KtQtXENfRH1Z56HjESILP+k1LsMXVen4YJzKjm7t/Wj3wBvxoahiEsYTT9RkJ1u6VqHqGJrA=="
    }
}

private fun ByteArray.sha256(): String =
    MessageDigest.getInstance("SHA-256")
        .digest(this)
        .joinToString("") { "%02x".format(it) }
