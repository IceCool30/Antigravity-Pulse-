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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.BridgeConnectionState
import com.example.data.remote.BridgeTelemetry
import com.example.data.remote.RemoteFileEntry
import com.example.ui.theme.AmberPending
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAppearanceSettings
import com.example.ui.theme.NeuralViolet

/**
 * Parses standard ANSI SGR escape codes (e.g., `\u001B[31m`, `\u001B[32m`, `\u001B[1;33m`,
 * `\u001B[0m`) into a styled Compose `AnnotatedString` for colorized compiler/test terminal logs.
 */
fun parseAnsiToAnnotatedString(
    rawText: String,
    defaultColor: Color
): AnnotatedString {
    val ansiRegex = Regex("\u001B\\[([0-9;]*)m")
    return buildAnnotatedString {
        var currentColor = defaultColor
        var isBold = false
        var lastIndex = 0

        for (match in ansiRegex.findAll(rawText)) {
            if (match.range.first > lastIndex) {
                val segment = rawText.substring(lastIndex, match.range.first)
                pushStyle(
                    SpanStyle(
                        color = currentColor,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                    )
                )
                append(segment)
                pop()
            }

            val codes = match.groupValues[1]
                .split(";")
                .mapNotNull { it.toIntOrNull() }
                .ifEmpty { listOf(0) }

            for (code in codes) {
                when (code) {
                    0 -> {
                        currentColor = defaultColor
                        isBold = false
                    }
                    1 -> isBold = true
                    22 -> isBold = false
                    30, 90 -> currentColor = Color(0xFF94A3B8)
                    31, 91 -> currentColor = CrimsonError
                    32, 92 -> currentColor = EmeraldOnline
                    33, 93 -> currentColor = AmberPending
                    34, 94 -> currentColor = Color(0xFF60A5FA)
                    35, 95 -> currentColor = NeuralViolet
                    36, 96 -> currentColor = ElectricCyan
                    37, 97 -> currentColor = Color.White
                    39 -> currentColor = defaultColor
                }
            }
            lastIndex = match.range.last + 1
        }

        if (lastIndex < rawText.length) {
            val tail = rawText.substring(lastIndex)
            pushStyle(
                SpanStyle(
                    color = currentColor,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                )
            )
            append(tail)
            pop()
        }
    }
}

/**
 * Live Remote File Browser panel inside the Code & Shell tab.
 * Queries the connected Antigravity remote bridge (`workspace.listFiles` & `workspace.readFile`)
 * to browse folders, preview source files, and attach any file directly to the prompt composer.
 */
@Composable
fun RemoteFileBrowserView(
    telemetry: BridgeTelemetry,
    currentPath: String,
    entries: List<RemoteFileEntry>,
    previewFilePath: String?,
    previewFileContent: String?,
    isLoading: Boolean,
    errorMessage: String?,
    onBrowsePath: (String) -> Unit,
    onReadFile: (String) -> Unit,
    onClosePreview: () -> Unit,
    onAttachFileToPrompt: (filePath: String, snippet: String) -> Unit,
    onOpenServerConfig: () -> Unit,
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

    var pathQuery by rememberSaveable(currentPath) { mutableStateOf(currentPath) }

    val isConnected = telemetry.state == BridgeConnectionState.CONNECTED_LIVE_SOCKET ||
        telemetry.state == BridgeConnectionState.CONNECTED_HTTP_BRIDGE

    LaunchedEffect(telemetry.activeEndpoint) {
        if (telemetry.activeEndpoint.isNotBlank() && entries.isEmpty() && !isLoading) {
            onBrowsePath(currentPath)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("remote_file_browser_root")
    ) {
        // Path Bar + Refresh / Up Directory
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(surface)
                .border(1.dp, border, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (previewFilePath != null || (currentPath.isNotBlank() && currentPath != ".")) {
                IconButton(
                    onClick = {
                        if (previewFilePath != null) {
                            onClosePreview()
                        } else {
                            val parent = currentPath.trimEnd('/').substringBeforeLast('/', ".")
                            onBrowsePath(parent.ifBlank { "." })
                        }
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate Up",
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            OutlinedTextField(
                value = pathQuery,
                onValueChange = { pathQuery = it },
                placeholder = { Text("Path (e.g. . or src/main.kt)") },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .testTag("remote_file_path_input")
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = { onBrowsePath(pathQuery.trim().ifBlank { "." }) },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(elevated)
                    .testTag("remote_file_refresh_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "List Remote Directory",
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (previewFilePath != null) {
            // File Content Preview + Attach to Prompt CTA
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(surface)
                    .border(1.dp, border, RoundedCornerShape(12.dp))
                    .padding(12.dp)
                    .testTag("remote_file_preview_panel")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = previewFilePath,
                        fontFamily = JetBrainsMonoFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = codeSizeSp,
                        color = accent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onAttachFileToPrompt(
                                previewFilePath,
                                previewFileContent.orEmpty().take(2000)
                            )
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent,
                            contentColor = Color(0xFF070708)
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("attach_remote_file_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.AttachFile,
                            contentDescription = "Attach to Prompt",
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Attach", fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(bg)
                        .border(1.dp, border, RoundedCornerShape(8.dp))
                        .padding(10.dp)
                ) {
                    val lines = previewFileContent.orEmpty().lines()
                    items(lines.size) { idx ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "${idx + 1}".padStart(4, ' '),
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = 11.sp,
                                color = secondaryFg.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = lines[idx],
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = codeSizeSp,
                                color = fg
                            )
                        }
                    }
                }
            }
        } else {
            // Directory Tree List
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(surface)
                    .border(1.dp, border, RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                when {
                    isLoading -> {
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = accent,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Querying remote workspace files...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )
                        }
                    }
                    !isConnected && telemetry.activeEndpoint.isBlank() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Folder,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(34.dp)
                            )
                            Text(
                                text = "Connect a Remote Host to Browse Workspace Files",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = fg
                            )
                            Text(
                                text = "Once connected, browse directories over JSON-RPC, inspect source files, and attach any file directly to your Antigravity prompt.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )
                            Button(
                                onClick = onOpenServerConfig,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = Color(0xFF070708)
                                )
                            ) {
                                Text("Configure Remote Host", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    entries.isEmpty() -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "No directory entries returned for \"$currentPath\".",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (errorMessage != null) AmberPending else secondaryFg
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { onBrowsePath(pathQuery.trim().ifBlank { "." }) }) {
                                    Text("Retry List", color = accent)
                                }
                                if (pathQuery.trim().isNotBlank() && pathQuery.trim() != ".") {
                                    Button(
                                        onClick = { onReadFile(pathQuery.trim()) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = accent,
                                            contentColor = Color(0xFF070708)
                                        )
                                    ) {
                                        Text("Open File Path", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("remote_file_entries_list"),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(entries, key = { it.path }) { entry ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(bg)
                                        .border(1.dp, border, RoundedCornerShape(10.dp))
                                        .clickable {
                                            if (entry.isDirectory) {
                                                onBrowsePath(entry.path)
                                            } else {
                                                onReadFile(entry.path)
                                            }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(
                                            imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.Description,
                                            contentDescription = if (entry.isDirectory) "Folder" else "File",
                                            tint = if (entry.isDirectory) accent else EmeraldOnline,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column {
                                            Text(
                                                text = entry.name,
                                                fontFamily = JetBrainsMonoFontFamily,
                                                fontSize = codeSizeSp,
                                                fontWeight = FontWeight.Medium,
                                                color = fg,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            if (!entry.isDirectory && entry.sizeBytes > 0) {
                                                Text(
                                                    text = "${entry.sizeBytes} B",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = secondaryFg
                                                )
                                            }
                                        }
                                    }
                                    if (!entry.isDirectory) {
                                        IconButton(
                                            onClick = { onAttachFileToPrompt(entry.path, "") },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.AttachFile,
                                                contentDescription = "Attach ${entry.name}",
                                                tint = accent,
                                                modifier = Modifier.size(16.dp)
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
}
