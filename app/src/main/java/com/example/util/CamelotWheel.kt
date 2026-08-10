package com.example.util

data class CamelotCode(
    val number: Int, // 1 to 12
    val letter: Char // 'A' (Minor) or 'B' (Major)
) {
    val formatted: String get() = "$number$letter"
}

data class SmoothnessInfo(
    val title: String,
    val score: Float,
    val badgeText: String
)

object CamelotWheel {

    private val keyToCamelotMapEntries = listOf(
        "8A" to CamelotCode(8, 'A'), "8B" to CamelotCode(8, 'B'),
        "A MINOR" to CamelotCode(8, 'A'), "A MIN" to CamelotCode(8, 'A'), "AM" to CamelotCode(8, 'A'),
        "C MAJOR" to CamelotCode(8, 'B'), "C MAJ" to CamelotCode(8, 'B'), "C" to CamelotCode(8, 'B'),

        "9A" to CamelotCode(9, 'A'), "9B" to CamelotCode(9, 'B'),
        "E MINOR" to CamelotCode(9, 'A'), "E MIN" to CamelotCode(9, 'A'), "EM" to CamelotCode(9, 'A'),
        "G MAJOR" to CamelotCode(9, 'B'), "G MAJ" to CamelotCode(9, 'B'), "G" to CamelotCode(9, 'B'),

        "10A" to CamelotCode(10, 'A'), "10B" to CamelotCode(10, 'B'),
        "B MINOR" to CamelotCode(10, 'A'), "B MIN" to CamelotCode(10, 'A'), "BM" to CamelotCode(10, 'A'),
        "D MAJOR" to CamelotCode(10, 'B'), "D MAJ" to CamelotCode(10, 'B'), "D" to CamelotCode(10, 'B'),

        "11A" to CamelotCode(11, 'A'), "11B" to CamelotCode(11, 'B'),
        "F# MINOR" to CamelotCode(11, 'A'), "F#M" to CamelotCode(11, 'A'), "GB MINOR" to CamelotCode(11, 'A'),
        "A MAJOR" to CamelotCode(11, 'B'), "A MAJ" to CamelotCode(11, 'B'),

        "12A" to CamelotCode(12, 'A'), "12B" to CamelotCode(12, 'B'),
        "C# MINOR" to CamelotCode(12, 'A'), "C#M" to CamelotCode(12, 'A'), "DB MINOR" to CamelotCode(12, 'A'),
        "E MAJOR" to CamelotCode(12, 'B'), "E MAJ" to CamelotCode(12, 'B'),

        "1A" to CamelotCode(1, 'A'), "1B" to CamelotCode(1, 'B'),
        "G# MINOR" to CamelotCode(1, 'A'), "G#M" to CamelotCode(1, 'A'), "AB MINOR" to CamelotCode(1, 'A'),
        "B MAJOR" to CamelotCode(1, 'B'), "B MAJ" to CamelotCode(1, 'B'),

        "2A" to CamelotCode(2, 'A'), "2B" to CamelotCode(2, 'B'),
        "D# MINOR" to CamelotCode(2, 'A'), "D#M" to CamelotCode(2, 'A'), "EB MINOR" to CamelotCode(2, 'A'),
        "F# MAJOR" to CamelotCode(2, 'B'), "F# MAJ" to CamelotCode(2, 'B'), "GB MAJOR" to CamelotCode(2, 'B'),

        "3A" to CamelotCode(3, 'A'), "3B" to CamelotCode(3, 'B'),
        "A# MINOR" to CamelotCode(3, 'A'), "A#M" to CamelotCode(3, 'A'), "BB MINOR" to CamelotCode(3, 'A'),
        "C# MAJOR" to CamelotCode(3, 'B'), "DB MAJOR" to CamelotCode(3, 'B'),

        "4A" to CamelotCode(4, 'A'), "4B" to CamelotCode(4, 'B'),
        "F MINOR" to CamelotCode(4, 'A'), "FM" to CamelotCode(4, 'A'),
        "G# MAJOR" to CamelotCode(4, 'B'), "AB MAJOR" to CamelotCode(4, 'B'),

        "5A" to CamelotCode(5, 'A'), "5B" to CamelotCode(5, 'B'),
        "C MINOR" to CamelotCode(5, 'A'), "CM" to CamelotCode(5, 'A'),
        "D# MAJOR" to CamelotCode(5, 'B'), "EB MAJOR" to CamelotCode(5, 'B'),

        "6A" to CamelotCode(6, 'A'), "6B" to CamelotCode(6, 'B'),
        "G MINOR" to CamelotCode(6, 'A'), "GM" to CamelotCode(6, 'A'),
        "A# MAJOR" to CamelotCode(6, 'B'), "BB MAJOR" to CamelotCode(6, 'B'),

        "7A" to CamelotCode(7, 'A'), "7B" to CamelotCode(7, 'B'),
        "D MINOR" to CamelotCode(7, 'A'), "DM" to CamelotCode(7, 'A'),
        "F MAJOR" to CamelotCode(7, 'B'), "F MAJ" to CamelotCode(7, 'B')
    )

    private val map: Map<String, CamelotCode> by lazy {
        val m = HashMap<String, CamelotCode>()
        keyToCamelotMapEntries.forEach { (k, v) -> m[k] = v }
        m
    }

    fun parseKey(rawKey: String?): CamelotCode? {
        if (rawKey.isNullOrBlank()) return null
        val upper = rawKey.trim().uppercase()

        // 1. Direct Camelot regex match e.g. "8A", "12B", "8A / Fm"
        val regex = Regex("""\b(1[0-2]|[1-9])([AB])\b""")
        val match = regex.find(upper)
        if (match != null) {
            val num = match.groupValues[1].toIntOrNull()
            val letter = match.groupValues[2].firstOrNull()
            if (num != null && letter != null) {
                return CamelotCode(num, letter)
            }
        }

        // 2. Direct map match
        map[upper]?.let { return it }

        // 3. Substring match
        for ((keyName, code) in keyToCamelotMapEntries) {
            if (upper.contains(keyName)) {
                return code
            }
        }

        return null
    }

    fun getCompatibilityScore(key1Str: String?, key2Str: String?): Float {
        val code1 = parseKey(key1Str)
        val code2 = parseKey(key2Str)

        if (code1 == null || code2 == null) {
            return 0.5f // Neutral for unknown keys
        }

        // Same exact key
        if (code1 == code2) return 1.0f

        // Same Camelot number, different letter (Relative Major/Minor e.g. 8A & 8B)
        if (code1.number == code2.number && code1.letter != code2.letter) return 1.0f

        // Circular distance calculation on wheel (1..12)
        val diff = Math.abs(code1.number - code2.number)
        val circularDistance = Math.min(diff, 12 - diff)

        return when {
            // Distance 1 on same letter (e.g. 8A and 9A or 8A and 7A) -> Very smooth
            circularDistance == 1 && code1.letter == code2.letter -> 0.8f
            // Distance 1 with different letter or Distance 2 same letter -> Okay
            circularDistance == 1 && code1.letter != code2.letter -> 0.5f
            circularDistance == 2 -> 0.5f
            // Distance 3+ -> Jarring / key clash
            else -> 0.2f
        }
    }

    fun getSmoothnessInfo(score: Float): SmoothnessInfo {
        return when {
            score >= 0.8f -> SmoothnessInfo("Smooth Transition", score, "⚡ Beat-Synced Harmonic Blend")
            score >= 0.5f -> SmoothnessInfo("Okay Transition", score, "🎵 Standard Equal Power Blend")
            else -> SmoothnessInfo("Gradual Key Mix", score, "🌊 Extended Fade (Key Clash)")
        }
    }
}
