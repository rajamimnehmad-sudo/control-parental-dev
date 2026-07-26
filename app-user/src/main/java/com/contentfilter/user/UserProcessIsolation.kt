package com.contentfilter.user

internal object UserProcessIsolation {
    const val DagV2DataDirectorySuffix = "dag2"

    fun isDagV2Process(
        packageName: String,
        processName: String,
    ): Boolean = processName == "$packageName:dag2"

    fun shouldStartPrimaryProcessWork(
        packageName: String,
        processName: String,
    ): Boolean = !isDagV2Process(packageName, processName)
}
