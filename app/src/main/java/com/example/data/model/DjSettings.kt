package com.example.data.model

data class DjSettings(
    val segmentDurationSec: Int = 90,     // Short-segment playback length (90-120s default)
    val startOffsetSec: Int = 20,         // Next track drop start time (~20 sec mark)
    val crossfadeDurationSec: Int = 10,   // Transition fade time in seconds (8-12s)
    val autoBpmMatch: Boolean = true,     // Automatically match pitch/tempo during transition
    val usePhaseVocoder: Boolean = false, // Disabled by default per Requirement 9 (opt-in)
    val partyLightsEnabled: Boolean = true,// Animated visual background party lights
    val partyRoomCode: String = "PARTY-808",
    val isDarkMode: Boolean = true,       // Theme toggle
    val debugModeEnabled: Boolean = false // DJ Transition Debug HUD overlay
)
