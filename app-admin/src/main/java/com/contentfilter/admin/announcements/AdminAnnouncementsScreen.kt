package com.contentfilter.admin.announcements

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.contentfilter.core.network.remote.RemoteAnnouncement
import com.contentfilter.core.network.remote.RemoteAnnouncementRepository
import com.contentfilter.core.network.remote.RemoteResult
import com.contentfilter.core.ui.PremiumFeedbackBanner
import com.contentfilter.core.ui.ProductListRow
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
    LaunchedEffect(Unit) { viewModel.refresh() }
    LaunchedEffect(state.loading, state.unreadCount) {
        if (!state.loading && state.unreadCount > 0) viewModel.markAllRead()
    }
    AnnouncementInbox(
        state = state,
        onDismiss = viewModel::dismiss,
        onUndo = viewModel::undoDismiss,
    )
}

@Composable
private fun AnnouncementInbox(
    state: AdminAnnouncementsState,
    onDismiss: (RemoteAnnouncement) -> Unit,
    onUndo: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().background(Color.White).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (state.message.isNotBlank()) {
            PremiumFeedbackBanner(
                text = state.message,
                isError = state.message.startsWith("No se pudo"),
                actionLabel = if (state.undoAnnouncement != null) "Deshacer" else null,
                onAction = if (state.undoAnnouncement != null) onUndo else null,
            )
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(0.dp)) {
            items(state.items, key = RemoteAnnouncement::id) { item ->
                AnnouncementSwipeRow(item = item, onDismiss = { onDismiss(item) })
            }
        }
    }
}

@Composable
private fun AnnouncementSwipeRow(
    item: RemoteAnnouncement,
    onDismiss: () -> Unit,
) {
    val dismissState =
        rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                if (value == SwipeToDismissBoxValue.EndToStart) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        )
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFFC62828))
                        .padding(end = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar aviso", tint = Color.White)
            }
        },
    ) {
        ProductListRow(
            leading = {
                Box(
                    modifier =
                        Modifier
                            .size(9.dp)
                            .clip(RoundedCornerShape(50))
                            .background(
                                if (item.isRead) Color.Transparent else MaterialTheme.colorScheme.primary,
                            ),
                )
            },
            headline = {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold,
                )
            },
            supporting = {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = item.body,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (item.isRead) FontWeight.Normal else FontWeight.Medium,
                    )
                    Text(
                        DateFormat.getDateTimeInstance().format(Date.from(item.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
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
                    is RemoteResult.Success ->
                        mutableState.value =
                            AdminAnnouncementsState(
                                items = result.value,
                                unreadCount = result.value.count { !it.isRead },
                                message = if (result.value.isEmpty()) "No hay avisos." else "",
                            )
                    is RemoteResult.Failure -> mutableState.value = AdminAnnouncementsState(message = "No se pudieron actualizar los avisos.")
                }
            }
        }

        fun markAllRead() {
            if (mutableState.value.markingRead || mutableState.value.unreadCount == 0) return
            mutableState.update { it.copy(markingRead = true) }
            viewModelScope.launch(Dispatchers.IO) {
                when (repository.markAllRead()) {
                    is RemoteResult.Success -> mutableState.update { it.copy(unreadCount = 0, markingRead = false) }
                    is RemoteResult.Failure ->
                        mutableState.update {
                            it.copy(markingRead = false, message = "No se pudieron marcar los avisos como leídos.")
                        }
                }
            }
        }

        fun dismiss(item: RemoteAnnouncement) {
            mutableState.update { state ->
                state.copy(
                    items = state.items.filterNot { it.id == item.id },
                    unreadCount = state.items.filterNot { it.id == item.id }.count { !it.isRead },
                    message = "Aviso eliminado.",
                    undoAnnouncement = item,
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                if (repository.dismiss(item.id) is RemoteResult.Failure) {
                    mutableState.update { state ->
                        state.copy(
                            items = (state.items + item).sortedByDescending(RemoteAnnouncement::createdAt),
                            unreadCount = (state.items + item).count { !it.isRead },
                            message = "No se pudo eliminar el aviso.",
                            undoAnnouncement = null,
                        )
                    }
                }
            }
        }

        fun undoDismiss() {
            val item = mutableState.value.undoAnnouncement ?: return
            val restored = item.copy(isRead = true)
            mutableState.update { state ->
                state.copy(
                    items =
                        (state.items + restored)
                            .distinctBy(RemoteAnnouncement::id)
                            .sortedByDescending(RemoteAnnouncement::createdAt),
                    message = "Aviso restaurado.",
                    undoAnnouncement = null,
                )
            }
            viewModelScope.launch(Dispatchers.IO) {
                if (repository.restore(item.id) is RemoteResult.Failure) {
                    mutableState.update { state ->
                        state.copy(
                            items = state.items.filterNot { it.id == item.id },
                            message = "No se pudo restaurar el aviso.",
                        )
                    }
                }
            }
        }
    }

data class AdminAnnouncementsState(
    val items: List<RemoteAnnouncement> = emptyList(),
    val unreadCount: Int = 0,
    val loading: Boolean = false,
    val markingRead: Boolean = false,
    val message: String = "",
    val undoAnnouncement: RemoteAnnouncement? = null,
)
