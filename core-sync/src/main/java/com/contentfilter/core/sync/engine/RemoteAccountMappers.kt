package com.contentfilter.core.sync.engine

import com.contentfilter.core.database.entity.AccountEntity
import com.contentfilter.core.network.dto.RemoteAccountDto

internal fun RemoteAccountDto.toEntity(): AccountEntity =
    AccountEntity(
        id = id,
        name = name,
        communityId = communityId,
        communityName = communityName,
        guideName = guideName,
    )
