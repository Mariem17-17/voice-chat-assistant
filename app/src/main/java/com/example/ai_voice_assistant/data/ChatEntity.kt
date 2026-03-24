package com.example.ai_voice_assistant.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_history")
data class ChatEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userPrompt: String,
    val aiResponse: String,
    val timestamp: Long = System.currentTimeMillis()
)
