package com.streammind.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammind.feature.accessibility.DescribeSceneUseCase
import com.streammind.feature.accessibility.SceneDescriptionSpeaker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AccessibilityUiState(
    val isDescribing: Boolean = false,
    val description: String? = null,
    val lastDescribedAtMs: Long? = null,
    val error: String? = null
)

@HiltViewModel
class AccessibilityViewModel @Inject constructor(
    private val describeScene: DescribeSceneUseCase,
    private val speaker: SceneDescriptionSpeaker
) : ViewModel() {

    private val _uiState = MutableStateFlow(AccessibilityUiState())
    val uiState: StateFlow<AccessibilityUiState> = _uiState.asStateFlow()

    init {
        // Fire-and-forget: TTS init is async internally: speak() checks
        // readiness before use, so nothing here needs to await it.
        speaker.initialize()
    }

    fun describeCurrentScene(videoUrl: String, atMs: Long, nearbyTranscriptText: String) {
        _uiState.update { it.copy(isDescribing = true, error = null) }
        viewModelScope.launch {
            describeScene(videoUrl, atMs, nearbyTranscriptText)
                .onSuccess { description ->
                    _uiState.update {
                        it.copy(isDescribing = false, description = description, lastDescribedAtMs = atMs)
                    }
                    speaker.speak(description)
                }
                .onFailure { throwable ->
                    _uiState.update {
                        it.copy(isDescribing = false, error = throwable.message ?: "Couldn't describe this scene")
                    }
                }
        }
    }

    fun stop() {
        speaker.stop()
    }

    fun reset() {
        stop()
        _uiState.update {
            AccessibilityUiState()
        }
    }

    /** Replays the last description via TTS without a new API call. */
    fun replay() {
        _uiState.value.description?.let(speaker::speak)
    }

    override fun onCleared() {
        // TextToSpeech holds native engine resources that outlive garbage
        // collection — this must be called explicitly, not left to finalizers.
        speaker.shutdown()
        super.onCleared()
    }
}
