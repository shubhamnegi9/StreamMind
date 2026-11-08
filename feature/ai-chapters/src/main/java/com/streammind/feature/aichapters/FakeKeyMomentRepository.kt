package com.streammind.feature.aichapters

import com.streammind.data.local.KeyMomentDao
import com.streammind.data.local.KeyMomentEntity
import kotlinx.coroutines.delay
import javax.inject.Inject

class FakeKeyMomentRepository @Inject constructor(
    private val keyMomentDao: KeyMomentDao
) : KeyMomentRepository {

    override suspend fun generateKeyMoments(videoId: String, transcript: String): List<KeyMomentEntity> {
        delay(500)

        val segments = transcript
            .lineSequence()
            .mapNotNull { line ->
                val match = TRANSCRIPT_SEGMENT.find(line) ?: return@mapNotNull null
                val startMs = match.groupValues[1].toLongOrNull() ?: return@mapNotNull null
                val text = match.groupValues[2].trim()
                if (text.isBlank()) null else startMs to text
            }
            .toList()

        if (segments.isEmpty()) {
            keyMomentDao.replaceForVideo(videoId, emptyList())
            return emptyList()
        }

        val selected = selectMoments(segments)
        val moments = selected.mapIndexed { index, (startMs, rawText) ->
            val description = cleanDescription(rawText)
            KeyMomentEntity(
                videoId = videoId,
                title = titleFor(index, description),
                description = description,
                startMs = startMs,
                kind = kindFor(index, selected.size)
            )
        }

        keyMomentDao.replaceForVideo(videoId, moments)
        return keyMomentDao.getMoments(videoId)
    }

    private fun selectMoments(segments: List<Pair<Long, String>>): List<Pair<Long, String>> {
        val count = minOf(MAX_MOMENTS, segments.size)
        if (count == segments.size) return segments

        return (0 until count).map { index ->
            val sourceIndex = ((index.toDouble() * (segments.lastIndex)) / (count - 1).coerceAtLeast(1))
                .roundToInt()
            segments[sourceIndex]
        }.distinctBy { it.first }
    }

    private fun cleanDescription(text: String): String =
        text.replace(Regex("^(Opening scene|Closing scene|Resolution):\\s*", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifBlank { "A notable moment in the video." }

    private fun titleFor(index: Int, description: String): String {
        val lower = description.lowercase()
        return when {
            lower.contains("mountain valley") || lower.contains("winter fog") -> "Mountain Opening"
            lower.contains("sheepdog") || lower.contains("shepherd girl") -> "Girl and Sheepdog"
            lower.contains("follows strange spirits") || lower.contains("toward an ancient forest") -> "Into the Forest"
            lower.contains("ancient forest") || lower.contains("seasonal world") -> "Ancient Forest"
            lower.contains("guardian spirit") || lower.contains("tests spring") -> "Guardian's Test"
            lower.contains("seasonal transformation") || lower.contains("guardian awakens") -> "Seasonal Transformation"
            lower.contains("ice melts") || lower.contains("returns to life") -> "Spring Returns"
            lower.contains("valley blooming") || lower.contains("valley") && lower.contains("bloom") -> "Valley in Bloom"
            lower.contains("robot battle") || lower.contains("city establishing") -> "Opening Battle"
            lower.contains("mission briefing") || lower.contains("resistance team") && lower.contains("mission") -> "Mission Briefing"
            lower.contains("returns through the city") || lower.contains("return") && lower.contains("city") -> "Return to the City"
            lower.contains("war memories") || lower.contains("earlier conflict") -> "War Memories"
            lower.contains("memory simulation") || lower.contains("project the bridge") -> "Memory Simulation"
            lower.contains("bridge and bunker") || lower.contains("bridge") && lower.contains("bunker") -> "Bridge and Bunker Operation"
            lower.contains("final robot battle") || lower.contains("final battle") -> "Final Robot Battle"
            lower.contains("fragile reconciliation") || lower.contains("reconciliation") -> "Fragile Reconciliation"
            lower.contains("closing credit") -> "Closing Credits"
            lower.contains("telephone lesson") -> "Telephone Lesson"
            lower.contains("clockwork precision") -> "Clockwork Exploration"
            lower.contains("dark void") || lower.contains("nothingness") -> "The Dark Void"
            lower.contains("service office") || lower.contains("office door") -> "The Office Door"
            lower.contains("large fan") || lower.contains("fan room") -> "The Fan Room"
            lower.contains("mechanical world") && lower.contains("breaking apart") -> "Reality Breaks"
            lower.contains("golden") && lower.contains("chaotic void") -> "Reality Shatters"
            lower.contains("quiet final state") || lower.contains("defeated emo") -> "Quiet Aftermath"
            lower.contains("blizzard") || lower.contains("shaman") -> "Blizzard and the Shaman"
            lower.contains("search") && lower.contains("scales") -> "Search for Scales"
            lower.contains("finding scales") || lower.contains("finding") && lower.contains("baby") -> "Finding Scales"
            lower.contains("takes scales") || lower.contains("taken from her") -> "Dragon Takes Scales"
            lower.contains("harsh landscapes") || lower.contains("following the trail") -> "Across the Lands"
            lower.contains("dragon's lair") || lower.contains("prepares to confront") -> "The Dragon's Lair"
            lower.contains("unable to recognize") || lower.contains("unable to") && lower.contains("recognize") -> "Scales Revealed"
            lower.contains("leaves the lair") || lower.contains("journey home") -> "Leaving the Lair"
            lower.contains("closing credit") -> "Closing Credits"
            lower.contains("office") && lower.contains("door") -> "The Office Door"
            lower.contains("final confrontation") || lower.contains("breaks the illusion") -> "Reality Unravels"
            lower.contains("battle") || lower.contains("fight") -> "The confrontation"
            lower.contains("discover") || lower.contains("reveal") -> "A revealing discovery"
            lower.contains("journey") || lower.contains("travel") -> "The journey continues"
            index == 0 -> "The story begins"
            index >= 4 -> "The closing beat"
            else -> description.substringBefore('.').trim().take(MAX_TITLE_LENGTH).ifBlank { "A key moment" }
        }
    }

    private fun kindFor(index: Int, total: Int): String = when {
        index == 0 -> "Opening"
        index == total - 1 -> "Finale"
        index == total / 2 -> "Turning point"
        else -> "Highlight"
    }

    private companion object {
        val TRANSCRIPT_SEGMENT = Regex("\\[(\\d+)ms]\\s*(.+)")
        const val MAX_MOMENTS = 6
        const val MAX_TITLE_LENGTH = 42
    }
}

private fun Double.roundToInt(): Int = kotlin.math.round(this).toInt()
