package com.streammind.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.streammind.data.local.PlaybackProgressEntity
import com.streammind.feature.player.PlaybackProgressViewModel
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class StreamMindVideo(
    val id: String,
    val title: String,
    val creator: String,
    val category: String,
    val durationMs: Long,
    val durationLabel: String,
    val progressLabel: String,
    val videoUrl: String,
    val accent: Color,
    val thumbnailRes: Int
)

object StreamMindLibrary {
    val videos = listOf(
        StreamMindVideo(
            id = "sample_1",
            title = "Big Buck Bunny",
            creator = "Blender Foundation",
            category = "Animation",
            durationMs = 596_000L,
            durationLabel = "9:56",
            progressLabel = "Continue from 4:20",
            videoUrl = "https://storage.googleapis.com/exoplayer-test-media-0/BigBuckBunny_320x180.mp4",
            accent = Color(0xFF4B2F72),
            thumbnailRes = R.drawable.thumb_big_buck_bunny
        ),
        // Self-hosted on GitHub Releases (see StreamMind's streammind-assets repo)
        // after the public buckets these previously pointed at (gtv-videos-bucket,
        // archive.org) turned out to be dead or permission-restricted. Self-hosting
        // means these URLs are under our own control and won't silently break again.
        // Durations verified against official sources (Blender's own project pages
        // and Wikipedia's cited runtimes), not guessed — see effectiveDurationMs in
        // PlayerScreen for why even a wrong value here would only affect the UI
        // briefly before ExoPlayer reports the real duration anyway.
        StreamMindVideo(
            id = "elephants_dream",
            title = "Elephants Dream",
            creator = "Blender Open Movie",
            category = "Sci-Fi",
            durationMs = 653_000L,
            durationLabel = "10:53",
            progressLabel = "New to your library",
            videoUrl = "https://github.com/shubhamnegi9/streammind-assets/releases/download/v1.0-assets/Elephants.Dream.mp4",
            accent = Color(0xFF37245B),
            thumbnailRes = R.drawable.thumb_elephants_dream
        ),
        StreamMindVideo(
            id = "sintel",
            title = "Sintel",
            creator = "Blender Open Movie",
            category = "Fantasy",
            durationMs = 888_000L,
            durationLabel = "14:48",
            progressLabel = "New to your library",
            videoUrl = "https://github.com/shubhamnegi9/streammind-assets/releases/download/v1.0-assets/Sintel.mp4",
            accent = Color(0xFF5B3D82),
            thumbnailRes = R.drawable.thumb_sintel
        ),
        StreamMindVideo(
            id = "tears_of_steel",
            title = "Tears of Steel",
            creator = "Blender Open Movie",
            category = "Sci-Fi",
            durationMs = 734_000L,
            durationLabel = "12:14",
            progressLabel = "New to your library",
            videoUrl = "https://github.com/shubhamnegi9/streammind-assets/releases/download/v1.0-assets/Tears.of.Steel.mp4",
            accent = Color(0xFF713E78),
            thumbnailRes = R.drawable.thumb_tears_of_steel
        ),
        StreamMindVideo(
            id = "spring",
            title = "Spring",
            creator = "Blender Open Movie",
            category = "Fantasy",
            durationMs = 464_000L,
            durationLabel = "7:44",
            progressLabel = "New to your library",
            videoUrl = "https://github.com/shubhamnegi9/streammind-assets/releases/download/v1.0-assets/Spring.mp4",
            accent = Color(0xFF6A4C9A),
            thumbnailRes = R.drawable.thumb_spring
        )
    )

    fun find(videoId: String): StreamMindVideo? = videos.firstOrNull { it.id == videoId }
}

@Composable
fun VideoLibraryScreen(
    onVideoSelected: (StreamMindVideo) -> Unit,
    modifier: Modifier = Modifier
) {
    val progressViewModel: PlaybackProgressViewModel = hiltViewModel()
    val continueProgress by progressViewModel.continueWatching.collectAsState()
    val context = LocalContext.current
    val personalizationStore = remember(context) { LibraryPersonalizationStore(context) }
    val isNewInstallation = remember(personalizationStore) {
        personalizationStore.resetIfNewInstallation()
    }
    var personalization by remember(personalizationStore, isNewInstallation) {
        mutableStateOf(personalizationStore.load())
    }
    var showPersonalizationDialog by remember { mutableStateOf(!personalization.completed) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("All") }
    var isGrid by rememberSaveable { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current
    val spotlightVideos = StreamMindLibrary.videos
    val categories = listOf("All", "Animation", "Sci-Fi", "Fantasy")
    val filteredVideos = StreamMindLibrary.videos.filter { video ->
        val matchesCategory = selectedCategory == "All" || video.category == selectedCategory
        val matchesQuery = query.isBlank() || listOf(video.title, video.creator, video.category)
            .joinToString(" ")
            .contains(query, ignoreCase = true)
        matchesCategory && matchesQuery
    }

    LaunchedEffect(isNewInstallation) {
        if (isNewInstallation) {
            // Room databases may also be restored after reinstall. Explicitly
            // remove playback rows so Continue Watching starts empty.
            progressViewModel.clearAll()
            showPersonalizationDialog = true
        }
    }
    val visibleContinueProgress = if (isNewInstallation) emptyList() else continueProgress

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp)
            .pointerInput(Unit) {
                detectTapGestures { focusManager.clearFocus() }
            }
    ) {
        Row(
            modifier = Modifier.padding(top = 18.dp, bottom = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(R.drawable.ic_launcher_foreground),
                contentDescription = "StreamMind",
                modifier = Modifier.size(42.dp)
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "StreamMind",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Watch smarter",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showPersonalizationDialog = true }) {
                Text("Personalize")
            }
        }

        SpotlightCarousel(
            videos = spotlightVideos,
            onVideoSelected = onVideoSelected
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            item {
                RecommendedForYouTray(
                    progress = visibleContinueProgress,
                    preferredCategories = personalization.selectedCategories,
                    onVideoSelected = onVideoSelected,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            if (visibleContinueProgress.isNotEmpty()) {
                item {
                    ContinueWatchingTray(
                        progress = visibleContinueProgress,
                        onVideoSelected = onVideoSelected,
                        onRemove = progressViewModel::remove
                    )
                }
            }

            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    label = { Text("Search videos, people, or topics") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Clear search"
                                )
                            }
                        }
                    }
                )
            }

            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(categories.size) { index ->
                        val category = categories[index]
                        FilterChip(
                            selected = selectedCategory == category,
                            onClick = { selectedCategory = category },
                            label = { Text(category) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (selectedCategory == "All") "All videos" else "$selectedCategory videos",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.weight(1f))
                    FilterChip(
                        selected = isGrid,
                        onClick = { isGrid = true },
                        label = { Text("Grid") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                    FilterChip(
                        selected = !isGrid,
                        onClick = { isGrid = false },
                        label = { Text("List") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            if (filteredVideos.isEmpty()) {
                item {
                    Box(modifier = Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        Text("No videos match your search.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (isGrid) {
                items(filteredVideos.chunked(2), key = { row -> row.first().id }) { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { video ->
                            Box(modifier = Modifier.weight(1f)) {
                                LibraryVideoCard(video = video, isGrid = true, onClick = { onVideoSelected(video) })
                            }
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
            } else {
                items(filteredVideos, key = { it.id }) { video ->
                    LibraryVideoCard(video = video, isGrid = false, onClick = { onVideoSelected(video) })
                }
            }
        }
    }

    if (showPersonalizationDialog) {
        PersonalizeLibraryDialog(
            initialCategories = personalization.selectedCategories,
            onSave = { selectedCategories ->
                personalizationStore.save(selectedCategories)
                personalization = personalization.copy(
                    selectedCategories = selectedCategories,
                    completed = true
                )
                showPersonalizationDialog = false
            },
            onSkip = {
                // Skipping is a completed onboarding decision. Persist an empty
                // selection so returning from PlayerScreen does not reopen the
                // dialog on every navigation cycle.
                personalizationStore.save(emptySet())
                personalization = personalization.copy(
                    selectedCategories = emptySet(),
                    completed = true
                )
                showPersonalizationDialog = false
            }
        )
    }
}

@Composable
private fun SpotlightCarousel(
    videos: List<StreamMindVideo>,
    onVideoSelected: (StreamMindVideo) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { videos.size })

    LaunchedEffect(pagerState, videos.size) {
        while (true) {
            kotlinx.coroutines.delay(4_000L)
            if (videos.isNotEmpty()) {
                val nextPage = (pagerState.currentPage + 1) % videos.size
                pagerState.animateScrollToPage(nextPage)
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(20.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .height(178.dp)
        ) { page ->
            val video = videos[page]
            SpotlightPage(
                video = video,
                onClick = { onVideoSelected(video) }
            )
        }
    }
}

@Composable
private fun SpotlightPage(video: StreamMindVideo, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(178.dp)
            .clickable(onClick = onClick)
    ) {
            Image(
                painter = painterResource(video.thumbnailRes),
                contentDescription = video.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.38f))
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp)
            ) {
                Text(
                    text = video.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Featured open movie · ${video.durationLabel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.88f)
                )
            }
            Button(
                onClick = onClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            ) {
                Text("Watch")
            }
    }
}

@Composable
private fun ContinueWatchingTray(
    progress: List<PlaybackProgressEntity>,
    onVideoSelected: (StreamMindVideo) -> Unit,
    onRemove: (String) -> Unit
) {
    val configuration = LocalConfiguration.current
    val matchingCardWidth = ((configuration.screenWidthDp.dp - 52.dp) / 2f).coerceAtLeast(132.dp)
    Column(modifier = Modifier.padding(bottom = 8.dp)) {
        Text(
            text = "Continue Watching",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 10.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(progress, key = { it.videoId }) { entry ->
                val video = StreamMindLibrary.find(entry.videoId) ?: return@items
                val progressFraction = (entry.positionMs.toFloat() / entry.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                    modifier = Modifier.width(matchingCardWidth)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .clickable { onVideoSelected(video) }
                    ) {
                        VideoArtwork(video = video, modifier = Modifier.fillMaxSize())
                        IconButton(
                            onClick = { onRemove(entry.videoId) },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove ${video.title} from Continue Watching",
                                tint = Color.White
                            )
                        }
                    }
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = video.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${formatPosition(entry.positionMs)} of ${video.durationLabel}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 3.dp)
                        )
                        androidx.compose.material3.LinearProgressIndicator(
                            progress = { progressFraction },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryVideoCard(video: StreamMindVideo, isGrid: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        if (isGrid) {
            Column {
                VideoArtwork(video = video, modifier = Modifier.fillMaxWidth().height(112.dp))
                VideoMetadata(video = video, modifier = Modifier.padding(12.dp))
            }
        } else {
            Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VideoArtwork(video = video, modifier = Modifier.width(132.dp).height(82.dp))
                VideoMetadata(video = video, modifier = Modifier.padding(start = 12.dp).weight(1f))
            }
        }
    }
}

private fun formatPosition(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000L).coerceAtLeast(0L)
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}

@Composable
fun VideoArtwork(video: StreamMindVideo, modifier: Modifier) {
    Box(
        modifier = modifier
            .background(video.accent, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(video.thumbnailRes),
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.22f))
        )
        Text(
            text = video.durationLabel,
            color = Color.White,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.5f))
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun VideoMetadata(video: StreamMindVideo, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            text = video.category.uppercase(),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = video.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
        Text(
            text = video.creator,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
