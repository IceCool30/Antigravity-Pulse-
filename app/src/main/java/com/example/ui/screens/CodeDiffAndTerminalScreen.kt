package com.example.ui.screens

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.FileDiffEntity
import com.example.data.local.TerminalLogEntity
import com.example.data.remote.BridgeConnectionState
import com.example.data.remote.BridgeTelemetry
import com.example.data.remote.RemoteFileEntry
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.DiffAdditionBg
import com.example.ui.theme.DiffDeletionBg
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAppearanceSettings

@Composable
fun CodeDiffAndTerminalScreen(
    diffs: List<FileDiffEntity>,
    terminalLogs: List<TerminalLogEntity>,
    telemetry: BridgeTelemetry = BridgeTelemetry(),
    isExecutingCommand: Boolean = false,
    savedMacros: List<String> = emptyList(),
    remoteBrowsePath: String = ".",
    remoteFileEntries: List<RemoteFileEntry> = emptyList(),
    previewFilePath: String? = null,
    previewFileContent: String? = null,
    isLoadingRemoteFiles: Boolean = false,
    remoteFileError: String? = null,
    onAcceptDiff: (FileDiffEntity) -> Unit,
    onRevertDiff: (FileDiffEntity) -> Unit,
    onRunCommand: (String) -> Unit,
    onSendCtrlC: () -> Unit = {},
    onClearTerminal: () -> Unit,
    onOpenServerConfig: () -> Unit = {},
    onSaveMacro: (String) -> Unit = {},
    onDeleteMacro: (String) -> Unit = {},
    onDebugErrorWithCopilot: (command: String, output: String) -> Unit = { _, _ -> },
    onBrowseRemotePath: (String) -> Unit = {},
    onReadRemoteFile: (String) -> Unit = {},
    onCloseRemoteFilePreview: () -> Unit = {},
    onAttachRemoteFileToPrompt: (filePath: String, snippet: String) -> Unit = { _, _ -> }
) {
    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val border = appearance.borderColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor
    val codeSizeSp = (appearance.fontSizePx - 1).coerceAtLeast(11).sp

    // Sub-tabs: 0 = Code Diffs, 1 = Remote Shell (default), 2 = Live Remote Files
    var selectedSubTab by rememberSaveable { mutableIntStateOf(1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Sub-Tab Switcher: Diffs | Shell | Files
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Code Diffs",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Diffs (${diffs.size})", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.2f),
                    selectedLabelColor = accent
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("subtab_code_diffs")
            )

            FilterChip(
                selected = selectedSubTab == 1,
                onClick = { selectedSubTab = 1 },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = "Remote PTY Shell",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Shell (${terminalLogs.size})", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.2f),
                    selectedLabelColor = accent
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("subtab_remote_shell")
            )

            FilterChip(
                selected = selectedSubTab == 2,
                onClick = { selectedSubTab = 2 },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = "Remote Files",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Files", maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = accent.copy(alpha = 0.2f),
                    selectedLabelColor = accent
                ),
                modifier = Modifier
                    .weight(0.9f)
                    .testTag("subtab_remote_files")
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (selectedSubTab) {
            0 -> {
                if (diffs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(surface)
                            .border(1.dp, border, RoundedCornerShape(14.dp))
                            .padding(24.dp)
                            .testTag("empty_diffs_state"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(36.dp)
                            )
                            Text(
                                text = "No Pending Code Diffs",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = fg
                            )
                            Text(
                                text = "When your connected Antigravity session modifies files, live unified diffs will stream here for hunk-by-hunk approval or revert.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("diff_inspector_list"),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(diffs, key = { it.id }) { diff ->
                            DiffInspectorCard(
                                diff = diff,
                                bg = bg,
                                surface = surface,
                                border = border,
                                fg = fg,
                                secondaryFg = secondaryFg,
                                accent = accent,
                                codeSizeSp = codeSizeSp,
                                onAccept = { onAcceptDiff(diff) },
                                onRevert = { onRevertDiff(diff) }
                            )
                        }
                    }
                }
            }

            1 -> {
                TerminalView(
                    terminalLogs = terminalLogs,
                    telemetry = telemetry,
                    isExecutingCommand = isExecutingCommand,
                    savedMacros = savedMacros,
                    onExecuteCommand = onRunCommand,
                    onSendCtrlC = onSendCtrlC,
                    onClearTerminal = onClearTerminal,
                    onOpenServerConfig = onOpenServerConfig,
                    onSaveMacro = onSaveMacro,
                    onDeleteMacro = onDeleteMacro,
                    onDebugErrorWithCopilot = onDebugErrorWithCopilot,
                    modifier = Modifier.fillMaxSize()
                )
            }

            else -> {
                RemoteFileBrowserView(
                    telemetry = telemetry,
                    currentPath = remoteBrowsePath,
                    entries = remoteFileEntries,
                    previewFilePath = previewFilePath,
                    previewFileContent = previewFileContent,
                    isLoading = isLoadingRemoteFiles,
                    errorMessage = remoteFileError,
                    onBrowsePath = onBrowseRemotePath,
                    onReadFile = onReadRemoteFile,
                    onClosePreview = onCloseRemoteFilePreview,
                    onAttachFileToPrompt = onAttachRemoteFileToPrompt,
                    onOpenServerConfig = onOpenServerConfig,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/**
 * `TerminalView` composable that interfaces directly with the remote bridge (`AntigravityBridgeClient`)
 * to execute shell commands over WebSocket JSON-RPC / HTTP and displays real-time ANSI-colorized
 * streaming output using an auto-scrolling `LazyColumn`, paired with a mobile terminal accessory
 * key bar (`Ctrl+C`, `Tab`, `↑`, `↓`, `|`, `&&`, `/`, `--`, custom macros) and one-tap Copilot debugging.
 */
@Composable
fun TerminalView(
    terminalLogs: List<TerminalLogEntity>,
    telemetry: BridgeTelemetry,
    isExecutingCommand: Boolean,
    onExecuteCommand: (String) -> Unit,
    onClearTerminal: () -> Unit,
    onOpenServerConfig: () -> Unit = {},
    savedMacros: List<String> = emptyList(),
    onSendCtrlC: () -> Unit = {},
    onSaveMacro: (String) -> Unit = {},
    onDeleteMacro: (String) -> Unit = {},
    onDebugErrorWithCopilot: (command: String, output: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val elevated = appearance.elevatedSurfaceColor
    val border = appearance.borderColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor
    val codeSizeSp = (appearance.fontSizePx - 1).coerceAtLeast(11).sp

    var commandInput by rememberSaveable { mutableStateOf("") }
    var historyCursor by rememberSaveable { mutableIntStateOf(-1) }
    var showAddMacroDialog by rememberSaveable { mutableStateOf(false) }
    var newMacroInput by rememberSaveable { mutableStateOf("") }
    var copiedFeedbackId by remember { mutableStateOf<String?>(null) }

    val clipboardManager = LocalClipboardManager.current
    val listState = rememberLazyListState()

    // Automatically scroll LazyColumn to the latest real-time terminal log entry
    LaunchedEffect(terminalLogs.size, isExecutingCommand) {
        if (terminalLogs.isNotEmpty()) {
            listState.animateScrollToItem(terminalLogs.size - 1)
        }
    }

    // Derive command history from actual executed commands (most recent unique commands)
    val recentCommandHistory = remember(terminalLogs) {
        terminalLogs.map { it.command }.distinct().reversed()
    }

    val isConnected = telemetry.state == BridgeConnectionState.CONNECTED_LIVE_SOCKET ||
        telemetry.state == BridgeConnectionState.CONNECTED_HTTP_BRIDGE
    val endpointLabel = telemetry.activeEndpoint.ifBlank { "No remote host connected" }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("terminal_view_root")
    ) {
        // 1. Live Remote Bridge Status, Reconnect/Queue Telemetry & Controls Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(surface)
                .border(1.dp, border, RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onOpenServerConfig() }
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                telemetry.isReconnecting -> AmberPending
                                telemetry.state == BridgeConnectionState.CONNECTED_LIVE_SOCKET -> EmeraldOnline
                                telemetry.state == BridgeConnectionState.CONNECTED_HTTP_BRIDGE -> accent
                                telemetry.state == BridgeConnectionState.CONNECTING -> AmberPending
                                else -> CrimsonError
                            }
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val statusHeader = when {
                        telemetry.isReconnecting -> "RECONNECTING (ATTEMPT #${telemetry.reconnectAttempt})"
                        isConnected -> "REMOTE BRIDGE CONNECTED"
                        else -> "BRIDGE DISCONNECTED"
                    }
                    Text(
                        text = if (telemetry.queuedCommandsCount > 0) {
                            "$statusHeader • ${telemetry.queuedCommandsCount} QUEUED"
                        } else {
                            statusHeader
                        },
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isConnected && !telemetry.isReconnecting) EmeraldOnline else AmberPending
                    )
                    Text(
                        text = endpointLabel,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        color = secondaryFg,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!isConnected) {
                    OutlinedButton(
                        onClick = onOpenServerConfig,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("terminal_connect_host_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = "Connect Host",
                            tint = accent,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Connect", style = MaterialTheme.typography.labelSmall, color = accent)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                }

                if (terminalLogs.isNotEmpty()) {
                    IconButton(
                        onClick = onClearTerminal,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("clear_terminal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Terminal Output",
                            tint = secondaryFg,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. Dynamic Command History & Saved Custom Macros Strip
        if (recentCommandHistory.isNotEmpty() || savedMacros.isNotEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(savedMacros, key = { "macro_$it" }) { macro ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.16f))
                            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .clickable {
                                if (!isExecutingCommand) onExecuteCommand(macro)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = macro,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Remove Macro $macro",
                            tint = secondaryFg,
                            modifier = Modifier
                                .size(13.dp)
                                .clickable { onDeleteMacro(macro) }
                        )
                    }
                }
                items(recentCommandHistory.take(6)) { pastCmd ->
                    SuggestionChip(
                        onClick = { commandInput = pastCmd },
                        label = {
                            Text(
                                text = pastCmd,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.History,
                                contentDescription = "Reuse Command",
                                tint = accent,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Scrollable Real-Time ANSI Terminal Output LazyColumn
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(surface)
                .border(1.dp, border, RoundedCornerShape(12.dp))
                .padding(12.dp)
                .testTag("terminal_output_list"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            if (terminalLogs.isEmpty() && !isExecutingCommand) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Terminal,
                            contentDescription = null,
                            tint = secondaryFg,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "Remote PTY Terminal Ready",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = fg
                        )
                        Text(
                            text = "Enter a shell command below to execute it over the remote bridge with ANSI color rendering, accessory keys, and one-tap AI debugging.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryFg
                        )
                    }
                }
            }

            items(terminalLogs, key = { it.id }) { log ->
                TerminalLogEntryRow(
                    log = log,
                    bg = bg,
                    elevated = elevated,
                    border = border,
                    fg = fg,
                    secondaryFg = secondaryFg,
                    accent = accent,
                    codeSizeSp = codeSizeSp,
                    lineHeightSp = (appearance.fontSizePx + 4).sp,
                    isCopied = copiedFeedbackId == log.id,
                    onRerunCommand = { onExecuteCommand(log.command) },
                    onCopyOutput = {
                        clipboardManager.setText(AnnotatedString("$ ${log.command}\n${log.output}"))
                        copiedFeedbackId = log.id
                    },
                    onExplainErrorWithCopilot = {
                        onDebugErrorWithCopilot(log.command, log.output)
                    }
                )
            }

            if (isExecutingCommand) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(elevated)
                            .padding(10.dp)
                            .testTag("terminal_executing_indicator"),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = EmeraldOnline,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "Executing on remote bridge ($endpointLabel)...",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = codeSizeSp,
                            color = EmeraldOnline
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 4. Mobile Terminal Accessory Key Bar (Ctrl+C, Tab, ↑, ↓, |, &&, /, --, + Macro)
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("terminal_accessory_key_bar"),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            item {
                TerminalAccessoryKey(
                    label = "Ctrl+C",
                    bg = CrimsonError.copy(alpha = 0.16f),
                    border = CrimsonError.copy(alpha = 0.5f),
                    fg = CrimsonError,
                    tag = "accessory_key_ctrl_c",
                    onClick = onSendCtrlC
                )
            }
            item {
                TerminalAccessoryKey(
                    label = "↑",
                    bg = elevated,
                    border = border,
                    fg = fg,
                    tag = "accessory_key_up",
                    onClick = {
                        if (recentCommandHistory.isNotEmpty()) {
                            val nextIdx = (historyCursor + 1).coerceAtMost(recentCommandHistory.lastIndex)
                            historyCursor = nextIdx
                            commandInput = recentCommandHistory[nextIdx]
                        }
                    }
                )
            }
            item {
                TerminalAccessoryKey(
                    label = "↓",
                    bg = elevated,
                    border = border,
                    fg = fg,
                    tag = "accessory_key_down",
                    onClick = {
                        if (historyCursor > 0) {
                            historyCursor -= 1
                            commandInput = recentCommandHistory[historyCursor]
                        } else {
                            historyCursor = -1
                            commandInput = ""
                        }
                    }
                )
            }
            val symbolKeys = listOf("Tab" to "  ", "|" to " | ", "&&" to " && ", "/" to "/", "--" to " --")
            items(symbolKeys) { (label, insertion) ->
                TerminalAccessoryKey(
                    label = label,
                    bg = elevated,
                    border = border,
                    fg = fg,
                    tag = "accessory_key_$label",
                    onClick = { commandInput += insertion }
                )
            }
            item {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .clickable {
                            newMacroInput = commandInput
                            showAddMacroDialog = true
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                        .testTag("add_terminal_macro_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Save Macro",
                        tint = accent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "Macro",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 5. Shell Command Input Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                prefix = {
                    Text(
                        text = "$ ",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldOnline
                    )
                },
                placeholder = {
                    Text(
                        "Enter remote shell command...",
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = codeSizeSp,
                        color = secondaryFg
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if (commandInput.isNotBlank() && !isExecutingCommand) {
                            val cmd = commandInput
                            commandInput = ""
                            historyCursor = -1
                            onExecuteCommand(cmd)
                        }
                    }
                ),
                modifier = Modifier
                    .weight(1f)
                    .testTag("terminal_command_input")
            )
            Spacer(modifier = Modifier.width(8.dp))
            IconButton(
                onClick = {
                    if (commandInput.isNotBlank() && !isExecutingCommand) {
                        val cmd = commandInput
                        commandInput = ""
                        historyCursor = -1
                        onExecuteCommand(cmd)
                    }
                },
                enabled = !isExecutingCommand,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isExecutingCommand) border else EmeraldOnline)
                    .testTag("run_terminal_command_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Execute Command",
                    tint = Color(0xFF070708)
                )
            }
        }
    }

    if (showAddMacroDialog) {
        AlertDialog(
            onDismissRequest = { showAddMacroDialog = false },
            containerColor = surface,
            title = {
                Text(
                    text = "Save One-Tap Command Macro",
                    style = MaterialTheme.typography.titleMedium,
                    color = fg
                )
            },
            text = {
                OutlinedTextField(
                    value = newMacroInput,
                    onValueChange = { newMacroInput = it },
                    label = { Text("Shell Command (e.g. git status --short)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newMacroInput.trim()
                        if (clean.isNotEmpty()) {
                            onSaveMacro(clean)
                        }
                        showAddMacroDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF070708)
                    )
                ) {
                    Text("Save Macro", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMacroDialog = false }) {
                    Text("Cancel", color = secondaryFg)
                }
            }
        )
    }
}

@Composable
private fun TerminalAccessoryKey(
    label: String,
    bg: Color,
    border: Color,
    fg: Color,
    tag: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontFamily = JetBrainsMonoFontFamily,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}

@Composable
private fun TerminalLogEntryRow(
    log: TerminalLogEntity,
    bg: Color,
    elevated: Color,
    border: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    codeSizeSp: androidx.compose.ui.unit.TextUnit,
    lineHeightSp: androidx.compose.ui.unit.TextUnit,
    isCopied: Boolean,
    onRerunCommand: () -> Unit,
    onCopyOutput: () -> Unit,
    onExplainErrorWithCopilot: () -> Unit
) {
    val isSuccess = log.exitCode == 0
    val defaultOutputColor = if (isSuccess) fg else secondaryFg
    val ansiOutput = remember(log.output, defaultOutputColor) {
        parseAnsiToAnnotatedString(log.output, defaultOutputColor)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$ ${log.command}",
                fontFamily = JetBrainsMonoFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = codeSizeSp,
                color = accent,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onRerunCommand() }
            )
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(
                        if (isSuccess) EmeraldOnline.copy(alpha = 0.14f)
                        else CrimsonError.copy(alpha = 0.14f)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "exit ${log.exitCode} • ${log.durationMs}ms",
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 10.sp,
                    color = if (isSuccess) EmeraldOnline else CrimsonError
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = onCopyOutput,
                modifier = Modifier
                    .size(28.dp)
                    .testTag("copy_terminal_log_${log.id}")
            ) {
                Icon(
                    imageVector = if (isCopied) Icons.Default.Check else Icons.Default.ContentCopy,
                    contentDescription = "Copy Terminal Output",
                    tint = if (isCopied) EmeraldOnline else secondaryFg,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        if (log.output.isNotBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = ansiOutput,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = codeSizeSp,
                lineHeight = lineHeightSp
            )
        }

        if (!isSuccess) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
                        .clickable { onExplainErrorWithCopilot() }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                        .testTag("explain_error_copilot_${log.id}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Explain and Fix Error with Copilot",
                        tint = accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Explain & Fix with Copilot",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = accent
                    )
                }
            }
        }
    }
}

@Composable
private fun DiffInspectorCard(
    diff: FileDiffEntity,
    bg: Color,
    surface: Color,
    border: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    codeSizeSp: androidx.compose.ui.unit.TextUnit,
    onAccept: () -> Unit,
    onRevert: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = surface),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, border, RoundedCornerShape(14.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = diff.filePath,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = codeSizeSp,
                        color = fg
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "+${diff.additions}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = EmeraldOnline
                        )
                        Text(
                            text = "-${diff.deletions}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = CrimsonError
                        )
                        Text(
                            text = "• ${diff.status}",
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = 11.sp,
                            color = accent
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Syntax-colored line-by-line unified diff renderer
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(bg)
                    .border(1.dp, border, RoundedCornerShape(8.dp))
                    .padding(8.dp)
            ) {
                diff.unifiedPatch.lines().forEach { line ->
                    val bgColor = when {
                        line.startsWith("+") -> DiffAdditionBg
                        line.startsWith("-") -> DiffDeletionBg
                        else -> Color.Transparent
                    }
                    val textColor = when {
                        line.startsWith("+") -> EmeraldOnline
                        line.startsWith("-") -> CrimsonError
                        line.startsWith("@@") -> accent
                        else -> secondaryFg
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bgColor)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = line,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontSize = codeSizeSp,
                            lineHeight = 16.sp,
                            color = textColor
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onRevert,
                    modifier = Modifier.testTag("revert_diff_${diff.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Undo,
                        contentDescription = "Revert Hunk",
                        tint = CrimsonError,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Revert Hunk", color = CrimsonError)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = EmeraldOnline,
                        contentColor = Color(0xFF070708)
                    ),
                    modifier = Modifier.testTag("accept_diff_${diff.id}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Accept Hunk",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accept Hunk", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
