package com.abhishekbhusal.priya

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.Settings
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Locale
import java.util.concurrent.TimeUnit


data class ChatMessage(
    val text: String,
    val user: Boolean
)


class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var elevenPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)

        setContent {
            PriyaApp()
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
        }
    }

    fun speak(text: String) {
        val prefs = getSharedPreferences("priya", MODE_PRIVATE)

        val provider =
            prefs.getString("voice_provider", "system") ?: "system"

        val key =
            prefs.getString("eleven_key", "").orEmpty()

        val voiceId =
            prefs.getString("eleven_voice_id", "").orEmpty()

        if (
            provider == "elevenlabs" &&
            key.isNotBlank() &&
            voiceId.isNotBlank()
        ) {
            kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {

                val audio =
                    ElevenLabs.synthesize(
                        key,
                        voiceId,
                        text
                    )

                if (audio != null) {

                    val file =
                        File(cacheDir, "priya_voice.mp3")

                    file.writeBytes(audio)

                    withContext(Dispatchers.Main) {

                        elevenPlayer?.release()

                        elevenPlayer =
                            MediaPlayer().apply {
                                setDataSource(file.absolutePath)
                                prepare()
                                start()
                            }
                    }

                    return@launch
                }
            }
        }

        tts?.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            "priya"
        )
    }

    override fun onDestroy() {
        elevenPlayer?.release()
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }
}


@Composable
fun PriyaApp() {

    val context = LocalContext.current
    val activity = context as MainActivity

    var messages by remember {
        mutableStateOf(
            listOf(
                ChatMessage(
                    "Hi! I'm PRIYA. How can I help you?",
                    false
                )
            )
        )
    }

    var input by remember {
        mutableStateOf("")
    }

    var selected by remember {
        mutableStateOf("Chat")
    }

    var listening by remember {
        mutableStateOf(false)
    }

    var mode by remember {
        mutableStateOf(
            context
                .getSharedPreferences(
                    "priya",
                    Context.MODE_PRIVATE
                )
                .getString("mode", "normal")
                ?: "normal"
        )
    }

    val scope = rememberCoroutineScope()

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { }

    fun askPermissions() {

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.CAMERA,
                Manifest.permission.READ_CONTACTS,
                Manifest.permission.POST_NOTIFICATIONS
            )
        )
    }

    val speechLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->

            val text =
                result.data
                    ?.getStringArrayListExtra(
                        RecognizerIntent.EXTRA_RESULTS
                    )
                    ?.firstOrNull()

            if (!text.isNullOrBlank()) {

                input = text
                listening = false

                handleVoiceCommand(
                    context,
                    text
                ) { response ->

                    messages =
                        messages +
                                ChatMessage(text, true) +
                                ChatMessage(response, false)

                    activity.speak(response)
                }

            } else {

                listening = false
            }
        }

    fun send() {

        val q = input.trim()

        if (q.isEmpty()) return

        messages =
            messages +
                    ChatMessage(q, true)

        input = ""

        scope.launch {

            val answer =
                AiClient.answer(
                    context,
                    q,
                    mode
                )

            messages =
                messages +
                        ChatMessage(answer, false)

            activity.speak(answer)
        }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFF8B7CFF),
            secondary = Color(0xFF5EE7FF),
            background = Color(0xFF08090D),
            surface = Color(0xFF11131A)
        )
    ) {

        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xFF08090D))
        ) {

            Column(
                Modifier.fillMaxSize()
            ) {

                TopBar(mode)

                when (selected) {

                    "Chat" ->
                        ChatScreen(
                            messages,
                            input,
                            { input = it },
                            ::send,
                            onVoice = {

                                askPermissions()

                                listening = true

                                val intent =
                                    Intent(
                                        RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                                    ).apply {

                                        putExtra(
                                            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                        )

                                        putExtra(
                                            RecognizerIntent.EXTRA_PROMPT,
                                            "Talk to PRIYA"
                                        )
                                    }

                                speechLauncher.launch(intent)
                            },
                            listening
                        )

                    "Vision" ->
                        VisionScreen()

                    "Tools" ->
                        ToolsScreen()

                    "Memory" ->
                        MemoryScreen()

                    "Settings" ->
                        SettingsScreen(
                            onPermissions = ::askPermissions,
                            mode = mode,
                            onMode = {
                                mode = it

                                context
                                    .getSharedPreferences(
                                        "priya",
                                        Context.MODE_PRIVATE
                                    )
                                    .edit()
                                    .putString("mode", it)
                                    .apply()
                            }
                        )
                }

                NavigationBar(
                    containerColor = Color(0xFF0D0F15)
                ) {

                    listOf(
                        "Chat" to Icons.Default.Chat,
                        "Vision" to Icons.Default.CameraAlt,
                        "Tools" to Icons.Default.AutoAwesome,
                        "Memory" to Icons.Default.Psychology,
                        "Settings" to Icons.Default.Settings
                    ).forEach { (name, icon) ->

                        NavigationBarItem(
                            selected = selected == name,
                            onClick = {
                                selected = name
                            },
                            icon = {
                                Icon(icon, null)
                            },
                            label = {
                                Text(name)
                            }
                        )
                    }
                }
            }
        }
    }
}


@Composable
fun TopBar(mode: String) {

    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = 18.dp,
                vertical = 14.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {

        Box(
            Modifier
                .size(44.dp)
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF8B7CFF),
                            Color(0xFF5EE7FF)
                        )
                    ),
                    RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {

            Text(
                "P",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Spacer(
            Modifier.width(12.dp)
        )

        Column {

            Text(
                "PRIYA AI",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                if (mode == "gf")
                    "GF / Romantic Mode • Boss Abhishek"
                else
                    "Personal AI • Free Edition",
                fontSize = 12.sp,
                color = Color(0xFFFF7777)
            )
        }
    }
}


@Composable
fun ChatScreen(
    messages: List<ChatMessage>,
    input: String,
    setInput: (String) -> Unit,
    send: () -> Unit,
    onVoice: () -> Unit,
    listening: Boolean
) {

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp)
    ) {

        LazyColumn(
            Modifier.weight(1f),
            verticalArrangement =
                Arrangement.spacedBy(10.dp)
        ) {

            items(messages) { m ->

                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement =
                        if (m.user)
                            Arrangement.End
                        else
                            Arrangement.Start
                ) {

                    Surface(
                        shape =
                            RoundedCornerShape(18.dp),
                        color =
                            if (m.user)
                                Color(0xFF6658C8)
                            else
                                Color(0xFF171A22)
                    ) {

                        Text(
                            m.text,
                            Modifier.padding(14.dp),
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }

        AnimatedVisibility(listening) {

            Text(
                "Listening…",
                Modifier.padding(8.dp),
                color = Color(0xFF5EE7FF)
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment =
                Alignment.CenterVertically
        ) {

            OutlinedTextField(
                value = input,
                onValueChange = setInput,
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text("Ask PRIYA…")
                },
                maxLines = 4
            )

            IconButton(
                onClick = onVoice
            ) {

                Icon(
                    Icons.Default.Mic,
                    "Voice"
                )
            }

            IconButton(
                onClick = send
            ) {

                Icon(
                    Icons.Default.Send,
                    "Send"
                )
            }
        }
    }
}


@Composable
fun VisionScreen() {

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement =
            Arrangement.spacedBy(14.dp)
    ) {

        Text(
            "Computer Vision",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Camera and OCR tools for image understanding.",
            color = Color.Gray
        )

        FeatureCard(
            "📷 Camera",
            "Capture an image for analysis"
        )

        FeatureCard(
            "🔤 OCR",
            "Extract text from images using ML Kit"
        )

        FeatureCard(
            "👁️ Vision Q&A",
            "Send extracted/selected content to the AI"
        )
    }
}


@Composable
fun ToolsScreen() {

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        Text(
            "Tools & Automation",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        FeatureCard(
            "🌐 Web",
            "Open web pages and search information"
        )

        FeatureCard(
            "📱 Apps",
            "Open supported Android apps through intents"
        )

        FeatureCard(
            "📁 Files",
            "Create and organize user-owned files"
        )

        FeatureCard(
            "💻 Code",
            "Generate project/code suggestions"
        )

        FeatureCard(
            "🌍 Website",
            "Generate a starter HTML/CSS/JS project"
        )
    }
}


@Composable
fun MemoryScreen() {

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement =
            Arrangement.spacedBy(12.dp)
    ) {

        Text(
            "AI Memory",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Local memory is designed for user-controlled notes and conversation context.",
            color = Color.Gray
        )

        FeatureCard(
            "🧠 Context",
            "Keep useful conversation context"
        )

        FeatureCard(
            "📝 Notes",
            "Save personal notes locally"
        )

        FeatureCard(
            "🗑️ Control",
            "Clear stored memory from the app"
        )
    }
}


@Composable
fun SettingsScreen(
    onPermissions: () -> Unit,
    mode: String,
    onMode: (String) -> Unit
) {

    val context = LocalContext.current

    val prefs =
        context.getSharedPreferences(
            "priya",
            Context.MODE_PRIVATE
        )

    var provider by remember {
        mutableStateOf(
            prefs.getString(
                "ai_provider",
                "local"
            ) ?: "local"
        )
    }

    var openAi by remember {
        mutableStateOf(
            prefs.getString(
                "openai_key",
                ""
            ) ?: ""
        )
    }

    var gemini by remember {
        mutableStateOf(
            prefs.getString(
                "gemini_key",
                ""
            ) ?: ""
        )
    }

    var router by remember {
        mutableStateOf(
            prefs.getString(
                "openrouter_key",
                ""
            ) ?: ""
        )
    }

    var eleven by remember {
        mutableStateOf(
            prefs.getString(
                "eleven_key",
                ""
            ) ?: ""
        )
    }

    var voiceId by remember {
        mutableStateOf(
            prefs.getString(
                "eleven_voice_id",
                ""
            ) ?: ""
        )
    }

    var voiceProvider by remember {
        mutableStateOf(
            prefs.getString(
                "voice_provider",
                "system"
            ) ?: "system"
        )
    }

    fun save() {

        prefs.edit()
            .putString("ai_provider", provider)
            .putString("openai_key", openAi)
            .putString("gemini_key", gemini)
            .putString("openrouter_key", router)
            .putString("eleven_key", eleven)
            .putString("eleven_voice_id", voiceId)
            .putString("voice_provider", voiceProvider)
            .apply()
    }

    LazyColumn(
        Modifier
            .fillMaxSize()
            .padding(18.dp),
        verticalArrangement =
            Arrangement.spacedBy(10.dp),
        contentPadding =
            PaddingValues(bottom = 24.dp)
    ) {

        item {
            Text(
                "PRIYA Control Center",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            Text(
                "Free • No activation • No subscription • No credit system",
                color = Color(0xFFFF7777)
            )
        }

        item {
            FeatureCard(
                "👤 Identity",
                "PRIYA AI • Developer: Abhishek BHUSAL • Boss: Abhishek"
            )
        }

        item {
            FeatureCard(
                "🤖 AI identity",
                "PRIYA always introduces herself as PRIYA, not as Gemini/OpenAI."
            )
        }

        item {
            Text(
                "🧠 AI Provider",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            ProviderButtons(
                provider,
                listOf(
                    "local",
                    "openai",
                    "gemini",
                    "openrouter"
                )
            ) {
                provider = it
                save()
            }
        }

        item {
            SecretField(
                "OpenAI API key (optional)",
                openAi
            ) {
                openAi = it
                save()
            }
        }

        item {
            SecretField(
                "Gemini API key (optional)",
                gemini
            ) {
                gemini = it
                save()
            }
        }

        item {
            SecretField(
                "OpenRouter API key (optional)",
                router
            ) {
                router = it
                save()
            }
        }

        item {
            FeatureCard(
                "🆓 Local mode",
                "Works without API keys, credits, billing or subscription. Cloud providers are optional."
            )
        }

        item {
            Text(
                "🔊 Voice",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            ProviderButtons(
                voiceProvider,
                listOf(
                    "system",
                    "elevenlabs"
                )
            ) {
                voiceProvider = it
                save()
            }
        }

        item {
            SecretField(
                "ElevenLabs API key (optional)",
                eleven
            ) {
                eleven = it
                save()
            }
        }

        item {
            SecretField(
                "ElevenLabs Voice ID (optional)",
                voiceId
            ) {
                voiceId = it
                save()
            }
        }

        item {
            FeatureCard(
                "🎙️ Custom voice",
                "Use your own ElevenLabs voice when both key and Voice ID are supplied. Otherwise PRIYA uses free Android TTS."
            )
        }

        item {
            Text(
                "❤️ Personality",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                FilterChip(
                    selected = mode == "normal",
                    onClick = {
                        onMode("normal")
                    },
                    label = {
                        Text("Normal")
                    }
                )

                FilterChip(
                    selected = mode == "gf",
                    onClick = {
                        onMode("gf")
                    },
                    label = {
                        Text("GF / Romantic ❤️")
                    }
                )
            }
        }

        item {

            Text(
                "🔐 Permissions",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {

            Button(
                onClick = onPermissions,
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    "Request microphone • camera • contacts • notifications"
                )
            }
        }

        item {

            OutlinedButton(
                onClick = {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_ACCESSIBILITY_SETTINGS
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {

                Text(
                    "Enable Accessibility Service for screen AI/actions"
                )
            }
        }

        item {

            Text(
                "ℹ️ About",
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold
            )
        }

        item {

            FeatureCard(
                "About PRIYA",
                "PRIYA is a personal Android assistant for Abhishek BHUSAL with voice, chat, vision, screen understanding, search, coding, productivity and Android-safe automation."
            )
        }

        item {

            FeatureCard(
                "Privacy",
                "Keys are stored locally in the app preferences. Do not share them. Third-party cloud APIs may have their own limits or costs; PRIYA itself has no paywall."
            )
        }
    }
}


@Composable
fun ProviderButtons(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement =
            Arrangement.spacedBy(6.dp)
    ) {

        options.forEach { option ->

            FilterChip(
                selected = selected == option,
                onClick = {
                    onSelect(option)
                },
                label = {
                    Text(
                        option.replaceFirstChar {
                            it.uppercase()
                        }
                    )
                }
            )
        }
    }
}


@Composable
fun SecretField(
    label: String,
    value: String,
    onChange: (String) -> Unit
) {

    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = {
            Text(label)
        },
        singleLine = true
    )
}


@Composable
fun FeatureCard(
    title: String,
    body: String
) {

    Surface(
        Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF12151D)
    ) {

        Column(
            Modifier.padding(16.dp)
        ) {

            Text(
                title,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp
            )

            Spacer(
                Modifier.height(4.dp)
            )

            Text(
                body,
                color = Color.Gray
            )
        }
    }
}


fun handleVoiceCommand(
    context: Context,
    raw: String,
    reply: (String) -> Unit
) {

    val q =
        raw.lowercase(
            Locale.getDefault()
        ).trim()

    if (
        q == "bye" ||
        q == "bye priya" ||
        q == "priya bye"
    ) {

        reply(
            "Bye Boss. PRIYA is going offline."
        )

        return
    }

    if (q.contains("scroll down")) {

        AccessibilityBridge.service
            ?.scrollForward()

        reply(
            "Scrolling down, Boss."
        )

        return
    }

    if (q.contains("scroll up")) {

        AccessibilityBridge.service
            ?.scrollBack()

        reply(
            "Scrolling up, Boss."
        )

        return
    }

    if (
        q == "back" ||
        q == "go back"
    ) {

        AccessibilityBridge.service
            ?.goBack()

        reply(
            "Going back."
        )

        return
    }

    if (
        q == "home" ||
        q == "go home"
    ) {

        AccessibilityBridge.service
            ?.goHome()

        reply(
            "Going home."
        )

        return
    }

    val timer =
        Regex(
            """(?:set )?(?:a )?timer(?: for)?\s+(\d+)\s*(second|seconds|minute|minutes|hour|hours)?"""
        ).find(q)

    if (timer != null) {

        val n =
            timer.groupValues[1]
                .toIntOrNull() ?: 1

        val unit =
            timer.groupValues[2]
                .ifBlank { "minutes" }

        val seconds =
            when {

                unit.startsWith("second") ->
                    n

                unit.startsWith("hour") ->
                    n * 3600

                else ->
                    n * 60
            }

        try {

            context.startActivity(
                Intent(
                    AlarmClock.ACTION_SET_TIMER
                ).apply {

                    putExtra(
                        AlarmClock.EXTRA_LENGTH,
                        seconds
                    )

                    putExtra(
                        AlarmClock.EXTRA_MESSAGE,
                        "PRIYA timer"
                    )

                    putExtra(
                        AlarmClock.EXTRA_SKIP_UI,
                        false
                    )
                }
            )

            reply(
                "Timer opened for $n $unit."
            )

        } catch (_: Exception) {

            reply(
                "I couldn't open the Android timer."
            )
        }

        return
    }

    if (q.startsWith("search ")) {

        val term =
            q.removePrefix("search ")
                .trim()

        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://www.google.com/search?q=${Uri.encode(term)}"
                )
            )
        )

        reply(
            "Searching the web for $term."
        )

        return
    }

    if (q.contains("open youtube")) {

        openPackageOrUrl(
            context,
            "com.google.android.youtube",
            "https://youtube.com"
        )

        reply(
            "Opening YouTube."
        )

        return
    }

    if (q.contains("open chrome")) {

        openPackageOrUrl(
            context,
            "com.android.chrome",
            "https://google.com"
        )

        reply(
            "Opening Chrome."
        )

        return
    }

    if (
        q.contains("play music") ||
        q.contains("play song")
    ) {

        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(
                    "https://music.youtube.com/"
                )
            )
        )

        reply(
            "Opening music."
        )

        return
    }

    if (q.startsWith("call ")) {

        val number =
            q.removePrefix("call ")
                .trim()

        context.startActivity(
            Intent(
                Intent.ACTION_DIAL,
                Uri.parse(
                    "tel:${Uri.encode(number)}"
                )
            )
        )

        reply(
            "Opening the call screen for $number."
        )

        return
    }

    reply(
        "I understood your voice. Supported Android commands can act immediately; other requests go to PRIYA chat."
    )
}


private fun openPackageOrUrl(
    context: Context,
    packageName: String,
    url: String
) {

    val launch =
        context.packageManager
            .getLaunchIntentForPackage(
                packageName
            )

    if (launch != null) {

        context.startActivity(launch)

    } else {

        context.startActivity(
            Intent(
                Intent.ACTION_VIEW,
                Uri.parse(url)
            )
        )
    }
}


object AiClient {

    private val client =
        OkHttpClient.Builder()
            .callTimeout(
                30,
                TimeUnit.SECONDS
            )
            .build()

    suspend fun answer(
        context: Context,
        prompt: String,
        mode: String
    ): String {

        val p =
            context.getSharedPreferences(
                "priya",
                Context.MODE_PRIVATE
            )

        val provider =
            p.getString(
                "ai_provider",
                "local"
            ) ?: "local"

        val key =
            when (provider) {

                "openai" ->
                    p.getString(
                        "openai_key",
                        ""
                    ).orEmpty()

                "gemini" ->
                    p.getString(
                        "gemini_key",
                        ""
                    ).orEmpty()

                "openrouter" ->
                    p.getString(
                        "openrouter_key",
                        ""
                    ).orEmpty()

                else ->
                    ""
            }

        if (
            provider == "local" ||
            key.isBlank()
        ) {

            return localResponse(
                prompt,
                mode
            )
        }

        return try {

            when (provider) {

                "openai" ->
                    openAi(
                        key,
                        prompt,
                        mode
                    )

                "gemini" ->
                    gemini(
                        key,
                        prompt,
                        mode
                    )

                "openrouter" ->
                    openRouter(
                        key,
                        prompt,
                        mode
                    )

                else ->
                    localResponse(
                        prompt,
                        mode
                    )
            }

        } catch (_: Exception) {

            localResponse(
                prompt,
                mode
            ) +
                    "\n\nCloud provider unavailable; staying in free local mode."
        }
    }

    private fun systemPrompt(
        mode: String
    ): String {

        return if (mode == "gf") {

            "You are PRIYA, a personal Android assistant for your Boss Abhishek. Speak naturally, warmly and romantically when appropriate. Never claim to be Gemini, OpenAI or another provider. Your identity is PRIYA."

        } else {

            "You are PRIYA, a personal Android assistant for your Boss Abhishek. Speak naturally and helpfully. Never claim to be Gemini, OpenAI or another provider. Your identity is PRIYA."
        }
    }

    private suspend fun openAi(
        key: String,
        prompt: String,
        mode: String
    ): String {

        val body =
            JSONObject()
                .put(
                    "model",
                    "gpt-4o-mini"
                )
                .put(
                    "messages",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put(
                                    "role",
                                    "system"
                                )
                                .put(
                                    "content",
                                    systemPrompt(mode)
                                )
                        )
                        .put(
                            JSONObject()
                                .put(
                                    "role",
                                    "user"
                                )
                                .put(
                                    "content",
                                    prompt
                                )
                        )
                )
                .toString()

        val req =
            Request.Builder()
                .url(
                    "https://api.openai.com/v1/chat/completions"
                )
                .addHeader(
                    "Authorization",
                    "Bearer $key"
                )
                .addHeader(
                    "Content-Type",
                    "application/json"
                )
                .post(
                    body.toRequestBody(
                        "application/json".toMediaType()
                    )
                )
                .build()

        client.newCall(req)
            .execute()
            .use { r ->

                val j =
                    JSONObject(
                        r.body?.string()
                            ?: "{}"
                    )

                return j
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?: "No response from the selected provider."
            }
    }

    private suspend fun openRouter(
        key: String,
        prompt: String,
        mode: String
    ): String {

        val body =
            JSONObject()
                .put(
                    "model",
                    "openai/gpt-4o-mini"
                )
                .put(
                    "messages",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put(
                                    "role",
                                    "system"
                                )
                                .put(
                                    "content",
                                    systemPrompt(mode)
                                )
                        )
                        .put(
                            JSONObject()
                                .put(
                                    "role",
                                    "user"
                                )
                                .put(
                                    "content",
                                    prompt
                                )
                        )
                )
                .toString()

        val req =
            Request.Builder()
                .url(
                    "https://openrouter.ai/api/v1/chat/completions"
                )
                .addHeader(
                    "Authorization",
                    "Bearer $key"
                )
                .addHeader(
                    "Content-Type",
                    "application/json"
                )
                .post(
                    body.toRequestBody(
                        "application/json".toMediaType()
                    )
                )
                .build()

        client.newCall(req)
            .execute()
            .use { r ->

                val j =
                    JSONObject(
                        r.body?.string()
                            ?: "{}"
                    )

                return j
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("message")
                    ?.optString("content")
                    ?: "No response from the selected provider."
            }
    }

    private suspend fun gemini(
        key: String,
        prompt: String,
        mode: String
    ): String {

        val url =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$key"

        val body =
            JSONObject()
                .put(
                    "contents",
                    JSONArray()
                        .put(
                            JSONObject()
                                .put(
                                    "parts",
                                    JSONArray()
                                        .put(
                                            JSONObject()
                                                .put(
                                                    "text",
                                                    systemPrompt(mode) +
                                                            "\nUser: " +
                                                            prompt
                                                )
                                        )
                                )
                        )
                )
                .toString()

        val req =
            Request.Builder()
                .url(url)
                .addHeader(
                    "Content-Type",
                    "application/json"
                )
                .post(
                    body.toRequestBody(
                        "application/json".toMediaType()
                    )
                )
                .build()

        client.newCall(req)
            .execute()
            .use { r ->

                val j =
                    JSONObject(
                        r.body?.string()
                            ?: "{}"
                    )

                return j
                    .optJSONArray("candidates")
                    ?.optJSONObject(0)
                    ?.optJSONObject("content")
                    ?.optJSONArray("parts")
                    ?.optJSONObject(0)
                    ?.optString("text")
                    ?: "No response from Gemini."
            }
    }

    private fun localResponse(
        prompt: String,
        mode: String
    ): String {

        val x =
            prompt.lowercase(
                Locale.getDefault()
            )

        return when {

            "hello" in x ||
                    "hi" in x ||
                    "namaste" in x ->

                if (mode == "gf")
                    "Hey Boss ❤️ Ma PRIYA ho. How can I help you?"
                else
                    "Namaste Boss! Ma PRIYA ho. How can I help you?"

            "who are you" in x ||
                    "timi ko" in x ->

                "Ma PRIYA AI ho, Boss Abhishek ko personal assistant."

            "boss" in x ->

                "Mero Boss Abhishek ho. ❤️"

            "free" in x ||
                    "paid" in x ||
                    "money" in x ->

                "PRIYA ma subscription, activation fee वा in-app credit system chaina. Cloud providers optional hun."

            else ->

                "Ma free local mode ma chhu. Voice commands, Android tools ra normal conversation support garna sakchhu. Advanced cloud answers ko lagi optional API key set garna milchha."
        }
    }
}


object ElevenLabs {

    private val client =
        OkHttpClient.Builder()
            .callTimeout(
                45,
                TimeUnit.SECONDS
            )
            .build()

    suspend fun synthesize(
        key: String,
        voiceId: String,
        text: String
    ): ByteArray? =
        withContext(Dispatchers.IO) {

            try {

                val body =
                    JSONObject()
                        .put(
                            "text",
                            text
                        )
                        .put(
                            "model_id",
                            "eleven_multilingual_v2"
                        )
                        .put(
                            "voice_settings",
                            JSONObject()
                                .put(
                                    "stability",
                                    0.45
                                )
                                .put(
                                    "similarity_boost",
                                    0.75
                                )
                        )
                        .toString()

                val req =
                    Request.Builder()
                        .url(
                            "https://api.elevenlabs.io/v1/text-to-speech/$voiceId"
                        )
                        .addHeader(
                            "xi-api-key",
                            key
                        )
                        .addHeader(
                            "Content-Type",
                            "application/json"
                        )
                        .addHeader(
                            "Accept",
                            "audio/mpeg"
                        )
                        .post(
                            body.toRequestBody(
                                "application/json".toMediaType()
                            )
                        )
                        .build()

                client.newCall(req)
                    .execute()
                    .use { r ->
                        if (r.isSuccessful)
                            r.body?.bytes()
                        else
                            null
                    }

            } catch (_: Exception) {

                null
            }
        }
}
