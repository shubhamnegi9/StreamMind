package com.streammind.feature.accessibility

import android.content.Context
import android.speech.tts.TextToSpeech
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Thin wrapper around Android's TextToSpeech engine. Holds Application context
 * (not an Activity), so it's safe to keep alive across the ViewModel's lifetime —
 * the caller is still responsible for calling shutdown() when done (see
 * AccessibilityViewModel.onCleared) since TextToSpeech holds native resources
 * that don't get cleaned up by garbage collection alone.
 */
class SceneDescriptionSpeaker @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var tts: TextToSpeech? = null
    private var isReady = false

    fun initialize() {
        if (tts != null) return
        tts = TextToSpeech(context) { status ->
            isReady = status == TextToSpeech.SUCCESS
        }
    }

    fun speak(text: String) {
        if (!isReady) return
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isReady = false
    }

    private companion object {
        const val UTTERANCE_ID = "scene_description"
    }
}
