package com.contentfilter.core.update.model

data class UpdateManifest(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val apkSha256: String,
    val releaseNotes: String,
)
