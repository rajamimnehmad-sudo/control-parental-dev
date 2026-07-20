package com.contentfilter.core.ui

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

@Composable
fun AppHelpAssistantScreen(
    context: HelpContext,
    onBack: () -> Unit,
    onAction: (HelpAction) -> Unit,
) {
    var question by rememberSaveable { mutableStateOf("") }
    val messages = remember { mutableStateListOf(HelpChatMessage.assistant(AppHelpAssistant.welcome(context))) }
    var suggestions by remember(context) { mutableStateOf(AppHelpAssistant.suggestions(context)) }
    var lastAction by remember { mutableStateOf<HelpAction?>(null) }
    val listState = rememberLazyListState()

    fun submit(prompt: String) {
        if (prompt.isBlank()) return
        val answer = AppHelpAssistant.answer(prompt, context, previousAction = lastAction)
        messages += HelpChatMessage.user(prompt)
        messages += HelpChatMessage.assistant(answer)
        suggestions = AppHelpAssistant.followUpSuggestions(answer, context)
        lastAction = answer.action ?: lastAction
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
            subtitle = "Asistente de Content Filter · funciona sin Internet",
            onBack = onBack,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        )
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(messages) { message ->
                HelpChatBubble(message = message, onAction = onAction)
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
                    OutlinedButton(onClick = { submit(suggestion) }) {
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
                    label = { Text("Preguntá sobre la app") },
                    maxLines = 3,
                )
                Button(enabled = question.isNotBlank(), onClick = { submit(question) }) {
                    Text("Enviar")
                }
            }
        }
    }
}

@Composable
private fun HelpChatBubble(
    message: HelpChatMessage,
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
            message.answer?.let { answer ->
                Text(answer.title, style = MaterialTheme.typography.titleMedium, color = ProductInk)
                Text(answer.body, style = MaterialTheme.typography.bodyMedium, color = ProductMutedInk)
                answer.action?.let { action ->
                    OutlinedButton(onClick = { onAction(action) }) {
                        Text(answer.actionLabel ?: "Abrir")
                    }
                }
            } ?: Text(
                message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
    }
}

private data class HelpChatMessage(
    val text: String,
    val fromUser: Boolean,
    val answer: HelpAnswer? = null,
) {
    companion object {
        fun user(text: String) = HelpChatMessage(text = text, fromUser = true)

        fun assistant(answer: HelpAnswer) = HelpChatMessage(text = answer.body, fromUser = false, answer = answer)
    }
}

private const val MaxQuestionLength = 240
