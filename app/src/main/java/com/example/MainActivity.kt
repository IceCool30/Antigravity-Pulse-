package com.example

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.cloud.configureAppCheck
import com.example.data.local.AgentSessionEntity
import com.example.data.local.AntigravityDatabase
import com.example.data.local.ServerProfileEntity
import com.example.data.remote.ApprovalNotificationHelper
import com.example.ui.screens.AppearanceSettingsSheet
import com.example.ui.screens.CodeDiffAndTerminalScreen
import com.example.ui.screens.ConnectionGuideScreen
import com.example.ui.screens.CopilotAndImageStudioScreen
import com.example.ui.screens.GeminiLiveVoiceModal
import com.example.ui.screens.QrPairingScannerDialog
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

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory Firebase App Check initialization with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)

        enableEdgeToEdge()

        setContent {
            val context = LocalContext.current
            val appearanceManager = remember { AppearancePreferencesManager(context) }
            val appearance by appearanceManager.settings.collectAsStateWithLifecycle()
            var isBiometricUnlocked by rememberSaveable { mutableStateOf(!appearance.isBiometricLockEnabled) }

            AntigravityPulseTheme(appearance = appearance) {
                if (appearance.isBiometricLockEnabled && !isBiometricUnlocked) {
                    BiometricLockGateScreen(
                        activity = this@MainActivity,
                        appearance = appearance,
                        onUnlocked = { isBiometricUnlocked = true }
                    )
                } else {
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
}

@Composable
private fun BiometricLockGateScreen(
    activity: FragmentActivity,
    appearance: AppearanceSettings,
    onUnlocked: () -> Unit
) {
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val border = appearance.borderColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor

    var authError by remember { mutableStateOf<String?>(null) }

    val biometricManager = remember { BiometricManager.from(activity) }
    val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or
        BiometricManager.Authenticators.DEVICE_CREDENTIAL
    val canAuthenticateStatus = remember { biometricManager.canAuthenticate(authenticators) }

    fun launchBiometricPrompt() {
        authError = null
        if (canAuthenticateStatus != BiometricManager.BIOMETRIC_SUCCESS) {
            authError = "No fingerprint, face unlock, or screen lock is enrolled on this device."
            return
        }
        val executor = ContextCompat.getMainExecutor(activity)
        val prompt = BiometricPrompt(
            activity,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    onUnlocked()
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    authError = errString.toString()
                }

                override fun onAuthenticationFailed() {
                    super.onAuthenticationFailed()
                    authError = "Biometric verification did not match. Please try again."
                }
            }
        )
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock Antigravity Pulse")
            .setSubtitle("Verify your identity to access your remote workspaces & pairing tokens")
            .setAllowedAuthenticators(authenticators)
            .build()
        prompt.authenticate(promptInfo)
    }

    LaunchedEffect(Unit) {
        if (canAuthenticateStatus == BiometricManager.BIOMETRIC_SUCCESS) {
            launchBiometricPrompt()
        }
    }

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
                .background(bg)
                .padding(24.dp)
                .testTag("biometric_lock_screen"),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .background(surface)
                    .border(1.dp, border, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.16f))
                        .border(1.5.dp, accent.copy(alpha = 0.55f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Fingerprint,
                        contentDescription = "Biometric Lock",
                        tint = accent,
                        modifier = Modifier.size(34.dp)
                    )
                }

                Text(
                    text = "Antigravity Pulse Locked",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = fg
                )
                Text(
                    text = "Your remote bridge endpoints, pairing tokens, and workspace sessions are protected by Biometric App Lock.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = secondaryFg,
                    textAlign = TextAlign.Center
                )

                if (authError != null) {
                    Text(
                        text = authError.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = { launchBiometricPrompt() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF070708)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("biometric_unlock_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Unlock with Biometrics / PIN", fontWeight = FontWeight.Bold)
                }

                if (canAuthenticateStatus != BiometricManager.BIOMETRIC_SUCCESS) {
                    OutlinedButton(
                        onClick = onUnlocked,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("biometric_fallback_continue_button")
                    ) {
                        Text("Continue (No Device Lock Configured)", color = secondaryFg)
                    }
                }
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
    val isExecutingCommand by antigravityViewModel.isExecutingCommand.collectAsStateWithLifecycle()
    val remoteBrowsePath by antigravityViewModel.remoteBrowsePath.collectAsStateWithLifecycle()
    val remoteFileEntries by antigravityViewModel.remoteFileEntries.collectAsStateWithLifecycle()
    val previewFilePath by antigravityViewModel.previewFilePath.collectAsStateWithLifecycle()
    val previewFileContent by antigravityViewModel.previewFileContent.collectAsStateWithLifecycle()
    val isLoadingRemoteFiles by antigravityViewModel.isLoadingRemoteFiles.collectAsStateWithLifecycle()
    val remoteFileError by antigravityViewModel.remoteFileError.collectAsStateWithLifecycle()

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
    var showGlobalQrScanner by rememberSaveable { mutableStateOf(false) }
    var showGlobalLiveVoice by rememberSaveable { mutableStateOf(false) }

    val activeSession = sessions.firstOrNull { it.id == selectedSessionId } ?: sessions.firstOrNull()
    val pendingApprovalMessage = activeMessages.lastOrNull {
        it.senderRole == "TOOL_APPROVAL" && it.approvalStatus == "PENDING"
    }

    // Keep auto-reconnect policy synced with persisted settings
    LaunchedEffect(appearance.isAutoReconnectEnabled) {
        antigravityViewModel.setAutoReconnectEnabled(appearance.isAutoReconnectEnabled)
    }

    // Post actionable Android notifications whenever a step approval arrives while enabled
    LaunchedEffect(appearance.isApprovalNotificationsEnabled) {
        antigravityViewModel.pendingApprovalNotificationEvents.collect { approvalMsg ->
            if (appearance.isApprovalNotificationsEnabled) {
                val workspace = sessions.firstOrNull { it.id == approvalMsg.sessionId }?.workspaceName
                    ?: "Remote Workspace"
                val posted = ApprovalNotificationHelper.showStepApprovalNotification(
                    context = context,
                    messageId = approvalMsg.id,
                    workspaceName = workspace,
                    command = approvalMsg.commandOrPath
                )
                if (!posted) {
                    antigravityViewModel.recordWebViewBridgeEvent(
                        "Enable Notification permission in Settings to approve steps from the shade."
                    )
                }
            }
        }
    }

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
                    onToggleAutoReconnect = antigravityViewModel::setAutoReconnectEnabled,
                    onSendTestApprovalNotification = antigravityViewModel::triggerTestApprovalNotification,
                    onClose = { showAppearanceSheet = false }
                )
            }
        }
        return
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        val isWideScreen = maxWidth >= 720.dp

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
            // Compact portrait/handheld Bottom Navigation Bar (replaced by NavigationRail on wide/foldable screens)
            AnimatedVisibility(
                visible = !isBarsHidden && !isWideScreen,
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
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(bg)
        ) {
            // Wide / Foldable / Landscape Side NavigationRail
            if (isWideScreen && !isBarsHidden) {
                NavigationRail(
                    containerColor = surface,
                    modifier = Modifier.testTag("adaptive_navigation_rail")
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    PulseTab.entries.forEachIndexed { index, tab ->
                        NavigationRailItem(
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
                                    maxLines = 1
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                selectedIconColor = accent,
                                selectedTextColor = accent,
                                indicatorColor = accent.copy(alpha = 0.16f),
                                unselectedIconColor = secondaryFg,
                                unselectedTextColor = secondaryFg
                            ),
                            modifier = Modifier.testTag("${tab.tag}_rail")
                        )
                    }
                }
                VerticalDivider(color = border, thickness = 1.dp)
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
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

                    val workspacePane: @Composable () -> Unit = {
                        CodeDiffAndTerminalScreen(
                            diffs = activeDiffs,
                            terminalLogs = activeTerminalLogs,
                            telemetry = telemetry,
                            isExecutingCommand = isExecutingCommand,
                            savedMacros = appearance.savedTerminalMacros,
                            remoteBrowsePath = remoteBrowsePath,
                            remoteFileEntries = remoteFileEntries,
                            previewFilePath = previewFilePath,
                            previewFileContent = previewFileContent,
                            isLoadingRemoteFiles = isLoadingRemoteFiles,
                            remoteFileError = remoteFileError,
                            onAcceptDiff = { diff ->
                                antigravityViewModel.updateFileDiffDecision(diff, true)
                            },
                            onRevertDiff = { diff ->
                                antigravityViewModel.updateFileDiffDecision(diff, false)
                            },
                            onRunCommand = antigravityViewModel::runNativeTerminalCommand,
                            onSendCtrlC = antigravityViewModel::sendTerminalCtrlC,
                            onClearTerminal = antigravityViewModel::clearTerminalForActiveSession,
                            onOpenServerConfig = { showServerModal = true },
                            onSaveMacro = { macro ->
                                onUpdateAppearance { cur ->
                                    cur.copy(savedTerminalMacros = (cur.savedTerminalMacros + macro).distinct())
                                }
                            },
                            onDeleteMacro = { macro ->
                                onUpdateAppearance { cur ->
                                    cur.copy(savedTerminalMacros = cur.savedTerminalMacros.filterNot { it == macro })
                                }
                            },
                            onDebugErrorWithCopilot = { failedCmd, output ->
                                val debugPrompt = "Debug and fix this failing remote shell command:\n\nCommand:\n```bash\n$failedCmd\n```\n\nOutput:\n```\n${output.take(2500)}\n```\n\nExplain the root cause concisely and provide the exact fix command or code patch."
                                selectedTabIndex = PulseTab.COPILOT.ordinal
                                copilotViewModel.sendChatMessage(debugPrompt)
                            },
                            onBrowseRemotePath = antigravityViewModel::browseRemoteDirectory,
                            onReadRemoteFile = antigravityViewModel::openRemoteFile,
                            onCloseRemoteFilePreview = antigravityViewModel::closeRemoteFilePreview,
                            onAttachRemoteFileToPrompt = { filePath, snippet ->
                                val attachedPrompt = if (snippet.isNotBlank()) {
                                    "Attached remote file `$filePath`:\n```\n$snippet\n```\nPlease review this file and advise on next steps."
                                } else {
                                    "Attached remote file reference: @$filePath"
                                }
                                antigravityViewModel.sendPromptToAntigravityAgent(attachedPrompt)
                                if (!isWideScreen) {
                                    selectedTabIndex = PulseTab.SESSIONS.ordinal
                                }
                            }
                        )
                    }

                    val sessionsPane: @Composable () -> Unit = {
                        SessionsAndChatScreen(
                            sessions = sessions,
                            selectedSessionId = selectedSessionId,
                            messages = activeMessages,
                            activeEndpointUrl = telemetry.activeEndpoint,
                            isFullscreenBarsHidden = isBarsHidden,
                            searchQuery = searchQuery,
                            onClearSearch = { searchQuery = "" },
                            onSelectSession = antigravityViewModel::selectSession,
                            onOpenNewSessionModal = { showNewSessionModal = true },
                            onOpenServerModal = { showServerModal = true },
                            onSendPrompt = { prompt ->
                                antigravityViewModel.sendPromptToAntigravityAgent(prompt)
                            },
                            onResolveApproval = antigravityViewModel::resolveStepApproval,
                            onUpdateDraft = antigravityViewModel::updateDraftAndScroll
                        )
                    }

                    Box(modifier = Modifier.weight(1f)) {
                        val activeTab = PulseTab.entries[selectedTabIndex]
                        if (isWideScreen && (activeTab == PulseTab.SESSIONS || activeTab == PulseTab.WORKSPACE)) {
                            // Landscape / Foldable Adaptive Two-Pane Split-View (Sessions + Shell/Diffs/Files)
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("adaptive_split_workspace")
                            ) {
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    sessionsPane()
                                }
                                VerticalDivider(color = border, thickness = 1.dp)
                                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                                    workspacePane()
                                }
                            }
                        } else {
                            when (activeTab) {
                                PulseTab.SESSIONS -> sessionsPane()
                                PulseTab.WORKSPACE -> workspacePane()
                                PulseTab.WEB_BRIDGE -> RetainedWebViewBridgeScreen(
                                    telemetry = telemetry,
                                    isFullscreenBarsHidden = isBarsHidden,
                                    onToggleDomBooster = antigravityViewModel::toggleDomLagBooster,
                                    onJsBridgeEvent = antigravityViewModel::recordWebViewBridgeEvent
                                )

                                PulseTab.COPILOT -> CopilotAndImageStudioScreen(
                                    messages = copilotMessages,
                                    activeEndpointUrl = telemetry.activeEndpoint,
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
                                    localSessions = sessions,
                                    onClearLocalData = antigravityViewModel::clearAllLocalWorkspaceData
                                )
                            }
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
                            // Floating Quick Control Overlay Drawer with Power-User Quick Actions
                            Column(
                                modifier = Modifier
                                    .padding(start = 8.dp, top = 8.dp, bottom = 8.dp)
                                    .verticalScroll(rememberScrollState())
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(surface.copy(alpha = 0.97f))
                                    .border(1.dp, border, RoundedCornerShape(20.dp))
                                    .padding(10.dp)
                                    .testTag("floating_quick_menu_overlay"),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Restore Bars button
                                Row(
                                    modifier = Modifier
                                        .width(164.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(accent)
                                        .clickable {
                                            showFloatingQuickMenu = false
                                            onUpdateAppearance { it.copy(isFullscreenBarsHidden = false) }
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("restore_bars_button"),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
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

                                // Power Quick Action: Approve Pending Step (when queued)
                                if (pendingApprovalMessage != null) {
                                    Row(
                                        modifier = Modifier
                                            .width(164.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(EmeraldOnline.copy(alpha = 0.2f))
                                            .border(1.dp, EmeraldOnline, RoundedCornerShape(12.dp))
                                            .clickable {
                                                antigravityViewModel.resolveStepApproval(pendingApprovalMessage, true)
                                                showFloatingQuickMenu = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                            .testTag("floating_quick_approve_step"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CheckCircle,
                                            contentDescription = "Approve Pending Step",
                                            tint = EmeraldOnline,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Approve Step",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldOnline
                                        )
                                    }
                                }

                                // Power Quick Action: Live Voice (gemini-3.8-live)
                                Row(
                                    modifier = Modifier
                                        .width(164.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(elevated)
                                        .clickable {
                                            showFloatingQuickMenu = false
                                            showGlobalLiveVoice = true
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                        .testTag("floating_quick_live_voice"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GraphicEq,
                                        contentDescription = "Live Voice Copilot",
                                        tint = accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Live Voice",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = fg
                                    )
                                }

                                // Power Quick Action: Scan QR Pair
                                Row(
                                    modifier = Modifier
                                        .width(164.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(elevated)
                                        .clickable {
                                            showFloatingQuickMenu = false
                                            showGlobalQrScanner = true
                                        }
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                        .testTag("floating_quick_scan_qr"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = "Scan QR Pair",
                                        tint = accent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Scan QR Pair",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = fg
                                    )
                                }

                                // Power Quick Action: Run Last Shell Command (when available)
                                val lastCmd = activeTerminalLogs.lastOrNull()?.command
                                if (!lastCmd.isNullOrBlank()) {
                                    Row(
                                        modifier = Modifier
                                            .width(164.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(elevated)
                                            .clickable {
                                                antigravityViewModel.runNativeTerminalCommand(lastCmd)
                                                showFloatingQuickMenu = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 8.dp)
                                            .testTag("floating_quick_rerun_last_cmd"),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PlayArrow,
                                            contentDescription = "Run Last Command",
                                            tint = EmeraldOnline,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Re-run: ${lastCmd.take(11)}",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = fg,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                HorizontalDivider(
                                    color = border,
                                    modifier = Modifier.width(150.dp)
                                )

                                // Quick Tab Switcher inside Fullscreen Mode
                                PulseTab.entries.forEachIndexed { index, tab ->
                                    val isSelected = selectedTabIndex == index
                                    Row(
                                        modifier = Modifier
                                            .width(164.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(
                                                if (isSelected) accent.copy(alpha = 0.18f)
                                                else Color.Transparent
                                            )
                                            .clickable {
                                                selectedTabIndex = index
                                                showFloatingQuickMenu = false
                                            }
                                            .padding(horizontal = 10.dp, vertical = 7.dp),
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
                                    modifier = Modifier.width(150.dp)
                                )

                                // Quick Appearance Customizer button
                                Row(
                                    modifier = Modifier
                                        .width(164.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            showFloatingQuickMenu = false
                                            showAppearanceSheet = true
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
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
                                        text = "Settings",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = fg
                                    )
                                }

                                // Quick Connection Guide button
                                Row(
                                    modifier = Modifier
                                        .width(164.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable {
                                            showFloatingQuickMenu = false
                                            showConnectionGuide = true
                                        }
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
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
    }
    }

    // Modal: Server Profile Switcher & Host Connection Editor (with Built-In QR Scanner)
    if (showServerModal) {
        var hostName by remember { mutableStateOf("") }
        var hostUrl by remember { mutableStateOf("https://") }
        var hostToken by remember { mutableStateOf("") }
        var keepAlive by remember { mutableStateOf(true) }
        var showModalQrScanner by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showServerModal = false },
            containerColor = surface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Remote Endpoints",
                        style = MaterialTheme.typography.titleLarge,
                        color = fg
                    )
                    OutlinedButton(
                        onClick = { showModalQrScanner = true },
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("server_modal_scan_qr_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan QR Code",
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Scan QR", color = accent, fontWeight = FontWeight.Bold)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
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
                                text = if (profile.lastLatencyMs > 0) "${profile.lastLatencyMs}ms" else "Saved",
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
                        if (hostUrl.isNotBlank()) {
                            antigravityViewModel.addOrUpdateServerProfile(
                                null,
                                hostName.ifBlank { "Antigravity Remote" },
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

        if (showModalQrScanner) {
            QrPairingScannerDialog(
                onPairingScanned = { payload ->
                    hostName = payload.hostName
                    hostUrl = payload.endpointUrl
                    hostToken = payload.authToken
                    showModalQrScanner = false
                },
                onDismiss = { showModalQrScanner = false }
            )
        }
    }

    // Global QR Scanner triggered from Fullscreen Quick Menu
    if (showGlobalQrScanner) {
        QrPairingScannerDialog(
            onPairingScanned = { payload ->
                antigravityViewModel.addOrUpdateServerProfile(
                    existingId = null,
                    name = payload.hostName,
                    endpointUrl = payload.endpointUrl,
                    connectionMode = "HYBRID_BRIDGE",
                    authToken = payload.authToken,
                    keepAlivePinned = true
                )
                showGlobalQrScanner = false
            },
            onDismiss = { showGlobalQrScanner = false }
        )
    }

    // Global Gemini Live Voice triggered from Fullscreen Quick Menu
    if (showGlobalLiveVoice) {
        GeminiLiveVoiceModal(
            activeWorkspaceName = activeSession?.workspaceName ?: "Remote Workspace",
            activeEndpointUrl = telemetry.activeEndpoint,
            onDispatchPromptToAntigravity = { prompt ->
                antigravityViewModel.sendPromptToAntigravityAgent(prompt)
                showGlobalLiveVoice = false
            },
            onRunRemoteTerminalCommand = { cmd ->
                antigravityViewModel.runNativeTerminalCommand(cmd)
            },
            onDismiss = { showGlobalLiveVoice = false }
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
                        text = activeSession?.workspaceName?.take(14) ?: "No Session",
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
                        text = {
                            Text(
                                text = if (latencyMs > 0) "Configure Remote Host (${latencyMs}ms)" else "Configure Remote Host",
                                color = fg
                            )
                        },
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
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
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
