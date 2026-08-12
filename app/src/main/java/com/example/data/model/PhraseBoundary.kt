package com.example.data.model

enum class PhraseType(val displayName: String, val icon: String) {
    INTRO("Intro", "🟢"),
    VERSE("Verse", "🔵"),
    BUILD("Build-Up", "📈"),
    CHORUS("Chorus / Drop", "🔥"),
    BREAK("Breakdown", "⏸️"),
    OUTRO("Outro / Fade", "🏁")
}

data class PhraseBoundary(
    val timestampMs: Long,
    val type: PhraseType,
    val confidence: Float = 0.8f,
    val energyLevel: Float = 0.5f,
    val description: String = ""
) {
    val formattedTime: String
        get() {
            val totalSec = timestampMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return String.format("%d:%02d", min, sec)
        }
}

data class FrequencyBandProfile(
    val lowEnergy: Float = 0.33f,   // 20Hz - 250Hz
    val midEnergy: Float = 0.33f,   // 250Hz - 2500Hz
    val highEnergy: Float = 0.33f,  // 2500Hz - 20000Hz
    val perceptualLoudnessLufs: Float = -14.0f
)
