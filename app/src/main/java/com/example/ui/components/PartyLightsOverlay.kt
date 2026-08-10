package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonMagenta
import com.example.ui.theme.NeonViolet

@Composable
fun PartyLightsOverlay(
    isEnabled: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    if (!isEnabled) return

    val infiniteTransition = rememberInfiniteTransition(label = "PartyLights")

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.05f,
        targetValue = if (isPlaying) 0.18f else 0.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val lightShiftX by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lightShiftX"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height

        // Top Left Magenta Glowing Light
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonMagenta.copy(alpha = pulseAlpha),
                    Color.Transparent
                ),
                center = Offset(w * lightShiftX, h * 0.15f),
                radius = w * 0.7f
            )
        )

        // Bottom Right Cyan Glowing Light
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonCyan.copy(alpha = pulseAlpha * 0.9f),
                    Color.Transparent
                ),
                center = Offset(w * (1f - lightShiftX), h * 0.75f),
                radius = w * 0.8f
            )
        )

        // Center Violet Beat Aura
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    NeonViolet.copy(alpha = pulseAlpha * 0.6f),
                    Color.Transparent
                ),
                center = Offset(w * 0.5f, h * 0.45f),
                radius = w * 0.5f
            )
        )
    }
}
