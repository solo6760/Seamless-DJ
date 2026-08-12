package com.example

import com.example.audio.processor.MultiBandEqProcessor
import com.example.audio.processor.RiserSweepProcessor
import com.example.data.model.*
import com.example.util.CamelotWheel
import com.example.util.SmartPlaylistOptimizer
import org.junit.Assert.*
import org.junit.Test

class DspTransitionUnitTest {

    @Test
    fun testCamelotWheelCompatibility() {
        // Exact match
        assertEquals(1.0f, CamelotWheel.getCompatibilityScore("8A", "8A"), 0.01f)

        // Adjacent key (+1 / -1 on same wheel)
        assertEquals(0.85f, CamelotWheel.getCompatibilityScore("8A", "9A"), 0.01f)
        assertEquals(0.85f, CamelotWheel.getCompatibilityScore("8A", "7A"), 0.01f)

        // Relative major/minor switch (8A <-> 8B)
        assertEquals(0.90f, CamelotWheel.getCompatibilityScore("8A", "8B"), 0.01f)

        // Diagonal harmonic shift (+1 and relative, 8A <-> 9B)
        assertEquals(0.75f, CamelotWheel.getCompatibilityScore("8A", "9B"), 0.01f)

        // Distant / Incompatible key
        val distantScore = CamelotWheel.getCompatibilityScore("8A", "2B")
        assertTrue(distantScore < 0.5f)
    }

    @Test
    fun testCamelotPitchShiftCompensation() {
        // 8A to 9A needs +1 semitone
        val shift = CamelotWheel.getOptimalPitchShiftSemitones("8A", "9A")
        assertNotNull(shift)
        assertEquals(1, shift)

        // If distance > 2, should return null (fallback to EQ/Echo rather than aggressive pitch shift)
        val excessiveShift = CamelotWheel.getOptimalPitchShiftSemitones("1A", "7A")
        assertNull(excessiveShift)
    }

    @Test
    fun testMultiBandEqProcessorGains() {
        val processor = MultiBandEqProcessor()
        val outProfile = FrequencyBandProfile(lowEnergy = 0.5f, midEnergy = 0.3f, highEnergy = 0.2f)
        val inProfile = FrequencyBandProfile(lowEnergy = 0.4f, midEnergy = 0.4f, highEnergy = 0.2f)

        // At progress = 0.0 (outgoing full, incoming silent)
        val startGains = processor.calculateGains(0.0f, outProfile, inProfile)
        assertEquals(1.0f, startGains.outgoingLowGain, 0.05f)
        assertEquals(0.0f, startGains.incomingLowGain, 0.05f)

        // At progress = 0.5 (midpoint swap: low end must avoid overlap muddiness)
        val midGains = processor.calculateGains(0.5f, outProfile, inProfile)
        val totalLow = midGains.outgoingLowGain + midGains.incomingLowGain
        assertTrue("Composite low energy must avoid bass clashing boom", totalLow <= 1.25f)

        // At progress = 1.0 (outgoing silent, incoming full)
        val endGains = processor.calculateGains(1.0f, outProfile, inProfile)
        assertEquals(0.0f, endGains.outgoingLowGain, 0.05f)
        assertEquals(1.0f, endGains.incomingLowGain, 0.05f)
    }

    @Test
    fun testRiserSweepProcessor() {
        val processor = RiserSweepProcessor()

        val start = processor.calculateRiserState(0.0f)
        assertEquals(1.0f, start.outgoingVolume, 0.01f)
        assertEquals(0.0f, start.incomingVolume, 0.01f)
        assertEquals(20f, start.highPassCutoffHz, 1f)

        val apex = processor.calculateRiserState(0.8f)
        assertTrue("High-pass cutoff should sweep up to build tension", apex.highPassCutoffHz > 800f)
        assertTrue("Resonance should build", apex.filterResonance > 1.2f)

        val end = processor.calculateRiserState(1.0f)
        assertEquals(0.0f, end.outgoingVolume, 0.01f)
        assertEquals(1.0f, end.incomingVolume, 0.01f)
    }

    @Test
    fun testSmartPlaylistOptimizer4DScoring() {
        val trackA = Track(
            id = "t1",
            title = "Track A",
            artist = "Artist A",
            bpm = 126,
            musicalKey = "8A / Fm",
            energyScore = 70,
            spectralFluxProfileCsv = "0.2,0.4,0.6,0.8,0.5"
        )

        val trackBCompatible = Track(
            id = "t2",
            title = "Track B",
            artist = "Artist B",
            bpm = 126,
            musicalKey = "8A / Fm",
            energyScore = 75,
            spectralFluxProfileCsv = "0.2,0.4,0.6,0.8,0.5"
        )

        val trackCIncompatible = Track(
            id = "t3",
            title = "Track C",
            artist = "Artist C",
            bpm = 95,
            musicalKey = "2B / F#",
            energyScore = 20,
            spectralFluxProfileCsv = "0.01,0.02,0.01"
        )

        val scoreCompatible = SmartPlaylistOptimizer.calculateCompatibilityScore(trackA, trackBCompatible)
        val scoreIncompatible = SmartPlaylistOptimizer.calculateCompatibilityScore(trackA, trackCIncompatible)

        assertTrue(scoreCompatible > 0.85f)
        assertTrue(scoreIncompatible < 0.45f)

        val decisionCompatible = SmartPlaylistOptimizer.createTransitionDecision(trackA, trackBCompatible)
        assertEquals(TransitionType.EQ_FADE, decisionCompatible.type)

        val decisionIncompatible = SmartPlaylistOptimizer.createTransitionDecision(trackA, trackCIncompatible)
        assertEquals(TransitionType.ECHO_OUT, decisionIncompatible.type)
    }

    @Test
    fun testNarrativeArcPlaylistOptimization() {
        val tracks = listOf(
            Track(id = "1", title = "Peak Bang", artist = "A", bpm = 130, musicalKey = "9A", energyScore = 95),
            Track(id = "2", title = "Warmup", artist = "B", bpm = 120, musicalKey = "8A", energyScore = 40),
            Track(id = "3", title = "Build 1", artist = "C", bpm = 124, musicalKey = "8A", energyScore = 60),
            Track(id = "4", title = "Build 2", artist = "D", bpm = 126, musicalKey = "9A", energyScore = 80),
            Track(id = "5", title = "Cool Down", artist = "E", bpm = 122, musicalKey = "9B", energyScore = 50)
        )

        val optimized = SmartPlaylistOptimizer.optimizePlaylist(tracks, startTrackIndex = 1) // Start with Warmup
        assertEquals("Warmup should remain starting track", "2", optimized.first().id)
        assertEquals("All tracks must be preserved without loss", 5, optimized.size)
    }
}
