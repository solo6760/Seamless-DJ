package com.example.audio.phase_vocoder

import android.util.Log

class PhaseVocoderStretchProcessor(
    private val onFallbackTriggered: (() -> Unit)? = null
) : AudioStretchProcessor {

    private val processor = PhaseVocoderProcessor()
    private val basicProcessor = BasicSpeedStretchProcessor()

    override fun stretch(buffer: FloatArray, ratio: Float): FloatArray {
        return try {
            processor.processBuffer(buffer, ratio)
        } catch (e: Exception) {
            Log.e("PhaseVocoderStretchProcessor", "Error during phase vocoder execution", e)
            onFallbackTriggered?.invoke()
            basicProcessor.stretch(buffer, ratio)
        }
    }
}
