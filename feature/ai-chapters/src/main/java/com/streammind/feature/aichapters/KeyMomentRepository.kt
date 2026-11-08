package com.streammind.feature.aichapters

import com.streammind.data.local.KeyMomentEntity

interface KeyMomentRepository {
    suspend fun generateKeyMoments(videoId: String, transcript: String): List<KeyMomentEntity>
}
