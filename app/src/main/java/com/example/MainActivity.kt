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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.cloud.configureAppCheck
import com.example.data.local.AntigravityDatabase
import com.example.data.local.ServerProfileEntity
import com.example.data.remote.ApprovalNotificationHelper
import com.example.data.remote.RetainedWebViewManager
import com.example.ui.components.FloatingPanelTabItem
import com.example.ui.components.MovableFloatingControlPanel
import com.example.ui.screens.AppearanceSettingsSheet
import com.example.ui.screens.ConnectionGuideScreen
import com.example.ui.screens.CopilotAndImageStudioScreen
import com.example.ui.screens.GeminiLiveVoiceModal
import com.example.ui.screens.QrPairingScannerDialog
import com.example.ui.screens.RetainedWebViewBridgeScreen
import com.example.ui.screens.StorageAndCloudSyncScreen
import com.example.ui.theme.AntigravityPulseTheme
import com.example.ui.theme.AppearancePreferencesManager
import com.example.ui.theme.AppearanceSettings
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalAppearanceSettings
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
            .setSubtitle("Verify your identity to access your remote endpoints & pairing tokens")
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
                    text = "Your remote bridge endpoints and pairing tokens are protected by Biometric App Lock.",
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
    val telemetry by antigravityViewModel.bridgeTelemetry.collectAsStateWithLifecycle()
    val statusBanner by antigravityViewModel.statusBanner.collectAsStateWithLifecycle()

    val copilotMessages by copilotViewModel.messages.collectAsStateWithLifecycle()
    val selectedModel by copilotViewModel.selectedModel.collectAsStateWithLifecycle()
    val selectedPersona by copilotViewModel.selectedPersona.collectAsStateWithLifecycle()
    val chatUiState by copilotViewModel.chatUiState.collectAsStateWithLifecycle()
    val imageUiState by copilotViewModel.imageUiState.collectAsStateWithLifecycle()
    val selectedInputBitmap by copilotViewModel.selectedInputBitmap.collectAsStateWithLifecycle()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(PulseTab.WEB_BRIDGE.ordinal) }
    var showConnectionGuide by rememberSaveable { mutableStateOf(appearance.showStartupGuideOnLaunch) }
    var showAppearanceSheet by rememberSaveable { mutableStateOf(false) }
    var showFloatingQuickMenu by rememberSaveable { mutableStateOf(false) }
    var showServerModal by rememberSaveable { mutableStateOf(false) }
    var showGlobalQrScanner by rememberSaveable { mutableStateOf(false) }
    var showGlobalLiveVoice by rememberSaveable { mutableStateOf(false) }

    val activeProfile = serverProfiles.firstOrNull { it.isSelected } ?: serverProfiles.firstOrNull()

    // Keep auto-reconnect policy synced with persisted settings
    LaunchedEffect(appearance.isAutoReconnectEnabled) {
        antigravityViewModel.setAutoReconnectEnabled(appearance.isAutoReconnectEnabled)
    }

    // Post actionable Android notifications whenever a step approval arrives while enabled
    LaunchedEffect(appearance.isApprovalNotificationsEnabled) {
        antigravityViewModel.pendingApprovalNotificationEvents.collect { approvalMsg ->
            if (appearance.isApprovalNotificationsEnabled) {
                val posted = ApprovalNotificationHelper.showStepApprovalNotification(
                    context = context,
                    messageId = approvalMsg.id,
                    workspaceName = activeProfile?.name ?: "Antigravity Remote",
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

    // Smart Back navigation: close floating panel -> return to Remote tab -> go back inside WebView
    BackHandler(
        enabled = !showConnectionGuide && !showAppearanceSheet &&
            (showFloatingQuickMenu || selectedTabIndex != PulseTab.WEB_BRIDGE.ordinal || RetainedWebViewManager.canGoBack())
    ) {
        when {
            showFloatingQuickMenu -> showFloatingQuickMenu = false
            selectedTabIndex != PulseTab.WEB_BRIDGE.ordinal -> selectedTabIndex = PulseTab.WEB_BRIDGE.ordinal
            else -> RetainedWebViewManager.goBack()
        }
    }

    fun connectAndOpenRemoteHost(hostName: String, url: String, token: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        val cleanName = hostName.trim().ifBlank { "Antigravity Remote" }
        antigravityViewModel.addOrUpdateServerProfile(
            existingId = null,
            name = cleanName,
            endpointUrl = cleanUrl,
            connectionMode = "HYBRID_BRIDGE",
            authToken = token.trim(),
            keepAlivePinned = true
        )
        RetainedWebViewManager.reloadOrForceLoadUrl(cleanUrl, telemetry.domLagBoosterEnabled)
        selectedTabIndex = PulseTab.WEB_BRIDGE.ordinal
    }

    // Step-by-Step Connection & Usage Guide (opened on demand from movable panel or Settings)
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
                        connectAndOpenRemoteHost(hostName, url, token)
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

    val panelTabs = PulseTab.entries.mapIndexed { idx, tab ->
        FloatingPanelTabItem(
            index = idx,
            title = stringResource(id = tab.titleRes),
            icon = tab.icon,
            testTag = tab.tag
        )
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
                            serverProfiles = serverProfiles,
                            activeProfile = activeProfile,
                            latencyMs = telemetry.latencyMs,
                            fg = fg,
                            secondaryFg = secondaryFg,
                            elevated = elevated,
                            border = border,
                            accent = accent,
                            onSelectProfile = { profile ->
                                antigravityViewModel.selectServerProfile(profile)
                                RetainedWebViewManager.reloadOrForceLoadUrl(
                                    profile.endpointUrl,
                                    telemetry.domLagBoosterEnabled
                                )
                                selectedTabIndex = PulseTab.WEB_BRIDGE.ordinal
                            },
                            onOpenServerModal = { showServerModal = true },
                            onOpenQrScanner = { showGlobalQrScanner = true },
                            onOpenLiveVoice = { showGlobalLiveVoice = true },
                            onRecenterLayout = {
                                RetainedWebViewManager.injectMobileLayoutFix()
                                antigravityViewModel.recordWebViewBridgeEvent("Re-centered Remote mobile layout")
                            },
                            onOpenAppearance = { showAppearanceSheet = true },
                            onOpenGuide = { showConnectionGuide = true },
                            onHideBarsFullscreen = {
                                onUpdateAppearance { it.copy(isFullscreenBarsHidden = true) }
                            }
                        )
                        HorizontalDivider(color = border, thickness = 1.dp)
                    }
                }
            },
            bottomBar = {
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

                        Box(modifier = Modifier.weight(1f)) {
                            val safeIndex = selectedTabIndex.coerceIn(0, PulseTab.entries.lastIndex)
                            when (PulseTab.entries[safeIndex]) {
                                PulseTab.WEB_BRIDGE -> RetainedWebViewBridgeScreen(
                                    telemetry = telemetry,
                                    isFullscreenBarsHidden = isBarsHidden,
                                    onToggleDomBooster = antigravityViewModel::toggleDomLagBooster,
                                    onSaveAndConnectUrl = { cleanUrl ->
                                        connectAndOpenRemoteHost(
                                            hostName = activeProfile?.name ?: "Antigravity Remote",
                                            url = cleanUrl,
                                            token = activeProfile?.authToken.orEmpty()
                                        )
                                    },
                                    onOpenQrScanner = { showGlobalQrScanner = true },
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
                                        antigravityViewModel.insertPromptIntoRemoteWebView(refinedPrompt)
                                        selectedTabIndex = PulseTab.WEB_BRIDGE.ordinal
                                    },
                                    onSetInputBitmap = copilotViewModel::setInputBitmapForEditing,
                                    onGenerateOrEditImage = copilotViewModel::generateOrEditUiAsset
                                )

                                PulseTab.STORAGE_SYNC -> StorageAndCloudSyncScreen(
                                    dao = dao,
                                    localProfiles = serverProfiles,
                                    onConnectHostUrl = { name, endpointUrl, token ->
                                        connectAndOpenRemoteHost(name, endpointUrl, token)
                                    },
                                    onInsertPromptIntoRemote = { promptText ->
                                        antigravityViewModel.insertPromptIntoRemoteWebView(promptText)
                                        selectedTabIndex = PulseTab.WEB_BRIDGE.ordinal
                                    },
                                    onOpenAddHostModal = { showServerModal = true },
                                    onClearLocalData = antigravityViewModel::clearAllLocalWorkspaceData
                                )
                            }
                        }
                    }

                    // Movable / Draggable Floating Side Panel (Active by default in Fullscreen Mode)
                    if (isBarsHidden) {
                        MovableFloatingControlPanel(
                            isExpanded = showFloatingQuickMenu,
                            xFraction = appearance.floatingHandleXFraction,
                            yFraction = appearance.floatingHandleYFraction,
                            tabs = panelTabs,
                            selectedTabIndex = selectedTabIndex.coerceIn(0, PulseTab.entries.lastIndex),
                            activeHostLabel = activeProfile?.name ?: "Connect Remote URL",
                            surface = surface,
                            elevated = elevated,
                            border = border,
                            fg = fg,
                            secondaryFg = secondaryFg,
                            accent = accent,
                            onExpandedChange = { showFloatingQuickMenu = it },
                            onPersistPositionFraction = { xFrac, yFrac ->
                                onUpdateAppearance {
                                    it.copy(
                                        floatingHandleXFraction = xFrac,
                                        floatingHandleYFraction = yFrac
                                    )
                                }
                            },
                            onSelectTab = { index -> selectedTabIndex = index },
                            onOpenHostSwitcher = { showServerModal = true },
                            onOpenQrScanner = { showGlobalQrScanner = true },
                            onRecenterWebLayout = {
                                RetainedWebViewManager.injectMobileLayoutFix()
                                antigravityViewModel.recordWebViewBridgeEvent("Re-centered Antigravity Remote layout")
                            },
                            onReloadWeb = {
                                RetainedWebViewManager.reloadCurrentPage()
                                antigravityViewModel.recordWebViewBridgeEvent("Reloading Antigravity Remote...")
                            },
                            onOpenLiveVoice = { showGlobalLiveVoice = true },
                            onShowBars = {
                                onUpdateAppearance { it.copy(isFullscreenBarsHidden = false) }
                            },
                            onOpenSettings = { showAppearanceSheet = true },
                            onOpenGuide = { showConnectionGuide = true }
                        )
                    }
                }
            }
        }
    }

    // Modal: Remote Host Switcher & URL Connection Editor (with Built-In QR Scanner)
    if (showServerModal) {
        var hostName by remember { mutableStateOf("") }
        var hostUrl by remember { mutableStateOf(telemetry.activeEndpoint.ifBlank { "https://" }) }
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
                        "Remote Hosts",
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
                        "Tap a saved Antigravity Remote host to connect, or paste a new link below:",
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
                                    RetainedWebViewManager.reloadOrForceLoadUrl(
                                        profile.endpointUrl,
                                        telemetry.domLagBoosterEnabled
                                    )
                                    selectedTabIndex = PulseTab.WEB_BRIDGE.ordinal
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
                        "Add / Connect New Remote Host",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent
                    )
                    OutlinedTextField(
                        value = hostName,
                        onValueChange = { hostName = it },
                        label = { Text("Host Label (e.g., My Workstation)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = hostUrl,
                        onValueChange = { hostUrl = it },
                        label = { Text("Antigravity Remote URL (https:// or http://)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = hostToken,
                        onValueChange = { hostToken = it },
                        label = { Text("Pairing / Auth Token (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Keep Remote pinned in RAM",
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
                            connectAndOpenRemoteHost(
                                hostName = hostName.ifBlank { "Antigravity Remote" },
                                url = hostUrl,
                                token = hostToken
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

    // Global QR Scanner triggered from Movable Side Panel or Top Bar
    if (showGlobalQrScanner) {
        QrPairingScannerDialog(
            onPairingScanned = { payload ->
                connectAndOpenRemoteHost(
                    hostName = payload.hostName,
                    url = payload.endpointUrl,
                    token = payload.authToken
                )
                showGlobalQrScanner = false
            },
            onDismiss = { showGlobalQrScanner = false }
        )
    }

    // Global Pause-Friendly Gemini Live Voice Modal
    if (showGlobalLiveVoice) {
        GeminiLiveVoiceModal(
            activeWorkspaceName = activeProfile?.name ?: "Antigravity Remote",
            activeEndpointUrl = telemetry.activeEndpoint,
            onDispatchPromptToAntigravity = { prompt ->
                antigravityViewModel.insertPromptIntoRemoteWebView(prompt)
                selectedTabIndex = PulseTab.WEB_BRIDGE.ordinal
                showGlobalLiveVoice = false
            },
            onRunRemoteTerminalCommand = { cmd ->
                antigravityViewModel.insertPromptIntoRemoteWebView(cmd)
                selectedTabIndex = PulseTab.WEB_BRIDGE.ordinal
            },
            onDismiss = { showGlobalLiveVoice = false }
        )
    }
}

/**
 * Compact 1-line Antigravity Remote Header Bar (shown only when user chooses "Show Bars").
 */
@Composable
private fun AntigravityRemoteTopBar(
    serverProfiles: List<ServerProfileEntity>,
    activeProfile: ServerProfileEntity?,
    latencyMs: Int,
    fg: Color,
    secondaryFg: Color,
    elevated: Color,
    border: Color,
    accent: Color,
    onSelectProfile: (ServerProfileEntity) -> Unit,
    onOpenServerModal: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onOpenLiveVoice: () -> Unit,
    onRecenterLayout: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenGuide: () -> Unit,
    onHideBarsFullscreen: () -> Unit
) {
    var showHostMenu by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: Antigravity "A" Emblem + Compact Saved Remote Host Dropdown Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false)
        ) {
            Canvas(
                modifier = Modifier
                    .size(26.dp)
                    .clickable { onOpenGuide() }
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

            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(elevated)
                        .border(1.dp, border, RoundedCornerShape(10.dp))
                        .clickable { showHostMenu = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("top_session_dropdown_pill"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (activeProfile != null) EmeraldOnline else accent)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = activeProfile?.name?.take(14) ?: "Connect Host",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = fg,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Switch Remote Host",
                        tint = secondaryFg,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showHostMenu,
                    onDismissRequest = { showHostMenu = false },
                    modifier = Modifier.background(elevated)
                ) {
                    serverProfiles.forEach { profile ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = profile.name,
                                        color = if (profile.isSelected) accent else fg,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = profile.endpointUrl,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryFg,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            },
                            onClick = {
                                onSelectProfile(profile)
                                showHostMenu = false
                            }
                        )
                    }
                    if (serverProfiles.isNotEmpty()) {
                        HorizontalDivider(color = border)
                    }
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = if (latencyMs > 0) "+ Add / Manage Host (${latencyMs}ms)" else "+ Add / Manage Remote Host",
                                color = accent
                            )
                        },
                        onClick = {
                            showHostMenu = false
                            onOpenServerModal()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("How to Connect & Use Guide", color = fg) },
                        onClick = {
                            showHostMenu = false
                            onOpenGuide()
                        }
                    )
                }
            }
        }

        // Right: QR Scan, Live Voice, Re-Center Layout, Appearance Settings, Fullscreen Hide Bars
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onOpenQrScanner,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("top_qr_scan_button")
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR Code",
                    tint = accent,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = onOpenLiveVoice,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("top_live_voice_button")
            ) {
                Icon(
                    imageVector = Icons.Default.GraphicEq,
                    contentDescription = "Live Voice",
                    tint = EmeraldOnline,
                    modifier = Modifier.size(19.dp)
                )
            }

            IconButton(
                onClick = onRecenterLayout,
                modifier = Modifier
                    .size(36.dp)
                    .testTag("top_recenter_layout_button")
            ) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Re-Center Mobile Layout",
                    tint = secondaryFg,
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
