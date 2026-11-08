package com.streammind.data.remote

import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

/** Shared so GeminiStreamingApi (raw OkHttp, not Retrofit) can build the same base. */
const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/"

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideOkHttpClient(
        apiKeyInterceptor: GeminiApiKeyInterceptor
    ): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            // BASIC, not BODY — avoids ever logging prompt/response text (which may
            // include transcript content) to Logcat, even in debug builds.
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(apiKeyInterceptor)
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, gson: Gson): Retrofit =
        Retrofit.Builder()
            .baseUrl(GEMINI_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()

    @Provides
    @Singleton
    fun provideGeminiApi(retrofit: Retrofit): GeminiApi = retrofit.create(GeminiApi::class.java)

    @Provides
    @Singleton
    fun provideEmbeddingApi(retrofit: Retrofit): EmbeddingApi =
        retrofit.create(EmbeddingApi::class.java)
}
