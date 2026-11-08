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

data class QuizUiState(
    val isLoading: Boolean = false,
    val questions: List<QuizQuestionEntity> = emptyList(),
    val currentIndex: Int = 0,
    val selectedOption: Int? = null,
    val isAnswerSubmitted: Boolean = false,
    val score: Int = 0,
    val isStarted: Boolean = false,
    val isCompleted: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class QuizViewModel @Inject constructor(
    private val generateQuiz: GenerateQuizUseCase
) : ViewModel() {
    private val _uiState = MutableStateFlow(QuizUiState())
    val uiState: StateFlow<QuizUiState> = _uiState.asStateFlow()

    private var currentVideoId: String? = null
    private var preparedVideoId: String? = null
    private var loadJob: Job? = null
    private var requestToken = 0L

    fun resetForVideo(videoId: String) {
        if (currentVideoId == videoId) return
        resetForOpen(videoId)
    }

    fun resetForOpen(videoId: String) {
        requestToken++
        loadJob?.cancel()
        currentVideoId = videoId
        preparedVideoId = null
        _uiState.value = QuizUiState()
    }

    fun prepare(videoId: String, transcript: List<TranscriptSegmentEntity>) {
        if (currentVideoId != videoId) resetForVideo(videoId)
        if (transcript.isEmpty() || preparedVideoId == videoId || _uiState.value.isLoading) return

        preparedVideoId = videoId
        val token = ++requestToken
        _uiState.value = QuizUiState(isLoading = true)
        loadJob = viewModelScope.launch {
            try {
                generateQuiz(videoId, transcript)
                    .onSuccess { questions ->
                        if (isCurrent(videoId, token)) {
                            _uiState.value = QuizUiState(questions = questions)
                        }
                    }
                    .onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        if (isCurrent(videoId, token)) {
                            preparedVideoId = null
                            _uiState.value = QuizUiState(error = throwable.message ?: "Couldn't create the quiz")
                        }
                    }
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }
    }

    fun retry(videoId: String, transcript: List<TranscriptSegmentEntity>) {
        if (currentVideoId != videoId) resetForVideo(videoId)
        preparedVideoId = null
        prepare(videoId, transcript)
    }

    fun start() {
        if (_uiState.value.questions.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(
                isStarted = true,
                isCompleted = false,
                currentIndex = 0,
                selectedOption = null,
                isAnswerSubmitted = false,
                score = 0
            )
        }
    }

    fun selectOption(index: Int) {
        val state = _uiState.value
        if (state.isStarted && !state.isAnswerSubmitted && index in 0..3) {
            _uiState.value = state.copy(selectedOption = index)
        }
    }

    fun submitAnswer() {
        val state = _uiState.value
        val question = state.questions.getOrNull(state.currentIndex) ?: return
        val selected = state.selectedOption ?: return
        if (state.isAnswerSubmitted) return
        _uiState.value = state.copy(
            isAnswerSubmitted = true,
            score = state.score + if (selected == question.correctOptionIndex) 1 else 0
        )
    }

    fun nextQuestion() {
        val state = _uiState.value
        if (!state.isAnswerSubmitted) return
        if (state.currentIndex >= state.questions.lastIndex) {
            _uiState.value = state.copy(isCompleted = true)
        } else {
            _uiState.value = state.copy(
                currentIndex = state.currentIndex + 1,
                selectedOption = null,
                isAnswerSubmitted = false
            )
        }
    }

    fun restart() = start()

    fun resetQuiz() {
        val state = _uiState.value
        if (state.questions.isNotEmpty()) {
            _uiState.value = state.copy(
                currentIndex = 0,
                selectedOption = null,
                isAnswerSubmitted = false,
                score = 0,
                isStarted = false,
                isCompleted = false,
                error = null
            )
        }
    }

    private fun isCurrent(videoId: String, token: Long): Boolean =
        currentVideoId == videoId && requestToken == token
}
