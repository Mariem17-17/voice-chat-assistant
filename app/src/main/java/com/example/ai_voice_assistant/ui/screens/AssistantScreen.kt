package com.example.ai_voice_assistant.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ai_voice_assistant.ui.components.GlassCard
import com.example.ai_voice_assistant.ui.theme.*

@Composable
fun AssistantScreen(
    aiResponse: String,
    recognizedText: String,
    isLoading: Boolean,
    isSpeaking: Boolean,
    onSpeak: () -> Unit,
    onStopSpeaking: () -> Unit,
    onResetMemory: () -> Unit,
    currentLanguage: String,
    onLanguageChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    onSendMessage: (String) -> Unit = {}
) {
    var messageText by remember { mutableStateOf("") }
    var showLanguageDropdown by remember { mutableStateOf(false) }
    val keyboardController = LocalSoftwareKeyboardController.current
    
    val languages = listOf(
        "English" to "en-US",
        "Français" to "fr-FR",
        "العربية" to "ar-SA"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        BackgroundStart,
                        BackgroundEnd
                    )
                )
            )
    ) {
        // Main Scrollable Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(80.dp)) // Space for language selector

            // AI Response Glass Card - Clear White Text
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 20.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        text = "AI Assistant",
                        style = MaterialTheme.typography.titleMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = aiResponse,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color.White,
                            lineHeight = 24.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                }
            }

            // User Input Glass Card
            GlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "You said:",
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = TextSecondary,
                            fontWeight = FontWeight.Medium
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = recognizedText,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Control Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Microphone Button
                Box(
                    modifier = Modifier
                        .size(70.dp)
                        .shadow(
                            elevation = 20.dp,
                            shape = CircleShape,
                            ambientColor = NeonPink.copy(alpha = 0.5f),
                            spotColor = NeonOrange.copy(alpha = 0.5f)
                        )
                        .background(
                            brush = Brush.linearGradient(colors = NeonGradient),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = onSpeak,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(30.dp),
                                color = Color.White,
                                strokeWidth = 3.dp
                            )
                        } else {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Default.Stop else Icons.Default.Mic,
                                contentDescription = if (isSpeaking) "Stop" else "Microphone",
                                tint = Color.White,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                }

                if (isSpeaking || isLoading) {
                    Spacer(modifier = Modifier.width(20.dp))
                    // Stop Button in its own clear container
                    GlassCard(
                        cornerRadius = 50.dp,
                        modifier = Modifier.size(60.dp)
                    ) {
                        IconButton(
                            onClick = onStopSpeaking,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Stop",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Reset Button - Transparent with border
            OutlinedButton(
                onClick = onResetMemory,
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("Reset Conversation", fontSize = 14.sp)
            }

            Spacer(modifier = Modifier.height(120.dp)) // Space for bottom input
        }

        // Language Selector - Top Right
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 48.dp, end = 16.dp)
                .zIndex(2f)
        ) {
            GlassCard(
                cornerRadius = 12.dp
            ) {
                IconButton(
                    onClick = { showLanguageDropdown = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Language",
                        tint = Color.White
                    )
                }
            }
            
            DropdownMenu(
                expanded = showLanguageDropdown,
                onDismissRequest = { showLanguageDropdown = false },
                modifier = Modifier.background(NavyBlue.copy(alpha = 0.9f))
            ) {
                languages.forEach { (name, tag) ->
                    DropdownMenuItem(
                        text = { Text(name, color = Color.White) },
                        onClick = {
                            onLanguageChange(tag)
                            showLanguageDropdown = false
                        }
                    )
                }
            }
        }

        // Bottom Text Input - Translucent and slim
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(16.dp)
                .padding(bottom = 16.dp) // Extra padding from bottom
                .zIndex(3f)
        ) {
            GlassCard(
                cornerRadius = 24.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { 
                            Text("Type a message...", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) 
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = NeonPink
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (messageText.isNotBlank()) {
                                    onSendMessage(messageText)
                                    messageText = ""
                                    keyboardController?.hide()
                                }
                            }
                        ),
                        singleLine = true
                    )
                    
                    IconButton(
                        onClick = {
                            if (messageText.isNotBlank()) {
                                onSendMessage(messageText)
                                messageText = ""
                                keyboardController?.hide()
                            }
                        },
                        enabled = messageText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (messageText.isNotBlank()) NeonPink else Color.White.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}
