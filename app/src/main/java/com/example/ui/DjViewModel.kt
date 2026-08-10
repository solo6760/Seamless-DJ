package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SeamlessDjEngine
import com.example.audio.DjEngineState
import com.example.data.importer.ArchiveAudioImporter
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.repository.DjRepository
import com.example.data.security.ApiKeyManager
import com.example.data.service.GeminiBpmService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DjViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = DjRepository(db)
    val audioEngine = SeamlessDjEngine(application)
    val apiKeyManager = ApiKeyManager(application)
    val geminiBpmService = GeminiBpmService()
    val beatDetectionEngine = com.example.audio.BeatDetectionEngine(application)
    val audioDspAnalyzer = com.example.audio.AudioDspAnalyzer(application)


    private val _apiKey = MutableStateFlow(apiKeyManager.getApiKey())
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    private val _isFirstLaunchCompleted = MutableStateFlow(true)
    val isFirstLaunchCompleted: StateFlow<Boolean> = _isFirstLaunchCompleted.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = repository.allPlaylists.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val guestRequests: StateFlow<List<GuestRequest>> = repository.guestRequests.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    val settings: StateFlow<DjSettings> = repository.settings.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DjSettings()
    )

    val engineState: StateFlow<DjEngineState> = audioEngine.engineState

    private val _selectedPlaylist = MutableStateFlow<Playlist?>(null)
    val selectedPlaylist: StateFlow<Playlist?> = _selectedPlaylist.asStateFlow()

    private val _pendingPlaylistForDialog = MutableStateFlow<Playlist?>(null)
    val pendingPlaylistForDialog: StateFlow<Playlist?> = _pendingPlaylistForDialog.asStateFlow()

    private val _isOptimizingQueue = MutableStateFlow(false)
    val isOptimizingQueue: StateFlow<Boolean> = _isOptimizingQueue.asStateFlow()

    private val _isQueueOptimized = MutableStateFlow(false)
    val isQueueOptimized: StateFlow<Boolean> = _isQueueOptimized.asStateFlow()

    private val _importUrlInput = MutableStateFlow("")
    val importUrlInput: StateFlow<String> = _importUrlInput.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val processedTrackBpmSet = mutableSetOf<String>()

    init {
        viewModelScope.launch {
            repository.seedSampleDataIfNeeded()
            // Observe settings changes and update engine
            repository.settings.collect { newSettings ->
                audioEngine.updateSettings(newSettings)
            }
        }

        // Observe queue changes to trigger background Gemini BPM grounding lookup
        viewModelScope.launch {
            audioEngine.engineState.collect { state ->
                checkAndResolveTrackBpms(state)
            }
        }
    }

    private fun checkAndResolveTrackBpms(state: DjEngineState) {
        val tracksToCheck = mutableListOf<Track>()
        state.currentTrack?.let { tracksToCheck.add(it) }
        state.nextTrack?.let { tracksToCheck.add(it) }
        tracksToCheck.addAll(state.queue.take(5))

        for (track in tracksToCheck) {
            if (!processedTrackBpmSet.contains(track.id)) {
                processedTrackBpmSet.add(track.id)
                viewModelScope.launch(Dispatchers.IO) {
                    audioEngine.updateTrackResolvedBpm(track.id, track.bpm, BpmStatus.FETCHING)
                    val resolved = repository.resolveTrackMetadata(
                        track = track,
                        audioDspAnalyzer = audioDspAnalyzer,
                        beatDetectionEngine = beatDetectionEngine,
                        apiKeyManager = apiKeyManager,
                        geminiBpmService = geminiBpmService
                    )
                    audioEngine.updateTrackResolvedMetadata(
                        trackId = track.id,
                        bpm = resolved.bpm,
                        status = resolved.bpmStatus,
                        musicalKey = resolved.musicalKey,
                        beatTimesMs = resolved.beatTimesMs
                    )
                }
            }
        }
    }


    fun saveGeminiApiKey(key: String, onComplete: (isValid: Boolean, errorMessage: String?) -> Unit) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) {
            apiKeyManager.clearApiKey()
            _apiKey.value = null
            onComplete(false, "API Key cannot be empty")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _userMessage.value = "Validating Gemini API Key..."
            val isValid = geminiBpmService.validateApiKey(trimmed)
            if (isValid) {
                apiKeyManager.setApiKey(trimmed)
                apiKeyManager.setFirstLaunchCompleted(true)
                _apiKey.value = trimmed
                _isFirstLaunchCompleted.value = true
                _userMessage.value = "Gemini API Key saved securely! BPM Auto-Lookup active."
                onComplete(true, null)
            } else {
                onComplete(false, "Invalid Gemini API Key or connection error. Please verify key.")
            }
        }
    }

    fun clearGeminiApiKey() {
        apiKeyManager.clearApiKey()
        _apiKey.value = null
        _userMessage.value = "Gemini API Key cleared."
    }

    fun skipFirstLaunchOnboarding() {
        apiKeyManager.setFirstLaunchCompleted(true)
        _isFirstLaunchCompleted.value = true
    }

    fun selectAndPlayPlaylist(playlist: Playlist) {
        openPlaylistDialog(playlist)
    }

    fun openPlaylistDialog(playlist: Playlist) {
        if (playlist.tracks.isNotEmpty()) {
            _pendingPlaylistForDialog.value = playlist
        }
    }

    fun dismissPlaylistDialog() {
        if (!_isOptimizingQueue.value) {
            _pendingPlaylistForDialog.value = null
        }
    }

    fun confirmAndStartPlaylist(playlist: Playlist, startTrackIndex: Int, optimizeOrder: Boolean) {
        _selectedPlaylist.value = playlist
        viewModelScope.launch {
            if (optimizeOrder && playlist.tracks.size >= 3) {
                _isOptimizingQueue.value = true
                val reordered = kotlinx.coroutines.withContext(Dispatchers.Default) {
                    com.example.util.SmartPlaylistOptimizer.optimizePlaylist(playlist.tracks, startTrackIndex)
                }
                audioEngine.setQueueAndPlay(reordered, 0)
                _isQueueOptimized.value = true
                _userMessage.value = "⚡ Playlist reordered for best transitions"
                _isOptimizingQueue.value = false
            } else {
                audioEngine.setQueueAndPlay(playlist.tracks, startTrackIndex)
                _isQueueOptimized.value = false
                _userMessage.value = "Playing playlist in original order."
            }
            _pendingPlaylistForDialog.value = null
        }
    }

    fun reshuffleCurrentQueue() {
        val currentQueue = audioEngine.engineState.value.queue
        if (currentQueue.isEmpty()) return

        viewModelScope.launch {
            _isOptimizingQueue.value = true
            val currentTrack = audioEngine.engineState.value.currentTrack
            val fullTracksToReorder = if (currentTrack != null) listOf(currentTrack) + currentQueue else currentQueue

            val reordered = kotlinx.coroutines.withContext(Dispatchers.Default) {
                com.example.util.SmartPlaylistOptimizer.optimizePlaylist(fullTracksToReorder, 0)
            }

            val remainingNewQueue = if (currentTrack != null) reordered.filter { it.id != currentTrack.id } else reordered
            audioEngine.updateQueue(remainingNewQueue)
            _isQueueOptimized.value = true
            _userMessage.value = "⚡ Queue re-optimized for best transitions!"
            _isOptimizingQueue.value = false
        }
    }

    fun togglePlayPause() {
        audioEngine.togglePlayPause()
    }

    fun skipTrack() {
        audioEngine.skipToNextTrack()
    }

    fun seekToPosition(seconds: Int) {
        audioEngine.seekToSegmentPosition(seconds)
    }

    fun seekByDelta(deltaSeconds: Int) {
        audioEngine.seekByDelta(deltaSeconds)
    }

    fun updateImportUrlInput(input: String) {
        _importUrlInput.value = input
    }

    fun toggleAutomix(enabled: Boolean) {
        audioEngine.toggleAutomix(enabled)
    }

    fun importTrackFromUrl() {
        val url = _importUrlInput.value
        if (url.isBlank()) return

        viewModelScope.launch {
            if (repository.isYouTubePlaylistUrl(url)) {
                val newPlaylist = repository.parseAndImportYouTubePlaylist(url)
                _selectedPlaylist.value = newPlaylist
                openPlaylistDialog(newPlaylist)
                _userMessage.value = "Imported YouTube Playlist: ${newPlaylist.name} (${newPlaylist.tracks.size} tracks)"
                _importUrlInput.value = ""
            } else {
                val parsedTrack = repository.parseAndImportUrl(url)
                if (parsedTrack != null) {
                    val currentPl = _selectedPlaylist.value ?: playlists.value.firstOrNull()
                    if (currentPl != null) {
                        val updatedTracks = currentPl.tracks + parsedTrack
                        val updatedPl = currentPl.copy(tracks = updatedTracks)
                        _selectedPlaylist.value = updatedPl
                        audioEngine.setQueueAndPlay(updatedTracks, 0)
                        _userMessage.value = "Imported track: ${parsedTrack.title}"
                    } else {
                        repository.createCustomPlaylist(
                            name = "Imported Party Stream",
                            description = "Custom track imported from web link",
                            sourceUrl = url,
                            tracks = listOf(parsedTrack)
                        )
                        _userMessage.value = "Created custom playlist with imported track!"
                    }
                    _importUrlInput.value = ""
                } else {
                    _userMessage.value = "Invalid URL. Please paste a YouTube (track or playlist), SoundCloud, or MP3 stream link."
                }
            }
        }
    }

    fun importLocalFolder(context: Context, folderUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            _userMessage.value = "Scanning folder and extracting MP3/FLAC/WAV audio..."
            val playlist = ArchiveAudioImporter.importFolder(context, folderUri)
            if (playlist.tracks.isNotEmpty()) {
                repository.saveImportedPlaylist(playlist)
                _selectedPlaylist.value = playlist
                openPlaylistDialog(playlist)
                _userMessage.value = "Imported Local Folder: ${playlist.name} (${playlist.tracks.size} tracks)"
            } else {
                _userMessage.value = "No supported audio files (MP3, FLAC, WAV, ZIP/TAR) found in selected folder."
            }
        }
    }

    fun importArchiveOrAudioFiles(context: Context, uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            _userMessage.value = "Extracting tracks from archive (ZIP/TAR/RAR) / audio files..."
            val playlist = ArchiveAudioImporter.importArchivesOrAudioFiles(context, uris)
            if (playlist.tracks.isNotEmpty()) {
                repository.saveImportedPlaylist(playlist)
                _selectedPlaylist.value = playlist
                openPlaylistDialog(playlist)
                _userMessage.value = "Imported Playlist: ${playlist.name} (${playlist.tracks.size} tracks)"
            } else {
                _userMessage.value = "No supported audio files (MP3, FLAC, WAV, ZIP) extracted from file(s)."
            }
        }
    }

    fun updateSettings(
        segmentDurationSec: Int,
        startOffsetSec: Int,
        crossfadeDurationSec: Int,
        autoBpmMatch: Boolean,
        usePhaseVocoder: Boolean = settings.value.usePhaseVocoder,
        partyLightsEnabled: Boolean,
        isDarkMode: Boolean = settings.value.isDarkMode
    ) {
        viewModelScope.launch {
            val current = settings.value
            val updated = current.copy(
                segmentDurationSec = segmentDurationSec,
                startOffsetSec = startOffsetSec,
                crossfadeDurationSec = crossfadeDurationSec,
                autoBpmMatch = autoBpmMatch,
                usePhaseVocoder = usePhaseVocoder,
                partyLightsEnabled = partyLightsEnabled,
                isDarkMode = isDarkMode
            )
            repository.saveSettings(updated)
            _userMessage.value = "DJ settings updated! Segments: ${segmentDurationSec}s | Drop: ${startOffsetSec}s | Fade: ${crossfadeDurationSec}s | Phase Vocoder: ${if (usePhaseVocoder) "On" else "Off"}"
        }
    }

    fun toggleDarkMode(isDark: Boolean) {
        viewModelScope.launch {
            val current = settings.value
            repository.saveSettings(current.copy(isDarkMode = isDark))
        }
    }

    fun addGuestRequest(title: String, artist: String, sourceUrl: String, requestedBy: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            val track = Track(
                id = "req_tr_${System.currentTimeMillis()}",
                title = title,
                artist = if (artist.isBlank()) "Guest Choice" else artist,
                albumArtUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop",
                durationMs = 210000L,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                bpm = 126,
                source = if (sourceUrl.contains("soundcloud")) TrackSource.SOUNDCLOUD else TrackSource.YOUTUBE,
                sourceUrl = sourceUrl
            )
            repository.addGuestRequest(track, if (requestedBy.isBlank()) "Party Guest" else requestedBy)
            _userMessage.value = "Song request added to queue!"
        }
    }

    fun upvoteGuestRequest(requestId: String) {
        viewModelScope.launch {
            repository.upvoteRequest(requestId)
        }
    }

    fun getPhaseVocoderMetrics(): com.example.audio.phase_vocoder.PhaseVocoderMetrics {
        return audioEngine.getPhaseVocoderMetrics()
    }

    fun runABComparisonTest() {
        audioEngine.runABComparisonTest { msg ->
            _userMessage.value = msg
        }
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.stopAll()
    }
}
