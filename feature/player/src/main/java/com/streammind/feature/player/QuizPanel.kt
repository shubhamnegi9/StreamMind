package com.streammind.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material.icons.filled.School
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import com.streammind.data.local.QuizQuestionEntity

@Composable
fun QuizPanel(
    state: QuizUiState,
    onGenerate: () -> Unit,
    onStart: () -> Unit,
    onSelectOption: (Int) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onRestart: () -> Unit,
    onReset: () -> Unit,
    onRetry: () -> Unit,
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
            Icon(
                imageVector = Icons.Filled.School,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f).padding(start = 8.dp)) {
                Text(
                    text = "AI Quiz & Learning Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Test your understanding of this video",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else if (state.questions.isNotEmpty()) {
                TextButton(onClick = onReset) { Text("Reset quiz") }
            }
        }

        when {
            state.isLoading -> Text("Preparing a personalized quiz…", style = MaterialTheme.typography.bodySmall)
            state.error != null -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onRetry) { Text("Retry") }
                }
            }
            state.questions.isEmpty() -> {
                Text(
                    "Generate an AI quiz when you are ready to test your understanding.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = onGenerate) { Text("Generate overall quiz") }
            }
            state.isCompleted -> CompletedQuiz(state = state, onRestart = onRestart)
            !state.isStarted -> {
                Text(
                    "Answer ${state.questions.size} multiple-choice questions, then review the explanations and jump back to the source moments.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = onStart) { Text("Start learning mode") }
            }
            else -> ActiveQuestion(
                state = state,
                question = state.questions[state.currentIndex],
                onSelectOption = onSelectOption,
                onSubmit = onSubmit,
                onNext = onNext,
                onSeekTo = onSeekTo
            )
        }
    }
}

@Composable
private fun ActiveQuestion(
    state: QuizUiState,
    question: QuizQuestionEntity,
    onSelectOption: (Int) -> Unit,
    onSubmit: () -> Unit,
    onNext: () -> Unit,
    onSeekTo: (Long) -> Unit
) {
    Text(
        text = "Question ${state.currentIndex + 1} of ${state.questions.size}  ·  Score ${state.score}",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = question.question,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
    question.options().forEachIndexed { index, option ->
        val isCorrect = state.isAnswerSubmitted && index == question.correctOptionIndex
        val isWrongSelection = state.isAnswerSubmitted && index == state.selectedOption && !isCorrect
        val correctGreen = Color(0xFF2E7D32)
        val correctGreenContent = Color.White
        val containerColor = when {
            isCorrect -> correctGreen
            isWrongSelection -> MaterialTheme.colorScheme.errorContainer
            else -> MaterialTheme.colorScheme.surface
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor, RoundedCornerShape(12.dp))
                .clickable(enabled = !state.isAnswerSubmitted) { onSelectOption(index) }
                .padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = state.selectedOption == index,
                onClick = { onSelectOption(index) },
                enabled = !state.isAnswerSubmitted,
                colors = RadioButtonDefaults.colors(
                    selectedColor = if (isCorrect) correctGreenContent else MaterialTheme.colorScheme.primary,
                    disabledSelectedColor = if (isCorrect) correctGreenContent else MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
            Text(
                text = option,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCorrect) correctGreenContent else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
    }

    AnimatedVisibility(
        visible = state.isAnswerSubmitted,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            val wasCorrect = state.selectedOption == question.correctOptionIndex
            Text(
                text = if (wasCorrect) "Correct" else "Not quite — the highlighted option is correct.",
                style = MaterialTheme.typography.labelLarge,
                color = if (wasCorrect) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.Bold
            )
            Text(question.explanation, style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { onSeekTo(question.sourceStartMs) }) {
                Text("Jump to source · ${formatQuizTimestamp(question.sourceStartMs)}")
            }
            Button(onClick = onNext) {
                Text(if (state.currentIndex == state.questions.lastIndex) "See results" else "Next question")
            }
        }
    }
    if (!state.isAnswerSubmitted) {
        Button(onClick = onSubmit, enabled = state.selectedOption != null) {
            Text("Check answer")
        }
    }
}

@Composable
private fun CompletedQuiz(state: QuizUiState, onRestart: () -> Unit) {
    Text("Learning mode complete", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
    Text(
        text = "You scored ${state.score} out of ${state.questions.size}.",
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold
    )
    Text(
        text = when {
            state.score == state.questions.size -> "Excellent recall — you mastered this video."
            state.score * 2 >= state.questions.size -> "Good work — review the explanations for the questions you missed."
            else -> "Keep learning — replay the source moments and try again."
        },
        style = MaterialTheme.typography.bodyMedium
    )
    Button(onClick = onRestart) { Text("Retake quiz") }
}

private fun formatQuizTimestamp(startMs: Long): String {
    val totalSeconds = startMs.coerceAtLeast(0L) / 1_000L
    return "${totalSeconds / 60}:${(totalSeconds % 60).toString().padStart(2, '0')}"
}
