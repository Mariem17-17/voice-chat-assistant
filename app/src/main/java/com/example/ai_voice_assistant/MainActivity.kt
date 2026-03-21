package com.example.ai_voice_assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.ai_voice_assistant.ui.theme.AI_voice_assistantTheme
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.launch


class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AI_voice_assistantTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // We call our new AI Screen here
                    GeminiTestScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun GeminiTestScreen(modifier: Modifier = Modifier) {
    // 1. State management for the UI
    var aiResponse by remember { mutableStateOf("Press the button to talk to your PFE assistant.") }
    var isLoading by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // 2. Initialize the Gemini Model
    // Note: In a real PFE, you'd fetch the key from BuildConfig. 
    // For this first test, you can paste your key directly here to verify it works.
    val generativeModel = GenerativeModel(
        // Use a currently supported model id.
        modelName = "gemini-2.0-flash",
        apiKey = "AIzaSyDch0fJULuT4ewuURRI-132RxXlNrUs6G0"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 
        
        // 3. Response Display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = aiResponse,
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyLarge
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 4. Action Button
        Button(
            onClick = {
                coroutineScope.launch {
                    isLoading = true
                    aiResponse = "Thinking..."
                    try {
                        val response = generativeModel.generateContent("Hello, introduce yourself as my PFE assistant")
                        aiResponse = response.text ?: "The AI returned an empty response."
                    } catch (e: Exception) {
                        val message = e.message ?: "Unknown error"
                        aiResponse = if (message.contains("NOT_FOUND") || message.contains("404")) {
                            "Model not found for your API setup. Try another model id (for example gemini-2.0-flash-lite)."
                        } else {
                            "Error: $message"
                        }
                    } finally {
                        isLoading = false
                    }
                }
            },
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Text("Ask AI")
            }
        }
    }
}