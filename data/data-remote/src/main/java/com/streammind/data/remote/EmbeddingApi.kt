package com.streammind.data.remote

import retrofit2.http.Body
import retrofit2.http.POST

/**
 * gemini-embedding-001: text-only embeddings, GA, free tier available.
 * Default output is 3072 dimensions; we request 768 instead — plenty of retrieval
 * quality for short transcript chunks, and a quarter of the storage in Room.
 *
 * taskType matters for retrieval quality: chunks are embedded as RETRIEVAL_DOCUMENT
 * and the user's question as RETRIEVAL_QUERY, so the model places them correctly
 * relative to each other in the vector space.
 */
interface EmbeddingApi {
    @POST("v1beta/models/gemini-embedding-001:embedContent")
    suspend fun embedContent(@Body request: EmbedRequest): EmbedResponse
}

data class EmbedRequest(
    val model: String = "models/gemini-embedding-001",
    val content: GeminiContent,
    val taskType: String,
    val outputDimensionality: Int = EMBEDDING_DIMENSIONS
)

data class EmbedResponse(val embedding: EmbeddingValues)

data class EmbeddingValues(val values: List<Float>)

const val EMBEDDING_DIMENSIONS = 768

object EmbeddingTaskType {
    const val DOCUMENT = "RETRIEVAL_DOCUMENT"
    const val QUERY = "RETRIEVAL_QUERY"
}
