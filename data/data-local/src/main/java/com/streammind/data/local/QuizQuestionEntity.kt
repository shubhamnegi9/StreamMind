package com.streammind.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "quiz_questions",
    indices = [Index(value = ["videoId", "questionOrder"], unique = true)]
)
data class QuizQuestionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val questionOrder: Int,
    val question: String,
    val optionA: String,
    val optionB: String,
    val optionC: String,
    val optionD: String,
    val correctOptionIndex: Int,
    val explanation: String,
    val sourceStartMs: Long
) {
    fun options(): List<String> = listOf(optionA, optionB, optionC, optionD)
}
