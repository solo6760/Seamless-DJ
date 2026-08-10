package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.SeamlessDjEngine
import com.example.audio.DjEngineState
import com.example.data.local.AppDatabase
import com.example.data.model.*
import com.example.data.repository.DjRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DjViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getDatabase(application)
    val repository = DjRepository(db)
    val audioEngine = SeamlessDjEngine(application)

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

    private val _importUrlInput = MutableStateFlow("")
    val importUrlInput: StateFlow<String> = _importUrlInput.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    init {
        viewModelScope.launch {
            repository.seedSampleDataIfNeeded()
            // Observe settings changes and update engine
            repository.settings.collect { newSettings ->
                audioEngine.updateSettings(newSettings)
            }
        }
    }

    fun selectAndPlayPlaylist(playlist: Playlist) {
        _selectedPlaylist.value = playlist
        if (playlist.tracks.isNotEmpty()) {
            audioEngine.setQueueAndPlay(playlist.tracks, 0)
        }
    }

    fun togglePlayPause() {
        audioEngine.togglePlayPause()
    }

    fun skipTrack() {
        audioEngine.skipToNextTrack()
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
                audioEngine.setQueueAndPlay(newPlaylist.tracks, 0)
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

    fun updateSettings(
        segmentDurationSec: Int,
        startOffsetSec: Int,
        crossfadeDurationSec: Int,
        autoBpmMatch: Boolean,
        partyLightsEnabled: Boolean
    ) {
        viewModelScope.launch {
            val current = settings.value
            val updated = current.copy(
                segmentDurationSec = segmentDurationSec,
                startOffsetSec = startOffsetSec,
                crossfadeDurationSec = crossfadeDurationSec,
                autoBpmMatch = autoBpmMatch,
                partyLightsEnabled = partyLightsEnabled
            )
            repository.saveSettings(updated)
            _userMessage.value = "DJ settings updated! Segments: ${segmentDurationSec}s | Drop: ${startOffsetSec}s | Fade: ${crossfadeDurationSec}s"
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

    fun clearUserMessage() {
        _userMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        audioEngine.stopAll()
    }
}
