package com.contentfilter.user.announcements

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.network.remote.RemoteAnnouncement
import com.contentfilter.core.network.remote.RemoteAnnouncementRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.ui.PremiumFeedbackBanner
import com.contentfilter.core.ui.ProductCard
import com.contentfilter.core.ui.ProductLazyVisualPage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import javax.inject.Inject

@Composable
fun UserAnnouncementsRoute(
    onBack: () -> Unit,
    viewModel: UserAnnouncementsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ProductLazyVisualPage(
        title = "Avisos",
        subtitle = "Mensajes de tu comunidad",
        onBack = onBack,
        banner =
            if (state.message.isNotBlank()) {
                {
                    PremiumFeedbackBanner(
                        text = state.message,
                        isError = state.message.startsWith("No se pudo"),
                    )
                }
            } else {
                null
            },
    ) {
        item {
            OutlinedButton(onClick = viewModel::refresh, modifier = Modifier.fillMaxWidth(), enabled = !state.loading) {
                Text(if (state.loading) "Actualizando..." else "Actualizar avisos")
            }
        }
        items(state.items, key = RemoteAnnouncement::id) { item ->
            ProductCard {
                Text(item.title, style = MaterialTheme.typography.titleMedium)
                Text(item.body, style = MaterialTheme.typography.bodyMedium)
                Text(
                    DateFormat.getDateTimeInstance().format(Date.from(item.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@HiltViewModel
class UserAnnouncementsViewModel
    @Inject
    constructor(
        private val repository: RemoteAnnouncementRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(UserAnnouncementsState())
        val state = mutableState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            if (mutableState.value.loading) return
            mutableState.update { it.copy(loading = true, message = "") }
            viewModelScope.launch(Dispatchers.IO) {
                when (val result = repository.listRecent()) {
                    is RemoteResult.Success -> mutableState.value = UserAnnouncementsState(items = result.value, message = if (result.value.isEmpty()) "No hay avisos." else "")
                    is RemoteResult.Failure -> mutableState.value = UserAnnouncementsState(message = "No se pudieron actualizar los avisos.")
                }
            }
        }
    }

data class UserAnnouncementsState(
    val items: List<RemoteAnnouncement> = emptyList(),
    val loading: Boolean = false,
    val message: String = "",
)
