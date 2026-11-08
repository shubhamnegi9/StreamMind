package com.streammind.feature.accessibility

import com.streammind.core.common.DispatcherProvider
import kotlinx.coroutines.withContext
import javax.inject.Inject

class DescribeSceneUseCase @Inject constructor(
    private val accessibilityRepository: AccessibilityRepository,
    private val dispatchers: DispatcherProvider
) {
    suspend operator fun invoke(
        videoUrl: String,
        atMs: Long,
        nearbyTranscriptText: String
    ): Result<String> = runCatching {
        val frame = withContext(dispatchers.io) {
            VideoFrameExtractor.extractFrameJpeg(videoUrl, atMs)
        } ?: throw IllegalStateException("Couldn't grab a frame from the video at this position")

        accessibilityRepository.describeScene(frame, nearbyTranscriptText)
    }
}
