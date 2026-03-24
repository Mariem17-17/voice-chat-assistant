package com.example.ai_voice_assistant.ui.screens

import android.speech.tts.TextToSpeech
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ai_voice_assistant.data.UserSettings
import com.example.ai_voice_assistant.ui.components.GlassCard
import com.example.ai_voice_assistant.ui.theme.*
import java.util.Locale

@Composable
fun PersonalizationScreen(
    settings: UserSettings,
    @Suppress("UNUSED_PARAMETER") tts: TextToSpeech?,
    onBack: () -> Unit,
    onLanguageChange: (String) -> Unit,
    onPersonaChange: (String) -> Unit,
    onRateChange: (Float) -> Unit,
    onPitchChange: (Float) -> Unit
) {
    val languages = listOf(
        "English" to "en-US",
        "Français" to "fr-FR",
        "العربية" to "ar-SA"
    )
    
    var showLangMenu by remember { mutableStateOf(false) }

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
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp, bottom = 24.dp),
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

            // Language Section
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
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = languages.find { it.second == settings.languageTag }?.first ?: "English",
                            color = Color.White
                        )
                        IconButton(onClick = { showLangMenu = true }) {
                            Icon(Icons.Default.KeyboardArrowDown, "Select", tint = Color.White)
                        }
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

            Spacer(modifier = Modifier.height(24.dp))

            // Voice Persona Section
            Text(
                text = "Voice Persona",
                style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                listOf("Male", "Female").forEach { persona ->
                    val isSelected = settings.voicePersona == persona
                    FilterChip(
                        selected = isSelected,
                        onClick = { onPersonaChange(persona) },
                        label = { Text(persona) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = Color.Transparent,
                            selectedContainerColor = NeonPink.copy(alpha = 0.3f),
                            labelColor = Color.White.copy(alpha = 0.7f),
                            selectedLabelColor = Color.White
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = Color.White.copy(alpha = 0.2f),
                            selectedBorderColor = NeonPink
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Audio Controls
            Text(
                text = "Speech Rate (${String.format(Locale.US, "%.1fx", settings.speechRate)})",
                style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = settings.speechRate,
                onValueChange = onRateChange,
                valueRange = 0.5f..2.0f,
                colors = SliderDefaults.colors(
                    thumbColor = NeonPink,
                    activeTrackColor = NeonPink,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Pitch (${String.format(Locale.US, "%.1fx", settings.pitch)})",
                style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary),
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Slider(
                value = settings.pitch,
                onValueChange = onPitchChange,
                valueRange = 0.5f..2.0f,
                colors = SliderDefaults.colors(
                    thumbColor = NeonOrange,
                    activeTrackColor = NeonOrange,
                    inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                )
            )
            
            Spacer(modifier = Modifier.height(100.dp))
        }
    }
}
