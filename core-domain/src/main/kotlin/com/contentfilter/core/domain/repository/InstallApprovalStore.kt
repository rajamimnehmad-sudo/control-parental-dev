package com.contentfilter.core.domain.repository

interface InstallApprovalStore {
    fun initializeBaseline(packageNames: Set<String>)

    fun isKnown(packageName: String): Boolean

    fun isPending(packageName: String): Boolean

    fun markPending(packageName: String)

    fun markApproved(packageName: String)

    fun remove(packageName: String)
}
