package com.contentfilter.user.internet

data class InternetUiState(
    val domainInput: String = "",
    val recentBlockedDomains: List<BlockedDomainUiState> = emptyList(),
    val pendingDomains: Set<String> = emptySet(),
    val message: String = "",
    val isSending: Boolean = false,
)

data class BlockedDomainUiState(
    val domain: String,
    val pending: Boolean,
)
