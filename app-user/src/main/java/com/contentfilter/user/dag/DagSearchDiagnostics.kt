package com.contentfilter.user.dag

internal enum class DagSearchDecisionReason {
    Allowed,
    Uncertain,
    DomainListBlock,
    AdminRuleBlock,
    PlatformBlock,
    LocalClassifierBlock,
}

internal data class DagClassifiedSearchResult(
    val classification: DagClassificationResult,
    val reason: DagSearchDecisionReason,
)

internal data class DagSearchDiagnostics(
    val braveReceived: Int,
    val serverRejected: Int,
    val domainListBlocked: Int,
    val adminRuleBlocked: Int,
    val platformBlocked: Int,
    val localClassifierBlocked: Int,
    val uncertainShown: Int,
    val allowedShown: Int,
) {
    val shown: Int get() = uncertainShown + allowedShown
    val accounted: Int
        get() =
            serverRejected + domainListBlocked + adminRuleBlocked + platformBlocked +
                localClassifierBlocked + shown
}

internal fun dagSearchDiagnostics(
    braveReceived: Int,
    serverRejected: Int,
    decisions: List<DagSearchDecisionReason>,
): DagSearchDiagnostics =
    DagSearchDiagnostics(
        braveReceived = braveReceived,
        serverRejected = serverRejected,
        domainListBlocked = decisions.count { it == DagSearchDecisionReason.DomainListBlock },
        adminRuleBlocked = decisions.count { it == DagSearchDecisionReason.AdminRuleBlock },
        platformBlocked = decisions.count { it == DagSearchDecisionReason.PlatformBlock },
        localClassifierBlocked = decisions.count { it == DagSearchDecisionReason.LocalClassifierBlock },
        uncertainShown = decisions.count { it == DagSearchDecisionReason.Uncertain },
        allowedShown = decisions.count { it == DagSearchDecisionReason.Allowed },
    )
