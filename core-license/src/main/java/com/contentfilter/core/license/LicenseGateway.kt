package com.contentfilter.core.license

import com.contentfilter.core.domain.model.LicenseState
import kotlinx.coroutines.flow.Flow

/**
 * Reads local license state without knowing the future payment provider.
 */
interface LicenseGateway {
    fun observeLicenseState(): Flow<LicenseState>
}
