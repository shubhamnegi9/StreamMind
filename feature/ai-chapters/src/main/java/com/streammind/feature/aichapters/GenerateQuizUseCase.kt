package com.streammind.feature.aichapters

import com.streammind.data.local.QuizQuestionDao
import com.streammind.data.local.QuizQuestionEntity
import com.streammind.data.local.TranscriptSegmentEntity
import javax.inject.Inject

class GenerateQuizUseCase @Inject constructor(
    private val quizRepository: QuizRepository,
    private val quizQuestionDao: QuizQuestionDao
) {
    suspend operator fun invoke(
        videoId: String,
        transcript: List<TranscriptSegmentEntity>
    ): Result<List<QuizQuestionEntity>> = runCatching {
        AiGenerationGate.withKey("quiz:$videoId") {
        if (quizQuestionDao.hasQuestions(videoId)) {
            val cached = quizQuestionDao.getQuestions(videoId)
            if (cachedBelongsToTranscript(cached, transcript)) return@withKey cached
            // Invalidate quizzes created by the first implementation, which used
            // the same generic prompt for every transcript moment.
            // Keep the previous rows until replacement succeeds so a 429 does
            // not destroy the last usable cached result.
        }

        val transcriptText = transcript.joinToString("\n") { "[${it.startMs}ms] ${it.text}" }
        quizRepository.generateQuiz(videoId, transcriptText)
        }
    }

    private companion object {
        const val LEGACY_GENERIC_QUESTION = "Which description best matches this moment in the video?"
    }

    private fun cachedBelongsToTranscript(
        questions: List<QuizQuestionEntity>,
        transcript: List<TranscriptSegmentEntity>
    ): Boolean {
        if (questions.isEmpty() || transcript.isEmpty()) return false
        if (questions.any { it.question == LEGACY_GENERIC_QUESTION }) return false
        val validStarts = transcript.map { it.startMs }.toSet()
        val matching = questions.count { question -> question.sourceStartMs in validStarts }
        return matching >= minOf(2, questions.size)
    }
}
