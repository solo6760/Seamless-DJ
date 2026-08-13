package com.example.audio.processor

import com.example.data.model.FrequencyBandProfile
import kotlin.math.cos
import kotlin.math.sin

/**
 * Multi-Band Parametric EQ Automation Processor (Requirement 3).
 *
 * Implements 3-4 parametric EQ bands:
 * - Low Shelf (< 250Hz): Smooth bass swap crossover preventing low-end muddiness.
 * - Mid Bell (250Hz - 2500Hz): Frequency isolation preventing vocal/lead clashes.
 * - High Shelf (> 2500Hz): Air & shimmer presence automation.
 *
 * Dynamically adjusts per-band gains based on the outgoing and incoming tracks'
 * frequency band profiles and psychoacoustic loudness targets.
 */
class MultiBandEqProcessor {

    data class MultiBandGains(
        val outgoingLowGain: Float,
        val outgoingMidGain: Float,
        val outgoingHighGain: Float,
        val outgoingOverallVol: Float,

        val incomingLowGain: Float,
        val incomingMidGain: Float,
        val incomingHighGain: Float,
        val incomingOverallVol: Float
    )

    fun calculateGains(
        progress: Float,
        outgoingProfile: FrequencyBandProfile = FrequencyBandProfile(),
        incomingProfile: FrequencyBandProfile = FrequencyBandProfile()
    ): MultiBandGains {
        val p = progress.coerceIn(0f, 1f)

        // Equal-power master curve
        val outMaster = cos(p * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)
        val inMaster = sin(p * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)

        // 1. Low-Band (Bass) Swap Automation:
        // Outgoing bass starts at 1.0f and drops to 0.0f
        // Incoming bass starts at 0.0f and ramps up to 1.0f
        val outLow = if (p < 0.6f) {
            (1.0f - (p / 0.6f) * 0.75f).coerceIn(0.0f, 1.0f)
        } else {
            (0.25f * (1.0f - (p - 0.6f) / 0.4f)).coerceIn(0.0f, 1.0f)
        }

        val inLow = if (p < 0.4f) {
            (p / 0.4f) * 0.15f
        } else {
            (0.15f + ((p - 0.4f) / 0.6f) * 0.85f).coerceIn(0.0f, 1.0f)
        }

        // 2. Mid-Band Automation (Vocal / Harmonic Isolation):
        // If incoming track has strong mids, attenuate outgoing mids faster to prevent vocal clash
        val midClashFactor = if (incomingProfile.midEnergy > 0.4f && outgoingProfile.midEnergy > 0.4f) 1.25f else 1.0f
        val outMid = (1.0f - (p * midClashFactor).coerceAtMost(1.0f) * 0.70f).coerceIn(0.20f, 1.0f)
        val inMid = (0.30f + p * 0.70f).coerceIn(0.30f, 1.0f)

        // 3. High-Band (Treble & Air) Automation:
        // If incoming track lacks high-end air, boost incoming highs slightly (+1.5dB = 1.15)
        val inHighBoost = if (incomingProfile.highEnergy < 0.25f) 1.15f else 1.0f
        val outHigh = (1.0f - p * 0.80f).coerceIn(0.15f, 1.0f)
        val inHigh = ((0.20f + p * 0.80f) * inHighBoost).coerceIn(0.20f, 1.0f)

        return MultiBandGains(
            outgoingLowGain = outLow,
            outgoingMidGain = outMid,
            outgoingHighGain = outHigh,
            outgoingOverallVol = outMaster,

            incomingLowGain = inLow,
            incomingMidGain = inMid,
            incomingHighGain = inHigh,
            incomingOverallVol = inMaster
        )
    }
}
