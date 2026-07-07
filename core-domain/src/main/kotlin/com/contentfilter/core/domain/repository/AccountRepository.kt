package com.contentfilter.core.domain.repository

import com.contentfilter.core.domain.model.AccountContext
import kotlinx.coroutines.flow.Flow

interface AccountRepository {
    fun observeAccount(accountId: String): Flow<AccountContext?>

    suspend fun saveAccount(account: AccountContext)
}
