package com.example.data.model

enum class BpmStatus {
    RESOLVED,
    FETCHING,
    UNKNOWN
}

enum class TrackSource {
    YOUTUBE,
    SOUNDCLOUD,
    CURATED,
    LOCAL_FILE
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String = "",
    val durationMs: Long = 180000L,
    val streamUrl: String = "",
    val bpm: Int = 124,
    val bpmStatus: BpmStatus = BpmStatus.UNKNOWN,
    val musicalKey: String = "8A / Fm",
    val source: TrackSource = TrackSource.YOUTUBE,
    val sourceUrl: String = "",
    val introOffsetSec: Int = 20,
    val segmentDurationSec: Int = 90,
    val beatTimesMs: List<Long> = emptyList(),
    val isBeatAnalyzing: Boolean = false,
    val energyScore: Int = 50,
    val lufs: Float = -14.0f,
    val transitionType: TransitionType = TransitionType.CROSSFADE,
    val phraseBoundaries: List<PhraseBoundary> = emptyList(),
    val spectralFluxProfileCsv: String = "",
    val frequencyProfile: FrequencyBandProfile = FrequencyBandProfile(),
    val harmonicConfidence: Int = 80,
    val optimalDropOffsetSec: Int = 20,
    val optimalOutroOffsetSec: Int = 0,
    val perceptualLoudnessLufs: Float = -14.0f
) {

    val energyCategory: String
        get() = when {
            energyScore >= 70 -> "High 🔴"
            energyScore >= 40 -> "Medium 🟡"
            else -> "Low 🟢"
        }

    val formattedDuration: String
        get() {
            val totalSec = durationMs / 1000
            val minutes = totalSec / 60
            val seconds = totalSec % 60
            return String.format("%d:%02d", minutes, seconds)
        }

    /**
     * Parses the cached spectral flux profile CSV string into a float array.
     */
    fun getSpectralFluxVector(): FloatArray {
        if (spectralFluxProfileCsv.isBlank()) return FloatArray(0)
        return try {
            spectralFluxProfileCsv.split(",").mapNotNull { it.trim().toFloatOrNull() }.toFloatArray()
        } catch (e: Exception) {
            FloatArray(0)
        }
    }
}
