package com.contentfilter.dagbrowser

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URI
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

internal data class DagDevUpdateManifest(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    val apkSha256: String,
    val releaseNotes: String,
)

internal sealed interface DagDevUpdateCheckResult {
    data class Available(val manifest: DagDevUpdateManifest) : DagDevUpdateCheckResult

    data object UpToDate : DagDevUpdateCheckResult

    data object Unavailable : DagDevUpdateCheckResult
}

internal sealed interface DagDevUpdateDownloadResult {
    data class Ready(val apk: File) : DagDevUpdateDownloadResult

    data object Failed : DagDevUpdateDownloadResult
}

/** Minimal DEV updater for DAG. Network, hash, package and signer checks all fail closed. */
internal class DagDevUpdateClient(
    private val context: Context,
    private val manifestUrl: String,
) {
    fun check(currentVersionCode: Long): DagDevUpdateCheckResult {
        if (!isHttps(manifestUrl)) return DagDevUpdateCheckResult.Unavailable
        val payload = readBoundedHttps(manifestUrl, MaxManifestBytes) ?: return DagDevUpdateCheckResult.Unavailable
        val manifest =
            DagDevUpdateManifestParser.parse(payload.decodeToString())
                ?: return DagDevUpdateCheckResult.Unavailable
        payload.fill(0)
        return if (manifest.versionCode > currentVersionCode) {
            DagDevUpdateCheckResult.Available(manifest)
        } else {
            DagDevUpdateCheckResult.UpToDate
        }
    }

    fun download(
        manifest: DagDevUpdateManifest,
        onProgress: (Int) -> Unit,
    ): DagDevUpdateDownloadResult {
        if (!isHttps(manifest.apkUrl)) return DagDevUpdateDownloadResult.Failed
        val directory = File(context.cacheDir, UpdateDirectory).apply { mkdirs() }
        val partial = File(directory, "dag-update.partial")
        val apk = File(directory, "dag-update-${manifest.versionCode}.apk")
        partial.delete()
        apk.delete()
        val connection = openHttps(manifest.apkUrl) ?: return DagDevUpdateDownloadResult.Failed
        return try {
            if (connection.responseCode != HttpsURLConnection.HTTP_OK) {
                DagDevUpdateDownloadResult.Failed
            } else {
                val length = connection.contentLengthLong
                if (length !in 1..MaxApkBytes) {
                    DagDevUpdateDownloadResult.Failed
                } else {
                    val digest = MessageDigest.getInstance("SHA-256")
                    connection.inputStream.use { input ->
                        FileOutputStream(partial).use { output ->
                            val buffer = ByteArray(BufferBytes)
                            var downloaded = 0L
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                downloaded += count
                                if (downloaded > length || downloaded > MaxApkBytes) {
                                    partial.delete()
                                    return DagDevUpdateDownloadResult.Failed
                                }
                                digest.update(buffer, 0, count)
                                output.write(buffer, 0, count)
                                onProgress(((downloaded * 100L) / length).toInt().coerceIn(0, 99))
                            }
                            buffer.fill(0)
                        }
                    }
                    val actualSha = digest.digest().joinToString("") { "%02x".format(it) }
                    if (
                        partial.length() != length ||
                        !actualSha.equals(manifest.apkSha256, ignoreCase = true) ||
                        !partial.renameTo(apk) ||
                        !isExpectedSignedArchive(apk)
                    ) {
                        partial.delete()
                        apk.delete()
                        DagDevUpdateDownloadResult.Failed
                    } else {
                        onProgress(100)
                        DagDevUpdateDownloadResult.Ready(apk)
                    }
                }
            }
        } catch (_: Exception) {
            partial.delete()
            apk.delete()
            DagDevUpdateDownloadResult.Failed
        } finally {
            connection.disconnect()
        }
    }

    fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        withTrustedInstallAuthorization {
            context.startActivity(
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }

    fun install(apk: File): Boolean {
        if (!apk.isFile || !isExpectedSignedArchive(apk)) return false
        withTrustedInstallAuthorization {
            val uri =
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.downloads.fileprovider",
                    apk,
                )
            context.startActivity(
                Intent(Intent.ACTION_INSTALL_PACKAGE)
                    .setData(uri)
                    .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
        return true
    }

    @Suppress("DEPRECATION")
    private fun isExpectedSignedArchive(apk: File): Boolean {
        val flags = PackageManager.GET_SIGNING_CERTIFICATES
        val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
        val current = context.packageManager.getPackageInfo(context.packageName, flags)
        val archiveSigners = archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        val currentSigners = current.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
        return archive.packageName == context.packageName &&
            archiveSigners.isNotEmpty() &&
            archiveSigners == currentSigners
    }

    private fun withTrustedInstallAuthorization(action: () -> Unit) {
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(
                    context: Context?,
                    intent: Intent?,
                ) = action()
            }
        context.sendOrderedBroadcast(
            Intent(TrustedInstallAction).setComponent(
                ComponentName(userPackageName(), TrustedInstallReceiverClass),
            ),
            TrustedInstallPermission,
            receiver,
            null,
            Activity.RESULT_OK,
            null,
            null,
        )
    }

    private fun userPackageName(): String =
        if (context.packageName.endsWith(".dev")) "com.contentfilter.user.dev" else "com.contentfilter.user"

    private fun readBoundedHttps(
        url: String,
        maximumBytes: Int,
    ): ByteArray? {
        val connection = openHttps(url) ?: return null
        return try {
            if (connection.responseCode != HttpsURLConnection.HTTP_OK) return null
            val length = connection.contentLengthLong
            if (length > maximumBytes) return null
            connection.inputStream.use { input ->
                val output = ArrayList<Byte>(minOf(maximumBytes, length.coerceAtLeast(0).toInt()))
                val buffer = ByteArray(4 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (output.size + count > maximumBytes) return null
                    repeat(count) { output += buffer[it] }
                }
                buffer.fill(0)
                output.toByteArray()
            }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun openHttps(url: String): HttpsURLConnection? =
        runCatching {
            (URL(url).openConnection() as HttpsURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = NetworkTimeoutMillis
                readTimeout = DownloadTimeoutMillis
                requestMethod = "GET"
                setRequestProperty("Accept-Encoding", "identity")
                setRequestProperty("Cache-Control", "no-cache")
            }
        }.getOrNull()

    private fun isHttps(value: String): Boolean =
        runCatching { URI(value).scheme.equals("https", ignoreCase = true) }.getOrDefault(false)

    private companion object {
        const val UpdateDirectory = "dag-updates"
        const val MaxManifestBytes = 64 * 1024
        const val MaxApkBytes = 220L * 1024L * 1024L
        const val BufferBytes = 64 * 1024
        const val NetworkTimeoutMillis = 20_000
        const val DownloadTimeoutMillis = 180_000
        const val TrustedInstallAction = "com.contentfilter.action.AUTHORIZE_TRUSTED_INSTALL"
        const val TrustedInstallPermission = "com.contentfilter.permission.AUTHORIZE_TRUSTED_INSTALL"
        const val TrustedInstallReceiverClass =
            "com.contentfilter.user.apps.TrustedInstallAuthorizationReceiver"
    }
}

internal object DagDevUpdateManifestParser {
    private val Sha256Pattern = Regex("^[0-9a-fA-F]{64}$")

    fun parse(raw: String): DagDevUpdateManifest? =
        runCatching {
            val json = JSONObject(raw)
            val versionCode = json.getLong("versionCode")
            val versionName = json.getString("versionName").trim()
            val apkUrl = json.getString("apkUrl").trim()
            val apkSha256 = json.getString("apkSha256").trim().lowercase()
            val releaseNotes = json.optString("releaseNotes").trim()
            require(versionCode > 0)
            require(versionName.length in 1..80)
            require(URI(apkUrl).scheme.equals("https", ignoreCase = true))
            require(Sha256Pattern.matches(apkSha256))
            require(releaseNotes.length <= 4_000)
            DagDevUpdateManifest(versionCode, versionName, apkUrl, apkSha256, releaseNotes)
        }.getOrNull()
}
