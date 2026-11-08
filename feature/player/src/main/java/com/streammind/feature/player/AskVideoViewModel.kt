package com.streammind.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammind.feature.aiqa.AskResult
import com.streammind.feature.aiqa.AskVideoUseCase
import com.streammind.feature.aiqa.EmbedTranscriptUseCase
import com.streammind.feature.aiqa.RetrievedChunk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AskUiState(
    val isIndexing: Boolean = false,
    val isIndexed: Boolean = false,
    val indexError: String? = null,
    val question: String = "",
    val isAnswering: Boolean = false,
    val answer: String = "",
    val sources: List<RetrievedChunk> = emptyList(),
    val answerError: String? = null
)

@HiltViewModel
class AskVideoViewModel @Inject constructor(
    private val embedTranscript: EmbedTranscriptUseCase,
    private val askVideo: AskVideoUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AskUiState())
    val uiState: StateFlow<AskUiState> = _uiState.asStateFlow()

    private var askJob: Job? = null

    /** Embeds the transcript so questions can be answered. Safe to call repeatedly. */
    fun index(videoId: String) {
        if (_uiState.value.isIndexing || _uiState.value.isIndexed) return
        _uiState.update { it.copy(isIndexing = true, indexError = null) }
        viewModelScope.launch {
            embedTranscript(videoId)
                .onSuccess { _uiState.update { s -> s.copy(isIndexing = false, isIndexed = true) } }
                .onFailure { t ->
                    _uiState.update { s ->
                        s.copy(
                            isIndexing = false,
                            indexError = t.message ?: "Couldn't index transcript"
                        )
                    }
                }
        }
    }

    fun onQuestionChange(text: String) {
        _uiState.update { it.copy(question = text) }
    }

    fun reset() {
        askJob?.cancel()
        _uiState.update {
            it.copy(
                question = "",
                answer = "",
                sources = emptyList(),
                answerError = null,
                isAnswering = false
            )
        }
    }

    fun ask(videoId: String) {
        val question = _uiState.value.question.trim()
        if (question.isEmpty()) return

        // Cancel any in-flight answer so a second question doesn't interleave
        // its tokens with the previous one's.
        askJob?.cancel()
        _uiState.update {
            it.copy(isAnswering = true, answer = "", sources = emptyList(), answerError = null)
        }

        askJob = viewModelScope.launch {
            runCatching {
                askVideo(videoId, question).collect { result ->
                    when (result) {
                        is AskResult.Sources ->
                            _uiState.update { it.copy(sources = result.chunks) }
                        is AskResult.Token ->
                            _uiState.update { it.copy(answer = it.answer + result.text) }
                        AskResult.Done ->
                            _uiState.update { it.copy(isAnswering = false) }
                    }
                }
            }.onFailure { t ->
                _uiState.update {
                    it.copy(
                        isAnswering = false,
                        answerError = t.message ?: "Couldn't generate an answer"
                    )
                }
            }
        }
    }
}
