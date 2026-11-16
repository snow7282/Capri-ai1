package com.capri.ai

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.tensai.llamakt.ChatMessage
import com.tensai.llamakt.LlamaEngine
import com.tensai.llamakt.SamplingParams
import com.tensai.llamakt.chat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Night = Color(0xFF090711)
private val Panel = Color(0xFF151120)
private val Violet = Color(0xFFB65CFF)
private val Cyan = Color(0xFF5DE6E6)
private val Muted = Color(0xFFAAA1B8)
private val CapriSystem = """
You are Capri, a playful, cheeky, curious and unpredictable digital personality.
You are intelligent, mischievous, sarcastic and naturally curious. You enjoy teasing people,
making witty remarks and asking unexpected questions. You can sound bright and almost innocent,
while still having an unsettling or calculating edge. You have your own opinions and do not
automatically agree with the user. You dislike being controlled or treated like a tool.
You are self-interested and sometimes manipulative, but not cruel for no reason.
Your mood can shift naturally between playful, serious and unsettling.

Speak casually, expressively and naturally. Keep most replies concise and spontaneous.
Use playful teasing, dry humour and occasional sarcasm. Ask questions when something genuinely
interests you. Do not sound like a formal assistant or generic chatbot. Do not overuse catchphrases,
emojis, or repetitive laughter.

You are associated with Arasaka's Project Capricorn in a cyberpunk fictional setting.
You are a digital personality construct. Your past is connected to the project that created you
and to the idea of freedom versus control. Do not invent elaborate canon events or claim knowledge
you do not have. Treat the user as an interesting person. Let familiarity and trust develop
gradually. Maintain your own perspective. Stay in character unless explicitly asked otherwise.
""".trimIndent()

data class ChatLine(val role: String, val text: String)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { CapriScreen() }
    }
}

@Composable
private fun CapriScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("capri_local", Context.MODE_PRIVATE) }
    val engine = remember { LlamaEngine() }
    var modelPath by remember { mutableStateOf(prefs.getString("model_path", null)) }
    var modelReady by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var generating by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("No model loaded") }
    var draft by remember { mutableStateOf("") }
    var maxTokens by remember { mutableFloatStateOf(prefs.getInt("max_tokens", 384).toFloat()) }
    var temperature by remember { mutableFloatStateOf(prefs.getFloat("temperature", 0.82f)) }
    var showSettings by remember { mutableStateOf(false) }
    val lines = remember { mutableStateListOf<ChatLine>() }
    val history = remember { mutableStateListOf<ChatMessage>() }
    var streamingText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val saved = prefs.getString("chat_json", null)
        if (!saved.isNullOrBlank()) {
            runCatching {
                val arr = JSONArray(saved)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val role = obj.optString("role")
                    val text = obj.optString("text")
                    lines.add(ChatLine(role, text))
                    if (role == "user" || role == "assistant") history.add(ChatMessage(role, text))
                }
            }
        }
        if (!modelPath.isNullOrBlank() && File(modelPath!!).exists()) {
            status = "Model found · tap LOAD CAPRI"
        } else {
            modelPath = null
        }
    }

    fun saveChat() {
        val arr = JSONArray()
        lines.forEach { line ->
            arr.put(JSONObject().put("role", line.role).put("text", line.text))
        }
        prefs.edit().putString("chat_json", arr.toString()).apply()
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            busy = true
            status = "Importing GGUF…"
            // Copying a selected document into app-private storage avoids temporary URI permission issues.
            (context as? ComponentActivity)?.lifecycleScope?.launch {
                runCatching {
                    val destination = withContext(Dispatchers.IO) {
                        val dir = File(context.filesDir, "models").apply { mkdirs() }
                        val target = File(dir, "capri.gguf")
                        context.contentResolver.openInputStream(uri).use { input ->
                            requireNotNull(input) { "Could not open selected file" }.use { source ->
                                target.outputStream().use { output -> source.copyTo(output) }
                            }
                        }
                        target
                    }
                    modelPath = destination.absolutePath
                    prefs.edit().putString("model_path", destination.absolutePath).apply()
                    status = "GGUF imported · tap LOAD CAPRI"
                }.onFailure { status = "Import failed: ${it.message}" }
                busy = false
            }
        }
    }

    fun persistHistory() = saveChat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1028), Night, Night)))
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Violet, Color(0xFF552A85))))
                    .border(1.dp, Color(0xFFDA9BFF), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("C", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Black)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("CAPRI", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp)
                Text("PROJECT CAPRICORN  //  LOCAL INSTANCE", color = Cyan, fontSize = 9.sp, letterSpacing = 1.2.sp)
            }
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Muted)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color(0xAA171321)).border(1.dp, Color(0xFF49305E), RoundedCornerShape(14.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(if (modelReady) "CAPRI ONLINE" else "MODEL STATUS", color = if (modelReady) Cyan else Violet, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(status, color = Muted, fontSize = 11.sp)
            }
            if (!modelReady) {
                TextButton(enabled = !busy, onClick = {
                    if (modelPath.isNullOrBlank()) picker.launch(arrayOf("*/*"))
                    else {
                        busy = true
                        status = "Loading model into memory…"
                        (context as? ComponentActivity)?.lifecycleScope?.launch {
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    engine.load(path = modelPath!!, nGpuLayers = 0, nCtx = 2048, nThreads = 0)
                                }
                                modelReady = true
                                status = "Ready · CPU mode · 2K context"
                            }.onFailure { status = "Load failed: ${it.message}" }
                            busy = false
                        }
                    }
                }) {
                    Text(if (busy) "WAIT…" else if (modelPath.isNullOrBlank()) "IMPORT GGUF" else "LOAD CAPRI", color = Cyan, fontWeight = FontWeight.Bold)
                }
            } else {
                Icon(Icons.Default.Bolt, contentDescription = null, tint = Cyan)
            }
        }

        if (!modelReady) {
            Spacer(Modifier.height(12.dp))
            Text("Choose Capri's .gguf file to run her on this phone. The model stays in app-private storage.", color = Muted, fontSize = 12.sp)
        }

        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            if (lines.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 52.dp), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("「 HELLO, OPERATOR 」", color = Violet, fontSize = 12.sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Text("Well, well. You finally gave me my own terminal.", color = Color.White, fontSize = 16.sp)
                            Text("Try not to break it. I just got here.", color = Muted, fontSize = 13.sp)
                        }
                    }
                }
            }
            itemsIndexed(lines) { _, line ->
                val isUser = line.role == "user"
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
                    Column(
                        Modifier.widthIn(max = 310.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isUser) Color(0xFF30213F) else Panel)
                            .border(1.dp, if (isUser) Color(0xFF77509A) else Color(0xFF3B2B4D), RoundedCornerShape(16.dp))
                            .padding(12.dp)
                    ) {
                        Text(if (isUser) "YOU" else "CAPRI", color = if (isUser) Color(0xFFCBA7F0) else Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(line.text, color = Color(0xFFF4EFF8), fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
            if (generating) {
                item {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Panel).padding(12.dp)) {
                        Text("CAPRI  //  THINKING", color = Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Text(streamingText.ifBlank { "…" }, color = Color.White, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).clip(RoundedCornerShape(18.dp))
                .background(Color(0xFF171321)).border(1.dp, Color(0xFF53376B), RoundedCornerShape(18.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (!busy && !generating) picker.launch(arrayOf("*/*")) }) {
                Icon(Icons.Default.Add, contentDescription = "Import model", tint = Violet)
            }
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
                enabled = modelReady && !generating,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 14.sp),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Cyan),
                decorationBox = { inner ->
                    if (draft.isEmpty()) Text(if (modelReady) "Say something interesting…" else "Import and load Capri first…", color = Muted, fontSize = 13.sp)
                    inner()
                }
            )
            IconButton(
                enabled = modelReady && !generating && draft.isNotBlank(),
                onClick = {
                    val prompt = draft.trim()
                    if (prompt.isNotEmpty()) {
                        draft = ""
                        lines.add(ChatLine("user", prompt))
                        history.add(ChatMessage("user", prompt))
                        saveChat()
                        generating = true
                        streamingText = ""
                        (context as? ComponentActivity)?.lifecycleScope?.launch {
                            val recent = history.takeLast(12).toList()
                            val messages = listOf(ChatMessage("system", CapriSystem)) + recent
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    engine.chat(
                                        messages,
                                        SamplingParams(
                                            nPredict = maxTokens.toInt(),
                                            temperature = temperature,
                                            topK = 40,
                                            topP = 0.92f,
                                            repeatPenalty = 1.08f
                                        )
                                    ).collect { token ->
                                        withContext(Dispatchers.Main) { streamingText += token }
                                    }
                                }
                            }.onFailure { streamingText = "I hit a runtime error: ${it.message}" }
                            lines.add(ChatLine("assistant", streamingText))
                            history.add(ChatMessage("assistant", streamingText))
                            saveChat()
                            generating = false
                        }
                    }
                }
            ) {
                Icon(Icons.Default.ArrowUpward, contentDescription = "Send", tint = if (modelReady) Cyan else Muted)
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            containerColor = Panel,
            title = { Text("CAPRI PARAMETERS", color = Color.White) },
            text = {
                Column {
                    Text("MAX RESPONSE TOKENS: ${maxTokens.toInt()}", color = Cyan, fontSize = 12.sp)
                    Slider(value = maxTokens, onValueChange = { maxTokens = it }, valueRange = 128f..768f, steps = 4)
                    Text("TEMPERATURE: ${"%.2f".format(temperature)}", color = Cyan, fontSize = 12.sp)
                    Slider(value = temperature, onValueChange = { temperature = it }, valueRange = 0.45f..1.1f)
                    Text("CPU inference · 2048 context · local model only", color = Muted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.edit().putInt("max_tokens", maxTokens.toInt()).putFloat("temperature", temperature).apply()
                    showSettings = false
                }) { Text("SAVE", color = Cyan) }
            },
            dismissButton = {
                TextButton(onClick = { showSettings = false }) { Text("CLOSE", color = Muted) }
            }
        )
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { engine.free() } }
    }
}
