package com.streammind.feature.aiqa

import com.streammind.data.remote.EmbedRequest
import com.streammind.data.remote.EmbeddingApi
import com.streammind.data.remote.EmbeddingTaskType
import com.streammind.data.remote.GeminiContent
import com.streammind.data.remote.GeminiPart
import com.streammind.data.remote.GeminiStreamingApi
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class RealQaRepository @Inject constructor(
    private val embeddingApi: EmbeddingApi,
    private val streamingApi: GeminiStreamingApi
) : QaRepository {

    override suspend fun embedDocument(text: String): FloatArray =
        embed(text, EmbeddingTaskType.DOCUMENT)

    override suspend fun embedQuery(text: String): FloatArray =
        embed(text, EmbeddingTaskType.QUERY)

    private suspend fun embed(text: String, taskType: String): FloatArray {
        val response = embeddingApi.embedContent(
            EmbedRequest(
                content = GeminiContent(parts = listOf(GeminiPart(text))),
                taskType = taskType
            )
        )
        return response.embedding.values.toFloatArray()
    }

    override fun streamAnswer(question: String, context: String): Flow<String> {
        // Two things this prompt is doing deliberately:
        // 1. "ONLY the context" + an explicit escape hatch, to reduce hallucination
        //    when the transcript genuinely doesn't cover the question.
        // 2. Asking for the [Nms] marker back, so the UI can offer a jump-to-
        //    timestamp action grounded in a real retrieved chunk.
        val prompt = """
            Answer the question using ONLY the transcript excerpts below.
            If the excerpts partially cover the question, answer with what they do
            say and don't add anything not present in them.
            Only say the excerpts don't cover it if they are genuinely unrelated to
            the question, not just less detailed than you'd like.
            Keep the answer to 2-3 sentences.
            End your answer with the timestamp marker (e.g. [123000ms]) of the excerpt
            you relied on most.

            Transcript excerpts:
            $context

            Question: $question
        """.trimIndent()

        return streamingApi.streamGenerateContent(prompt)
    }
}
