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

    @Test
    fun testPhraseBoundaryDetectionAndSegmentation() {
        val phrases = listOf(
            PhraseBoundary(0L, PhraseType.INTRO, 0.9f, 0.3f, "Intro"),
            PhraseBoundary(15000L, PhraseType.BUILD, 0.8f, 0.6f, "Build-Up"),
            PhraseBoundary(30000L, PhraseType.CHORUS, 0.95f, 0.9f, "Main Drop"),
            PhraseBoundary(75000L, PhraseType.OUTRO, 0.85f, 0.35f, "Outro")
        )

        val track = Track(
            id = "phrase_test",
            title = "Phrase Test Track",
            artist = "Artist",
            durationMs = 90000L,
            phraseBoundaries = phrases
        )

        val intro = track.phraseBoundaries.firstOrNull { it.type == PhraseType.INTRO }
        assertNotNull(intro)
        assertEquals("0:00", intro?.formattedTime)

        val drop = track.phraseBoundaries.firstOrNull { it.type == PhraseType.CHORUS }
        assertNotNull(drop)
        assertEquals("0:30", drop?.formattedTime)
    }

    @Test
    fun testSpectralFluxCorrelation() {
        val v1 = floatArrayOf(0.1f, 0.4f, 0.7f, 0.9f, 0.5f, 0.2f)
        val v2Same = floatArrayOf(0.1f, 0.4f, 0.7f, 0.9f, 0.5f, 0.2f)
        val v3Inverse = floatArrayOf(0.9f, 0.6f, 0.3f, 0.1f, 0.5f, 0.8f)

        val corrSelf = com.example.audio.AudioDspAnalyzer.calculateSpectralFluxCorrelation(v1, v2Same)
        val corrInv = com.example.audio.AudioDspAnalyzer.calculateSpectralFluxCorrelation(v1, v3Inverse)

        assertTrue("Identical flux vectors must yield high correlation score (~1.0)", corrSelf > 0.90f)
        assertTrue("Inverted flux vectors must yield lower correlation score", corrInv < corrSelf)
    }

    @Test
    fun testOnsetBasedBeatAlignment() {
        val engine = com.example.audio.BeatDetectionEngine(null)
        val outgoingBeats = listOf(0L, 500L, 1000L, 1500L, 2000L, 2500L)
        val incomingBeats = listOf(0L, 480L, 960L, 1440L, 1920L)

        val (outBeat, inBeat) = engine.findAlignedOnsetTransition(
            outgoingBeats = outgoingBeats,
            incomingBeats = incomingBeats,
            targetTransitionTimeMs = 1020L,
            incomingDropOffsetMs = 950L
        )

        assertEquals("Should align to closest outgoing beat 1000ms", 1000L, outBeat)
        assertEquals("Should align to closest incoming beat 960ms", 960L, inBeat)
    }

    @Test
    fun testDjSettingsShortSegmentDefaults() {
        val settings = DjSettings()
        assertEquals("Default segment duration must be 90s", 90, settings.segmentDurationSec)
        assertTrue("Segment duration must be in 90-120s range", settings.segmentDurationSec in 90..120)
        assertEquals("Default crossfade duration must be 10s (8-12s range)", 10, settings.crossfadeDurationSec)
    }

    @Test
    fun testTransitionFadesWithin8To12Seconds() {
        val trackA = Track(id = "1", title = "A", artist = "A", bpm = 126, musicalKey = "8A", energyScore = 80)
        val trackB = Track(id = "2", title = "B", artist = "B", bpm = 126, musicalKey = "8A", energyScore = 80)
        val trackC = Track(id = "3", title = "C", artist = "C", bpm = 90, musicalKey = "2B", energyScore = 20)

        val decisionA = SmartPlaylistOptimizer.createTransitionDecision(trackA, trackB)
        val decisionB = SmartPlaylistOptimizer.createTransitionDecision(trackA, trackC)

        assertTrue("Transition duration must be >= 8s", decisionA.transitionDurationMs >= 8_000L)
        assertTrue("Transition duration must be <= 12s", decisionA.transitionDurationMs <= 12_000L)
        assertTrue("Incompatible transition duration must be >= 8s", decisionB.transitionDurationMs >= 8_000L)
        assertTrue("Incompatible transition duration must be <= 12s", decisionB.transitionDurationMs <= 12_000L)
    }

    @Test
    fun testShortSegmentTransitionTimingAndPhraseBoundary() {
        val targetSegment = 90
        val windowStart = targetSegment - 10 // 80s
        val windowEnd = targetSegment + 5   // 95s

        // Track with a chorus/outro boundary at 85s
        val phrasesWithBoundary = listOf(
            PhraseBoundary(0L, PhraseType.INTRO, 0.9f, 0.3f, "Intro"),
            PhraseBoundary(85000L, PhraseType.CHORUS, 0.9f, 0.8f, "Chorus End")
        )
        val trackWithBoundary = Track(
            id = "t_boundary",
            title = "Track with Boundary",
            artist = "Artist",
            durationMs = 210000L,
            introOffsetSec = 0,
            phraseBoundaries = phrasesWithBoundary
        )

        val matchingPhrase = trackWithBoundary.phraseBoundaries.firstOrNull { boundary ->
            val boundaryElapsedSec = ((boundary.timestampMs / 1000L) - trackWithBoundary.introOffsetSec).toInt()
            boundaryElapsedSec in windowStart..windowEnd
        }
        assertNotNull("Should detect phrase boundary in 80s..95s window", matchingPhrase)
        assertEquals(85, (matchingPhrase!!.timestampMs / 1000L).toInt())

        // Track without boundary in window should fall back to 90s target
        val phrasesWithoutBoundary = listOf(
            PhraseBoundary(0L, PhraseType.INTRO, 0.9f, 0.3f, "Intro"),
            PhraseBoundary(40000L, PhraseType.CHORUS, 0.9f, 0.8f, "Chorus")
        )
        val trackWithoutBoundary = Track(
            id = "t_no_boundary",
            title = "Track without Boundary in window",
            artist = "Artist",
            durationMs = 210000L,
            introOffsetSec = 0,
            phraseBoundaries = phrasesWithoutBoundary
        )
        val matchingPhraseFallback = trackWithoutBoundary.phraseBoundaries.firstOrNull { boundary ->
            val boundaryElapsedSec = ((boundary.timestampMs / 1000L) - trackWithoutBoundary.introOffsetSec).toInt()
            boundaryElapsedSec in windowStart..windowEnd
        }
        assertNull("Should find no phrase boundary in 80..95s window", matchingPhraseFallback)
    }

    @Test
    fun testInfiniteQueueLoopBackSimulation() {
        val tracks = listOf(
            Track(id = "1", title = "Track 1", artist = "A"),
            Track(id = "2", title = "Track 2", artist = "B"),
            Track(id = "3", title = "Track 3", artist = "C")
        )

        var current = tracks[0]
        var next = tracks[1]
        var queue = tracks.drop(1) // [2, 3]

        // Advance 1: Track 1 -> Track 2
        var updatedQueue = if (queue.isNotEmpty() && queue.first() == next) queue.drop(1) else queue
        var loopBackQueue = updatedQueue + current
        current = next
        next = loopBackQueue.first()
        queue = loopBackQueue
        assertEquals("Track 2", current.title)
        assertEquals("Track 3", next.title)
        assertEquals(listOf("Track 3", "Track 1"), queue.map { it.title })

        // Advance 2: Track 2 -> Track 3
        updatedQueue = if (queue.isNotEmpty() && queue.first() == next) queue.drop(1) else queue
        loopBackQueue = updatedQueue + current
        current = next
        next = loopBackQueue.first()
        queue = loopBackQueue
        assertEquals("Track 3", current.title)
        assertEquals("Track 1", next.title)
        assertEquals(listOf("Track 1", "Track 2"), queue.map { it.title })

        // Advance 3: Track 3 -> Track 1 (Loop back)
        updatedQueue = if (queue.isNotEmpty() && queue.first() == next) queue.drop(1) else queue
        loopBackQueue = updatedQueue + current
        current = next
        next = loopBackQueue.first()
        queue = loopBackQueue
        assertEquals("Track 1", current.title)
        assertEquals("Track 2", next.title)
        assertEquals(listOf("Track 2", "Track 3"), queue.map { it.title })
    }
}
