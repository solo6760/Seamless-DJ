package com.example.data.model

enum class PlaylistSource {
    YOUTUBE,
    SOUNDCLOUD,
    CURATED,
    CUSTOM
}

data class Playlist(
    val id: String,
    val name: String,
    val description: String,
    val coverUrl: String,
    val source: PlaylistSource = PlaylistSource.CURATED,
    val genre: String = "House / Dance",
    val avgBpm: Int = 126,
    val tracks: List<Track> = emptyList()
)
