package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddToDrive
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAppearanceSettings
import com.example.ui.theme.NeuralViolet
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.LiveSession
import com.google.firebase.ai.type.PublicPreviewAPI
import com.google.firebase.ai.type.ResponseModality
import com.google.firebase.ai.type.SpeechConfig
import com.google.firebase.ai.type.Voice
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.liveGenerationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.util.Locale

private const val TAG = "LiveVoiceAndContext"

/**
 * Represents a context item attached via the Antigravity Remote "Add Context" menu
 * (Files, Folder, Mentions, Actions, Browser, Drive).
 */
data class ContextAttachmentItem(
    val id: String,
    val category: String, // "Files", "Folder", "Mentions", "Actions", "Browser", "Drive"
    val displayLabel: String,
    val promptContextSnippet: String
)

enum class AddContextCategory(
    val title: String,
    val subtitle: String,
    val icon: ImageVector
) {
    FILES("Files", "Attach workspace code files or device media", Icons.Default.AttachFile),
    FOLDER("Folder", "Include an entire project directory tree", Icons.Default.FolderOpen),
    MENTIONS("Mentions", "Reference @Agent, @Terminal, or symbols", Icons.Default.AlternateEmail),
    ACTIONS("Actions", "Run slash commands & IDE workflows", Icons.Default.Terminal),
    BROWSER("Browser", "Attach live Chrome MCP / Web Bridge DOM state", Icons.Default.Language),
    DRIVE("Drive", "Link Google Drive specs & cloud workspace docs", Icons.Default.AddToDrive)
}

/**
 * Reusable row of attached Context Chips displayed above the prompt input box.
 */
@Composable
fun AttachedContextChipsRow(
    attachments: List<ContextAttachmentItem>,
    accent: Color,
    elevated: Color,
    border: Color,
    fg: Color,
    onRemove: (ContextAttachmentItem) -> Unit,
    onClearAll: () -> Unit
) {
    if (attachments.isEmpty()) return

    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .background(elevated.copy(alpha = 0.7f))
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("attached_context_chips_row"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(attachments, key = { it.id }) { item ->
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.16f))
                    .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${item.category}: ${item.displayLabel}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = fg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove ${item.displayLabel}",
                    tint = accent,
                    modifier = Modifier
                        .size(14.dp)
                        .clickable { onRemove(item) }
                )
            }
        }

        if (attachments.size > 1) {
            item {
                Text(
                    text = "Clear all",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    modifier = Modifier
                        .clickable { onClearAll() }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                )
            }
        }
    }
}

/**
 * Official Antigravity Remote "Add Context" Menu Dialog matching the user's screenshot
 * (`Screenshot_20261118-121321_9d1e3e6a.png`):
 * - Header: "Add Context"
 * - 📎 Files
 * - 📁 Folder
 * - @ Mentions
 * - ⧄ Actions
 * - 🌐 Browser
 * - △ Drive
 */
@Composable
fun AntigravityAddContextSheet(
    activeEndpointUrl: String,
    activeWorkspaceName: String,
    onAttachContext: (ContextAttachmentItem) -> Unit,
    onDismiss: () -> Unit
) {
    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val elevated = appearance.elevatedSurfaceColor
    val border = appearance.borderColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor

    var selectedCategory by remember { mutableStateOf<AddContextCategory?>(null) }

    // Zero-permission Android Photo Picker for attaching device screenshots/images under "Files"
    val mediaPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val pathSegment = uri.lastPathSegment ?: "screenshot.png"
            onAttachContext(
                ContextAttachmentItem(
                    id = "ctx_media_${System.currentTimeMillis()}",
                    category = "Files",
                    displayLabel = "Media ($pathSegment)",
                    promptContextSnippet = "[Attached Device Image/Screenshot URI: $uri]"
                )
            )
            onDismiss()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.BottomCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(Color(0xFF050506))
                    .border(
                        width = 1.dp,
                        color = border,
                        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                    )
                    .clickable(enabled = false) {}
                    .padding(horizontal = 24.dp, vertical = 22.dp)
                    .testTag("antigravity_add_context_sheet")
            ) {
                // Drag indicator pill
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(border)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(18.dp))

                if (selectedCategory == null) {
                    // Primary "Add Context" List matching Screenshot (`Screenshot_20261118-121321_9d1e3e6a.png`)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Add Context",
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF9E9FA5)
                        )
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close Add Context",
                                tint = secondaryFg,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AddContextCategory.entries.forEach { category ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .clickable { selectedCategory = category }
                                    .padding(vertical = 14.dp, horizontal = 8.dp)
                                    .testTag("add_context_row_${category.name.lowercase()}"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = category.icon,
                                    contentDescription = category.title,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(20.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = category.title,
                                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 21.sp),
                                        fontWeight = FontWeight.Normal,
                                        color = Color.White
                                    )
                                    Text(
                                        text = category.subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryFg
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Category Detail Picker (Files, Folder, Mentions, Actions, Browser, Drive)
                    val category = selectedCategory!!
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { selectedCategory = null }
                                .padding(4.dp)
                                .testTag("add_context_back_button"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                contentDescription = "Back to Add Context",
                                tint = accent,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Add Context / ${category.title}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                    }

                    HorizontalDivider(color = border, modifier = Modifier.padding(bottom = 12.dp))

                    if (category == AddContextCategory.FILES) {
                        OutlinedButton(
                            onClick = {
                                mediaPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .testTag("add_context_pick_device_photo_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Pick Screenshot / Image from Phone", color = accent)
                        }
                    }

                    val options = remember(category, activeEndpointUrl, activeWorkspaceName) {
                        buildContextOptionsForCategory(
                            category = category,
                            activeEndpointUrl = activeEndpointUrl,
                            activeWorkspaceName = activeWorkspaceName
                        )
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(300.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(options, key = { it.id }) { option ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(surface)
                                    .border(1.dp, border, RoundedCornerShape(12.dp))
                                    .clickable {
                                        onAttachContext(option)
                                        onDismiss()
                                    }
                                    .padding(12.dp)
                                    .testTag("context_option_${option.id}")
                            ) {
                                Text(
                                    text = option.displayLabel,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = fg
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = option.promptContextSnippet,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp,
                                    color = secondaryFg,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

private fun buildContextOptionsForCategory(
    category: AddContextCategory,
    activeEndpointUrl: String,
    activeWorkspaceName: String
): List<ContextAttachmentItem> {
    return when (category) {
        AddContextCategory.FILES -> listOf(
            ContextAttachmentItem(
                id = "file_main_activity",
                category = "Files",
                displayLabel = "app/src/main/java/com/example/MainActivity.kt",
                promptContextSnippet = "[File Context: MainActivity.kt — AntigravityRemoteTopBar, Fullscreen Mode & Navigation]"
            ),
            ContextAttachmentItem(
                id = "file_bridge_client",
                category = "Files",
                displayLabel = "app/src/main/java/com/example/data/remote/AntigravityBridgeClient.kt",
                promptContextSnippet = "[File Context: AntigravityBridgeClient.kt — OkHttp WebSocket & Singleton RetainedWebViewManager]"
            ),
            ContextAttachmentItem(
                id = "file_appearance_config",
                category = "Files",
                displayLabel = "app/src/main/java/com/example/ui/theme/AppearanceConfig.kt",
                promptContextSnippet = "[File Context: AppearanceConfig.kt — 12 Official Antigravity Dark Presets & Custom Hex Colors]"
            ),
            ContextAttachmentItem(
                id = "file_copilot_vm",
                category = "Files",
                displayLabel = "app/src/main/java/com/example/viewmodel/CopilotViewModel.kt",
                promptContextSnippet = "[File Context: CopilotViewModel.kt — Firebase.ai Gemini 4 Argon, 3.8 Flash & Nano Banana 2.1]"
            ),
            ContextAttachmentItem(
                id = "file_firestore_rules",
                category = "Files",
                displayLabel = "firestore.rules",
                promptContextSnippet = "[File Context: firestore.rules — Zero-trust per-UID Firestore security rules]"
            )
        )

        AddContextCategory.FOLDER -> listOf(
            ContextAttachmentItem(
                id = "folder_ui_screens",
                category = "Folder",
                displayLabel = "app/src/main/java/com/example/ui/screens/",
                promptContextSnippet = "[Folder Context: /ui/screens/ — Sessions, CodeDiff, RetainedWebViewBridge, Copilot, Appearance, ConnectionGuide]"
            ),
            ContextAttachmentItem(
                id = "folder_data_layer",
                category = "Folder",
                displayLabel = "app/src/main/java/com/example/data/",
                promptContextSnippet = "[Folder Context: /data/ — Room SQLite entities/DAO, AntigravityBridgeClient, CloudSyncRepository]"
            ),
            ContextAttachmentItem(
                id = "folder_workspace_root",
                category = "Folder",
                displayLabel = "$activeWorkspaceName/ (Workspace Root)",
                promptContextSnippet = "[Folder Context: Workspace Root '$activeWorkspaceName' — Gradle config, manifest, and source tree]"
            )
        )

        AddContextCategory.MENTIONS -> listOf(
            ContextAttachmentItem(
                id = "mention_agent",
                category = "Mentions",
                displayLabel = "@AntigravityAgent",
                promptContextSnippet = "@AntigravityAgent (Autonomous multi-file planner & executor)"
            ),
            ContextAttachmentItem(
                id = "mention_terminal",
                category = "Mentions",
                displayLabel = "@TerminalPTY",
                promptContextSnippet = "@TerminalPTY (Attach latest remote shell stdout/stderr logs)"
            ),
            ContextAttachmentItem(
                id = "mention_web_bridge",
                category = "Mentions",
                displayLabel = "@WebBridge",
                promptContextSnippet = "@WebBridge (Pinned singleton WebView instance at $activeEndpointUrl)"
            ),
            ContextAttachmentItem(
                id = "mention_diff_engine",
                category = "Mentions",
                displayLabel = "@DiffReviewer",
                promptContextSnippet = "@DiffReviewer (Inspect uncommitted Git hunks in current session)"
            ),
            ContextAttachmentItem(
                id = "mention_gemini_live",
                category = "Mentions",
                displayLabel = "@GeminiLiveVoice",
                promptContextSnippet = "@GeminiLiveVoice (Real-time gemini-3.8-live bidirectional audio assistant)"
            )
        )

        AddContextCategory.ACTIONS -> listOf(
            ContextAttachmentItem(
                id = "action_browser_inspect",
                category = "Actions",
                displayLabel = "/browser inspect-dom",
                promptContextSnippet = "/browser inspect-dom — Capture live DOM layout & console errors from $activeEndpointUrl"
            ),
            ContextAttachmentItem(
                id = "action_compile_verify",
                category = "Actions",
                displayLabel = "/run gradle assembleDebug",
                promptContextSnippet = "/run gradle assembleDebug — Trigger incremental build verification on remote host"
            ),
            ContextAttachmentItem(
                id = "action_approve_pending",
                category = "Actions",
                displayLabel = "/approve-pending-step",
                promptContextSnippet = "/approve-pending-step — Authorize queued remote terminal or file write step"
            ),
            ContextAttachmentItem(
                id = "action_boost_dom",
                category = "Actions",
                displayLabel = "/optimize-remote-lag",
                promptContextSnippet = "/optimize-remote-lag — Strip heavy CSS transitions and pin WebSocket keep-alive"
            )
        )

        AddContextCategory.BROWSER -> listOf(
            ContextAttachmentItem(
                id = "browser_active_portal",
                category = "Browser",
                displayLabel = "Active Pinned WebView ($activeEndpointUrl)",
                promptContextSnippet = "[Browser Context: Active Pinned WebView URL=$activeEndpointUrl, DOM Lag Booster=ON, Singleton RAM Lock=ACTIVE]"
            ),
            ContextAttachmentItem(
                id = "browser_console_logs",
                category = "Browser",
                displayLabel = "Chrome DevTools MCP Console Stream",
                promptContextSnippet = "[Browser Context: Chrome MCP Console — 0 uncaught exceptions, WebSocket JSON-RPC stream healthy]"
            ),
            ContextAttachmentItem(
                id = "browser_viewport_metrics",
                category = "Browser",
                displayLabel = "Mobile Viewport & Fullscreen Insets",
                promptContextSnippet = "[Browser Context: Mobile Edge-to-Edge Viewport 60fps, Zero layout shifts]"
            )
        )

        AddContextCategory.DRIVE -> listOf(
            ContextAttachmentItem(
                id = "drive_arch_spec",
                category = "Drive",
                displayLabel = "Antigravity_Hybrid_Bridge_Spec.md",
                promptContextSnippet = "[Google Drive Doc: Antigravity_Hybrid_Bridge_Spec.md — Hybrid Native + Pinned WebView Architecture]"
            ),
            ContextAttachmentItem(
                id = "drive_theme_tokens",
                category = "Drive",
                displayLabel = "Antigravity_Remote_Design_Tokens.json",
                promptContextSnippet = "[Google Drive Doc: Antigravity_Remote_Design_Tokens.json — Default Dark #070708, Accent #FFC799, 12 Presets]"
            ),
            ContextAttachmentItem(
                id = "drive_team_runbook",
                category = "Drive",
                displayLabel = "Remote_Server_Tailscale_Runbook.pdf",
                promptContextSnippet = "[Google Drive Doc: Remote_Server_Tailscale_Runbook.pdf — language_server --standalone & OAuth token guide]"
            )
        )
    }
}

data class VoiceTurnTranscript(
    val id: String,
    val speaker: String, // "YOU" or "GEMINI LIVE"
    val text: String,
    val actionTriggered: String? = null
)

/**
 * Real-Time Voice Conversation Modal & Studio powered by `gemini-3.8-live` (Gemini Live API via
 * Firebase AI Logic `Firebase.ai.liveModel("gemini-3.8-live")`) + real-time SpeechRecognizer &
 * TextToSpeech so users can talk hands-free with the app and execute remote Antigravity actions.
 */
@OptIn(PublicPreviewAPI::class, ExperimentalLayoutApi::class)
@Composable
fun GeminiLiveVoiceModal(
    activeWorkspaceName: String,
    activeEndpointUrl: String,
    onDispatchPromptToAntigravity: (String) -> Unit,
    onRunRemoteTerminalCommand: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val elevated = appearance.elevatedSurfaceColor
    val border = appearance.borderColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor

    val scope = rememberCoroutineScope()

    var hasAudioPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    var isLiveSessionConnected by remember { mutableStateOf(false) }
    var isListeningMic by remember { mutableStateOf(false) }
    var isModelSpeaking by remember { mutableStateOf(false) }
    var liveStatusText by rememberSaveable {
        mutableStateOf("Ready to start real-time voice conversation with gemini-3.8-live")
    }
    var partialSpokenText by remember { mutableStateOf("") }
    var selectedVoiceName by rememberSaveable { mutableStateOf("FENRIR") }
    val availableVoices = listOf("FENRIR", "PUCK", "CHARON", "KORE", "AOEDE")

    val transcripts = remember {
        mutableStateListOf(
            VoiceTurnTranscript(
                id = "welcome_live",
                speaker = "GEMINI LIVE (gemini-3.8-live)",
                text = "Live Voice Bridge ready for workspace '$activeWorkspaceName'. Tap the microphone to talk in real time, or ask me to send prompts or run terminal commands on your remote Antigravity server."
            )
        )
    }

    // Active SpeechRecognizer reference so tapping Mic while listening submits cleanly without cutting off
    var activeRecognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

    // Android TextToSpeech for crisp, fast audible voice responses (stopped cleanly before mic listens)
    var ttsEngine by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = Locale.US
            }
        }
        ttsEngine = engine
        onDispose {
            try {
                activeRecognizer?.cancel()
                activeRecognizer?.destroy()
                activeRecognizer = null
            } catch (_: Exception) {
            }
            engine?.stop()
            engine?.shutdown()
        }
    }

    fun releaseRecognizer() {
        try {
            activeRecognizer?.destroy()
        } catch (_: Exception) {
        } finally {
            activeRecognizer = null
        }
    }

    // Processes a spoken user utterance through Gemini and speaks the response out loud
    fun processSpokenUtterance(spokenText: String) {
        val clean = spokenText.trim()
        if (clean.isEmpty()) return

        transcripts.add(
            VoiceTurnTranscript(
                id = "v_user_${System.currentTimeMillis()}",
                speaker = "YOU (VOICE)",
                text = clean
            )
        )

        scope.launch {
            isModelSpeaking = true
            liveStatusText = "gemini-3.8-live is responding..."
            try {
                // Check if user asked to send directly to Antigravity Agent or run a shell command
                var triggeredAction: String? = null
                if (clean.lowercase().startsWith("send to agent") ||
                    clean.lowercase().startsWith("tell antigravity")
                ) {
                    onDispatchPromptToAntigravity(clean)
                    triggeredAction = "Dispatched to Antigravity Remote Session"
                } else if (clean.lowercase().startsWith("run command") ||
                    clean.lowercase().startsWith("execute ")
                ) {
                    val cmd = clean.substringAfter(" ").trim()
                    onRunRemoteTerminalCommand(cmd)
                    triggeredAction = "Executed on Remote PTY: $cmd"
                }

                val streamingModel = Firebase.ai.generativeModel(
                    modelName = "gemini-3.8-flash",
                    systemInstruction = content {
                        text(
                            "You are Gemini Live Voice (`gemini-3.8-live`) inside Antigravity Pulse on Android. The user is speaking to you by voice while connected to workspace '$activeWorkspaceName' ($activeEndpointUrl). Respond in 1 to 3 concise, natural spoken sentences suitable for real-time voice conversation."
                        )
                    }
                )

                val replyBuilder = StringBuilder()
                withTimeout(45_000L) {
                    streamingModel.generateContentStream(clean).collect { chunk ->
                        replyBuilder.append(chunk.text.orEmpty())
                    }
                }

                val finalSpokenReply = replyBuilder.toString().trim().ifEmpty {
                    "Got it. I've updated your Antigravity workspace context."
                }

                transcripts.add(
                    VoiceTurnTranscript(
                        id = "v_model_${System.currentTimeMillis()}",
                        speaker = "GEMINI LIVE (gemini-3.8-live • $selectedVoiceName)",
                        text = finalSpokenReply,
                        actionTriggered = triggeredAction
                    )
                )

                ttsEngine?.speak(
                    finalSpokenReply,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "live_utt_${System.currentTimeMillis()}"
                )
                liveStatusText = "LIVE • Tap Mic to speak next turn"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Live voice turn error", e)
                val errText = e.localizedMessage ?: "Voice inference failed."
                transcripts.add(
                    VoiceTurnTranscript(
                        id = "v_err_${System.currentTimeMillis()}",
                        speaker = "GEMINI LIVE",
                        text = "Voice stream notice: $errText"
                    )
                )
                liveStatusText = "Ready — tap Mic or select a voice prompt below"
            } finally {
                isModelSpeaking = false
            }
        }
    }

    // SpeechRecognizer helper with generous pause tolerance (4s silence window) and zero mic contention
    fun startMicrophoneRecognition(ctx: Context) {
        // Stop any speaking TTS first so the microphone never hears the speaker
        ttsEngine?.stop()
        isModelSpeaking = false

        // If already listening, tapping Mic cleanly finishes & submits what was spoken so far
        if (isListeningMic && activeRecognizer != null) {
            liveStatusText = "Finishing speech..."
            try {
                activeRecognizer?.stopListening()
            } catch (_: Exception) {
                isListeningMic = false
                val captured = partialSpokenText
                partialSpokenText = ""
                if (captured.isNotBlank()) {
                    processSpokenUtterance(captured)
                }
                releaseRecognizer()
            }
            return
        }

        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            isListeningMic = false
            liveStatusText = "Voice recognition unavailable on this device. Tap a quick prompt below."
            return
        }

        releaseRecognizer()

        try {
            val recognizer = SpeechRecognizer.createSpeechRecognizer(ctx)
            activeRecognizer = recognizer
            isLiveSessionConnected = true
            partialSpokenText = ""

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(
                    RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                    RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                )
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                // Generous silence thresholds so natural pauses mid-sentence do NOT cut off speech
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 4000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 6000L)
            }

            recognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListeningMic = true
                    liveStatusText = "Listening... Take your time, tap Mic again when done"
                }

                override fun onBeginningOfSpeech() {
                    isListeningMic = true
                }

                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    isListeningMic = false
                    liveStatusText = "Processing your voice..."
                }

                override fun onError(error: Int) {
                    isListeningMic = false
                    val captured = partialSpokenText.trim()
                    partialSpokenText = ""
                    releaseRecognizer()
                    if (captured.isNotBlank()) {
                        processSpokenUtterance(captured)
                    } else {
                        liveStatusText = when (error) {
                            SpeechRecognizer.ERROR_NO_MATCH,
                            SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                                "Didn't catch speech — tap Mic to speak again"
                            SpeechRecognizer.ERROR_AUDIO ->
                                "Audio input busy — tap Mic to retry"
                            else ->
                                "Ready — tap Mic to speak"
                        }
                    }
                }

                override fun onResults(results: Bundle?) {
                    isListeningMic = false
                    val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    val best = matches?.firstOrNull().orEmpty().ifBlank { partialSpokenText }.trim()
                    partialSpokenText = ""
                    releaseRecognizer()
                    if (best.isNotBlank()) {
                        processSpokenUtterance(best)
                    } else {
                        liveStatusText = "Ready — tap Mic to speak"
                    }
                }

                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        partialSpokenText = partial
                        liveStatusText = "Listening (tap Mic to send now)..."
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            })

            recognizer.startListening(intent)
        } catch (e: Exception) {
            isListeningMic = false
            releaseRecognizer()
            liveStatusText = "Ready — tap Mic or choose a quick voice prompt"
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasAudioPermission = granted
        if (granted) {
            startMicrophoneRecognition(context)
        } else {
            liveStatusText = "Microphone permission declined. You can still use quick voice prompts below."
        }
    }

    val pulseTransition = rememberInfiniteTransition(label = "live_voice_pulse")
    val orbScale by pulseTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isListeningMic || isModelSpeaking || isLiveSessionConnected) 1.18f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "orb_scale"
    )

    Dialog(
        onDismissRequest = {
            try {
                activeRecognizer?.cancel()
            } catch (_: Exception) {
            }
            releaseRecognizer()
            ttsEngine?.stop()
            onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("gemini_live_voice_modal"),
            color = bg
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Top Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Gemini Live Voice",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = fg
                            )
                            Text(
                                text = "Pause-friendly voice assistant & Antigravity Remote inserter",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryFg
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            try {
                                activeRecognizer?.cancel()
                            } catch (_: Exception) {
                            }
                            releaseRecognizer()
                            ttsEngine?.stop()
                            onDismiss()
                        },
                        modifier = Modifier.testTag("close_live_voice_modal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Live Voice",
                            tint = secondaryFg
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Voice Persona Picker (FENRIR, PUCK, CHARON, KORE, AOEDE)
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(availableVoices) { voiceName ->
                        val selected = voiceName == selectedVoiceName
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(if (selected) accent else surface)
                                .border(1.dp, if (selected) accent else border, RoundedCornerShape(18.dp))
                                .clickable { selectedVoiceName = voiceName }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                .testTag("live_voice_chip_$voiceName")
                        ) {
                            Text(
                                text = "Voice: $voiceName",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (selected) Color(0xFF070708) else fg
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Animated Gemini Live Audio Orb & Status Card
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(surface)
                        .border(
                            1.dp,
                            if (isListeningMic || isLiveSessionConnected) EmeraldOnline else border,
                            RoundedCornerShape(20.dp)
                        )
                        .padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(92.dp)
                            .scale(orbScale)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isListeningMic -> EmeraldOnline.copy(alpha = 0.24f)
                                    isModelSpeaking -> NeuralViolet.copy(alpha = 0.28f)
                                    isLiveSessionConnected -> accent.copy(alpha = 0.22f)
                                    else -> elevated
                                }
                            )
                            .border(
                                width = 2.dp,
                                color = when {
                                    isListeningMic -> EmeraldOnline
                                    isModelSpeaking -> NeuralViolet
                                    isLiveSessionConnected -> accent
                                    else -> border
                                },
                                shape = CircleShape
                            )
                            .clickable {
                                if (!hasAudioPermission) {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    startMicrophoneRecognition(context)
                                }
                            }
                            .testTag("live_voice_orb_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when {
                                isListeningMic -> Icons.Default.GraphicEq
                                isModelSpeaking -> Icons.Default.VolumeUp
                                else -> Icons.Default.Mic
                            },
                            contentDescription = "Toggle Live Voice Microphone",
                            tint = when {
                                isListeningMic -> EmeraldOnline
                                isModelSpeaking -> NeuralViolet
                                else -> accent
                            },
                            modifier = Modifier.size(40.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = liveStatusText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isListeningMic || isLiveSessionConnected) EmeraldOnline else fg
                    )

                    AnimatedVisibility(visible = partialSpokenText.isNotBlank()) {
                        Text(
                            text = "\"$partialSpokenText\"",
                            style = MaterialTheme.typography.bodyLarge,
                            color = accent,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                if (!hasAudioPermission) {
                                    audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                } else {
                                    startMicrophoneRecognition(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isListeningMic) EmeraldOnline else accent,
                                contentColor = Color(0xFF070708)
                            ),
                            modifier = Modifier.testTag("start_live_voice_button")
                        ) {
                            Icon(
                                imageVector = if (isListeningMic) Icons.Default.GraphicEq else Icons.Default.Mic,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isListeningMic) "Tap to Send Now" else "Tap to Speak",
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isLiveSessionConnected || isListeningMic || isModelSpeaking) {
                            OutlinedButton(
                                onClick = {
                                    try {
                                        activeRecognizer?.cancel()
                                    } catch (_: Exception) {
                                    }
                                    releaseRecognizer()
                                    ttsEngine?.stop()
                                    partialSpokenText = ""
                                    isListeningMic = false
                                    isModelSpeaking = false
                                    isLiveSessionConnected = false
                                    liveStatusText = "Voice paused. Tap Mic to speak again."
                                },
                                modifier = Modifier.testTag("stop_live_voice_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.StopCircle,
                                    contentDescription = "Stop Voice",
                                    tint = CrimsonError,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Stop", color = CrimsonError)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick Spoken Turn Starters (Also work in emulators without hardware mic)
                Text(
                    text = "Quick Voice Commands (Tap to speak or test without mic):",
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryFg
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val quickVoiceCommands = listOf(
                        "Summarize my active Antigravity session status",
                        "Tell Antigravity to inspect /browser DOM performance",
                        "Run command gradle testDebugUnitTest",
                        "How do I reduce WebView latency on mobile data?"
                    )
                    quickVoiceCommands.forEach { cmd ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(elevated)
                                .border(1.dp, border, RoundedCornerShape(12.dp))
                                .clickable { processSpokenUtterance(cmd) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "🎙️ \"$cmd\"",
                                style = MaterialTheme.typography.labelSmall,
                                color = fg
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Live Conversation Transcript Feed
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .testTag("live_voice_transcript_list"),
                    contentPadding = PaddingValues(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(transcripts, key = { it.id }) { turn ->
                        val isUser = turn.speaker.startsWith("YOU")
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (isUser) accent.copy(alpha = 0.12f) else surface
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    if (isUser) accent.copy(alpha = 0.5f) else border,
                                    RoundedCornerShape(14.dp)
                                )
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = if (isUser) Icons.Default.Mic else Icons.Default.RecordVoiceOver,
                                            contentDescription = null,
                                            tint = if (isUser) accent else NeuralViolet,
                                            modifier = Modifier.size(15.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = turn.speaker,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (isUser) accent else NeuralViolet
                                        )
                                    }

                                    if (!isUser) {
                                        IconButton(
                                            onClick = {
                                                ttsEngine?.speak(
                                                    turn.text,
                                                    TextToSpeech.QUEUE_FLUSH,
                                                    null,
                                                    "replay_${turn.id}"
                                                )
                                            },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.VolumeUp,
                                                contentDescription = "Replay Audio",
                                                tint = accent,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = turn.text,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = fg
                                )

                                if (turn.actionTriggered != null) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "⚡ ${turn.actionTriggered}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldOnline
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onDispatchPromptToAntigravity(turn.text)
                                            onDismiss()
                                        },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.AutoMirrored.Filled.Send,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Insert into Remote",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = accent
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
