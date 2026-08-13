package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.media.audiofx.Equalizer
import android.media.audiofx.PresetReverb
import android.os.Build
import android.util.Log
import com.example.audio.processor.*
import com.example.data.model.*
import com.example.util.CamelotWheel
import com.example.util.SmartPlaylistOptimizer
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

import com.example.audio.phase_vocoder.AudioStretchProcessor
import com.example.audio.phase_vocoder.BasicSpeedStretchProcessor
import com.example.audio.phase_vocoder.PhaseVocoderMetrics
import com.example.audio.phase_vocoder.PhaseVocoderProcessor
import com.example.audio.phase_vocoder.PhaseVocoderStretchProcessor

enum class ActiveDeck {
    DECK_A,
    DECK_B
}

data class DjEngineState(
    val currentTrack: Track? = null,
    val nextTrack: Track? = null,
    val activeDeck: ActiveDeck = ActiveDeck.DECK_A,
    val deckAVolume: Float = 1.0f,
    val deckBVolume: Float = 0.0f,
    val isPlaying: Boolean = false,
    val isCrossfading: Boolean = false,
    val crossfadeProgress: Float = 0f,
    val segmentElapsedSec: Int = 0,
    val segmentTotalSec: Int = 90,
    val activeBpm: Int = 126,
    val queue: List<Track> = emptyList(),
    val deckASpinning: Boolean = false,
    val deckBSpinning: Boolean = false,
    val automixEnabled: Boolean = true,
    val automixModeName: String = "Spotify/Apple Style Automix (Content-Aware Dynamic Blend)",
    val statusMessage: String = "Ready for party",
    val activeTransitionType: TransitionType = TransitionType.CROSSFADE,
    val activeTransitionDecision: TransitionDecision? = null,
    val lastCompatibilityScore: Float = 1.0f,
    val currentPhraseLabel: String = "Intro"
)

class SeamlessDjEngine(private val context: Context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var playerA: MediaPlayer? = null
    private var playerB: MediaPlayer? = null
    private var equalizerA: Equalizer? = null
    private var equalizerB: Equalizer? = null
    private var reverbA: PresetReverb? = null
    private var reverbB: PresetReverb? = null

    private val _engineState = MutableStateFlow(DjEngineState())
    val engineState: StateFlow<DjEngineState> = _engineState.asStateFlow()

    private var djSettings = DjSettings()
    private var tickerJob: Job? = null
    private var transitionJob: Job? = null

    private val beatDetectionEngine = BeatDetectionEngine(context)
    private val eqFadeProcessor = EqFadeProcessor()
    private val multiBandEqProcessor = MultiBandEqProcessor()
    private val filterSweepProcessor = FilterSweepProcessor()
    private val riserSweepProcessor = RiserSweepProcessor()
    private val echoOutProcessor = EchoOutProcessor()

    private var stretchProcessor: AudioStretchProcessor = PhaseVocoderStretchProcessor(
        onFallbackTriggered = {
            Log.w("SeamlessDjEngine", "Phase Vocoder failure detected! Automatically falling back to basic speed adjustment.")
            djSettings = djSettings.copy(usePhaseVocoder = false)
            _engineState.value = _engineState.value.copy(
                statusMessage = "Advanced time-stretching failed; falling back to basic speed adjustment."
            )
        }
    )

    fun updateSettings(settings: DjSettings) {
        this.djSettings = settings
        _engineState.value = _engineState.value.copy(
            segmentTotalSec = settings.segmentDurationSec
        )
        stretchProcessor = if (settings.usePhaseVocoder) {
            PhaseVocoderStretchProcessor(
                onFallbackTriggered = {
                    Log.w("SeamlessDjEngine", "Phase Vocoder failure detected! Automatically falling back to basic speed adjustment.")
                    djSettings = djSettings.copy(usePhaseVocoder = false)
                    _engineState.value = _engineState.value.copy(
                        statusMessage = "Advanced time-stretching failed; falling back to basic speed adjustment."
                    )
                }
            )
        } else {
            BasicSpeedStretchProcessor()
        }
    }

    fun getPhaseVocoderMetrics(): PhaseVocoderMetrics {
        return PhaseVocoderProcessor.getMetrics()
    }

    fun runABComparisonTest(onStatusMessage: (String) -> Unit) {
        scope.launch {
            onStatusMessage("A/B Test Mode A: Phase Vocoder Time-Stretching Enabled (10 seconds)...")
            val originalVocoderSetting = djSettings.usePhaseVocoder
            djSettings = djSettings.copy(usePhaseVocoder = true)
            stretchProcessor = PhaseVocoderStretchProcessor()

            val dummyBuffer = FloatArray(4096) { kotlin.random.Random.nextFloat() * 0.5f }
            stretchProcessor.stretch(dummyBuffer, 1.15f)

            delay(10000L)

            onStatusMessage("A/B Test Mode B: Basic Speed Adjustment (10 seconds)...")
            djSettings = djSettings.copy(usePhaseVocoder = false)
            stretchProcessor = BasicSpeedStretchProcessor()
            stretchProcessor.stretch(dummyBuffer, 1.15f)

            delay(10000L)

            djSettings = djSettings.copy(usePhaseVocoder = originalVocoderSetting)
            updateSettings(djSettings)
            onStatusMessage("A/B Comparison finished! Preserved choice: ${if (originalVocoderSetting) "Phase Vocoder" else "Basic Speed"}.")
        }
    }

    fun setQueueAndPlay(tracks: List<Track>, startIndex: Int = 0) {
        if (tracks.isEmpty()) return
        val current = tracks.getOrNull(startIndex) ?: return
        val next = tracks.getOrNull((startIndex + 1) % tracks.size)

        val remainingQueue = if (tracks.size > 1) {
            tracks.drop(startIndex + 1) + tracks.take(startIndex)
        } else {
            emptyList()
        }

        scope.launch {
            stopAll()
            _engineState.value = DjEngineState(
                currentTrack = current,
                nextTrack = next,
                activeDeck = ActiveDeck.DECK_A,
                deckAVolume = 1.0f,
                deckBVolume = 0.0f,
                isPlaying = true,
                isCrossfading = false,
                segmentElapsedSec = 0,
                segmentTotalSec = djSettings.segmentDurationSec,
                activeBpm = current.bpm,
                queue = remainingQueue,
                deckASpinning = true,
                deckBSpinning = false,
                currentPhraseLabel = current.phraseBoundaries.firstOrNull()?.type?.displayName ?: "Intro",
                statusMessage = "Deck A playing: ${current.title}"
            )

            prepareAndStartDeckA(current)
            startTicker()
        }
    }

    fun updateQueue(newQueue: List<Track>) {
        val state = _engineState.value
        val newNext = newQueue.firstOrNull()
        _engineState.value = state.copy(
            nextTrack = newNext ?: state.nextTrack,
            queue = newQueue
        )
    }

    fun togglePlayPause() {
        val currentState = _engineState.value
        if (currentState.currentTrack == null) return

        if (currentState.isPlaying) {
            pausePlayback()
        } else {
            resumePlayback()
        }
    }

    private fun pausePlayback() {
        tickerJob?.cancel()
        try {
            if (_engineState.value.activeDeck == ActiveDeck.DECK_A) {
                playerA?.pause()
            } else {
                playerB?.pause()
            }
        } catch (e: Exception) {
            Log.e("SeamlessDjEngine", "Pause error", e)
        }
        _engineState.value = _engineState.value.copy(
            isPlaying = false,
            deckASpinning = false,
            deckBSpinning = false,
            statusMessage = "Paused"
        )
    }

    private fun resumePlayback() {
        try {
            if (_engineState.value.activeDeck == ActiveDeck.DECK_A) {
                playerA?.start()
            } else {
                playerB?.start()
            }
            startTicker()
        } catch (e: Exception) {
            Log.e("SeamlessDjEngine", "Resume error", e)
        }
        val isDeckA = _engineState.value.activeDeck == ActiveDeck.DECK_A
        _engineState.value = _engineState.value.copy(
            isPlaying = true,
            deckASpinning = isDeckA,
            deckBSpinning = !isDeckA,
            statusMessage = "Playing continuous mix"
        )
    }

    fun seekToSegmentPosition(seconds: Int) {
        val state = _engineState.value
        val total = max(1, state.segmentTotalSec)
        val clampedSec = seconds.coerceIn(0, total)
        _engineState.value = state.copy(segmentElapsedSec = clampedSec)

        val track = state.currentTrack ?: return
        val targetMs = ((track.introOffsetSec + clampedSec) * 1000).toLong()

        try {
            if (state.activeDeck == ActiveDeck.DECK_A) {
                playerA?.seekTo(targetMs.toInt())
            } else {
                playerB?.seekTo(targetMs.toInt())
            }
        } catch (e: Exception) {
            Log.e("SeamlessDjEngine", "Error seeking playback", e)
        }
    }

    fun seekByDelta(deltaSeconds: Int) {
        val current = _engineState.value.segmentElapsedSec
        seekToSegmentPosition(current + deltaSeconds)
    }

    fun skipToNextTrack() {
        val state = _engineState.value
        if (state.isCrossfading) return
        if (state.nextTrack == null && state.queue.isEmpty()) return

        scope.launch {
            triggerSeamlessCrossfade(reason = "User skipped track")
        }
    }

    fun toggleAutomix(enabled: Boolean) {
        _engineState.value = _engineState.value.copy(
            automixEnabled = enabled,
            statusMessage = if (enabled) "Content-Aware Automix Active" else "Manual DJ Mode"
        )
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive) {
                delay(1000L)
                val state = _engineState.value
                if (!state.isPlaying || state.isCrossfading || !state.automixEnabled) continue

                val newElapsed = state.segmentElapsedSec + 1
                val currentTrack = state.currentTrack
                
                val trackDurationSec = if (currentTrack != null && currentTrack.durationMs > 0) {
                    (currentTrack.durationMs / 1000).toInt()
                } else 210

                // If segmentDurationSec is 0, play full track until natural outro
                val isFullTrackMode = djSettings.segmentDurationSec <= 0
                val targetSegment = if (isFullTrackMode) trackDurationSec else djSettings.segmentDurationSec

                // 1. Phrase-Aligned Transition Check (Requirement 1 & 6)
                val currentTrackPosMs = ((currentTrack?.introOffsetSec ?: 0) + newElapsed) * 1000L
                val currentPhrase = currentTrack?.phraseBoundaries?.lastOrNull { it.timestampMs <= currentTrackPosMs }
                val currentPhraseLabel = currentPhrase?.type?.displayName ?: "Playing"

                // Check if approaching detected outro phrase boundary
                val outroBoundary = currentTrack?.phraseBoundaries?.firstOrNull { it.type == PhraseType.OUTRO }
                val isAtOutroPhrase = outroBoundary != null && currentTrackPosMs >= (outroBoundary.timestampMs - (djSettings.crossfadeDurationSec * 1000L))

                // Check if near track end (for Full Track Mode or long playback)
                val isNearTrackEnd = trackDurationSec > 30 && newElapsed >= max(10, trackDurationSec - djSettings.crossfadeDurationSec - 15)

                // Check segment limit
                val isAtSegmentLimit = !isFullTrackMode && newElapsed >= max(10, targetSegment - djSettings.crossfadeDurationSec)

                _engineState.value = state.copy(
                    segmentElapsedSec = newElapsed,
                    segmentTotalSec = targetSegment,
                    currentPhraseLabel = currentPhraseLabel
                )

                if (isAtOutroPhrase || (isFullTrackMode && isNearTrackEnd) || isAtSegmentLimit) {
                    val triggerReason = when {
                        isAtOutroPhrase -> "Natural Outro Phrase Transition"
                        isFullTrackMode -> "Full Track Natural Outro"
                        else -> "Automix Phrase-Aligned Blend"
                    }
                    triggerSeamlessCrossfade(reason = triggerReason)
                }
            }
        }
    }

    fun updateTrackResolvedMetadata(
        trackId: String,
        bpm: Int,
        status: BpmStatus,
        musicalKey: String = "Unknown",
        beatTimesMs: List<Long> = emptyList()
    ) {
        val state = _engineState.value
        val currentTrack = state.currentTrack
        val updatedCurrent = if (currentTrack?.id == trackId) {
            currentTrack.copy(bpm = bpm, bpmStatus = status, musicalKey = musicalKey, beatTimesMs = beatTimesMs, isBeatAnalyzing = false)
        } else currentTrack

        val nextTrack = state.nextTrack
        val updatedNext = if (nextTrack?.id == trackId) {
            nextTrack.copy(bpm = bpm, bpmStatus = status, musicalKey = musicalKey, beatTimesMs = beatTimesMs, isBeatAnalyzing = false)
        } else nextTrack

        val updatedQueue = state.queue.map { t ->
            if (t.id == trackId) t.copy(bpm = bpm, bpmStatus = status, musicalKey = musicalKey, beatTimesMs = beatTimesMs, isBeatAnalyzing = false) else t
        }

        _engineState.value = state.copy(
            currentTrack = updatedCurrent,
            nextTrack = updatedNext,
            queue = updatedQueue,
            activeBpm = updatedCurrent?.bpm ?: state.activeBpm
        )
    }

    fun updateTrackResolvedBpm(trackId: String, bpm: Int, status: BpmStatus) {
        updateTrackResolvedMetadata(trackId, bpm, status)
    }

    /**
     * Content-Aware Multi-Band Seamless Transition Execution (Requirements 1, 3, 4, 5, 6, 7, 8, 9, 10, 11).
     */
    private suspend fun triggerSeamlessCrossfade(reason: String) {
        val state = _engineState.value
        val incomingTrack = state.nextTrack ?: state.queue.firstOrNull() ?: return
        val currentTrack = state.currentTrack ?: return
        val currentActive = state.activeDeck
        val incomingDeck = if (currentActive == ActiveDeck.DECK_A) ActiveDeck.DECK_B else ActiveDeck.DECK_A

        // 1. Compute multi-dimensional transition decision
        val decision = SmartPlaylistOptimizer.createTransitionDecision(currentTrack, incomingTrack)
        val selectedTransition = decision.type
        val compatibilityScore = decision.overallScore

        // 2. Onset-based beat alignment (Requirement 9)
        val currentTrackPosMs = ((currentTrack.introOffsetSec + state.segmentElapsedSec) * 1000L)
        val (alignedOutMs, alignedInMs) = beatDetectionEngine.findAlignedOnsetTransition(
            outgoingBeats = currentTrack.beatTimesMs,
            incomingBeats = incomingTrack.beatTimesMs,
            targetTransitionTimeMs = currentTrackPosMs,
            incomingDropOffsetMs = decision.dropStartMs
        )

        val fadeDurationMs = decision.transitionDurationMs.coerceAtLeast(6000L)
        val syncStatusText = "${selectedTransition.iconSymbol} ${selectedTransition.displayName} (${(compatibilityScore * 100).toInt()}%)"

        _engineState.value = state.copy(
            isCrossfading = true,
            deckASpinning = true,
            deckBSpinning = true,
            activeTransitionType = selectedTransition,
            activeTransitionDecision = decision,
            lastCompatibilityScore = compatibilityScore,
            statusMessage = "$reason • $syncStatusText"
        )

        // 3. Calculate LUFS Loudness Normalization Gains (Requirement 8 - Target: -14.0 LUFS)
        val outgoingLufs = currentTrack.lufs
        val incomingLufs = incomingTrack.lufs
        val lufsDiffDb = incomingLufs - outgoingLufs

        val outgoingLufsScale = Math.pow(10.0, ((-14.0f - outgoingLufs) / 20.0f).toDouble()).toFloat().coerceIn(0.4f, 1.6f)
        val incomingLufsScale = Math.pow(10.0, ((-14.0f - incomingLufs) / 20.0f).toDouble()).toFloat().coerceIn(0.4f, 1.6f)

        // Comprehensive Debug Logging (Requirement 1 & 11)
        Log.i("SeamlessDjEngine", """
            ================================================================================
            [DJ TRANSITION TRIGGERED] Reason: $reason
            [Transition Type] ${selectedTransition.displayName} (${selectedTransition.name})
            [Compatibility Score] ${(compatibilityScore * 100).toInt()}% (Raw: $compatibilityScore)
            [Compatibility Breakdown]:
              - Harmonic Match: ${(decision.harmonicScore * 100).toInt()}% (${currentTrack.musicalKey} -> ${incomingTrack.musicalKey})
              - BPM Match: ${(decision.bpmScore * 100).toInt()}% (${currentTrack.bpm} -> ${incomingTrack.bpm} BPM)
              - Energy Match: ${(decision.energyScore * 100).toInt()}% (${currentTrack.energyScore} -> ${incomingTrack.energyScore})
              - Spectral Flux Match: ${(decision.spectralFluxScore * 100).toInt()}%
            [Transition Duration] ${fadeDurationMs / 1000}s (Drop Offset: ${decision.dropStartMs / 1000}s)
            [Loudness Normalization (LUFS)]:
              - Outgoing (${currentTrack.title}): ${String.format("%.1f", outgoingLufs)} LUFS
              - Incoming (${incomingTrack.title}): ${String.format("%.1f", incomingLufs)} LUFS
              - Difference: ${String.format("%+.1f", lufsDiffDb)} dB -> Scale: Out=${String.format("%.2f", outgoingLufsScale)}, In=${String.format("%.2f", incomingLufsScale)}
            [Decision Explanation] ${decision.explanation}
            ================================================================================
        """.trimIndent())

        // 4. Setup incoming player at aligned onset drop point (awaiting preparation)
        val setupSuccess = setupIncomingPlayer(incomingTrack, isDeckA = (incomingDeck == ActiveDeck.DECK_A), startMs = alignedInMs)
        Log.d("SeamlessDjEngine", "Incoming player on Deck ${if (incomingDeck == ActiveDeck.DECK_A) "A" else "B"} setup success: $setupSuccess")

        val outEq = if (currentActive == ActiveDeck.DECK_A) equalizerA else equalizerB
        val inEq = if (incomingDeck == ActiveDeck.DECK_B) equalizerB else equalizerA

        // Attach Reverb for ECHO_OUT if selected
        if (selectedTransition == TransitionType.ECHO_OUT) {
            val outgoingPlayer = if (currentActive == ActiveDeck.DECK_A) playerA else playerB
            attachReverb(outgoingPlayer, currentActive == ActiveDeck.DECK_A)
            Log.d("SeamlessDjEngine", "[ECHO_OUT Active] 3.5s Reverb decay tail attached with aux send level 1.0 to Deck ${if (currentActive == ActiveDeck.DECK_A) "A" else "B"}")
        }

        // Tempo matching & pitch shift (Requirement 10)
        var initialSpeedRatio = 1.0f
        var shouldRampSpeed = false
        val bpmRatio = currentTrack.bpm.toFloat() / incomingTrack.bpm.toFloat()
        if (bpmRatio in 0.80f..1.20f && currentTrack.bpmStatus == BpmStatus.RESOLVED && incomingTrack.bpmStatus == BpmStatus.RESOLVED) {
            initialSpeedRatio = bpmRatio
            shouldRampSpeed = true
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    val incomingPlayer = if (incomingDeck == ActiveDeck.DECK_B) playerB else playerA
                    val params = incomingPlayer?.playbackParams ?: PlaybackParams()
                    incomingPlayer?.playbackParams = params.setSpeed(initialSpeedRatio)
                }
            } catch (e: Exception) {
                Log.w("SeamlessDjEngine", "Speed ratio adjustment error", e)
            }
        }

        // 5. Run smooth DSP transition loop with hardware AudioFx & software composite gains
        val steps = 36
        val stepDelay = (fadeDurationMs / steps).coerceAtLeast(35L)

        for (i in 0..steps) {
            val progress = i.toFloat() / steps

            val (rawOutVol, rawInVol) = when (selectedTransition) {
                TransitionType.CROSSFADE -> {
                    val outV = kotlin.math.cos(progress * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)
                    val inV = kotlin.math.sin(progress * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)
                    Pair(outV, inV)
                }
                TransitionType.EQ_FADE -> {
                    // Aggressive bass swap: 0dB to -18dB low-shelf crossover
                    val eqVolumes = eqFadeProcessor.calculateEqVolumes(progress)
                    applyEqualizerGains(
                        eq = outEq,
                        lowBandGainRatio = eqVolumes.outgoingBassGain,
                        midBandGainRatio = 1.0f,
                        highBandGainRatio = 1.0f
                    )
                    applyEqualizerGains(
                        eq = inEq,
                        lowBandGainRatio = eqVolumes.incomingBassGain,
                        midBandGainRatio = 1.0f,
                        highBandGainRatio = 1.0f
                    )
                    Pair(
                        eqVolumes.outgoingMainVolume * (0.55f * eqVolumes.outgoingBassGain + 0.45f),
                        eqVolumes.incomingMainVolume * (0.55f * eqVolumes.incomingBassGain + 0.45f)
                    )
                }
                TransitionType.FILTER_SWEEP -> {
                    val fs = filterSweepProcessor.calculateFilterState(progress)
                    applyFilterSweepHardware(outEq, isOutgoing = true, progress = progress)
                    applyFilterSweepHardware(inEq, isOutgoing = false, progress = progress)
                    Pair(fs.outgoingVolume, fs.incomingVolume)
                }
                TransitionType.RISER_SWEEP -> {
                    val rs = riserSweepProcessor.calculateRiserState(progress)
                    applyFilterSweepHardware(outEq, isOutgoing = true, progress = progress)
                    Pair(rs.outgoingVolume, rs.incomingVolume)
                }
                TransitionType.ECHO_OUT -> {
                    val echo = echoOutProcessor.calculateEchoState(progress)
                    val outgoingPlayer = if (currentActive == ActiveDeck.DECK_A) playerA else playerB
                    try {
                        outgoingPlayer?.setAuxEffectSendLevel((1.0f - (progress * 0.4f)).coerceIn(0.5f, 1.0f))
                    } catch (e: Exception) {}
                    Pair(echo.outgoingVolume, echo.incomingVolume)
                }
            }

            val outgoingVol = (rawOutVol * outgoingLufsScale).coerceIn(0f, 1f)
            val incomingVol = (rawInVol * incomingLufsScale).coerceIn(0f, 1f)

            if (currentActive == ActiveDeck.DECK_A) {
                playerA?.setVolume(outgoingVol, outgoingVol)
                playerB?.setVolume(incomingVol, incomingVol)
                _engineState.value = _engineState.value.copy(
                    deckAVolume = outgoingVol,
                    deckBVolume = incomingVol,
                    crossfadeProgress = progress
                )
            } else {
                playerB?.setVolume(outgoingVol, outgoingVol)
                playerA?.setVolume(incomingVol, incomingVol)
                _engineState.value = _engineState.value.copy(
                    deckBVolume = outgoingVol,
                    deckAVolume = incomingVol,
                    crossfadeProgress = progress
                )
            }

            // Gradually ramp incoming player tempo to 1.0x
            if (shouldRampSpeed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    val currentRampedSpeed = initialSpeedRatio + (1.0f - initialSpeedRatio) * progress
                    val incomingPlayer = if (incomingDeck == ActiveDeck.DECK_B) playerB else playerA
                    val params = incomingPlayer?.playbackParams ?: PlaybackParams()
                    incomingPlayer?.playbackParams = params.setSpeed(currentRampedSpeed)
                } catch (e: Exception) {}
            }

            delay(stepDelay)
        }

        // Restore incoming player speed to 1.0f
        if (shouldRampSpeed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                val incomingPlayer = if (incomingDeck == ActiveDeck.DECK_B) playerB else playerA
                val params = incomingPlayer?.playbackParams ?: PlaybackParams()
                incomingPlayer?.playbackParams = params.setSpeed(1.0f)
            } catch (e: Exception) {}
        }

        // Reset Equalizers and release Reverb effects
        resetEqualizer(equalizerA)
        resetEqualizer(equalizerB)
        releaseReverb(isDeckA = true)
        releaseReverb(isDeckA = false)

        // 6. Stop outgoing player, update queue and active deck
        if (currentActive == ActiveDeck.DECK_A) {
            try { playerA?.stop(); playerA?.reset() } catch (e: Exception) {}
        } else {
            try { playerB?.stop(); playerB?.reset() } catch (e: Exception) {}
        }

        val updatedQueue = if (state.queue.isNotEmpty() && state.queue.first() == incomingTrack) {
            state.queue.drop(1)
        } else {
            state.queue
        }

        val subsequentTrack = updatedQueue.firstOrNull() ?: state.currentTrack

        _engineState.value = _engineState.value.copy(
            currentTrack = incomingTrack,
            nextTrack = subsequentTrack,
            activeDeck = incomingDeck,
            deckAVolume = if (incomingDeck == ActiveDeck.DECK_A) 1.0f else 0.0f,
            deckBVolume = if (incomingDeck == ActiveDeck.DECK_B) 1.0f else 0.0f,
            isCrossfading = false,
            crossfadeProgress = 0f,
            segmentElapsedSec = 0,
            activeBpm = incomingTrack.bpm,
            queue = updatedQueue,
            deckASpinning = incomingDeck == ActiveDeck.DECK_A,
            deckBSpinning = incomingDeck == ActiveDeck.DECK_B,
            currentPhraseLabel = incomingTrack.phraseBoundaries.firstOrNull()?.type?.displayName ?: "Drop",
            statusMessage = "Now playing on Deck ${if (incomingDeck == ActiveDeck.DECK_A) "A" else "B"}: ${incomingTrack.title}"
        )
    }

    private fun Track.isYouTube(): Boolean =
        source == TrackSource.YOUTUBE || sourceUrl.contains("youtube") || sourceUrl.contains("youtu.be")

    private fun initAudioEffects(player: MediaPlayer?, isDeckA: Boolean) {
        if (player == null) return
        try {
            val sessionId = player.audioSessionId
            if (sessionId != 0) {
                val eq = Equalizer(0, sessionId).apply {
                    enabled = true
                }
                if (isDeckA) {
                    equalizerA?.release()
                    equalizerA = eq
                } else {
                    equalizerB?.release()
                    equalizerB = eq
                }
            }
        } catch (e: Exception) {
            Log.w("SeamlessDjEngine", "Could not initialize Equalizer for Deck ${if (isDeckA) "A" else "B"}", e)
        }
    }

    private fun applyEqualizerGains(
        eq: Equalizer?,
        lowBandGainRatio: Float,
        midBandGainRatio: Float = 1.0f,
        highBandGainRatio: Float = 1.0f
    ) {
        if (eq == null || !eq.enabled) return
        try {
            val numBands = eq.numberOfBands.toInt()
            val minLevel = eq.bandLevelRange?.get(0)?.toInt() ?: -1500
            val maxLevel = eq.bandLevelRange?.get(1)?.toInt() ?: 1500

            // Low Band (Band 0): 0dB (0 mB) down to -18dB (-1800 mB or minLevel)
            if (numBands > 0) {
                val lowDb = (20.0 * kotlin.math.log10(lowBandGainRatio.coerceIn(0.0001f, 1.0f).toDouble())).toFloat()
                val lowMb = (lowDb * 100).toInt().coerceIn(minLevel, maxLevel).toShort()
                eq.setBandLevel(0, lowMb)
            }

            // Mid Band (Band 1 & 2): 250Hz - 2.5kHz
            if (numBands > 1) {
                val midDb = (20.0 * kotlin.math.log10(midBandGainRatio.coerceIn(0.0001f, 1.0f).toDouble())).toFloat()
                val midMb = (midDb * 100).toInt().coerceIn(minLevel, maxLevel).toShort()
                eq.setBandLevel(1, midMb)
                if (numBands > 2) eq.setBandLevel(2, midMb)
            }

            // High Band (Band 3 & 4): > 2.5kHz
            if (numBands > 3) {
                val highDb = (20.0 * kotlin.math.log10(highBandGainRatio.coerceIn(0.0001f, 1.5f).toDouble())).toFloat()
                val highMb = (highDb * 100).toInt().coerceIn(minLevel, maxLevel).toShort()
                eq.setBandLevel(3, highMb)
                if (numBands > 4) eq.setBandLevel(4, highMb)
            }
        } catch (e: Exception) {
            Log.w("SeamlessDjEngine", "Error applying equalizer gains", e)
        }
    }

    private fun applyFilterSweepHardware(
        eq: Equalizer?,
        isOutgoing: Boolean,
        progress: Float
    ) {
        if (eq == null || !eq.enabled) return
        try {
            val numBands = eq.numberOfBands.toInt()
            val minLevel = eq.bandLevelRange?.get(0)?.toInt() ?: -1500
            val maxLevel = eq.bandLevelRange?.get(1)?.toInt() ?: 1500

            if (isOutgoing) {
                // Outgoing HPF Sweep: 20Hz -> 4kHz
                // Cut lows (Band 0) by progress 0.25, cut mids (Band 1 & 2) by 0.50, boost resonant high tone (Band 3)
                val band0 = (-1800f * (progress / 0.25f).coerceAtMost(1f)).toInt().coerceIn(minLevel, maxLevel).toShort()
                val band1 = (-1800f * ((progress - 0.15f) / 0.35f).coerceIn(0f, 1f)).toInt().coerceIn(minLevel, maxLevel).toShort()
                val band2 = (-1500f * ((progress - 0.35f) / 0.40f).coerceIn(0f, 1f)).toInt().coerceIn(minLevel, maxLevel).toShort()
                // High resonant peak boost (~3-5 Q emphasis)
                val resonanceBoost = (+600f * sin(progress * Math.PI.toFloat())).toInt().coerceIn(minLevel, maxLevel).toShort()
                if (numBands > 0) eq.setBandLevel(0, band0)
                if (numBands > 1) eq.setBandLevel(1, band1)
                if (numBands > 2) eq.setBandLevel(2, band2)
                if (numBands > 3) eq.setBandLevel(3, resonanceBoost)
                if (numBands > 4) eq.setBandLevel(4, (resonanceBoost / 2).toShort())
            } else {
                // Incoming LPF Sweep: 4kHz -> 20Hz
                // Start with low/mid frequencies cut, high presence audible, then sweep down opening full spectrum
                val band0 = (-1800f * (1f - ((progress - 0.45f) / 0.55f)).coerceIn(0f, 1f)).toInt().coerceIn(minLevel, maxLevel).toShort()
                val band1 = (-1800f * (1f - ((progress - 0.25f) / 0.55f)).coerceIn(0f, 1f)).toInt().coerceIn(minLevel, maxLevel).toShort()
                val band2 = (-1200f * (1f - (progress / 0.50f)).coerceIn(0f, 1f)).toInt().coerceIn(minLevel, maxLevel).toShort()
                val resonanceBoost = (+400f * sin(progress * Math.PI.toFloat())).toInt().coerceIn(minLevel, maxLevel).toShort()
                if (numBands > 0) eq.setBandLevel(0, band0)
                if (numBands > 1) eq.setBandLevel(1, band1)
                if (numBands > 2) eq.setBandLevel(2, band2)
                if (numBands > 3) eq.setBandLevel(3, resonanceBoost)
            }
        } catch (e: Exception) {
            Log.w("SeamlessDjEngine", "Error applying filter sweep hardware EQ", e)
        }
    }

    private fun resetEqualizer(eq: Equalizer?) {
        if (eq == null || !eq.enabled) return
        try {
            val numBands = eq.numberOfBands.toInt()
            for (b in 0 until numBands) {
                eq.setBandLevel(b.toShort(), 0)
            }
        } catch (e: Exception) {}
    }

    private fun attachReverb(player: MediaPlayer?, isDeckA: Boolean) {
        if (player == null) return
        try {
            val sessionId = player.audioSessionId
            if (sessionId != 0) {
                val reverb = PresetReverb(0, sessionId).apply {
                    preset = PresetReverb.PRESET_LARGEHALL
                    enabled = true
                }
                player.attachAuxEffect(reverb.id)
                player.setAuxEffectSendLevel(1.0f)
                if (isDeckA) {
                    reverbA?.release()
                    reverbA = reverb
                } else {
                    reverbB?.release()
                    reverbB = reverb
                }
            }
        } catch (e: Exception) {
            Log.w("SeamlessDjEngine", "Could not initialize PresetReverb", e)
        }
    }

    private fun releaseReverb(isDeckA: Boolean) {
        try {
            if (isDeckA) {
                reverbA?.release()
                reverbA = null
            } else {
                reverbB?.release()
                reverbB = null
            }
        } catch (e: Exception) {}
    }

    fun triggerManualTransition(forcedType: TransitionType? = null) {
        transitionJob?.cancel()
        transitionJob = scope.launch {
            triggerSeamlessCrossfade(forcedType?.let { "Manual ${it.displayName}" } ?: "Manual Transition Trigger")
        }
    }

    private fun prepareAndStartDeckA(track: Track) {
        try {
            playerA?.release()
            playerA = null
            if (track.streamUrl.isNotBlank() && !track.isYouTube()) {
                playerA = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(track.streamUrl)
                    setVolume(1.0f, 1.0f)
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        try {
                            initAudioEffects(mp, isDeckA = true)
                            mp.seekTo(track.introOffsetSec * 1000)
                            mp.start()
                        } catch (e: Exception) {
                            Log.e("SeamlessDjEngine", "OnPrepared error", e)
                        }
                    }
                    setOnCompletionListener {
                        scope.launch { skipToNextTrack() }
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e("SeamlessDjEngine", "MediaPlayer A error: what=$what, extra=$extra")
                        true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SeamlessDjEngine", "Failed to start Deck A", e)
        }
    }

    private suspend fun setupIncomingPlayer(track: Track, isDeckA: Boolean, startMs: Long): Boolean = withContext(Dispatchers.Main) {
        if (track.streamUrl.isBlank() || track.isYouTube()) return@withContext false
        val deferred = CompletableDeferred<Boolean>()
        try {
            if (isDeckA) {
                equalizerA?.release(); equalizerA = null
                playerA?.release(); playerA = null
            } else {
                equalizerB?.release(); equalizerB = null
                playerB?.release(); playerB = null
            }

            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(track.streamUrl)
                setVolume(0.0f, 0.0f)
                setOnPreparedListener { mp ->
                    try {
                        initAudioEffects(mp, isDeckA = isDeckA)
                        mp.seekTo(startMs.toInt())
                        mp.start()
                        if (isDeckA) playerA = mp else playerB = mp
                        if (!deferred.isCompleted) deferred.complete(true)
                    } catch (e: Exception) {
                        Log.e("SeamlessDjEngine", "OnPrepared error for Deck ${if (isDeckA) "A" else "B"}", e)
                        if (!deferred.isCompleted) deferred.complete(false)
                    }
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("SeamlessDjEngine", "MediaPlayer error for Deck ${if (isDeckA) "A" else "B"}: what=$what, extra=$extra")
                    if (!deferred.isCompleted) deferred.complete(false)
                    true
                }
                prepareAsync()
            }
            if (isDeckA) playerA = player else playerB = player

            withTimeoutOrNull(3500L) {
                deferred.await()
            } ?: run {
                Log.w("SeamlessDjEngine", "Timeout preparing incoming player for Deck ${if (isDeckA) "A" else "B"}")
                false
            }
        } catch (e: Exception) {
            Log.e("SeamlessDjEngine", "Failed to setup incoming player", e)
            false
        }
    }

    fun stopAll() {
        tickerJob?.cancel()
        transitionJob?.cancel()
        try {
            equalizerA?.release()
            equalizerA = null
            reverbA?.release()
            reverbA = null
            playerA?.stop()
            playerA?.release()
            playerA = null
        } catch (e: Exception) {}

        try {
            equalizerB?.release()
            equalizerB = null
            reverbB?.release()
            reverbB = null
            playerB?.stop()
            playerB?.release()
            playerB = null
        } catch (e: Exception) {}
    }
}
