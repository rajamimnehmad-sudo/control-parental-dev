package com.contentfilter.core.update.config

import android.content.Context
import com.contentfilter.core.update.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class BuildConfigUpdateConfigProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : UpdateConfigProvider {
        override fun manifestUrl(): String = appSpecificManifestUrl().ifBlank { BuildConfig.UPDATE_MANIFEST_URL }

        override fun adminManifestUrl(): String =
            BuildConfig.UPDATE_MANIFEST_URL_ADMIN.ifBlank { BuildConfig.UPDATE_MANIFEST_URL }

        private fun appSpecificManifestUrl(): String =
            if (context.packageName.contains(AdminPackageMarker)) {
                BuildConfig.UPDATE_MANIFEST_URL_ADMIN
            } else {
                BuildConfig.UPDATE_MANIFEST_URL_USER
            }

        private companion object {
            const val AdminPackageMarker = ".admin"
        }
    }
