package com.streammind.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_progress")
data class PlaybackProgressEntity(
    @PrimaryKey val videoId: String,
    val positionMs: Long,
    val durationMs: Long,
    val updatedAtMs: Long
)
