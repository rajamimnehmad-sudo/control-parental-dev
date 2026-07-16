package com.contentfilter.core.update.install

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

class AndroidApkInstaller
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
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

        @Suppress("DEPRECATION")
        private fun withTrustedInstallAuthorization(action: () -> Unit) {
            val receiver =
                object : BroadcastReceiver() {
                    override fun onReceive(
                        context: Context?,
                        intent: Intent?,
                    ) = action()
                }
            context.sendOrderedBroadcast(
                Intent(TrustedInstallAction).setPackage(userPackageName()),
                TrustedInstallPermission,
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
            const val TrustedInstallPermission = "com.contentfilter.permission.AUTHORIZE_TRUSTED_INSTALL"
        }
    }
