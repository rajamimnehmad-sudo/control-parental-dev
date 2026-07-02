package com.contentfilter.admin.rules

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.domain.model.PolicyLevel
import com.contentfilter.core.domain.model.PolicyRule
import com.contentfilter.core.domain.model.RuleAction
import com.contentfilter.core.domain.model.RuleScope
import com.contentfilter.core.domain.usecase.admin.ObservePolicyRulesUseCase
import com.contentfilter.core.domain.usecase.admin.SavePolicyRuleUseCase
import com.contentfilter.core.sync.SyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class RulesViewModel
    @Inject
    constructor(
        observePolicyRules: ObservePolicyRulesUseCase,
        private val saveRule: SavePolicyRuleUseCase,
        private val syncScheduler: SyncScheduler,
    ) : ViewModel() {
        private val form = MutableStateFlow(
            RulesUiState(),
        )

        val uiState = combine(observePolicyRules(), form) { rules, formState ->
            formState.copy(
                rules = rules.sortedWith(compareBy({ it.scope.name }, { it.target })),
                offlineMode = false,
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = form.value,
        )

        fun onTargetChanged(value: String) {
            form.update { it.copy(target = value, message = "") }
        }

        fun onActionChanged(action: RuleAction) {
            form.update { it.copy(selectedAction = action) }
        }

        fun createAppRule() = createRule(RuleScope.App)

        fun createDomainRule() = createRule(RuleScope.Domain)

        fun toggle(rule: PolicyRule) {
            viewModelScope.launch {
                saveRule(rule.copy(enabled = !rule.enabled))
                syncScheduler.requestSync()
            }
        }

        private fun createRule(scope: RuleScope) {
            val state = form.value
            val target = normalizeTarget(scope, state.target)
            if (target == null) {
                form.update {
                    it.copy(
                        message = when (scope) {
                            RuleScope.App -> "Ingresá un paquete válido, por ejemplo com.android.chrome."
                            RuleScope.Domain -> "Ingresá solo un dominio, por ejemplo example.com. No pegues URL completa."
                            else -> "Objetivo inválido."
                        },
                    )
                }
                return
            }
            viewModelScope.launch {
                saveRule(
                    PolicyRule(
                        id = UUID.randomUUID().toString(),
                        level = PolicyLevel.Account,
                        scope = scope,
                        target = target,
                        action = state.selectedAction,
                        priority = 100,
                        enabled = true,
                    ),
                )
                syncScheduler.requestSync()
                form.update { it.copy(target = "", message = "Regla guardada.") }
            }
        }

        private fun normalizeTarget(
            scope: RuleScope,
            rawTarget: String,
        ): String? {
            val trimmed = rawTarget.trim()
            return when (scope) {
                RuleScope.App -> trimmed.takeIf { PackageNameRegex.matches(it) }
                RuleScope.Domain -> trimmed
                    .lowercase()
                    .takeIf { "://" !in it && "/" !in it && DomainRegex.matches(it) }
                else -> null
            }
        }

        private companion object {
            val PackageNameRegex = Regex("^[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+$")
            val DomainRegex = Regex("^(?=.{1,253}$)([a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+[a-z]{2,63}$")
        }
    }
