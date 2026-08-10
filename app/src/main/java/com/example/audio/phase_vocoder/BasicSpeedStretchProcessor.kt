package com.example.audio.phase_vocoder

import kotlin.math.roundToInt

class BasicSpeedStretchProcessor : AudioStretchProcessor {
    override fun stretch(buffer: FloatArray, ratio: Float): FloatArray {
        if (ratio <= 0f || buffer.isEmpty() || Math.abs(ratio - 1.0f) < 0.001f) {
            return buffer
        }
        val outputLength = (buffer.size / ratio).roundToInt()
        if (outputLength <= 0) return buffer
        val output = FloatArray(outputLength)
        for (i in 0 until outputLength) {
            val srcIndex = i * ratio
            val index0 = srcIndex.toInt().coerceIn(0, buffer.size - 1)
            val index1 = (index0 + 1).coerceIn(0, buffer.size - 1)
            val frac = srcIndex - index0
            output[i] = buffer[index0] * (1f - frac) + buffer[index1] * frac
        }
        return output
    }
}
