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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streammind.feature.aiqa.RetrievedChunk

@Composable
fun SemanticSearchPanel(
    state: SemanticSearchUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClear: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = "Semantic Search",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 8.dp).weight(1f)
            )
            if (state.isIndexing) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        Text(
            text = "Search the meaning of moments in this video and jump to the best matches.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = state.query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("e.g. the robot enters the laboratory") },
            singleLine = true,
            enabled = state.isIndexed && !state.isSearching,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = onClear) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear semantic search")
                    }
                }
            },
            supportingText = {
                when {
                    state.indexError != null -> Text("Indexing failed: ${state.indexError}")
                    !state.isIndexed && state.isIndexing -> Text("Preparing transcript search…")
                    !state.isIndexed -> Text("Transcript search is not ready yet")
                    state.searchError != null -> Text("Search failed: ${state.searchError}")
                }
            }
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Button(
                onClick = onSearch,
                enabled = state.isIndexed && !state.isSearching && state.query.isNotBlank()
            ) {
                if (state.isSearching) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Text("Find moments")
                }
            }
            if (!state.isSearching && state.results.isNotEmpty()) {
                Text("${state.results.size} matches", style = MaterialTheme.typography.labelMedium)
            }
        }

        if (!state.isSearching && state.isIndexed && state.query.isNotBlank() && state.results.isEmpty() && state.searchError == null) {
            Text("No matching moments found.", style = MaterialTheme.typography.bodySmall)
        }

        if (state.results.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 260.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(state.results, key = { it.segment.id }) { result ->
                    SemanticSearchResult(result = result, onClick = { onSeekTo(result.segment.startMs) })
                }
            }
        }
    }
}

@Composable
private fun SemanticSearchResult(result: RetrievedChunk, onClick: () -> Unit) {
    val timestamp = formatSearchTimestamp(result.segment.startMs)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = timestamp,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = result.segment.text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

private fun formatSearchTimestamp(startMs: Long): String {
    val totalSeconds = startMs.coerceAtLeast(0L) / 1_000L
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
