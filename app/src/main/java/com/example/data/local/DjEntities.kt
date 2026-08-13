package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String,
    val coverUrl: String,
    val source: String,
    val genre: String,
    val avgBpm: Int
)

@Entity(tableName = "playlist_tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String,
    val durationMs: Long,
    val streamUrl: String,
    val bpm: Int,
    val musicalKey: String,
    val source: String,
    val sourceUrl: String,
    val introOffsetSec: Int,
    val segmentDurationSec: Int,
    val trackOrder: Int
)

@Entity(tableName = "guest_requests")
data class GuestRequestEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String,
    val streamUrl: String,
    val bpm: Int,
    val source: String,
    val requestedBy: String,
    val upvotes: Int,
    val timestamp: Long
)

@Entity(tableName = "dj_settings")
data class DjSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val segmentDurationSec: Int,
    val startOffsetSec: Int,
    val crossfadeDurationSec: Int,
    val autoBpmMatch: Boolean,
    val usePhaseVocoder: Boolean = false,
    val partyLightsEnabled: Boolean,
    val partyRoomCode: String,
    val isDarkMode: Boolean = true,
    val debugModeEnabled: Boolean = false
)

@Entity(tableName = "song_bpm_cache")
data class SongBpmEntity(
    @PrimaryKey val trackKey: String,
    val bpm: Int,
    val status: String,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "song_metadata_cache")
data class SongMetadataEntity(
    @PrimaryKey val trackKey: String,
    val bpm: Int,
    val bpmConfidence: Int = 80,
    val musicalKey: String,
    val camelotKey: String,
    val keyConfidence: Int = 80,
    val validatedByGemini: Boolean = false,
    val status: String,
    val analysisConfidence: String = "medium",
    val energyScore: Int = 50,
    val lufs: Float = -14.0f,
    val phraseBoundariesJson: String = "",
    val spectralFluxCsv: String = "",
    val lowEnergy: Float = 0.33f,
    val midEnergy: Float = 0.33f,
    val highEnergy: Float = 0.33f,
    val optimalDropOffsetSec: Int = 20,
    val optimalOutroOffsetSec: Int = 0,
    val perceptualLufs: Float = -14.0f,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "beat_cache")
data class BeatCacheEntity(
    @PrimaryKey val trackKey: String,
    val beatTimesCsv: String,
    val bpm: Int,
    val timestamp: Long = System.currentTimeMillis()
)
