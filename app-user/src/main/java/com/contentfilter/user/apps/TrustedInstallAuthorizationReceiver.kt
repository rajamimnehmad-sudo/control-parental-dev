package com.contentfilter.user.apps

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.contentfilter.core.domain.repository.ProtectionStateStore
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class TrustedInstallAuthorizationReceiver : BroadcastReceiver() {
    @Inject lateinit var protectionStateStore: ProtectionStateStore

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        if (intent.action != TrustedInstallAction) return
        protectionStateStore.authorizeTrustedInstall(System.currentTimeMillis() + TrustedInstallWindowMillis)
    }

    private companion object {
        const val TrustedInstallAction = "com.contentfilter.action.AUTHORIZE_TRUSTED_INSTALL"
        const val TrustedInstallWindowMillis = 2 * 60_000L
    }
}
