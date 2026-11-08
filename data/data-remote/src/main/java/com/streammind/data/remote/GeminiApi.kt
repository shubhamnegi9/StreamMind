package com.streammind.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

interface GeminiApi {
    // Confirmed working (free tier) as of July 2026. Google's free-tier lineup
    // has shifted several times this year — gemini-2.0-flash (used here
    // originally) was deprecated and retired, which is what caused the earlier
    // "429, limit: 0" error. If that error reappears, this model has likely aged
    // out too — check https://aistudio.google.com for which model currently
    // shows a "Free tier" badge and swap it in here.
    @POST("v1beta/models/gemini-3.5-flash:generateContent")
    suspend fun generateContent(@Body request: GeminiRequest): GeminiResponse
}
