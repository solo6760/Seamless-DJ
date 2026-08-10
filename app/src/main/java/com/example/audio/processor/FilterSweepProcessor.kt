package com.example.audio.processor

import kotlin.math.cos
import kotlin.math.sin

/**
 * Filter Sweep Transition Processor
 *
 * Applies resonant high-pass filter sweep for less compatible tracks:
 * Outgoing track: Cutoff frequency sweeps from 20Hz (full range) up to 5000Hz (bass/mids cut, highs pass).
 * Incoming track: Cutoff frequency sweeps from 5000Hz down to 20Hz (full range restored).
 * Sweeps over ~3–5 seconds to mask harmonic/tempo clashes.
 */
class FilterSweepProcessor {

    data class FilterState(
        val outgoingHighPassCutoffHz: Float,
        val outgoingVolume: Float,
        val incomingHighPassCutoffHz: Float,
        val incomingVolume: Float
    )

    fun calculateFilterState(progress: Float): FilterState {
        val p = progress.coerceIn(0f, 1f)

        // Rapid 3-5s filter sweep curve
        val outgoingCutoff = 20f + (5000f - 20f) * (p * p) // Quadratic sweep up
        val incomingCutoff = 5000f - (5000f - 20f) * (p * (2f - p)) // Inverse quadratic sweep down

        val outgoingVol = cos(p * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)
        val incomingVol = sin(p * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)

        return FilterState(
            outgoingHighPassCutoffHz = outgoingCutoff,
            outgoingVolume = outgoingVol,
            incomingHighPassCutoffHz = incomingCutoff,
            incomingVolume = incomingVol
        )
    }
}
