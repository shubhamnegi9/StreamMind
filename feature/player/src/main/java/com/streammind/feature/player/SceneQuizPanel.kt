package com.streammind.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streammind.data.local.QuizQuestionEntity
import com.streammind.data.local.TranscriptSegmentEntity

@Composable
fun SceneQuizPanel(
    state: SceneQuizUiState,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSelectOption: (Int) -> Unit,
    onSubmit: () -> Unit,
    onResetAnswer: () -> Unit,
    onGenerate: () -> Unit,
    onSeekTo: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(18.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onExpandedChange(!expanded) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(Icons.Filled.Movie, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(
                text = "Scene Quiz",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "Collapse scene quiz" else "Expand scene quiz"
            )
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                when {
                    state.isLoading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text("Preparing a question for this scene…", modifier = Modifier.padding(start = 8.dp))
                    }
                    state.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.error, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                        TextButton(onClick = onResetAnswer) { Text("Dismiss") }
                    }
                    state.question != null -> SceneQuestionContent(
                        question = state.question,
                        selectedOption = state.selectedOption,
                        isAnswerSubmitted = state.isAnswerSubmitted,
                        onSelectOption = onSelectOption,
                        onSubmit = onSubmit,
                        onResetAnswer = onResetAnswer,
                        onSeekTo = onSeekTo
                    )
                    else -> {
                        Text("Generate a question for this scene when you want to explore it.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = onGenerate) { Text("Generate scene question") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SceneQuestionContent(
    question: QuizQuestionEntity,
    selectedOption: Int?,
    isAnswerSubmitted: Boolean,
    onSelectOption: (Int) -> Unit,
    onSubmit: () -> Unit,
    onResetAnswer: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    val correctGreen = Color(0xFF2E7D32)
    val correctGreenContent = Color.White
    Text(question.question, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    question.options().forEachIndexed { index, option ->
        val isCorrect = isAnswerSubmitted && index == question.correctOptionIndex
        val isWrong = isAnswerSubmitted && index == selectedOption && !isCorrect
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    when {
                        isCorrect -> correctGreen
                        isWrong -> MaterialTheme.colorScheme.errorContainer
                        else -> MaterialTheme.colorScheme.surface
                    },
                    RoundedCornerShape(11.dp)
                )
                .clickable(enabled = !isAnswerSubmitted) { onSelectOption(index) }
                .padding(horizontal = 5.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selectedOption == index,
                onClick = { onSelectOption(index) },
                enabled = !isAnswerSubmitted,
                colors = RadioButtonDefaults.colors(
                    selectedColor = if (isCorrect) correctGreenContent else MaterialTheme.colorScheme.primary,
                    disabledSelectedColor = if (isCorrect) correctGreenContent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Text(
                text = option,
                color = if (isCorrect) correctGreenContent else MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }
    if (isAnswerSubmitted) {
        val isCorrect = selectedOption == question.correctOptionIndex
        Text(
            text = if (isCorrect) "Correct" else "Not quite — the green option is correct.",
            color = if (isCorrect) correctGreenContent else MaterialTheme.colorScheme.error,
            fontWeight = FontWeight.Bold
        )
        Text(question.explanation, style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TextButton(onClick = { onSeekTo(question.sourceStartMs) }) {
                Text("Jump to ${formatSceneQuizTimestamp(question.sourceStartMs)}")
            }
            TextButton(onClick = onResetAnswer) { Text("Try again") }
        }
    } else {
        Button(onClick = onSubmit, enabled = selectedOption != null) { Text("Check answer") }
    }
}

private fun formatSceneQuizTimestamp(startMs: Long): String {
    val seconds = startMs.coerceAtLeast(0L) / 1_000L
    return "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
