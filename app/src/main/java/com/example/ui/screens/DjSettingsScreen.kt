package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.DjViewModel
import com.example.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DjSettingsScreen(
    viewModel: DjViewModel,
    onBackToDeck: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentSettings by viewModel.settings.collectAsState()

    var segmentSec by remember(currentSettings) { mutableFloatStateOf(currentSettings.segmentDurationSec.toFloat()) }
    var dropOffsetSec by remember(currentSettings) { mutableFloatStateOf(currentSettings.startOffsetSec.toFloat()) }
    var fadeSec by remember(currentSettings) { mutableFloatStateOf(currentSettings.crossfadeDurationSec.toFloat()) }
    var bpmSync by remember(currentSettings) { mutableStateOf(currentSettings.autoBpmMatch) }
    var partyLights by remember(currentSettings) { mutableStateOf(currentSettings.partyLightsEnabled) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Automated DJ Mix Settings",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackToDeck,
                        modifier = Modifier.testTag("back_from_settings_button")
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back", tint = TextPrimary)
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
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Segment Duration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DjSurface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Song Segment Play Time",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${segmentSec.roundToInt()} seconds (${segmentSec.roundToInt() / 60}m ${segmentSec.roundToInt() % 60}s)",
                            color = NeonViolet,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "PDF Standard: Each song plays for ~1m 30s (90s) before transitioning.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = segmentSec,
                        onValueChange = { segmentSec = it },
                        valueRange = 45f..180f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonViolet,
                            activeTrackColor = NeonViolet,
                            inactiveTrackColor = DjSurfaceVariant
                        ),
                        modifier = Modifier.testTag("segment_duration_slider")
                    )
                }
            }

            // Drop Offset Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DjSurface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Incoming Track Start Offset",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "At ${dropOffsetSec.roundToInt()}s mark",
                            color = NeonCyan,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "PDF Standard: Next track begins around the 20-second mark (skips intro noise).",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = dropOffsetSec,
                        onValueChange = { dropOffsetSec = it },
                        valueRange = 0f..40f,
                        steps = 7,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonCyan,
                            activeTrackColor = NeonCyan,
                            inactiveTrackColor = DjSurfaceVariant
                        ),
                        modifier = Modifier.testTag("drop_offset_slider")
                    )
                }
            }

            // Crossfade Duration Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DjSurface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Transition Crossfade Time",
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${fadeSec.roundToInt()} seconds",
                            color = NeonMagenta,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Smooth volume fade curve between outgoing & incoming track.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = fadeSec,
                        onValueChange = { fadeSec = it },
                        valueRange = 2f..12f,
                        steps = 10,
                        colors = SliderDefaults.colors(
                            thumbColor = NeonMagenta,
                            activeTrackColor = NeonMagenta,
                            inactiveTrackColor = DjSurfaceVariant
                        ),
                        modifier = Modifier.testTag("crossfade_duration_slider")
                    )
                }
            }

            // Toggles Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DjSurface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Auto BPM Beat Match Sync",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Aligns tempo so rhythm continues without interruption.",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Switch(
                            checked = bpmSync,
                            onCheckedChange = { bpmSync = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonEmerald,
                                checkedTrackColor = NeonEmerald.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.testTag("bpm_sync_switch")
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = DjCardBorder)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Party Visual Light Show",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Ambient party lighting overlay reacting to beats.",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Switch(
                            checked = partyLights,
                            onCheckedChange = { partyLights = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = NeonMagenta,
                                checkedTrackColor = NeonMagenta.copy(alpha = 0.3f)
                            ),
                            modifier = Modifier.testTag("party_lights_switch")
                        )
                    }
                }
            }

            // Gemini API Key Management Card
            val currentApiKey by viewModel.apiKey.collectAsState()
            var showUpdateKeyDialog by remember { mutableStateOf(false) }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = DjSurface),
                shape = RoundedCornerShape(16.dp),
                border = CardDefaults.outlinedCardBorder()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gemini API Key (BPM Auto-Lookup)",
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Powers live Google Search grounded BPM queries for queued songs.",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }

                        Surface(
                            color = if (!currentApiKey.isNullOrBlank()) NeonEmerald.copy(alpha = 0.2f) else NeonAmber.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (!currentApiKey.isNullOrBlank()) "Configured" else "Not Set",
                                color = if (!currentApiKey.isNullOrBlank()) NeonEmerald else NeonAmber,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = { showUpdateKeyDialog = true },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("update_api_key_settings_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = DjSurfaceVariant)
                        ) {
                            Text(
                                text = if (currentApiKey.isNullOrBlank()) "Set API Key" else "Update API Key",
                                fontSize = 12.sp,
                                color = TextPrimary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (!currentApiKey.isNullOrBlank()) {
                            OutlinedButton(
                                onClick = { viewModel.clearGeminiApiKey() },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("clear_api_key_settings_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text("Clear Key", fontSize = 12.sp, color = Color(0xFFFF6B6B))
                            }
                        }
                    }
                }
            }

            if (showUpdateKeyDialog) {
                com.example.ui.components.ApiKeyOnboardingDialog(
                    onSaveKey = { key, onResult ->
                        viewModel.saveGeminiApiKey(key) { success, err ->
                            if (success) {
                                showUpdateKeyDialog = false
                            }
                            onResult(success, err)
                        }
                    },
                    onSkip = { showUpdateKeyDialog = false }
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save Settings Button
            Button(
                onClick = {
                    viewModel.updateSettings(
                        segmentDurationSec = segmentSec.roundToInt(),
                        startOffsetSec = dropOffsetSec.roundToInt(),
                        crossfadeDurationSec = fadeSec.roundToInt(),
                        autoBpmMatch = bpmSync,
                        partyLightsEnabled = partyLights
                    )
                    onBackToDeck()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_settings_button"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
            ) {
                Icon(imageVector = Icons.Default.Save, contentDescription = "Save")
                Spacer(modifier = Modifier.width(8.dp))
                Text("SAVE DJ MIX CONFIGURATION", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
