package com.streammind.data.local

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): StreamMindDatabase =
        Room.databaseBuilder(
            context,
            StreamMindDatabase::class.java,
            context.getString(R.string.streammind_database_name)
        )
            // Fine for a pre-release portfolio project with no real user data to
            // preserve across schema changes. A shipping app would write a real
            // Migration here instead of wiping local data on every version bump.
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideTranscriptDao(database: StreamMindDatabase): TranscriptDao =
        database.transcriptDao()

    @Provides
    fun provideChapterDao(database: StreamMindDatabase): ChapterDao =
        database.chapterDao()

    @Provides
    fun providePlaybackProgressDao(database: StreamMindDatabase): PlaybackProgressDao =
        database.playbackProgressDao()
    @Provides
    fun provideKeyMomentDao(database: StreamMindDatabase): KeyMomentDao =
        database.keyMomentDao()

    @Provides
    fun provideQuizQuestionDao(database: StreamMindDatabase): QuizQuestionDao =
        database.quizQuestionDao()

}
