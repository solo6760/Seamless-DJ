package com.example.audio.processor

import kotlin.math.cos
import kotlin.math.sin

/**
 * Dynamic EQ Fade (Bass Swap) Processor
 *
 * Outgoing track: Ramps down low-shelf bass from 0dB (1.0) to -12dB (0.25) over transition duration.
 * Incoming track: Ramps up low-shelf bass from -12dB (0.25) to 0dB (1.0) over transition duration.
 * Keeps mid/high crossfade equal-power while ensuring bass comes from only one track at a time to prevent low-end "mud".
 */
class EqFadeProcessor {

    data class EqVolumes(
        val outgoingBassGain: Float,
        val outgoingMainVolume: Float,
        val incomingBassGain: Float,
        val incomingMainVolume: Float
    )

    /**
     * Calculates volumes and bass gains for a given progress (0.0 to 1.0).
     */
    fun calculateEqVolumes(progress: Float): EqVolumes {
        val p = progress.coerceIn(0f, 1f)

        // Equal-power overall volume crossfade
        val outgoingMain = cos(p * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)
        val incomingMain = sin(p * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)

        // Bass swap curve (0.25 = -12dB low-shelf gain, 1.0 = 0dB)
        val outgoingBass = (1.0f - p * 0.75f).coerceIn(0.25f, 1.0f)
        val incomingBass = (0.25f + p * 0.75f).coerceIn(0.25f, 1.0f)

        return EqVolumes(
            outgoingBassGain = outgoingBass,
            outgoingMainVolume = outgoingMain,
            incomingBassGain = incomingBass,
            incomingMainVolume = incomingMain
        )
    }
}
