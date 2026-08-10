package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.data.model.BpmStatus
import com.example.data.model.DjSettings
import com.example.data.model.Track
import com.example.data.model.TrackSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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
    val automixModeName: String = "Spotify/Apple Style Automix (Equal Power Beat-Blend)",
    val statusMessage: String = "Ready for party"
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
        val currentTrack = _engineState.value.currentTrack ?: return
        scope.launch {
            onStatusMessage("A/B Test Mode A: Phase Vocoder Time-Stretching Enabled (10 seconds)...")
            val originalVocoderSetting = djSettings.usePhaseVocoder
            djSettings = djSettings.copy(usePhaseVocoder = true)
            stretchProcessor = PhaseVocoderStretchProcessor()

            // Simulate synthetic buffer stretch for test verification
            val dummyBuffer = FloatArray(4096) { kotlin.random.Random.nextFloat() * 0.5f }
            stretchProcessor.stretch(dummyBuffer, 1.15f)

            delay(10000L)

            onStatusMessage("A/B Test Mode B: Basic Speed Adjustment (10 seconds)...")
            djSettings = djSettings.copy(usePhaseVocoder = false)
            stretchProcessor = BasicSpeedStretchProcessor()
            stretchProcessor.stretch(dummyBuffer, 1.15f)

            delay(10000L)

            // Restore
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

    /**
     * SEEK & SCRUBBING FUNCTIONALITY:
     * Allows rewinding or fast-forwarding during playback.
     */
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

    /**
     * SKIP FUNCTION:
     * Triggers smooth beat-matched crossfade transition immediately on skip button tap!
     */
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
            statusMessage = if (enabled) "Automix Mode Activated (Equal Power Continuous Crossfade)" else "Manual DJ Mode"
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
                val transitionTriggerSec = max(10, targetSegment - djSettings.crossfadeDurationSec)

                _engineState.value = state.copy(segmentElapsedSec = newElapsed)

                if (newElapsed >= transitionTriggerSec) {
                    triggerSeamlessCrossfade(reason = "Automix ${djSettings.crossfadeDurationSec}s Beat Blend")
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

    private suspend fun triggerSeamlessCrossfade(reason: String) {
        val state = _engineState.value
        val incomingTrack = state.nextTrack ?: state.queue.firstOrNull() ?: return
        val currentTrack = state.currentTrack
        val currentActive = state.activeDeck
        val incomingDeck = if (currentActive == ActiveDeck.DECK_A) ActiveDeck.DECK_B else ActiveDeck.DECK_A

        // Calculate Camelot Key Harmonic Compatibility Score (0.0 to 1.0)
        val keyScore = com.example.util.CamelotWheel.getCompatibilityScore(currentTrack?.musicalKey, incomingTrack.musicalKey)
        val smoothnessInfo = com.example.util.CamelotWheel.getSmoothnessInfo(keyScore)

        val outgoingBpmKnown = currentTrack != null && currentTrack.bpmStatus == BpmStatus.RESOLVED && currentTrack.bpm in 40..220
        val incomingBpmKnown = incomingTrack.bpmStatus == BpmStatus.RESOLVED && incomingTrack.bpm in 40..220
        val isBeatMatched = keyScore >= 0.8f && outgoingBpmKnown && incomingBpmKnown

        // Determine Fade Strategy & Duration based on Camelot Wheel compatibility
        val fadeDurationMs = when {
            isBeatMatched -> 2200L // Tighter ~2.2s beat-matched window
            keyScore >= 0.5f -> (djSettings.crossfadeDurationSec * 1000L).coerceAtLeast(15000L) // Standard ~20s
            else -> 28000L // Very gradual ~28s fade to mask key clash
        }

        val syncStatusText = when {
            isBeatMatched -> "Apple AutoMix Beat-Matched Blend (${currentTrack?.bpm}➔${incomingTrack.bpm} BPM)"
            keyScore >= 0.5f -> "Harmonic Standard Blend (${smoothnessInfo.title})"
            else -> "Gradual Masking Fade (Key Clash)"
        }

        _engineState.value = state.copy(
            isCrossfading = true,
            deckASpinning = true,
            deckBSpinning = true,
            statusMessage = "Automixing: $reason • $syncStatusText"
        )

        // 1. Prepare incoming deck player with beat boundary snapping
        val rawStartMs = (incomingTrack.introOffsetSec * 1000L).coerceAtLeast(0L)
        val dropStartMs = if (isBeatMatched && incomingTrack.beatTimesMs.isNotEmpty()) {
            incomingTrack.beatTimesMs.minByOrNull { Math.abs(it - rawStartMs) } ?: rawStartMs
        } else {
            rawStartMs
        }

        if (incomingDeck == ActiveDeck.DECK_B) {
            setupPlayerB(incomingTrack, dropStartMs)
        } else {
            setupPlayerA(incomingTrack, dropStartMs)
        }

        // Calculate initial tempo-stretch ratio for beat matching
        var initialSpeedRatio = 1.0f
        var shouldRampSpeed = false
        if (isBeatMatched && currentTrack != null) {
            val ratio = currentTrack.bpm.toFloat() / incomingTrack.bpm.toFloat()
            if (ratio in 0.80f..1.20f) { // Clamped safe ratio without audio distortion
                initialSpeedRatio = ratio
                shouldRampSpeed = true
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                        val incomingPlayer = if (incomingDeck == ActiveDeck.DECK_B) playerB else playerA
                        val params = incomingPlayer?.playbackParams ?: android.media.PlaybackParams()
                        incomingPlayer?.playbackParams = params.setSpeed(initialSpeedRatio)
                    }
                } catch (e: Exception) {
                    Log.w("SeamlessDjEngine", "Speed ratio adjustment failed", e)
                }
            }
        }

        // 2. Perform trigonometric equal-power crossfade with tempo-ramp
        val steps = 25
        val stepDelay = (fadeDurationMs / steps).coerceAtLeast(40L)

        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            val outgoingVol = kotlin.math.cos(progress * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)
            val incomingVol = kotlin.math.sin(progress * Math.PI.toFloat() / 2f).coerceIn(0f, 1f)

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

            // Gradually ramp incoming player speed back to 1.0x during crossfade
            if (shouldRampSpeed && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                try {
                    val currentRampedSpeed = initialSpeedRatio + (1.0f - initialSpeedRatio) * progress
                    val incomingPlayer = if (incomingDeck == ActiveDeck.DECK_B) playerB else playerA
                    val params = incomingPlayer?.playbackParams ?: android.media.PlaybackParams()
                    incomingPlayer?.playbackParams = params.setSpeed(currentRampedSpeed)
                } catch (e: Exception) {
                    // Ignore speed ramp errors
                }
            }

            delay(stepDelay)
        }

        // Reset playback speed to 1.0f on completion
        if (shouldRampSpeed && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val incomingPlayer = if (incomingDeck == ActiveDeck.DECK_B) playerB else playerA
                val params = incomingPlayer?.playbackParams ?: android.media.PlaybackParams()
                incomingPlayer?.playbackParams = params.setSpeed(1.0f)
            } catch (e: Exception) {}
        }

        // 3. Complete transition, stop outgoing player, advance queue


        // 3. Complete transition, stop outgoing player, advance queue
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
