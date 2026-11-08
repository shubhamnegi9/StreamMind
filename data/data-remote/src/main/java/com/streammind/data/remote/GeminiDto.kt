package com.streammind.data.remote

import com.google.gson.annotations.SerializedName

/**
 * Plain Gson data classes. Gson matches JSON keys to Kotlin property names by
 * default, so @SerializedName is only needed where they genuinely differ from
 * Gemini's camelCase API — which, since these are named to match already, is
 * nowhere below. Kept as a comment/example on responseMimeType for reference.
 */
data class GeminiRequest(
    val contents: List<GeminiContent>,
    val generationConfig: GeminiGenerationConfig = GeminiGenerationConfig()
)

data class GeminiContent(val parts: List<GeminiPart>)

/**
 * text and inlineData are both nullable because a single Part is one or the
 * other, never both — Gemini's multimodal requests send a list of Parts where
 * some are text and some are image data, not one Part carrying both. Existing
 * call sites using GeminiPart(prompt) positionally are unaffected since text
 * stays the first parameter.
 */
data class GeminiPart(
    val text: String? = null,
    val inlineData: GeminiInlineData? = null
)

data class GeminiInlineData(
    val mimeType: String,
    val data: String
)

data class GeminiGenerationConfig(
    // Defaults to JSON mode, which forces the model to return raw JSON with no
    // markdown fences or preamble (used by chapter generation). The streaming Q&A
    // path overrides this to text/plain, since it wants prose, not a JSON object.
    @SerializedName("responseMimeType")
    val responseMimeType: String = "application/json"
)

data class GeminiResponse(val candidates: List<GeminiCandidate> = emptyList())

data class GeminiCandidate(val content: GeminiContent)

/** Shape the model is instructed to return for the chapters prompt. */
data class ChapterDto(val title: String, val startMs: Long)

/** Shape the model is instructed to return for Key Moments and Highlights. */
data class KeyMomentDto(
    val title: String,
    val description: String,
    val startMs: Long,
    val kind: String = "Highlight"
)

/** Shape the model is instructed to return for AI Quizzes. */
data class QuizQuestionDto(
    val question: String,
    val options: List<String>,
    val correctOptionIndex: Int,
    val explanation: String,
    val sourceStartMs: Long
)
