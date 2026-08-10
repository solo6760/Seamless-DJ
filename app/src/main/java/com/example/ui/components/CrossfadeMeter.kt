package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.audio.ActiveDeck
import com.example.ui.theme.*

@Composable
fun CrossfadeMeter(
    activeDeck: ActiveDeck,
    deckAVolume: Float,
    deckBVolume: Float,
    isCrossfading: Boolean,
    crossfadeProgress: Float,
    bpm: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("crossfade_meter_card"),
        colors = CardDefaults.cardColors(containerColor = DjSurface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Label Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DECK A (${(deckAVolume * 100).toInt()}%)",
                    fontWeight = FontWeight.Bold,
                    color = NeonViolet,
                    fontSize = 12.sp
                )

                // Beat Sync Badge
                Surface(
                    color = if (isCrossfading) NeonMagenta.copy(alpha = 0.2f) else DjSurfaceVariant,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.border(
                        1.dp,
                        if (isCrossfading) NeonMagenta else DjCardBorder,
                        RoundedCornerShape(20.dp)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (isCrossfading) NeonMagenta else NeonEmerald)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isCrossfading) "FADING MIX..." else "SYNC $bpm BPM",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (isCrossfading) NeonMagenta else NeonEmerald
                        )
                    }
                }

                Text(
                    text = "DECK B (${(deckBVolume * 100).toInt()}%)",
                    fontWeight = FontWeight.Bold,
                    color = NeonCyan,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Crossfader Visual Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                NeonViolet.copy(alpha = 0.8f),
                                DjSurfaceVariant,
                                NeonCyan.copy(alpha = 0.8f)
                            )
                        )
                    )
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                // Crossfader Slider Position Indicator
                val sliderPosition = if (activeDeck == ActiveDeck.DECK_A) {
                    if (isCrossfading) crossfadeProgress else 0.0f
                } else {
                    if (isCrossfading) 1.0f - crossfadeProgress else 1.0f
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.12f)
                        .fillMaxHeight(0.85f)
                        .align(
                            when {
                                sliderPosition <= 0.1f -> Alignment.CenterStart
                                sliderPosition >= 0.9f -> Alignment.CenterEnd
                                else -> Alignment.Center
                            }
                        )
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .border(1.dp, Color.Black, RoundedCornerShape(8.dp))
                )
            }

            if (isCrossfading) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { crossfadeProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = NeonMagenta,
                    trackColor = DjSurfaceVariant
                )
            }
        }
    }
}
