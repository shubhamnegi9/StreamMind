package com.streammind.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammind.data.local.KeyMomentEntity
import com.streammind.data.local.TranscriptSegmentEntity
import com.streammind.feature.aichapters.GenerateKeyMomentsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class KeyMomentsUiState(
    val isLoading: Boolean = false,
    val moments: List<KeyMomentEntity> = emptyList(),
    val error: String? = null
)

@HiltViewModel
class KeyMomentsViewModel @Inject constructor(
    private val generateKeyMoments: GenerateKeyMomentsUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(KeyMomentsUiState())
    val uiState: StateFlow<KeyMomentsUiState> = _uiState.asStateFlow()

    private var currentVideoId: String? = null
    private var preparedVideoId: String? = null
    private var loadJob: Job? = null
    private var requestToken = 0L

    fun resetForVideo(videoId: String) {
        if (currentVideoId == videoId) return

        requestToken++
        loadJob?.cancel()
        currentVideoId = videoId
        preparedVideoId = null
        _uiState.value = KeyMomentsUiState()
    }

    fun prepare(videoId: String, transcript: List<TranscriptSegmentEntity>) {
        if (currentVideoId != videoId) resetForVideo(videoId)
        if (transcript.isEmpty() || preparedVideoId == videoId || _uiState.value.isLoading) return

        preparedVideoId = videoId
        val token = ++requestToken
        _uiState.value = KeyMomentsUiState(isLoading = true)
        loadJob = viewModelScope.launch {
            try {
                generateKeyMoments(videoId, transcript)
                    .onSuccess { moments ->
                        if (isCurrent(videoId, token)) {
                            _uiState.value = KeyMomentsUiState(moments = moments)
                        }
                    }
                    .onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        if (isCurrent(videoId, token)) {
                            preparedVideoId = null
                            _uiState.value = KeyMomentsUiState(
                                error = throwable.message ?: "Couldn't generate Key Moments"
                            )
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

    private fun isCurrent(videoId: String, token: Long): Boolean =
        currentVideoId == videoId && requestToken == token
}
