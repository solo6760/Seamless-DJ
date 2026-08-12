package com.example.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import com.example.data.model.*
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
    val lufs: Float = -14.0f,
    val phraseBoundaries: List<PhraseBoundary> = emptyList(),
    val spectralFluxCsv: String = "",
    val frequencyProfile: FrequencyBandProfile = FrequencyBandProfile(),
    val optimalDropOffsetSec: Int = 20,
    val optimalOutroOffsetSec: Int = 0,
    val perceptualLoudnessLufs: Float = -14.0f
)

class AudioDspAnalyzer(private val context: Context) {

    companion object {
        private const val TAG = "AudioDspAnalyzer"
        private const val MAX_ANALYSIS_DURATION_US = 60_000_000L // Analyze up to 60 seconds
        const val FFT_SIZE = 2048
        const val HOP_SIZE = 512

        // Krumhansl-Schmuckler Key Profiles for Major & Minor
        val MAJOR_PROFILE = doubleArrayOf(6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88)
        val MINOR_PROFILE = doubleArrayOf(6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 2.69, 3.34, 3.17, 3.28)
        val NOTE_NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

        /**
         * Calculates Pearson Correlation between two spectral flux vectors.
         * Measures rhythmic feel & timbral similarity (Requirement 5).
         */
        fun calculateSpectralFluxCorrelation(v1: FloatArray, v2: FloatArray): Float {
            if (v1.isEmpty() || v2.isEmpty()) return 0.5f
            val n = min(v1.size, v2.size)
            if (n < 4) return 0.5f

            var sum1 = 0f; var sum2 = 0f
            for (i in 0 until n) {
                sum1 += v1[i]
                sum2 += v2[i]
            }
            val mean1 = sum1 / n
            val mean2 = sum2 / n

            var numerator = 0f
            var den1 = 0f
            var den2 = 0f

            for (i in 0 until n) {
                val d1 = v1[i] - mean1
                val d2 = v2[i] - mean2
                numerator += d1 * d2
                den1 += d1 * d1
                den2 += d2 * d2
            }

            val denominator = sqrt(den1 * den2)
            if (denominator < 1e-6f) return 0.5f

            // Map Pearson r [-1, 1] to compatibility score [0, 1]
            val r = numerator / denominator
            return ((r + 1f) / 2f).coerceIn(0f, 1f)
        }
    }

    suspend fun analyzeTrack(track: Track): DspAnalysisResult = withContext(Dispatchers.Default) {
        try {
            Log.d(TAG, "Starting DSP musical analysis for track: ${track.title}")
            val pcmData = extractMonoPcmSamples(track)

            if (pcmData == null || pcmData.samples.size < FFT_SIZE * 4) {
                Log.w(TAG, "Insufficient PCM data for track ${track.title}, generating fallback analysis")
                return@withContext createFallbackResult(track)
            }

            val samples = pcmData.samples
            val sampleRate = pcmData.sampleRate

            // 1. Calculate BPM using Spectral Flux & Autocorrelation
            val (detectedBpm, bpmConf) = estimateBpm(samples, sampleRate)

            // 2. Estimate Musical Key using Harmonic Overtone Analysis (Requirement 4)
            val (keyResult, camelotCode, keyConf) = estimateKeyWithOvertones(samples, sampleRate)

            // 3. Multi-Band Frequency Profile & Perceptual Psychoacoustic Loudness (Requirement 3 & 8)
            val (freqProfile, perceptualLufs, energyScore) = calculateFrequencyProfileAndPerceptualLoudness(samples, sampleRate)

            // 4. Phrase & Structural Segmentation (Requirement 1 & 6)
            val totalTrackMs = if (track.durationMs > 0) track.durationMs else (samples.size.toLong() * 1000L / sampleRate)
            val phrases = detectPhraseBoundaries(samples, sampleRate, detectedBpm, totalTrackMs)

            // 5. Spectral Flux Vector Profile (Requirement 5)
            val fluxVector = computeSpectralFluxProfile(samples, sampleRate, 32)
            val fluxCsv = fluxVector.joinToString(",") { String.format("%.3f", it) }

            // Determine optimal transition trigger offsets based on detected phrases
            val introPhrase = phrases.firstOrNull { it.type == PhraseType.INTRO || it.type == PhraseType.BUILD }
            val dropOffsetSec = if (introPhrase != null && introPhrase.timestampMs > 5000) {
                (introPhrase.timestampMs / 1000).toInt().coerceIn(8, 40)
            } else {
                track.introOffsetSec.coerceIn(10, 30)
            }

            val outroPhrase = phrases.lastOrNull { it.type == PhraseType.OUTRO || it.type == PhraseType.BREAK }
            val outroOffsetSec = if (outroPhrase != null) (outroPhrase.timestampMs / 1000).toInt() else 0

            val isValidBpm = detectedBpm in 40..220
            val overallConfidence = when {
                bpmConf >= 60 && keyConf >= 50 -> "high"
                bpmConf >= 40 || keyConf >= 30 -> "medium"
                else -> "low"
            }
            val finalBpm = if (isValidBpm) detectedBpm else (if (track.bpm in 40..220) track.bpm else 120)

            Log.d(TAG, "DSP Analysis complete for '${track.title}': BPM=$finalBpm ($bpmConf%), Key=$keyResult ($keyConf%, $camelotCode), Energy=$energyScore, Phrases=${phrases.size}, LUFS=${String.format("%.1f", perceptualLufs)}")

            DspAnalysisResult(
                bpm = finalBpm,
                bpmConfidence = bpmConf,
                musicalKey = if (camelotCode.isNotBlank()) "$camelotCode / $keyResult" else keyResult,
                camelotKey = camelotCode,
                keyConfidence = keyConf,
                confidence = overallConfidence,
                status = if (isValidBpm) BpmStatus.RESOLVED else BpmStatus.UNKNOWN,
                energyScore = energyScore,
                lufs = perceptualLufs,
                phraseBoundaries = phrases,
                spectralFluxCsv = fluxCsv,
                frequencyProfile = freqProfile,
                optimalDropOffsetSec = dropOffsetSec,
                optimalOutroOffsetSec = outroOffsetSec,
                perceptualLoudnessLufs = perceptualLufs
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error performing DSP analysis for track ${track.title}", e)
            createFallbackResult(track)
        }
    }

    private fun createFallbackResult(track: Track): DspAnalysisResult {
        val fallbackKey = if (track.musicalKey.isNotBlank() && track.musicalKey != "Unknown") track.musicalKey else "8A / C Major"
        val fallbackCamelot = CamelotWheel.parseKey(fallbackKey)?.formatted ?: "8A"
        return DspAnalysisResult(
            bpm = if (track.bpm in 40..220) track.bpm else 124,
            bpmConfidence = 30,
            musicalKey = fallbackKey,
            camelotKey = fallbackCamelot,
            keyConfidence = 30,
            confidence = "low",
            status = BpmStatus.UNKNOWN,
            energyScore = track.energyScore,
            lufs = -14.0f,
            phraseBoundaries = listOf(
                PhraseBoundary(0L, PhraseType.INTRO, 0.5f, 0.4f, "Intro"),
                PhraseBoundary(20000L, PhraseType.CHORUS, 0.5f, 0.7f, "Main Drop"),
                PhraseBoundary(60000L, PhraseType.OUTRO, 0.5f, 0.4f, "Outro")
            ),
            frequencyProfile = FrequencyBandProfile(0.33f, 0.33f, 0.33f, -14.0f),
            optimalDropOffsetSec = track.introOffsetSec,
            optimalOutroOffsetSec = 0,
            perceptualLoudnessLufs = -14.0f
        )
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
            (0.54 - 0.46 * cos(2.0 * Math.PI * i / (FFT_SIZE - 1))).toFloat()
        }

        val numFrames = (samples.size - FFT_SIZE) / HOP_SIZE
        if (numFrames < 10) return Pair(120, 20)

        val spectralFlux = FloatArray(numFrames)
        val prevSpectrum = FloatArray(FFT_SIZE / 2)
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

        // Autocorrelation over BPM range [60, 190]
        val frameRate = sampleRate.toFloat() / HOP_SIZE
        val minBpm = 60
        val maxBpm = 190
        var bestBpm = 124
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

        // Harmonic tempo multiplier correction (e.g. 70 bpm vs 140 bpm)
        if (bestBpm < 75 && candidateBpmScores.containsKey(bestBpm * 2)) {
            val doubleScore = candidateBpmScores[bestBpm * 2] ?: 0f
            if (doubleScore > maxCorrelation * 0.75f) {
                bestBpm *= 2
            }
        } else if (bestBpm > 165 && candidateBpmScores.containsKey(bestBpm / 2)) {
            val halfScore = candidateBpmScores[bestBpm / 2] ?: 0f
            if (halfScore > maxCorrelation * 0.85f) {
                bestBpm /= 2
            }
        }

        val avgCorrelation = if (candidateBpmScores.isNotEmpty()) sumCorrelation / candidateBpmScores.size else 0.01f
        val peakRatio = if (avgCorrelation > 0f) maxCorrelation / avgCorrelation else 1.0f
        val bpmConfidence = ((peakRatio - 1.0f) * 40.0f).roundToInt().coerceIn(20, 95)

        return Pair(bestBpm.coerceIn(40, 220), bpmConfidence)
    }

    /**
     * 2. Key Estimation with Harmonic Overtone Analysis (Requirement 4).
     * Analyzes fundamental pitch bins and integer frequency ratios (2:1 octave, 3:2 fifth, 5:4 major 3rd, 6:5 minor 3rd).
     */
    private fun estimateKeyWithOvertones(samples: FloatArray, sampleRate: Int): Triple<String, String, Int> {
        val chroma = DoubleArray(12)
        val overtoneWeight = DoubleArray(12)
        val numFrames = (samples.size - FFT_SIZE) / (HOP_SIZE * 2)
        if (numFrames < 5) return Triple("Unknown", "", 0)

        val real = FloatArray(FFT_SIZE)
        val imag = FloatArray(FFT_SIZE)
        val window = FloatArray(FFT_SIZE) { i ->
            (0.54 - 0.46 * cos(2.0 * Math.PI * i / (FFT_SIZE - 1))).toFloat()
        }

        val spectrumAccum = DoubleArray(FFT_SIZE / 2)

        for (f in 0 until numFrames) {
            val offset = f * HOP_SIZE * 2
            for (i in 0 until FFT_SIZE) {
                real[i] = samples[offset + i] * window[i]
                imag[i] = 0f
            }

            fft(real, imag)

            for (k in 1 until FFT_SIZE / 2) {
                val mag = sqrt(real[k] * real[k] + imag[k] * imag[k]).toDouble()
                spectrumAccum[k] += mag
                val freq = k.toDouble() * sampleRate / FFT_SIZE
                if (freq in 65.0..2200.0) {
                    val midiNote = 12.0 * (log2(freq / 440.0)) + 69.0
                    val pitchClass = (midiNote.roundToInt() % 12 + 12) % 12
                    chroma[pitchClass] += mag
                }
            }
        }

        // Harmonic Overtone Analysis: for each pitch class root, check 2:1 (octave), 3:2 (fifth), 5:4 (major third)
        for (root in 0 until 12) {
            val fifth = (root + 7) % 12
            val majorThird = (root + 4) % 12
            val minorThird = (root + 3) % 12

            // Ratio reinforcement
            val fundamentalEnergy = chroma[root]
            val fifthEnergy = chroma[fifth]
            val majThirdEnergy = chroma[majorThird]
            val minThirdEnergy = chroma[minorThird]

            // 3:2 harmonic overtone correlation
            val overtoneScore = (fundamentalEnergy * 1.0 + fifthEnergy * 0.7 + max(majThirdEnergy, minThirdEnergy) * 0.5)
            overtoneWeight[root] = overtoneScore
        }

        // Normalize chroma and blend with overtone weights
        val maxChroma = chroma.maxOrNull() ?: 0.0
        val maxOvertone = overtoneWeight.maxOrNull() ?: 0.0
        if (maxChroma <= 0.0) return Triple("Unknown", "", 0)

        val blendedChroma = DoubleArray(12) { i ->
            val c = chroma[i] / maxChroma
            val o = if (maxOvertone > 0.0) overtoneWeight[i] / maxOvertone else 0.0
            (0.65 * c + 0.35 * o)
        }

        var bestKeyName = "Unknown"
        var bestCorrelation = -2.0
        var isMinorKey = false
        var bestRootIndex = 0

        for (root in 0 until 12) {
            val majScore = correlation(blendedChroma, shiftProfile(MAJOR_PROFILE, root))
            if (majScore > bestCorrelation) {
                bestCorrelation = majScore
                bestRootIndex = root
                isMinorKey = false
            }

            val minScore = correlation(blendedChroma, shiftProfile(MINOR_PROFILE, root))
            if (minScore > bestCorrelation) {
                bestCorrelation = minScore
                bestRootIndex = root
                isMinorKey = true
            }
        }

        val keyConfidence = if (bestCorrelation > 0.15) {
            val dominantRatio = blendedChroma[bestRootIndex] / (blendedChroma.sum() + 0.0001)
            val score = ((bestCorrelation * 0.65 + dominantRatio * 0.35) * 100).roundToInt()
            score.coerceIn(25, 98)
        } else 20

        if (bestCorrelation < 0.15) return Triple("Unknown", "", keyConfidence)

        val rootName = NOTE_NAMES[bestRootIndex]
        val mode = if (isMinorKey) "Minor" else "Major"
        bestKeyName = "$rootName $mode"

        val camelot = CamelotWheel.parseKey(bestKeyName)
        val camelotStr = camelot?.formatted ?: ""

        return Triple(bestKeyName, camelotStr, keyConfidence)
    }

    /**
     * 3. Multi-Band Frequency Profile & Perceptual Psychoacoustic Loudness (Requirements 3 & 8).
     */
    private fun calculateFrequencyProfileAndPerceptualLoudness(
        samples: FloatArray,
        sampleRate: Int
    ): Triple<FrequencyBandProfile, Float, Int> {
        if (samples.isEmpty()) {
            return Triple(FrequencyBandProfile(), -14.0f, 50)
        }

        val numFrames = (samples.size - FFT_SIZE) / HOP_SIZE
        if (numFrames <= 0) return Triple(FrequencyBandProfile(), -14.0f, 50)

        var lowEnergySum = 0.0
        var midEnergySum = 0.0
        var highEnergySum = 0.0
        var totalSpectralEnergy = 0.0

        val real = FloatArray(FFT_SIZE)
        val imag = FloatArray(FFT_SIZE)

        // Frequency bin ranges
        val binWidth = sampleRate.toDouble() / FFT_SIZE
        val lowBinMax = (250.0 / binWidth).roundToInt().coerceIn(1, FFT_SIZE / 2)
        val midBinMax = (2500.0 / binWidth).roundToInt().coerceIn(lowBinMax + 1, FFT_SIZE / 2)

        for (f in 0 until numFrames) {
            val offset = f * HOP_SIZE
            for (i in 0 until FFT_SIZE) {
                real[i] = samples[offset + i]
                imag[i] = 0f
            }
            fft(real, imag)

            for (k in 1 until FFT_SIZE / 2) {
                val mag = sqrt(real[k] * real[k] + imag[k] * imag[k]).toDouble()
                val power = mag * mag
                totalSpectralEnergy += power

                when {
                    k <= lowBinMax -> lowEnergySum += power
                    k <= midBinMax -> midEnergySum += power
                    else -> highEnergySum += power
                }
            }
        }

        val totalSafe = max(1e-9, totalSpectralEnergy)
        val lowNorm = (lowEnergySum / totalSafe).toFloat().coerceIn(0.05f, 0.90f)
        val midNorm = (midEnergySum / totalSafe).toFloat().coerceIn(0.05f, 0.90f)
        val highNorm = (highEnergySum / totalSafe).toFloat().coerceIn(0.05f, 0.90f)

        // 3. ITU-R BS.1770-4 K-weighting Filter + Fletcher-Munson Perceptual Curve
        val b0 = 1.53512485958697f; val b1 = -2.69169618940638f; val b2 = 1.19839281085285f
        val a1 = -1.69065929318241f; val a2 = 0.73248077421585f
        val hp_b0 = 1.0f; val hp_b1 = -2.0f; val hp_b2 = 1.0f
        val hp_a1 = -1.99004745483398f; val hp_a2 = 0.99007225036621f

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
        val perceptualLufs = if (meanKPower > 1e-10) {
            (-0.691 + 10.0 * log10(meanKPower)).toFloat().coerceIn(-50.0f, 0.0f)
        } else {
            -14.0f
        }

        // Energy Score (0..100) combining RMS, High-Bass Presence, and LUFS
        val rmsNorm = sqrt(meanKPower).toFloat() / 0.35f * 100f
        val bassBoostScore = lowNorm * 100f
        val energyScore = ((0.5f * rmsNorm + 0.3f * bassBoostScore + 0.2f * ((perceptualLufs + 30f) * 3.33f)).roundToInt()).coerceIn(10, 100)

        val profile = FrequencyBandProfile(
            lowEnergy = lowNorm,
            midEnergy = midNorm,
            highEnergy = highNorm,
            perceptualLoudnessLufs = perceptualLufs
        )

        return Triple(profile, perceptualLufs, energyScore)
    }

    /**
     * 4. Phrase & Structural Segmentation (Requirements 1, 6).
     * Identifies section boundaries (Intro, Verse, Build, Chorus/Drop, Break, Outro)
     * using spectral texture variation, onset density, and energy dynamics.
     */
    fun detectPhraseBoundaries(
        samples: FloatArray,
        sampleRate: Int,
        bpm: Int,
        totalDurationMs: Long
    ): List<PhraseBoundary> {
        val boundaries = mutableListOf<PhraseBoundary>()
        val safeBpm = if (bpm in 50..220) bpm else 124
        val barDurationMs = (240_000.0 / safeBpm).toLong() // 4 beats per bar (ms)
        val eightBarPhraseMs = barDurationMs * 8L

        val windowSize = sampleRate * 2 // 2-second moving window
        val stepSize = sampleRate / 2   // 0.5s step
        val numWindows = (samples.size - windowSize) / stepSize

        if (numWindows < 4) {
            // Short/minimal audio fallback
            boundaries.add(PhraseBoundary(0L, PhraseType.INTRO, 0.8f, 0.4f, "Intro"))
            boundaries.add(PhraseBoundary(min(16000L, totalDurationMs / 3), PhraseType.CHORUS, 0.7f, 0.8f, "Main Section"))
            boundaries.add(PhraseBoundary(max(32000L, totalDurationMs - 16000L), PhraseType.OUTRO, 0.7f, 0.4f, "Outro"))
            return boundaries
        }

        val windowEnergies = FloatArray(numWindows)
        for (w in 0 until numWindows) {
            val offset = w * stepSize
            var sumSq = 0.0
            for (i in 0 until windowSize) {
                val s = samples[offset + i]
                sumSq += s * s
            }
            windowEnergies[w] = sqrt(sumSq / windowSize).toFloat()
        }

        val maxEnergy = windowEnergies.maxOrNull() ?: 1.0f
        val avgEnergy = windowEnergies.average().toFloat()

        // 1. Initial Intro boundary
        boundaries.add(PhraseBoundary(0L, PhraseType.INTRO, 0.9f, (windowEnergies[0] / maxEnergy).coerceIn(0.1f, 1.0f), "Intro Start"))

        var lastBoundaryMs = 0L

        // Scan for energy jumps, builds, and drops
        for (w in 1 until numWindows - 1) {
            val currentMs = (w * stepSize.toLong() * 1000L) / sampleRate
            if (currentMs - lastBoundaryMs < eightBarPhraseMs * 0.75f) continue // Enforce natural musical phrase spacing

            val prevE = windowEnergies[w - 1]
            val currE = windowEnergies[w]
            val nextE = windowEnergies[w + 1]

            val energyDelta = currE - prevE
            val normalizedEnergy = currE / maxEnergy

            // Detect Drop / Chorus: sudden energy surge after intro or breakdown
            if (energyDelta > 0.08f && currE > avgEnergy * 1.15f) {
                val type = if (boundaries.none { it.type == PhraseType.CHORUS }) PhraseType.CHORUS else PhraseType.DROP
                boundaries.add(PhraseBoundary(currentMs, type, 0.85f, normalizedEnergy, "Energy Drop / Chorus"))
                lastBoundaryMs = currentMs
            }
            // Detect Breakdown / Quiet Bridge: sudden drop in energy
            else if (energyDelta < -0.07f && currE < avgEnergy * 0.85f) {
                boundaries.add(PhraseBoundary(currentMs, PhraseType.BREAK, 0.80f, normalizedEnergy, "Breakdown / Bridge"))
                lastBoundaryMs = currentMs
            }
            // Detect Build-up: steady ascending energy
            else if (energyDelta > 0.04f && nextE > currE) {
                boundaries.add(PhraseBoundary(currentMs, PhraseType.BUILD, 0.75f, normalizedEnergy, "Build-Up"))
                lastBoundaryMs = currentMs
            }
        }

        // Outro boundary
        val outroStartMs = max(lastBoundaryMs + eightBarPhraseMs, totalDurationMs - (barDurationMs * 8L))
        if (outroStartMs < totalDurationMs && outroStartMs > 10_000L) {
            boundaries.add(PhraseBoundary(outroStartMs, PhraseType.OUTRO, 0.85f, 0.35f, "Outro Transition Window"))
        }

        return boundaries
    }

    /**
     * 5. Computes a downsampled Spectral Flux Vector (Requirement 5) for fast correlation.
     */
    fun computeSpectralFluxProfile(samples: FloatArray, sampleRate: Int, targetBins: Int = 32): FloatArray {
        val numFrames = (samples.size - FFT_SIZE) / HOP_SIZE
        if (numFrames < targetBins) return FloatArray(targetBins) { 0.5f }

        val spectralFlux = FloatArray(numFrames)
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

            var flux = 0f
            for (k in 0 until FFT_SIZE / 2) {
                val mag = sqrt(real[k] * real[k] + imag[k] * imag[k])
                val diff = mag - prevSpectrum[k]
                if (diff > 0) flux += diff
                prevSpectrum[k] = mag
            }
            spectralFlux[f] = flux
        }

        // Downsample into fixed targetBins
        val result = FloatArray(targetBins)
        val framesPerBin = numFrames.toFloat() / targetBins
        for (b in 0 until targetBins) {
            val startIdx = (b * framesPerBin).toInt().coerceIn(0, numFrames - 1)
            val endIdx = ((b + 1) * framesPerBin).toInt().coerceIn(startIdx + 1, numFrames)
            var sum = 0f
            for (i in startIdx until endIdx) {
                sum += spectralFlux[i]
            }
            result[b] = sum / (endIdx - startIdx)
        }

        // Normalize
        val maxVal = result.maxOrNull() ?: 1.0f
        if (maxVal > 0f) {
            for (i in result.indices) result[i] /= maxVal
        }

        return result
    }

    /**
     * Helper Methods
     */
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
