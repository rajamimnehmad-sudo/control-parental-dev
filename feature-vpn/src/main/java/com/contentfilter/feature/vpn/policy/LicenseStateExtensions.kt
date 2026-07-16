package com.contentfilter.feature.vpn.policy

import com.contentfilter.core.domain.model.LicenseState
import com.contentfilter.core.domain.model.allowsProtection

internal fun LicenseState.isActivated(): Boolean = allowsProtection()
