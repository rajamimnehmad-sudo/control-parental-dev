package com.contentfilter.feature.vpn.policy

import com.contentfilter.core.domain.model.LicenseState

internal fun LicenseState.isActivated(): Boolean = this != LicenseState.PendingActivation
