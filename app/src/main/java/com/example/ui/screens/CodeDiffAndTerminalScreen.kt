package com.example.ui.screens

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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Undo
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.FileDiffEntity
import com.example.data.local.TerminalLogEntity
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
    onAcceptDiff: (FileDiffEntity) -> Unit,
    onRevertDiff: (FileDiffEntity) -> Unit,
    onRunCommand: (String) -> Unit,
    onClearTerminal: () -> Unit
) {
    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val border = appearance.borderColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor
    val codeSizeSp = (appearance.fontSizePx - 1).coerceAtLeast(11).sp

    var selectedSubTab by rememberSaveable { mutableIntStateOf(0) }
    var commandInput by rememberSaveable { mutableStateOf("") }

    val quickCommands = listOf(
        "language_server --status",
        "git status -sb",
        "gradle :app:assembleDebug",
        "mcp --inspect-chrome"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Sub-Tab Switcher: Native Code Diffs vs Native PTY Terminal
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedSubTab == 0,
                onClick = { selectedSubTab = 0 },
                label = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Code Diffs",
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Code Diffs (${diffs.size})")
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
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remote Shell (${terminalLogs.size})")
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
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (selectedSubTab == 0) {
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
        } else {
            // Native PTY Terminal Console
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Quick Remote Macros",
                        style = MaterialTheme.typography.labelLarge,
                        color = secondaryFg
                    )
                    IconButton(
                        onClick = onClearTerminal,
                        modifier = Modifier.testTag("clear_terminal_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear Terminal Logs",
                            tint = secondaryFg
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(quickCommands) { cmd ->
                        SuggestionChip(
                            onClick = { onRunCommand(cmd) },
                            label = {
                                Text(
                                    text = cmd,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 11.sp
                                )
                            },
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Run Macro",
                                    tint = EmeraldOnline,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(surface)
                        .border(1.dp, border, RoundedCornerShape(12.dp))
                        .padding(12.dp)
                        .testTag("terminal_output_list"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(terminalLogs, key = { it.id }) { log ->
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$ ${log.command}",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = codeSizeSp,
                                    color = accent
                                )
                                Text(
                                    text = "exit ${log.exitCode} • ${log.durationMs}ms",
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = 10.sp,
                                    color = EmeraldOnline
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = log.output,
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = codeSizeSp,
                                lineHeight = (appearance.fontSizePx + 4).sp,
                                color = fg
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = commandInput,
                        onValueChange = { commandInput = it },
                        placeholder = {
                            Text(
                                "Run remote shell command...",
                                fontFamily = JetBrainsMonoFontFamily,
                                fontSize = codeSizeSp,
                                color = secondaryFg
                            )
                        },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("terminal_command_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            if (commandInput.isNotBlank()) {
                                val cmd = commandInput
                                commandInput = ""
                                onRunCommand(cmd)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(EmeraldOnline)
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
