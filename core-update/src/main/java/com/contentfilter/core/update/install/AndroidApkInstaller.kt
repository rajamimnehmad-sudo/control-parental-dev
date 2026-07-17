package com.contentfilter.core.update.install

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.contentfilter.core.domain.repository.ProtectionStateStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class AndroidApkInstaller
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val protectionStateStore: ProtectionStateStore,
    ) : ApkInstaller {
        override fun canRequestPackageInstalls(): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()

        override fun openInstallPermissionSettings() {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val intent =
                Intent(
                    Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                    Uri.parse("package:${context.packageName}"),
                ).withNewTask()
            withTrustedInstallAuthorization {
                runCatching {
                    context.startActivity(intent)
                }.onFailure { exception ->
                    Log.e(LogTag, "Could not open install permission settings: ${exception.message}", exception)
                }
            }
        }

        override fun openPackageInstaller(apk: File) {
            withTrustedInstallAuthorization {
                runCatching {
                    val uri =
                        FileProvider.getUriForFile(
                            context,
                            "${context.packageName}.update.fileprovider",
                            apk,
                        )
                    val intent =
                        Intent(Intent.ACTION_INSTALL_PACKAGE)
                            .setData(uri)
                            .putExtra(Intent.EXTRA_NOT_UNKNOWN_SOURCE, true)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            .withNewTask()
                    context.startActivity(intent)
                }.onFailure { exception ->
                    Log.e(LogTag, "Could not open package installer: ${exception.message}", exception)
                }
            }
        }

        override fun openVerifiedCompanionInstaller(
            apk: File,
            expectedPackageName: String,
        ): Boolean {
            if (!isExpectedSignedApk(apk, expectedPackageName)) return false
            openPackageInstaller(apk)
            return true
        }

        @Suppress("DEPRECATION")
        private fun isExpectedSignedApk(
            apk: File,
            expectedPackageName: String,
        ): Boolean {
            val flags = PackageManager.GET_SIGNING_CERTIFICATES
            val archive = context.packageManager.getPackageArchiveInfo(apk.absolutePath, flags) ?: return false
            val current = context.packageManager.getPackageInfo(context.packageName, flags)
            val archiveSigners = archive.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            val currentSigners = current.signingInfo?.apkContentsSigners?.map { it.toCharsString() }?.toSet().orEmpty()
            return isTrustedCompanionArchive(
                archivePackageName = archive.packageName,
                expectedPackageName = expectedPackageName,
                archiveSigners = archiveSigners,
                currentSigners = currentSigners,
            )
        }

        @Suppress("DEPRECATION")
        private fun withTrustedInstallAuthorization(action: () -> Unit) {
            val authorizationUntil = System.currentTimeMillis() + TrustedInstallWindowMillis
            if (context.packageName == userPackageName()) {
                protectionStateStore.authorizeTrustedInstall(authorizationUntil)
            }
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context?,
                        intent: Intent?,
                    ) = action()
                }
            context.sendOrderedBroadcast(
                Intent(TrustedInstallAction).setComponent(
                    ComponentName(
                        userPackageName(),
                        TrustedInstallReceiverClass,
                    ),
                ),
                null,
                receiver,
                null,
                Activity.RESULT_OK,
                null,
                null,
            )
        }

        private fun userPackageName(): String =
            when {
                context.packageName.endsWith(".dev") -> "com.contentfilter.user.dev"
                context.packageName.endsWith(".beta") -> "com.contentfilter.user.beta"
                else -> "com.contentfilter.user"
            }

        private fun Intent.withNewTask(): Intent = addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        private companion object {
            const val LogTag = "ApkInstaller"
            const val TrustedInstallAction = "com.contentfilter.action.AUTHORIZE_TRUSTED_INSTALL"
            const val TrustedInstallReceiverClass =
                "com.contentfilter.user.apps.TrustedInstallAuthorizationReceiver"
            const val TrustedInstallWindowMillis = 5 * 60_000L
        }
    }

internal fun isTrustedCompanionArchive(
    archivePackageName: String,
    expectedPackageName: String,
    archiveSigners: Set<String>,
    currentSigners: Set<String>,
): Boolean =
    archivePackageName == expectedPackageName &&
        archiveSigners.isNotEmpty() &&
        archiveSigners == currentSigners
