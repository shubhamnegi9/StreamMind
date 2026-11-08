package com.streammind.feature.aichapters

import com.streammind.data.local.QuizQuestionEntity

interface QuizRepository {
    suspend fun generateQuiz(videoId: String, transcript: String): List<QuizQuestionEntity>
}
