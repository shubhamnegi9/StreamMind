package com.streammind.feature.aiqa

import com.streammind.data.remote.EMBEDDING_DIMENSIONS
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import javax.inject.Inject
import kotlin.random.Random

/**
 * Offline stand-in for the Gemini calls. Two design choices worth noting:
 *
 * 1. Embeddings are deterministic per input string (seeded by its hash), not random.
 *    That means the same chunk always embeds to the same vector, so retrieval is
 *    reproducible across runs — and identical query text actually scores highest
 *    against the matching chunk, which makes the fake usable for eyeballing the
 *    retrieval flow rather than just returning noise.
 *
 * 2. The answer is emitted word-by-word with a delay, not in one chunk. The whole
 *    point of the streaming UI is progressive rendering, so a fake that emitted
 *    once would hide layout bugs that only show up mid-stream.
 */
class FakeQaRepository @Inject constructor() : QaRepository {

    override suspend fun embedDocument(text: String): FloatArray = pseudoEmbed(text)

    override suspend fun embedQuery(text: String): FloatArray = pseudoEmbed(text)

    private suspend fun pseudoEmbed(text: String): FloatArray {
        delay(30)
        val random = Random(text.lowercase().trim().hashCode())
        return FloatArray(EMBEDDING_DIMENSIONS) { random.nextFloat() - 0.5f }
    }

    override fun streamAnswer(question: String, context: String): Flow<String> = flow {
        delay(400)
        val firstTimestamp = Regex("""\[(\d+)ms]""").find(context)?.groupValues?.get(1)
        val answer = buildString {
            append("Based on the transcript, ")
            append("this section covers exactly what you asked about. ")
            append("(Offline demo answer — no model was called.)")
            if (firstTimestamp != null) append(" [${firstTimestamp}ms]")
        }

        answer.split(" ").forEach { word ->
            delay(45)
            emit("$word ")
        }
    }
}
