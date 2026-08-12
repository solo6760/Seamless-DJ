package com.example.data.model

enum class TransitionType(val displayName: String, val iconSymbol: String, val description: String) {
    CROSSFADE("Crossfade", "🔊", "Equal-power volume blend with matched spectral texture"),
    EQ_FADE("Multi-Band EQ", "🔄", "3-Band parametric EQ fade with dynamic bass & frequency isolation"),
    FILTER_SWEEP("Filter Sweep", "✨", "Resonant high-pass filter sweep masking timbre & frequency clash"),
    RISER_SWEEP("Energy Riser", "🚀", "Rising resonant filter build bridging large energy differentials"),
    ECHO_OUT("Echo Out", "🌊", "Extended reverb decay tail bridging major key or BPM mismatches")
}

data class TransitionDecision(
    val type: TransitionType,
    val overallScore: Float,
    val harmonicScore: Float,
    val bpmScore: Float,
    val energyScore: Float,
    val spectralFluxScore: Float,
    val explanation: String,
    val transitionDurationMs: Long,
    val dropStartMs: Long,
    val pitchShiftSemitones: Int = 0
)

/**
 * Contextual transition selector based on multi-dimensional musical parameters.
 */
fun selectContextualTransition(
    harmonicScore: Float,
    bpmSimilarity: Float,
    energyDiff: Int,
    spectralFluxCorrelation: Float
): TransitionType {
    val bpmDiffRatio = 1.0f - bpmSimilarity
    val isLargeEnergyJump = energyDiff >= 30

    return when {
        // 1. Large energy jump (Low -> High build)
        isLargeEnergyJump -> TransitionType.RISER_SWEEP

        // 2. Extreme key or BPM mismatch -> Echo Out with reverb decay tail
        harmonicScore < 0.35f && bpmDiffRatio > 0.15f -> TransitionType.ECHO_OUT

        // 3. Significant timbral/spectral difference -> Filter Sweep to mask clashes
        spectralFluxCorrelation < 0.40f -> TransitionType.FILTER_SWEEP

        // 4. Good harmonic compatibility with 10-20% BPM difference or normal mix -> Multi-Band EQ Fade
        harmonicScore >= 0.6f && (bpmDiffRatio in 0.08f..0.22f || spectralFluxCorrelation < 0.70f) -> TransitionType.EQ_FADE

        // 5. High compatibility across all dimensions -> Clean Crossfade
        harmonicScore >= 0.8f && bpmSimilarity >= 0.90f && spectralFluxCorrelation >= 0.70f -> TransitionType.CROSSFADE

        // Default fallback to Multi-Band EQ
        else -> if (harmonicScore >= 0.5f) TransitionType.EQ_FADE else TransitionType.FILTER_SWEEP
    }
}

fun selectTransitionType(compatibilityScore: Float): TransitionType {
    return when {
        compatibilityScore >= 0.85f -> TransitionType.CROSSFADE
        compatibilityScore >= 0.65f -> TransitionType.EQ_FADE
        compatibilityScore >= 0.45f -> TransitionType.FILTER_SWEEP
        else -> TransitionType.ECHO_OUT
    }
}
