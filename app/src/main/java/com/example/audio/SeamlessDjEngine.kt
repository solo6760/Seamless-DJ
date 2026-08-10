package com.example.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import com.example.data.model.DjSettings
import com.example.data.model.Track
import com.example.data.model.TrackSource
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max
import kotlin.math.min

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

    fun updateSettings(settings: DjSettings) {
        this.djSettings = settings
        _engineState.value = _engineState.value.copy(
            segmentTotalSec = settings.segmentDurationSec
        )
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

    private suspend fun triggerSeamlessCrossfade(reason: String) {
        val state = _engineState.value
        val incomingTrack = state.nextTrack ?: state.queue.firstOrNull() ?: return
        val currentActive = state.activeDeck
        val incomingDeck = if (currentActive == ActiveDeck.DECK_A) ActiveDeck.DECK_B else ActiveDeck.DECK_A

        _engineState.value = state.copy(
            isCrossfading = true,
            deckASpinning = true,
            deckBSpinning = true,
            statusMessage = "Automixing: $reason"
        )

        // 1. Prepare incoming deck player at intro start offset (~20s mark)
        val dropStartMs = (incomingTrack.introOffsetSec * 1000L).coerceAtLeast(0L)
        if (incomingDeck == ActiveDeck.DECK_B) {
            setupPlayerB(incomingTrack, dropStartMs)
        } else {
            setupPlayerA(incomingTrack, dropStartMs)
        }

        // 2. Perform smooth trigonometric equal-power volume crossfade (Constant acoustic output energy)
        val fadeDurationMs = (djSettings.crossfadeDurationSec * 1000L).coerceAtLeast(2000L)
        val steps = 25
        val stepDelay = fadeDurationMs / steps

        for (i in 0..steps) {
            val progress = i.toFloat() / steps
            // Equal Power trigonometric curve (Apple Music / Spotify Automix standard)
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
            delay(stepDelay)
        }

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
            playerA = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                setDataSource(track.streamUrl)
                val initialVol = if (track.isYouTube()) 0.0f else 1.0f
                setVolume(initialVol, initialVol)
                prepareAsync()
                setOnPreparedListener { mp ->
                    mp.seekTo(track.introOffsetSec * 1000)
                    mp.start()
                }
                setOnCompletionListener {
                    scope.launch { skipToNextTrack() }
                }
            }
        } catch (e: Exception) {
            Log.e("SeamlessDjEngine", "Failed to start Deck A", e)
        }
    }

    private fun setupPlayerA(track: Track, startMs: Long) {
        try {
            playerA?.release()
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
                    mp.seekTo(startMs.toInt())
                    mp.start()
                }
            }
        } catch (e: Exception) {
            Log.e("SeamlessDjEngine", "Failed setup Player A", e)
        }
    }

    private fun setupPlayerB(track: Track, startMs: Long) {
        try {
            playerB?.release()
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
                    mp.seekTo(startMs.toInt())
                    mp.start()
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
