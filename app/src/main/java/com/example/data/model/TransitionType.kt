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
    val isLargeEnergyJump = energyDiff >= 25

    // Multi-dimensional compatibility (40% Harmonic, 25% BPM, 15% Energy, 20% Spectral Flux)
    val overallCompat = (0.40f * harmonicScore) + (0.25f * bpmSimilarity) + (0.15f * if (energyDiff < 15) 1.0f else 0.5f) + (0.20f * spectralFluxCorrelation)

    return when {
        // 1. Large energy jump (Low -> High build)
        isLargeEnergyJump -> TransitionType.RISER_SWEEP

        // 2. Significant key clash or large tempo clash or overall low score -> Echo Out with reverb decay tail
        harmonicScore < 0.40f || bpmDiffRatio > 0.18f || overallCompat < 0.45f -> TransitionType.ECHO_OUT

        // 3. Significant timbral/spectral difference or medium clash -> Resonant Filter Sweep
        spectralFluxCorrelation < 0.55f || overallCompat < 0.65f -> TransitionType.FILTER_SWEEP

        // 4. Good harmonic compatibility (0.65 to 0.79) -> Multi-Band EQ Fade with aggressive bass swap
        overallCompat < 0.80f -> TransitionType.EQ_FADE

        // 5. Identical key, BPM, energy (>= 0.80) -> Tight, snappy beat-matched Crossfade
        else -> TransitionType.CROSSFADE
    }
}

fun selectTransitionType(compatibilityScore: Float): TransitionType {
    return when {
        compatibilityScore >= 0.80f -> TransitionType.CROSSFADE        // Identical key, BPM, energy
        compatibilityScore >= 0.65f -> TransitionType.EQ_FADE          // Good harmonic match, use bass swap
        compatibilityScore >= 0.45f -> TransitionType.FILTER_SWEEP     // Medium clash, need filter sweep
        else -> TransitionType.ECHO_OUT                               // Poor match, mask with reverb
    }
}
