package com.example.audio.processor

import kotlin.math.exp
import kotlin.math.sin

/**
 * Echo Out / Reverb Tail Transition Processor
 *
 * For poor compatibility transitions:
 * Applies exponential decay reverb tail envelope to outgoing track while incoming track fades in underneath.
 * Bridges transition and masks key/BPM mismatches seamlessly.
 */
class EchoOutProcessor {

    data class EchoState(
        val outgoingReverbDecayGain: Float,
        val outgoingVolume: Float,
        val incomingVolume: Float
    )

    fun calculateEchoState(progress: Float): EchoState {
        val p = progress.coerceIn(0f, 1f)

        // Exponential decay envelope for reverb tail (e^-3p)
        val reverbDecay = exp(-3.0 * p).toFloat().coerceIn(0f, 1f)
        val outgoingVol = (1.0f - p) * reverbDecay

        // Steady fade in for incoming track
        val incomingVol = sin(p * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)

        return EchoState(
            outgoingReverbDecayGain = reverbDecay,
            outgoingVolume = outgoingVol,
            incomingVolume = incomingVol
        )
    }
}
