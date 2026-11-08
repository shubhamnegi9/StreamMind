package com.streammind.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "key_moments",
    indices = [Index(value = ["videoId", "startMs"])]
)
data class KeyMomentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val title: String,
    val description: String,
    val startMs: Long,
    val kind: String = "Highlight"
)
