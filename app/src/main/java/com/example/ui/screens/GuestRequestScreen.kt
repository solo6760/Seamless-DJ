package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.ui.DjViewModel
import com.example.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuestRequestScreen(
    viewModel: DjViewModel,
    onBackToDeck: () -> Unit,
    modifier: Modifier = Modifier
) {
    val requests by viewModel.guestRequests.collectAsState()
    val settings by viewModel.settings.collectAsState()

    var songTitle by remember { mutableStateOf("") }
    var artistName by remember { mutableStateOf("") }
    var sourceUrl by remember { mutableStateOf("") }
    var guestName by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Guest Song Requests (${settings.partyRoomCode})",
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 16.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackToDeck,
                        modifier = Modifier.testTag("back_to_deck_from_requests")
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                // Room Code Banner
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DjSurfaceVariant),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = NeonCyan.copy(alpha = 0.2f),
                            shape = CircleShape,
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Room QR",
                                    tint = NeonCyan
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column {
                            Text(
                                text = "PARTY ROOM CODE: ${settings.partyRoomCode}",
                                fontWeight = FontWeight.Black,
                                color = TextPrimary,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Guests can request songs from YouTube & SoundCloud and vote them up!",
                                color = TextSecondary,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            item {
                // Request Form
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = DjSurface),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "REQUEST A SONG FOR THE DJ MIX",
                            fontWeight = FontWeight.Bold,
                            color = NeonMagenta,
                            fontSize = 13.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        OutlinedTextField(
                            value = songTitle,
                            onValueChange = { songTitle = it },
                            label = { Text("Song Title *", color = TextSecondary) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("request_title_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DjSurfaceVariant,
                                unfocusedContainerColor = DjSurfaceVariant,
                                focusedBorderColor = NeonMagenta,
                                unfocusedBorderColor = DjCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = artistName,
                            onValueChange = { artistName = it },
                            label = { Text("Artist Name", color = TextSecondary) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("request_artist_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DjSurfaceVariant,
                                unfocusedContainerColor = DjSurfaceVariant,
                                focusedBorderColor = NeonMagenta,
                                unfocusedBorderColor = DjCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = sourceUrl,
                            onValueChange = { sourceUrl = it },
                            label = { Text("YouTube / SoundCloud Link (Optional)", color = TextSecondary) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("request_url_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DjSurfaceVariant,
                                unfocusedContainerColor = DjSurfaceVariant,
                                focusedBorderColor = NeonMagenta,
                                unfocusedBorderColor = DjCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = guestName,
                            onValueChange = { guestName = it },
                            label = { Text("Your Name / Alias", color = TextSecondary) },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("request_guest_name_input"),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = DjSurfaceVariant,
                                unfocusedContainerColor = DjSurfaceVariant,
                                focusedBorderColor = NeonMagenta,
                                unfocusedBorderColor = DjCardBorder,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary
                            )
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Button(
                            onClick = {
                                if (songTitle.isNotBlank()) {
                                    viewModel.addGuestRequest(songTitle, artistName, sourceUrl, guestName)
                                    songTitle = ""
                                    artistName = ""
                                    sourceUrl = ""
                                    guestName = ""
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("submit_request_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = NeonMagenta)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Submit Request")
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("SUBMIT SONG REQUEST", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }
            }

            item {
                Text(
                    text = "GUEST REQUEST QUEUE (MOST UPVOTED FIRST)",
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            if (requests.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = DjSurface)
                    ) {
                        Text(
                            text = "No guest requests yet. Be the first to request a song above!",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(requests) { req ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("request_item_${req.id}"),
                        colors = CardDefaults.cardColors(containerColor = DjSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = CardDefaults.outlinedCardBorder()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = req.track.title,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${req.track.artist} • Requested by ${req.requestedBy}",
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }

                            // Upvote Button
                            Button(
                                onClick = { viewModel.upvoteGuestRequest(req.id) },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = DjSurfaceVariant),
                                modifier = Modifier.testTag("upvote_button_${req.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ThumbUp,
                                    contentDescription = "Upvote",
                                    tint = NeonAmber,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "+${req.upvotes}",
                                    fontWeight = FontWeight.Bold,
                                    color = NeonAmber,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
