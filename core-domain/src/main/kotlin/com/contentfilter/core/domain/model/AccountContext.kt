package com.contentfilter.core.domain.model

data class AccountContext(
    val id: String,
    val name: String,
    val communityId: String?,
    val communityName: String,
    val guideName: String,
)
