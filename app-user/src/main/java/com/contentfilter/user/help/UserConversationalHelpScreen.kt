package com.contentfilter.user.help

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.contentfilter.core.domain.help.AppHelpAssistant
import com.contentfilter.core.domain.help.HelpAction
import com.contentfilter.core.domain.help.HelpAnswer
import com.contentfilter.core.domain.help.HelpContext
import com.contentfilter.core.domain.help.HelpReportDraft
import com.contentfilter.core.ui.ProductAppBackground
import com.contentfilter.core.ui.ProductInk
import com.contentfilter.core.ui.ProductMutedInk
import com.contentfilter.core.ui.ProductPageHeader
import com.contentfilter.core.ui.ProductViolet
import kotlinx.coroutines.launch

@Composable
internal fun UserConversationalHelpScreen(
    context: HelpContext,
    modelState: GloshiaModelState,
    onPrepareModel: () -> Unit,
    onGenerate: suspend (String, HelpContext, String) -> String?,
    onBack: () -> Unit,
    onAction: (HelpAction) -> Unit,
    onAutomaticReport: (HelpReportDraft) -> Unit,
) {
    var question by rememberSaveable { mutableStateOf("") }
    val messages = remember { mutableStateListOf(UserHelpMessage.assistant(AppHelpAssistant.welcome(context))) }
    var suggestions by remember(context) { mutableStateOf(AppHelpAssistant.suggestions(context)) }
    var lastAction by remember { mutableStateOf<HelpAction?>(null) }
    var generating by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    fun submit(prompt: String) {
        if (prompt.isBlank() || generating) return
        val fallback = AppHelpAssistant.answer(prompt, context, previousAction = lastAction)
        messages += UserHelpMessage.user(prompt)
        question = ""

        fun finish(answer: HelpAnswer) {
            answer.report?.let(onAutomaticReport)
            suggestions = AppHelpAssistant.followUpSuggestions(answer, context)
            lastAction = answer.action ?: lastAction
        }

        if (!modelState.canGenerate) {
            messages += UserHelpMessage.assistant(fallback)
            finish(fallback)
            return
        }

        val pendingIndex = messages.size
        messages += UserHelpMessage.pending()
        generating = true
        scope.launch {
            val generated =
                onGenerate(
                    prompt,
                    context,
                    fallback.body,
                )
            val answer =
                if (generated.isNullOrBlank()) {
                    fallback
                } else {
                    fallback.copy(
                        title = "GloshIA",
                        body = generated,
                    )
                }
            messages[pendingIndex] = UserHelpMessage.assistant(answer)
            finish(fallback)
            generating = false
        }
    }

    fun clearConversation() {
        if (generating) return
        messages.clear()
        messages += UserHelpMessage.assistant(AppHelpAssistant.welcome(context))
        suggestions = AppHelpAssistant.suggestions(context)
        lastAction = null
        question = ""
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(ProductAppBackground)
                .statusBarsPadding()
                .imePadding(),
    ) {
        ProductPageHeader(
            title = "Ayuda",
            subtitle =
                if (modelState.canGenerate) {
                    "GloshIA local · conversación privada"
                } else {
                    "Ayuda básica disponible sin Internet"
                },
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Este teléfono · historial sólo durante esta pantalla",
                style = MaterialTheme.typography.bodySmall,
                color = ProductMutedInk,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(
                enabled = !generating,
                onClick = ::clearConversation,
            ) {
                Text("Borrar chat")
            }
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                GloshiaModelCard(
                    state = modelState,
                    onPrepareModel = onPrepareModel,
                )
            }
            items(messages) { message ->
                UserHelpBubble(
                    message = message,
                    onAction = onAction,
                )
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.94f)).padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                suggestions.forEach { suggestion ->
                    OutlinedButton(
                        enabled = !generating,
                        onClick = { submit(suggestion) },
                    ) {
                        Text(suggestion)
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    modifier = Modifier.weight(1f),
                    value = question,
                    onValueChange = { question = it.take(MaxQuestionLength) },
                    enabled = !generating,
                    label = { Text(if (generating) "GloshIA está pensando…" else "Preguntá sobre la app") },
                    maxLines = 3,
                )
                Button(
                    enabled = question.isNotBlank() && !generating,
                    onClick = { submit(question) },
                ) {
                    Text("Enviar")
                }
            }
        }
    }
}

@Composable
private fun GloshiaModelCard(
    state: GloshiaModelState,
    onPrepareModel: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            when (state.phase) {
                GloshiaModelPhase.Ready ->
                    Text(
                        "GloshIA conversacional está lista. El chat se procesa en este teléfono.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProductMutedInk,
                    )
                GloshiaModelPhase.Missing -> {
                    Text(
                        "Activá GloshIA conversacional",
                        style = MaterialTheme.typography.titleMedium,
                        color = ProductInk,
                    )
                    Text(
                        "Descarga única de 647 MB. Después conversa localmente, sin API ni costo por consulta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProductMutedInk,
                    )
                    Button(onClick = onPrepareModel) {
                        Text("Descargar modelo")
                    }
                }
                GloshiaModelPhase.Downloading -> {
                    Text("Descargando GloshIA…", style = MaterialTheme.typography.titleMedium, color = ProductInk)
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "${state.downloadedBytes.toMegabytes()} de ${state.totalBytes.toMegabytes()} MB",
                        style = MaterialTheme.typography.bodySmall,
                        color = ProductMutedInk,
                    )
                }
                GloshiaModelPhase.Verifying -> {
                    Text("Verificando el modelo…", style = MaterialTheme.typography.titleMedium, color = ProductInk)
                    Text("Glosh comprueba su firma antes de usarlo.", color = ProductMutedInk)
                }
                GloshiaModelPhase.Loading -> {
                    Text("Preparando GloshIA…", style = MaterialTheme.typography.titleMedium, color = ProductInk)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                GloshiaModelPhase.Unsupported -> {
                    Text("Modo de ayuda básica", style = MaterialTheme.typography.titleMedium, color = ProductInk)
                    Text(state.detail.orEmpty(), color = ProductMutedInk)
                }
                GloshiaModelPhase.Error -> {
                    Text(
                        "GloshIA necesita reintentar",
                        style = MaterialTheme.typography.titleMedium,
                        color = ProductInk,
                    )
                    Text(state.detail ?: "No se pudo preparar el modelo.", color = ProductMutedInk)
                    OutlinedButton(onClick = onPrepareModel) {
                        Text("Reintentar")
                    }
                }
            }
        }
    }
}

@Composable
private fun UserHelpBubble(
    message: UserHelpMessage,
    onAction: (HelpAction) -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.fromUser) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth(if (message.fromUser) 0.82f else 0.92f)
                    .background(
                        color = if (message.fromUser) ProductViolet else Color.White,
                        shape = RoundedCornerShape(20.dp),
                    ).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                message.pending ->
                    Text(
                        "Pensando en este teléfono…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ProductMutedInk,
                    )
                message.answer != null -> {
                    Text(message.answer.title, style = MaterialTheme.typography.titleMedium, color = ProductInk)
                    Text(message.answer.body, style = MaterialTheme.typography.bodyMedium, color = ProductMutedInk)
                    message.answer.action?.let { action ->
                        OutlinedButton(onClick = { onAction(action) }) {
                            Text(message.answer.actionLabel ?: "Abrir")
                        }
                    }
                }
                else ->
                    Text(
                        message.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
            }
        }
    }
}

private data class UserHelpMessage(
    val text: String,
    val fromUser: Boolean,
    val answer: HelpAnswer? = null,
    val pending: Boolean = false,
) {
    companion object {
        fun user(text: String) = UserHelpMessage(text = text, fromUser = true)

        fun assistant(answer: HelpAnswer) = UserHelpMessage(text = answer.body, fromUser = false, answer = answer)

        fun pending() = UserHelpMessage(text = "", fromUser = false, pending = true)
    }
}

private fun Long.toMegabytes(): Long = this / (1024 * 1024)

private const val MaxQuestionLength = 500
