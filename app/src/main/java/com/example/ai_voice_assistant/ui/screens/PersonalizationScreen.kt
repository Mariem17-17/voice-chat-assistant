package com.example.ai_voice_assistant.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_voice_assistant.data.UserSettings
import com.example.ai_voice_assistant.ui.components.GlassCard
import com.example.ai_voice_assistant.ui.theme.*

@Composable
fun PersonalizationScreen(
    settings: UserSettings,
    tts: TextToSpeech?,
    onBack: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onPersonaChange: (String) -> Unit,
    onVoiceChange: (String) -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit,
    onDeleteHistory: () -> Unit,
    onLogout: () -> Unit,
    onDeleteAccount: () -> Unit
) {
    val languages = listOf(
        "English" to "en-US",
        "Français" to "fr-FR",
        "العربية" to "ar-SA"
    )
    
    var showLangMenu by remember { mutableStateOf(false) }
    var showVoiceMenu by remember { mutableStateOf(false) }
    var showDeleteHistoryDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }

    // Filter voices based on selected language
    val availableVoices = remember(tts, settings.languageTag) {
        tts?.voices?.filter { voice ->
            voice.locale.toLanguageTag().startsWith(settings.languageTag.split("-")[0])
        }?.sortedBy { it.name } ?: emptyList()
    }

    if (showDeleteHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteHistoryDialog = false },
            containerColor = NavyBlue,
            title = { Text("Delete All History?", color = Color.White) },
            text = { Text("This will permanently remove all chat logs. This cannot be undone.", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = { onDeleteHistory(); showDeleteHistoryDialog = false }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteHistoryDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    if (showDeleteAccountDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAccountDialog = false },
            containerColor = NavyBlue,
            title = { Text("Delete My Account?", color = Color.White) },
            text = { Text("This is permanent. All your data will be erased in compliance with GDPR.", color = Color.White.copy(alpha = 0.7f)) },
            confirmButton = {
                TextButton(onClick = { onDeleteAccount(); showDeleteAccountDialog = false }) {
                    Text("Delete Permanently", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAccountDialog = false }) {
                    Text("Cancel", color = Color.White)
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = Brush.radialGradient(colors = listOf(BackgroundStart, BackgroundEnd)))
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Settings", style = MaterialTheme.typography.headlineMedium.copy(color = Color.White, fontWeight = FontWeight.Bold))
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                item {
                    Text(text = "Voice Selection", style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary))
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            val currentVoiceDisplay = if (settings.selectedVoiceName.isNotEmpty()) {
                                settings.selectedVoiceName.split("#").last()
                            } else {
                                "Default Voice"
                            }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showVoiceMenu = true }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = currentVoiceDisplay, color = Color.White)
                                Icon(Icons.Default.KeyboardArrowDown, "Select", tint = Color.White)
                            }
                            
                            DropdownMenu(
                                expanded = showVoiceMenu,
                                onDismissRequest = { showVoiceMenu = false },
                                modifier = Modifier
                                    .background(NavyBlue.copy(alpha = 0.9f))
                                    .fillMaxWidth(0.8f)
                            ) {
                                if (availableVoices.isEmpty()) {
                                    DropdownMenuItem(
                                        text = { Text("No voices available", color = Color.White) },
                                        onClick = { showVoiceMenu = false }
                                    )
                                } else {
                                    availableVoices.forEach { voice ->
                                        val gender = if (voice.name.contains("female", ignoreCase = true)) "Female" else "Male"
                                        DropdownMenuItem(
                                            text = { 
                                                Column {
                                                    Text(voice.name.split("#").last(), color = Color.White)
                                                    Text(gender, color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                                }
                                            },
                                            onClick = {
                                                onVoiceChange(voice.name)
                                                showVoiceMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item {
                    Text(text = "Voice Personalization", style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary))
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Pitch Slider
                            Text(text = "Pitch: ${"%.1f".format(settings.pitch)}", color = Color.White, fontSize = 14.sp)
                            Slider(
                                value = settings.pitch,
                                onValueChange = { onPitchChange(it) },
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonPink,
                                    activeTrackColor = NeonPink,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Speed Slider
                            Text(text = "Speech Rate: ${"%.1f".format(settings.speechRate)}", color = Color.White, fontSize = 14.sp)
                            Slider(
                                value = settings.speechRate,
                                onValueChange = { onRateChange(it) },
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonOrange,
                                    activeTrackColor = NeonOrange,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                                )
                            )
                        }
                    }
                }

                item {
                    Text(text = "Language", style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary))
                    GlassCard(modifier = Modifier.fillMaxWidth()) {
                        Box(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { showLangMenu = true }
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = languages.find { it.second == settings.languageTag }?.first ?: "English", color = Color.White)
                                Icon(Icons.Default.KeyboardArrowDown, "Select", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showLangMenu,
                                onDismissRequest = { showLangMenu = false },
                                modifier = Modifier.background(NavyBlue.copy(alpha = 0.9f))
                            ) {
                                languages.forEach { (name, tag) ->
                                    DropdownMenuItem(
                                        text = { Text(name, color = Color.White) },
                                        onClick = { onLanguageChange(tag); showLangMenu = false }
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    Text(text = "Account & Privacy", style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onLogout,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Logout, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Logout", fontWeight = FontWeight.Bold)
                        }

                        Button(
                            onClick = { showDeleteHistoryDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.1f), contentColor = Color.Red),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.DeleteForever, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Clear Chat History", fontWeight = FontWeight.Bold)
                        }

                        OutlinedButton(
                            onClick = { showDeleteAccountDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.PersonRemove, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Delete My Account", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
