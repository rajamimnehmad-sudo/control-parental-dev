package com.contentfilter.user.internet

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
                    UserWebUiState(webNavigationBlocked = snapshot.rules.webNavigationBlocked())
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
