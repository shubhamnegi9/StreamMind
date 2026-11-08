package com.streammind.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface KeyMomentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(moments: List<KeyMomentEntity>)

    @Query("DELETE FROM key_moments WHERE videoId = :videoId")
    suspend fun deleteForVideo(videoId: String)

    @Query("SELECT * FROM key_moments WHERE videoId = :videoId ORDER BY startMs ASC")
    suspend fun getMoments(videoId: String): List<KeyMomentEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM key_moments WHERE videoId = :videoId)")
    suspend fun hasMoments(videoId: String): Boolean

    @Transaction
    suspend fun replaceForVideo(videoId: String, moments: List<KeyMomentEntity>) {
        deleteForVideo(videoId)
        insertAll(moments)
    }
}
