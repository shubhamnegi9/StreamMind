package com.streammind.feature.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.streammind.data.local.ChapterEntity
import kotlin.math.abs

/**
 * Draws a visible track + a dot per chapter, and reports a tap near any dot back
 * via onChapterTapped.
 *
 * This renders as its own bar below the player, not overlaid directly on top of
 * ExoPlayer's built-in seek bar inside PlayerView's controller. Overlaying it on
 * ExoPlayer's own DefaultTimeBar would need a custom controller layout to get
 * pixel-accurate alignment with its internal padding/track, which wasn't worth the
 * complexity for Phase 2 — this bar still seeks the same ExoPlayer instance on tap,
 * it just lives as a second, deliberately chapter-only control underneath.
 */
@Composable
fun ChapterMarkerOverlay(
    chapters: List<ChapterEntity>,
    videoDurationMs: Long,
    onChapterTapped: (ChapterEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    if (chapters.isEmpty() || videoDurationMs <= 0) return

    val trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    val markerColor = MaterialTheme.colorScheme.primary
    val markerOutline = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
            .padding(vertical = 2.dp)
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .pointerInput(chapters, videoDurationMs) {
                    detectTapGestures { tapOffset: Offset ->
                        val tapFraction = (tapOffset.x / size.width).coerceIn(0f, 1f)
                        val tapMs = (tapFraction * videoDurationMs).toLong()
                        val nearest = chapters.minByOrNull { abs(it.startMs - tapMs) }
                        val toleranceMs = (videoDurationMs * 0.03).toLong().coerceAtLeast(3000)
                        if (nearest != null && abs(nearest.startMs - tapMs) <= toleranceMs) {
                            onChapterTapped(nearest)
                        }
                    }
                }
        ) {
            val trackY = size.height / 2

            // Track line so the bar reads as a scrubber even with zero chapters tapped yet.
            drawLine(
                color = trackColor,
                start = Offset(0f, trackY),
                end = Offset(size.width, trackY),
                strokeWidth = 3.dp.toPx()
            )

            chapters.forEach { chapter ->
                val fraction = (chapter.startMs.toFloat() / videoDurationMs).coerceIn(0f, 1f)
                val x = fraction * size.width
                // Outline first so the dot stays visible regardless of what's
                // directly behind it, then the actual marker on top.
                drawCircle(color = markerOutline, radius = 7.dp.toPx(), center = Offset(x, trackY))
                drawCircle(color = markerColor, radius = 5.dp.toPx(), center = Offset(x, trackY))
            }
        }
    }
}

@Composable
fun ChapterList(
    chapters: List<ChapterEntity>,
    onChapterTapped: (ChapterEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Column {
            chapters.forEach { chapter ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onChapterTapped(chapter) }
                        .padding(horizontal = 20.dp, vertical = 5.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(14.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "%d:%02d".format(chapter.startMs / 60_000, (chapter.startMs / 1000) % 60),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(text = chapter.title, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
