package com.streammind.feature.accessibility

import com.streammind.data.local.TranscriptSegmentEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NearestSegmentTest {

    private fun segment(startMs: Long) =
        TranscriptSegmentEntity(videoId = "v1", startMs = startMs, endMs = startMs + 1000, text = "seg-$startMs")

    @Test
    fun `empty list returns null`() {
        assertNull(nearestSegment(emptyList(), 5000))
    }

    @Test
    fun `exact match wins`() {
        val segments = listOf(segment(0), segment(5000), segment(10000))
        assertEquals(5000L, nearestSegment(segments, 5000)?.startMs)
    }

    @Test
    fun `picks closer of two neighbors`() {
        val segments = listOf(segment(0), segment(10000))
        assertEquals(0L, nearestSegment(segments, 4000)?.startMs)
        assertEquals(10000L, nearestSegment(segments, 6000)?.startMs)
    }

    @Test
    fun `timestamp before all segments picks the earliest`() {
        val segments = listOf(segment(5000), segment(10000))
        assertEquals(5000L, nearestSegment(segments, 0)?.startMs)
    }
}
