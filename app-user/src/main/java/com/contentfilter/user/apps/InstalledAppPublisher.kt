package com.contentfilter.user.apps

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Base64
import com.contentfilter.core.domain.model.DeviceActivation
import com.contentfilter.core.network.dto.RemoteInstalledAppDto
import com.contentfilter.core.network.remote.RemoteInstalledAppRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

class InstalledAppPublisher
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val remoteInstalledAppRepository: RemoteInstalledAppRepository,
    ) {
        suspend fun publish(activation: DeviceActivation) = publish(activation, installedApps())

        suspend fun publish(
            activation: DeviceActivation,
            apps: List<DetectedApp>,
        ) = withContext(Dispatchers.IO) {
            val now = Instant.now().toString()
            remoteInstalledAppRepository.upsertInstalledApps(
                apps.map { app ->
                    RemoteInstalledAppDto(
                        id = stableId(activation.deviceId, app.packageName),
                        accountId = activation.accountId,
                        deviceId = activation.deviceId,
                        appName = app.name,
                        packageName = app.packageName,
                        versionName = app.versionName,
                        isSystemApp = app.isSystemApp,
                        iconBase64 = app.iconBase64,
                        updatedAt = now,
                    )
                },
            )
        }

        fun installedApps(): List<DetectedApp> {
            val packageManager = context.packageManager
            return packageManager.installedPackages()
                .asSequence()
                .mapNotNull { it.applicationInfo?.let { app -> it to app } }
                .filter { (_, app) -> packageManager.getLaunchIntentForPackage(app.packageName) != null }
                .filterNot { (_, app) -> app.packageName.isAlwaysAllowedPackage() }
                .map { (info, app) ->
                    DetectedApp(
                        name = app.loadLabel(packageManager).toString().ifBlank { app.packageName },
                        packageName = app.packageName,
                        versionName = info.versionName,
                        isSystemApp = app.isSystemApp(),
                        iconBase64 = runCatching { app.loadIcon(packageManager).toBase64Png() }.getOrNull(),
                    )
                }
                .distinctBy { it.packageName }
                .sortedWith(compareBy({ it.name.lowercase() }, { it.packageName }))
                .toList()
        }

        private fun stableId(
            deviceId: String,
            packageName: String,
        ): String = UUID.nameUUIDFromBytes("$deviceId:$packageName".toByteArray(StandardCharsets.UTF_8)).toString()

        data class DetectedApp(
            val name: String,
            val packageName: String,
            val versionName: String?,
            val isSystemApp: Boolean,
            val iconBase64: String?,
        )

        private fun PackageManager.installedPackages(): List<PackageInfo> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getInstalledPackages(PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong()))
            } else {
                @Suppress("DEPRECATION")
                getInstalledPackages(PackageManager.GET_META_DATA)
            }

        private fun ApplicationInfo.isSystemApp(): Boolean =
            flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

        private fun Drawable.toBase64Png(): String {
            val bitmap = Bitmap.createBitmap(IconSizePx, IconSizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            setBounds(0, 0, canvas.width, canvas.height)
            draw(canvas)
            val output = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, IconPngQuality, output)
            return Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        }

        private companion object {
            const val IconSizePx = 96
            const val IconPngQuality = 90
            val ExactAllowedPackageNames =
                setOf(
                    "android",
                    "com.android.contacts",
                    "com.android.dialer",
                    "com.android.packageinstaller",
                    "com.android.permissioncontroller",
                    "com.android.phone",
                    "com.android.providers.downloads",
                    "com.android.settings",
                    "com.contentfilter.admin",
                    "com.contentfilter.admin.dev",
                    "com.contentfilter.admin.beta",
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
                    "com.android.vending",
                )
            val AllowedPackagePrefixes =
                listOf(
                    "com.android.launcher",
                    "com.google.android.apps.nexuslauncher",
                    "com.google.android.inputmethod",
                    "com.google.android.webview",
                )

            fun String.isAlwaysAllowedPackage(): Boolean =
                this in ExactAllowedPackageNames ||
                    AllowedPackagePrefixes.any { startsWith(it) } ||
                    endsWith(".launcher")
        }
    }
