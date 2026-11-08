package com.streammind.feature.accessibility

import android.util.Base64
import com.streammind.data.remote.GeminiApi
import com.streammind.data.remote.GeminiContent
import com.streammind.data.remote.GeminiGenerationConfig
import com.streammind.data.remote.GeminiInlineData
import com.streammind.data.remote.GeminiPart
import com.streammind.data.remote.GeminiRequest
import javax.inject.Inject

class RealAccessibilityRepository @Inject constructor(
    private val geminiApi: GeminiApi
) : AccessibilityRepository {

    override suspend fun describeScene(imageJpeg: ByteArray, nearbyTranscriptText: String): String {
        val prompt = """
            Describe this video frame in 1-2 short sentences for a listener who
            can't see the screen. Focus on what's visually happening — actions,
            expressions, setting — not just objects present. Don't restate the
            transcript context, use it only to understand what's going on.

            Transcript context around this moment: $nearbyTranscriptText
        """.trimIndent()

        val imageBase64 = Base64.encodeToString(imageJpeg, Base64.NO_WRAP)

        val request = GeminiRequest(
            contents = listOf(
                GeminiContent(
                    parts = listOf(
                        GeminiPart(text = prompt),
                        GeminiPart(inlineData = GeminiInlineData(mimeType = "image/jpeg", data = imageBase64))
                    )
                )
            ),
            generationConfig = GeminiGenerationConfig(responseMimeType = "text/plain")
        )

        val response = geminiApi.generateContent(request)
        return response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: "No description available for this frame."
    }
}
