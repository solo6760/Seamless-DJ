package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.Track
import com.example.ui.theme.*

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun YouTubePlayerWidget(
    track: Track?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    if (track == null) return

    val videoId = remember(track.sourceUrl, track.id) {
        extractYouTubeVideoId(track.sourceUrl)
    }

    val embedUrl = remember(videoId, track.sourceUrl, isPlaying) {
        when {
            track.sourceUrl.contains("list=") -> {
                val listId = track.sourceUrl.substringAfter("list=").substringBefore("&")
                val indexParam = if (track.sourceUrl.contains("index=")) {
                    "&index=" + track.sourceUrl.substringAfter("index=").substringBefore("&")
                } else ""
                "https://www.youtube.com/embed/videoseries?list=$listId$indexParam&autoplay=${if (isPlaying) 1 else 0}&enablejsapi=1&playsinline=1"
            }
            videoId.isNotBlank() -> {
                "https://www.youtube.com/embed/$videoId?autoplay=${if (isPlaying) 1 else 0}&enablejsapi=1&playsinline=1&controls=1"
            }
            else -> {
                "https://www.youtube.com/embed/5qap5aO4i9A?autoplay=1&playsinline=1"
            }
        }
    }

    val htmlContent = remember(embedUrl) {
        """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <style>
                body { margin: 0; padding: 0; background-color: #0d0a14; display: flex; justify-content: center; align-items: center; height: 100vh; overflow: hidden; }
                iframe { width: 100%; height: 100%; border: none; }
            </style>
        </head>
        <body>
            <iframe id="ytplayer" type="text/html" src="$embedUrl"
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                allowfullscreen></iframe>
        </body>
        </html>
        """.trimIndent()
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, YouTubeRed, RoundedCornerShape(16.dp))
            .testTag("youtube_embedded_player_card"),
        colors = CardDefaults.cardColors(containerColor = DjSurface)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DjSurfaceVariant)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.VideoLibrary,
                        contentDescription = "YouTube",
                        tint = YouTubeRed,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE YOUTUBE MEDIA EMBED",
                        color = YouTubeRed,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = track.title,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 180.dp)
                )
            }

            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            domStorageEnabled = true
                            databaseEnabled = true
                            allowFileAccess = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString = "Mozilla/5.0 (Linux; Android 10; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Mobile Safari/537.36"
                        }
                        webChromeClient = WebChromeClient()
                        webViewClient = WebViewClient()
                        loadDataWithBaseURL("https://www.youtube.com", htmlContent, "text/html", "UTF-8", null)
                    }
                },
                update = { webView ->
                    if (webView.tag != htmlContent) {
                        webView.tag = htmlContent
                        webView.loadDataWithBaseURL("https://www.youtube.com", htmlContent, "text/html", "UTF-8", null)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

fun extractYouTubeVideoId(url: String): String {
    if (url.isBlank()) return ""
    return when {
        url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
        url.contains("youtu.be/") -> url.substringAfter("youtu.be/").substringBefore("?").substringBefore("&")
        url.contains("embed/") -> url.substringAfter("embed/").substringBefore("?").substringBefore("&")
        else -> ""
    }
}
