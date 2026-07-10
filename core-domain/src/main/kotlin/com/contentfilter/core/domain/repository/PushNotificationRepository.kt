package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.ProtectionAlertType

interface PushNotificationRepository {
    suspend fun registerAdminToken(token: String)

    suspend fun reportProtectionAlert(type: ProtectionAlertType)
}
