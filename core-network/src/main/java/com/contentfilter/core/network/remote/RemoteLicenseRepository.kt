package com.contentfilter.core.network.remote

import com.contentfilter.core.domain.model.LicenseEntitlement

interface RemoteLicenseRepository {
    suspend fun getDeviceEntitlement(deviceId: String): RemoteResult<LicenseEntitlement>
}
