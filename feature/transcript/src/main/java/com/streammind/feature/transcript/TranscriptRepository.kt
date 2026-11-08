package com.streammind.feature.transcript

import android.content.Context
import com.streammind.data.local.TranscriptDao
import com.streammind.data.local.TranscriptSegmentEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject

@Serializable
data class TranscriptSegmentDto(val startMs: Long, val endMs: Long, val text: String)

/**
 * Phase 1: transcripts are pre-generated (e.g. via Whisper offline) and shipped as
 * JSON assets, so Phase 1 doesn't depend on getting live SpeechRecognizer working
 * perfectly first. Swap getOrCreateTranscript's asset-read for a real SpeechRecognizer
 * or Whisper API call later — nothing above this class needs to change.
 */
interface TranscriptRepository {
    suspend fun getOrCreateTranscript(videoId: String): List<TranscriptSegmentEntity>
}

class AssetTranscriptRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val transcriptDao: TranscriptDao
) : TranscriptRepository {

    override suspend fun getOrCreateTranscript(videoId: String): List<TranscriptSegmentEntity> {
        val json = context.assets.open("transcripts/$videoId.json")
            .bufferedReader()
            .use { it.readText() }

        val dtos = Json.decodeFromString<List<TranscriptSegmentDto>>(json)
        val sourceSegments = dtos.map {
            TranscriptSegmentEntity(
                videoId = videoId,
                startMs = it.startMs,
                endMs = it.endMs,
                text = it.text
            )
        }
        val cachedSegments = transcriptDao.getSegments(videoId)
        val cacheMatchesSource = cachedSegments.map { segment ->
            Triple(segment.startMs, segment.endMs, segment.text)
        } == sourceSegments.map { segment ->
            Triple(segment.startMs, segment.endMs, segment.text)
        }

        if (!cacheMatchesSource) {
            transcriptDao.replaceForVideo(videoId, sourceSegments)
        }
        return transcriptDao.getSegments(videoId)
    }
}
