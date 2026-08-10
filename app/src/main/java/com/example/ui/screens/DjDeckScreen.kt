package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.audio.ActiveDeck
import com.example.data.model.Track
import com.example.ui.DjViewModel
import com.example.ui.components.CrossfadeMeter
import com.example.ui.components.PartyLightsOverlay
import com.example.ui.components.TurntableDeck
import com.example.ui.components.YouTubePlayerWidget
import com.example.ui.theme.*

@Composable
fun DjDeckScreen(
    viewModel: DjViewModel,
    onNavigateToSources: () -> Unit,
    onNavigateToRequests: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val engineState by viewModel.engineState.collectAsState()
    val djSettings by viewModel.settings.collectAsState()
    val activeTrack = engineState.currentTrack
    val nextTrack = engineState.nextTrack

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DjBackground)
    ) {
        // Party Vibe Ambient Lighting Background
        PartyLightsOverlay(
            isEnabled = djSettings.partyLightsEnabled,
            isPlaying = engineState.isPlaying
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Top Header: Title + Party Room Badge + Settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "SEAMLESS PARTY DJ",
                        fontWeight = FontWeight.Black,
                        color = TextPrimary,
                        fontSize = 18.sp,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = engineState.statusMessage,
                        color = NeonMagenta,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Room Code Chip
                    Surface(
                        color = DjSurfaceVariant,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .border(1.dp, DjCardBorder, RoundedCornerShape(20.dp))
                            .clickable { onNavigateToRequests() }
                            .testTag("room_code_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Party Requests",
                                tint = NeonCyan,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = djSettings.partyRoomCode,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier.testTag("dj_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "DJ Settings",
                            tint = TextPrimary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Main Dual Decks View (Deck A and Deck B)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                TurntableDeck(
                    deckName = "DECK A",
                    isActive = engineState.activeDeck == ActiveDeck.DECK_A,
                    isSpinning = engineState.deckASpinning,
                    volume = engineState.deckAVolume,
                    track = if (engineState.activeDeck == ActiveDeck.DECK_A) activeTrack else nextTrack,
                    accentColor = NeonViolet,
                    modifier = Modifier.weight(1f)
                )

                TurntableDeck(
                    deckName = "DECK B",
                    isActive = engineState.activeDeck == ActiveDeck.DECK_B,
                    isSpinning = engineState.deckBSpinning,
                    volume = engineState.deckBVolume,
                    track = if (engineState.activeDeck == ActiveDeck.DECK_B) activeTrack else nextTrack,
                    accentColor = NeonCyan,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Crossfade & Sync Meter Bar
            CrossfadeMeter(
                activeDeck = engineState.activeDeck,
                deckAVolume = engineState.deckAVolume,
                deckBVolume = engineState.deckBVolume,
                isCrossfading = engineState.isCrossfading,
                crossfadeProgress = engineState.crossfadeProgress,
                bpm = engineState.activeBpm
            )

            // Automix Mode Banner (Spotify / Apple Music Automix standard)
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DjSurface),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            color = NeonCyan.copy(alpha = 0.2f),
                            shape = CircleShape,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.AutoMode,
                                    contentDescription = "Automix",
                                    tint = NeonCyan,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "AUTOMIX MODE (SPOTIFY / APPLE STYLE)",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = TextPrimary
                            )
                            Text(
                                text = if (engineState.automixEnabled) "Equal-Power Beat Blend • Continuous Flow" else "Manual Control Mode",
                                fontSize = 10.sp,
                                color = if (engineState.automixEnabled) NeonEmerald else TextMuted
                            )
                        }
                    }

                    Switch(
                        checked = engineState.automixEnabled,
                        onCheckedChange = { viewModel.toggleAutomix(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonCyan.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.testTag("automix_toggle_switch")
                    )
                }
            }

            // Live YouTube Embed Stream Player (when playing YouTube source)
            if (activeTrack?.source == com.example.data.model.TrackSource.YOUTUBE || activeTrack?.sourceUrl?.contains("youtube") == true) {
                Spacer(modifier = Modifier.height(10.dp))
                YouTubePlayerWidget(
                    track = activeTrack,
                    isPlaying = engineState.isPlaying
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Segment Progress Bar (1m 30s playback segment tracker)
            val segmentElapsed = engineState.segmentElapsedSec
            val segmentTotal = engineState.segmentTotalSec
            val progressFraction = (segmentElapsed.toFloat() / segmentTotal.toFloat()).coerceIn(0f, 1f)

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DjSurface),
                shape = RoundedCornerShape(12.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "MIX SEGMENT: ${segmentElapsed}s / ${segmentTotal}s",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextSecondary
                        )
                        Text(
                            text = "TRANSITION AT ${segmentTotal - djSettings.crossfadeDurationSec}s",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonMagenta
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { progressFraction },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = NeonViolet,
                        trackColor = DjSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // MAIN PARTY PLAYBACK CONTROLS (Play/Pause & SKIP WITH BEAT FADE)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Play / Pause Button
                Button(
                    onClick = { viewModel.togglePlayPause() },
                    modifier = Modifier
                        .size(64.dp)
                        .testTag("play_pause_button"),
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                ) {
                    Icon(
                        imageVector = if (engineState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play / Pause",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                // SKIP BUTTON: Smooth Beat-Matched Transition (Pdf core requirement)
                Button(
                    onClick = { viewModel.skipTrack() },
                    modifier = Modifier
                        .height(64.dp)
                        .padding(horizontal = 8.dp)
                        .testTag("skip_track_button"),
                    shape = RoundedCornerShape(32.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = NeonMagenta
                    ),
                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.SkipNext,
                            contentDescription = "Skip Track",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "SKIP TRACK",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(
                                text = "BEAT CROSSFADE ⚡",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                // Change Playlist / Music Sources Button
                IconButton(
                    onClick = onNavigateToSources,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(DjSurfaceVariant)
                        .border(1.dp, DjCardBorder, CircleShape)
                        .testTag("music_sources_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.QueueMusic,
                        contentDescription = "Music Sources",
                        tint = NeonCyan
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Upcoming Track Queue List
            Text(
                text = "UPCOMING IN MIX QUEUE",
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                fontSize = 12.sp,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(6.dp))

            val queueList = engineState.queue
            if (queueList.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DjSurface)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.LibraryMusic,
                            contentDescription = "Playlist",
                            tint = NeonViolet
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Tap Music Sources to select a playlist or paste YouTube/SoundCloud URL!",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    itemsIndexed(queueList.take(6)) { index, track ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("queue_item_$index"),
                            colors = CardDefaults.cardColors(containerColor = DjSurface),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = DjSurfaceVariant,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = "#${index + 1}",
                                            fontWeight = FontWeight.Bold,
                                            color = NeonCyan,
                                            fontSize = 12.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = track.title,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        fontSize = 13.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${track.artist} • Drop at ${track.introOffsetSec}s",
                                        color = TextSecondary,
                                        fontSize = 11.sp
                                    )
                                }

                                Surface(
                                    color = DjSurfaceVariant,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text(
                                        text = "${track.bpm} BPM",
                                        color = NeonAmber,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
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
