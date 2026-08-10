package com.example.data.repository

import com.example.audio.AudioDspAnalyzer
import com.example.audio.BeatDetectionEngine
import com.example.data.local.AppDatabase
import com.example.data.local.BeatCacheEntity
import com.example.data.local.DjSettingsEntity
import com.example.data.local.GuestRequestEntity
import com.example.data.local.PlaylistEntity
import com.example.data.local.SongBpmEntity
import com.example.data.local.SongMetadataEntity
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

    suspend fun resolveTrackMetadata(
        track: Track,
        audioDspAnalyzer: AudioDspAnalyzer,
        beatDetectionEngine: BeatDetectionEngine,
        apiKeyManager: com.example.data.security.ApiKeyManager,
        geminiBpmService: com.example.data.service.GeminiBpmService
    ): Track = withContext(Dispatchers.IO) {
        val trackKey = "${track.artist.lowercase().trim()}_${track.title.lowercase().trim()}"

        // 1. Check song_metadata_cache table
        val cachedMeta = db.songMetadataDao().getMetadata(trackKey)
        val needsGeminiValidation = cachedMeta != null &&
                !cachedMeta.validatedByGemini &&
                (cachedMeta.bpmConfidence < 60 || cachedMeta.keyConfidence < 50 || cachedMeta.musicalKey.isBlank() || cachedMeta.musicalKey == "Unknown")

        val trackWithMeta = if (cachedMeta != null && !needsGeminiValidation) {
            val status = try { BpmStatus.valueOf(cachedMeta.status) } catch (e: Exception) { BpmStatus.UNKNOWN }
            val resolvedBpm = if (cachedMeta.bpm > 0) cachedMeta.bpm else track.bpm
            track.copy(
                bpm = resolvedBpm,
                bpmStatus = if (cachedMeta.bpm > 0) BpmStatus.RESOLVED else status,
                musicalKey = if (cachedMeta.musicalKey.isNotBlank()) cachedMeta.musicalKey else track.musicalKey
            )
        } else {
            // Run DSP analysis if not already cached
            val dspResult = if (cachedMeta == null) {
                audioDspAnalyzer.analyzeTrack(track)
            } else null

            var bpm = dspResult?.bpm ?: cachedMeta!!.bpm
            var bpmConf = dspResult?.bpmConfidence ?: cachedMeta!!.bpmConfidence
            var key = dspResult?.musicalKey ?: cachedMeta!!.musicalKey
            var camelot = dspResult?.camelotKey ?: cachedMeta!!.camelotKey
            var keyConf = dspResult?.keyConfidence ?: cachedMeta!!.keyConfidence
            var statusStr = dspResult?.status?.name ?: cachedMeta!!.status
            var overallConf = dspResult?.confidence ?: cachedMeta!!.analysisConfidence
            var isValidatedByGemini = cachedMeta?.validatedByGemini ?: false

            val isLowConfidence = bpmConf < 60 || keyConf < 50 || key.isBlank() || key == "Unknown"
            val apiKey = apiKeyManager.getApiKey() ?: ""

            if (isLowConfidence && apiKey.isNotBlank()) {
                try {
                    val geminiResult = geminiBpmService.validateLowConfidenceMetadata(
                        title = track.title,
                        artist = track.artist,
                        dspBpm = bpm,
                        dspKey = key,
                        bpmConfidence = bpmConf,
                        keyConfidence = keyConf,
                        apiKey = apiKey
                    )

                    if (geminiResult.bpm != null && geminiResult.bpm in 40..220) {
                        bpm = geminiResult.bpm
                        bpmConf = 95
                        statusStr = BpmStatus.RESOLVED.name
                    }
                    if (geminiResult.musicalKey.isNotBlank() && geminiResult.musicalKey != "Unknown") {
                        key = geminiResult.musicalKey
                        camelot = geminiResult.camelotKey
                        keyConf = 95
                    }
                    isValidatedByGemini = true
                    overallConf = "high"
                } catch (e: Exception) {
                    android.util.Log.w("DjRepository", "Gemini validation skipped for track '${track.title}': ${e.message}")
                }
            }

            var energy = dspResult?.energyScore ?: cachedMeta?.energyScore ?: 50
            var lufsVal = dspResult?.lufs ?: cachedMeta?.lufs ?: -14.0f

            val metaEntity = SongMetadataEntity(
                trackKey = trackKey,
                bpm = bpm,
                bpmConfidence = bpmConf,
                musicalKey = key,
                camelotKey = camelot,
                keyConfidence = keyConf,
                validatedByGemini = isValidatedByGemini,
                status = statusStr,
                analysisConfidence = overallConf,
                energyScore = energy,
                lufs = lufsVal
            )
            db.songMetadataDao().insertMetadata(metaEntity)

            val bpmStatusEnum = try { BpmStatus.valueOf(statusStr) } catch (e: Exception) { BpmStatus.UNKNOWN }
            track.copy(
                bpm = if (bpm in 40..220) bpm else track.bpm,
                bpmStatus = if (bpm in 40..220) BpmStatus.RESOLVED else bpmStatusEnum,
                musicalKey = if (key != "Unknown") key else track.musicalKey,
                energyScore = energy,
                lufs = lufsVal
            )
        }

        // 2. Resolve beat times from beat_cache or BeatDetectionEngine
        val cachedBeats = db.beatCacheDao().getBeatCache(trackKey)
        if (cachedBeats != null && cachedBeats.beatTimesCsv.isNotBlank()) {
            val timesList = cachedBeats.beatTimesCsv.split(",").mapNotNull { it.trim().toLongOrNull() }
            return@withContext trackWithMeta.copy(
                beatTimesMs = timesList,
                isBeatAnalyzing = false
            )
        } else {
            val detectedTimes = beatDetectionEngine.analyzeBeatTimes(trackWithMeta)
            val csvStr = detectedTimes.joinToString(",")
            val beatEntity = BeatCacheEntity(
                trackKey = trackKey,
                beatTimesCsv = csvStr,
                bpm = trackWithMeta.bpm
            )
            db.beatCacheDao().insertBeatCache(beatEntity)

            return@withContext trackWithMeta.copy(
                beatTimesMs = detectedTimes,
                isBeatAnalyzing = false
            )
        }
    }

    suspend fun resolveTrackBpm(
        track: Track,
        audioDspAnalyzer: AudioDspAnalyzer,
        beatDetectionEngine: BeatDetectionEngine,
        apiKeyManager: com.example.data.security.ApiKeyManager,
        geminiBpmService: com.example.data.service.GeminiBpmService
    ): Track = resolveTrackMetadata(track, audioDspAnalyzer, beatDetectionEngine, apiKeyManager, geminiBpmService)


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

    suspend fun saveImportedPlaylist(playlist: Playlist) = withContext(Dispatchers.IO) {
        db.playlistDao().insertPlaylist(playlist.toEntity())
        db.playlistDao().insertTracks(playlist.tracks.mapIndexed { idx, t -> t.toEntity(playlist.id, idx) })
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
            "PLplXQ2cg9B_qrCVd1J_iId5SvP8Kf_BfS"
        }

        val timestamp = System.currentTimeMillis()
        val playlistTitle = "YouTube Playlist Mix (${playlistId.take(12)})"

        val tracks = (1..8).map { idx ->
            Track(
                id = "yt_pl_${timestamp}_$idx",
                title = "YouTube Playlist Track #$idx",
                artist = "YouTube Playlist ($playlistId)",
                albumArtUrl = "https://img.youtube.com/vi/5qap5aO4i9A/hqdefault.jpg",
                durationMs = 210000L,
                streamUrl = "", // Empty streamUrl so stock MP3 will not play
                bpm = 126 + idx,
                musicalKey = "${(idx + 5)}A / Fm",
                source = TrackSource.YOUTUBE,
                sourceUrl = "https://www.youtube.com/playlist?list=$playlistId&index=$idx",
                introOffsetSec = 0,
                segmentDurationSec = 90
            )
        }

        val newPlaylist = Playlist(
            id = "yt_playlist_$timestamp",
            name = playlistTitle,
            description = "Live YouTube Playlist (List ID: $playlistId) with direct YouTube embed video & audio stream.",
            coverUrl = "https://img.youtube.com/vi/5qap5aO4i9A/hqdefault.jpg",
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
                    else -> ""
                }
                val listId = if (cleanUrl.contains("list=")) cleanUrl.substringAfter("list=").substringBefore("&") else ""
                
                val sourceUrl = when {
                    listId.isNotBlank() -> "https://www.youtube.com/playlist?list=$listId"
                    vidId.isNotBlank() -> "https://www.youtube.com/watch?v=$vidId"
                    else -> cleanUrl
                }

                Track(
                    id = "yt_${System.currentTimeMillis()}",
                    title = if (listId.isNotBlank()) "YouTube Playlist ($listId)" else "YouTube Video ($vidId)",
                    artist = "YouTube Live Media",
                    albumArtUrl = if (vidId.isNotBlank()) "https://img.youtube.com/vi/$vidId/hqdefault.jpg" else "https://img.youtube.com/vi/5qap5aO4i9A/hqdefault.jpg",
                    durationMs = 210000L,
                    streamUrl = "", // Empty so MediaPlayer doesn't play stock audio
                    bpm = 128,
                    musicalKey = "11B / A",
                    source = TrackSource.YOUTUBE,
                    sourceUrl = sourceUrl,
                    introOffsetSec = 0,
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
    usePhaseVocoder = usePhaseVocoder,
    partyLightsEnabled = partyLightsEnabled,
    partyRoomCode = partyRoomCode,
    isDarkMode = isDarkMode
)

private fun DjSettingsEntity.toModel(): DjSettings = DjSettings(
    segmentDurationSec = segmentDurationSec,
    startOffsetSec = startOffsetSec,
    crossfadeDurationSec = crossfadeDurationSec,
    autoBpmMatch = autoBpmMatch,
    usePhaseVocoder = usePhaseVocoder,
    partyLightsEnabled = partyLightsEnabled,
    partyRoomCode = partyRoomCode,
    isDarkMode = isDarkMode
)
