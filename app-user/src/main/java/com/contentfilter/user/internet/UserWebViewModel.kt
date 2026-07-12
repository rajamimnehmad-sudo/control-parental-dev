package com.contentfilter.user.internet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.WebProtectionSemantics
import com.contentfilter.core.domain.model.externalSearchResultsAllowed
import com.contentfilter.core.domain.model.safeSearchEnabled
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
                        "webNavigation user snapshot policy=${snapshot.id.take(8)} version=${snapshot.version} " +
                            "webNavigationBlocked=$blocked " +
                            "externalSearchResultsAllowed=${snapshot.rules.externalSearchResultsAllowed()} " +
                            "safeSearch=${snapshot.rules.safeSearchEnabled()}",
                    )
                    UserWebUiState(
                        webNavigationBlocked = blocked,
                        externalSearchResultsAllowed = snapshot.rules.externalSearchResultsAllowed(),
                        safeSearchEnabled = snapshot.rules.safeSearchEnabled(),
                    )
                }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = UserWebUiState(),
                )
    }

data class UserWebUiState(
    val webNavigationBlocked: Boolean = false,
    val externalSearchResultsAllowed: Boolean = true,
    val safeSearchEnabled: Boolean = true,
) {
    val onlyResultsEnabled: Boolean
        get() = WebProtectionSemantics.onlyResultsEnabled(externalSearchResultsAllowed)

    val activeLayers: List<String>
        get() =
            buildList {
                if (safeSearchEnabled) add("SafeSearch")
                if (onlyResultsEnabled) add("Solo resultados")
            }
}

private const val LogTag = "UserWebViewModel"
