package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists")
    suspend fun getAllPlaylistsSync(): List<PlaylistEntity>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY trackOrder ASC")
    fun getTracksForPlaylist(playlistId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM playlist_tracks WHERE playlistId = :playlistId ORDER BY trackOrder ASC")
    suspend fun getTracksForPlaylistSync(playlistId: String): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylists(playlists: List<PlaylistEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<TrackEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrack(track: TrackEntity)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)
}

@Dao
interface GuestRequestDao {
    @Query("SELECT * FROM guest_requests ORDER BY upvotes DESC, timestamp ASC")
    fun getAllRequests(): Flow<List<GuestRequestEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: GuestRequestEntity)

    @Query("UPDATE guest_requests SET upvotes = upvotes + 1 WHERE id = :id")
    suspend fun upvoteRequest(id: String)

    @Query("DELETE FROM guest_requests WHERE id = :id")
    suspend fun deleteRequest(id: String)

    @Query("DELETE FROM guest_requests")
    suspend fun clearRequests()
}

@Dao
interface SettingsDao {
    @Query("SELECT * FROM dj_settings WHERE id = 1")
    fun getSettings(): Flow<DjSettingsEntity?>

    @Query("SELECT * FROM dj_settings WHERE id = 1")
    suspend fun getSettingsSync(): DjSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSettings(settings: DjSettingsEntity)
}

@Dao
interface SongBpmDao {
    @Query("SELECT * FROM song_bpm_cache WHERE trackKey = :trackKey LIMIT 1")
    suspend fun getBpm(trackKey: String): SongBpmEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBpm(entity: SongBpmEntity)
}

@Dao
interface SongMetadataDao {
    @Query("SELECT * FROM song_metadata_cache WHERE trackKey = :trackKey LIMIT 1")
    suspend fun getMetadata(trackKey: String): SongMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMetadata(entity: SongMetadataEntity)
}

@Dao
interface BeatCacheDao {
    @Query("SELECT * FROM beat_cache WHERE trackKey = :trackKey LIMIT 1")
    suspend fun getBeatCache(trackKey: String): BeatCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeatCache(entity: BeatCacheEntity)
}

