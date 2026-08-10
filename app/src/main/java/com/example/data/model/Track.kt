package com.example.data.model

enum class TrackSource {
    YOUTUBE,
    SOUNDCLOUD,
    CURATED
}

data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val albumArtUrl: String,
    val durationMs: Long,
    val streamUrl: String,
    val bpm: Int = 124,
    val musicalKey: String = "8A / Fm",
    val source: TrackSource = TrackSource.YOUTUBE,
    val sourceUrl: String = "",
    val introOffsetSec: Int = 20,
    val segmentDurationSec: Int = 90
) {
    val formattedDuration: String
        get() {
            val totalSec = durationMs / 1000
            val minutes = totalSec / 60
            val seconds = totalSec % 60
            return String.format("%d:%02d", minutes, seconds)
        }
}
