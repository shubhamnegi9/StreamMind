package com.streammind.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammind.core.common.NetworkMonitor
import com.streammind.data.local.ChapterEntity
import com.streammind.data.local.TranscriptSegmentEntity
import com.streammind.feature.aichapters.GenerateChaptersUseCase
import com.streammind.feature.transcript.TranscriptRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import javax.inject.Inject

data class PlayerUiState(
    val isLoadingTranscript: Boolean = false,
    val transcript: List<TranscriptSegmentEntity> = emptyList(),
    val transcriptVideoId: String? = null,
    val transcriptError: String? = null,
    val isLoadingChapters: Boolean = false,
    val chapters: List<ChapterEntity> = emptyList(),
    val chapterError: String? = null,
    val isOnline: Boolean = true
)

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val transcriptRepository: TranscriptRepository,
    private val generateChapters: GenerateChaptersUseCase,
    networkMonitor: NetworkMonitor
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var activeVideoId: String? = null
    private var transcriptJob: Job? = null
    private var chapterJob: Job? = null

    init {
        networkMonitor.isOnline
            .onEach { online -> _uiState.update { it.copy(isOnline = online) } }
            .launchIn(viewModelScope)
    }

    fun loadVideo(videoId: String) {
        activeVideoId = videoId
        transcriptJob?.cancel()
        chapterJob?.cancel()
        _uiState.update {
            it.copy(
                isLoadingTranscript = true,
                transcript = emptyList(),
                transcriptVideoId = null,
                transcriptError = null,
                isLoadingChapters = false,
                chapters = emptyList(),
                chapterError = null
            )
        }
        transcriptJob = viewModelScope.launch {
            val transcriptResult = runCatching { transcriptRepository.getOrCreateTranscript(videoId) }

            transcriptResult
                .onSuccess { segments ->
                    if (activeVideoId != videoId) return@onSuccess
                    _uiState.update {
                        it.copy(
                            isLoadingTranscript = false,
                            transcript = segments,
                            transcriptVideoId = videoId
                        )
                    }
                    // Chapters depend on having a transcript first, so this only
                    // fires once transcript load succeeds. In fake mode this is
                    // free and instant; in live mode this is a real API call —
                    // a production app might gate this behind a user tap instead
                    // of firing it automatically, to control cost.
                    loadChapters(videoId, segments)
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (activeVideoId != videoId) return@onFailure
                    _uiState.update {
                        it.copy(
                            isLoadingTranscript = false,
                            transcriptError = throwable.message ?: "Couldn't load transcript"
                        )
                    }
                }
        }
    }

    private fun loadChapters(videoId: String, transcript: List<TranscriptSegmentEntity>) {
        if (activeVideoId != videoId) return
        _uiState.update { it.copy(isLoadingChapters = true, chapterError = null) }
        chapterJob = viewModelScope.launch {
            generateChapters(videoId, transcript)
                .onSuccess { chapters ->
                    if (activeVideoId == videoId) {
                        _uiState.update { it.copy(isLoadingChapters = false, chapters = chapters) }
                    }
                }
                .onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (activeVideoId == videoId) {
                        _uiState.update {
                            it.copy(
                                isLoadingChapters = false,
                                chapterError = throwable.message ?: "Couldn't generate chapters"
                            )
                        }
                    }
                }
        }
    }
}
