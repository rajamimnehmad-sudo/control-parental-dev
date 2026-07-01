package com.contentfilter.core.update

import com.contentfilter.core.domain.model.UpdateState
import kotlinx.coroutines.flow.Flow

/**
 * Exposes update state. APK download and installation are implemented in later phases.
 */
interface UpdateGateway {
    fun observeUpdateState(): Flow<UpdateState>
}
