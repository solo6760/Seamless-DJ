package com.example.audio.processor

import kotlin.math.exp
import kotlin.math.sin

/**
 * Energy Riser Sweep Processor (Requirement 7).
 *
 * For high energy differentials (Low -> High builds):
 * Applies an ascending resonant filter sweep (150Hz -> 9000Hz) with an exponential
 * energy swell envelope on the outgoing track right into the incoming drop downbeat.
 */
class RiserSweepProcessor {

    data class RiserState(
        val outgoingResonantCutoffHz: Float,
        val outgoingResonanceQ: Float,
        val outgoingVolume: Float,
        val incomingVolume: Float
    ) {
        val highPassCutoffHz: Float get() = outgoingResonantCutoffHz
        val filterResonance: Float get() = outgoingResonanceQ
    }

    fun calculateRiserState(progress: Float): RiserState {
        val p = progress.coerceIn(0f, 1f)

        // Exponential ascending cutoff frequency: 20Hz -> 8500Hz
        val cutoff = 20f + (8500f - 20f) * (p * p * p)

        // Resonance Q peaks near transition drop (p=0.8)
        val resonanceQ = 1.0f + 4.0f * sin(p * Math.PI.toFloat())

        // Volume builds until 0.85 then cuts right as the incoming drop hits
        val outgoingVol = if (p < 0.85f) {
            (1.0f + 0.1f * (p / 0.85f))
        } else {
            ((1.0f - (p - 0.85f) / 0.15f) * 1.1f).coerceIn(0f, 1.1f)
        }

        // Incoming volume builds rapidly after 0.70
        val incomingVol = if (p < 0.70f) {
            (p / 0.70f) * 0.3f
        } else {
            0.3f + ((p - 0.70f) / 0.30f) * 0.7f
        }.coerceIn(0f, 1f)

        return RiserState(
            outgoingResonantCutoffHz = cutoff,
            outgoingResonanceQ = resonanceQ,
            outgoingVolume = outgoingVol.coerceIn(0f, 1.2f),
            incomingVolume = incomingVol
        )
    }
}
