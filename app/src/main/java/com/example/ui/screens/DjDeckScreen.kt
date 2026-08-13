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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.audio.ActiveDeck
import com.example.data.model.Track
import com.example.data.model.TransitionType
import com.example.ui.DjViewModel
import com.example.ui.components.ApiKeyOnboardingDialog
import com.example.ui.components.CrossfadeMeter
import com.example.ui.components.PartyLightsOverlay
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
    val isFirstLaunchCompleted by viewModel.isFirstLaunchCompleted.collectAsState()
    val isQueueOptimized by viewModel.isQueueOptimized.collectAsState()

    val activeTrack = engineState.currentTrack
    val nextTrack = engineState.nextTrack
    val queueList = engineState.queue

    if (!isFirstLaunchCompleted) {
        ApiKeyOnboardingDialog(
            onSaveKey = { key, onResult ->
                viewModel.saveGeminiApiKey(key, onResult)
            },
            onSkip = {
                viewModel.skipFirstLaunchOnboarding()
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Subtle ambient party light overlay
        PartyLightsOverlay(
            isEnabled = djSettings.partyLightsEnabled,
            isPlaying = engineState.isPlaying
        )

        val scrollState = rememberScrollState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Top Header Bar: Room code & settings
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Automix DJ Pro",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = engineState.statusMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Party Room Request Chip
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(20.dp),
                        shadowElevation = 2.dp,
                        modifier = Modifier
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp))
                            .clickable { onNavigateToRequests() }
                            .testTag("room_code_chip")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode,
                                contentDescription = "Party Requests",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = djSettings.partyRoomCode,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    // Settings Button (Min 48dp touch target)
                    IconButton(
                        onClick = onNavigateToSettings,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("dj_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }

            // 1. NOW PLAYING CARD (Primary Centerpiece)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(4.dp, RoundedCornerShape(24.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(24.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Large Album Art (~65% width)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.65f)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(20.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        if (!activeTrack?.albumArtUrl.isNullOrBlank()) {
                            AsyncImage(
                                model = activeTrack?.albumArtUrl,
                                contentDescription = activeTrack?.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MusicNote,
                                contentDescription = "Album Art",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                        }

                        // Phrase structural tag overlay on Album Art
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "📍 ${engineState.currentPhraseLabel.uppercase()}",
                                style = MaterialTheme.typography.labelSmall,
                                color = WarmOrange,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Song Title (Large bold)
                    Text(
                        text = activeTrack?.title ?: "No Track Playing",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // Artist Name (Medium secondary color)
                    Text(
                        text = activeTrack?.artist ?: "Select a playlist from Music Sources",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // DSP Metrics Pill Badges: BPM, Musical Key, LUFS Loudness, Energy Arc
                    if (activeTrack != null) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (activeTrack.bpmStatus == com.example.data.model.BpmStatus.FETCHING || activeTrack.isBeatAnalyzing) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(10.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "DSP...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Default.Speed,
                                            contentDescription = "BPM",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${activeTrack.bpm} BPM",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }

                            if (activeTrack.musicalKey.isNotBlank()) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(16.dp),
                                    modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "🎹 ${activeTrack.musicalKey}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }

                            // Energy Level Badge
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "⚡ ${activeTrack.energyCategory} (${activeTrack.energyScore})",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }

                            // LUFS Normalization Target
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "🔊 ${String.format("%.1f", activeTrack.lufs)} LUFS",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Crossfade / Multi-Band EQ Transition HUD
                    if (engineState.isCrossfading) {
                        Surface(
                            color = WarmOrange.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                                .border(1.dp, WarmOrange.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${engineState.activeTransitionType.iconSymbol} ${engineState.activeTransitionType.displayName} Mixing",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = WarmOrange,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${(engineState.crossfadeProgress * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = WarmOrange,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                LinearProgressIndicator(
                                    progress = { engineState.crossfadeProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(RoundedCornerShape(3.dp)),
                                    color = WarmOrange,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                                )

                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Deck A Vol: ${(engineState.deckAVolume * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Deck B Vol: ${(engineState.deckBVolume * 100).toInt()}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    // Segment Tracker & Progress Scrubber Slider
                    val segmentElapsed = engineState.segmentElapsedSec
                    val segmentTotal = kotlin.math.max(1, engineState.segmentTotalSec)

                    var isDragging by remember { mutableStateOf(false) }
                    var dragPosition by remember(segmentElapsed) { mutableFloatStateOf(segmentElapsed.toFloat()) }

                    val currentDisplaySec = if (isDragging) dragPosition.toInt() else segmentElapsed
                    val remainingSec = (segmentTotal - currentDisplaySec).coerceAtLeast(0)

                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${currentDisplaySec / 60}:${String.format("%02d", currentDisplaySec % 60)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (engineState.isCrossfading) "CROSSFADING NOW" else "Transition in ${remainingSec}s",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (engineState.isCrossfading) WarmOrange else MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${segmentTotal / 60}:${String.format("%02d", segmentTotal % 60)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.seekByDelta(-10) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("rewind_10s_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastRewind,
                                    contentDescription = "Rewind 10 Seconds",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Slider(
                                value = if (isDragging) dragPosition else segmentElapsed.toFloat().coerceIn(0f, segmentTotal.toFloat()),
                                onValueChange = {
                                    isDragging = true
                                    dragPosition = it
                                },
                                onValueChangeFinished = {
                                    isDragging = false
                                    viewModel.seekToPosition(dragPosition.toInt())
                                },
                                valueRange = 0f..segmentTotal.toFloat(),
                                colors = SliderDefaults.colors(
                                    thumbColor = WarmOrange,
                                    activeTrackColor = WarmOrange,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("audio_scrubber_slider")
                            )

                            IconButton(
                                onClick = { viewModel.seekByDelta(10) },
                                modifier = Modifier
                                    .size(36.dp)
                                    .testTag("fast_forward_10s_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FastForward,
                                    contentDescription = "Fast Forward 10 Seconds",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Live YouTube Embed Stream Player (when active source is YouTube)
            if (activeTrack?.source == com.example.data.model.TrackSource.YOUTUBE || activeTrack?.sourceUrl?.contains("youtube") == true) {
                YouTubePlayerWidget(
                    track = activeTrack,
                    isPlaying = engineState.isPlaying
                )
            }

            // 2. NEXT TRACK PREVIEW CARD
            nextTrack?.let { next ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(16.dp)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier
                            .padding(14.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = WarmOrange.copy(alpha = 0.15f),
                            shape = CircleShape,
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SkipNext,
                                    contentDescription = "Next Track",
                                    tint = WarmOrange,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "NEXT UP",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = next.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${next.artist} • ${next.bpm} BPM • Energy: ${next.energyCategory}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        val score = if (activeTrack != null) {
                            com.example.util.SmartPlaylistOptimizer.calculateCompatibilityScore(activeTrack, next)
                        } else 1.0f
                        val transitionDecision = if (activeTrack != null) {
                            com.example.util.SmartPlaylistOptimizer.createTransitionDecision(activeTrack, next)
                        } else null
                        val transitionType = transitionDecision?.type ?: com.example.data.model.selectTransitionType(score)

                        Surface(
                            color = when {
                                score >= 0.7f -> StatusGreen.copy(alpha = 0.15f)
                                score >= 0.5f -> WarmOrange.copy(alpha = 0.15f)
                                else -> StatusGray.copy(alpha = 0.15f)
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${transitionType.iconSymbol} ${transitionType.displayName} (${(score * 100).toInt()}%)",
                                    color = when {
                                        score >= 0.7f -> StatusGreen
                                        score >= 0.5f -> WarmOrange
                                        else -> StatusGray
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // 2.5 DJ TRANSITION DEBUG HUD WIDGET (Requirement 1 & Debug Mode)
            if (djSettings.debugModeEnabled) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(2.dp, RoundedCornerShape(16.dp))
                        .testTag("dj_debug_hud_card"),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.BugReport,
                                    contentDescription = "DJ Debug HUD",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "DJ Transition Debug HUD",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val timeUntilTransition = if (engineState.isCrossfading) {
                                "🎛️ Crossfading ${(engineState.crossfadeProgress * 100).toInt()}%"
                            } else {
                                val remaining = (engineState.segmentTotalSec - engineState.segmentElapsedSec).coerceAtLeast(0)
                                if (engineState.segmentTotalSec > 0) "${remaining}s to transition" else "Playing Full Song"
                            }

                            Text(
                                text = timeUntilTransition,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val activeDec = if (activeTrack != null && nextTrack != null) {
                            com.example.util.SmartPlaylistOptimizer.createTransitionDecision(activeTrack, nextTrack)
                        } else engineState.activeTransitionDecision

                        if (activeDec != null) {
                            Text(
                                text = "Selected: ${activeDec.type.iconSymbol} ${activeDec.type.displayName} • ${(activeDec.overallScore * 100).toInt()}% match",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = activeDec.explanation,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            // Compatibility score breakdown chips
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Key (Harmonic)", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${(activeDec.harmonicScore * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("${activeTrack?.musicalKey ?: "8A"}➔${nextTrack?.musicalKey ?: "8A"}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("BPM Match", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${(activeDec.bpmScore * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("${activeTrack?.bpm ?: 124}➔${nextTrack?.bpm ?: 124}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Energy Fit", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${(activeDec.energyScore * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("Δ${kotlin.math.abs((activeTrack?.energyScore ?: 50) - (nextTrack?.energyScore ?: 50))}", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text("Spectral Flux", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        Text("${(activeDec.spectralFluxScore * 100).toInt()}%", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("Groove", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // DSP Realtime status info
                            val lufsDiff = ((nextTrack?.lufs ?: -14f) - (activeTrack?.lufs ?: -14f))
                            Text(
                                text = "Loudness: Out ${String.format("%.1f", activeTrack?.lufs ?: -14f)} LUFS | In ${String.format("%.1f", nextTrack?.lufs ?: -14f)} LUFS (Δ${String.format("%+.1f", lufsDiff)}dB)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "Playing single track. Queue more tracks to view live transition analysis.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Quick Test Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.triggerTransition(com.example.data.model.TransitionType.EQ_FADE) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(2.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("EQ -15dB", fontSize = 9.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.triggerTransition(com.example.data.model.TransitionType.FILTER_SWEEP) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(2.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Filter HPF", fontSize = 9.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.triggerTransition(com.example.data.model.TransitionType.ECHO_OUT) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(2.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Echo Out", fontSize = 9.sp)
                            }
                            OutlinedButton(
                                onClick = { viewModel.triggerTransition(com.example.data.model.TransitionType.CROSSFADE) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(2.dp),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text("Crossfade", fontSize = 9.sp)
                            }
                        }
                    }
                }
            }

            // 3. LARGE PLAYBACK CONTROLS (Generous > 48dp Touch Targets for Party Use)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Music Sources Button (Min 48dp)
                        IconButton(
                            onClick = onNavigateToSources,
                            modifier = Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                .testTag("music_sources_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.QueueMusic,
                                contentDescription = "Music Sources",
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Play / Pause Primary Button (Large 64dp x 64dp)
                        Button(
                            onClick = { viewModel.togglePlayPause() },
                            modifier = Modifier
                                .size(64.dp)
                                .testTag("play_pause_button"),
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                        ) {
                            Icon(
                                imageVector = if (engineState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Skip Track Button with Beat Crossfade (Large 52dp height)
                        Button(
                            onClick = { viewModel.skipTrack() },
                            modifier = Modifier
                                .height(52.dp)
                                .testTag("skip_track_button"),
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Skip Track",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "SKIP",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Automix Mode Toggle Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.AutoMode,
                                contentDescription = "Automix",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (engineState.automixEnabled) "Automix Continuous Play Active" else "Manual Control Mode",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Switch(
                            checked = engineState.automixEnabled,
                            onCheckedChange = { viewModel.toggleAutomix(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("automix_toggle_switch")
                        )
                    }
                }
            }

            // 4. QUEUE VIEW SECTION
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(2.dp, RoundedCornerShape(20.dp)),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Header with count ("5 tracks in queue")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${queueList.size} tracks in queue",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold
                        )

                        if (queueList.isNotEmpty()) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
                                    .clickable { viewModel.reshuffleCurrentQueue() }
                                    .testTag("reshuffle_queue_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Shuffle,
                                        contentDescription = "Reorder Queue",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (isQueueOptimized) "⚡ Reordered Arc" else "Reorder Flow",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (queueList.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.LibraryMusic,
                                    contentDescription = "Empty Queue",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "No upcoming songs. Tap Music Sources to pick a playlist!",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            queueList.forEachIndexed { index, track ->
                                val score = if (activeTrack != null) {
                                    com.example.util.SmartPlaylistOptimizer.calculateCompatibilityScore(activeTrack, track)
                                } else 1.0f
                                val decision = if (activeTrack != null) {
                                    com.example.util.SmartPlaylistOptimizer.createTransitionDecision(activeTrack, track)
                                } else null
                                val transitionType = decision?.type ?: com.example.data.model.selectTransitionType(score)

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            viewModel.skipTrack()
                                        }
                                        .testTag("queue_item_$index"),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Orange Accent Bar / Number
                                        Box(
                                            modifier = Modifier
                                                .width(4.dp)
                                                .height(36.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(if (index == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                                        )

                                        Spacer(modifier = Modifier.width(10.dp))

                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = track.title,
                                                style = MaterialTheme.typography.titleMedium,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "${track.artist} • ${track.bpm} BPM • Energy: ${track.energyCategory}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }

                                        Spacer(modifier = Modifier.width(8.dp))

                                        // Compatibility Score Badge (Icon + Color + Text)
                                        Surface(
                                            color = when {
                                                score >= 0.7f -> StatusGreen.copy(alpha = 0.15f)
                                                score >= 0.5f -> WarmOrange.copy(alpha = 0.15f)
                                                else -> StatusGray.copy(alpha = 0.15f)
                                            },
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = "${transitionType.iconSymbol} ${(score * 100).toInt()}%",
                                                    color = when {
                                                        score >= 0.7f -> StatusGreen
                                                        score >= 0.5f -> WarmOrange
                                                        else -> StatusGray
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.Bold
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

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
