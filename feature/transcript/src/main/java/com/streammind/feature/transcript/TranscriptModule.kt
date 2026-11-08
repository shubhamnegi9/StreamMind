package com.streammind.feature.transcript

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TranscriptModule {

    @Binds
    @Singleton
    abstract fun bindTranscriptRepository(
        impl: AssetTranscriptRepository
    ): TranscriptRepository
}
