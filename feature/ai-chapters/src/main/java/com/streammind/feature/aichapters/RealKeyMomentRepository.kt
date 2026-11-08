package com.streammind.feature.aichapters

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.streammind.data.local.KeyMomentDao
import com.streammind.data.local.KeyMomentEntity
import com.streammind.data.remote.GeminiApi
import com.streammind.data.remote.GeminiContent
import com.streammind.data.remote.GeminiPart
import com.streammind.data.remote.GeminiRequest
import com.streammind.data.remote.KeyMomentDto
import kotlinx.coroutines.delay
import retrofit2.HttpException
import javax.inject.Inject

class RealKeyMomentRepository @Inject constructor(
    private val geminiApi: GeminiApi,
    private val keyMomentDao: KeyMomentDao,
    private val gson: Gson
) : KeyMomentRepository {

    override suspend fun generateKeyMoments(videoId: String, transcript: String): List<KeyMomentEntity> {
        val prompt = """
            You are given a video transcript with millisecond timestamps.
            Identify the most important, memorable, or useful moments for a viewer who wants a quick highlight reel.
            Return ONLY a JSON array in this exact shape, with no markdown or explanation:
            [{"title": string, "description": string, "startMs": number, "kind": string}]

            Rules:
            - Return 4 to 8 moments, or fewer only if the transcript has fewer meaningful segments.
            - Select moments that represent story turns, important actions, discoveries, emotional peaks, or memorable visual beats.
            - Titles must be short, polished labels of 2 to 5 words, not copied transcript sentences.
            - Descriptions must be one concise sentence that explains why the moment matters.
            - kind must be one of: "Opening", "Highlight", "Turning point", "Discovery", "Finale".
            - Use only startMs values that appear in the transcript. Do not invent timestamps.
            - Sort the result by startMs ascending and do not return duplicate timestamps.
            - Include an opening moment near the beginning and a finale near the end when the transcript supports them.

            Transcript:
            $transcript
        """.trimIndent()

        val request = GeminiRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)))))
        val response = callWithRetry { geminiApi.generateContent(request) }
        val rawJson = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Gemini returned no content for Key Moments generation")

        val listType = TypeToken.getParameterized(List::class.java, KeyMomentDto::class.java).type
        val dtos: List<KeyMomentDto> = gson.fromJson(rawJson, listType) ?: emptyList()
        val moments = dtos
            .asSequence()
            .filter { it.title.isNotBlank() && it.description.isNotBlank() && it.startMs >= 0L }
            .distinctBy { it.startMs }
            .sortedBy { it.startMs }
            .take(MAX_MOMENTS)
            .map { dto ->
                KeyMomentEntity(
                    videoId = videoId,
                    title = dto.title.trim().take(MAX_TITLE_LENGTH),
                    description = dto.description.trim().take(MAX_DESCRIPTION_LENGTH),
                    startMs = dto.startMs,
                    kind = dto.kind.trim().ifBlank { "Highlight" }
                )
            }
            .toList()

        if (moments.isEmpty()) {
            throw IllegalStateException("Gemini returned no valid Key Moments")
        }

        keyMomentDao.replaceForVideo(videoId, moments)
        return keyMomentDao.getMoments(videoId)
    }

    private suspend fun <T> callWithRetry(
        maxAttempts: Int = 2,
        initialDelayMs: Long = 2_000L,
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
        val TRANSIENT_HTTP_CODES = setOf(500, 502, 503, 504)
        const val MAX_MOMENTS = 8
        const val MAX_TITLE_LENGTH = 60
        const val MAX_DESCRIPTION_LENGTH = 220
    }
}
