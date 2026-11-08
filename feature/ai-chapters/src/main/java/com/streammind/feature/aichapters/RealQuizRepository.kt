package com.streammind.feature.aichapters

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.streammind.data.local.QuizQuestionDao
import com.streammind.data.local.QuizQuestionEntity
import com.streammind.data.remote.GeminiApi
import com.streammind.data.remote.GeminiContent
import com.streammind.data.remote.GeminiPart
import com.streammind.data.remote.GeminiRequest
import com.streammind.data.remote.QuizQuestionDto
import kotlinx.coroutines.delay
import retrofit2.HttpException
import javax.inject.Inject

class RealQuizRepository @Inject constructor(
    private val geminiApi: GeminiApi,
    private val quizQuestionDao: QuizQuestionDao,
    private val gson: Gson
) : QuizRepository {

    override suspend fun generateQuiz(videoId: String, transcript: String): List<QuizQuestionEntity> {
        val prompt = """
            You are given a video transcript with millisecond timestamps.
            Create a short learning-mode quiz that tests understanding of the video.
            Return ONLY a JSON array in this exact shape, with no markdown or explanation:
            [{"question": string, "options": [string, string, string, string], "correctOptionIndex": number, "explanation": string, "sourceStartMs": number}]

            Rules:
            - Return 5 questions, or fewer only if the transcript has fewer meaningful segments.
            - Each question must have exactly four distinct answer options.
            - correctOptionIndex must be an integer from 0 through 3.
            - Questions must test details, actions, motivations, events, or cause and effect from the transcript.
            - Keep questions clear and concise. Do not use information outside the transcript.
            - explanation must briefly explain why the correct answer is supported by the transcript.
            - sourceStartMs must be copied from a timestamp in the transcript, not invented.
            - Use different transcript moments and sort questions by sourceStartMs ascending.

            Transcript:
            $transcript
        """.trimIndent()

        val request = GeminiRequest(contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)))))
        val response = callWithRetry { geminiApi.generateContent(request) }
        val rawJson = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw IllegalStateException("Gemini returned no content for quiz generation")
        val listType = TypeToken.getParameterized(List::class.java, QuizQuestionDto::class.java).type
        val dtos: List<QuizQuestionDto> = gson.fromJson(rawJson, listType) ?: emptyList()
        val validStarts = TRANSCRIPT_SEGMENT.findAll(transcript)
            .mapNotNull { it.groupValues[1].toLongOrNull() }
            .toSet()

        val questions = dtos.asSequence()
            .filter { dto ->
                dto.question.isNotBlank() &&
                    dto.options.size == OPTION_COUNT &&
                    dto.options.map { it.trim().lowercase() }.distinct().size == OPTION_COUNT &&
                    dto.correctOptionIndex in 0 until OPTION_COUNT &&
                    dto.explanation.isNotBlank() &&
                    dto.sourceStartMs in validStarts
            }
            .distinctBy { it.sourceStartMs }
            .sortedBy { it.sourceStartMs }
            .take(MAX_QUESTIONS)
            .mapIndexed { index, dto ->
                QuizQuestionEntity(
                    videoId = videoId,
                    questionOrder = index,
                    question = dto.question.trim().take(MAX_QUESTION_LENGTH),
                    optionA = dto.options[0].trim().take(MAX_OPTION_LENGTH),
                    optionB = dto.options[1].trim().take(MAX_OPTION_LENGTH),
                    optionC = dto.options[2].trim().take(MAX_OPTION_LENGTH),
                    optionD = dto.options[3].trim().take(MAX_OPTION_LENGTH),
                    correctOptionIndex = dto.correctOptionIndex,
                    explanation = dto.explanation.trim().take(MAX_EXPLANATION_LENGTH),
                    sourceStartMs = dto.sourceStartMs
                )
            }
            .toList()

        if (questions.isEmpty()) throw IllegalStateException("Gemini returned no valid quiz questions")
        quizQuestionDao.replaceForVideo(videoId, questions)
        return quizQuestionDao.getQuestions(videoId)
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
        val TRANSCRIPT_SEGMENT = Regex("\\[(\\d+)ms]")
        const val MAX_QUESTIONS = 5
        const val OPTION_COUNT = 4
        const val MAX_QUESTION_LENGTH = 180
        const val MAX_OPTION_LENGTH = 120
        const val MAX_EXPLANATION_LENGTH = 240
    }
}
