package com.streammind.feature.accessibility

import kotlinx.coroutines.delay
import javax.inject.Inject

class FakeAccessibilityRepository @Inject constructor() : AccessibilityRepository {

    override suspend fun describeScene(imageJpeg: ByteArray, nearbyTranscriptText: String): String {
        delay(500)
        // Offline demo mode never actually looks at the frame — it just echoes
        // the transcript context back in a visually-descriptive tone, so the UI
        // and TTS flow are still exercisable without a real vision call.
        return if (nearbyTranscriptText.isNotBlank()) {
            "A wide shot showing the scene where $nearbyTranscriptText " +
                "(offline demo description — no image was analyzed)"
        } else {
            "A wide shot of the meadow setting. (Offline demo description — no image was analyzed.)"
        }
    }
}
