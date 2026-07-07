package com.contentfilter.core.data

import com.contentfilter.core.database.entity.AccountEntity
import com.contentfilter.core.domain.model.AccountContext

internal fun AccountEntity.toDomain(): AccountContext =
    AccountContext(
        id = id,
        name = name,
        communityId = communityId,
        communityName = communityName,
        guideName = guideName,
    )

internal fun AccountContext.toEntity(): AccountEntity =
    AccountEntity(
        id = id,
        name = name,
        communityId = communityId,
        communityName = communityName,
        guideName = guideName,
    )
