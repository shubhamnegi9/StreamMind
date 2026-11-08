package com.streammind.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        TranscriptSegmentEntity::class,
        ChapterEntity::class,
        PlaybackProgressEntity::class,
        KeyMomentEntity::class,
        QuizQuestionEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class StreamMindDatabase : RoomDatabase() {
    abstract fun transcriptDao(): TranscriptDao
    abstract fun chapterDao(): ChapterDao
    abstract fun playbackProgressDao(): PlaybackProgressDao
    abstract fun keyMomentDao(): KeyMomentDao
    abstract fun quizQuestionDao(): QuizQuestionDao
}
