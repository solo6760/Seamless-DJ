package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.PlaybackParams
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

    private val _engineState = MutableStateFlow(DjEngineState())
    val engineState: StateFlow<DjEngineState> = _engineState.asStateFlow()

    private var djSettings = DjSettings()
    private var tickerJob: Job? = null
    private var transitionJob: Job? = null

    private val beatDetectionEngine = BeatDetectionEngine(context)
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
                val targetSegment = state.segmentTotalSec

                val currentTrack = state.currentTrack
                val trackDurationSec = if (currentTrack != null && currentTrack.durationMs > 0) {
                    (currentTrack.durationMs / 1000).toInt()
                } else targetSegment

                // 1. Phrase-Aligned Transition Check (Requirement 1 & 6)
                val currentTrackPosMs = ((currentTrack?.introOffsetSec ?: 0) + newElapsed) * 1000L
                val currentPhrase = currentTrack?.phraseBoundaries?.lastOrNull { it.timestampMs <= currentTrackPosMs }
                val currentPhraseLabel = currentPhrase?.type?.displayName ?: "Playing"

                // Check if approaching outro phrase or target segment limit
                val outroBoundary = currentTrack?.phraseBoundaries?.firstOrNull { it.type == PhraseType.OUTRO }
                val isAtOutroPhrase = outroBoundary != null && currentTrackPosMs >= outroBoundary.timestampMs - (djSettings.crossfadeDurationSec * 1000L)

                val isAtSegmentLimit = newElapsed >= max(10, targetSegment - djSettings.crossfadeDurationSec)

                _engineState.value = state.copy(
                    segmentElapsedSec = newElapsed,
                    currentPhraseLabel = currentPhraseLabel
                )

                if (isAtOutroPhrase || isAtSegmentLimit) {
                    val triggerReason = if (isAtOutroPhrase) "Natural Outro Phrase Transition" else "Automix Beat Blend"
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
     * Content-Aware Multi-Band Seamless Transition Execution (Requirements 1, 3, 6, 7, 9, 10).
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

        // 3. Calculate LUFS Loudness Normalization Gains (Target: -14.0 LUFS)
        val outgoingLufs = currentTrack.lufs
        val outgoingLufsScale = Math.pow(10.0, ((-14.0f - outgoingLufs) / 20.0f).toDouble()).toFloat().coerceIn(0.3f, 1.8f)

        val incomingLufs = incomingTrack.lufs
        val incomingLufsScale = Math.pow(10.0, ((-14.0f - incomingLufs) / 20.0f).toDouble()).toFloat().coerceIn(0.3f, 1.8f)

        // 4. Setup incoming player at aligned onset drop point
        if (incomingDeck == ActiveDeck.DECK_B) {
            setupPlayerB(incomingTrack, alignedInMs)
        } else {
            setupPlayerA(incomingTrack, alignedInMs)
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

        // 5. Run smooth multi-band EQ / filter / riser transition loop
        val steps = 30
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
                    val eqGains = multiBandEqProcessor.calculateGains(
                        progress = progress,
                        outgoingProfile = currentTrack.frequencyProfile,
                        incomingProfile = incomingTrack.frequencyProfile
                    )
                    // Bass swap: composite gain reflecting low + mid + high isolation
                    val outComposite = eqGains.outgoingOverallVol * (0.5f * eqGains.outgoingLowGain + 0.3f * eqGains.outgoingMidGain + 0.2f * eqGains.outgoingHighGain)
                    val inComposite = eqGains.incomingOverallVol * (0.5f * eqGains.incomingLowGain + 0.3f * eqGains.incomingMidGain + 0.2f * eqGains.incomingHighGain)
                    Pair(outComposite.coerceIn(0f, 1f), inComposite.coerceIn(0f, 1f))
                }
                TransitionType.FILTER_SWEEP -> {
                    val fs = filterSweepProcessor.calculateFilterState(progress)
                    Pair(fs.outgoingVolume, fs.incomingVolume)
                }
                TransitionType.RISER_SWEEP -> {
                    val rs = riserSweepProcessor.calculateRiserState(progress)
                    Pair(rs.outgoingVolume, rs.incomingVolume)
                }
                TransitionType.ECHO_OUT -> {
                    val echo = echoOutProcessor.calculateEchoState(progress)
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

    private fun setupPlayerA(track: Track, startMs: Long) {
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
                    setVolume(0.0f, 0.0f)
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        try {
                            mp.seekTo(startMs.toInt())
                            mp.start()
                        } catch (e: Exception) {
                            Log.e("SeamlessDjEngine", "OnPrepared setup A error", e)
                        }
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e("SeamlessDjEngine", "MediaPlayer setup A error: what=$what, extra=$extra")
                        true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SeamlessDjEngine", "Failed setup Player A", e)
        }
    }

    private fun setupPlayerB(track: Track, startMs: Long) {
        try {
            playerB?.release()
            playerB = null
            if (track.streamUrl.isNotBlank() && !track.isYouTube()) {
                playerB = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .build()
                    )
                    setDataSource(track.streamUrl)
                    setVolume(0.0f, 0.0f)
                    prepareAsync()
                    setOnPreparedListener { mp ->
                        try {
                            mp.seekTo(startMs.toInt())
                            mp.start()
                        } catch (e: Exception) {
                            Log.e("SeamlessDjEngine", "OnPrepared setup B error", e)
                        }
                    }
                    setOnErrorListener { _, what, extra ->
                        Log.e("SeamlessDjEngine", "MediaPlayer setup B error: what=$what, extra=$extra")
                        true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("SeamlessDjEngine", "Failed setup Player B", e)
        }
    }

    fun stopAll() {
        tickerJob?.cancel()
        transitionJob?.cancel()
        try {
            playerA?.stop()
            playerA?.release()
            playerA = null
        } catch (e: Exception) {}

        try {
            playerB?.stop()
            playerB?.release()
            playerB = null
        } catch (e: Exception) {}
    }
}
