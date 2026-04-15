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
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ai_voice_assistant.data.ChatDatabase
import com.example.ai_voice_assistant.data.ChatEntity
import com.example.ai_voice_assistant.data.SettingsDataStore
import com.example.ai_voice_assistant.data.UserSettings
import com.example.ai_voice_assistant.ui.navigation.BottomNavigationBar
import com.example.ai_voice_assistant.ui.navigation.BottomNavItem
import com.example.ai_voice_assistant.ui.screens.AssistantScreenState
import com.example.ai_voice_assistant.ui.screens.HistoryScreen
import com.example.ai_voice_assistant.ui.screens.PersonalizationScreen
import com.example.ai_voice_assistant.ui.theme.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import android.provider.ContactsContract

class MainActivity : ComponentActivity() {
    private val systemPrompt = """
    STRICT RULE: Match the user's language. If the user speaks English, respond 100% in English. If the user speaks French, respond 100% in French. Do not switch languages based on profile data.
    You are a multilingual assistant. Always respond in the user's language.
    Be pedagogical: when a system action is triggered, briefly explain what was prepared and what happens next.
    Exception for alarms: for [ACTION_ALARM], confirm that the alarm is already set (example FR: "C'est noté, j'ai programmé votre alarme pour 8h00."). Do NOT ask for manual verification for alarms.
    For other actions like [ACTION_SMS], [ACTION_ADD_CONTACT], and [ACTION_CALL], keep asking the user to verify/confirm with the opened interface.
    Examples:
    - Call confirmation: "Je prépare l'appel pour vous, il ne vous reste plus qu'à appuyer sur le bouton vert."
    - Contact confirmation: "La fiche contact est prête avec les informations demandées. Vérifiez-les et cliquez sur enregistrer."
    For actions, ALWAYS start your reply with a tag.
    Valid tags:
    - [ACTION_ALARM:HH:mm]
    - [ACTION_CALL:number] 
    - [ACTION_SMS:number:message]
    - [ACTION_NOTE:text]
    - [ACTION_ADD_CONTACT:name:number]
    - [ACTION_YOUTUBE:query]
    - [ACTION_CAMERA]
""".trimIndent()
    private val chatHistory = mutableListOf<Message>()
    private var textToSpeech: TextToSpeech? = null

    var isTtsReady by mutableStateOf(false)
        private set
    var isTtsSpeaking by mutableStateOf(false)
        private set
    var isLoading by mutableStateOf(false)

    private val database by lazy { ChatDatabase.getDatabase(this) }
    private val chatDao by lazy { database.chatDao() }
    private val settingsDataStore by lazy { SettingsDataStore(this) }

    var currentSettings by mutableStateOf(UserSettings("en-US", "Female", "", 1.0f, 1.0f))
        private set

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

            lifecycleScope.launch(Dispatchers.IO) {
                chatDao.insertChat(ChatEntity(userPrompt = userPrompt, aiResponse = assistantText))
            }

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
                launchSystemIntentWithDelay {
                    startActivity(intent)
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
                launchSystemIntentWithDelay {
                    startActivity(intent)
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
                launchSystemIntentWithDelay {
                    startActivity(intent)
                }
            } catch (e: Exception) {
                launchSystemIntentWithDelay {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query")))
                }
            }
        }
        // Note Action (Local Persistence)
        val noteMatch = Regex("""\[ACTION_NOTE:(.*?)\]""").find(processedResponse)
        if (noteMatch != null) {
            val noteText = noteMatch.groupValues[1].trim()
            // Le message de l'IA s'affiche dans le chat, puis on confirme localement.
            launchSystemIntentWithDelay {
                Toast.makeText(this, "Note enregistrée : $noteText", Toast.LENGTH_LONG).show()
            }
            // TODO: chatViewModel.saveNote(noteText)
        }
/*
        // Flashlight Action
        val lightMatch = Regex("""\[ACTION_LIGHT:(ON|OFF)\]""").find(processedResponse)
        if (lightMatch != null) {
            val state = lightMatch.groupValues[1] == "ON"
            try {
                val cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val cameraId = cameraManager.cameraIdList[0]
                cameraManager.setTorchMode(cameraId, state)
            } catch (e: Exception) {
                Toast.makeText(this, "Flashlight failed", Toast.LENGTH_SHORT).show()
            }
        }
*/
        // SMS Action
        val smsMatch = Regex("""\[ACTION_SMS:([^:]+):(.+?)\]""").find(processedResponse)
        if (smsMatch != null) {
            val number = smsMatch.groupValues[1].trim()
            val message = smsMatch.groupValues[2].trim()
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("smsto:$number")
                putExtra("sms_body", message)
            }
            launchSystemIntentWithDelay {
                startActivity(intent)
            }
        }

        // Add Contact Action
        val contactMatch = Regex("""\[ACTION_ADD_CONTACT:([^:]+):([^\]]+)\]""").find(processedResponse)
        if (contactMatch != null) {
            val name = contactMatch.groupValues[1].trim()
            val phone = contactMatch.groupValues[2].trim()
            val intent = Intent(Intent.ACTION_INSERT).apply {
                type = ContactsContract.Contacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.NAME, name)
                putExtra(ContactsContract.Intents.Insert.PHONE, phone)
            }
            launchSystemIntentWithDelay {
                startActivity(intent)
            }
        }


        // Camera Action
        if (processedResponse.contains("[ACTION_CAMERA]")) {
            val intent = Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            launchSystemIntentWithDelay {
                startActivity(intent)
            }
        }

        return cleanTags(processedResponse)
    }

    private fun cleanTags(text: String): String {
        return text.replace(Regex("""\[ACTION_[A-Z_]+:[^\]]+\]"""), "")
            .replace("[ACTION_CAMERA]", "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun launchSystemIntentWithDelay(action: () -> Unit) {
        coroutineScope.launch {
            // Laisse le temps à l'UI d'afficher le message de confirmation avant le changement d'écran.
            delay(350)
            try {
                action()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Action failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
    fun speak(text: String) {
        if (!isTtsReady || text.isBlank()) return

        textToSpeech?.apply {
            // 1) Détecter la langue du texte juste avant la lecture.
            val normalized = " ${text.lowercase()} "
            val englishMarkers = listOf(" the ", " is ", " are ", " for ", " with ", "please", "hello")
            val frenchMarkers = listOf(" le ", " la ", " les ", " est ", " pour ", " avec ", "bonjour", "merci")

            val englishScore = englishMarkers.count { normalized.contains(it) }
            val frenchScore = frenchMarkers.count { normalized.contains(it) }

            // 2) La langue détectée prime sur currentSettings.languageTag pour éviter les accents incohérents.
            val targetLocale = when {
                englishScore > frenchScore -> Locale.ENGLISH
                frenchScore > englishScore -> Locale.FRENCH
                else -> Locale.forLanguageTag(currentSettings.languageTag)
            }
            language = targetLocale

            // 3) Appliquer les réglages de vitesse et de ton
            setSpeechRate(currentSettings.speechRate)
            setPitch(currentSettings.pitch)

            // 4) Sélectionner une voix correspondant à la langue détectée.
            val preferredVoice = voices?.find {
                it.locale.language == targetLocale.language &&
                        it.name.lowercase().contains(currentSettings.voicePersona.lowercase())
            }
            if (preferredVoice != null) voice = preferredVoice

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
                val settings by settingsDataStore.settingsFlow.collectAsState(initial = UserSettings("en-US", "Female", "", 1.0f, 1.0f))
                
                LaunchedEffect(settings) {
                    currentSettings = settings
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
                    Scaffold(
                        containerColor = Color.Transparent,
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
                                    val chats by chatDao.getAllChats().collectAsState(initial = emptyList())
                                    HistoryScreen(
                                        chats = chats,
                                        onReSpeak = { text -> speak(text) },
                                        onDeleteAll = { 
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                chatDao.deleteAllChats()
                                            }
                                        }
                                    )
                                }
                                composable(BottomNavItem.Personalization.route) {
                                    PersonalizationScreen(
                                        settings = settings,
                                        tts = textToSpeech,
                                        onBack = { navController.popBackStack() },
                                        onLanguageChange = { tag ->
                                            lifecycleScope.launch { settingsDataStore.updateLanguage(tag) }
                                        },
                                        onPersonaChange = { persona ->
                                            lifecycleScope.launch { settingsDataStore.updateVoicePersona(persona) }
                                        },
                                        onVoiceChange = { voiceName ->
                                            lifecycleScope.launch { settingsDataStore.updateSelectedVoiceName(voiceName) }
                                        },
                                        onRateChange = { rate ->
                                            lifecycleScope.launch { settingsDataStore.updateSpeechRate(rate) }
                                        },
                                        onPitchChange = { pitch ->
                                            lifecycleScope.launch { settingsDataStore.updatePitch(pitch) }
                                        },
                                        onDeleteHistory = {
                                            lifecycleScope.launch(Dispatchers.IO) {
                                                chatDao.deleteAllChats()
                                            }
                                        }
                                    )
                                }
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
