package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*

@Composable
fun ApiKeyOnboardingDialog(
    onSaveKey: (key: String, onResult: (Boolean, String?) -> Unit) -> Unit,
    onSkip: () -> Unit
) {
    var apiKeyInput by remember { mutableStateOf("") }
    var isValidating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showKeyText by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { /* Require user interaction */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .border(2.dp, NeonViolet, RoundedCornerShape(24.dp))
                .testTag("api_key_onboarding_dialog"),
            shape = RoundedCornerShape(24.dp),
            color = DjSurface
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Surface(
                    color = NeonViolet.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Gemini AI",
                            tint = NeonViolet,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "SEAMLESS PARTY DJ SETUP",
                    fontWeight = FontWeight.Black,
                    color = TextPrimary,
                    fontSize = 18.sp,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Enter your Gemini API key to enable live Google Search grounded BPM lookups and automatic beat-synced party mixing!",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Error Banner
                AnimatedVisibility(visible = errorMessage != null) {
                    errorMessage?.let { err ->
                        Surface(
                            color = Color(0xFF3E1212),
                            shape = RoundedCornerShape(10.dp),
                            border = CardDefaults.outlinedCardBorder(),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = "⚠️ $err",
                                color = Color(0xFFFF6B6B),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }

                // API Key Text Input
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = {
                        apiKeyInput = it
                        errorMessage = null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("api_key_text_input"),
                    label = { Text("Gemini API Key", color = TextSecondary) },
                    placeholder = { Text("AIzaSy...", color = TextMuted) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Key, contentDescription = "Key", tint = NeonViolet)
                    },
                    trailingIcon = {
                        IconButton(onClick = { showKeyText = !showKeyText }) {
                            Icon(
                                imageVector = if (showKeyText) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle Visibility",
                                tint = TextSecondary
                            )
                        }
                    },
                    visualTransformation = if (showKeyText) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = NeonViolet,
                        unfocusedBorderColor = DjCardBorder,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Save & Validate Button
                Button(
                    onClick = {
                        if (apiKeyInput.isBlank()) {
                            errorMessage = "Please enter or paste a valid Gemini API Key."
                            return@Button
                        }
                        isValidating = true
                        errorMessage = null
                        onSaveKey(apiKeyInput) { success, err ->
                            isValidating = false
                            if (!success) {
                                errorMessage = err ?: "Invalid API key. Please check and try again."
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("save_api_key_button"),
                    enabled = !isValidating,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = NeonViolet)
                ) {
                    if (isValidating) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("VALIDATING KEY...", fontWeight = FontWeight.Bold)
                    } else {
                        Text("SAVE & ACTIVATE BPM LOOKUP", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Skip / Set Up Later
                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.testTag("skip_api_key_button")
                ) {
                    Text("Skip for Now (Manual BPM Mode)", color = TextSecondary, fontSize = 12.sp)
                }
            }
        }
    }
}
