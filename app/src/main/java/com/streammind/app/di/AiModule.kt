package com.streammind.app.di

import com.streammind.data.remote.GeminiApiKey
import com.streammind.feature.aichapters.AiRepository
import com.streammind.feature.aichapters.FakeAiRepository
import com.streammind.feature.aichapters.RealAiRepository
import com.streammind.feature.aichapters.FakeKeyMomentRepository
import com.streammind.feature.aichapters.KeyMomentRepository
import com.streammind.feature.aichapters.RealKeyMomentRepository
import com.streammind.feature.aichapters.FakeQuizRepository
import com.streammind.feature.aichapters.QuizRepository
import com.streammind.feature.aichapters.RealQuizRepository
import com.streammind.feature.aiqa.FakeQaRepository
import com.streammind.feature.aiqa.QaRepository
import com.streammind.feature.aiqa.RealQaRepository
import com.streammind.feature.accessibility.AccessibilityRepository
import com.streammind.feature.accessibility.FakeAccessibilityRepository
import com.streammind.feature.accessibility.RealAccessibilityRepository
import com.streammind.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * BuildConfig.USE_FAKE_AI and BuildConfig.GEMINI_API_KEY only exist in :app's
 * generated BuildConfig — library modules can't see them. So this is the one
 * place in the whole graph that decides real-vs-fake; everything downstream just
 * injects AiRepository and doesn't know or care which implementation it got.
 *
 * Switch by selecting the fakeDebug / liveDebug build variant in Android Studio.
 */
@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @GeminiApiKey
    fun provideGeminiApiKey(): String = BuildConfig.GEMINI_API_KEY

    @Provides
    @Singleton
    fun provideAiRepository(
        real: RealAiRepository,
        fake: FakeAiRepository
    ): AiRepository = if (BuildConfig.USE_FAKE_AI) fake else real

    @Provides
    @Singleton
    fun provideKeyMomentRepository(
        real: RealKeyMomentRepository,
        fake: FakeKeyMomentRepository
    ): KeyMomentRepository = if (BuildConfig.USE_FAKE_AI) fake else real

    @Provides
    @Singleton
    fun provideQuizRepository(
        real: RealQuizRepository,
        fake: FakeQuizRepository
    ): QuizRepository = if (BuildConfig.USE_FAKE_AI) fake else real

    @Provides
    @Singleton
    fun provideQaRepository(
        real: RealQaRepository,
        fake: FakeQaRepository
    ): QaRepository = if (BuildConfig.USE_FAKE_AI) fake else real

    @Provides
    @Singleton
    fun provideAccessibilityRepository(
        real: RealAccessibilityRepository,
        fake: FakeAccessibilityRepository
    ): AccessibilityRepository = if (BuildConfig.USE_FAKE_AI) fake else real
}
