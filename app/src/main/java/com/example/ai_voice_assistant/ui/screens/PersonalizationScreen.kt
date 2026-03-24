package com.example.ai_voice_assistant.ui.screens

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_voice_assistant.data.UserSettings
import com.example.ai_voice_assistant.ui.components.GlassCard
import com.example.ai_voice_assistant.ui.theme.*
import java.util.Locale

@Composable
fun PersonalizationScreen(
    settings: UserSettings,
    tts: TextToSpeech?,
    onBack: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onPersonaChange: (String) -> Unit,
    onVoiceChange: (String) -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit
) {
    val languages = listOf(
        "English" to "en-US",
        "Français" to "fr-FR",
        "العربية" to "ar-SA"
    )
    
    var showLangMenu by remember { mutableStateOf(false) }
    
    // Get and filter available voices based on current language
    val availableVoices = remember(tts, settings.languageTag) {
        tts?.voices?.filter { voice ->
            voice.locale.toLanguageTag().startsWith(settings.languageTag.split("-")[0])
        }?.sortedBy { it.name } ?: emptyList()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(BackgroundStart, BackgroundEnd)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Personalization",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            // Scrollable Content
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                // Language Section
                item {
                    Text(
                        text = "Language",
                        style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
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
                                Text(
                                    text = languages.find { it.second == settings.languageTag }?.first ?: "English",
                                    color = Color.White
                                )
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
                                        onClick = {
                                            onLanguageChange(tag)
                                            showLangMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                // Voice Selection Section (Transaction List Style)
                item {
                    Text(
                        text = "Available Voices",
                        style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                
                items(availableVoices) { voice ->
                    val isSelected = settings.selectedVoiceName == voice.name
                    VoiceItem(
                        voice = voice,
                        isSelected = isSelected,
                        onClick = { onVoiceChange(voice.name) }
                    )
                }

                // Audio Controls Section
                item {
                    Text(
                        text = "Speech Controls",
                        style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary),
                        modifier = Modifier.padding(top = 8.dp, bottom = 8.dp)
                    )
                    GlassCard {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Rate: ${String.format(Locale.US, "%.1fx", settings.speechRate)}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Slider(
                                value = settings.speechRate,
                                onValueChange = onRateChange,
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonPink,
                                    activeTrackColor = NeonPink
                                )
                            )
                            
                            Spacer(modifier = Modifier.height(16.dp))
                            
                            Text(
                                text = "Pitch: ${String.format(Locale.US, "%.1fx", settings.pitch)}",
                                color = Color.White,
                                style = MaterialTheme.typography.labelLarge
                            )
                            Slider(
                                value = settings.pitch,
                                onValueChange = onPitchChange,
                                valueRange = 0.5f..2.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonOrange,
                                    activeTrackColor = NeonOrange
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceItem(
    voice: Voice,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Voice Icon (Circle Background)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        brush = Brush.linearGradient(
                            colors = if (isSelected) NeonGradient else listOf(Color.White.copy(alpha = 0.1f), Color.White.copy(alpha = 0.1f))
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.RecordVoiceOver,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = voice.name.substringAfterLast("."),
                    style = MaterialTheme.typography.bodyLarge.copy(
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                )
                Text(
                    text = if (voice.isNetworkConnectionRequired) "Network Required" else "Offline",
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = TextSecondary
                    )
                )
            }
            
            if (isSelected) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = "Selected",
                    tint = NeonPink,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
