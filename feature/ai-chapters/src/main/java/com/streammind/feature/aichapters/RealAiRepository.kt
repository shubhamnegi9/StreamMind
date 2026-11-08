package com.streammind.feature.aichapters

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.streammind.data.local.ChapterDao
import com.streammind.data.local.ChapterEntity
import com.streammind.data.remote.ChapterDto
import com.streammind.data.remote.GeminiApi
import com.streammind.data.remote.GeminiContent
import com.streammind.data.remote.GeminiPart
import com.streammind.data.remote.GeminiRequest
import kotlinx.coroutines.delay
import retrofit2.HttpException
import javax.inject.Inject

class RealAiRepository @Inject constructor(
    private val geminiApi: GeminiApi,
    private val chapterDao: ChapterDao,
    private val gson: Gson
) : AiRepository {

    override suspend fun generateChapters(videoId: String, transcript: String): List<ChapterEntity> {
        val prompt = """
            You are given a video transcript with millisecond timestamps.
            Return ONLY a JSON array of chapters in this exact shape, nothing else:
            [{"title": string, "startMs": number}]

            Rules:
            - Titles must be 2-4 words. Not a sentence, not a trimmed-down copy
              of the transcript line — a short label like you'd see on a DVD
              chapter menu or a video's timestamp comments.
            - Do NOT reuse the transcript's own phrasing. Compress the idea into
              new, punchier words instead of cutting a sentence down to size.
            - Return between 4 and 8 chapters, spaced out across the full transcript.
            - The first chapter must start at startMs 0.
            - startMs values must be numbers taken from the transcript timestamps, not invented.

            Example of what NOT to do (title too close to the source line):
            Transcript: "Sintel wakes injured in a snowy mountain cave, haunted by memory."
            Bad title: "Sintel Wakes Injured In Cave"   (just a trimmed copy)
            Good title: "Waking in the Snow"             (short, evocative, new wording)

            Another example:
            Transcript: "A flashback shows the original uprising, when rogue robots turned against their creators."
            Bad title: "Robots Turn Against Creators"    (still just the sentence, shortened)
            Good title: "The Uprising"                    (compressed to the core idea)

            Transcript:
            $transcript
        """.trimIndent()

        val request = GeminiRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)))))
        val response = callWithRetry { geminiApi.generateContent(request) }

        val rawJson = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Gemini returned no content for chapter generation")

        val listType = TypeToken.getParameterized(List::class.java, ChapterDto::class.java).type
        val dtos: List<ChapterDto> = gson.fromJson(rawJson, listType)
        val entities = dtos.map { dto ->
            ChapterEntity(videoId = videoId, title = dto.title, startMs = dto.startMs)
        }

        chapterDao.replaceForVideo(videoId, entities)
        return chapterDao.getChapters(videoId)
    }

    /** Retry only transient capacity/rate-limit failures; fail fast on bad requests. */
    private suspend fun <T> callWithRetry(
        maxAttempts: Int = 2,
        initialDelayMs: Long = 2_000,
        block: suspend () -> T
    ): T {
        var attempt = 0
        var delayMs = initialDelayMs
        while (true) {
            try {
                return block()
            } catch (e: HttpException) {
                attempt++
                if (e.code() !in TRANSIENT_HTTP_CODES || attempt >= maxAttempts) {
                    val body = e.response()?.errorBody()?.string()
                    throw IllegalStateException(
                        "Gemini API call failed (HTTP ${e.code()}): ${body ?: e.message()}",
                        e
                    )
                }
                delay(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(12_000L)
            }
        }
    }

    private companion object {
        // 429 means quota/rate limit; retrying immediately only consumes more
        // quota. Capacity failures remain retryable with bounded backoff.
        val TRANSIENT_HTTP_CODES = setOf(500, 502, 503, 504)
    }
}
