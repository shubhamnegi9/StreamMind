package com.streammind.data.local

import androidx.room.TypeConverter
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Converts between EmbeddingVector and the BLOB Room actually stores. Operates
 * on EmbeddingVector, not raw FloatArray — see EmbeddingVector's doc comment for
 * why that distinction matters for DAO query parameters specifically.
 */
class Converters {
    @TypeConverter
    fun fromEmbeddingVector(value: EmbeddingVector?): ByteArray? {
        if (value == null) return null
        val floats = value.values
        val buffer = ByteBuffer.allocate(floats.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        floats.forEach { buffer.putFloat(it) }
        return buffer.array()
    }

    @TypeConverter
    fun toEmbeddingVector(bytes: ByteArray?): EmbeddingVector? {
        if (bytes == null) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val floats = FloatArray(bytes.size / 4)
        for (i in floats.indices) floats[i] = buffer.getFloat()
        return EmbeddingVector(floats)
    }
}
