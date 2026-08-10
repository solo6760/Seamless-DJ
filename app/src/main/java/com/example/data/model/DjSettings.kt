package com.example.data.model

data class DjSettings(
    val segmentDurationSec: Int = 90,     // Play segment length (e.g. 1 min 30 sec)
    val startOffsetSec: Int = 20,         // Next track drop start time (~20 sec mark)
    val crossfadeDurationSec: Int = 6,    // Transition fade time in seconds
    val autoBpmMatch: Boolean = true,     // Automatically match pitch/tempo during transition
    val usePhaseVocoder: Boolean = true,  // Advanced Phase Vocoder time-stretching
    val partyLightsEnabled: Boolean = true,// Animated visual background party lights
    val partyRoomCode: String = "PARTY-808",
    val isDarkMode: Boolean = true        // Google Home modern minimal style theme toggle
)
