package com.streammind.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject

class GeminiApiKeyInterceptor @Inject constructor(
    @GeminiApiKey private val apiKey: String
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val urlWithKey = original.url.newBuilder()
            .addQueryParameter("key", apiKey)
            .build()
        return chain.proceed(original.newBuilder().url(urlWithKey).build())
    }
}
