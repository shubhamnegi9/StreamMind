package com.streammind.feature.aiqa

import com.streammind.data.local.EmbeddingVector
import com.streammind.data.local.TranscriptDao
import javax.inject.Inject

/**
 * Embeds every transcript segment for a video, one API call per segment.
 *
 * This is the expensive step in the whole RAG pipeline, so it's guarded twice:
 * it no-ops entirely if nothing is missing an embedding, and it only embeds the
 * specific segments that lack one. Re-opening a video is therefore free.
 */
class EmbedTranscriptUseCase @Inject constructor(
    private val transcriptDao: TranscriptDao,
    private val qaRepository: QaRepository
) {
    suspend operator fun invoke(videoId: String): Result<Unit> = runCatching {
        if (transcriptDao.countSegmentsMissingEmbeddings(videoId) == 0) return@runCatching

        val segments = transcriptDao.getSegments(videoId)
        segments.filter { it.embedding == null }.forEach { segment ->
            val vector = qaRepository.embedDocument(segment.text)
            transcriptDao.updateEmbedding(segment.id, EmbeddingVector(vector))
        }
    }
}
