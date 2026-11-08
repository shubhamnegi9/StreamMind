package com.streammind.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlinx.coroutines.delay

data class VideoPlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val isEnded: Boolean = false,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playerError: PlaybackException? = null
)

/**
 * ExoPlayer doesn't push position updates — currentPosition is a snapshot you
 * have to poll. This registers a Player.Listener for events that ARE pushed
 * (play/pause, buffering, errors) and layers a polling loop on top for position.
 *
 * The poll loop runs unconditionally, not just while isPlaying: gating it on
 * isPlaying caused a real bug — during buffering, isPlaying is false, so the
 * loop would stop and the scrubber would keep showing a stale pre-seek position
 * until playback resumed, which is exactly the "snapping back oddly" symptom.
 * Always polling means the displayed position tracks the player's real state
 * even while paused, seeking, or buffering.
 */
@Composable
fun rememberVideoPlaybackState(player: Player): State<VideoPlaybackState> {
    val state = remember { mutableStateOf(VideoPlaybackState()) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.value = state.value.copy(isPlaying = isPlaying)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                state.value = state.value.copy(
                    isBuffering = playbackState == Player.STATE_BUFFERING,
                    isEnded = playbackState == Player.STATE_ENDED,
                    durationMs = player.duration.coerceAtLeast(0L),
                    // A successful return to STATE_READY means whatever error
                    // caused playback to stop is no longer relevant — clear it
                    // so the UI stops treating this as an error state.
                    playerError = if (playbackState == Player.STATE_READY) null else state.value.playerError
                )
            }

            override fun onPlayerError(error: PlaybackException) {
                state.value = state.value.copy(playerError = error, isBuffering = false)
            }
        }
        player.addListener(listener)
        state.value = state.value.copy(
            isPlaying = player.isPlaying,
            isBuffering = player.playbackState == Player.STATE_BUFFERING,
            isEnded = player.playbackState == Player.STATE_ENDED,
            durationMs = player.duration.coerceAtLeast(0L)
        )
        onDispose { player.removeListener(listener) }
    }

    LaunchedEffect(player) {
        while (true) {
            state.value = state.value.copy(currentPositionMs = player.currentPosition)
            delay(200)
        }
    }

    return state
}
