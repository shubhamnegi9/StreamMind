package com.streammind.feature.aichapters

import com.streammind.data.local.ChapterEntity

interface AiRepository {
    /**
     * Sends the transcript to the model and returns generated chapters.
     * Implementations are responsible for persisting the result via ChapterDao —
     * callers just get back what's now in the database for this video.
     */
    suspend fun generateChapters(videoId: String, transcript: String): List<ChapterEntity>
}
