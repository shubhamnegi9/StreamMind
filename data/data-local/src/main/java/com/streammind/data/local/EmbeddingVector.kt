package com.streammind.data.local

/**
 * A plain FloatArray as a DAO @Query parameter triggers Room's built-in support
 * for expanding array/collection parameters into an IN-clause style list of "?"
 * placeholders — that's Room's answer to "give me rows WHERE id IN (:ids)". It
 * takes priority over registered TypeConverters for that same type, so a raw
 * FloatArray parameter never reaches Converters.fromFloatArray at all. Wrapping
 * it in a normal class sidesteps that special case entirely.
 *
 * Overrides equals/hashCode manually because Kotlin data classes compare array
 * properties by reference, not content, by default — contentEquals/contentHashCode
 * are needed for this to behave the way "value class holding data" should.
 */
class EmbeddingVector(val values: FloatArray) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is EmbeddingVector) return false
        return values.contentEquals(other.values)
    }

    override fun hashCode(): Int = values.contentHashCode()
}
