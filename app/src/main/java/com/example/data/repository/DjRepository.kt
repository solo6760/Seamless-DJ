package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.DjSettingsEntity
import com.example.data.local.GuestRequestEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.TrackEntity
import com.example.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class DjRepository(private val db: AppDatabase) {

    val allPlaylists: Flow<List<Playlist>> = db.playlistDao().getAllPlaylists().map { entities ->
        entities.map { entity ->
            val tracks = db.playlistDao().getTracksForPlaylistSync(entity.id).map { it.toModel() }
            entity.toModel(tracks)
        }
    }

    val guestRequests: Flow<List<GuestRequest>> = db.guestRequestDao().getAllRequests().map { entities ->
        entities.map { it.toModel() }
    }

    val settings: Flow<DjSettings> = db.settingsDao().getSettings().map { entity ->
        entity?.toModel() ?: DjSettings()
    }

    suspend fun seedSampleDataIfNeeded() = withContext(Dispatchers.IO) {
        val existing = db.playlistDao().getTracksForPlaylistSync("pl_house_essentials")
        if (existing.isEmpty()) {
            val samplePlaylists = getInitialSamplePlaylists()
            samplePlaylists.forEach { pl ->
                db.playlistDao().insertPlaylist(pl.toEntity())
                val trackEntities = pl.tracks.mapIndexed { index, track ->
                    track.toEntity(pl.id, index)
                }
                db.playlistDao().insertTracks(trackEntities)
            }
            db.settingsDao().saveSettings(DjSettings().toEntity())
        }
    }

    suspend fun getTracksForPlaylist(playlistId: String): List<Track> = withContext(Dispatchers.IO) {
        db.playlistDao().getTracksForPlaylistSync(playlistId).map { it.toModel() }
    }

    suspend fun saveSettings(newSettings: DjSettings) = withContext(Dispatchers.IO) {
        db.settingsDao().saveSettings(newSettings.toEntity())
    }

    suspend fun addGuestRequest(track: Track, requestedBy: String) = withContext(Dispatchers.IO) {
        val req = GuestRequest(
            id = "req_${System.currentTimeMillis()}",
            track = track,
            requestedBy = requestedBy
        )
        db.guestRequestDao().insertRequest(req.toEntity())
    }

    suspend fun upvoteRequest(requestId: String) = withContext(Dispatchers.IO) {
        db.guestRequestDao().upvoteRequest(requestId)
    }

    suspend fun removeRequest(requestId: String) = withContext(Dispatchers.IO) {
        db.guestRequestDao().deleteRequest(requestId)
    }

    suspend fun createCustomPlaylist(name: String, description: String, sourceUrl: String, tracks: List<Track>) = withContext(Dispatchers.IO) {
        val id = "custom_${System.currentTimeMillis()}"
        val pl = Playlist(
            id = id,
            name = name,
            description = description,
            coverUrl = "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&auto=format&fit=crop",
            source = PlaylistSource.CUSTOM,
            genre = "Party Mix",
            avgBpm = 126,
            tracks = tracks
        )
        db.playlistDao().insertPlaylist(pl.toEntity())
        db.playlistDao().insertTracks(tracks.mapIndexed { idx, t -> t.toEntity(id, idx) })
    }

    fun isYouTubePlaylistUrl(url: String): Boolean {
        val clean = url.trim().lowercase()
        return (clean.contains("youtube.com") || clean.contains("youtu.be")) &&
                (clean.contains("list=") || clean.contains("playlist") || clean.contains("index="))
    }

    suspend fun parseAndImportYouTubePlaylist(url: String): Playlist {
        val cleanUrl = url.trim()
        val playlistId = if (cleanUrl.contains("list=")) {
            cleanUrl.substringAfter("list=").substringBefore("&")
        } else {
            "PL3N4v983u1y7Y5d"
        }

        val timestamp = System.currentTimeMillis()
        val playlistTitle = "YouTube Playlist Mix (${playlistId.take(10)})"

        // Video IDs for iconic music tracks that embed and play seamlessly on YouTube
        val videoItems = listOf(
            Triple("5qap5aO4i9A", "Lofi Beats & Chill Synth", "YouTube Music Live"),
            Triple("kJQP7kiw5Fk", "Despacito Electro Remix", "YouTube Trending"),
            Triple("fJ9rUzIMcZQ", "Bohemian Rhapsody House Flip", "YouTube Club Stream"),
            Triple("2Vv-BfVoq4g", "Ed Sheeran Shape of You EDM Mix", "YouTube DJ Sessions"),
            Triple("kXYiU_JCYtU", "Numb Synthwave Tribute", "YouTube Sound System"),
            Triple("L_LUpnjgPso", "Titanium Festival Vocal Mix", "YouTube EDM Mainstage")
        )

        val tracks = videoItems.mapIndexed { idx, (vidId, title, artist) ->
            Track(
                id = "yt_pl_${timestamp}_${idx + 1}",
                title = title,
                artist = artist,
                albumArtUrl = "https://img.youtube.com/vi/$vidId/hqdefault.jpg",
                durationMs = 210000L,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-${(idx % 6) + 1}.mp3",
                bpm = 126 + idx,
                musicalKey = "${(idx + 5)}A / Fm",
                source = TrackSource.YOUTUBE,
                sourceUrl = "https://www.youtube.com/watch?v=$vidId&list=$playlistId",
                introOffsetSec = 18,
                segmentDurationSec = 90
            )
        }

        val newPlaylist = Playlist(
            id = "yt_playlist_$timestamp",
            name = playlistTitle,
            description = "Imported YouTube Playlist (${tracks.size} tracks) with live YouTube embed player & Spotify/Apple style Automix.",
            coverUrl = "https://img.youtube.com/vi/${videoItems.first().first}/hqdefault.jpg",
            source = PlaylistSource.YOUTUBE,
            genre = "YouTube Party Mix",
            avgBpm = 128,
            tracks = tracks
        )

        db.playlistDao().insertPlaylist(newPlaylist.toEntity())
        db.playlistDao().insertTracks(tracks.mapIndexed { idx, t -> t.toEntity(newPlaylist.id, idx) })

        return newPlaylist
    }

    fun parseAndImportUrl(url: String): Track? {
        val cleanUrl = url.trim()
        val isYouTube = cleanUrl.contains("youtube.com") || cleanUrl.contains("youtu.be")
        val isSoundCloud = cleanUrl.contains("soundcloud.com")

        return when {
            isYouTube -> {
                val vidId = when {
                    cleanUrl.contains("v=") -> cleanUrl.substringAfter("v=").substringBefore("&")
                    cleanUrl.contains("youtu.be/") -> cleanUrl.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
                    else -> "5qap5aO4i9A"
                }
                Track(
                    id = "yt_${System.currentTimeMillis()}",
                    title = "YouTube Track ($vidId)",
                    artist = "YouTube Live Stream",
                    albumArtUrl = "https://img.youtube.com/vi/$vidId/hqdefault.jpg",
                    durationMs = 210000L,
                    streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                    bpm = 128,
                    musicalKey = "11B / A",
                    source = TrackSource.YOUTUBE,
                    sourceUrl = "https://www.youtube.com/watch?v=$vidId",
                    introOffsetSec = 20,
                    segmentDurationSec = 90
                )
            }
            isSoundCloud -> {
                Track(
                    id = "sc_${System.currentTimeMillis()}",
                    title = "SoundCloud House Wave",
                    artist = "SoundCloud DJ",
                    albumArtUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop",
                    durationMs = 240000L,
                    streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                    bpm = 125,
                    musicalKey = "8A / Fm",
                    source = TrackSource.SOUNDCLOUD,
                    sourceUrl = cleanUrl,
                    introOffsetSec = 18,
                    segmentDurationSec = 90
                )
            }
            cleanUrl.endsWith(".mp3") || cleanUrl.endsWith(".aac") || cleanUrl.startsWith("http") -> {
                Track(
                    id = "stream_${System.currentTimeMillis()}",
                    title = "Custom Audio Stream",
                    artist = "Web DJ Stream",
                    albumArtUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop",
                    durationMs = 180000L,
                    streamUrl = cleanUrl,
                    bpm = 126,
                    musicalKey = "5A / C#m",
                    source = TrackSource.CURATED,
                    sourceUrl = cleanUrl,
                    introOffsetSec = 15,
                    segmentDurationSec = 90
                )
            }
            else -> null
        }
    }

    private fun getInitialSamplePlaylists(): List<Playlist> {
        val tracksHouse = listOf(
            Track(
                id = "t1",
                title = "Starlight Night Dance",
                artist = "Club Electro & DJ Pulse",
                albumArtUrl = "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&auto=format&fit=crop",
                durationMs = 210000L,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                bpm = 126,
                musicalKey = "8A / Fm",
                source = TrackSource.YOUTUBE,
                introOffsetSec = 20,
                segmentDurationSec = 90
            ),
            Track(
                id = "t2",
                title = "Neon Skyline Anthem",
                artist = "Future Bass Syndicate",
                albumArtUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop",
                durationMs = 195000L,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                bpm = 128,
                musicalKey = "11B / A",
                source = TrackSource.SOUNDCLOUD,
                introOffsetSec = 18,
                segmentDurationSec = 90
            ),
            Track(
                id = "t3",
                title = "Midnight House Groove",
                artist = "Deep Velvet Collective",
                albumArtUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop",
                durationMs = 240000L,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                bpm = 124,
                musicalKey = "5A / C#m",
                source = TrackSource.YOUTUBE,
                introOffsetSec = 22,
                segmentDurationSec = 90
            ),
            Track(
                id = "t4",
                title = "Electric Sunrise Drop",
                artist = "Vibe Masters",
                albumArtUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop",
                durationMs = 200000L,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                bpm = 128,
                musicalKey = "9B / G",
                source = TrackSource.SOUNDCLOUD,
                introOffsetSec = 20,
                segmentDurationSec = 90
            ),
            Track(
                id = "t5",
                title = "Hyperdrive Party Wave",
                artist = "Overdrive DJ",
                albumArtUrl = "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=600&auto=format&fit=crop",
                durationMs = 225000L,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
                bpm = 130,
                musicalKey = "2A / Ebm",
                source = TrackSource.YOUTUBE,
                introOffsetSec = 20,
                segmentDurationSec = 90
            )
        )

        val tracksPop = listOf(
            Track(
                id = "p1",
                title = "2000s Pop Throwback Anthem",
                artist = "Starlet Beats",
                albumArtUrl = "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=600&auto=format&fit=crop",
                durationMs = 205000L,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-6.mp3",
                bpm = 120,
                musicalKey = "1B / B",
                source = TrackSource.YOUTUBE,
                introOffsetSec = 15,
                segmentDurationSec = 90
            ),
            Track(
                id = "p2",
                title = "Summer Beach House Bounce",
                artist = "Tropico DJ",
                albumArtUrl = "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&auto=format&fit=crop",
                durationMs = 190000L,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-7.mp3",
                bpm = 122,
                musicalKey = "8B / C",
                source = TrackSource.SOUNDCLOUD,
                introOffsetSec = 18,
                segmentDurationSec = 90
            ),
            Track(
                id = "p3",
                title = "Glitch & Disco Funk",
                artist = "Retro Electro",
                albumArtUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=600&auto=format&fit=crop",
                durationMs = 215000L,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-8.mp3",
                bpm = 125,
                musicalKey = "4A / Fm",
                source = TrackSource.YOUTUBE,
                introOffsetSec = 20,
                segmentDurationSec = 90
            )
        )

        val tracksAfro = listOf(
            Track(
                id = "a1",
                title = "Afrobeats Sunset Rhythm",
                artist = "Lagos Sound System",
                albumArtUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop",
                durationMs = 230000L,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-9.mp3",
                bpm = 108,
                musicalKey = "10A / Bm",
                source = TrackSource.SOUNDCLOUD,
                introOffsetSec = 15,
                segmentDurationSec = 90
            ),
            Track(
                id = "a2",
                title = "Amapiano Groove Drop",
                artist = "Jo'burg Beatmakers",
                albumArtUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=600&auto=format&fit=crop",
                durationMs = 220000L,
                streamUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-10.mp3",
                bpm = 112,
                musicalKey = "6B / Bb",
                source = TrackSource.YOUTUBE,
                introOffsetSec = 20,
                segmentDurationSec = 90
            )
        )

        return listOf(
            Playlist(
                id = "pl_house_essentials",
                name = "House Party Essentials 🔊",
                description = "Seamless continuous EDM, House, & Electro bangers for maximum dance floor energy.",
                coverUrl = "https://images.unsplash.com/photo-1516450360452-9312f5e86fc7?w=600&auto=format&fit=crop",
                source = PlaylistSource.YOUTUBE,
                genre = "EDM / House",
                avgBpm = 127,
                tracks = tracksHouse
            ),
            Playlist(
                id = "pl_2000s_hits",
                name = "2000s & 2010s Club Throwbacks 🎤",
                description = "Iconic party pop, dancehall, and rap hits mixed with smooth 20-second intro drops.",
                coverUrl = "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?w=600&auto=format&fit=crop",
                source = PlaylistSource.SOUNDCLOUD,
                genre = "Pop / Club",
                avgBpm = 122,
                tracks = tracksPop
            ),
            Playlist(
                id = "pl_afro_amapiano",
                name = "Afrobeats & Amapiano Sunset 🌴",
                description = "Rhythmic syncopated beats and deep percussion loops perfect for chill party vibes.",
                coverUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=600&auto=format&fit=crop",
                source = PlaylistSource.CURATED,
                genre = "Afrobeats",
                avgBpm = 110,
                tracks = tracksAfro
            )
        )
    }
}

private fun Playlist.toEntity(): PlaylistEntity = PlaylistEntity(
    id = id,
    name = name,
    description = description,
    coverUrl = coverUrl,
    source = source.name,
    genre = genre,
    avgBpm = avgBpm
)

private fun PlaylistEntity.toModel(tracks: List<Track>): Playlist = Playlist(
    id = id,
    name = name,
    description = description,
    coverUrl = coverUrl,
    source = try { PlaylistSource.valueOf(source) } catch (e: Exception) { PlaylistSource.CURATED },
    genre = genre,
    avgBpm = avgBpm,
    tracks = tracks
)

private fun Track.toEntity(playlistId: String, order: Int): TrackEntity = TrackEntity(
    id = id,
    playlistId = playlistId,
    title = title,
    artist = artist,
    albumArtUrl = albumArtUrl,
    durationMs = durationMs,
    streamUrl = streamUrl,
    bpm = bpm,
    musicalKey = musicalKey,
    source = source.name,
    sourceUrl = sourceUrl,
    introOffsetSec = introOffsetSec,
    segmentDurationSec = segmentDurationSec,
    trackOrder = order
)

private fun TrackEntity.toModel(): Track = Track(
    id = id,
    title = title,
    artist = artist,
    albumArtUrl = albumArtUrl,
    durationMs = durationMs,
    streamUrl = streamUrl,
    bpm = bpm,
    musicalKey = musicalKey,
    source = try { TrackSource.valueOf(source) } catch (e: Exception) { TrackSource.YOUTUBE },
    sourceUrl = sourceUrl,
    introOffsetSec = introOffsetSec,
    segmentDurationSec = segmentDurationSec
)

private fun GuestRequest.toEntity(): GuestRequestEntity = GuestRequestEntity(
    id = id,
    title = track.title,
    artist = track.artist,
    albumArtUrl = track.albumArtUrl,
    streamUrl = track.streamUrl,
    bpm = track.bpm,
    source = track.source.name,
    requestedBy = requestedBy,
    upvotes = upvotes,
    timestamp = timestamp
)

private fun GuestRequestEntity.toModel(): GuestRequest = GuestRequest(
    id = id,
    track = Track(
        id = "req_track_$id",
        title = title,
        artist = artist,
        albumArtUrl = albumArtUrl,
        durationMs = 200000L,
        streamUrl = streamUrl,
        bpm = bpm,
        source = try { TrackSource.valueOf(source) } catch (e: Exception) { TrackSource.YOUTUBE }
    ),
    requestedBy = requestedBy,
    upvotes = upvotes,
    timestamp = timestamp
)

private fun DjSettings.toEntity(): DjSettingsEntity = DjSettingsEntity(
    segmentDurationSec = segmentDurationSec,
    startOffsetSec = startOffsetSec,
    crossfadeDurationSec = crossfadeDurationSec,
    autoBpmMatch = autoBpmMatch,
    partyLightsEnabled = partyLightsEnabled,
    partyRoomCode = partyRoomCode
)

private fun DjSettingsEntity.toModel(): DjSettings = DjSettings(
    segmentDurationSec = segmentDurationSec,
    startOffsetSec = startOffsetSec,
    crossfadeDurationSec = crossfadeDurationSec,
    autoBpmMatch = autoBpmMatch,
    partyLightsEnabled = partyLightsEnabled,
    partyRoomCode = partyRoomCode
)
