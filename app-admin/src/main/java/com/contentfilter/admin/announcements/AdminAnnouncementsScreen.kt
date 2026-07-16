package com.contentfilter.admin.announcements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.network.remote.RemoteAnnouncement
import com.contentfilter.core.network.remote.RemoteAnnouncementRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.ui.ProductCard
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
fun AdminAnnouncementsRoute(viewModel: AdminAnnouncementsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    AnnouncementInbox(state, viewModel::refresh)
}

@Composable
private fun AnnouncementInbox(
    state: AdminAnnouncementsState,
    onRefresh: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onRefresh, modifier = Modifier.fillMaxWidth(), enabled = !state.loading) {
            Text(if (state.loading) "Actualizando..." else "Actualizar avisos")
        }
        if (state.message.isNotBlank()) Text(state.message)
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
}

@HiltViewModel
class AdminAnnouncementsViewModel
    @Inject
    constructor(
        private val repository: RemoteAnnouncementRepository,
    ) : ViewModel() {
        private val mutableState = MutableStateFlow(AdminAnnouncementsState())
        val state = mutableState.asStateFlow()

        init {
            refresh()
        }

        fun refresh() {
            if (mutableState.value.loading) return
            mutableState.update { it.copy(loading = true, message = "") }
            viewModelScope.launch(Dispatchers.IO) {
                when (val result = repository.listRecent()) {
                    is RemoteResult.Success -> mutableState.value = AdminAnnouncementsState(items = result.value, message = if (result.value.isEmpty()) "No hay avisos." else "")
                    is RemoteResult.Failure -> mutableState.value = AdminAnnouncementsState(message = "No se pudieron actualizar los avisos.")
                }
            }
        }
    }

data class AdminAnnouncementsState(
    val items: List<RemoteAnnouncement> = emptyList(),
    val loading: Boolean = false,
    val message: String = "",
)
