package com.contentfilter.feature.activation

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class InstalledAppVersionProvider
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun versionCode(): Int =
            context.packageManager
                .getPackageInfo(context.packageName, 0)
                .longVersionCode
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
    }
