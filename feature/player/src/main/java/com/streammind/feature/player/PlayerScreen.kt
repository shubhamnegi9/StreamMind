package com.streammind.feature.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.streammind.feature.accessibility.nearestSegment
import kotlinx.coroutines.delay

private const val CONTROLS_AUTO_HIDE_DELAY_MS = 3000L
private const val DOUBLE_TAP_SEEK_MS = 10_000L

// How long offline+buffering must persist before showing the error overlay.
// Prevents a flash when seeking while offline — ExoPlayer briefly enters
// STATE_BUFFERING before resolving the seek from its local buffer.
private const val OFFLINE_BUFFER_GRACE_MS = 600L

@Composable
fun PlayerScreen(
    videoId: String,
    videoUrl: String,
    videoDurationMs: Long,
    videoTitle: String = "Big Buck Bunny",
    videoSubtitle: String = "StreamMind original · Watch, ask, and explore",
    onBackToLibrary: (() -> Unit)? = null,
    isInPipMode: Boolean = false,
    onRegisterPipCallbacks: ((playPause: () -> Unit, mute: () -> Unit) -> Unit)? = null,
    onPlaybackStateChanged: ((isPlaying: Boolean, isMuted: Boolean) -> Unit)? = null,
    viewModel: PlayerViewModel = hiltViewModel(),
    askViewModel: AskVideoViewModel = hiltViewModel(),
    accessibilityViewModel: AccessibilityViewModel = hiltViewModel(),
    progressViewModel: PlaybackProgressViewModel = hiltViewModel(),
    semanticSearchViewModel: SemanticSearchViewModel = hiltViewModel(),
    keyMomentsViewModel: KeyMomentsViewModel = hiltViewModel(),
    quizViewModel: QuizViewModel = hiltViewModel(),
    sceneQuizViewModel: SceneQuizViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()
    val askState by askViewModel.uiState.collectAsState()
    val accessibilityState by accessibilityViewModel.uiState.collectAsState()
    val semanticSearchState by semanticSearchViewModel.uiState.collectAsState()
    val keyMomentsState by keyMomentsViewModel.uiState.collectAsState()
    val quizState by quizViewModel.uiState.collectAsState()
    val sceneQuizState by sceneQuizViewModel.uiState.collectAsState()
    var sceneQuizExpanded by remember { mutableStateOf(false) }
    var isMuted by remember { mutableStateOf(false) }
    var askPanelVisible by remember { mutableStateOf(false) }
    var accessibilityPanelVisible by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var speedMenuExpanded by remember { mutableStateOf(false) }

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
        }
    }
    val playbackState by rememberVideoPlaybackState(exoPlayer)
    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    // Prefer the real duration ExoPlayer reports once metadata loads. The
    // catalog's hardcoded durationMs is only a fallback for the brief window
    // before that happens (avoids a "0:00" flash) — trusting the hardcoded
    // value indefinitely is exactly the bug class that bit the original
    // Big Buck Bunny duration mismatch, just multiplied by every video in a
    // library instead of one.
    val effectiveDurationMs = playbackState.durationMs.takeIf { it > 0 } ?: videoDurationMs
        // Restore and continuously persist progress through Room. Each title gets
    // its own row, allowing the library to render a real Continue Watching tray.
    LaunchedEffect(videoId) {
        val savedPosition = progressViewModel.continueWatching.value
            .firstOrNull { it.videoId == videoId }?.positionMs ?: 0L
        if (savedPosition > 5_000L && savedPosition < videoDurationMs - 5_000L) {
            delay(250L)
            exoPlayer.seekTo(savedPosition)
        }
    }
    LaunchedEffect(videoId) {
        while (true) {
            progressViewModel.save(videoId, exoPlayer.currentPosition, effectiveDurationMs)
            delay(1_000L)
        }
    }
    LaunchedEffect(videoId) {
        keyMomentsViewModel.resetForVideo(videoId)
        quizViewModel.resetForOpen(videoId)
        sceneQuizViewModel.resetForOpen(videoId)
        sceneQuizExpanded = false
    }

    LaunchedEffect(videoId, uiState.transcriptVideoId, uiState.transcript.size) {
        if (uiState.transcriptVideoId == videoId && uiState.transcript.isNotEmpty()) {
            semanticSearchViewModel.prepare(videoId)
        }
    }

    val contentListState = rememberLazyListState()
    val currentScene = uiState.transcript.lastOrNull { segment ->
        playbackState.currentPositionMs >= segment.startMs
    }
    LaunchedEffect(videoId, uiState.transcriptVideoId, currentScene?.startMs) {
        if (uiState.transcriptVideoId == videoId && currentScene != null) {
            sceneQuizViewModel.showScene(videoId, currentScene)
        }
    }

    DisposableEffect(exoPlayer) {
        onRegisterPipCallbacks?.invoke(
            { if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play() },
            { isMuted = !isMuted; exoPlayer.volume = if (isMuted) 0f else 1f }
        )
        onDispose { onRegisterPipCallbacks?.invoke({}, {}) }
    }

    LaunchedEffect(playbackState.isPlaying, isMuted) {
        onPlaybackStateChanged?.invoke(playbackState.isPlaying, isMuted)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> if (!isInPipMode) exoPlayer.pause()
                Lifecycle.Event.ON_RESUME -> exoPlayer.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            progressViewModel.save(videoId, exoPlayer.currentPosition, effectiveDurationMs)
            exoPlayer.release()
        }
    }

    LaunchedEffect(videoId) { viewModel.loadVideo(videoId) }

    LaunchedEffect(videoId, uiState.transcriptVideoId, uiState.transcript.isNotEmpty()) {
        if (uiState.transcriptVideoId == videoId && uiState.transcript.isNotEmpty()) {
            askViewModel.index(videoId)
        }
    }

    // ── Offline + buffering debounce ───────────────────────────────────────────
    // isOfflineBuffering is the raw signal; offlineBufferingConfirmed is the
    // debounced version — only true after the raw signal persists for the grace
    // period. This prevents a flash of the error overlay on every seek.
    val isOfflineBuffering = !uiState.isOnline && playbackState.isBuffering
    var offlineBufferingConfirmed by remember { mutableStateOf(false) }
    LaunchedEffect(isOfflineBuffering) {
        if (isOfflineBuffering) {
            delay(OFFLINE_BUFFER_GRACE_MS)
            offlineBufferingConfirmed = true
        } else {
            offlineBufferingConfirmed = false
        }
    }

    // ── Reconnect state ────────────────────────────────────────────────────────
    // isReconnecting is set the moment uiState.isOnline flips back to true while
    // an error was showing. It serves two purposes:
    //   1. Dismisses the error overlay immediately (before ExoPlayer is ready)
    //   2. Shows the buffering spinner during the gap between prepare() and
    //      ExoPlayer entering STATE_BUFFERING
    //
    // We track this separately from hasPlaybackProblem because playerError is
    // only cleared by ExoPlayer reaching STATE_READY — if we wait for that, the
    // overlay stays up the whole time the player is re-buffering after reconnect.
    // Triggering directly on isOnline lets us dismiss the overlay instantly.
    var isReconnecting by remember { mutableStateOf(false) }
    var hadProblemBeforeOnline by remember { mutableStateOf(false) }

    // When a problem is active, latch the flag so we know to reconnect.
    val rawProblem = playbackState.playerError != null || offlineBufferingConfirmed
    LaunchedEffect(rawProblem) {
        if (rawProblem) hadProblemBeforeOnline = true
    }

    // React to connectivity returning while a problem was active.
    LaunchedEffect(uiState.isOnline) {
        if (uiState.isOnline && hadProblemBeforeOnline) {
            hadProblemBeforeOnline = false
            isReconnecting = true
            // prepare() resets ExoPlayer from any error/idle state; play() then
            // starts buffering. Both are needed — play() alone won't recover from
            // a fatal SOURCE_ERROR.
            exoPlayer.prepare()
            exoPlayer.play()
        }
    }

    // Clear isReconnecting once the player is actually buffering or playing.
    LaunchedEffect(playbackState.isBuffering, playbackState.isPlaying) {
        if (isReconnecting && (playbackState.isBuffering || playbackState.isPlaying)) {
            isReconnecting = false
        }
    }

    // The overlay shows when there is a raw problem AND we are NOT mid-reconnect.
    // Once isReconnecting is true the overlay is dismissed immediately, even before
    // ExoPlayer has cleared playerError (which only happens at STATE_READY).
    val hasPlaybackProblem = rawProblem && !isReconnecting

    // ── Controls visibility ────────────────────────────────────────────────────
    var controlsVisible by remember { mutableStateOf(true) }

    // Hide controls the moment the error overlay appears so they don't show
    // underneath it.
    LaunchedEffect(hasPlaybackProblem) {
        if (hasPlaybackProblem) controlsVisible = false
    }

    LaunchedEffect(controlsVisible, playbackState.isPlaying, speedMenuExpanded) {
        if (controlsVisible && playbackState.isPlaying && !speedMenuExpanded) {
            delay(CONTROLS_AUTO_HIDE_DELAY_MS)
            if (!speedMenuExpanded) controlsVisible = false
        }
    }

    // Computed once from screen width rather than using Modifier.aspectRatio —
    // an explicit height forces exact top-down layout constraints on the native
    // PlayerView child instead of relying on Compose negotiating measurement
    // with it, which is the more deterministic choice for AndroidView interop.
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val playerHeightDp = remember(configuration.screenWidthDp) {
        (configuration.screenWidthDp * 9f / 16f).dp
    }

    if (isInPipMode) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(playerHeightDp),
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                    controllerAutoShow = false
                    controllerHideOnTouch = true
                    hideController()
                    // The Compose container is fixed at 16:9. ZOOM fills that
                    // viewport for every source aspect ratio, avoiding the
                    // shorter-looking FIT letterbox on Sintel, Tears of Steel,
                    // and Spring.
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        onBackToLibrary?.let { onBack ->
            Row(
                modifier = Modifier
                    .clickable(onClick = onBack)
                    .padding(start = 8.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowBack,
                    contentDescription = "Back to Library",
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "Library",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            Column(modifier = Modifier.fillMaxSize()) {
                PlayerSurface(
            exoPlayer = exoPlayer,
            playbackState = playbackState,
            effectiveDurationMs = effectiveDurationMs,
            hasPlaybackProblem = hasPlaybackProblem,
            isReconnecting = isReconnecting,
            controlsVisible = controlsVisible,
            playbackSpeed = playbackSpeed,
            onPlaybackSpeedChange = { playbackSpeed = it },
            speedMenuExpanded = speedMenuExpanded,
            onSpeedMenuExpandedChange = { speedMenuExpanded = it },
            onControlsVisibleChange = { controlsVisible = it }
        )

        ChapterMarkerOverlay(
            chapters = uiState.chapters,
            videoDurationMs = effectiveDurationMs,
            onChapterTapped = { chapter -> exoPlayer.seekTo(chapter.startMs) },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
        )

        if (!uiState.isOnline && sceneQuizState.question == null) {
            OfflinePanelPlaceholder(
                featureName = "Scene Quiz",
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        } else {
            SceneQuizPanel(
                state = sceneQuizState,
                expanded = sceneQuizExpanded,
                onExpandedChange = {
                    sceneQuizExpanded = it
                    if (it) {
                        askPanelVisible = false
                        accessibilityPanelVisible = false
                        accessibilityViewModel.stop()
                    }
                },
                onSelectOption = sceneQuizViewModel::selectOption,
                onSubmit = sceneQuizViewModel::submitAnswer,
                onResetAnswer = sceneQuizViewModel::resetAnswer,
                onGenerate = sceneQuizViewModel::generateCurrentScene,
                onSeekTo = { startMs -> exoPlayer.seekTo(startMs) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }

            LazyColumn(
                state = contentListState,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 112.dp)
            ) {
            item {
                VideoDetails(
                    title = videoTitle,
                    subtitle = videoSubtitle,
                    chapterCount = uiState.chapters.size,
                    durationMs = effectiveDurationMs,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                )
            }
            item { SectionHeader("Chapters") }
            when {
                !uiState.isOnline && uiState.chapters.isEmpty() ->
                    item {
                        OfflinePanelPlaceholder(
                            featureName = "AI Chapters",
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                uiState.isLoadingChapters -> item { LoadingRow("Generating chapters…") }
                uiState.chapterError != null ->
                    item { ErrorText("Chapters unavailable: ${uiState.chapterError}") }
                uiState.chapters.isEmpty() -> item { ErrorText("No chapters yet.") }
                else -> item {
                    ChapterList(
                        chapters = uiState.chapters,
                        onChapterTapped = { chapter -> exoPlayer.seekTo(chapter.startMs) }
                    )
                }
            }

            item { SectionHeader("Transcript") }
            when {
                uiState.isLoadingTranscript -> item { LoadingRow("Loading transcript…") }
                uiState.transcriptError != null ->
                    item { ErrorText("Transcript unavailable: ${uiState.transcriptError}") }
                else -> items(uiState.transcript) { segment ->
                    TranscriptRow(segment.startMs, segment.text)
                }
            }

            item {
                Spacer(modifier = Modifier.height(28.dp))
            }

            item {
                if (!uiState.isOnline && keyMomentsState.moments.isEmpty()) {
                    OfflinePanelPlaceholder(
                        featureName = "Key Moments & Highlights",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                } else {
                    KeyMomentsPanel(
                        state = keyMomentsState,
                        onGenerate = { keyMomentsViewModel.prepare(videoId, uiState.transcript) },
                        onRetry = { keyMomentsViewModel.retry(videoId, uiState.transcript) },
                        onSeekTo = { startMs -> exoPlayer.seekTo(startMs) },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
            }

            item {
                if (!uiState.isOnline) {
                    OfflinePanelPlaceholder(
                        featureName = "Semantic Search",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                } else {
                    SemanticSearchPanel(
                        state = semanticSearchState,
                        onQueryChange = semanticSearchViewModel::onQueryChange,
                        onSearch = { semanticSearchViewModel.search(videoId) },
                        onClear = semanticSearchViewModel::clear,
                        onSeekTo = { startMs -> exoPlayer.seekTo(startMs) },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            }

            item {
                if (!uiState.isOnline && quizState.questions.isEmpty()) {
                    OfflinePanelPlaceholder(
                        featureName = "AI Quiz & Learning Mode",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                } else {
                    QuizPanel(
                        state = quizState,
                        onGenerate = { quizViewModel.prepare(videoId, uiState.transcript) },
                        onStart = {
                            askPanelVisible = false
                            accessibilityPanelVisible = false
                            accessibilityViewModel.stop()
                            quizViewModel.start()
                        },
                        onSelectOption = quizViewModel::selectOption,
                        onSubmit = quizViewModel::submitAnswer,
                        onNext = quizViewModel::nextQuestion,
                        onRestart = quizViewModel::restart,
                        onReset = quizViewModel::resetQuiz,
                        onRetry = { quizViewModel.retry(videoId, uiState.transcript) },
                        onSeekTo = { startMs -> exoPlayer.seekTo(startMs) },
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
                    )
                }
            }


            }

            }

            androidx.compose.animation.AnimatedVisibility(
                visible = askPanelVisible || accessibilityPanelVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 5 }),
                exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 5 })
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable {
                                askPanelVisible = false
                                accessibilityPanelVisible = false
                                accessibilityViewModel.stop()
                            }
                            .zIndex(1f)
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 16.dp, bottom = 84.dp)
                            .zIndex(2f)
                            .fillMaxWidth(0.9f)
                            .heightIn(max = 520.dp)
                            .verticalScroll(rememberScrollState())
                            .clickable { }
                    ) {
                    if (askPanelVisible) {
                        val hasTranscript = uiState.transcript.isNotEmpty()
                        when {
                            !uiState.isOnline -> OfflinePanelPlaceholder(
                                featureName = "Ask about this video",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                            !hasTranscript -> OfflinePanelPlaceholder(
                                featureName = "Ask about this video",
                                message = "No transcript is available for this video, so there's nothing to search.",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                            else -> AskVideoPanel(
                                state = askState,
                                onQuestionChange = askViewModel::onQuestionChange,
                                onAsk = { askViewModel.ask(videoId) },
                                onSeekTo = { startMs -> exoPlayer.seekTo(startMs) },
                                onDismiss = { askPanelVisible = false },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        }
                    }
                    if (accessibilityPanelVisible) {
                        if (uiState.isOnline) {
                            AccessibilityPanel(
                                state = accessibilityState,
                                onDescribeScene = {
                                    val atMs = exoPlayer.currentPosition
                                    val nearbyText = nearestSegment(uiState.transcript, atMs)?.text.orEmpty()
                                    accessibilityViewModel.describeCurrentScene(videoUrl, atMs, nearbyText)
                                },
                                onReplay = accessibilityViewModel::replay,
                                onDismiss = {
                                    accessibilityViewModel.stop()
                                    accessibilityPanelVisible = false
                                },
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
                            )
                        } else {
                            OfflinePanelPlaceholder(
                                featureName = "Audio scene description",
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                            )
                        }
                    }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                FloatingActionButton(
                    onClick = {
                        val opening = !askPanelVisible
                        askPanelVisible = opening
                        if (opening) askViewModel.reset()
                        if (opening) {
                            accessibilityViewModel.stop()
                            accessibilityPanelVisible = false
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = "Ask StreamMind")
                }
                FloatingActionButton(
                    onClick = {
                        val opening = !accessibilityPanelVisible
                        accessibilityPanelVisible = opening
                        if (opening) {
                            accessibilityViewModel.reset()
                            askPanelVisible = false
                        } else {
                            accessibilityViewModel.stop()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.secondary
                ) {
                    Icon(Icons.Filled.RecordVoiceOver, contentDescription = "Audio scene description")
                }
            }
        }
    }
}

@Composable
private fun PlayerSurface(
    exoPlayer: ExoPlayer,
    playbackState: VideoPlaybackState,
    effectiveDurationMs: Long,
    hasPlaybackProblem: Boolean,
    isReconnecting: Boolean,
    controlsVisible: Boolean,
    playbackSpeed: Float,
    onPlaybackSpeedChange: (Float) -> Unit,
    speedMenuExpanded: Boolean,
    onSpeedMenuExpandedChange: (Boolean) -> Unit,
    onControlsVisibleChange: (Boolean) -> Unit
) {
    val context = LocalContext.current

    // rememberUpdatedState ensures the lambdas inside pointerInput(Unit) always
    // read the *current* value of controlsVisible and onControlsVisibleChange,
    // even though the pointerInput block is never re-executed after first
    // composition (it is keyed on Unit). Without this, the lambda captures the
    // initial value at creation time and the toggle becomes stale — e.g.
    // controlsVisible is permanently read as `true`, so tapping always hides but
    // never shows (or vice-versa).
    val currentControlsVisible by rememberUpdatedState(controlsVisible)
    val currentOnControlsVisibleChange by rememberUpdatedState(onControlsVisibleChange)

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val playerHeightDp = remember(configuration.screenWidthDp) {
        (configuration.screenWidthDp * 9f / 16f).dp
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(playerHeightDp)
    ) {
        // ── Video surface ──────────────────────────────────────────────────────
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(context).apply {
                    player = exoPlayer
                    useController = false
                    controllerAutoShow = false
                    controllerHideOnTouch = true
                    hideController()
                    // The Compose container is fixed at 16:9. ZOOM fills that
                    // viewport for every source aspect ratio, avoiding the
                    // shorter-looking FIT letterbox on Sintel, Tears of Steel,
                    // and Spring.
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                }
            }
        )

        // ── Gesture zones ──────────────────────────────────────────────────────
        // Three full-height columns laid out as: [left 33%][centre 34%][right 33%]
        // Each zone is a separate Box so double-tap areas (left/right) are distinct
        // from the centre-tap-only area.
        //
        // All lambdas read via rememberUpdatedState refs so they always see the
        // current controlsVisible value, not a captured-at-creation snapshot.

        // Left zone — rewind on double-tap
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.33f)
                .align(Alignment.CenterStart)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            currentOnControlsVisibleChange(!currentControlsVisible)
                        },
                        onDoubleTap = {
                            val newPos = (exoPlayer.currentPosition - DOUBLE_TAP_SEEK_MS)
                                .coerceAtLeast(0L)
                            exoPlayer.seekTo(newPos)
                        }
                    )
                }
        )

        // Right zone — forward on double-tap
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.33f)
                .align(Alignment.CenterEnd)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            currentOnControlsVisibleChange(!currentControlsVisible)
                        },
                        onDoubleTap = {
                            val newPos = (exoPlayer.currentPosition + DOUBLE_TAP_SEEK_MS)
                                .coerceAtMost(effectiveDurationMs.coerceAtLeast(0L))
                            exoPlayer.seekTo(newPos)
                        }
                    )
                }
        )

        // Centre zone — toggle controls only
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.34f)
                .align(Alignment.Center)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            currentOnControlsVisibleChange(!currentControlsVisible)
                        }
                    )
                }
        )

        // ── Controls overlay ───────────────────────────────────────────────────
        AnimatedVisibility(
            visible = (controlsVisible || speedMenuExpanded) && !playbackState.isEnded,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PlayerControls(
                playbackState = playbackState,
                durationMs = effectiveDurationMs,
                playbackSpeed = playbackSpeed,
                onPlaybackSpeedChange = onPlaybackSpeedChange,
                onSpeedMenuExpandedChange = onSpeedMenuExpandedChange,
                seekEnabled = !hasPlaybackProblem,
                onPlayPause = {
                    when {
                        playbackState.playerError != null -> {
                            exoPlayer.prepare()
                            exoPlayer.play()
                        }
                        exoPlayer.isPlaying -> exoPlayer.pause()
                        else -> exoPlayer.play()
                    }
                },
                onSeekTo = { positionMs -> exoPlayer.seekTo(positionMs) }
            )
        }

        // ── Buffering / reconnect spinner ──────────────────────────────────────
        // Shown for normal buffering, and while isReconnecting (the gap between
        // network returning and ExoPlayer reaching STATE_BUFFERING).
        // Neither case has a playback problem, so no need to check that separately.
        val showSpinner = playbackState.isBuffering || isReconnecting
        if (showSpinner) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
                strokeWidth = 3.dp
            )
        }

        // ── Offline / error overlay ────────────────────────────────────────────
        // Drawn on top of everything. The pointerInput consumer swallows all
        // touches so the gesture zones underneath never fire while this is visible
        // — without it, taps would toggle the controls overlay behind this screen.
        if (hasPlaybackProblem) {
            OfflineOverlay(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture { awaitPointerEvent() }
                    }
            )
        }

        // ── Replay button ──────────────────────────────────────────────────────
        if (playbackState.isEnded && !hasPlaybackProblem) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
                    .pointerInput(Unit) {
                        awaitEachGesture { awaitPointerEvent() }
                    },
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = {
                        exoPlayer.seekTo(0L)
                        exoPlayer.play()
                    },
                    modifier = Modifier
                        .size(72.dp)
                        .background(Color.White.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Filled.Replay,
                        contentDescription = "Replay",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Column(modifier = Modifier.padding(top = 24.dp)) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 20.dp),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
        )
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
        )
    }
}

@Composable
private fun VideoDetails(
    title: String,
    subtitle: String,
    chapterCount: Int,
    durationMs: Long,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
        Row(
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = {},
                label = { Text(formatDuration(durationMs)) },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
            AssistChip(
                onClick = {},
                label = { Text("$chapterCount chapters") },
                colors = AssistChipDefaults.assistChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            )
        }
    }
}

private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs.coerceAtLeast(0L) + 500L) / 1_000L
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes mins $seconds secs"
}

@Composable
private fun TranscriptRow(startMs: Long, text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp)
            .background(MaterialTheme.colorScheme.surface, androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "%d:%02d".format(startMs / 60_000, (startMs / 1000) % 60),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        Text(text = text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LoadingRow(label: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
        Text(text = label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ErrorText(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.bodyMedium
    )
}
