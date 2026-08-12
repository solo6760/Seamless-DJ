package com.example.util

data class CamelotCode(
    val number: Int, // 1 to 12
    val letter: Char // 'A' (Minor) or 'B' (Major)
) {
    val formatted: String get() = "$number$letter"

    /**
     * Pitch class index (0 to 11, where C=0, C#=1, etc.)
     */
    val pitchClass: Int
        get() {
            // Mapping Camelot number & mode to 0..11 pitch class
            // 8B is C Major (0), 5B is Eb Major (3), etc.
            // Major circle of fifths: 8B=C(0), 9B=G(7), 10B=D(2), 11B=A(9), 12B=E(4), 1B=B(11),
            // 2B=F#(6), 3B=Db(1), 4B=Ab(8), 5B=Eb(3), 6B=Bb(10), 7B=F(5)
            // Minor: 8A=Am(9), 9A=Em(4), 10A=Bm(11), 11A=F#m(6), 12A=C#m(1), 1A=G#m(8),
            // 2A=D#m(3), 3A=Bbm(10), 4A=Fm(5), 5A=Cm(0), 6A=Gm(7), 7A=Dm(2)
            val majorRoot = when (number) {
                8 -> 0   // C
                9 -> 7   // G
                10 -> 2  // D
                11 -> 9  // A
                12 -> 4  // E
                1 -> 11  // B
                2 -> 6   // F#
                3 -> 1   // Db
                4 -> 8   // Ab
                5 -> 3   // Eb
                6 -> 10  // Bb
                7 -> 5   // F
                else -> 0
            }
            return if (letter == 'B') majorRoot else (majorRoot + 9) % 12
        }
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

    /**
     * Harmonic compatibility scoring taking harmonic overtone analysis confidence into account.
     */
    fun getCompatibilityScore(
        key1Str: String?,
        key2Str: String?,
        confidence1: Int = 80,
        confidence2: Int = 80
    ): Float {
        val code1 = parseKey(key1Str)
        val code2 = parseKey(key2Str)

        if (code1 == null || code2 == null) {
            return 0.5f // Neutral for unknown keys
        }

        // Base harmonic relationship score
        val baseScore = when {
            // Same exact key
            code1 == code2 -> 1.0f

            // Relative Major / Relative Minor (e.g. 8A & 8B)
            code1.number == code2.number && code1.letter != code2.letter -> 1.0f

            // Circular distance calculation on wheel (1..12)
            else -> {
                val diff = Math.abs(code1.number - code2.number)
                val circularDistance = Math.min(diff, 12 - diff)

                when {
                    // Dominant / Subdominant (Distance 1 on same letter e.g. 8A and 9A or 8A and 7A) -> 0.85
                    circularDistance == 1 && code1.letter == code2.letter -> 0.85f
                    // Diagonal change (Distance 1 with different letter e.g. 8A and 9B) -> 0.65
                    circularDistance == 1 && code1.letter != code2.letter -> 0.65f
                    // Energy Boost modulation (Distance 2, same letter e.g. 8A and 10A) -> 0.55
                    circularDistance == 2 && code1.letter == code2.letter -> 0.55f
                    circularDistance == 2 -> 0.45f
                    // Distance 3+ -> Key clash
                    else -> 0.20f
                }
            }
        }

        // Scale by confidence (if confidence is low, pull score slightly towards neutral 0.5)
        val avgConf = ((confidence1 + confidence2) / 2f).coerceIn(10f, 100f) / 100f
        return (baseScore * avgConf + 0.5f * (1.0f - avgConf)).coerceIn(0.1f, 1.0f)
    }

    /**
     * Requirement 10: Harmonic Pitch Compensation.
     * Calculates the subtle pitch shift (-2..+2 semitones) for incoming track
     * that transforms key2 into a harmonious key with key1.
     * Returns 0 if already compatible (score >= 0.7f) or if no shift is beneficial.
     */
    fun calculateOptimalPitchShift(key1Str: String?, key2Str: String?): Int {
        val code1 = parseKey(key1Str) ?: return 0
        val code2 = parseKey(key2Str) ?: return 0

        val currentScore = getCompatibilityScore(key1Str, key2Str)
        if (currentScore >= 0.70f) return 0 // Already harmonically smooth

        var bestShift = 0
        var bestScore = currentScore

        // Check ±1 and ±2 semitone shifts
        val candidates = listOf(1, -1, 2, -2)
        for (shift in candidates) {
            // Shifting pitch class by shift semitones alters Camelot number
            // Each +1 semitone corresponds to +7 steps around circle of fifths (mod 12)
            val rawNum = (code2.number - 1 + (shift * 7)) % 12
            val shiftedNum = if (rawNum < 0) rawNum + 12 + 1 else rawNum + 1
            val shiftedCode = CamelotCode(shiftedNum, code2.letter)

            val testScore = getCompatibilityScore(code1.formatted, shiftedCode.formatted)
            // Weight candidate: subtle shifts (±1) are preferred over ±2
            val penalty = if (Math.abs(shift) == 1) 0.05f else 0.15f
            val netScore = testScore - penalty

            if (netScore > bestScore && testScore >= 0.80f) {
                bestScore = netScore
                bestShift = shift
            }
        }

        return bestShift
    }

    fun getSmoothnessInfo(score: Float): SmoothnessInfo {
        return when {
            score >= 0.8f -> SmoothnessInfo("Harmonic Blend", score, "⚡ Beat-Synced Harmonic Blend")
            score >= 0.5f -> SmoothnessInfo("Standard Blend", score, "🎵 Equal Power Dynamic Blend")
            else -> SmoothnessInfo("Key Transition", score, "🌊 Reverb Tail Echo Out")
        }
    }
}
