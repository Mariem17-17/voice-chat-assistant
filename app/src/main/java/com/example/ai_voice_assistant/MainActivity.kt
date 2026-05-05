package com.example.ai_voice_assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.ContactsContract
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ai_voice_assistant.data.*
import com.example.ai_voice_assistant.security.SecureStorageManager
import com.example.ai_voice_assistant.ui.navigation.BottomNavigationBar
import com.example.ai_voice_assistant.ui.navigation.BottomNavItem
import com.example.ai_voice_assistant.ui.screens.*
import com.example.ai_voice_assistant.ui.theme.*
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.Locale
import org.json.JSONObject
import org.vosk.Model
import org.vosk.Recognizer
import org.vosk.android.RecognitionListener
import org.vosk.android.SpeechService
import org.vosk.android.StorageService

sealed class AuthScreen(val route: String) {
    object Login : AuthScreen("login")
    object Register : AuthScreen("register")
    object MainAssistant : AuthScreen("main_assistant")
}

class MainActivity : ComponentActivity() {

    private val TAG = "AI_ASSISTANT_DEBUG"
    private val nluManager by lazy { NluManager(this) }
    private val secureStorageManager by lazy { SecureStorageManager(this) }

    private var voskModel: Model? = null
    private var voskSpeechService: SpeechService? = null
    private var googleSpeechRecognizer: SpeechRecognizer? = null
    
    private var voskPartialHandler: ((String) -> Unit)? = null
    private var voskCompleteHandler: ((String, String) -> Unit)? = null
    private var voskErrorHandler: ((String) -> Unit)? = null

    var isVoskModelReady by mutableStateOf(false)
        private set
    var isVoskListening by mutableStateOf(false)
        private set

    private var auth: FirebaseAuth? = null
    
    private val sessionManager by lazy { SessionManager(this) }

    private val requestRecordAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) startUnifiedSpeechRecognitionInternal()
        else {
            isVoskListening = false
            runOnUiThread { Toast.makeText(this, "Microphone permission required.", Toast.LENGTH_SHORT).show() }
        }
    }

    private val systemPrompt = "You are an English AI Assistant. If the user asks for CALL, SMS, ALARM, YOUTUBE, CONTACT, or CAMERA, your response MUST include [ACTION_CALL:number], [ACTION_SMS:number:message], [ACTION_ALARM:HH:mm], [ACTION_YOUTUBE:query], [ACTION_CONTACT:name:number], or [ACTION_CAMERA] at the beginning. Be concise."
    private val chatHistory = mutableListOf<Message>()

    private val groqApi: GroqApi by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        val okHttpClient = OkHttpClient.Builder().addInterceptor(loggingInterceptor).build()
        Retrofit.Builder()
            .baseUrl("https://api.groq.com/openai/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GroqApi::class.java)
    }

    fun isNetworkAvailable(): Boolean {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        return activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || 
               activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    suspend fun processTextMessage(text: String): String {
        return if (isNetworkAvailable()) {
            isLoading = true
            try {
                chatHistory.add(Message(role = "user", content = text))
                val request = GroqRequest(model = "llama-3.3-70b-versatile", messages = chatHistory.toList())
                val response = groqApi.getCompletion("Bearer ${BuildConfig.GROQ_API_KEY}", request)
                val assistantText = response.choices.firstOrNull()?.message?.content ?: "Processing..."
                chatHistory.add(Message(role = "assistant", content = assistantText))
                
                val cleanedReply = handleSystemAction(assistantText)
                withContext(Dispatchers.IO) {
                    chatDao.insertChat(ChatEntity(userPrompt = text, aiResponse = cleanedReply))
                }
                cleanedReply
            } catch (e: Exception) {
                processOffline(text)
            } finally {
                isLoading = false
            }
        } else {
            processOffline(text)
        }
    }

    private suspend fun processOffline(text: String): String {
        val nluPredict = nluManager.predict(text)
        val reply = processAssistantUserMessageWithNlu(text, nluPredict)
        withContext(Dispatchers.IO) {
            chatDao.insertChat(ChatEntity(userPrompt = text, aiResponse = reply))
        }
        return reply
    }

    private fun handleSystemAction(response: String): String {
        var responseHasAction = false
        Regex("""\[ACTION_CONTACT:([^:]+):([^\]]+)\]""").find(response)?.let {
            responseHasAction = true
            launchNluContactSave(it.groupValues[1].trim(), it.groupValues[2].trim())
        }
        Regex("""\[ACTION_YOUTUBE:(.+?)\]""").find(response)?.let {
            responseHasAction = true
            launchNluYoutubeSearch(it.groupValues[1].trim())
        }
        Regex("""\[ACTION_ALARM:(\d{1,2}):(\d{2})\]""").find(response)?.let {
            responseHasAction = true
            setAlarm(it.groupValues[1].toInt(), it.groupValues[2].toInt())
        }
        Regex("""\[ACTION_SMS:([^:]+):(.+?)\]""").find(response)?.let {
            responseHasAction = true
            launchNluSmsComposer(it.groupValues[1].trim(), it.groupValues[2].trim())
        }
        Regex("""\[ACTION_CALL:([^\]]+)\]""").find(response)?.let {
            responseHasAction = true
            launchNluCallComposer(it.groupValues[1].trim())
        }
        if (response.contains("[ACTION_CAMERA]")) {
            responseHasAction = true
            launchSystemIntentWithDelay { startActivity(Intent(android.provider.MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) }
        }
        val cleaned = response.replace(Regex("""\[ACTION_[A-Z_]+:[^\]]+\]"""), "").replace("[ACTION_CAMERA]", "").trim()
        return if (responseHasAction) "Action prepared. Please tap to confirm." else cleaned
    }

    private fun processAssistantUserMessageWithNlu(userPrompt: String, nluPredict: Pair<String, Float>): String {
        val (intentName, confidence) = nluPredict
        if (confidence < 0.3f) return "I didn't quite catch that. Could you repeat?"
        return when (intentName) {
            "CONTACT" -> {
                val number = Regex("\\d+").find(userPrompt)?.value ?: ""
                val name = userPrompt.replace(Regex("(?i)save|contact|add|number|with|$number"), "").trim()
                launchNluContactSave(name, number); "Action prepared. Please tap to confirm."
            }
            "YOUTUBE" -> {
                val query = userPrompt.replace(Regex("(?i)search|youtube|on|for"), "").trim()
                launchNluYoutubeSearch(query); "Action prepared. Please tap to confirm."
            }
            "ALARM" -> {
                val time = Regex("(\\d{1,2})[:\\s](\\d{2})").find(userPrompt)
                if (time != null) setAlarm(time.groupValues[1].toInt(), time.groupValues[2].toInt()) else setAlarmFallback()
                "Action prepared. Please tap to confirm."
            }
            "CALL" -> {
                val number = Regex("\\d+").find(userPrompt)?.value ?: ""
                launchNluCallComposer(number); "Action prepared. Please tap to confirm."
            }
            "SMS" -> {
                val number = Regex("\\d+").find(userPrompt)?.value ?: ""
                launchNluSmsComposer(number, ""); "Action prepared. Please tap to confirm."
            }
            else -> "Command recognized: $intentName"
        }
    }

    private fun setAlarm(hour: Int, min: Int) {
        launchSystemIntentWithDelay {
            val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
                putExtra(AlarmClock.EXTRA_HOUR, hour)
                putExtra(AlarmClock.EXTRA_MINUTES, min)
                putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                putExtra(AlarmClock.EXTRA_MESSAGE, "AI Assistant Alarm")
            }
            startActivity(intent)
        }
    }

    private fun setAlarmFallback() {
        val calendar = java.util.Calendar.getInstance().apply { add(java.util.Calendar.MINUTE, 10) }
        setAlarm(calendar.get(java.util.Calendar.HOUR_OF_DAY), calendar.get(java.util.Calendar.MINUTE))
    }

    fun processNlu(text: String) {
        val cleaned = text.trim().lowercase()
        if (cleaned.isBlank()) return
        stopAllSTT()
        if (cleaned == "hello" || cleaned == "hi") {
            val reply = "Hello! How can I help you today?"
            voskCompleteHandler?.invoke(cleaned, reply)
            speak(reply)
            return
        }
        lifecycleScope.launch {
            val reply = processTextMessage(cleaned)
            runOnUiThread {
                voskCompleteHandler?.invoke(cleaned, reply)
                speak(reply)
            }
        }
    }

    private val voskRecognitionListener = object : RecognitionListener {
        override fun onPartialResult(hypothesis: String) {
            val partial = try { JSONObject(hypothesis).optString("partial", "") } catch(_: Exception) { "" }
            runOnUiThread { voskPartialHandler?.invoke(partial) }
        }
        override fun onResult(hypothesis: String) {
            val text = try { JSONObject(hypothesis).optString("text", "").trim().lowercase() } catch(_: Exception) { "" }
            if (text.isNotEmpty()) processNlu(text)
        }
        override fun onFinalResult(hypothesis: String) {
            val text = try { JSONObject(hypothesis).optString("text", "").trim().lowercase() } catch(_: Exception) { "" }
            if (text.isNotEmpty()) processNlu(text)
        }
        override fun onError(e: Exception) { runOnUiThread { isVoskListening = false } }
        override fun onTimeout() = stopAllSTT()
    }

    private var textToSpeech: TextToSpeech? = null
    var isTtsReady by mutableStateOf(false)
    var isTtsSpeaking by mutableStateOf(false)
    var isLoading by mutableStateOf(false)
    private val database by lazy { ChatDatabase.getDatabase(this) }
    private val chatDao by lazy { database.chatDao() }
    val settingsDataStore by lazy { SettingsDataStore(this) }
    var currentSettings by mutableStateOf(UserSettings("en-US", "Female", "", 1.0f, 1.0f))

    fun speak(text: String) {
        if (!isTtsReady || text.isBlank()) return
        textToSpeech?.apply {
            setSpeechRate(currentSettings.speechRate)
            setPitch(currentSettings.pitch)
            
            // Apply selected voice
            if (currentSettings.selectedVoiceName.isNotEmpty()) {
                voices?.find { it.name == currentSettings.selectedVoiceName }?.let {
                    voice = it
                }
            }
            
            language = Locale.ENGLISH
            speak(text, TextToSpeech.QUEUE_FLUSH, null, "reply")
        }
    }

    fun stopSpeaking() {
        textToSpeech?.stop()
        isTtsSpeaking = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()

        lifecycleScope.launch {
            settingsDataStore.settingsFlow.collect { settings ->
                currentSettings = settings
            }
        }
        
        // Safe Firebase Initialization
        try {
            FirebaseApp.initializeApp(this)
            auth = FirebaseAuth.getInstance()
        } catch (e: Exception) {
            Log.e(TAG, "Firebase initialization failed: ${e.message}")
        }

        unpackVoskModel()
        chatHistory.add(Message(role = "system", content = systemPrompt))
        textToSpeech = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                isTtsReady = true
                textToSpeech?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                    override fun onStart(id: String?) { isTtsSpeaking = true }
                    override fun onDone(id: String?) { isTtsSpeaking = false }
                    override fun onError(id: String?) { isTtsSpeaking = false }
                })
            }
        }
        
        setContent {
            AI_voice_assistantTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val rootNavController = rememberNavController()
                    val assistantNavController = rememberNavController()
                    
                    val initialRoute = remember {
                        val user = auth?.currentUser
                        if (user != null && user.isEmailVerified) AuthScreen.MainAssistant.route 
                        else AuthScreen.Login.route
                    }

                    NavHost(navController = rootNavController, startDestination = initialRoute) {
                        composable(AuthScreen.Login.route) {
                            LoginScreen(
                                auth = auth,
                                onLoginSuccess = { uid ->
                                    sessionManager.saveUserUid(uid)
                                    rootNavController.navigate(AuthScreen.MainAssistant.route) {
                                        popUpTo(AuthScreen.Login.route) { inclusive = true }
                                    }
                                },
                                onNavigateToRegister = { rootNavController.navigate(AuthScreen.Register.route) }
                            )
                        }
                        composable(AuthScreen.Register.route) {
                            RegisterScreen(
                                auth = auth,
                                onRegisterSuccess = {
                                    Toast.makeText(this@MainActivity, "Registration successful. Please verify your email.", Toast.LENGTH_SHORT).show()
                                    rootNavController.navigate(AuthScreen.Login.route) {
                                        popUpTo(AuthScreen.Register.route) { inclusive = true }
                                    }
                                },
                                onNavigateToLogin = { rootNavController.popBackStack() }
                            )
                        }
                        composable(AuthScreen.MainAssistant.route) {
                            MainLayout(
                                rootNavController = rootNavController,
                                assistantNavController = assistantNavController,
                                onLogout = {
                                    auth?.signOut()
                                    sessionManager.clearSession()
                                    rootNavController.navigate(AuthScreen.Login.route) {
                                        popUpTo(AuthScreen.MainAssistant.route) { inclusive = true }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun MainLayout(
        rootNavController: androidx.navigation.NavHostController,
        assistantNavController: androidx.navigation.NavHostController,
        onLogout: () -> Unit
    ) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = { BottomNavigationBar(assistantNavController) }
        ) { innerPadding ->
            NavHost(
                navController = assistantNavController,
                startDestination = BottomNavItem.Assistant.route,
                modifier = Modifier.padding(innerPadding)
            ) {
                composable(BottomNavItem.Assistant.route) {
                    AssistantScreenState()
                }
                composable(BottomNavItem.History.route) { 
                    val chats by chatDao.getAllChats().collectAsState(initial = emptyList())
                    HistoryScreen(
                        chats = chats,
                        onReSpeak = { speak(it) },
                        onDeleteAll = { lifecycleScope.launch { chatDao.deleteAllChats() } }
                    )
                }
                composable(BottomNavItem.Personalization.route) {
                    PersonalizationScreen(
                        settings = currentSettings,
                        tts = textToSpeech,
                        onBack = { assistantNavController.popBackStack() },
                        onLanguageChange = { tag -> lifecycleScope.launch { settingsDataStore.updateLanguage(tag) } },
                        onPersonaChange = { persona -> lifecycleScope.launch { settingsDataStore.updateVoicePersona(persona) } },
                        onVoiceChange = { voice -> 
                            lifecycleScope.launch { 
                                settingsDataStore.updateSelectedVoiceName(voice)
                                secureStorageManager.saveVoiceName(voice)
                            } 
                        },
                        onRateChange = { rate -> 
                            lifecycleScope.launch { 
                                settingsDataStore.updateSpeechRate(rate)
                                secureStorageManager.saveSpeechRate(rate)
                            } 
                        },
                        onPitchChange = { pitch -> 
                            lifecycleScope.launch { 
                                settingsDataStore.updatePitch(pitch)
                                secureStorageManager.savePitch(pitch)
                            } 
                        },
                        onDeleteHistory = { lifecycleScope.launch { chatDao.deleteAllChats() } },
                        onLogout = onLogout,
                        onDeleteAccount = { /* Handle delete account */ }
                    )
                }
            }
        }
    }

    private fun unpackVoskModel() {
        StorageService.unpack(this, "model-en-us", "model",
            { model: Model? ->
                voskModel = model
                isVoskModelReady = true
            },
            { e: Exception -> Log.e(TAG, "Vosk unpack error: " + e.message) }
        )
    }

    fun startUnifiedSpeechRecognition(
        onPartial: (String) -> Unit,
        onComplete: (String, String) -> Unit,
        onError: (String) -> Unit
    ) {
        this.voskPartialHandler = onPartial
        this.voskCompleteHandler = onComplete
        this.voskErrorHandler = onError
        
        if (isVoskListening) stopAllSTT()
        else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestRecordAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
            } else {
                startUnifiedSpeechRecognitionInternal()
            }
        }
    }

    private fun startUnifiedSpeechRecognitionInternal() {
        if (!isNetworkAvailable()) {
            startVoskInternal()
        } else {
            startGoogleSpeechInternal()
        }
    }

    private fun startVoskInternal() {
        if (voskModel == null) {
            Toast.makeText(this, "Offline model not ready.", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            voskSpeechService = SpeechService(Recognizer(voskModel, 16000.0f), 16000.0f)
            voskSpeechService?.startListening(voskRecognitionListener)
            isVoskListening = true
        } catch (e: Exception) {
            Log.e(TAG, "Vosk start error: " + e.message)
        }
    }

    private fun startGoogleSpeechInternal() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        }
        googleSpeechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : android.speech.RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { isVoskListening = true }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { isVoskListening = false }
                override fun onError(error: Int) { 
                    isVoskListening = false 
                    voskErrorHandler?.invoke("Speech Error: $error")
                }
                override fun onResults(results: Bundle?) {
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) processNlu(matches[0])
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    if (!matches.isNullOrEmpty()) {
                        runOnUiThread { voskPartialHandler?.invoke(matches[0]) }
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            startListening(intent)
        }
    }

    fun stopAllSTT() {
        voskSpeechService?.stop()
        voskSpeechService = null
        googleSpeechRecognizer?.stopListening()
        googleSpeechRecognizer?.destroy()
        googleSpeechRecognizer = null
        isVoskListening = false
    }

    private fun launchNluContactSave(name: String, number: String) {
        launchSystemIntentWithDelay {
            val intent = Intent(ContactsContract.Intents.Insert.ACTION).apply {
                type = ContactsContract.RawContacts.CONTENT_TYPE
                putExtra(ContactsContract.Intents.Insert.NAME, name)
                putExtra(ContactsContract.Intents.Insert.PHONE, number)
            }
            startActivity(intent)
        }
    }

    private fun launchNluYoutubeSearch(query: String) {
        launchSystemIntentWithDelay {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=$query"))
            startActivity(intent)
        }
    }

    private fun launchNluSmsComposer(number: String, message: String) {
        launchSystemIntentWithDelay {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("sms:$number")).apply {
                putExtra("sms_body", message)
            }
            startActivity(intent)
        }
    }

    private fun launchNluCallComposer(number: String) {
        launchSystemIntentWithDelay {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
            startActivity(intent)
        }
    }

    private fun launchSystemIntentWithDelay(block: () -> Unit) {
        lifecycleScope.launch {
            delay(2000)
            block()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voskSpeechService?.stop()
        voskSpeechService?.shutdown()
        googleSpeechRecognizer?.destroy()
        textToSpeech?.stop()
        textToSpeech?.shutdown()
    }
}
