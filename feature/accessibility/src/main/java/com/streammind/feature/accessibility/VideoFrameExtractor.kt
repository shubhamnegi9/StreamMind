package com.streammind.feature.accessibility

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import java.io.ByteArrayOutputStream

/**
 * Pulls a single frame out of the video for the model to describe.
 *
 * MediaMetadataRetriever works directly against an http(s) URL — no need to
 * download the whole file or route this through ExoPlayer — but it's a blocking
 * call, so callers must run this off the main thread (see DescribeSceneUseCase,
 * which wraps it in Dispatchers.IO).
 */
object VideoFrameExtractor {

    fun extractFrameJpeg(
        videoUrl: String,
        atMs: Long,
        maxDimensionPx: Int = 512,
        jpegQuality: Int = 70
    ): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(videoUrl, HashMap())
            val frame = retriever.getFrameAtTime(
                atMs * 1000, // microseconds
                MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: return null

            val scaled = scaleDown(frame, maxDimensionPx)
            ByteArrayOutputStream().use { output ->
                scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, output)
                output.toByteArray()
            }
        } catch (e: Exception) {
            // Frame extraction can fail for lots of reasons (network hiccup, seek
            // past the end, unsupported codec) that aren't worth surfacing
            // differently — the caller just needs to know it didn't work.
            null
        } finally {
            retriever.release()
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxDimensionPx: Int): Bitmap {
        val longestSide = maxOf(bitmap.width, bitmap.height)
        if (longestSide <= maxDimensionPx) return bitmap

        val scale = maxDimensionPx.toFloat() / longestSide
        val newWidth = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val newHeight = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
