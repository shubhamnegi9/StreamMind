package com.streammind.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TranscriptDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(segments: List<TranscriptSegmentEntity>)

    @Query("SELECT * FROM transcript_segments WHERE videoId = :videoId ORDER BY startMs ASC")
    fun observeSegments(videoId: String): Flow<List<TranscriptSegmentEntity>>

    @Query("SELECT * FROM transcript_segments WHERE videoId = :videoId ORDER BY startMs ASC")
    suspend fun getSegments(videoId: String): List<TranscriptSegmentEntity>

    // Used by Phase 3's RAG retriever once embeddings are populated.
    @Query("SELECT * FROM transcript_segments WHERE videoId = :videoId AND embedding IS NOT NULL")
    suspend fun getSegmentsWithEmbeddings(videoId: String): List<TranscriptSegmentEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM transcript_segments WHERE videoId = :videoId LIMIT 1)")
    suspend fun hasTranscript(videoId: String): Boolean

    @Query("DELETE FROM transcript_segments WHERE videoId = :videoId")
    suspend fun deleteForVideo(videoId: String)

    @androidx.room.Transaction
    suspend fun replaceForVideo(videoId: String, segments: List<TranscriptSegmentEntity>) {
        deleteForVideo(videoId)
        insertAll(segments)
    }

    @Query("UPDATE transcript_segments SET embedding = :embedding WHERE id = :segmentId")
    suspend fun updateEmbedding(segmentId: Long, embedding: EmbeddingVector)

    /**
     * Embedding every segment costs one API call each, so callers use this to skip
     * work already done — reopening a video shouldn't re-embed the whole transcript.
     */
    @Query("SELECT COUNT(*) FROM transcript_segments WHERE videoId = :videoId AND embedding IS NULL")
    suspend fun countSegmentsMissingEmbeddings(videoId: String): Int
}
