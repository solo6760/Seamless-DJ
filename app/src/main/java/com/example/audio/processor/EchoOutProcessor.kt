package com.example.audio.processor

import kotlin.math.exp
import kotlin.math.sin

/**
 * Echo Out / Reverb Tail Transition Processor (Requirement 5).
 *
 * For incompatible tracks:
 * Applies a 3-4s rich reverb / delay tail on the outgoing track.
 * The dry outgoing signal drops swiftly, while the 100% wet reverb echo tail rings out
 * and decays exponentially (e^-2.8p) across the transition window into the incoming track.
 *
 * Incoming track fades in cleanly under the decaying ambient reverb tail.
 */
class EchoOutProcessor {

    data class EchoState(
        val outgoingReverbDecayGain: Float,
        val outgoingDryVolume: Float,
        val outgoingVolume: Float,
        val incomingVolume: Float,
        val reverbDecayTimeMs: Long = 3500L,
        val wetMixRatio: Float = 1.0f
    )

    fun calculateEchoState(progress: Float): EchoState {
        val p = progress.coerceIn(0f, 1f)

        // Exponential decay envelope for reverb tail (e^-2.8p)
        val reverbTail = exp(-2.8 * p).toFloat().coerceIn(0f, 1f)

        // Outgoing dry signal drops quickly (by p=0.4 it's completely gone)
        val drySignal = if (p < 0.40f) (1.0f - p / 0.40f) else 0.0f

        // Outgoing audio composite: initial dry signal + prominent reverberant tail
        val outgoingComposite = (0.35f * drySignal + 0.65f * reverbTail).coerceIn(0f, 1f)

        // Incoming track builds steadily under the reverb wash
        val incomingVol = sin(p * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)

        return EchoState(
            outgoingReverbDecayGain = reverbTail,
            outgoingDryVolume = drySignal,
            outgoingVolume = outgoingComposite,
            incomingVolume = incomingVol,
            reverbDecayTimeMs = 3500L,
            wetMixRatio = 1.0f
        )
    }
}
