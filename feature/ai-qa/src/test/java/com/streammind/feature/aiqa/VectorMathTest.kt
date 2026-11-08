package com.streammind.feature.aiqa

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorMathTest {

    @Test
    fun `identical vectors score 1`() {
        val v = floatArrayOf(0.2f, 0.5f, -0.3f)
        assertEquals(1f, cosineSimilarity(v, v), 1e-5f)
    }

    @Test
    fun `orthogonal vectors score 0`() {
        val a = floatArrayOf(1f, 0f)
        val b = floatArrayOf(0f, 1f)
        assertEquals(0f, cosineSimilarity(a, b), 1e-5f)
    }

    @Test
    fun `opposite vectors score -1`() {
        val a = floatArrayOf(1f, 2f)
        val b = floatArrayOf(-1f, -2f)
        assertEquals(-1f, cosineSimilarity(a, b), 1e-5f)
    }

    @Test
    fun `similarity is scale invariant`() {
        val a = floatArrayOf(1f, 2f, 3f)
        val scaled = floatArrayOf(10f, 20f, 30f)
        assertEquals(1f, cosineSimilarity(a, scaled), 1e-5f)
    }

    @Test
    fun `zero vector does not divide by zero`() {
        val a = floatArrayOf(0f, 0f)
        val b = floatArrayOf(1f, 1f)
        assertEquals(0f, cosineSimilarity(a, b), 1e-5f)
    }

    @Test
    fun `closer vector ranks higher`() {
        val query = floatArrayOf(1f, 0f)
        val near = floatArrayOf(0.9f, 0.1f)
        val far = floatArrayOf(-0.5f, 1f)
        assertTrue(cosineSimilarity(query, near) > cosineSimilarity(query, far))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `mismatched dimensions throw`() {
        cosineSimilarity(floatArrayOf(1f, 2f), floatArrayOf(1f, 2f, 3f))
    }
}
