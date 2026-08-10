package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.example.data.model.BpmStatus
import com.example.data.model.Track
import com.example.util.CamelotWheel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.*

data class DspAnalysisResult(
    val bpm: Int,
    val bpmConfidence: Int,
    val musicalKey: String,
    val camelotKey: String,
    val keyConfidence: Int,
    val confidence: String, // "high", "medium", "low"
    val status: BpmStatus,
    val energyScore: Int = 50,
    val lufs: Float = -14.0f
)

class AudioDspAnalyzer(private val context: Context) {

    private companion object {
        const val TAG = "AudioDspAnalyzer"
        const val MAX_ANALYSIS_DURATION_US = 45_000_000L // Analyze first 45 seconds
        const val FFT_SIZE = 2048
        const val HOP_SIZE = 512
        
        // Krumhansl-Schmuckler Key Profiles
        val MAJOR_PROFILE = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
        val MINOR_PROFILE = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 2.69, 3.34, 3.17, 3.28)
        val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    }

    suspend fun analyzeTrack(track: Track): DspAnalysisResult = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Starting DSP analysis for track: ${track.title}")
            val pcmData = extractMonoPcmSamples(track)
            
            if (pcmData == null || pcmData.samples.size < FFT_SIZE * 4) {
                Log.w(TAG, "Insufficient PCM data extracted for track ${track.title}, using fallback")
                return@withContext DspAnalysisResult(
                    bpm = if (track.bpm in 40..220) track.bpm else 120,
                    bpmConfidence = 20,
                    musicalKey = if (track.musicalKey.isNotBlank() && track.musicalKey != "Unknown") track.musicalKey else "Unknown",
                    camelotKey = "",
                    keyConfidence = 20,
                    confidence = "low",
                    status = BpmStatus.UNKNOWN
                )
            }

            val samples = pcmData.samples
            val sampleRate = pcmData.sampleRate

            // 1. Calculate BPM using Spectral Flux & Autocorrelation
            val (detectedBpm, bpmConf) = estimateBpm(samples, sampleRate)
            
            // 2. Estimate Musical Key using Chromagram & Pitch Profiles
            val (keyResult, camelotCode, keyConf) = estimateKey(samples, sampleRate)

            // 3. Calculate Energy Score & LUFS Loudness
            val (energyScore, lufs) = calculateEnergyAndLufs(samples, sampleRate)

            val isValidBpm = detectedBpm in 40..220
            val overallConfidence = when {
                bpmConf >= 60 && keyConf >= 50 -> "high"
                bpmConf >= 40 || keyConf >= 30 -> "medium"
                else -> "low"
            }
            val finalBpm = if (isValidBpm) detectedBpm else (if (track.bpm in 40..220) track.bpm else 120)

            Log.d(TAG, "DSP Analysis complete for '${track.title}': BPM=$finalBpm ($bpmConf%), Key=$keyResult ($keyConf%, $camelotCode), Energy=$energyScore, LUFS=${String.format("%.1f", lufs)}")

            DspAnalysisResult(
                bpm = finalBpm,
                bpmConfidence = bpmConf,
                musicalKey = if (camelotCode.isNotBlank()) "$camelotCode / $keyResult" else keyResult,
                camelotKey = camelotCode,
                keyConfidence = keyConf,
                confidence = overallConfidence,
                status = if (isValidBpm) BpmStatus.RESOLVED else BpmStatus.UNKNOWN,
                energyScore = energyScore,
                lufs = lufs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error performing DSP analysis for track ${track.title}", e)
            DspAnalysisResult(
                bpm = if (track.bpm in 40..220) track.bpm else 120,
                bpmConfidence = 0,
                musicalKey = "Unknown",
                camelotKey = "",
                keyConfidence = 0,
                confidence = "low",
                status = BpmStatus.UNKNOWN
            )
        }
    }

    private data class ExtractedPcm(
        val samples: FloatArray,
        val sampleRate: Int
    )

    private fun extractMonoPcmSamples(track: Track): ExtractedPcm? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        try {
            val sourceUrl = track.streamUrl.ifBlank { track.sourceUrl }
            if (sourceUrl.isBlank()) return null

            val uri = Uri.parse(sourceUrl)
            if (uri.scheme == "file" || sourceUrl.startsWith("/")) {
                val filePath = uri.path ?: sourceUrl
                val file = File(filePath)
                if (!file.exists()) return null
                extractor.setDataSource(file.absolutePath)
            } else if (uri.scheme == "android.resource" || uri.scheme == "content") {
                extractor.setDataSource(context, uri, null)
            } else if (sourceUrl.startsWith("http")) {
                extractor.setDataSource(sourceUrl)
            } else {
                extractor.setDataSource(sourceUrl)
            }

            var audioTrackIndex = -1
            var format: MediaFormat? = null

            for (i in 0 until extractor.trackCount) {
                val trackFormat = extractor.getTrackFormat(i)
                val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    format = trackFormat
                    break
                }
            }

            if (audioTrackIndex < 0 || format == null) {
                extractor.release()
                return null
            }

            extractor.selectTrack(audioTrackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT) else 1

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val info = MediaCodec.BufferInfo()
            val sampleList = FloatArrayList()
            var isEOS = false

            while (!isEOS) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0 || extractor.sampleTime > MAX_ANALYSIS_DURATION_US) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isEOS = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                var outputIndex = codec.dequeueOutputBuffer(info, 10_000)
                while (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && info.size > 0) {
                        outputBuffer.position(info.offset)
                        outputBuffer.limit(info.offset + info.size)

                        val shortBuffer = outputBuffer.asShortBuffer()
                        while (shortBuffer.hasRemaining()) {
                            if (channelCount == 2) {
                                val left = shortBuffer.get() / 32768.0f
                                if (shortBuffer.hasRemaining()) {
                                    val right = shortBuffer.get() / 32768.0f
                                    sampleList.add((left + right) * 0.5f)
                                } else {
                                    sampleList.add(left)
                                }
                            } else {
                                sampleList.add(shortBuffer.get() / 32768.0f)
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isEOS = true
                        break
                    }
                    outputIndex = codec.dequeueOutputBuffer(info, 0)
                }
            }

            return ExtractedPcm(sampleList.toArray(), sampleRate)
        } catch (e: Exception) {
            Log.e(TAG, "Error extracting PCM for track ${track.title}: ${e.message}")
            return null
        } finally {
            try {
                codec?.stop()
                codec?.release()
            } catch (e: Exception) { /* Ignore */ }
            try {
                extractor.release()
            } catch (e: Exception) { /* Ignore */ }
        }
    }

    /**
     * 1. BPM Estimation via STFT Spectral Flux & Autocorrelation
     */
    private fun estimateBpm(samples: FloatArray, sampleRate: Int): Pair<Int, Int> {
        val window = FloatArray(FFT_SIZE) { i ->
            (0.54 - 0.46 * cos(2.0 * Math.PI * i / (FFT_SIZE - 1))).toFloat() // Hamming
        }

        val numFrames = (samples.size - FFT_SIZE) / HOP_SIZE
        if (numFrames < 10) return Pair(120, 20)

        val spectralFlux = FloatArray(numFrames)
        val prevSpectrum = FloatArray(FFT_SIZE / 2)
        val currentSpectrum = FloatArray(FFT_SIZE / 2)
        val real = FloatArray(FFT_SIZE)
        val imag = FloatArray(FFT_SIZE)

        for (f in 0 until numFrames) {
            val offset = f * HOP_SIZE
            for (i in 0 until FFT_SIZE) {
                real[i] = samples[offset + i] * window[i]
                imag[i] = 0f
            }

            fft(real, imag)

            var flux = 0f
            for (k in 0 until FFT_SIZE / 2) {
                val mag = sqrt(real[k] * real[k] + imag[k] * imag[k])
                currentSpectrum[k] = mag
                val diff = mag - prevSpectrum[k]
                if (diff > 0) flux += diff * diff
                prevSpectrum[k] = mag
            }
            spectralFlux[f] = flux
        }

        // Normalize spectral flux
        var maxFlux = 0f
        for (flux in spectralFlux) {
            if (flux > maxFlux) maxFlux = flux
        }
        if (maxFlux > 0f) {
            for (i in spectralFlux.indices) spectralFlux[i] /= maxFlux
        }

        // Autocorrelation over BPM range [50, 200]
        val frameRate = sampleRate.toFloat() / HOP_SIZE
        val minBpm = 50
        val maxBpm = 200
        var bestBpm = 120
        var maxCorrelation = -1f
        var sumCorrelation = 0f

        val candidateBpmScores = mutableMapOf<Int, Float>()

        for (bpm in minBpm..maxBpm) {
            val lagInFrames = (60.0f * frameRate / bpm).roundToInt()
            if (lagInFrames <= 0 || lagInFrames >= numFrames / 2) continue

            var corr = 0f
            var count = 0
            for (i in 0 until (numFrames - lagInFrames)) {
                corr += spectralFlux[i] * spectralFlux[i + lagInFrames]
                count++
            }
            if (count > 0) {
                val score = corr / count
                candidateBpmScores[bpm] = score
                sumCorrelation += score
                if (score > maxCorrelation) {
                    maxCorrelation = score
                    bestBpm = bpm
                }
            }
        }

        // Check for harmonic tempo multipliers (e.g., half/double tempo correction)
        if (bestBpm < 70 && candidateBpmScores.containsKey(bestBpm * 2)) {
            val doubleScore = candidateBpmScores[bestBpm * 2] ?: 0f
            if (doubleScore > maxCorrelation * 0.7f) {
                bestBpm *= 2
            }
        } else if (bestBpm > 170 && candidateBpmScores.containsKey(bestBpm / 2)) {
            val halfScore = candidateBpmScores[bestBpm / 2] ?: 0f
            if (halfScore > maxCorrelation * 0.8f) {
                bestBpm /= 2
            }
        }

        val avgCorrelation = if (candidateBpmScores.isNotEmpty()) sumCorrelation / candidateBpmScores.size else 0.01f
        val peakRatio = if (avgCorrelation > 0f) maxCorrelation / avgCorrelation else 1.0f
        val bpmConfidence = ((peakRatio - 1.0f) * 35.0f).roundToInt().coerceIn(15, 95)

        return Pair(bestBpm.coerceIn(40, 220), bpmConfidence)
    }

    /**
     * 2. Key Estimation using Chromagram and Pitch Profiles
     */
    private fun estimateKey(samples: FloatArray, sampleRate: Int): Triple<String, String, Int> {
        val chroma = DoubleArray(12)
        val numFrames = (samples.size - FFT_SIZE) / (HOP_SIZE * 2) // Step 2x for key speed
        if (numFrames < 5) return Triple("Unknown", "", 0)

        val real = FloatArray(FFT_SIZE)
        val imag = FloatArray(FFT_SIZE)
        val window = FloatArray(FFT_SIZE) { i ->
            (0.54 - 0.46 * cos(2.0 * Math.PI * i / (FFT_SIZE - 1))).toFloat()
        }

        for (f in 0 until numFrames) {
            val offset = f * HOP_SIZE * 2
            for (i in 0 until FFT_SIZE) {
                real[i] = samples[offset + i] * window[i]
                imag[i] = 0f
            }

            fft(real, imag)

            for (k in 1 until FFT_SIZE / 2) {
                val freq = k.toDouble() * sampleRate / FFT_SIZE
                if (freq < 65.0 || freq > 2100.0) continue // Human hearing musical range (C2 - C7)

                val mag = sqrt(real[k] * real[k] + imag[k] * imag[k]).toDouble()
                val midiNote = 12.0 * (log2(freq / 440.0)) + 69.0
                val pitchClass = (midiNote.roundToInt() % 12 + 12) % 12
                chroma[pitchClass] += mag
            }
        }

        // Normalize chroma
        val maxChroma = chroma.maxOrNull() ?: 0.0
        val sumChroma = chroma.sum()
        if (maxChroma <= 0.0) return Triple("Unknown", "", 0)

        for (i in chroma.indices) {
            chroma[i] /= maxChroma
        }

        // Compare against Major and Minor profiles
        var bestKeyName = "Unknown"
        var bestCorrelation = -2.0
        var isMinorKey = false
        var bestRootIndex = 0

        for (root in 0 until 12) {
            // Major Correlation
            val majScore = correlation(chroma, shiftProfile(MAJOR_PROFILE, root))
            if (majScore > bestCorrelation) {
                bestCorrelation = majScore
                bestRootIndex = root
                isMinorKey = false
            }

            // Minor Correlation
            val minScore = correlation(chroma, shiftProfile(MINOR_PROFILE, root))
            if (minScore > bestCorrelation) {
                bestCorrelation = minScore
                bestRootIndex = root
                isMinorKey = true
            }
        }

        val keyConfidence = if (bestCorrelation > 0.15) {
            val dominantRatio = maxChroma / (sumChroma + 0.0001)
            val score = ((bestCorrelation * 0.6 + dominantRatio * 0.4) * 100).roundToInt()
            score.coerceIn(15, 95)
        } else 0

        if (bestCorrelation < 0.2) return Triple("Unknown", "", keyConfidence)

        val rootName = NOTE_NAMES[bestRootIndex]
        val mode = if (isMinorKey) "Minor" else "Major"
        bestKeyName = "$rootName $mode"

        val camelot = CamelotWheel.parseKey(bestKeyName)
        val camelotStr = camelot?.formatted ?: ""

        return Triple(bestKeyName, camelotStr, keyConfidence)
    }

    private fun shiftProfile(profile: DoubleArray, shift: Int): DoubleArray {
        val result = DoubleArray(12)
        for (i in 0 until 12) {
            result[(i + shift) % 12] = profile[i]
        }
        return result
    }

    private fun correlation(x: DoubleArray, y: DoubleArray): Double {
        val meanX = x.average()
        val meanY = y.average()
        var num = 0.0
        var denX = 0.0
        var denY = 0.0

        for (i in 0 until 12) {
            val diffX = x[i] - meanX
            val diffY = y[i] - meanY
            num += diffX * diffY
            denX += diffX * diffX
            denY += diffY * diffY
        }

        val den = sqrt(denX * denY)
        return if (den == 0.0) 0.0 else num / den
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
            val wStepR = cos(angle).toFloat()
            val wStepI = sin(angle).toFloat()

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

    /**
     * Energy Analysis (RMS + Spectral Flux) & BS.1770-4 LUFS Loudness Measurement
     */
    private fun calculateEnergyAndLufs(samples: FloatArray, sampleRate: Int): Pair<Int, Float> {
        if (samples.isEmpty()) return Pair(50, -14.0f)

        // 1. RMS Loudness
        var sumSq = 0.0
        for (s in samples) {
            sumSq += (s * s).toDouble()
        }
        val rms = sqrt(sumSq / samples.size).toFloat()
        val rmsNormalized = (rms / 0.30f * 100f).coerceIn(0f, 100f)

        // 2. Spectral Flux Activity
        val numFrames = (samples.size - FFT_SIZE) / HOP_SIZE
        var totalFlux = 0f
        if (numFrames > 0) {
            val prevSpectrum = FloatArray(FFT_SIZE / 2)
            val real = FloatArray(FFT_SIZE)
            val imag = FloatArray(FFT_SIZE)

            for (f in 0 until numFrames) {
                val offset = f * HOP_SIZE
                for (i in 0 until FFT_SIZE) {
                    real[i] = samples[offset + i]
                    imag[i] = 0f
                }
                fft(real, imag)

                var frameFlux = 0f
                for (k in 0 until FFT_SIZE / 2) {
                    val mag = sqrt(real[k] * real[k] + imag[k] * imag[k])
                    val diff = mag - prevSpectrum[k]
                    if (diff > 0) frameFlux += diff
                    prevSpectrum[k] = mag
                }
                totalFlux += frameFlux
            }
        }
        val avgFlux = if (numFrames > 0) totalFlux / numFrames else 0f
        val fluxNormalized = (avgFlux / 12f * 100f).coerceIn(0f, 100f)

        val energyScore = (0.6f * rmsNormalized + 0.4f * fluxNormalized).roundToInt().coerceIn(0, 100)

        // 3. ITU-R BS.1770-4 LUFS Loudness
        val b0 = 1.53512485958697f
        val b1 = -2.69169618940638f
        val b2 = 1.19839281085285f
        val a1 = -1.69065929318241f
        val a2 = 0.73248077421585f

        val hp_b0 = 1.0f
        val hp_b1 = -2.0f
        val hp_b2 = 1.0f
        val hp_a1 = -1.99004745483398f
        val hp_a2 = 0.99007225036621f

        var x1 = 0f; var x2 = 0f; var y1 = 0f; var y2 = 0f
        var hpx1 = 0f; var hpx2 = 0f; var hpy1 = 0f; var hpy2 = 0f

        var kPowerSum = 0.0
        for (s in samples) {
            val y = b0 * s + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1; x1 = s
            y2 = y1; y1 = y

            val hpy = hp_b0 * y + hp_b1 * hpx1 + hp_b2 * hpx2 - hp_a1 * hpy1 - hp_a2 * hpy2
            hpx2 = hpx1; hpx1 = y
            hpy2 = hpy1; hpy1 = hpy

            kPowerSum += (hpy * hpy).toDouble()
        }

        val meanKPower = kPowerSum / samples.size
        val lufs = if (meanKPower > 1e-10) {
            (-0.691 + 10.0 * log10(meanKPower)).toFloat().coerceIn(-60.0f, 0.0f)
        } else {
            -14.0f
        }

        return Pair(energyScore, lufs)
    }

    private class FloatArrayList(initialCapacity: Int = 100_000) {
        private var data = FloatArray(initialCapacity)
        var size = 0
            private set

        fun add(element: Float) {
            if (size == data.size) {
                data = data.copyOf(data.size * 2)
            }
            data[size++] = element
        }

        fun toArray(): FloatArray = data.copyOf(size)
    }
}
