package com.streammind.data.remote

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

/**
 * Retrofit has no native SSE support, so this drops to raw OkHttp and reads the
 * response body line-by-line. Each `data:` line carries one partial GeminiResponse
 * chunk, which we unwrap to its text and emit immediately — so the UI can render
 * the answer as it arrives instead of sitting on a spinner for several seconds.
 */
class GeminiStreamingApi @Inject constructor(
    private val client: OkHttpClient,
    private val gson: Gson
) {
    fun streamGenerateContent(prompt: String): Flow<String> = callbackFlow {
        // No responseMimeType here: this endpoint streams prose, not JSON.
        val payload = gson.toJson(
            GeminiRequest(
                contents = listOf(GeminiContent(parts = listOf(GeminiPart(prompt)))),
                generationConfig = GeminiGenerationConfig(responseMimeType = "text/plain")
            )
        )

        val request = Request.Builder()
            .url("${GEMINI_BASE_URL}v1beta/models/$STREAMING_MODEL:streamGenerateContent?alt=sse")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        val call = client.newCall(request)
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                close(e)
            }

            override fun onResponse(call: Call, response: Response) {
                response.use { res ->
                    if (!res.isSuccessful) {
                        val body = res.body?.string()
                        close(
                            IllegalStateException(
                                "Gemini streaming call failed (HTTP ${res.code}): ${body ?: "no body"}"
                            )
                        )
                        return
                    }

                    val source = res.body?.source()
                    if (source == null) {
                        close(IllegalStateException("Gemini streaming call returned an empty body"))
                        return
                    }

                    try {
                        while (!source.exhausted()) {
                            val line = source.readUtf8Line() ?: continue
                            if (!line.startsWith("data:")) continue
                            val json = line.removePrefix("data:").trim()
                            if (json.isEmpty() || json == "[DONE]") continue

                            val text = runCatching {
                                gson.fromJson(json, GeminiResponse::class.java)
                                    .candidates.firstOrNull()
                                    ?.content?.parts?.firstOrNull()?.text
                            }.getOrNull()

                            if (!text.isNullOrEmpty()) trySend(text)
                        }
                        close()
                    } catch (e: IOException) {
                        close(e)
                    }
                }
            }
        })

        awaitClose { call.cancel() }
    }.flowOn(Dispatchers.IO)

    private companion object {
        // Same model as chapter generation — see GeminiApi for the note on why
        // this ID needs re-checking whenever Google's free tier shifts.
        const val STREAMING_MODEL = "gemini-3.5-flash"
    }
}
