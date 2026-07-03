package com.contentfilter.core.update.repository

import android.content.Context
import com.contentfilter.core.update.config.UpdateConfigProvider
import com.contentfilter.core.update.model.UpdateCheckResult
import com.contentfilter.core.update.model.UpdateDownloadResult
import com.contentfilter.core.update.model.UpdateManifest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject

class DevApkUpdateRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val configProvider: UpdateConfigProvider,
        private val httpClient: OkHttpClient,
    ) : ApkUpdateRepository {
        override suspend fun checkForUpdate(currentVersionCode: Int): UpdateCheckResult =
            withContext(Dispatchers.IO) {
                val url = configProvider.manifestUrl()
                if (url.isBlank()) return@withContext UpdateCheckResult.NotConfigured

                val manifest = fetchManifest(url) ?: return@withContext UpdateCheckResult.NetworkError
                if (manifest.versionCode > currentVersionCode) {
                    UpdateCheckResult.Available(manifest)
                } else {
                    UpdateCheckResult.UpToDate
                }
            }

        override suspend fun download(manifest: UpdateManifest): UpdateDownloadResult =
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(manifest.apkUrl).get().build()
                runCatching {
                    httpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext UpdateDownloadResult.DownloadError
                        val body = response.body ?: return@withContext UpdateDownloadResult.DownloadError
                        val updateDir = File(context.cacheDir, UpdateCacheDir).apply { mkdirs() }
                        val apk = File(updateDir, manifest.apkFileName())
                        apk.outputStream().use { output ->
                            body.byteStream().use { input -> input.copyTo(output) }
                        }
                        if (!apk.sha256Matches(manifest.apkSha256)) {
                            apk.delete()
                            return@withContext UpdateDownloadResult.InvalidChecksum
                        }
                        UpdateDownloadResult.Success(apk)
                    }
                }.getOrElse { UpdateDownloadResult.DownloadError }
            }

        private fun fetchManifest(url: String): UpdateManifest? {
            val request = Request.Builder().url(url).get().build()
            return runCatching {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    response.body?.string()?.toUpdateManifest()
                }
            }.getOrNull()
        }

        private fun String.toUpdateManifest(): UpdateManifest {
            val json = JSONObject(this)
            return UpdateManifest(
                versionCode = json.getInt("versionCode"),
                versionName = json.getString("versionName"),
                apkUrl = json.getString("apkUrl"),
                apkSha256 = json.getString("apkSha256"),
                releaseNotes = json.optString("releaseNotes"),
            )
        }

        private fun File.sha256Matches(expected: String): Boolean {
            val digest = MessageDigest.getInstance("SHA-256")
            inputStream().use { input ->
                val buffer = ByteArray(BufferSizeBytes)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            val actual = digest.digest().joinToString(separator = "") { "%02x".format(it) }
            return actual.equals(expected.trim(), ignoreCase = true)
        }

        private companion object {
            fun UpdateManifest.apkFileName(): String = apkUrl.substringAfterLast('/').ifBlank { DefaultUpdateApkName }

            const val UpdateCacheDir = "updates"
            const val DefaultUpdateApkName = "dev-update.apk"
            const val BufferSizeBytes = 8 * 1024
        }
    }
