package com.streammind.feature.aichapters

/** Stable concise chapter labels for the offline fakeDebug experience. */
internal object ChapterTitleCatalog {
    private val titles = mapOf(
        "sample_1" to listOf(
            "Opening Meadow",
            "Rabbit Wakes",
            "Forest Exploration",
            "Rodent Mischief",
            "Conflict Escalates",
            "Trap Preparation",
            "Forest Chase",
            "Rabbit's Victory",
            "Closing Credits"
        ),
        "elephants_dream" to listOf(
            "Entering the Machine",
            "Telephone Lesson",
            "Clockwork Exploration",
            "The Dark Void",
            "The Office Door",
            "The Fan Room",
            "Reality Breaks",
            "Reality Shatters",
            "Quiet Aftermath",
            "Closing Credits"
        ),
        "sintel" to listOf(
            "Blizzard and the Shaman",
            "Search for Scales",
            "Finding Scales",
            "Dragon Takes Scales",
            "Across the Lands",
            "The Dragon's Lair",
            "Final Battle",
            "Scales Revealed",
            "Leaving the Lair",
            "Closing Credits"
        ),
        "tears_of_steel" to listOf(
            "Opening Battle",
            "Mission Briefing",
            "Return to the City",
            "War Memories",
            "Memory Simulation",
            "Bridge and Bunker Operation",
            "Final Robot Battle",
            "Fragile Reconciliation",
            "Closing Credits"
        ),
        "spring" to listOf(
            "Mountain Opening",
            "Girl and Sheepdog",
            "Into the Forest",
            "Ancient Forest",
            "Guardian's Test",
            "Seasonal Transformation",
            "Spring Returns",
            "Valley in Bloom",
            "Closing Credits"
        )
    )

    fun forVideo(videoId: String): List<String> = titles[videoId].orEmpty()
}
