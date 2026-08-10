package com.example.audio.phase_vocoder

import android.util.Log
import java.util.concurrent.atomic.AtomicLong

data class PhaseVocoderMetrics(
    val totalBuffersProcessed: Long = 0,
    val totalProcessingTimeMs: Long = 0,
    val avgProcessingTimeMs: Double = 0.0,
    val lastBufferTimeMs: Long = 0
)

class PhaseVocoderProcessor {

    companion object {
        private const val TAG = "PhaseVocoderProcessor"
        const val FFT_SIZE = 2048
        const val HOP_SIZE = 512

        private val metricsBuffers = AtomicLong(0)
        private val metricsTotalTimeMs = AtomicLong(0)
        private var lastBufferTime = 0L

        fun getMetrics(): PhaseVocoderMetrics {
            val count = metricsBuffers.get()
            val totalTime = metricsTotalTimeMs.get()
            val avg = if (count > 0) totalTime.toDouble() / count else 0.0
            return PhaseVocoderMetrics(
                totalBuffersProcessed = count,
                totalProcessingTimeMs = totalTime,
                avgProcessingTimeMs = avg,
                lastBufferTimeMs = lastBufferTime
            )
        }

        fun resetMetrics() {
            metricsBuffers.set(0)
            metricsTotalTimeMs.set(0)
            lastBufferTime = 0L
        }
    }

    private val basicFallback = BasicSpeedStretchProcessor()

    fun processBuffer(inputBuffer: FloatArray, ratio: Float): FloatArray {
        if (ratio < 0.5f || ratio > 2.0f || Math.abs(ratio - 1.0f) < 0.001f || inputBuffer.size < FFT_SIZE * 2) {
            return basicFallback.stretch(inputBuffer, ratio)
        }

        val startTime = System.currentTimeMillis()

        try {
            val hopAnalysis = HOP_SIZE
            val hopSynthesis = (HOP_SIZE / ratio).toInt().coerceAtLeast(64)

            val window = FloatArray(FFT_SIZE) { i ->
                (0.5 * (1.0 - Math.cos(2.0 * Math.PI * i / (FFT_SIZE - 1)))).toFloat()
            }

            val numFrames = (inputBuffer.size - FFT_SIZE) / hopAnalysis
            if (numFrames <= 0) {
                return basicFallback.stretch(inputBuffer, ratio)
            }

            val outputLength = numFrames * hopSynthesis + FFT_SIZE
            val outputBuffer = FloatArray(outputLength)
            val windowSum = FloatArray(outputLength)

            val prevAnalysisPhase = DoubleArray(FFT_SIZE / 2 + 1)
            val prevSynthesisPhase = DoubleArray(FFT_SIZE / 2 + 1)

            val real = FloatArray(FFT_SIZE)
            val imag = FloatArray(FFT_SIZE)

            for (f in 0 until numFrames) {
                val inputOffset = f * hopAnalysis
                val outputOffset = f * hopSynthesis

                // 1. STFT
                for (i in 0 until FFT_SIZE) {
                    real[i] = inputBuffer[inputOffset + i] * window[i]
                    imag[i] = 0f
                }
                fft(real, imag)

                // 2. Phase unwrapping & Instantaneous frequency
                val frameReal = FloatArray(FFT_SIZE)
                val frameImag = FloatArray(FFT_SIZE)

                for (k in 0..FFT_SIZE / 2) {
                    val r = real[k].toDouble()
                    val im = imag[k].toDouble()
                    val magnitude = Math.sqrt(r * r + im * im)
                    val phase = Math.atan2(im, r)

                    val binCenterFreq = 2.0 * Math.PI * k / FFT_SIZE
                    val phaseDiff = phase - prevAnalysisPhase[k]
                    prevAnalysisPhase[k] = phase

                    val deltaPhaseUnwrapped = pvocAngle(phaseDiff - hopAnalysis * binCenterFreq)
                    val instFreq = binCenterFreq + deltaPhaseUnwrapped / hopAnalysis

                    val synthPhase = pvocAngle(prevSynthesisPhase[k] + hopSynthesis * instFreq)
                    prevSynthesisPhase[k] = synthPhase

                    val synReal = magnitude * Math.cos(synthPhase)
                    val synImag = magnitude * Math.sin(synthPhase)

                    frameReal[k] = synReal.toFloat()
                    frameImag[k] = synImag.toFloat()
                    if (k > 0 && k < FFT_SIZE / 2) {
                        frameReal[FFT_SIZE - k] = synReal.toFloat()
                        frameImag[FFT_SIZE - k] = (-synImag).toFloat()
                    }
                }

                // 3. ISTFT (Inverse FFT)
                ifft(frameReal, frameImag)

                // 4. Overlap-add
                for (i in 0 until FFT_SIZE) {
                    val outIndex = outputOffset + i
                    if (outIndex < outputLength) {
                        outputBuffer[outIndex] += frameReal[i] * window[i]
                        windowSum[outIndex] += window[i] * window[i]
                    }
                }
            }

            // Normalize overlap-add
            for (i in 0 until outputLength) {
                if (windowSum[i] > 1e-4f) {
                    outputBuffer[i] /= windowSum[i]
                }
            }

            val durationMs = System.currentTimeMillis() - startTime
            lastBufferTime = durationMs
            metricsBuffers.incrementAndGet()
            metricsTotalTimeMs.addAndGet(durationMs)

            if (durationMs > 100) {
                Log.w(TAG, "Phase Vocoder buffer processing took ${durationMs}ms (>100ms warning threshold)")
            }

            if (metricsBuffers.get() % 30L == 0L) {
                val metrics = getMetrics()
                Log.d(TAG, "Phase Vocoder: ${metrics.totalBuffersProcessed} buffers processed, avg ${String.format("%.2f", metrics.avgProcessingTimeMs)} ms per buffer")
            }

            return outputBuffer

        } catch (e: Exception) {
            Log.e(TAG, "Phase Vocoder failed, falling back to basic stretch", e)
            return basicFallback.stretch(inputBuffer, ratio)
        }
    }

    private fun pvocAngle(angle: Double): Double {
        var wrapped = angle % (2.0 * Math.PI)
        if (wrapped > Math.PI) wrapped -= 2.0 * Math.PI
        if (wrapped < -Math.PI) wrapped += 2.0 * Math.PI
        return wrapped
    }

    private fun fft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        var j = 0
        for (i in 0 until n - 1) {
            if (i < j) {
                val tempR = real[i]
                real[i] = real[j]
                real[j] = tempR
                val tempI = imag[i]
                imag[i] = imag[j]
                imag[j] = tempI
            }
            var k = n shr 1
            while (k <= j) {
                j -= k
                k = k shr 1
            }
            j += k
        }

        var len = 2
        while (len <= n) {
            val halfLen = len shr 1
            val angle = -2.0 * Math.PI / len
            val wStepR = Math.cos(angle).toFloat()
            val wStepI = Math.sin(angle).toFloat()

            var i = 0
            while (i < n) {
                var wR = 1.0f
                var wI = 0.0f
                for (k in 0 until halfLen) {
                    val pos = i + k
                    val matchPos = pos + halfLen

                    val uR = real[pos]
                    val uI = imag[pos]

                    val vR = real[matchPos] * wR - imag[matchPos] * wI
                    val vI = real[matchPos] * wI + imag[matchPos] * wR

                    real[pos] = uR + vR
                    imag[pos] = uI + vI

                    real[matchPos] = uR - vR
                    imag[matchPos] = uI - vI

                    val nextWR = wR * wStepR - wI * wStepI
                    val nextWI = wR * wStepI + wI * wStepR
                    wR = nextWR
                    wI = nextWI
                }
                i += len
            }
            len = len shl 1
        }
    }

    private fun ifft(real: FloatArray, imag: FloatArray) {
        val n = real.size
        for (i in 0 until n) {
            imag[i] = -imag[i]
        }
        fft(real, imag)
        for (i in 0 until n) {
            real[i] = real[i] / n
            imag[i] = -imag[i] / n
        }
    }
}
