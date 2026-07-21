package com.contentfilter.user.internet

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.WebProtectionSemantics
import com.contentfilter.core.domain.model.dagEnabled
import com.contentfilter.core.domain.model.externalSearchResultsAllowed
import com.contentfilter.core.domain.model.safeSearchEnabled
import com.contentfilter.core.domain.model.webNavigationBlocked
import com.contentfilter.core.domain.repository.PolicyRepository
import com.contentfilter.core.domain.repository.SystemStatusRepository
import com.contentfilter.user.dag.DagLauncherController
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import javax.inject.Inject

@HiltViewModel
class UserWebViewModel
    @Inject
    constructor(
        policyRepository: PolicyRepository,
        systemStatusRepository: SystemStatusRepository,
        private val dagLauncherController: DagLauncherController,
    ) : ViewModel() {
        val uiState =
            combine(
                policyRepository.observeActivePolicy(),
                systemStatusRepository.observeHealth(),
                minuteTicks(),
                dagLauncherController.keepSeparateLauncher,
            ) { snapshot, health, nowEpochMillis, keepSeparateLauncher ->
                val blocked = snapshot.rules.webNavigationBlocked()
                Log.i(
                    LogTag,
                    "webNavigation user snapshot policy=${snapshot.id.take(8)} version=${snapshot.version} " +
                        "webNavigationBlocked=$blocked " +
                        "externalSearchResultsAllowed=${snapshot.rules.externalSearchResultsAllowed()} " +
                        "safeSearch=${snapshot.rules.safeSearchEnabled()} " +
                        "dagEnabled=${snapshot.rules.dagEnabled()}",
                )
                UserWebUiState(
                    webNavigationBlocked = blocked,
                    externalSearchResultsAllowed = snapshot.rules.externalSearchResultsAllowed(),
                    safeSearchEnabled = snapshot.rules.safeSearchEnabled(),
                    dagEnabled = health.dagEntitled && snapshot.rules.dagEnabled(),
                    dagEntitled = health.dagEntitled,
                    keepSeparateDagLauncher = keepSeparateLauncher,
                    schedule = resolveWebScheduleStatus(snapshot.rules, nowEpochMillis),
                )
            }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = UserWebUiState(),
                )

        fun setKeepSeparateDagLauncher(enabled: Boolean) {
            dagLauncherController.setKeepSeparateLauncher(enabled)
        }
    }

data class UserWebUiState(
    val webNavigationBlocked: Boolean = false,
    val externalSearchResultsAllowed: Boolean = true,
    val safeSearchEnabled: Boolean = true,
    val dagEnabled: Boolean = false,
    val dagEntitled: Boolean = false,
    val keepSeparateDagLauncher: Boolean = true,
    val schedule: WebScheduleStatus? = null,
) {
    val onlyResultsEnabled: Boolean
        get() = WebProtectionSemantics.onlyResultsEnabled(externalSearchResultsAllowed)

    val activeLayers: List<String>
        get() =
            buildList {
                if (safeSearchEnabled) add("SafeSearch")
                if (onlyResultsEnabled) add("Solo resultados")
                if (dagEnabled) add("DAG")
            }
}

private fun minuteTicks() =
    flow {
        while (currentCoroutineContext().isActive) {
            val now = System.currentTimeMillis()
            emit(now)
            delay(MillisPerMinute - now % MillisPerMinute)
        }
    }

private const val LogTag = "UserWebViewModel"
private const val MillisPerMinute = 60_000L
