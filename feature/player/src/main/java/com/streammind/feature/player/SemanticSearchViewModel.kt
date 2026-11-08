package com.streammind.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammind.feature.aiqa.EmbedTranscriptUseCase
import com.streammind.feature.aiqa.RetrieveRelevantChunksUseCase
import com.streammind.feature.aiqa.RetrievedChunk
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SemanticSearchUiState(
    val isIndexing: Boolean = false,
    val isIndexed: Boolean = false,
    val indexError: String? = null,
    val query: String = "",
    val isSearching: Boolean = false,
    val results: List<RetrievedChunk> = emptyList(),
    val searchError: String? = null
)

@HiltViewModel
class SemanticSearchViewModel @Inject constructor(
    private val embedTranscript: EmbedTranscriptUseCase,
    private val retrieveRelevantChunks: RetrieveRelevantChunksUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(SemanticSearchUiState())
    val uiState: StateFlow<SemanticSearchUiState> = _uiState.asStateFlow()

    private var searchJob: Job? = null
    private var indexJob: Job? = null
    private var indexedVideoId: String? = null

    fun prepare(videoId: String) {
        if (indexedVideoId == videoId) return

        searchJob?.cancel()
        indexJob?.cancel()
        indexedVideoId = videoId
        _uiState.value = SemanticSearchUiState(isIndexing = true)

        indexJob = viewModelScope.launch {
            embedTranscript(videoId)
                .onSuccess {
                    _uiState.update { state ->
                        state.copy(isIndexing = false, isIndexed = true)
                    }
                }
                .onFailure { throwable ->
                    indexedVideoId = null
                    _uiState.update { state ->
                        state.copy(
                            isIndexing = false,
                            isIndexed = false,
                            indexError = throwable.message ?: "Couldn't index this video"
                        )
                    }
                }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { it.copy(query = query, searchError = null) }
    }

    fun search(videoId: String) {
        val query = _uiState.value.query.trim()
        if (query.isEmpty()) {
            _uiState.update { it.copy(results = emptyList(), searchError = null) }
            return
        }

        searchJob?.cancel()
        _uiState.update {
            it.copy(isSearching = true, results = emptyList(), searchError = null)
        }
        searchJob = viewModelScope.launch {
            runCatching { retrieveRelevantChunks(videoId, query, topK = 8) }
                .onSuccess { matches ->
                    _uiState.update { state ->
                        state.copy(isSearching = false, results = matches)
                    }
                }
                .onFailure { throwable ->
                    _uiState.update { state ->
                        state.copy(
                            isSearching = false,
                            searchError = throwable.message ?: "Semantic search failed"
                        )
                    }
                }
        }
    }

    fun clear() {
        searchJob?.cancel()
        _uiState.update {
            it.copy(query = "", results = emptyList(), searchError = null, isSearching = false)
        }
    }
}

