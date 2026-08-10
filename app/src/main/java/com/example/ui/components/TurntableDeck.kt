package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.Track
import com.example.ui.theme.*

@Composable
fun TurntableDeck(
    deckName: String, // "DECK A" or "DECK B"
    isActive: Boolean,
    isSpinning: Boolean,
    volume: Float,
    track: Track?,
    accentColor: Color,
    modifier: Modifier = Modifier
) {
    // Rotation animation for spinning vinyl disc
    val infiniteTransition = rememberInfiniteTransition(label = "VinylSpin")
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isSpinning) 2500 else 100000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .border(
                width = if (isActive) 2.dp else 1.dp,
                color = if (isActive) accentColor else DjCardBorder,
                shape = RoundedCornerShape(20.dp)
            )
            .testTag("deck_card_${deckName.lowercase().replace(" ", "_")}"),
        colors = CardDefaults.cardColors(
            containerColor = if (isActive) DjSurfaceVariant else DjSurface
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Row: Deck Badge + Active Light + Volume %
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(if (isActive) accentColor else TextMuted)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = deckName,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) TextPrimary else TextSecondary,
                        fontSize = 14.sp
                    )
                }

                Surface(
                    color = accentColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        text = "GAIN ${(volume * 100).toInt()}%",
                        color = accentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Animated Vinyl Disc Canvas + Album Art Center
            Box(
                modifier = Modifier
                    .size(130.dp)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                // Vinyl Record Background Base
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .rotate(if (isSpinning) rotationAngle else 0f)
                ) {
                    val radius = size.minDimension / 2
                    val centerPt = Offset(size.width / 2, size.height / 2)

                    // Outer Dark Disc
                    drawCircle(
                        color = Color(0xFF14121F),
                        radius = radius,
                        center = centerPt
                    )

                    // Outer Rim Line
                    drawCircle(
                        color = accentColor.copy(alpha = 0.5f),
                        radius = radius - 2,
                        center = centerPt,
                        style = Stroke(width = 2f)
                    )

                    // Grooves
                    for (r in listOf(radius * 0.85f, radius * 0.7f, radius * 0.55f)) {
                        drawCircle(
                            color = Color(0xFF2A253A),
                            radius = r,
                            center = centerPt,
                            style = Stroke(width = 1.5f)
                        )
                    }

                    // Strobe lines on vinyl
                    for (angle in 0 until 360 step 30) {
                        rotate(degrees = angle.toFloat(), pivot = centerPt) {
                            drawLine(
                                color = Color.White.copy(alpha = 0.15f),
                                start = Offset(centerPt.x, centerPt.y - radius + 4),
                                end = Offset(centerPt.x, centerPt.y - radius + 12),
                                strokeWidth = 3f
                            )
                        }
                    }
                }

                // Center Album Art or Music Icon
                if (track?.albumArtUrl?.isNotBlank() == true) {
                    AsyncImage(
                        model = track.albumArtUrl,
                        contentDescription = "Track Art",
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .border(2.dp, accentColor, CircleShape)
                            .rotate(if (isSpinning) rotationAngle else 0f)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                            .border(2.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "DJ",
                            color = Color.White,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 16.sp
                        )
                    }
                }

                // Spindle Hole
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Track Details
            Text(
                text = track?.title ?: "No track queued",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Text(
                text = track?.artist ?: "Select playlist to load",
                color = TextSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Track Meta Chips (BPM & Key & Source)
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (track != null) {
                    Surface(
                        color = DjSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, DjCardBorder, RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "⚡ ${track.bpm} BPM",
                            fontSize = 10.sp,
                            color = NeonAmber,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = DjSurface,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.border(1.dp, DjCardBorder, RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "🎵 ${track.source.name}",
                            fontSize = 10.sp,
                            color = if (track.source.name == "YOUTUBE") YouTubeRed else SoundCloudOrange,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}
