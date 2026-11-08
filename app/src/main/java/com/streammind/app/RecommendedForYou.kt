package com.streammind.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.streammind.data.local.PlaybackProgressEntity

private data class RecommendedVideo(
    val video: StreamMindVideo,
    val reason: String,
    val score: Int
)

@Composable
fun RecommendedForYouTray(
    progress: List<PlaybackProgressEntity>,
    preferredCategories: Set<String> = emptySet(),
    onVideoSelected: (StreamMindVideo) -> Unit,
    modifier: Modifier = Modifier
) {
    // A progress row can be created when a player is opened, before the user
    // has meaningfully watched anything. Treat five seconds as the minimum
    // watch-history signal so new users do not see a recommendation tray.
    val watchHistory = progress.filter { it.positionMs > 5_000L }
    if (watchHistory.isEmpty() && preferredCategories.isEmpty()) return
    val recommendations = buildRecommendations(
        videos = StreamMindLibrary.videos,
        progress = watchHistory,
        preferredCategories = preferredCategories
    )
    if (recommendations.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Recommended for You",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Personalized from your viewing and learning activity",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp, bottom = 10.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(recommendations, key = { it.video.id }) { recommendation ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.width(188.dp)
                ) {
                    Column(
                        modifier = Modifier.clickable { onVideoSelected(recommendation.video) }
                    ) {
                        VideoArtwork(
                            video = recommendation.video,
                            modifier = Modifier.fillMaxWidth().height(106.dp)
                        )
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = recommendation.video.title,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = recommendation.reason,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .padding(top = 5.dp)
                                    .fillMaxWidth()
                                    .height(36.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun buildRecommendations(
    videos: List<StreamMindVideo>,
    progress: List<PlaybackProgressEntity>,
    preferredCategories: Set<String>
): List<RecommendedVideo> {
    if (videos.isEmpty()) return emptyList()

    val progressById = progress.associateBy { it.videoId }
    val watchedVideos = progress.mapNotNull { entry ->
        StreamMindLibrary.find(entry.videoId)?.let { video -> video to entry }
    }
    val preferredCategory = watchedVideos
        .groupingBy { it.first.category }
        .eachCount()
        .maxByOrNull { it.value }
        ?.key

    val preferredVideo = watchedVideos.lastOrNull()?.first
    val candidates = videos.map { video ->
        val entry = progressById[video.id]
        val completion = entry?.let {
            (it.positionMs.toFloat() / it.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
        } ?: 0f
        val sameCategory = preferredCategory != null && video.category == preferredCategory
        val matchesPreference = video.category in preferredCategories
        val isUnwatched = entry == null
        val isUnfinished = completion in 0.05f..0.95f
        val score = (if (matchesPreference) 60 else 0) +
            (if (sameCategory) 40 else 0) +
            (if (isUnfinished) 25 else 0) +
            (if (isUnwatched) 15 else 0) +
            (if (video.id != preferredVideo?.id) 5 else -20)
        val reason = when {
            isUnfinished -> "Continue exploring ${video.category}"
            matchesPreference -> "Because you chose ${video.category}"
            sameCategory -> "Because you watched ${preferredCategory.orEmpty()}"
            preferredVideo != null -> "Because you watched ${preferredVideo.title}"
            else -> "A great place to start"
        }
        RecommendedVideo(video, reason, score)
    }

    return candidates
        .sortedWith(compareByDescending<RecommendedVideo> { it.score }.thenBy { it.video.title })
        .take(4)
}
