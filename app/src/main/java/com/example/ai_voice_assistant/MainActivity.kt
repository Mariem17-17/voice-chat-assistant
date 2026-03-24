package com.example.ai_voice_assistant

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ai_voice_assistant.ui.navigation.BottomNavigationBar
import com.example.ai_voice_assistant.ui.navigation.BottomNavItem
import com.example.ai_voice_assistant.ui.screens.HistoryScreen
import com.example.ai_voice_assistant.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val systemPrompt = "You are a multilingual assistant. Always respond in the user's language (English, French, or Arabic). For actions, ALWAYS keep tags in English and never translate anything inside brackets. Valid tags are: [ACTION_ALARM:HH:mm], [ACTION_CALL:number], [ACTION_NOTE:text], [ACTION_YOUTUBE:search query], and [ACTION_CAMERA]. If an action is requested, start your reply with the correct action tag."
    private val chatHistory = mutableListOf<Message>()
    private var textToSpeech: TextToSpeech? = null

    var isTtsReady by mutableStateOf(false)
        private set
    var isTtsSpeaking by mutableStateOf(false)
        private set
    var currentLanguageTag by mutableStateOf("en-US")
    var isLoading by mutableStateOf(false)

    private val groqApi: GroqApi by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.groq.com/openai/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApi::class.java)
    }
    
    suspend fun sendToGroq(userPrompt: String): String {
        isLoading = true
        try {
            chatHistory.add(Message(role = "user", content = userPrompt))
            trimHistoryToTenMessages()

            val request = GroqRequest(
                model = "llama-3.3-70b-versatile",
                messages = chatHistory.toList()
            )
            val response = groqApi.getCompletion(
                authorization = "Bearer ${BuildConfig.GROQ_API_KEY}",
                request = request
            )
            val assistantText = response.choices.firstOrNull()?.message?.content
                ?: "The AI returned an empty response."

            chatHistory.add(Message(role = "assistant", content = assistantText))
            trimHistoryToTenMessages()
            return assistantText
        } finally {
            isLoading = false
        }
    }
    
    fun resetMemory() {
        chatHistory.clear()
        chatHistory.add(Message(role = "system", content = systemPrompt))
    }
    
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    
    fun handleSpeechResult(result: androidx.activity.result.ActivityResult, onResult: (String, String) -> Unit) {
        if (result.resultCode == RESULT_OK) {
            val spokenText = result.data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                coroutineScope.launch {
                    val response = sendToGroq(spokenText)
                    val cleanedResponse = handleSystemAction(response)
                    onResult(spokenText, cleanedResponse)
                    speak(cleanedResponse)
                }
            }
        }
    }
    
    fun handleSystemAction(response: String): String {
        val processedResponse = response

        // Call Action
        val callMatch = Regex("""\[ACTION_CALL:([^\]]+)]""").find(processedResponse)
        if (callMatch != null) {
            val number = callMatch.groupValues[1].trim()
            if (number.isNotBlank()) {
                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Call failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // Alarm Action
        val alarmMatch = Regex("""\[ACTION_ALARM:(\d{1,2}):(\d{2})]""").find(processedResponse)
        if (alarmMatch != null) {
            val hour = alarmMatch.groupValues[1].toIntOrNull()
            val minutes = alarmMatch.groupValues[2].toIntOrNull()
            if (hour != null && minutes != null) {
                val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minutes)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                }
                try {
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "Alarm failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        // YouTube Action
        val youtubeMatch = Regex("""\[ACTION_YOUTUBE:(.*?)\]""").find(processedResponse)
        if (youtubeMatch != null) {
            val query = youtubeMatch.groupValues[1].trim()
            val intent = Intent(Intent.ACTION_SEARCH).apply {
                setPackage("com.google.android.youtube")
                putExtra("query", query)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            try {
                startActivity(intent)
            } catch (e: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query")))
            }
        }

        // Camera Action
        if (processedResponse.contains("[ACTION_CAMERA]")) {
            val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Camera failed", Toast.LENGTH_SHORT).show()
            }
        }

        return processedResponse
            .replace(Regex("""\[ACTION_[A-Z_]+:[^\]]+]"""), "")
            .replace(Regex("""\[ACTION_CAMERA]"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }
    
    fun speak(text: String) {
        if (!isTtsReady || text.isBlank()) return
        textToSpeech?.apply {
            language = Locale.forLanguageTag(currentLanguageTag)
            speak(text, TextToSpeech.QUEUE_FLUSH, null, "groq_reply")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()

        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) { isTtsSpeaking = true }
                    override fun onDone(utteranceId: String?) { isTtsSpeaking = false }
                    override fun onError(utteranceId: String?) { isTtsSpeaking = false }
                })
            }
        }

        enableEdgeToEdge()

        setContent {
            AI_voice_assistantTheme {
                val navController = rememberNavController()
                Scaffold(
                    bottomBar = {
                        BottomNavigationBar(navController = navController)
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        NavHost(
                            navController = navController,
                            startDestination = BottomNavItem.Assistant.route
                        ) {
                            composable(BottomNavItem.Assistant.route) {
                                AssistantScreenState()
                            }
                            composable(BottomNavItem.History.route) {
                                HistoryScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        textToSpeech?.stop()
        textToSpeech?.shutdown()
        super.onDestroy()
    }

    private fun trimHistoryToTenMessages() {
        ensureSystemPromptAtTop()
        while (chatHistory.size > 10) {
            val removeIndex = if (chatHistory.size > 1 && chatHistory[0].role == "system") 1 else 0
            chatHistory.removeAt(removeIndex)
        }
    }

    private fun ensureSystemPromptAtTop() {
        if (chatHistory.isEmpty() || chatHistory[0].role != "system") {
            chatHistory.add(0, Message(role = "system", content = systemPrompt))
        }
    }

    fun onStopSpeaking() {
        textToSpeech?.stop()
        isTtsSpeaking = false
    }
}
