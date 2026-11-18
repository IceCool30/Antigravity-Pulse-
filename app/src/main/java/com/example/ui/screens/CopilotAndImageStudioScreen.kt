package com.example.ui.screens

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.CopilotMessageEntity
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalAppearanceSettings
import com.example.ui.theme.NeuralViolet
import com.example.viewmodel.CopilotChatUiState
import com.example.viewmodel.CopilotRolePersona
import com.example.viewmodel.GeminiCopilotModel
import com.example.viewmodel.ImageStudioUiState

@Composable
fun CopilotAndImageStudioScreen(
    messages: List<CopilotMessageEntity>,
    selectedModel: GeminiCopilotModel,
    selectedPersona: CopilotRolePersona,
    chatUiState: CopilotChatUiState,
    imageUiState: ImageStudioUiState,
    selectedInputBitmap: android.graphics.Bitmap?,
    onSelectModel: (GeminiCopilotModel) -> Unit,
    onSelectPersona: (CopilotRolePersona) -> Unit,
    onSendChatMessage: (String) -> Unit,
    onForwardToAntigravityAgent: (String) -> Unit,
    onSetInputBitmap: (android.graphics.Bitmap?) -> Unit,
    onGenerateOrEditImage: (String) -> Unit
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

    var activeMode by rememberSaveable { mutableIntStateOf(0) } // 0 = Gemini Copilot Chat, 1 = Nano Banana Image Studio
    var chatInput by rememberSaveable { mutableStateOf("") }
    var imagePromptInput by rememberSaveable { mutableStateOf("") }

    // Zero-permission Android Photo Picker for image editing
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val decoded = BitmapFactory.decodeStream(stream)
                    if (decoded != null) {
                        onSetInputBitmap(decoded)
                    }
                }
            } catch (_: Exception) {
                // Ignore invalid media selection
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Top Mode Switcher: Multi-Turn Gemini Copilot vs Nano Banana 2.1 Asset Studio
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = activeMode == 0,
                onClick = { activeMode = 0 },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Gemini Copilot Chat",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Gemini Copilot")
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = NeuralViolet.copy(alpha = 0.22f),
                    selectedLabelColor = NeuralViolet
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("copilot_mode_chat")
            )

            FilterChip(
                selected = activeMode == 1,
                onClick = { activeMode = 1 },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Brush,
                            contentDescription = "Nano Banana 2.1 Image Studio",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("UI Image Studio")
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.22f),
                    selectedLabelColor = accent
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("copilot_mode_image_studio")
            )
        }

        if (activeMode == 0) {
            // 1. Model Selector Row (gemini-4-argon, gemini-3.8-flash, gemini-3.5-flash-lite)
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp)
            ) {
                items(GeminiCopilotModel.entries) { model ->
                    val isSelected = model == selectedModel
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectModel(model) },
                        label = {
                            Text("${model.displayTitle} (${model.badgeLabel})")
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NeuralViolet.copy(alpha = 0.2f),
                            selectedLabelColor = NeuralViolet,
                            containerColor = surface,
                            labelColor = secondaryFg
                        ),
                        modifier = Modifier.testTag("model_chip_${model.modelId}")
                    )
                }
            }

            // 2. Role Persona Selector Row
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(CopilotRolePersona.entries) { persona ->
                    val isSelected = persona == selectedPersona
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectPersona(persona) },
                        label = { Text(persona.title) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedLabelColor = accent,
                            containerColor = surface,
                            labelColor = secondaryFg
                        ),
                        modifier = Modifier.testTag("persona_chip_${persona.name}")
                    )
                }
            }

            // 3. Scrollable Multi-Turn Conversation Thread
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("copilot_message_list"),
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    val isUser = msg.senderRole == "USER"
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isUser) NeuralViolet.copy(alpha = 0.12f) else surface
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(
                                1.dp,
                                if (isUser) NeuralViolet.copy(alpha = 0.45f) else border,
                                RoundedCornerShape(14.dp)
                            )
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isUser) "YOU" else "${msg.rolePersona} • ${msg.modelUsed}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isUser) NeuralViolet else accent
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = msg.text,
                                style = MaterialTheme.typography.bodyLarge,
                                color = fg
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = { onForwardToAntigravityAgent(msg.text) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier.testTag("forward_to_agent_${msg.id}")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.RocketLaunch,
                                        contentDescription = "Send to Antigravity Agent",
                                        tint = accent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Send to Antigravity Agent",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = accent
                                    )
                                }
                            }
                        }
                    }
                }

                // Streaming / Loading / Error state cards
                when (chatUiState) {
                    is CopilotChatUiState.Loading -> {
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = NeuralViolet,
                                    strokeWidth = 2.dp
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "${selectedModel.displayTitle} is reasoning...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryFg
                                )
                            }
                        }
                    }
                    is CopilotChatUiState.Streaming -> {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = surface),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, NeuralViolet, RoundedCornerShape(14.dp))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "STREAMING • ${selectedModel.modelId}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = NeuralViolet
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = chatUiState.partialText,
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = fg
                                    )
                                }
                            }
                        }
                    }
                    is CopilotChatUiState.Error -> {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = CrimsonError.copy(alpha = 0.14f)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .border(1.dp, CrimsonError, RoundedCornerShape(12.dp))
                                    .testTag("copilot_error_card")
                            ) {
                                Text(
                                    text = "Gemini Error: ${chatUiState.message}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CrimsonError,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                    is CopilotChatUiState.Idle -> Unit
                }
            }

            // 4. Copilot Chat Input Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = chatInput,
                    onValueChange = { chatInput = it },
                    placeholder = {
                        Text(
                            "Draft an Antigravity agent prompt or debug architecture...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryFg
                        )
                    },
                    maxLines = 3,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("copilot_chat_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = {
                        if (chatInput.isNotBlank()) {
                            val toSend = chatInput
                            chatInput = ""
                            onSendChatMessage(toSend)
                        }
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NeuralViolet)
                        .testTag("copilot_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send to Gemini Copilot",
                        tint = Color.White
                    )
                }
            }
        } else {
            // Nano Banana 2.1 (gemini-nano-banana-2.1) Image Generation & Editing Studio
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = surface),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, border, RoundedCornerShape(14.dp))
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Nano Banana 2.1 UI Asset & Mockup Studio",
                                style = MaterialTheme.typography.titleLarge,
                                color = accent
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Powered by `gemini-nano-banana-2.1`. Generate new UI mockups, architecture diagrams, or icons from text prompts—or pick an existing image from your device to edit with text instructions.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        photoPickerLauncher.launch(
                                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                        )
                                    },
                                    modifier = Modifier.testTag("pick_image_to_edit_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Image,
                                        contentDescription = "Pick Image to Edit",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        if (selectedInputBitmap == null) "Pick Image to Edit"
                                        else "Change Source Image"
                                    )
                                }

                                if (selectedInputBitmap != null) {
                                    OutlinedButton(
                                        onClick = { onSetInputBitmap(null) },
                                        modifier = Modifier.testTag("clear_source_image_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Clear,
                                            contentDescription = "Clear Source Image",
                                            tint = CrimsonError,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("New Image Mode", color = CrimsonError)
                                    }
                                }
                            }

                            if (selectedInputBitmap != null) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Edit Mode Active: Your prompt below will modify the selected image.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldOnline
                                )
                            }
                        }
                    }
                }

                // Quick Prompt Suggestions
                item {
                    val samplePrompts = listOf(
                        "Dark obsidian mobile IDE dashboard mockup with neon cyan telemetry charts",
                        "Minimalist geometric app icon for a quantum WebSocket bridge",
                        "Add a glowing purple AI copilot sidebar to the layout"
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(samplePrompts) { sample ->
                            SuggestionChip(
                                onClick = { imagePromptInput = sample },
                                label = { Text(sample) }
                            )
                        }
                    }
                }

                // Image Output Canvas
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(elevated)
                            .border(1.dp, border, RoundedCornerShape(14.dp))
                            .padding(16.dp)
                            .testTag("image_studio_canvas"),
                        contentAlignment = Alignment.Center
                    ) {
                        when (val state = imageUiState) {
                            is ImageStudioUiState.Idle -> {
                                if (selectedInputBitmap != null) {
                                    Image(
                                        bitmap = selectedInputBitmap.asImageBitmap(),
                                        contentDescription = "Selected Source Image for Editing",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(240.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        modifier = Modifier.padding(vertical = 32.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Brush,
                                            contentDescription = null,
                                            tint = accent,
                                            modifier = Modifier.size(40.dp)
                                        )
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "Enter a prompt below to synthesize or edit a UI asset with gemini-nano-banana-2.1",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = secondaryFg
                                        )
                                    }
                                }
                            }
                            is ImageStudioUiState.Loading -> {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.padding(vertical = 36.dp)
                                ) {
                                    CircularProgressIndicator(color = accent)
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = "Synthesizing image with gemini-nano-banana-2.1 (may take 30–60s)...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = fg
                                    )
                                }
                            }
                            is ImageStudioUiState.Success -> {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Image(
                                        bitmap = state.bitmap.asImageBitmap(),
                                        contentDescription = state.promptUsed,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(260.dp)
                                            .clip(RoundedCornerShape(12.dp)),
                                        contentScale = ContentScale.Fit
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = if (state.wasEdited) "Edited with gemini-nano-banana-2.1"
                                        else "Generated with gemini-nano-banana-2.1",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldOnline
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            onForwardToAntigravityAgent(
                                                "Update workspace UI to match generated Nano Banana asset spec: \"${state.promptUsed}\""
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = accent,
                                            contentColor = Color(0xFF070708)
                                        ),
                                        modifier = Modifier.testTag("send_asset_spec_to_agent_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.RocketLaunch,
                                            contentDescription = "Send Asset Spec to Antigravity Agent",
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Send Asset Spec to Antigravity Agent", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                            is ImageStudioUiState.Error -> {
                                Text(
                                    text = "Image Studio Error: ${state.message}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = CrimsonError,
                                    modifier = Modifier.padding(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Image Prompt Composer
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = imagePromptInput,
                    onValueChange = { imagePromptInput = it },
                    placeholder = {
                        Text(
                            if (selectedInputBitmap == null) "Describe UI mockup or icon to create..."
                            else "Describe how to edit the selected image...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryFg
                        )
                    },
                    maxLines = 2,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("image_studio_prompt_input")
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = {
                        if (imagePromptInput.isNotBlank()) {
                            onGenerateOrEditImage(imagePromptInput)
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF070708)
                    ),
                    modifier = Modifier
                        .height(52.dp)
                        .testTag("image_studio_generate_button")
                ) {
                    Text(
                        text = if (selectedInputBitmap == null) "Create" else "Edit",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
