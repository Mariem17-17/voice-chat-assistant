package com.example.ai_voice_assistant.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai_voice_assistant.ui.components.GlassCard
import com.example.ai_voice_assistant.ui.theme.*

data class ChatHistoryItem(
    val id: String,
    val userMessage: String,
    val aiResponse: String,
    val timestamp: String
)

@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier
) {
    // Sample data - replace with actual chat history
    val sampleHistory = listOf(
        ChatHistoryItem(
            id = "1",
            userMessage = "Set an alarm for 7am",
            aiResponse = "I have set your alarm for 7:00 AM.",
            timestamp = "10:30 AM"
        ),
        ChatHistoryItem(
            id = "2", 
            userMessage = "Take a note buy milk and bread",
            aiResponse = "I saved your note: buy milk and bread",
            timestamp = "10:25 AM"
        ),
        ChatHistoryItem(
            id = "3",
            userMessage = "Call mom",
            aiResponse = "Opening dialer for your mom...",
            timestamp = "10:20 AM"
        )
    )

    Column(
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = "History",
                    tint = TextPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = "Chat History",
                    style = MaterialTheme.typography.titleMedium.copy(
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                )
            }
        }

        // History List
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sampleHistory) { item ->
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                text = "You:",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextSecondary,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                            Text(
                                text = item.timestamp,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    color = TextSecondary
                                )
                            )
                        }
                        Text(
                            text = item.userMessage,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            ),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        
                        Divider(
                            modifier = Modifier.padding(vertical = 8.dp),
                            color = GlassBorder.copy(alpha = 0.3f)
                        )
                        
                        Text(
                            text = "AI:",
                            style = MaterialTheme.typography.labelSmall.copy(
                                color = TextSecondary,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        Text(
                            text = item.aiResponse,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                color = TextPrimary
                            ),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
