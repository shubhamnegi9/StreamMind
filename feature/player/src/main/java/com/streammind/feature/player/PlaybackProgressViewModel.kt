package com.streammind.feature.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.streammind.data.local.PlaybackProgressDao
import com.streammind.data.local.PlaybackProgressEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class PlaybackProgressViewModel @Inject constructor(
    private val dao: PlaybackProgressDao
) : ViewModel() {
    val continueWatching: StateFlow<List<PlaybackProgressEntity>> =
        dao.observeContinueWatching().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000L),
            initialValue = emptyList()
        )

    fun save(videoId: String, positionMs: Long, durationMs: Long) {
        if (positionMs <= 5_000L || durationMs <= 0L) return
        viewModelScope.launch {
            dao.upsert(
                PlaybackProgressEntity(
                    videoId = videoId,
                    positionMs = positionMs.coerceAtMost(durationMs),
                    durationMs = durationMs,
                    updatedAtMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun remove(videoId: String) {
        viewModelScope.launch { dao.delete(videoId) }
    }

    fun clearAll() {
        viewModelScope.launch { dao.deleteAll() }
    }
}
