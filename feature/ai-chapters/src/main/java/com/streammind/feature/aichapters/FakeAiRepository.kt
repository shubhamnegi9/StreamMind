package com.streammind.feature.aichapters

import com.streammind.data.local.ChapterDao
import com.streammind.data.local.ChapterEntity
import kotlinx.coroutines.delay
import javax.inject.Inject

/**
 * Returns canned chapters instantly (well, after a small simulated delay so loading
 * states are still visible). Used whenever BuildConfig.USE_FAKE_AI is true — default
 * for local debug builds — so you can develop and demo the chapters UI without an
 * API key, without spending quota, and without a live-coding round depending on wifi.
 */
class FakeAiRepository @Inject constructor(
    private val chapterDao: ChapterDao
) : AiRepository {

    override suspend fun generateChapters(videoId: String, transcript: String): List<ChapterEntity> {
        delay(600)

        val segments = transcript
            .lineSequence()
            .mapNotNull { line ->
                val match = TRANSCRIPT_SEGMENT.find(line) ?: return@mapNotNull null
                val startMs = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val segmentText = match.groupValues[2].trim()
                startMs to segmentText
            }
            .toList()
        val curatedTitles = ChapterTitleCatalog.forVideo(videoId)
        val entities = segments.mapIndexed { index, (startMs, segmentText) ->
            ChapterEntity(
                videoId = videoId,
                title = curatedTitles.getOrElse(index) { chapterTitle(segmentText) },
                startMs = startMs
            )
        }

        chapterDao.replaceForVideo(videoId, entities)
        return chapterDao.getChapters(videoId)
    }

    private fun chapterTitle(segmentText: String): String {
        val withoutSceneLabel = segmentText.replace(
            Regex("^(Opening scene|Closing scene|Resolution):\\s*", RegexOption.IGNORE_CASE),
            ""
        )
        val firstSentence = withoutSceneLabel.substringBefore('.').trim()
        return firstSentence.ifBlank { "Video chapter" }.take(MAX_CHAPTER_TITLE_LENGTH)
    }

    private companion object {
        val TRANSCRIPT_SEGMENT = Regex("\\[(\\d+)ms]\\s*(.+)")
        const val MAX_CHAPTER_TITLE_LENGTH = 54
    }
}
