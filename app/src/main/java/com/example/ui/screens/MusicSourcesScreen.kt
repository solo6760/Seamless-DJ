package com.example.ui.screens

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
    val playlists by viewModel.playlists.collectAsState()
    val importInput by viewModel.importUrlInput.collectAsState()
    val userMessage by viewModel.userMessage.collectAsState()

    var selectedTab by remember { mutableStateOf(0) } // 0: Curated Playlists, 1: Import Link

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
                            text = "Party Playlists",
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 0) NeonViolet else TextSecondary
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        Text(
                            text = "Import Web Link",
                            fontWeight = FontWeight.Bold,
                            color = if (selectedTab == 1) NeonCyan else TextSecondary
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
            } else {
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

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "SUPPORTED SOURCES",
                        fontWeight = FontWeight.Bold,
                        color = TextSecondary,
                        fontSize = 12.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = DjSurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, DjCardBorder, RoundedCornerShape(10.dp))
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayCircle, contentDescription = "YouTube", tint = YouTubeRed)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("YouTube", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Surface(
                            color = DjSurface,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .weight(1f)
                                .border(1.dp, DjCardBorder, RoundedCornerShape(10.dp))
                        ) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Cloud, contentDescription = "SoundCloud", tint = SoundCloudOrange)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("SoundCloud", color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                    color = if (playlist.source == PlaylistSource.YOUTUBE) YouTubeRed.copy(alpha = 0.2f) else SoundCloudOrange.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = playlist.source.name,
                        color = if (playlist.source == PlaylistSource.YOUTUBE) YouTubeRed else SoundCloudOrange,
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
