package com.streammind.feature.aichapters

import com.streammind.data.local.KeyMomentDao
import com.streammind.data.local.KeyMomentEntity
import com.streammind.data.local.TranscriptSegmentEntity
import javax.inject.Inject

class GenerateKeyMomentsUseCase @Inject constructor(
    private val keyMomentRepository: KeyMomentRepository,
    private val keyMomentDao: KeyMomentDao
) {
    suspend operator fun invoke(
        videoId: String,
        transcript: List<TranscriptSegmentEntity>
    ): Result<List<KeyMomentEntity>> = runCatching {
        AiGenerationGate.withKey("key-moments:$videoId") {
        if (keyMomentDao.hasMoments(videoId)) {
            val cached = keyMomentDao.getMoments(videoId)
            if (cachedBelongsToTranscript(cached, transcript)) {
                return@withKey cached
            }
            // A previous app version could have generated rows while the player
            // still held the preceding video's transcript. Do not trust those
            // rows merely because their videoId matches; remove them and rebuild
            // from the transcript that is actually loaded for this video.
            // Keep the previous rows until replacement succeeds so a 429 does
            // not destroy the last usable cached result.
        }

        val transcriptText = transcript.joinToString("\n") { "[${it.startMs}ms] ${it.text}" }
        keyMomentRepository.generateKeyMoments(videoId, transcriptText)
        }
    }

    private fun cachedBelongsToTranscript(
        cached: List<KeyMomentEntity>,
        transcript: List<TranscriptSegmentEntity>
    ): Boolean {
        if (cached.isEmpty() || transcript.isEmpty()) return false

        // The fake generator always includes the final transcript cue. This
        // catches stale cached highlights when a closing-credit segment is
        // added or the video timing data is corrected.
        if (cached.maxOf { it.startMs } != transcript.maxOf { it.startMs }) return false

        val transcriptByStart = transcript.associateBy { it.startMs }
        if (cached.any { it.startMs !in transcriptByStart }) return false
        val matchingMoments = cached.count { moment ->
            val segment = transcriptByStart[moment.startMs] ?: return@count false
            val momentTokens = meaningfulTokens("${moment.title} ${moment.description}")
            val transcriptTokens = meaningfulTokens(segment.text)
            momentTokens.intersect(transcriptTokens).isNotEmpty()
        }
        return matchingMoments >= minOf(2, cached.size)
    }

    private fun meaningfulTokens(text: String): Set<String> =
        Regex("[a-zA-Z0-9]{4,}")
            .findAll(text.lowercase())
            .map { it.value }
            .toSet()
}
