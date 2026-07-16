package com.contentfilter.core.update.config

interface UpdateConfigProvider {
    fun manifestUrl(): String

    fun adminManifestUrl(): String
}
