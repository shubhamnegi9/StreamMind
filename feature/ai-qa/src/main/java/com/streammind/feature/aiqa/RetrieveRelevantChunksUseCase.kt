package com.streammind.feature.aiqa

import com.streammind.data.local.TranscriptDao
import com.streammind.data.local.TranscriptSegmentEntity
import javax.inject.Inject

data class RetrievedChunk(
    val segment: TranscriptSegmentEntity,
    val score: Float
)

/**
 * The "R" in RAG. Brute-force cosine similarity across every embedded chunk.
 *
 * Why no vector database: a transcript is on the order of tens to low hundreds of
 * chunks, so scanning all of them is sub-millisecond and the index would cost more
 * than it saves. ANN indexing (HNSW/IVF) only starts paying off in the tens of
 * thousands of vectors — which is the answer to give if an interviewer asks when
 * you'd switch.
 */
class RetrieveRelevantChunksUseCase @Inject constructor(
    private val transcriptDao: TranscriptDao,
    private val qaRepository: QaRepository
) {
    suspend operator fun invoke(
        videoId: String,
        query: String,
        topK: Int = 4
    ): List<RetrievedChunk> {
        val candidates = transcriptDao.getSegmentsWithEmbeddings(videoId)
        if (candidates.isEmpty()) return emptyList()

        val queryVector = qaRepository.embedQuery(query)

        val queryTerms = tokenize(query)

        val scored = candidates
            .mapNotNull { segment ->
                val embedding = segment.embedding?.values ?: return@mapNotNull null
                val vectorScore = cosineSimilarity(queryVector, embedding)
                val lexicalScore = lexicalOverlap(queryTerms, segment.text)
                // Keep vector similarity dominant for live semantic retrieval, while
                // lexical overlap makes fakeDebug deterministic and useful for demos.
                val combinedScore = (vectorScore * 0.7f) + (lexicalScore * 0.3f)
                ScoredChunk(RetrievedChunk(segment, combinedScore), lexicalScore)
            }
            .sortedByDescending { it.result.score }

        if (scored.isEmpty()) return emptyList()

        val bestScore = scored.first().result.score
        val minimumScore = maxOf(MIN_RELEVANCE_SCORE, bestScore * RELATIVE_RELEVANCE_RATIO)
        val hasLexicalMatch = scored.any { it.lexicalScore > 0f }

        return scored
            // If the query contains words found in the transcript, a result with
            // zero lexical overlap is not allowed to displace a directly matching
            // moment merely because its embedding is broadly similar. This fixes
            // queries such as "Rodents start troubling" returning the opening
            // meadow and rabbit-wakes segments.
            .filter { scoredChunk ->
                scoredChunk.result.score >= minimumScore &&
                    (!hasLexicalMatch || scoredChunk.lexicalScore > 0f)
            }
            .take(topK)
            .map { it.result }
    }

    private fun tokenize(text: String): Set<String> =
        text.lowercase()
            .split(Regex("[^a-z0-9]+"))
            .filter { it.length > 2 }
            .toSet()

    private fun lexicalOverlap(queryTerms: Set<String>, segmentText: String): Float {
        if (queryTerms.isEmpty()) return 0f
        val segmentTerms = tokenize(segmentText)
        return queryTerms.intersect(segmentTerms).size.toFloat() / queryTerms.size
    }

    private data class ScoredChunk(
        val result: RetrievedChunk,
        val lexicalScore: Float
    )

    private companion object {
        const val MIN_RELEVANCE_SCORE = 0.35f
        const val RELATIVE_RELEVANCE_RATIO = 0.78f
    }
}
