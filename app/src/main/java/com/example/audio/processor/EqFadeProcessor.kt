package com.example.audio.processor

import kotlin.math.cos
import kotlin.math.sin

/**
 * Dynamic EQ Fade (Aggressive Bass Swap) Processor (Requirement 3).
 *
 * Outgoing track: Low-shelf EQ at 80Hz (Q=0.7) starts at 0dB (1.0), ramped down aggressively
 * to -15dB (0.177) by progress 0.60, then drops smoothly to silence.
 *
 * Incoming track: Low-shelf EQ at 80Hz starts suppressed at -15dB (0.177) until progress 0.40,
 * then ramps swiftly up to full 0dB (1.0).
 *
 * Master volume curves follow equal-power sinusoidal laws over 14-18 seconds, creating an
 * unmistakable and clean DJ bass crossover without low-end mud.
 */
class EqFadeProcessor {

    data class EqVolumes(
        val outgoingBassGain: Float,
        val outgoingBassDb: Float,
        val outgoingMainVolume: Float,
        val incomingBassGain: Float,
        val incomingBassDb: Float,
        val incomingMainVolume: Float,
        val cutoffFreqHz: Float = 80f,
        val qFactor: Float = 0.7f
    )

    /**
     * Calculates volumes, bass linear gains, and attenuation in dB for progress (0.0 to 1.0).
     */
    fun calculateEqVolumes(progress: Float): EqVolumes {
        val p = progress.coerceIn(0f, 1f)

        // Equal-power overall volume crossfade
        val outgoingMain = cos(p * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)
        val incomingMain = sin(p * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)

        // Aggressive bass swap curve:
        // -15dB linear gain is 10^(-15/20) ≈ 0.1778f
        val minBassGain = 0.1778f

        // Outgoing bass ramps down aggressively from 1.0 (0dB) to 0.1778 (-15dB)
        val outBassLinear = if (p < 0.60f) {
            (1.0f - (p / 0.60f) * (1.0f - minBassGain)).coerceIn(minBassGain, 1.0f)
        } else {
            (minBassGain * (1.0f - (p - 0.60f) / 0.40f)).coerceIn(0.0f, minBassGain)
        }

        // Incoming bass suppressed at -15dB (0.1778) until 40% progress, then ramps to 1.0 (0dB)
        val inBassLinear = if (p < 0.40f) {
            minBassGain
        } else {
            (minBassGain + ((p - 0.40f) / 0.60f) * (1.0f - minBassGain)).coerceIn(minBassGain, 1.0f)
        }

        val outBassDb = (20f * kotlin.math.log10(outBassLinear.coerceAtLeast(0.001f))).coerceIn(-30f, 0f)
        val inBassDb = (20f * kotlin.math.log10(inBassLinear.coerceAtLeast(0.001f))).coerceIn(-30f, 0f)

        return EqVolumes(
            outgoingBassGain = outBassLinear,
            outgoingBassDb = outBassDb,
            outgoingMainVolume = outgoingMain,
            incomingBassGain = inBassLinear,
            incomingBassDb = inBassDb,
            incomingMainVolume = incomingMain,
            cutoffFreqHz = 80f,
            qFactor = 0.7f
        )
    }
}
