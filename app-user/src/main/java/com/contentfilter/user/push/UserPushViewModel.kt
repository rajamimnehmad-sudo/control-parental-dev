package com.contentfilter.user.push

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UserPushViewModel
    @Inject
    constructor(
        private val registrar: UserPushRegistrar,
    ) : ViewModel() {
        fun registerIfReady() = registrar.registerIfReady()
    }
