package com.contentfilter.user.internet

import android.content.Context
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
import com.contentfilter.feature.vpn.domainlist.WebDomainListState
import com.contentfilter.feature.vpn.domainlist.WebDomainListStatus
import com.contentfilter.feature.vpn.domainlist.WebDomainListStore
import com.contentfilter.feature.vpn.domainlist.WebDomainListUpdater
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class UserWebViewModel
    @Inject
    constructor(
        @ApplicationContext context: Context,
        policyRepository: PolicyRepository,
        systemStatusRepository: SystemStatusRepository,
        private val domainListUpdater: WebDomainListUpdater,
        domainListStore: WebDomainListStore,
    ) : ViewModel() {
        private val checkingDomainList = MutableStateFlow(false)

        val uiState =
            combine(
                policyRepository.observeActivePolicy(),
                domainListStore.observeStatus(),
                checkingDomainList,
                systemStatusRepository.observeHealth(),
            ) { snapshot, domainListStatus, isCheckingDomainList, health ->
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
                    showDomainListDiagnostics = context.packageName.endsWith(".dev"),
                    domainList = domainListStatus.toUiState(isCheckingDomainList),
                )
            }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = UserWebUiState(),
                )

        fun checkDomainListNow() {
            if (checkingDomainList.value) return
            viewModelScope.launch {
                checkingDomainList.value = true
                try {
                    domainListUpdater.refreshIfDue(force = true)
                } finally {
                    checkingDomainList.value = false
                }
            }
        }
    }

data class UserWebUiState(
    val webNavigationBlocked: Boolean = false,
    val externalSearchResultsAllowed: Boolean = true,
    val safeSearchEnabled: Boolean = true,
    val dagEnabled: Boolean = false,
    val dagEntitled: Boolean = false,
    val showDomainListDiagnostics: Boolean = false,
    val domainList: DomainListUiState = DomainListUiState(),
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

data class DomainListUiState(
    val version: Long = 0L,
    val installedAtEpochMillis: Long = 0L,
    val status: String = "Sin base",
    val lastCheckAtEpochMillis: Long = 0L,
    val lastCheckResult: String = "Todavia no comprobada",
    val canaryIncluded: Boolean = false,
    val lastError: String? = null,
    val isChecking: Boolean = false,
)

internal fun WebDomainListStatus.toUiState(isChecking: Boolean): DomainListUiState =
    DomainListUiState(
        version = version,
        installedAtEpochMillis = installedAtEpochMillis,
        status =
            when (state) {
                WebDomainListState.Active -> "Activa"
                WebDomainListState.Error -> if (version > 0L) "Error, version anterior activa" else "Error"
                WebDomainListState.NoBase -> "Sin base"
            },
        lastCheckAtEpochMillis = lastCheckAtEpochMillis,
        lastCheckResult = lastCheckResult,
        canaryIncluded = canaryIncluded,
        lastError = lastError?.sanitizedDomainListError(),
        isChecking = isChecking,
    )

private fun String.sanitizedDomainListError(): String =
    when (this) {
        "manifest-download" -> "No se pudo descargar el manifiesto"
        "data-download" -> "No se pudo descargar la base"
        "environment" -> "El ambiente de la base no coincide"
        "size" -> "El tamano descargado no coincide"
        "checksum" -> "El checksum no coincide"
        "data-signature" -> "La firma de la base no es valida"
        else -> "No se pudo verificar la actualizacion"
    }

private const val LogTag = "UserWebViewModel"
