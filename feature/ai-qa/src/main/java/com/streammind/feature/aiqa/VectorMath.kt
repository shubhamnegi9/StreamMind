package com.streammind.feature.aiqa

import kotlin.math.sqrt

/**
 * Cosine similarity between two equal-length vectors, in [-1, 1].
 *
 * Deliberately a pure top-level function with no Android or Room dependencies —
 * this is the one piece of retrieval logic with real correctness risk, so it's
 * worth being trivially unit-testable (see VectorMathTest).
 */
fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
    require(a.size == b.size) {
        "Vector size mismatch: ${a.size} vs ${b.size}. Embeddings from different " +
            "models or outputDimensionality settings can't be compared."
    }

    var dot = 0f
    var normA = 0f
    var normB = 0f
    for (i in a.indices) {
        dot += a[i] * b[i]
        normA += a[i] * a[i]
        normB += b[i] * b[i]
    }

    val denominator = sqrt(normA) * sqrt(normB)
    // Guard against a zero vector, which would otherwise divide by zero.
    return if (denominator == 0f) 0f else dot / denominator
}
