package com.example.data.model

enum class TransitionType(val displayName: String, val iconSymbol: String, val description: String) {
    CROSSFADE("Crossfade", "🔊", "Simple equal-power volume blend"),
    EQ_FADE("EQ Fade (Bass Swap)", "🔄", "Dynamic low-shelf bass swap to prevent low-end mud"),
    FILTER_SWEEP("Filter Sweep", "✨", "Resonant high-pass sweep masking frequency clashes"),
    ECHO_OUT("Echo Out", "🌊", "Reverb decay tail bridging key/BPM transitions")
}

fun selectTransitionType(compatibilityScore: Float): TransitionType {
    return when {
        compatibilityScore >= 0.85f -> TransitionType.CROSSFADE
        compatibilityScore >= 0.70f -> TransitionType.EQ_FADE
        compatibilityScore >= 0.50f -> TransitionType.FILTER_SWEEP
        else -> TransitionType.ECHO_OUT
    }
}
