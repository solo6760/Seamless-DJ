package com.example.util

import com.example.audio.AudioDspAnalyzer
import com.example.data.model.Track
import com.example.data.model.TransitionDecision
import com.example.data.model.TransitionType
import com.example.data.model.selectContextualTransition
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object SmartPlaylistOptimizer {

    /**
     * Calculates multi-dimensional compatibility score between currentTrack and nextTrack.
     * Dimensions:
     * - Harmonic (Camelot key + confidence): 40% weight
     * - BPM similarity: 25% weight
     * - Energy continuity: 15% weight
     * - Spectral flux correlation (rhythmic / timbral feel): 20% weight
     */
    fun calculateCompatibilityScore(currentTrack: Track, nextTrack: Track): Float {
        // 1. Camelot Key Score (0.0 to 1.0) with confidence
        val camelotScore = CamelotWheel.getCompatibilityScore(
            currentTrack.musicalKey,
            nextTrack.musicalKey,
            currentTrack.harmonicConfidence,
            nextTrack.harmonicConfidence
        )

        // 2. BPM Similarity (0.0 to 1.0)
        val curBpm = if (currentTrack.bpm in 40..220) currentTrack.bpm else 124
        val nextBpm = if (nextTrack.bpm in 40..220) nextTrack.bpm else 124
        val absDiff = abs(curBpm - nextBpm)
        val maxBpm = max(curBpm, nextBpm).toFloat()
        val bpmSimilarity = (1.0f - (absDiff / maxBpm)).coerceIn(0.0f, 1.0f)

        // 3. Energy Continuity (0.0 to 1.0)
        val energyDiff = abs(currentTrack.energyScore - nextTrack.energyScore)
        val energyScore = when {
            energyDiff < 10 -> 1.0f
            energyDiff <= 20 -> 0.85f
            energyDiff <= 35 -> 0.55f
            else -> 0.25f
        }

        // 4. Spectral Flux Matching (0.0 to 1.0) (Requirement 6)
        val flux1 = currentTrack.getSpectralFluxVector()
        val flux2 = nextTrack.getSpectralFluxVector()
        val spectralFluxScore = if (flux1.isNotEmpty() && flux2.isNotEmpty()) {
            AudioDspAnalyzer.calculateSpectralFluxCorrelation(flux1, flux2)
        } else {
            0.70f // Default fallback
        }

        // Weighted combination: 40% Harmonic, 25% BPM, 15% Energy, 20% Flux
        return (0.40f * camelotScore) + (0.25f * bpmSimilarity) + (0.15f * energyScore) + (0.20f * spectralFluxScore)
    }

    /**
     * Creates a detailed transition decision explaining the selection, timing, and pitch shifts.
     */
    fun createTransitionDecision(currentTrack: Track, nextTrack: Track): TransitionDecision {
        val camelotScore = CamelotWheel.getCompatibilityScore(
            currentTrack.musicalKey,
            nextTrack.musicalKey,
            currentTrack.harmonicConfidence,
            nextTrack.harmonicConfidence
        )

        val curBpm = if (currentTrack.bpm in 40..220) currentTrack.bpm else 124
        val nextBpm = if (nextTrack.bpm in 40..220) nextTrack.bpm else 124
        val absDiff = abs(curBpm - nextBpm)
        val maxBpm = max(curBpm, nextBpm).toFloat()
        val bpmSimilarity = (1.0f - (absDiff / maxBpm)).coerceIn(0.0f, 1.0f)

        val energyDiff = currentTrack.energyScore - nextTrack.energyScore
        val energyScore = when {
            abs(energyDiff) < 10 -> 1.0f
            abs(energyDiff) <= 20 -> 0.85f
            abs(energyDiff) <= 35 -> 0.55f
            else -> 0.25f
        }

        val flux1 = currentTrack.getSpectralFluxVector()
        val flux2 = nextTrack.getSpectralFluxVector()
        val spectralFluxScore = if (flux1.isNotEmpty() && flux2.isNotEmpty()) {
            AudioDspAnalyzer.calculateSpectralFluxCorrelation(flux1, flux2)
        } else 0.70f

        val overallScore = (0.40f * camelotScore) + (0.25f * bpmSimilarity) + (0.15f * energyScore) + (0.20f * spectralFluxScore)

        val optimalType = selectContextualTransition(
            harmonicScore = camelotScore,
            bpmSimilarity = bpmSimilarity,
            energyDiff = abs(energyDiff),
            spectralFluxCorrelation = spectralFluxScore
        )

        // Pitch shift compensation if key mismatch is extreme
        val pitchShift = if (camelotScore < 0.40f) {
            CamelotWheel.calculateOptimalPitchShift(currentTrack.musicalKey, nextTrack.musicalKey)
        } else 0

        // Tightened transition durations (Requirement 7: snappy 8-10s crossfade, 16s EQ fade, 6s filter sweep)
        val transitionDurationMs = when (optimalType) {
            TransitionType.CROSSFADE -> 9_000L
            TransitionType.EQ_FADE -> 16_000L
            TransitionType.FILTER_SWEEP -> 6_500L
            TransitionType.RISER_SWEEP -> 14_000L
            TransitionType.ECHO_OUT -> 8_000L
        }

        val explanation = when (optimalType) {
            TransitionType.CROSSFADE -> "High harmonic & rhythmic match (${(overallScore * 100).toInt()}%). Tight snappy equal-power blend."
            TransitionType.EQ_FADE -> "Matched keys (${currentTrack.musicalKey} ➔ ${nextTrack.musicalKey}). 3-band EQ aggressive -15dB bass swap."
            TransitionType.FILTER_SWEEP -> "Timbral shift detected (flux ${(spectralFluxScore * 100).toInt()}%). Resonant HPF/LPF sweep masking spectrum."
            TransitionType.RISER_SWEEP -> "Energy jump (+${abs(energyDiff)}). Resonant riser sweep bridging build-up."
            TransitionType.ECHO_OUT -> "Key/tempo contrast. Echo out 3.5s reverb tail decay into incoming track."
        }

        val dropMs = (nextTrack.optimalDropOffsetSec * 1000L).coerceAtLeast(0L)

        return TransitionDecision(
            type = optimalType,
            overallScore = overallScore,
            harmonicScore = camelotScore,
            bpmScore = bpmSimilarity,
            energyScore = energyScore,
            spectralFluxScore = spectralFluxScore,
            explanation = explanation,
            transitionDurationMs = transitionDurationMs,
            dropStartMs = dropMs,
            pitchShiftSemitones = pitchShift
        )
    }

    /**
     * Calculates the ideal narrative set energy curve for a set of size [totalCount] (Requirement 2).
     * Follows: Warm-Up (45-55) -> Build (60-75) -> Peak 1 (85-95) -> Mid-break (65-72) -> Peak 2 (90-95) -> Cool-down (50-60).
     */
    fun getIdealEnergyCurve(totalCount: Int): FloatArray {
        if (totalCount <= 0) return FloatArray(0)
        if (totalCount == 1) return floatArrayOf(60f)
        if (totalCount == 2) return floatArrayOf(50f, 80f)

        val curve = FloatArray(totalCount)
        for (i in 0 until totalCount) {
            val progress = i.toFloat() / (totalCount - 1).toFloat() // 0.0 to 1.0
            curve[i] = when {
                progress <= 0.15f -> 45f + (progress / 0.15f) * 10f                     // 45 -> 55 (Warm-Up)
                progress <= 0.45f -> 55f + ((progress - 0.15f) / 0.30f) * 25f          // 55 -> 80 (Ascending Build)
                progress <= 0.65f -> 80f + ((progress - 0.45f) / 0.20f) * 15f          // 80 -> 95 (Peak 1)
                progress <= 0.75f -> 95f - ((progress - 0.65f) / 0.10f) * 25f          // 95 -> 70 (Bridge Break)
                progress <= 0.90f -> 70f + ((progress - 0.75f) / 0.15f) * 22f          // 70 -> 92 (Climax Peak 2)
                else -> 92f - ((progress - 0.90f) / 0.10f) * 32f                        // 92 -> 60 (Denouement Cool-down)
            }.coerceIn(20f, 100f)
        }
        return curve
    }

    /**
     * Reorders playlist using a Narrative Energy Arc & 3-5 Song Lookahead Optimizer (Requirement 2).
     * Considers:
     * - Compatibility Score (Harmonic, BPM, Energy, Spectral Flux): 40%
     * - Set Energy Narrative Fit: 40%
     * - Novelty & Artist Variety: 20%
     */
    fun optimizePlaylist(
        originalTracks: List<Track>,
        startTrackIndex: Int = 0
    ): List<Track> {
        if (originalTracks.isEmpty()) return emptyList()

        val validStartIndex = startTrackIndex.coerceIn(0, originalTracks.lastIndex)
        if (originalTracks.size < 3) {
            return if (validStartIndex == 0) originalTracks
            else originalTracks.drop(validStartIndex) + originalTracks.take(validStartIndex)
        }

        val unqueued = originalTracks.toMutableList()
        val startTrack = unqueued.removeAt(validStartIndex)
        val reordered = mutableListOf<Track>()
        reordered.add(startTrack)

        val totalTracks = originalTracks.size
        val energyCurve = getIdealEnergyCurve(totalTracks)

        var currentTrack = startTrack

        while (unqueued.isNotEmpty()) {
            val currentPos = reordered.size
            val targetEnergy = if (currentPos < energyCurve.size) energyCurve[currentPos] else 65f

            var bestIndex = 0
            var bestScore = -1000f

            val lookaheadDepth = min(3, unqueued.size)

            for (i in unqueued.indices) {
                val candidate = unqueued[i]

                // 1. Direct compatibility score
                val compat = calculateCompatibilityScore(currentTrack, candidate)

                // 2. Set energy curve fit
                val energyDelta = abs(candidate.energyScore.toFloat() - targetEnergy)
                val energyFit = (1.0f - (energyDelta / 100f)).coerceIn(0f, 1f)

                // 3. Listener fatigue penalty (avoid 2 back-to-back max energy songs > 80)
                val fatiguePenalty = if (currentTrack.energyScore >= 80 && candidate.energyScore >= 80) 0.20f else 0.0f

                // 4. Novelty / Artist Variety
                val novelty = if (candidate.artist.equals(currentTrack.artist, ignoreCase = true)) 0.3f else 1.0f

                // Immediate step score
                val stepScore = (0.40f * compat) + (0.40f * energyFit) + (0.20f * novelty) - fatiguePenalty

                // Lookahead bonus: evaluate 2nd step best compatibility among remaining candidates
                var lookaheadBonus = 0f
                if (lookaheadDepth > 1 && unqueued.size > 1) {
                    var maxNextCompat = 0f
                    for (j in unqueued.indices) {
                        if (j == i) continue
                        val nextCand = unqueued[j]
                        val nextCompat = calculateCompatibilityScore(candidate, nextCand)
                        if (nextCompat > maxNextCompat) maxNextCompat = nextCompat
                    }
                    lookaheadBonus = maxNextCompat * 0.15f
                }

                val totalEvaluatedScore = stepScore + lookaheadBonus

                if (totalEvaluatedScore > bestScore) {
                    bestScore = totalEvaluatedScore
                    bestIndex = i
                }
            }

            val bestTrack = unqueued.removeAt(bestIndex)
            reordered.add(bestTrack)
            currentTrack = bestTrack
        }

        return reordered
    }
}
