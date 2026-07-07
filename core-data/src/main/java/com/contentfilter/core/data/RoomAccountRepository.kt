package com.contentfilter.core.data

import com.contentfilter.core.database.dao.AccountDao
import com.contentfilter.core.domain.model.AccountContext
import com.contentfilter.core.domain.repository.AccountRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class RoomAccountRepository
    @Inject
    constructor(
        private val accountDao: AccountDao,
    ) : AccountRepository {
        override fun observeAccount(accountId: String): Flow<AccountContext?> =
            accountDao.observeAccount(accountId).map { it?.toDomain() }

        override suspend fun saveAccount(account: AccountContext) {
            accountDao.upsert(account.toEntity())
        }
    }
