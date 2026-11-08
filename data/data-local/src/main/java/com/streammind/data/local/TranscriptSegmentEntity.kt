package com.streammind.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One chunk of transcribed speech with its time range in the source video.
 * This is the unit Phase 2 (chapters) and Phase 3 (RAG retrieval) both build on,
 * so getting the shape right in Phase 1 matters more than it looks like it does.
 */
@Entity(tableName = "transcript_segments")
data class TranscriptSegmentEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoId: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    // Populated in Phase 3 once the embedding API is wired up; null until then.
    // Type is EmbeddingVector, not FloatArray — see EmbeddingVector's doc comment.
    val embedding: EmbeddingVector? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TranscriptSegmentEntity) return false
        return id == other.id && videoId == other.videoId &&
            startMs == other.startMs && endMs == other.endMs && text == other.text &&
            embedding == other.embedding
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + videoId.hashCode()
        result = 31 * result + startMs.hashCode()
        result = 31 * result + endMs.hashCode()
        result = 31 * result + text.hashCode()
        result = 31 * result + (embedding?.hashCode() ?: 0)
        return result
    }
}
