package com.streammind.feature.accessibility

interface AccessibilityRepository {
    /**
     * @param imageJpeg a single video frame, already scaled down
     * @param nearbyTranscriptText transcript context around the timestamp the
     *   frame was taken from — helps the model describe what's happening, not
     *   just what's visually in frame
     */
    suspend fun describeScene(imageJpeg: ByteArray, nearbyTranscriptText: String): String
}
