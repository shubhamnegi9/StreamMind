package com.streammind.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.concurrent.TimeUnit

private const val SEEK_STEP_MS = 10_000L

/**
 * Play/pause + ±10s seek buttons + scrubber.
 *
 * [seekEnabled] lets the caller disable the scrubber while the player is in an
 * error/offline state so the user can't drag to positions that won't play.
 *
 * No buffering spinner or error UI here — those need to stay visible even while
 * this whole overlay is auto-hidden (see PlayerScreen which wraps this in an
 * AnimatedVisibility toggled by tap-to-show/hide).
 */
@Composable
fun PlayerControls(
    playbackState: VideoPlaybackState,
    durationMs: Long,
    playbackSpeed: Float = 1.0f,
    onPlaybackSpeedChange: (Float) -> Unit = {},
    onSpeedMenuExpandedChange: (Boolean) -> Unit = {},
    onPlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    seekEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    var dragPositionMs by remember { mutableStateOf<Long?>(null) }
    var speedMenuExpanded by remember { mutableStateOf(false) }
    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    Box(modifier = modifier.fillMaxSize()) {

        // Playback speed is kept in the player overlay so it remains available
        // without adding another panel to the surrounding player screen.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        listOf(Color(0xFF6A45B8), Color(0xFFB57CFF))
                    ),
                    shape = RoundedCornerShape(18.dp)
                )
        ) {
            TextButton(onClick = {
                speedMenuExpanded = true
                onSpeedMenuExpandedChange(true)
            }) {
                Text(
                    text = formatSpeedLabel(playbackSpeed),
                    color = Color.White
                )
            }
            DropdownMenu(
                expanded = speedMenuExpanded,
                onDismissRequest = {
                    speedMenuExpanded = false
                    onSpeedMenuExpandedChange(false)
                }
            ) {
                speedOptions.forEach { speed ->
                    DropdownMenuItem(
                        text = { Text(formatSpeedLabel(speed)) },
                        onClick = {
                            onPlaybackSpeedChange(speed)
                            speedMenuExpanded = false
                            onSpeedMenuExpandedChange(false)
                        }
                    )
                }
            }
        }

        // ── Centre row: rewind / play-pause / forward ──────────────────────────
        Row(
            modifier = Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    val newPos = (playbackState.currentPositionMs - SEEK_STEP_MS).coerceAtLeast(0L)
                    onSeekTo(newPos)
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(Color(0xFF2A1B45).copy(alpha = 0.9f), RoundedCornerShape(18.dp))
            ) {
                Icon(
                    imageVector = Icons.Filled.Replay10,
                    contentDescription = "Rewind 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }

            IconButton(
                onClick = onPlayPause,
                modifier = Modifier
                    .size(60.dp)
                    .background(Color(0xFF2A1B45).copy(alpha = 0.9f), RoundedCornerShape(18.dp))
            ) {
                Icon(
                    imageVector = if (playbackState.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(
                onClick = {
                    val newPos = (playbackState.currentPositionMs + SEEK_STEP_MS)
                        .coerceAtMost(durationMs.coerceAtLeast(0L))
                    onSeekTo(newPos)
                },
                modifier = Modifier
                    .size(52.dp)
                    .background(Color(0xFF2A1B45).copy(alpha = 0.9f), RoundedCornerShape(18.dp))
            ) {
                Icon(
                    imageVector = Icons.Filled.Forward10,
                    contentDescription = "Forward 10 seconds",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }

        // ── Bottom: scrubber + timestamps ──────────────────────────────────────
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xFF120B1E).copy(alpha = 0.88f), RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .padding(horizontal = 12.dp)
        ) {
            val displayedPositionMs = dragPositionMs ?: playbackState.currentPositionMs
            val effectiveDurationMs = durationMs.coerceAtLeast(1L)

            // Constrained to a fixed, short height: Material3's Slider defaults to
            // a much taller layout box to guarantee a 48dp touch target, which was
            // the actual cause of the seekbar looking like it ate half the player.
            Box(modifier = Modifier.fillMaxWidth().height(20.dp)) {
                Slider(
                    value = displayedPositionMs.toFloat().coerceIn(0f, effectiveDurationMs.toFloat()),
                    valueRange = 0f..effectiveDurationMs.toFloat(),
                    onValueChange = { if (seekEnabled) dragPositionMs = it.toLong() },
                    onValueChangeFinished = {
                        if (seekEnabled) {
                            dragPositionMs?.let(onSeekTo)
                            dragPositionMs = null
                        }
                    },
                    enabled = seekEnabled,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFFE0B7FF),
                        activeTrackColor = Color(0xFFB39DFF),
                        inactiveTrackColor = Color(0xFFB39DFF).copy(alpha = 0.3f),
                        disabledThumbColor = Color.White.copy(alpha = 0.4f),
                        disabledActiveTrackColor = Color.White.copy(alpha = 0.3f),
                        disabledInactiveTrackColor = Color.White.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.fillMaxSize()
                )
            }

            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Text(
                    text = formatTime(displayedPositionMs),
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatTime(durationMs),
                    color = Color.White,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun formatSpeedLabel(speed: Float): String {
    return if (speed % 1f == 0f) "${speed.toInt()}x" else "${speed}x"
}

private fun formatTime(ms: Long): String {
    val totalSeconds = TimeUnit.MILLISECONDS.toSeconds(ms.coerceAtLeast(0L))
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
