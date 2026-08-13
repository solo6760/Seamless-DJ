package com.example.audio.processor

import kotlin.math.cos
import kotlin.math.sin

/**
 * Filter Sweep Transition Processor (Requirement 4).
 *
 * Outgoing track: High-pass filter sweeping from 20Hz -> 4000Hz over 5-8 seconds.
 * Strips out bass and mids, leaving only crisp highs and vocals.
 *
 * Incoming track: Low-pass filter sweeping from 4000Hz down to 20Hz over 5-8 seconds.
 * Gradually introduces full spectrum from top sparkle down to booming bass.
 *
 * Resonant Q (Q = 3.5 - 4.5) creates a pronounced "DJ mixer knob tone" with resonant peak emphasis.
 */
class FilterSweepProcessor {

    data class FilterState(
        val outgoingHighPassCutoffHz: Float,
        val incomingLowPassCutoffHz: Float,
        val outgoingVolume: Float,
        val incomingVolume: Float,
        val resonanceQ: Float = 4.0f,
        val isResonanceActive: Boolean = true
    )

    fun calculateFilterState(progress: Float): FilterState {
        val p = progress.coerceIn(0f, 1f)

        // Exponential sweep curves (20Hz -> 4000Hz)
        // Outgoing HPF: 20Hz -> 4000Hz (cuts bass at p=0.2, cuts mids at p=0.5, leaving only highs > 2kHz)
        val outgoingHpf = 20f + (4000f - 20f) * (p * p)

        // Incoming LPF: 4000Hz -> 20Hz (inverse sweep bringing in full frequency range)
        val incomingLpf = 4000f - (4000f - 20f) * (p * (2f - p))

        // Dynamic resonant Q peak emphasis around mid-sweep (p = 0.5)
        val dynamicQ = 3.0f + 2.0f * sin(p * Math.PI.toFloat())

        // Volume blend with high-pass attenuation factor
        val rawOutVol = cos(p * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)
        val rawInVol = sin(p * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)

        // Boost presence tone slightly due to resonance
        val outResonanceBoost = 1.0f + 0.25f * sin(p * Math.PI.toFloat())
        val outgoingVol = (rawOutVol * outResonanceBoost).coerceIn(0f, 1.15f)

        return FilterState(
            outgoingHighPassCutoffHz = outgoingHpf,
            incomingLowPassCutoffHz = incomingLpf,
            outgoingVolume = outgoingVol,
            incomingVolume = rawInVol,
            resonanceQ = dynamicQ,
            isResonanceActive = true
        )
    }
}
