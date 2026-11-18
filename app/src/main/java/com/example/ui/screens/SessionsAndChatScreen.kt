package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.AgentSessionEntity
import com.example.data.local.SessionMessageEntity
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAppearanceSettings
import com.example.ui.theme.NeuralViolet

@Composable
fun SessionsAndChatScreen(
    sessions: List<AgentSessionEntity>,
    selectedSessionId: String,
    messages: List<SessionMessageEntity>,
    activeEndpointUrl: String,
    isFullscreenBarsHidden: Boolean,
    searchQuery: String,
    onClearSearch: () -> Unit,
    onSelectSession: (String) -> Unit,
    onOpenNewSessionModal: () -> Unit,
    onOpenServerModal: () -> Unit,
    onSendPrompt: (String) -> Unit,
    onResolveApproval: (SessionMessageEntity, Boolean) -> Unit,
    onUpdateDraft: (String, String, Int) -> Unit
) {
    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val elevated = appearance.elevatedSurfaceColor
    val border = appearance.borderColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor

    val activeSession = sessions.firstOrNull { it.id == selectedSessionId } ?: sessions.firstOrNull()
    val pendingApprovalMsg = messages.lastOrNull {
        it.senderRole == "TOOL_APPROVAL" && it.approvalStatus == "PENDING"
    }

    val displayedMessages = if (searchQuery.isBlank()) {
        messages
    } else {
        messages.filter {
            it.content.contains(searchQuery, ignoreCase = true) ||
                it.commandOrPath.contains(searchQuery, ignoreCase = true)
        }
    }

    var promptInput by rememberSaveable(selectedSessionId) {
        mutableStateOf(activeSession?.draftPrompt.orEmpty())
    }
    var showAddContextSheet by rememberSaveable { mutableStateOf(false) }
    var showLiveVoiceModal by rememberSaveable { mutableStateOf(false) }
    val attachedContexts = remember { mutableStateListOf<ContextAttachmentItem>() }

    val listState = rememberLazyListState()
    LaunchedEffect(displayedMessages.size, selectedSessionId) {
        if (displayedMessages.isNotEmpty()) {
            listState.animateScrollToItem(displayedMessages.size - 1)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Optional compact session switcher strip (automatically hidden in Distraction-Free Fullscreen Mode)
        AnimatedVisibility(visible = !isFullscreenBarsHidden) {
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(sessions, key = { it.id }) { session ->
                    val isSelected = session.id == selectedSessionId
                    val statusColor = when (session.status) {
                        "AWAITING_APPROVAL" -> AmberPending
                        "ACTIVE" -> EmeraldOnline
                        else -> accent
                    }
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectSession(session.id) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(statusColor)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "${session.workspaceName} (${session.branchName})",
                                    maxLines = 1
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = 0.18f),
                            selectedLabelColor = accent,
                            containerColor = surface,
                            labelColor = secondaryFg
                        ),
                        modifier = Modifier.testTag("session_chip_${session.id}")
                    )
                }
                item {
                    OutlinedButton(
                        onClick = onOpenNewSessionModal,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("new_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Session",
                            tint = accent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New", style = MaterialTheme.typography.labelLarge, color = accent)
                    }
                }
            }
        }

        if (searchQuery.isNotBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(elevated)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Filtering messages by \"$searchQuery\" (${displayedMessages.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = accent
                )
                IconButton(
                    onClick = onClearSearch,
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear Search",
                        tint = secondaryFg,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        // Fullscreen Native Zero-Lag Conversation Stream
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .testTag("session_message_list"),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (displayedMessages.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = surface),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                            .border(1.dp, border, RoundedCornerShape(16.dp))
                            .testTag("sessions_clean_empty_state")
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = if (activeEndpointUrl.isBlank()) {
                                    "Ready to Connect Your Antigravity Remote Host"
                                } else {
                                    "Connected to $activeEndpointUrl"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = fg
                            )
                            Text(
                                text = "Your workspace is clean with zero dummy data. Connect to your Google Antigravity remote server, create a workspace session, or send your first prompt below.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = onOpenServerModal,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = accent,
                                        contentColor = Color(0xFF070708)
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Connect Host", fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = onOpenNewSessionModal,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("New Session", color = fg)
                                }
                            }
                        }
                    }
                }
            }
            items(displayedMessages, key = { it.id }) { message ->
                SessionMessageCard(
                    message = message,
                    bg = bg,
                    surface = surface,
                    border = border,
                    fg = fg,
                    secondaryFg = secondaryFg,
                    accent = accent,
                    fontSizePx = appearance.fontSizePx,
                    onApprove = { onResolveApproval(message, true) },
                    onReject = { onResolveApproval(message, false) }
                )
            }
        }

        // Sticky Pending Approval Bar (if any step is awaiting approval)
        AnimatedVisibility(visible = pendingApprovalMsg != null) {
            pendingApprovalMsg?.let { approval ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = elevated),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .border(1.dp, AmberPending, RoundedCornerShape(12.dp))
                        .testTag("sticky_approval_bar")
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Approval Required",
                                tint = AmberPending,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "STEP APPROVAL REQUIRED ON REMOTE HOST",
                                style = MaterialTheme.typography.labelSmall,
                                color = AmberPending
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = approval.commandOrPath,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 12.sp,
                            color = fg,
                            maxLines = 2
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            OutlinedButton(
                                onClick = { onResolveApproval(approval, false) },
                                modifier = Modifier.testTag("reject_step_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "Reject",
                                    tint = CrimsonError,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Reject", color = CrimsonError)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = { onResolveApproval(approval, true) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = EmeraldOnline,
                                    contentColor = Color(0xFF070708)
                                ),
                                modifier = Modifier.testTag("approve_step_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Approve",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Approve & Run", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Attached Context Chips Row (Files, Folder, Mentions, Actions, Browser, Drive)
        AttachedContextChipsRow(
            attachments = attachedContexts,
            accent = accent,
            elevated = elevated,
            border = border,
            fg = fg,
            onRemove = { attachedContexts.remove(it) },
            onClearAll = { attachedContexts.clear() }
        )

        // Native Prompt Composer (+ Add Context, Text Input, Live Voice gemini-3.8-live, Send)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // + Add Context Button (Matches Screenshot `Screenshot_20261118-121321_9d1e3e6a.png`)
            IconButton(
                onClick = { showAddContextSheet = true },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(elevated)
                    .border(1.dp, border, RoundedCornerShape(12.dp))
                    .testTag("open_add_context_sheet_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Context (Files, Folder, Mentions, Actions, Browser, Drive)",
                    tint = accent
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            OutlinedTextField(
                value = promptInput,
                onValueChange = { newText ->
                    promptInput = newText
                    onUpdateDraft(selectedSessionId, newText, listState.firstVisibleItemIndex)
                },
                placeholder = {
                    Text(
                        "Ask Antigravity, tap + for context, or 🎙️ Live...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("agent_prompt_input"),
                maxLines = 3
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Gemini Live Voice (gemini-3.8-live) Button
            IconButton(
                onClick = { showLiveVoiceModal = true },
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(NeuralViolet.copy(alpha = 0.2f))
                    .border(1.dp, NeuralViolet.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                    .testTag("open_live_voice_session_button")
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Voice Conversation (gemini-3.8-live)",
                    tint = NeuralViolet
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = {
                    if (promptInput.isNotBlank() || attachedContexts.isNotEmpty()) {
                        val contextPrefix = if (attachedContexts.isNotEmpty()) {
                            attachedContexts.joinToString("\n") { it.promptContextSnippet } + "\n\n"
                        } else {
                            ""
                        }
                        val combined = (contextPrefix + promptInput.ifBlank {
                            "Execute with the attached context items."
                        }).trim()
                        promptInput = ""
                        attachedContexts.clear()
                        onSendPrompt(combined)
                    }
                },
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent)
                    .testTag("send_agent_prompt_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send Prompt to Antigravity",
                    tint = Color(0xFF070708)
                )
            }
        }
    }

    if (showAddContextSheet) {
        AntigravityAddContextSheet(
            activeEndpointUrl = activeEndpointUrl,
            activeWorkspaceName = activeSession?.workspaceName ?: "workspace",
            onAttachContext = { item ->
                if (attachedContexts.none { it.id == item.id }) {
                    attachedContexts.add(item)
                }
                if (item.category == "Actions" && promptInput.isBlank()) {
                    promptInput = item.displayLabel + " "
                } else if (item.category == "Mentions" && !promptInput.contains(item.displayLabel)) {
                    promptInput = (promptInput + " " + item.displayLabel).trimStart() + " "
                }
            },
            onDismiss = { showAddContextSheet = false }
        )
    }

    if (showLiveVoiceModal) {
        GeminiLiveVoiceModal(
            activeWorkspaceName = activeSession?.workspaceName ?: "workspace",
            activeEndpointUrl = activeEndpointUrl,
            onDispatchPromptToAntigravity = { spokenPrompt ->
                onSendPrompt("[Live Voice Command]: $spokenPrompt")
            },
            onRunRemoteTerminalCommand = { spokenCmd ->
                onSendPrompt("/run $spokenCmd")
            },
            onDismiss = { showLiveVoiceModal = false }
        )
    }
}

@Composable
private fun SessionMessageCard(
    message: SessionMessageEntity,
    bg: Color,
    surface: Color,
    border: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    fontSizePx: Int,
    onApprove: () -> Unit,
    onReject: () -> Unit
) {
    val isUser = message.senderRole == "USER"
    val isThought = message.senderRole == "THOUGHT"
    val isApproval = message.senderRole == "TOOL_APPROVAL"
    val isTerminal = message.senderRole == "TERMINAL"

    val borderColor = when {
        isUser -> accent.copy(alpha = 0.55f)
        isApproval && message.approvalStatus == "PENDING" -> AmberPending
        isThought -> NeuralViolet.copy(alpha = 0.45f)
        isTerminal -> EmeraldOnline.copy(alpha = 0.45f)
        else -> border
    }

    val containerColor = when {
        isUser -> accent.copy(alpha = 0.1f)
        isThought -> NeuralViolet.copy(alpha = 0.08f)
        isTerminal -> bg
        else -> surface
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when {
                            isThought -> Icons.Default.Memory
                            isTerminal -> Icons.Default.Terminal
                            isApproval -> Icons.Default.Bolt
                            else -> Icons.Default.Speed
                        },
                        contentDescription = message.senderRole,
                        tint = when {
                            isUser -> accent
                            isThought -> NeuralViolet
                            isApproval -> AmberPending
                            isTerminal -> EmeraldOnline
                            else -> accent
                        },
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = when (message.senderRole) {
                            "USER" -> "YOU (REMOTE MOBILE)"
                            "THOUGHT" -> "AGENT REASONING (${message.durationMs}ms)"
                            "TOOL_APPROVAL" -> "STEP APPROVAL GATE (${message.approvalStatus})"
                            "TERMINAL" -> "REMOTE PTY OUTPUT"
                            else -> "ANTIGRAVITY AGENT"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            isUser -> accent
                            isThought -> NeuralViolet
                            isApproval -> AmberPending
                            isTerminal -> EmeraldOnline
                            else -> secondaryFg
                        }
                    )
                }

                if (message.commandOrPath.isNotBlank() && !isApproval) {
                    Text(
                        text = message.commandOrPath,
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = message.content,
                style = if (isTerminal) {
                    MaterialTheme.typography.labelSmall.copy(
                        fontSize = (fontSizePx - 1).coerceAtLeast(11).sp,
                        lineHeight = (fontSizePx + 4).sp
                    )
                } else {
                    MaterialTheme.typography.bodyLarge
                },
                color = fg
            )

            if (isApproval) {
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .border(1.dp, border, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    Text(
                        text = "$ ${message.commandOrPath}",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = (fontSizePx - 1).coerceAtLeast(11).sp,
                        color = AmberPending
                    )
                }
                if (message.approvalStatus == "PENDING") {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = onReject) {
                            Text("Reject", color = CrimsonError)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = onApprove,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = EmeraldOnline,
                                contentColor = Color(0xFF070708)
                            )
                        ) {
                            Text("Approve & Execute", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
