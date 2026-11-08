package com.streammind.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ChapterDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(chapters: List<ChapterEntity>)

    @Query("DELETE FROM chapters WHERE videoId = :videoId")
    suspend fun deleteForVideo(videoId: String)

    @Query("SELECT * FROM chapters WHERE videoId = :videoId ORDER BY startMs ASC")
    suspend fun getChapters(videoId: String): List<ChapterEntity>

    @Query("SELECT * FROM chapters WHERE videoId = :videoId ORDER BY startMs ASC")
    fun observeChapters(videoId: String): Flow<List<ChapterEntity>>

    @Query("SELECT EXISTS(SELECT 1 FROM chapters WHERE videoId = :videoId LIMIT 1)")
    suspend fun hasChapters(videoId: String): Boolean

    /** Regenerating chapters should replace the old set, not append to it. */
    @Transaction
    suspend fun replaceForVideo(videoId: String, chapters: List<ChapterEntity>) {
        deleteForVideo(videoId)
        insertAll(chapters)
    }
}
