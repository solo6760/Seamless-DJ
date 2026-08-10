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
    val partyLightsEnabled: Boolean,
    val partyRoomCode: String
)
