package com.example.util

import com.example.data.model.Track
import kotlin.math.abs
import kotlin.math.max

object SmartPlaylistOptimizer {

    /**
     * Calculates compatibility score between currentTrack and nextTrack.
     * Formula per specification:
     * - Key compatibility (Camelot): 60% weight
     * - BPM similarity: 30% weight (1 - abs(current_bpm - next_bpm) / max(current_bpm, next_bpm))
     * - Beat detection quality: 10% weight (1.0 if beat data reliable, 0.5 otherwise)
     *
     * Final score = 0.6 * camelot_score + 0.3 * bpm_similarity + 0.1 * beat_quality
     */
    fun calculateCompatibilityScore(currentTrack: Track, nextTrack: Track): Float {
        // 1. Camelot Key compatibility (0.0 to 1.0)
        val camelotScore = CamelotWheel.getCompatibilityScore(currentTrack.musicalKey, nextTrack.musicalKey)

        // 2. BPM Similarity (0.0 to 1.0)
        val curBpm = if (currentTrack.bpm in 40..220) currentTrack.bpm else 124
        val nextBpm = if (nextTrack.bpm in 40..220) nextTrack.bpm else 124
        val absDiff = abs(curBpm - nextBpm)
        val maxBpm = max(curBpm, nextBpm).toFloat()
        val bpmSimilarity = (1.0f - (absDiff / maxBpm)).coerceIn(0.0f, 1.0f)

        // 3. Beat Detection Quality (1.0 if reliable beat data exists, 0.5 otherwise)
        val beatQuality = if (nextTrack.beatTimesMs.isNotEmpty()) 1.0f else 0.5f

        // Weighted sum
        return (0.6f * camelotScore) + (0.3f * bpmSimilarity) + (0.1f * beatQuality)
    }

    /**
     * Reorders a playlist starting from startTrackIndex using a greedy optimization algorithm.
     * Pick the track with the highest weighted compatibility score at each step.
     * Deterministic tie-breaking: selects the track that appeared earlier in the original list.
     * If playlist has fewer than 3 tracks, returns the original order (rotated to startTrackIndex).
     */
    fun optimizePlaylist(
        originalTracks: List<Track>,
        startTrackIndex: Int = 0
    ): List<Track> {
        if (originalTracks.isEmpty()) return emptyList()

        val validStartIndex = startTrackIndex.coerceIn(0, originalTracks.lastIndex)

        // Fallback for small playlists (< 3 songs)
        if (originalTracks.size < 3) {
            return if (validStartIndex == 0) {
                originalTracks
            } else {
                originalTracks.drop(validStartIndex) + originalTracks.take(validStartIndex)
            }
        }

        val unqueued = originalTracks.toMutableList()
        val startTrack = unqueued.removeAt(validStartIndex)

        val reordered = mutableListOf<Track>()
        reordered.add(startTrack)

        var currentTrack = startTrack

        while (unqueued.isNotEmpty()) {
            var bestIndex = 0
            var maxScore = -1.0f

            for (i in unqueued.indices) {
                val candidate = unqueued[i]
                val score = calculateCompatibilityScore(currentTrack, candidate)
                // Using strict > preserves deterministic tie-breaking (earliest in original order)
                if (score > maxScore) {
                    maxScore = score
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
