package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Playlist
import com.example.data.model.PlaylistSource
import com.example.data.model.TrackSource
import com.example.ui.DjViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicSourcesScreen(
    viewModel: DjViewModel,
    onBackToDeck: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val playlists by viewModel.playlists.collectAsState()
    val importInput by viewModel.importUrlInput.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Curated Playlists, 1: Import Link, 2: Import Folder / Archive

    // Folder Launcher
    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        uri?.let {
            viewModel.importLocalFolder(context, it)
            onBackToDeck()
        }
    }

    // Archive / Audio Files Launcher
    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.importArchiveOrAudioFiles(context, uris)
            onBackToDeck()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Music Sources (YouTube / SoundCloud)",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackToDeck,
                        modifier = Modifier.testTag("back_to_deck_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Back",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DjBackground)
            )
        },
        containerColor = DjBackground,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // User Feedback Toast Snackbar
            userMessage?.let { msg ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = NeonMagenta.copy(alpha = 0.2f)),
                    shape = RoundedCornerShape(10.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = msg, color = TextPrimary, fontSize = 12.sp, modifier = Modifier.weight(1f))
                        Text(
                            text = "DISMISS",
                            color = NeonMagenta,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier
                                .clickable { viewModel.clearUserMessage() }
                                .padding(4.dp)
                        )
                    }
                }
            }

            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = DjSurface,
                contentColor = NeonViolet,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, DjCardBorder, RoundedCornerShape(12.dp))
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            text = "Playlists",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selectedTab == 0) NeonViolet else TextSecondary
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Web Link",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selectedTab == 1) NeonCyan else TextSecondary
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = {
                        Text(
                            text = "Folder / Archive",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (selectedTab == 2) NeonAmber else TextSecondary
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (selectedTab == 0) {
                // List of YouTube & SoundCloud Playlists
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    items(playlists) { playlist ->
                        PlaylistCard(
                            playlist = playlist,
                            onPlayClick = {
                                viewModel.selectAndPlayPlaylist(playlist)
                                onBackToDeck()
                            }
                        )
                    }
                }
            } else if (selectedTab == 1) {
                // Link Import Tab
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DjSurface),
                        shape = RoundedCornerShape(16.dp),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.PlaylistPlay,
                                    contentDescription = "YouTube Playlist",
                                    tint = YouTubeRed,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "IMPORT WHOLE YOUTUBE PLAYLIST / LINK",
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCyan,
                                    fontSize = 13.sp
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Paste a YouTube playlist link (e.g. youtube.com/playlist?list=PL...) or single track URL. All tracks will be queued for continuous Spotify/Apple style Automixing!",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            OutlinedTextField(
                                value = importInput,
                                onValueChange = { viewModel.updateImportUrlInput(it) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("import_url_input"),
                                placeholder = { Text("https://www.youtube.com/playlist?list=PL...", color = TextMuted) },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = DjSurfaceVariant,
                                    unfocusedContainerColor = DjSurfaceVariant,
                                    focusedBorderColor = NeonCyan,
                                    unfocusedBorderColor = DjCardBorder,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Quick Preset Buttons for Testing YouTube Playlist Import
                            Text(
                                text = "QUICK TEST SAMPLE PLAYLIST LINKS:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextSecondary
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        viewModel.updateImportUrlInput("https://www.youtube.com/playlist?list=PL3N4v983u1y7Y5d")
                                    },
                                    label = { Text("EDM Festival Playlist", fontSize = 11.sp, color = NeonCyan) },
                                    colors = FilterChipDefaults.filterChipColors(containerColor = DjSurfaceVariant)
                                )
                                FilterChip(
                                    selected = false,
                                    onClick = {
                                        viewModel.updateImportUrlInput("https://www.youtube.com/playlist?list=PL8x1a39y0mK4281")
                                    },
                                    label = { Text("House & Synth Mix", fontSize = 11.sp, color = NeonMagenta) },
                                    colors = FilterChipDefaults.filterChipColors(containerColor = DjSurfaceVariant)
                                )
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = {
                                    viewModel.importTrackFromUrl()
                                    onBackToDeck()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("import_and_mix_button"),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan)
                            ) {
                                Icon(imageVector = Icons.Default.CloudDownload, contentDescription = "Import Playlist")
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("IMPORT PLAYLIST & START AUTOMIX", fontWeight = FontWeight.Bold, color = Color.Black)
                            }
                        }
                    }
                }
            } else {
                // Folder & Archive Import Tab (Tab 2)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DjSurface),
                            shape = RoundedCornerShape(16.dp),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = "Import Folder",
                                        tint = NeonAmber,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "IMPORT ENTIRE MUSIC FOLDER",
                                        fontWeight = FontWeight.Bold,
                                        color = NeonAmber,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Select any folder on your device containing MP3, FLAC, WAV, M4A, or AAC audio tracks. All music in the folder and its subdirectories will be extracted into a seamless custom DJ playlist!",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        folderLauncher.launch(null)
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("import_folder_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonAmber)
                                ) {
                                    Icon(imageVector = Icons.Default.Folder, contentDescription = "Browse Folder", tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("SELECT & IMPORT FOLDER", fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                        }
                    }

                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = DjSurface),
                            shape = RoundedCornerShape(16.dp),
                            border = CardDefaults.outlinedCardBorder()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Archive,
                                        contentDescription = "Import Archive",
                                        tint = NeonMagenta,
                                        modifier = Modifier.size(26.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(
                                        text = "IMPORT ZIP / RAR / TAR.GZ / AUDIO FILES",
                                        fontWeight = FontWeight.Bold,
                                        color = NeonMagenta,
                                        fontSize = 14.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Pick .ZIP, .TAR.GZ, .TAR, or .RAR archive packages or select multiple MP3, FLAC, and WAV audio files. The DJ engine will unpack the archive, extract metadata & album art, and generate a custom party mix queue!",
                                    color = TextSecondary,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )

                                Spacer(modifier = Modifier.height(14.dp))

                                Button(
                                    onClick = {
                                        fileLauncher.launch(arrayOf("audio/*", "application/zip", "application/x-zip-compressed", "application/x-tar", "application/gzip", "application/x-gzip", "application/x-rar-compressed", "*/*"))
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .testTag("import_archive_files_button"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta)
                                ) {
                                    Icon(imageVector = Icons.Default.Unarchive, contentDescription = "Select Archive", tint = Color.White)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("SELECT ARCHIVE OR AUDIO FILES", fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            }
                        }
                    }

                    item {
                        Surface(
                            color = DjSurfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(
                                    text = "SUPPORTED LOCAL AUDIO & ARCHIVE FORMATS",
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    listOf("MP3", "FLAC", "WAV", "M4A", "ZIP", "TAR.GZ", "RAR").forEach { fmt ->
                                        Surface(
                                            color = DjSurface,
                                            shape = RoundedCornerShape(6.dp),
                                            border = CardDefaults.outlinedCardBorder()
                                        ) {
                                            Text(
                                                text = fmt,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = NeonCyan,
                                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistCard(
    playlist: Playlist,
    onPlayClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("playlist_card_${playlist.id}"),
        colors = CardDefaults.cardColors(containerColor = DjSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    model = playlist.coverUrl,
                    contentDescription = playlist.name,
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(12.dp))
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${playlist.genre} • ${playlist.avgBpm} BPM Avg",
                        color = NeonAmber,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${playlist.tracks.size} Tracks",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }

                Surface(
                    color = when (playlist.source) {
                        PlaylistSource.YOUTUBE -> YouTubeRed.copy(alpha = 0.2f)
                        PlaylistSource.SOUNDCLOUD -> SoundCloudOrange.copy(alpha = 0.2f)
                        PlaylistSource.CUSTOM -> NeonAmber.copy(alpha = 0.2f)
                        else -> NeonViolet.copy(alpha = 0.2f)
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = if (playlist.source == PlaylistSource.CUSTOM) "LOCAL / CUSTOM" else playlist.source.name,
                        color = when (playlist.source) {
                            PlaylistSource.YOUTUBE -> YouTubeRed
                            PlaylistSource.SOUNDCLOUD -> SoundCloudOrange
                            PlaylistSource.CUSTOM -> NeonAmber
                            else -> NeonViolet
                        },
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = playlist.description,
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onPlayClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(42.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
            ) {
                Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Start DJ Mix")
                Spacer(modifier = Modifier.width(6.dp))
                Text(text = "START SEAMLESS DJ MIX", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
