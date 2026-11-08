package com.streammind.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface QuizQuestionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(questions: List<QuizQuestionEntity>)

    @Query("DELETE FROM quiz_questions WHERE videoId = :videoId")
    suspend fun deleteForVideo(videoId: String)

    @Query("SELECT * FROM quiz_questions WHERE videoId = :videoId ORDER BY questionOrder ASC")
    suspend fun getQuestions(videoId: String): List<QuizQuestionEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM quiz_questions WHERE videoId = :videoId)")
    suspend fun hasQuestions(videoId: String): Boolean

    @Transaction
    suspend fun replaceForVideo(videoId: String, questions: List<QuizQuestionEntity>) {
        deleteForVideo(videoId)
        insertAll(questions)
    }
}
