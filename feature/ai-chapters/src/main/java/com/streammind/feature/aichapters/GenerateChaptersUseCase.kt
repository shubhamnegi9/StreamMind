package com.streammind.feature.aichapters

import com.streammind.data.local.ChapterDao
import com.streammind.data.local.ChapterEntity
import com.streammind.data.local.TranscriptSegmentEntity
import javax.inject.Inject

class GenerateChaptersUseCase @Inject constructor(
    private val aiRepository: AiRepository,
    private val chapterDao: ChapterDao
) {
    /**
     * Returns cached chapters if this video already has them, otherwise generates
     * new ones. Callers that explicitly want to regenerate (e.g. a "Retry" button)
     * should call aiRepository.generateChapters directly instead.
     */
    suspend operator fun invoke(
        videoId: String,
        transcript: List<TranscriptSegmentEntity>
    ): Result<List<ChapterEntity>> = runCatching {
        AiGenerationGate.withKey("chapters:$videoId") {
        if (chapterDao.hasChapters(videoId)) {
            val cached = chapterDao.getChapters(videoId)
            val transcriptStarts = transcript.map { it.startMs }
            val cachedStarts = cached.map { it.startMs }
            val curatedTitles = ChapterTitleCatalog.forVideo(videoId)
            if (cachedStarts == transcriptStarts) {
                if (curatedTitles.isNotEmpty()) {
                    val refreshed = cached.mapIndexed { index, chapter ->
                        chapter.copy(title = curatedTitles.getOrElse(index) { chapter.title })
                    }
                    if (refreshed != cached) chapterDao.replaceForVideo(videoId, refreshed)
                    return@withKey refreshed
                }
                return@withKey cached
            }
            // Keep the previous rows until a replacement succeeds. If Gemini
            // is rate-limited, the user should not lose the last usable cache.
        }
        val transcriptText = transcript.joinToString("\n") { "[${it.startMs}ms] ${it.text}" }
        aiRepository.generateChapters(videoId, transcriptText)
        }
    }
}
