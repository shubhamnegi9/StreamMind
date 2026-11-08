package com.streammind.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammind.data.local.QuizQuestionEntity
import com.streammind.data.local.TranscriptSegmentEntity
import com.streammind.feature.aichapters.GenerateQuizUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SceneQuizUiState(
    val videoId: String? = null,
    val segment: TranscriptSegmentEntity? = null,
    val isLoading: Boolean = false,
    val question: QuizQuestionEntity? = null,
    val selectedOption: Int? = null,
    val isAnswerSubmitted: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SceneQuizViewModel @Inject constructor(
    private val generateQuiz: GenerateQuizUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(SceneQuizUiState())
    val uiState: StateFlow<SceneQuizUiState> = _uiState.asStateFlow()

    private var activeVideoId: String? = null
    private var activeStartMs: Long? = null
    private var loadJob: Job? = null
    private var requestToken = 0L

    fun resetForOpen(videoId: String) {
        requestToken++
        loadJob?.cancel()
        activeVideoId = videoId
        activeStartMs = null
        _uiState.value = SceneQuizUiState(videoId = videoId)
    }

    fun showScene(videoId: String, segment: TranscriptSegmentEntity) {
        if (activeVideoId != videoId) resetForOpen(videoId)
        if (activeStartMs == segment.startMs) return

        activeStartMs = segment.startMs
        loadJob?.cancel()
        _uiState.value = SceneQuizUiState(
            videoId = videoId,
            segment = segment
        )
    }

    fun generateCurrentScene() {
        val state = _uiState.value
        val videoId = state.videoId ?: return
        val segment = state.segment ?: return
        if (state.isLoading || state.question != null) return

        val token = ++requestToken
        _uiState.value = state.copy(isLoading = true, error = null)

        // A synthetic cache key keeps scene questions separate from the
        // overall-video quiz while still allowing the same Room-backed generator
        // and fake/live repository wiring to be reused.
        val sceneVideoId = "$videoId-scene-${segment.startMs}"
        loadJob = viewModelScope.launch {
            try {
                generateQuiz(sceneVideoId, listOf(segment))
                    .onSuccess { questions ->
                        if (isCurrent(videoId, segment.startMs, token)) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                question = questions.firstOrNull()
                            )
                        }
                    }
                    .onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        if (isCurrent(videoId, segment.startMs, token)) {
                            _uiState.value = _uiState.value.copy(
                                isLoading = false,
                                error = throwable.message ?: "Couldn't create a scene question"
                            )
                        }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    fun selectOption(index: Int) {
        val state = _uiState.value
        if (state.question != null && !state.isAnswerSubmitted && index in 0..3) {
            _uiState.value = state.copy(selectedOption = index)
        }
    }

    fun submitAnswer() {
        val state = _uiState.value
        if (state.question != null && state.selectedOption != null && !state.isAnswerSubmitted) {
            _uiState.value = state.copy(isAnswerSubmitted = true)
        }
    }

    fun resetAnswer() {
        _uiState.value = _uiState.value.copy(selectedOption = null, isAnswerSubmitted = false)
    }

    private fun isCurrent(videoId: String, startMs: Long, token: Long): Boolean =
        activeVideoId == videoId && activeStartMs == startMs && requestToken == token
}
