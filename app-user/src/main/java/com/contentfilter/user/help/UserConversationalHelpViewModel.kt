package com.contentfilter.user.help

import androidx.lifecycle.ViewModel
import com.contentfilter.core.domain.help.HelpContext
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class UserConversationalHelpViewModel
    @Inject
    constructor(
        private val modelManager: GloshiaLocalModelManager,
    ) : ViewModel() {
        val modelState: StateFlow<GloshiaModelState> = modelManager.state

        fun prepareModel() {
            modelManager.prepare()
        }

        suspend fun generate(
            prompt: String,
            context: HelpContext,
            reliableAnswer: String,
        ): String? =
            runCatching {
                modelManager.generate(
                    prompt = prompt,
                    context = context,
                    reliableAnswer = reliableAnswer,
                )
            }.getOrNull()
    }
