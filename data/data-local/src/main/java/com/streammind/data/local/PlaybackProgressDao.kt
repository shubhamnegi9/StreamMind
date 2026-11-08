package com.streammind.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaybackProgressDao {
    @Query("SELECT * FROM playback_progress WHERE positionMs > 5000 ORDER BY updatedAtMs DESC")
    fun observeContinueWatching(): Flow<List<PlaybackProgressEntity>>

    @Upsert
    suspend fun upsert(progress: PlaybackProgressEntity)

    @Query("DELETE FROM playback_progress WHERE videoId = :videoId")
    suspend fun delete(videoId: String)

    @Query("DELETE FROM playback_progress")
    suspend fun deleteAll()
}
