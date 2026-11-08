package com.streammind.feature.aichapters

import com.streammind.data.local.QuizQuestionDao
import com.streammind.data.local.QuizQuestionEntity
import kotlinx.coroutines.delay
import javax.inject.Inject

class FakeQuizRepository @Inject constructor(
    private val quizQuestionDao: QuizQuestionDao
) : QuizRepository {

    override suspend fun generateQuiz(videoId: String, transcript: String): List<QuizQuestionEntity> {
        delay(550)
        val segments = transcript.lineSequence().mapNotNull { line ->
            val match = TRANSCRIPT_SEGMENT.find(line) ?: return@mapNotNull null
            val startMs = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
            val text = match.groupValues[2].trim()
            if (text.isBlank()) null else startMs to text
        }.toList()

        if (segments.isEmpty()) {
            quizQuestionDao.replaceForVideo(videoId, emptyList())
            return emptyList()
        }

        val selected = selectSegments(segments)
        val questions = selected.mapIndexed { index, (startMs, rawText) ->
            val correct = compact(rawText)
            val distractors = selected
                .asSequence()
                .map { compact(it.second) }
                .filter { it != correct }
                .distinct()
                .take(3)
                .toMutableList()
            var fillerIndex = 1
            while (distractors.size < 3) {
                val filler = "This detail is not mentioned in the video${" ".repeat(fillerIndex)}."
                if (filler !in distractors && filler != correct) distractors += filler
                fillerIndex++
            }

            val correctIndex = index % OPTION_COUNT
            val options = (distractors + correct).take(OPTION_COUNT).toMutableList()
            val correctValue = options.removeAt(options.lastIndex)
            options.add(correctIndex, correctValue)

            QuizQuestionEntity(
                videoId = videoId,
                questionOrder = index,
                question = questionFor(rawText, index),
                optionA = options[0],
                optionB = options[1],
                optionC = options[2],
                optionD = options[3],
                correctOptionIndex = correctIndex,
                explanation = "The transcript describes: $correct",
                sourceStartMs = startMs
            )
        }

        quizQuestionDao.replaceForVideo(videoId, questions)
        return quizQuestionDao.getQuestions(videoId)
    }

    private fun selectSegments(segments: List<Pair<Long, String>>): List<Pair<Long, String>> {
        val count = minOf(MAX_QUESTIONS, segments.size)
        if (count == segments.size) return segments
        return (0 until count).map { index ->
            val sourceIndex = ((index.toDouble() * segments.lastIndex) /
                (count - 1).coerceAtLeast(1)).toInt()
            segments[sourceIndex]
        }.distinctBy { it.first }
    }

    private fun questionFor(text: String, index: Int): String {
        val lower = text.lowercase()
        return when {
            lower.contains("wake") || lower.contains("begin") || lower.contains("opens") ->
                "What happens as this story begins?"
            lower.contains("plan") || lower.contains("prepare") || lower.contains("gather") ->
                "What are the characters preparing to do?"
            lower.contains("rescue") || lower.contains("save") || lower.contains("protect") ->
                "Who or what is being helped in this moment?"
            lower.contains("discover") || lower.contains("reveal") || lower.contains("learn") ->
                "What important discovery is made here?"
            lower.contains("memory") || lower.contains("flashback") || lower.contains("past") ->
                "Which part of the past is revisited?"
            lower.contains("battle") || lower.contains("fight") || lower.contains("attack") || lower.contains("conflict") ->
                "What conflict takes place in this scene?"
            lower.contains("escape") || lower.contains("return") || lower.contains("arrive") ->
                "What change occurs as the story moves forward?"
            index == 0 -> "What key event introduces the story?"
            else -> "What is the main development in this part of the video?"
        }
    }

    private fun compact(text: String): String =
        text.replace(Regex("^(Opening scene|Closing scene|Resolution):\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(MAX_OPTION_LENGTH)

    private companion object {
        val TRANSCRIPT_SEGMENT = Regex("\\[(\\d+)ms]\\s*(.+)")
        const val MAX_QUESTIONS = 5
        const val OPTION_COUNT = 4
        const val MAX_OPTION_LENGTH = 110
    }
}
