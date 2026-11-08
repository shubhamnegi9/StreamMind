package com.streammind.app

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.streammind.feature.player.PlayerScreen
import dagger.hilt.android.AndroidEntryPoint

// Intent actions broadcast from PiP remote action buttons.
private const val ACTION_PIP_PLAY_PAUSE = "com.streammind.pip.PLAY_PAUSE"
private const val ACTION_PIP_MUTE       = "com.streammind.pip.MUTE"
private const val PIP_REQUEST_CODE      = 101

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // ── Reactive PiP state ─────────────────────────────────────────────────────
    // isInPictureInPictureMode on Activity is a plain Boolean — Compose reads it
    // once and never re-observes it. Holding it in mutableStateOf means any
    // composable that reads pipActive will recompose when PiP mode changes, which
    // is what hides the chapter markers and panels when entering PiP.
    private val pipActive = mutableStateOf(false)

    // Playback + mute state mirrored here so the PiP button icons stay in sync.
    // Updated by the BroadcastReceiver and by onPictureInPictureModeChanged.
    private var pipIsPlaying = false
    private var pipIsMuted   = false

    // ── BroadcastReceiver for PiP button actions ───────────────────────────────
    // The system fires the PendingIntents we attach to RemoteActions; we catch
    // them here and forward the command to ExoPlayer via PlayerScreen's exposed
    // lambdas. Because the composable tree owns the ExoPlayer instance we use a
    // shared callback holder so the receiver can reach into it without holding a
    // direct reference to the composable.
    private var onPipPlayPause: (() -> Unit)? = null
    private var onPipMute: (() -> Unit)? = null

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ACTION_PIP_PLAY_PAUSE -> onPipPlayPause?.invoke()
                ACTION_PIP_MUTE       -> onPipMute?.invoke()
            }
        }
    }

    // ── Lifecycle ──────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)

        // Register PiP params early so the system already knows the aspect ratio
        // before the user triggers PiP for the first time.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            updatePipParams()
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = androidx.compose.ui.graphics.Color(0xFFB39DFF),
                    onPrimary = androidx.compose.ui.graphics.Color(0xFF21154A),
                    primaryContainer = androidx.compose.ui.graphics.Color(0xFF3A2B68),
                    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFFE9DDFF),
                    secondary = androidx.compose.ui.graphics.Color(0xFFE0B7FF),
                    onSecondary = androidx.compose.ui.graphics.Color(0xFF321247),
                    secondaryContainer = androidx.compose.ui.graphics.Color(0xFF51236D),
                    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFFF7D9FF),
                    tertiary = androidx.compose.ui.graphics.Color(0xFF8DD7FF),
                    background = androidx.compose.ui.graphics.Color(0xFF0C0814),
                    surface = androidx.compose.ui.graphics.Color(0xFF161020),
                    surfaceVariant = androidx.compose.ui.graphics.Color(0xFF261A38),
                    onSurface = androidx.compose.ui.graphics.Color(0xFFF7F1FF),
                    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFFD0C4DD),
                    outline = androidx.compose.ui.graphics.Color(0xFF9789AA)
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    var showSplash by remember { mutableStateOf(true) }
                    var selectedVideoId by rememberSaveable { mutableStateOf<String?>(null) }
                    val selectedVideo = selectedVideoId?.let(StreamMindLibrary::find)

                    // System/device back button, not just the in-app "‹ Library"
                    // button: without this, back on the player screen exits the
                    // whole app instead of returning to the library. enabled is
                    // tied to selectedVideo != null so it does nothing extra when
                    // already on the library screen (default back behavior there
                    // — exit the app — is correct).
                    androidx.activity.compose.BackHandler(enabled = selectedVideo != null) {
                        selectedVideoId = null
                        pipIsPlaying = false
                        pipIsMuted = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) updatePipParams()
                    }

                    if (showSplash) {
                        AnimatedSplashScreen(onFinished = { showSplash = false })
                    } else if (selectedVideo == null) {
                        VideoLibraryScreen(
                            onVideoSelected = { video -> selectedVideoId = video.id }
                        )
                    } else {
                        PlayerScreen(
                            videoId = selectedVideo.id,
                            videoUrl = selectedVideo.videoUrl,
                            videoDurationMs = selectedVideo.durationMs,
                            videoTitle = selectedVideo.title,
                            videoSubtitle = "${selectedVideo.creator} · ${selectedVideo.category}",
                            onBackToLibrary = {
                                selectedVideoId = null
                                pipIsPlaying = false
                                pipIsMuted = false
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) updatePipParams()
                            },
                            // Reactive: pipActive is mutableStateOf so Compose will
                            // recompose this call site when PiP mode toggles.
                            isInPipMode = pipActive.value,
                            // Wire callbacks so the PiP receiver can drive the player.
                            onRegisterPipCallbacks = { playPause, mute ->
                                onPipPlayPause = playPause
                                onPipMute = mute
                            },
                            // Called by PlayerScreen whenever playback/mute state
                            // changes so we can refresh the PiP button icons.
                            onPlaybackStateChanged = { isPlaying, isMuted ->
                                pipIsPlaying = isPlaying
                                pipIsMuted   = isMuted
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    updatePipParams()
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Register the receiver for PiP button intents while the activity is
        // at least started. RECEIVER_NOT_EXPORTED: we only send from this process.
        val filter = IntentFilter().apply {
            addAction(ACTION_PIP_PLAY_PAUSE)
            addAction(ACTION_PIP_MUTE)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pipReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(pipReceiver, filter)
        }
    }

    override fun onStop() {
        super.onStop()
        unregisterReceiver(pipReceiver)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        // Write to the mutableStateOf so Compose recomposes and the chapter
        // markers / panels are hidden while PiP is active.
        pipActive.value = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Only enter PiP if the video is actively playing. If it's paused the
        // user expects the normal home-button behaviour (background the app),
        // not a tiny frozen window appearing over their home screen.
        if (!pipIsPlaying) return

        // Manual entry for API 26–30; API 31+ uses setAutoEnterEnabled.
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S) {
            @Suppress("NewApi")
            enterPictureInPictureMode(
                PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
            )
        }
    }

    // ── PiP params builder ─────────────────────────────────────────────────────

    @RequiresApi(Build.VERSION_CODES.O)
    private fun updatePipParams() {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(buildPipActions())
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Auto-enter PiP on home/recents only when actively playing.
                    // When paused, pressing home should background the app normally.
                    setAutoEnterEnabled(pipIsPlaying)
                }
            }
            .build()
        setPictureInPictureParams(params)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildPipActions(): List<RemoteAction> {
        val actions = mutableListOf<RemoteAction>()

        // ── Play / Pause ───────────────────────────────────────────────────────
        val playPauseIconRes = if (pipIsPlaying)
            android.R.drawable.ic_media_pause
        else
            android.R.drawable.ic_media_play
        val playPauseLabel = if (pipIsPlaying) "Pause" else "Play"

        val playPauseIntent = PendingIntent.getBroadcast(
            this,
            PIP_REQUEST_CODE,
            Intent(ACTION_PIP_PLAY_PAUSE).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        actions += RemoteAction(
            Icon.createWithResource(this, playPauseIconRes),
            playPauseLabel,
            playPauseLabel,
            playPauseIntent
        )

        // ── Mute / Unmute ──────────────────────────────────────────────────────
        // android.R.drawable doesn't have a dedicated mute icon that's guaranteed
        // present on all API levels, so we use the volume icons from the system.
        // These are internal resources that exist on all API 26+ devices.
        val muteIconRes = if (pipIsMuted)
            android.R.drawable.ic_lock_silent_mode_off
        else
            android.R.drawable.ic_lock_silent_mode
        val muteLabel = if (pipIsMuted) "Unmute" else "Mute"

        val muteIntent = PendingIntent.getBroadcast(
            this,
            PIP_REQUEST_CODE + 1,
            Intent(ACTION_PIP_MUTE).setPackage(packageName),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        actions += RemoteAction(
            Icon.createWithResource(this, muteIconRes),
            muteLabel,
            muteLabel,
            muteIntent
        )

        return actions
    }
}
