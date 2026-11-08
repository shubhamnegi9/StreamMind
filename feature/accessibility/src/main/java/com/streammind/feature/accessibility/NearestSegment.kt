package com.streammind.feature.accessibility

import com.streammind.data.local.TranscriptSegmentEntity
import kotlin.math.abs

/** Finds whichever segment's start time is closest to atMs. Empty list -> null. */
fun nearestSegment(segments: List<TranscriptSegmentEntity>, atMs: Long): TranscriptSegmentEntity? =
    segments.minByOrNull { abs(it.startMs - atMs) }
