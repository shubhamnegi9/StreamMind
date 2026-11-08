package com.streammind.feature.aiqa

import kotlinx.coroutines.flow.Flow

interface QaRepository {
    /** Embeds a transcript chunk for storage. */
    suspend fun embedDocument(text: String): FloatArray

    /** Embeds a user question for querying. Uses a different task type than documents. */
    suspend fun embedQuery(text: String): FloatArray

    /** Streams a grounded answer token-by-token. */
    fun streamAnswer(question: String, context: String): Flow<String>
}
