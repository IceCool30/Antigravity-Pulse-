package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.cloud.configureAppCheck
import com.example.data.local.AgentSessionEntity
import com.example.data.local.AntigravityDatabase
import com.example.data.local.ServerProfileEntity
import com.example.ui.screens.AppearanceSettingsSheet
import com.example.ui.screens.CodeDiffAndTerminalScreen
import com.example.ui.screens.ConnectionGuideScreen
import com.example.ui.screens.CopilotAndImageStudioScreen
import com.example.ui.screens.RetainedWebViewBridgeScreen
import com.example.ui.screens.SessionsAndChatScreen
import com.example.ui.screens.StorageAndCloudSyncScreen
import com.example.ui.theme.AntigravityPulseTheme
import com.example.ui.theme.AppearancePreferencesManager
import com.example.ui.theme.AppearanceSettings
import com.example.ui.theme.ElectricCyan
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalAppearanceSettings
import com.example.ui.theme.NeuralViolet
import com.example.viewmodel.AntigravityViewModel
import com.example.viewmodel.CopilotViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory Firebase App Check initialization with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val appearanceManager = remember { AppearancePreferencesManager(context) }
            val appearance by appearanceManager.settings.collectAsStateWithLifecycle()

            AntigravityPulseTheme(appearance = appearance) {
                AntigravityPulseApp(
                    appearance = appearance,
                    onUpdateAppearance = appearanceManager::update,
                    onSelectPreset = appearanceManager::applyPreset,
                    onResetPreset = appearanceManager::resetCurrentPresetColors
                )
            }
        }
    }
}

private enum class PulseTab(
    val titleRes: Int,
    val icon: ImageVector,
    val tag: String
) {
    SESSIONS(R.string.nav_sessions, Icons.Default.Forum, "nav_tab_sessions"),
    WORKSPACE(R.string.nav_workspace, Icons.Default.Code, "nav_tab_workspace"),
    WEB_BRIDGE(R.string.nav_bridge, Icons.Default.Language, "nav_tab_bridge"),
    COPILOT(R.string.nav_copilot, Icons.Default.AutoAwesome, "nav_tab_copilot"),
    STORAGE_SYNC(R.string.nav_sync, Icons.Default.CloudSync, "nav_tab_sync")
}

@Composable
fun AntigravityPulseApp(
    appearance: AppearanceSettings,
    onUpdateAppearance: ((AppearanceSettings) -> AppearanceSettings) -> Unit,
    onSelectPreset: (com.example.ui.theme.AntigravityThemePreset) -> Unit,
    onResetPreset: () -> Unit
) {
    val context = LocalContext.current
    val dao = remember { AntigravityDatabase.getInstance(context).dao() }

    val antigravityViewModel: AntigravityViewModel = viewModel(
        factory = AntigravityViewModel.provideFactory(dao)
    )
    val copilotViewModel: CopilotViewModel = viewModel(
        factory = CopilotViewModel.provideFactory(dao)
    )

    val serverProfiles by antigravityViewModel.serverProfiles.collectAsStateWithLifecycle()
    val sessions by antigravityViewModel.sessions.collectAsStateWithLifecycle()
    val selectedSessionId by antigravityViewModel.selectedSessionId.collectAsStateWithLifecycle()
    val activeMessages by antigravityViewModel.activeSessionMessages.collectAsStateWithLifecycle()
    val activeDiffs by antigravityViewModel.activeSessionDiffs.collectAsStateWithLifecycle()
    val activeTerminalLogs by antigravityViewModel.activeTerminalLogs.collectAsStateWithLifecycle()
    val telemetry by antigravityViewModel.bridgeTelemetry.collectAsStateWithLifecycle()
    val statusBanner by antigravityViewModel.statusBanner.collectAsStateWithLifecycle()

    val copilotMessages by copilotViewModel.messages.collectAsStateWithLifecycle()
    val selectedModel by copilotViewModel.selectedModel.collectAsStateWithLifecycle()
    val selectedPersona by copilotViewModel.selectedPersona.collectAsStateWithLifecycle()
    val chatUiState by copilotViewModel.chatUiState.collectAsStateWithLifecycle()
    val imageUiState by copilotViewModel.imageUiState.collectAsStateWithLifecycle()
    val selectedInputBitmap by copilotViewModel.selectedInputBitmap.collectAsStateWithLifecycle()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showConnectionGuide by rememberSaveable { mutableStateOf(appearance.showStartupGuideOnLaunch) }
    var showAppearanceSheet by rememberSaveable { mutableStateOf(false) }
    var showFloatingQuickMenu by rememberSaveable { mutableStateOf(false) }
    var showSearchBar by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showServerModal by rememberSaveable { mutableStateOf(false) }
    var showNewSessionModal by rememberSaveable { mutableStateOf(false) }
    var showBridgeExplainerModal by rememberSaveable { mutableStateOf(false) }

    val activeTheme = LocalAppearanceSettings.current
    val bg = activeTheme.backgroundColor
    val surface = activeTheme.surfaceColor
    val elevated = activeTheme.elevatedSurfaceColor
    val border = activeTheme.borderColor
    val fg = activeTheme.foregroundColor
    val secondaryFg = activeTheme.secondaryTextColor
    val accent = activeTheme.accentColor

    val isBarsHidden = appearance.isFullscreenBarsHidden

    // Return to primary Sessions hub when pressing system Back from secondary tabs
    BackHandler(enabled = !showConnectionGuide && !showAppearanceSheet && selectedTabIndex != 0) {
        selectedTabIndex = 0
    }

    // Complete Step-by-Step Connection & Usage Guide (Shown automatically on launch or when opened from Settings)
    if (showConnectionGuide) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(bg),
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                ConnectionGuideScreen(
                    settings = appearance,
                    activeEndpointUrl = telemetry.activeEndpoint,
                    onUpdateSettings = onUpdateAppearance,
                    onQuickConnectWebBridge = { hostName, url, token ->
                        antigravityViewModel.addOrUpdateServerProfile(
                            existingId = null,
                            name = hostName,
                            endpointUrl = url,
                            connectionMode = "HYBRID_BRIDGE",
                            authToken = token,
                            keepAlivePinned = true
                        )
                        selectedTabIndex = PulseTab.WEB_BRIDGE.ordinal
                        showConnectionGuide = false
                    },
                    onQuickConnectNativeSessions = { hostName, url, token ->
                        antigravityViewModel.addOrUpdateServerProfile(
                            existingId = null,
                            name = hostName,
                            endpointUrl = url,
                            connectionMode = "HYBRID_BRIDGE",
                            authToken = token,
                            keepAlivePinned = true
                        )
                        selectedTabIndex = PulseTab.SESSIONS.ordinal
                        showConnectionGuide = false
                    },
                    onOpenAppearanceSettings = {
                        showConnectionGuide = false
                        showAppearanceSheet = true
                    },
                    onCloseGuide = {
                        showConnectionGuide = false
                    }
                )
            }
        }
        return
    }

    if (showAppearanceSheet) {
        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(bg),
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                AppearanceSettingsSheet(
                    settings = appearance,
                    onUpdateSettings = onUpdateAppearance,
                    onSelectPreset = onSelectPreset,
                    onResetPreset = onResetPreset,
                    onOpenConnectionGuide = {
                        showAppearanceSheet = false
                        showConnectionGuide = true
                    },
                    onClose = { showAppearanceSheet = false }
                )
            }
        }
        return
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(bg),
        contentWindowInsets = WindowInsets.safeDrawing,
        topBar = {
            // Collapsible Sleek Antigravity Remote Top Bar (Hidden in Distraction-Free Fullscreen Mode)
            AnimatedVisibility(
                visible = !isBarsHidden,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surface)
                ) {
                    AntigravityRemoteTopBar(
                        sessions = sessions,
                        selectedSessionId = selectedSessionId,
                        latencyMs = telemetry.latencyMs,
                        fg = fg,
                        secondaryFg = secondaryFg,
                        elevated = elevated,
                        border = border,
                        accent = accent,
                        onSelectSession = antigravityViewModel::selectSession,
                        onOpenServerModal = { showServerModal = true },
                        onOpenNewSessionModal = { showNewSessionModal = true },
                        onToggleSearch = { showSearchBar = !showSearchBar },
                        onOpenAppearance = { showAppearanceSheet = true },
                        onPingRefresh = antigravityViewModel::pingActiveServer,
                        onOpenExplainer = { showConnectionGuide = true },
                        onHideBarsFullscreen = {
                            onUpdateAppearance { it.copy(isFullscreenBarsHidden = true) }
                        }
                    )

                    AnimatedVisibility(visible = showSearchBar) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search messages, commands, or files...") },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("top_search_input")
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    searchQuery = ""
                                    showSearchBar = false
                                }
                            ) {
                                Text("Done", color = accent)
                            }
                        }
                    }

                    HorizontalDivider(color = border, thickness = 1.dp)
                }
            }
        },
        bottomBar = {
            // Collapsible Bottom Navigation Bar (Hidden in Distraction-Free Fullscreen Mode)
            AnimatedVisibility(
                visible = !isBarsHidden,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Column {
                    HorizontalDivider(color = border, thickness = 1.dp)
                    NavigationBar(
                        containerColor = surface,
                        tonalElevation = 0.dp
                    ) {
                        PulseTab.entries.forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTabIndex == index,
                                onClick = { selectedTabIndex = index },
                                icon = {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = stringResource(id = tab.titleRes)
                                    )
                                },
                                label = {
                                    Text(
                                        text = stringResource(id = tab.titleRes),
                                        style = MaterialTheme.typography.labelSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = accent,
                                    selectedTextColor = accent,
                                    indicatorColor = accent.copy(alpha = 0.16f),
                                    unselectedIconColor = secondaryFg,
                                    unselectedTextColor = secondaryFg
                                ),
                                modifier = Modifier.testTag(tab.tag)
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(bg)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (statusBanner != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(EmeraldOnline.copy(alpha = 0.16f))
                            .clickable { antigravityViewModel.clearBanner() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                            .testTag("status_feedback_banner"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = statusBanner.orEmpty(),
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldOnline,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Dismiss",
                            style = MaterialTheme.typography.labelSmall,
                            color = fg
                        )
                    }
                }

                Box(modifier = Modifier.weight(1f)) {
                    when (PulseTab.entries[selectedTabIndex]) {
                        PulseTab.SESSIONS -> SessionsAndChatScreen(
                            sessions = sessions,
                            selectedSessionId = selectedSessionId,
                            messages = activeMessages,
                            isFullscreenBarsHidden = isBarsHidden,
                            searchQuery = searchQuery,
                            onClearSearch = { searchQuery = "" },
                            onSelectSession = antigravityViewModel::selectSession,
                            onOpenNewSessionModal = { showNewSessionModal = true },
                            onSendPrompt = { prompt ->
                                antigravityViewModel.sendPromptToAntigravityAgent(prompt)
                            },
                            onResolveApproval = antigravityViewModel::resolveStepApproval,
                            onUpdateDraft = antigravityViewModel::updateDraftAndScroll
                        )

                        PulseTab.WORKSPACE -> CodeDiffAndTerminalScreen(
                            diffs = activeDiffs,
                            terminalLogs = activeTerminalLogs,
                            onAcceptDiff = { diff ->
                                antigravityViewModel.updateFileDiffDecision(diff, true)
                            },
                            onRevertDiff = { diff ->
                                antigravityViewModel.updateFileDiffDecision(diff, false)
                            },
                            onRunCommand = antigravityViewModel::runNativeTerminalCommand,
                            onClearTerminal = antigravityViewModel::clearTerminalForActiveSession
                        )

                        PulseTab.WEB_BRIDGE -> RetainedWebViewBridgeScreen(
                            telemetry = telemetry,
                            isFullscreenBarsHidden = isBarsHidden,
                            onToggleDomBooster = antigravityViewModel::toggleDomLagBooster,
                            onJsBridgeEvent = antigravityViewModel::recordWebViewBridgeEvent
                        )

                        PulseTab.COPILOT -> CopilotAndImageStudioScreen(
                            messages = copilotMessages,
                            selectedModel = selectedModel,
                            selectedPersona = selectedPersona,
                            chatUiState = chatUiState,
                            imageUiState = imageUiState,
                            selectedInputBitmap = selectedInputBitmap,
                            onSelectModel = copilotViewModel::selectModel,
                            onSelectPersona = copilotViewModel::selectPersona,
                            onSendChatMessage = copilotViewModel::sendChatMessage,
                            onForwardToAntigravityAgent = { refinedPrompt ->
                                antigravityViewModel.sendPromptToAntigravityAgent(refinedPrompt)
                                selectedTabIndex = 0
                            },
                            onSetInputBitmap = copilotViewModel::setInputBitmapForEditing,
                            onGenerateOrEditImage = copilotViewModel::generateOrEditUiAsset
                        )

                        PulseTab.STORAGE_SYNC -> StorageAndCloudSyncScreen(
                            dao = dao,
                            localProfiles = serverProfiles,
                            localSessions = sessions
                        )
                    }
                }
            }

            // Floating Edge Handle & Summonable Overlay Dock when Top & Bottom Bars are hidden!
            if (isBarsHidden) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(start = 0.dp)
                ) {
                    if (!showFloatingQuickMenu) {
                        // Subtle edge pill matching Screenshot 3's left-edge floating handle
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp))
                                .background(elevated.copy(alpha = 0.88f))
                                .border(
                                    1.dp,
                                    border,
                                    RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                                )
                                .clickable { showFloatingQuickMenu = true }
                                .padding(horizontal = 8.dp, vertical = 14.dp)
                                .testTag("floating_fullscreen_edge_handle"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = "Open Quick Controls or Restore Bars",
                                tint = accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else {
                        // Floating Quick Control Overlay Drawer
                        Column(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(surface.copy(alpha = 0.96f))
                                .border(1.dp, border, RoundedCornerShape(20.dp))
                                .padding(10.dp)
                                .testTag("floating_quick_menu_overlay"),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            // Restore Bars button
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(accent)
                                    .clickable {
                                        showFloatingQuickMenu = false
                                        onUpdateAppearance { it.copy(isFullscreenBarsHidden = false) }
                                    }
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .testTag("restore_bars_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FullscreenExit,
                                    contentDescription = "Show Top & Bottom Bars",
                                    tint = Color(0xFF070708),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Show Bars",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF070708)
                                )
                            }

                            // Quick Tab Switcher inside Fullscreen Mode
                            PulseTab.entries.forEachIndexed { index, tab ->
                                val isSelected = selectedTabIndex == index
                                Row(
                                    modifier = Modifier
                                        .width(148.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            if (isSelected) accent.copy(alpha = 0.18f)
                                            else Color.Transparent
                                        )
                                        .clickable {
                                            selectedTabIndex = index
                                            showFloatingQuickMenu = false
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = null,
                                        tint = if (isSelected) accent else secondaryFg,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = stringResource(id = tab.titleRes),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = if (isSelected) accent else fg
                                    )
                                }
                            }

                            HorizontalDivider(
                                color = border,
                                modifier = Modifier.width(136.dp)
                            )

                            // Quick Appearance Customizer button
                            Row(
                                modifier = Modifier
                                    .width(148.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        showFloatingQuickMenu = false
                                        showAppearanceSheet = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Settings,
                                    contentDescription = "Appearance Settings",
                                    tint = secondaryFg,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Appearance",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = fg
                                )
                            }

                            // Quick Connection Guide button
                            Row(
                                modifier = Modifier
                                    .width(148.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        showFloatingQuickMenu = false
                                        showConnectionGuide = true
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.HelpOutline,
                                    contentDescription = "How to Connect Guide",
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "How to Use Guide",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = accent
                                )
                            }

                            // Collapse Drawer back to Edge Pill
                            TextButton(
                                onClick = { showFloatingQuickMenu = false }
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                    contentDescription = "Hide Menu",
                                    tint = secondaryFg,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Keep Fullscreen", color = secondaryFg)
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal: Server Profile Switcher & Host Connection Editor
    if (showServerModal) {
        var hostName by remember { mutableStateOf("") }
        var hostUrl by remember { mutableStateOf("https://") }
        var hostToken by remember { mutableStateOf("") }
        var keepAlive by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showServerModal = false },
            containerColor = surface,
            title = {
                Text(
                    "Antigravity Remote Endpoints",
                    style = MaterialTheme.typography.titleLarge,
                    color = fg
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Select an existing bridge profile or add your Tailscale / Cloudflare / LAN language_server URL:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryFg
                    )
                    serverProfiles.forEach { profile ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (profile.isSelected) accent.copy(alpha = 0.15f)
                                    else elevated
                                )
                                .clickable {
                                    antigravityViewModel.selectServerProfile(profile)
                                    showServerModal = false
                                }
                                .padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = profile.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (profile.isSelected) accent else fg
                                )
                                Text(
                                    text = profile.endpointUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryFg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = "${profile.lastLatencyMs}ms",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldOnline
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Add New Remote Host",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent
                    )
                    OutlinedTextField(
                        value = hostName,
                        onValueChange = { hostName = it },
                        label = { Text("Host Label (e.g., Home Linux Server)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = hostUrl,
                        onValueChange = { hostUrl = it },
                        label = { Text("URL or WebSocket (wss:// or https://)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = hostToken,
                        onValueChange = { hostToken = it },
                        label = { Text("Pairing / OAuth Token (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Pin WebSocket & WebView in RAM",
                            style = MaterialTheme.typography.bodyMedium,
                            color = fg
                        )
                        Switch(checked = keepAlive, onCheckedChange = { keepAlive = it })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (hostName.isNotBlank() && hostUrl.isNotBlank()) {
                            antigravityViewModel.addOrUpdateServerProfile(
                                null,
                                hostName,
                                hostUrl,
                                "HYBRID_BRIDGE",
                                hostToken,
                                keepAlive
                            )
                        }
                        showServerModal = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF070708)
                    )
                ) {
                    Text("Save & Connect", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showServerModal = false }) {
                    Text("Close", color = secondaryFg)
                }
            }
        )
    }

    // Modal: Create New Antigravity Workspace Session
    if (showNewSessionModal) {
        var workspace by remember { mutableStateOf("") }
        var branch by remember { mutableStateOf("main") }
        var taskPrompt by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showNewSessionModal = false },
            containerColor = surface,
            title = {
                Text(
                    "Start New Antigravity Session",
                    style = MaterialTheme.typography.titleLarge,
                    color = fg
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = workspace,
                        onValueChange = { workspace = it },
                        label = { Text("Workspace / Repo Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = branch,
                        onValueChange = { branch = it },
                        label = { Text("Git Branch") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = taskPrompt,
                        onValueChange = { taskPrompt = it },
                        label = { Text("Initial Task Prompt (Optional)") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        antigravityViewModel.createNewSession(workspace, branch, taskPrompt)
                        showNewSessionModal = false
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF070708)
                    )
                ) {
                    Text("Create Session", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewSessionModal = false }) {
                    Text("Cancel", color = secondaryFg)
                }
            }
        )
    }

    // Modal: Hybrid Bridge Explainer
    if (showBridgeExplainerModal) {
        AlertDialog(
            onDismissRequest = { showBridgeExplainerModal = false },
            containerColor = surface,
            title = {
                Text(
                    "Hybrid Native + Pinned WebView Bridge",
                    style = MaterialTheme.typography.titleLarge,
                    color = accent
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "1. Zero-Lag Local Room Cache: Conversations, diffs, and shell logs open in 0ms from on-device SQLite.\n\n" +
                            "2. Fullscreen Mode: Tap the Fullscreen icon in the top bar (or toggle it in Settings) to hide all top and bottom bars completely. Tap the subtle left-edge handle anytime to summon controls.\n\n" +
                            "3. Official Antigravity Themes: Tap the gear icon to switch between Default Dark, Catppuccin, Dracula, Tokyo Night, Vesper, or custom hex colors.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = fg
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showBridgeExplainerModal = false },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF070708)
                    )
                ) {
                    Text("Got It", fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

/**
 * Sleek 1-line Antigravity Remote Header Bar matching Screenshot 3 (`Screenshot_20261118-112653_7786cf00.jpg`):
 * `[A logo] [ ● 55d52da... ▾ ]   🔍   ⚙️   ↻   ⛶`
 */
@Composable
private fun AntigravityRemoteTopBar(
    sessions: List<AgentSessionEntity>,
    selectedSessionId: String,
    latencyMs: Int,
    fg: Color,
    secondaryFg: Color,
    elevated: Color,
    border: Color,
    accent: Color,
    onSelectSession: (String) -> Unit,
    onOpenServerModal: () -> Unit,
    onOpenNewSessionModal: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenAppearance: () -> Unit,
    onPingRefresh: () -> Unit,
    onOpenExplainer: () -> Unit,
    onHideBarsFullscreen: () -> Unit
) {
    var showSessionMenu by remember { mutableStateOf(false) }
    val activeSession = sessions.firstOrNull { it.id == selectedSessionId } ?: sessions.firstOrNull()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Antigravity "A" Emblem + Compact Session Selector Pill `[ ● 55d52da... ▾ ]`
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            // Antigravity "A" vector emblem
            Canvas(
                modifier = Modifier
                    .size(26.dp)
                    .clickable { onOpenExplainer() }
                    .testTag("antigravity_logo_button")
            ) {
                val path = Path().apply {
                    moveTo(size.width * 0.15f, size.height * 0.85f)
                    cubicTo(
                        size.width * 0.32f, size.height * 0.45f,
                        size.width * 0.42f, size.height * 0.15f,
                        size.width * 0.5f, size.height * 0.15f
                    )
                    cubicTo(
                        size.width * 0.58f, size.height * 0.15f,
                        size.width * 0.68f, size.height * 0.45f,
                        size.width * 0.85f, size.height * 0.85f
                    )
                }
                drawPath(
                    path = path,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0xFF4285F4),
                            Color(0xFFEA4335),
                            Color(0xFFFBBC05),
                            Color(0xFF34A853)
                        )
                    ),
                    style = Stroke(width = 6f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Compact Session Dropdown Pill matching `[ ● 55d52da... ▾ ]` in Screenshot 3
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(elevated)
                        .border(1.dp, border, RoundedCornerShape(10.dp))
                        .clickable { showSessionMenu = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("top_session_dropdown_pill"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(EmeraldOnline)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activeSession?.workspaceName?.take(12)?.let { "$it..." }
                            ?: "55d52da...",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = fg,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Switch Session or Host",
                        tint = secondaryFg,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showSessionMenu,
                    onDismissRequest = { showSessionMenu = false },
                    modifier = Modifier.background(elevated)
                ) {
                    sessions.forEach { sess ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = "${sess.workspaceName} (${sess.branchName})",
                                        color = if (sess.id == selectedSessionId) accent else fg,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${sess.status} • ${latencyMs}ms",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryFg
                                    )
                                }
                            },
                            onClick = {
                                onSelectSession(sess.id)
                                showSessionMenu = false
                            }
                        )
                    }
                    HorizontalDivider(color = border)
                    DropdownMenuItem(
                        text = { Text("+ New Workspace Session", color = accent) },
                        onClick = {
                            showSessionMenu = false
                            onOpenNewSessionModal()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Configure Remote Host (${latencyMs}ms)", color = fg) },
                        onClick = {
                            showSessionMenu = false
                            onOpenServerModal()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("How to Connect & Use Guide", color = accent) },
                        onClick = {
                            showSessionMenu = false
                            onOpenExplainer()
                        }
                    )
                }
            }
        }

        // Right: Search (🔍), Guide (?), Appearance Settings (⚙️), Refresh/Ping (↻), Fullscreen Hide Bars (⛶)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onToggleSearch,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("top_search_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search Session",
                    tint = secondaryFg,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = onOpenExplainer,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("top_guide_button")
            ) {
                Icon(
                    imageVector = Icons.Default.HelpOutline,
                    contentDescription = "How to Connect & Use Guide",
                    tint = accent,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = onOpenAppearance,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("top_appearance_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Appearance & Theme Customizer",
                    tint = secondaryFg,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = onPingRefresh,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("top_refresh_bridge_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Ping & Refresh Bridge",
                    tint = secondaryFg,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = onHideBarsFullscreen,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("top_hide_bars_fullscreen_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Hide Top & Bottom Bars for Fullscreen",
                    tint = accent,
                    modifier = Modifier.size(21.dp)
                )
            }
        }
    }
}
