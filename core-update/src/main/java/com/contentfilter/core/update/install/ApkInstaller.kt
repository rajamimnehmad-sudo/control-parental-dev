package com.contentfilter.core.update.install

import java.io.File

interface ApkInstaller {
    fun canRequestPackageInstalls(): Boolean

    fun openInstallPermissionSettings()

    fun openPackageInstaller(apk: File)
}
