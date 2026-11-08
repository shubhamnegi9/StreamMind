package com.streammind.feature.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.streammind.feature.aiqa.RetrievedChunk

@Composable
fun AskVideoPanel(
    state: AskUiState,
    onQuestionChange: (String) -> Unit,
    onAsk: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(text = "Ask StreamMind", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Filled.Close, contentDescription = "Close Ask StreamMind")
            }
        }
        Text("Get an answer grounded in this video.", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 3.dp))

        when {
            state.isIndexing -> StatusRow("Indexing transcript…", showSpinner = true)
            state.indexError != null -> StatusRow("Indexing failed: ${state.indexError}")
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = state.question,
                onValueChange = onQuestionChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("e.g. what happens in the forest?") },
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                keyboardActions = KeyboardActions(onSend = { onAsk() })
            )
            Button(
                onClick = onAsk,
                enabled = state.isIndexed && !state.isAnswering && state.question.isNotBlank()
            ) {
                Text("Ask AI")
            }
        }

        if (state.sources.isNotEmpty()) {
            Text(
                text = "Jump to source",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 12.dp)
            )
            LazyRow(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.sources) { chunk ->
                    SourceChip(chunk = chunk, onClick = { onSeekTo(chunk.segment.startMs) })
                }
            }
        }

        if (state.isAnswering && state.answer.isEmpty()) {
            StatusRow("Thinking…", showSpinner = true)
        }

        if (state.answer.isNotEmpty()) {
            Text(
                text = state.answer,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            )
        }

        if (state.answerError != null) {
            Text(
                text = "Answer failed: ${state.answerError}",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}

@Composable
private fun SourceChip(chunk: RetrievedChunk, onClick: () -> Unit) {
    val seconds = chunk.segment.startMs / 1000
    Text(
        text = "${seconds}s · ${"%.2f".format(chunk.score)}",
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
private fun StatusRow(text: String, showSpinner: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showSpinner) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall)
    }
}
