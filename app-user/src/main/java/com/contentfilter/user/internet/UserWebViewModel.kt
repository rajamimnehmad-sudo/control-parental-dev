package com.contentfilter.user.internet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.webNavigationBlocked
import com.contentfilter.core.domain.repository.PolicyRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class UserWebViewModel
    @Inject
    constructor(
        policyRepository: PolicyRepository,
    ) : ViewModel() {
        val uiState =
            policyRepository
                .observeActivePolicy()
                .map { snapshot ->
                    val blocked = snapshot.rules.webNavigationBlocked()
                    Log.i(
                        LogTag,
                        "webNavigation user snapshot policy=${snapshot.id} version=${snapshot.version} blocked=$blocked",
                    )
                    UserWebUiState(webNavigationBlocked = blocked)
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = UserWebUiState(),
                )
    }

data class UserWebUiState(
    val webNavigationBlocked: Boolean = false,
)

private const val LogTag = "UserWebViewModel"
