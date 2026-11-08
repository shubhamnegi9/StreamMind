package com.streammind.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chapters")
data class ChapterEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoId: String,
    val title: String,
    val startMs: Long
)
