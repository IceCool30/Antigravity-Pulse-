package com.example

import android.Manifest
import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.Settings
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.PermissionRequest
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import java.util.concurrent.atomic.AtomicLong
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.cloud.authStateFlow
import com.example.data.cloud.clearFirestoreLocalCache
import com.example.data.cloud.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.example.data.local.AntigravityDatabase
import com.example.data.local.ServerProfileEntity
import com.example.data.remote.AiStudioPinnedWebViewManager
import com.example.data.remote.DevWebWorkspace
import com.example.data.remote.DevWebWorkspaceRegistry
import com.example.data.remote.PulseBrowserManager
import com.example.data.remote.RetainedWebViewManager
import com.example.data.remote.T3CodePinnedWebViewManager
import com.example.data.remote.WebViewOfflineCacheHelper
import com.example.data.remote.WorkspaceMemoryKeepAliveCoordinator
import com.example.notifications.PulseNotificationManager
import com.example.service.WorkspaceKeepAliveService
import com.example.ui.components.FloatingPanelTabItem
import com.example.ui.components.MovableFloatingControlPanel
import com.example.ui.screens.AiStudioWebScreen
import com.example.ui.screens.AppearanceSettingsSheet
import com.example.ui.screens.PinnedWebWorkspaceScreen
import com.example.ui.screens.PulseBrowserScreen
import com.example.ui.screens.QrPairingScannerDialog
import com.example.ui.screens.RetainedWebViewBridgeScreen
import com.example.ui.screens.StorageAndCloudSyncScreen
import com.example.ui.screens.T3CodeWebScreen
import com.example.ui.theme.AntigravityPulseTheme
import com.example.ui.theme.AppearancePreferencesManager
import com.example.ui.theme.AppearanceSettings
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalAppearanceSettings
import com.example.ui.theme.frostedGlass
import com.example.viewmodel.AntigravityViewModel

class MainActivity : FragmentActivity() {
    companion object {
        private const val BIOMETRIC_RELOCK_TIMEOUT_MS = 60_000L
    }

    private var backgroundedAtElapsedMs: Long = 0L
    private var relockCallback: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Mandatory Firebase App Check initialization with launch intent before any UI or Firebase calls
        configureAppCheck(this, intent)
        PulseNotificationManager.ensureChannelsCreated(this)

        // Apply FLAG_SECURE synchronously before the first frame renders so Recents/screenshots cannot capture pre-lock UI
        val initialAppearanceManager = AppearancePreferencesManager(this)
        if (initialAppearanceManager.settings.value.isBiometricLockEnabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE
            )
        }

        enableEdgeToEdge()

        setContent {
            val appearanceManager = remember { initialAppearanceManager }
            val appearance by appearanceManager.settings.collectAsStateWithLifecycle()
            // Keep unlock state strictly in RAM (never rememberSaveable) so process death / state restoration cannot bypass the lock
            var isBiometricUnlocked by remember { mutableStateOf(!appearance.isBiometricLockEnabled) }
            var hasUnlockedOnce by remember { mutableStateOf(!appearance.isBiometricLockEnabled) }

            DisposableEffect(appearance.isBiometricLockEnabled) {
                relockCallback = if (appearance.isBiometricLockEnabled) {
                    { isBiometricUnlocked = false }
                } else {
                    null
                }
                onDispose { relockCallback = null }
            }

            // Protect against screenshots, screen capture, and Recents thumbnail leaks when Biometric Lock is active
            LaunchedEffect(appearance.isBiometricLockEnabled) {
                if (appearance.isBiometricLockEnabled) {
                    window.setFlags(
                        WindowManager.LayoutParams.FLAG_SECURE,
                        WindowManager.LayoutParams.FLAG_SECURE
                    )
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            AntigravityPulseTheme(appearance = appearance) {
                val isLockGateActive = appearance.isBiometricLockEnabled && !isBiometricUnlocked
                Box(modifier = Modifier.fillMaxSize()) {
                    // Keep the live remote session mounted once unlocked so returning from
                    // background/biometric lock drops straight back into the exact active session,
                    // while stripping accessibility semantics from the obscured layer while locked.
                    if (!appearance.isBiometricLockEnabled || isBiometricUnlocked || hasUnlockedOnce) {
                        Box(
                            modifier = if (isLockGateActive) {
                                Modifier
                                    .fillMaxSize()
                                    .clearAndSetSemantics {}
                            } else {
                                Modifier.fillMaxSize()
                            }
                        ) {
                            AntigravityPulseApp(
                                appearance = appearance,
                                onUpdateAppearance = appearanceManager::update,
                                onSelectPreset = appearanceManager::applyPreset,
                                onResetPreset = appearanceManager::resetToDefaultDark
                            )
                        }
                    }

                    if (isLockGateActive) {
                        BiometricLockGateScreen(
                            activity = this@MainActivity,
                            appearance = appearance,
                            onUnlocked = {
                                hasUnlockedOnce = true
                                isBiometricUnlocked = true
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PulseNotificationManager.isAppInForeground = true
        if (backgroundedAtElapsedMs > 0L &&
            (SystemClock.elapsedRealtime() - backgroundedAtElapsedMs) > BIOMETRIC_RELOCK_TIMEOUT_MS
        ) {
            relockCallback?.invoke()
        }
        backgroundedAtElapsedMs = 0L
        RetainedWebViewManager.keepTimersRunning()
        AiStudioPinnedWebViewManager.keepTimersRunning()
        T3CodePinnedWebViewManager.keepTimersRunning()
        DevWebWorkspaceRegistry.keepTimersRunningAll()
        PulseBrowserManager.keepTimersRunningAll()
    }

    override fun onPause() {
        PulseNotificationManager.isAppInForeground = false
        WorkspaceMemoryKeepAliveCoordinator.saveAllStateBackups()
        RetainedWebViewManager.keepTimersRunning()
        AiStudioPinnedWebViewManager.keepTimersRunning()
        T3CodePinnedWebViewManager.keepTimersRunning()
        DevWebWorkspaceRegistry.keepTimersRunningAll()
        PulseBrowserManager.keepTimersRunningAll()
        super.onPause()
    }

    override fun onStop() {
        PulseNotificationManager.isAppInForeground = false
        backgroundedAtElapsedMs = SystemClock.elapsedRealtime()
        WorkspaceMemoryKeepAliveCoordinator.saveAllStateBackups()
        RetainedWebViewManager.keepTimersRunning()
        AiStudioPinnedWebViewManager.keepTimersRunning()
        T3CodePinnedWebViewManager.keepTimersRunning()
        DevWebWorkspaceRegistry.keepTimersRunningAll()
        PulseBrowserManager.keepTimersRunningAll()
        super.onStop()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        WorkspaceMemoryKeepAliveCoordinator.onSystemTrimMemory(level)
    }
}

@Composable
private fun BiometricLockGateScreen(
    activity: FragmentActivity,
    appearance: AppearanceSettings,
    onUnlocked: () -> Unit
) {
    val activeTheme = LocalAppearanceSettings.current
    val bg = activeTheme.backgroundColor
    val surface = activeTheme.surfaceColor
    val border = activeTheme.borderColor
    val fg = activeTheme.foregroundColor
    val secondaryFg = activeTheme.secondaryTextColor
    val accent = activeTheme.accentColor
    val isFrostedGlass = activeTheme.isFrostedGlassEnabled

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

    // Intercept hardware/gesture Back so locked state cannot mutate underlying app navigation
    BackHandler(enabled = true) {
        activity.moveTaskToBack(true)
    }

    LaunchedEffect(Unit) {
        if (canAuthenticateStatus == BiometricManager.BIOMETRIC_SUCCESS) {
            launchBiometricPrompt()
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
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
                    .frostedGlass(
                        shape = RoundedCornerShape(24.dp),
                        baseColor = surface,
                        tintColor = accent,
                        borderColor = border,
                        enabled = isFrostedGlass,
                        fillAlpha = 0.84f,
                        tintAlpha = 0.08f,
                        borderAlpha = 0.65f
                    )
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
                        contentColor = activeTheme.onAccentColor
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
    val shortTitleRes: Int,
    val icon: ImageVector,
    val tag: String,
    val devWorkspace: DevWebWorkspace? = null
) {
    WEB_BRIDGE(R.string.nav_bridge, R.string.nav_bridge, Icons.Default.Language, "nav_tab_bridge"),
    T3_CODE(R.string.nav_t3_code, R.string.nav_t3_code, Icons.Default.Terminal, "nav_tab_t3_code"),
    AI_STUDIO(R.string.nav_ai_studio, R.string.nav_ai_studio, Icons.Default.AutoAwesome, "nav_tab_ai_studio"),
    GEMINI(R.string.nav_gemini, R.string.nav_gemini, Icons.Default.Forum, "nav_tab_gemini", DevWebWorkspace.GEMINI),
    GITHUB(R.string.nav_github, R.string.nav_github, Icons.Default.Code, "nav_tab_github", DevWebWorkspace.GITHUB),
    VERCEL(R.string.nav_vercel, R.string.nav_vercel, Icons.Default.RocketLaunch, "nav_tab_vercel", DevWebWorkspace.VERCEL),
    SUPABASE(R.string.nav_supabase, R.string.nav_supabase, Icons.Default.Storage, "nav_tab_supabase", DevWebWorkspace.SUPABASE),
    BROWSER(R.string.nav_browser, R.string.nav_browser, Icons.Default.Public, "nav_tab_browser"),
    STORAGE_SYNC(R.string.nav_sync, R.string.nav_sync_short, Icons.Default.CloudSync, "nav_tab_sync");

    fun workspaceCoordinatorId(): String? = when (this) {
        WEB_BRIDGE -> WorkspaceMemoryKeepAliveCoordinator.WORKSPACE_REMOTE
        T3_CODE -> WorkspaceMemoryKeepAliveCoordinator.WORKSPACE_T3_CODE
        AI_STUDIO -> WorkspaceMemoryKeepAliveCoordinator.WORKSPACE_AI_STUDIO
        GEMINI -> WorkspaceMemoryKeepAliveCoordinator.WORKSPACE_GEMINI
        GITHUB -> WorkspaceMemoryKeepAliveCoordinator.WORKSPACE_GITHUB
        VERCEL -> WorkspaceMemoryKeepAliveCoordinator.WORKSPACE_VERCEL
        SUPABASE -> WorkspaceMemoryKeepAliveCoordinator.WORKSPACE_SUPABASE
        BROWSER -> WorkspaceMemoryKeepAliveCoordinator.WORKSPACE_BROWSER
        STORAGE_SYNC -> null
    }
}

/**
 * Maps incoming WebView `getUserMedia` permission requests strictly to the least-privilege
 * Android runtime permissions (`RECORD_AUDIO` for voice input and `CAMERA` for live video),
 * rejecting any unsupported or sensitive WebView resource types.
 */
internal fun resolveAllowedWebResourcesAndPermissions(
    requestedResources: Array<String>
): Pair<List<String>, List<String>> {
    val allowedResources = mutableListOf<String>()
    val androidPermissions = mutableListOf<String>()
    for (resource in requestedResources) {
        when (resource) {
            PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                allowedResources.add(resource)
                androidPermissions.add(Manifest.permission.RECORD_AUDIO)
            }
            PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                allowedResources.add(resource)
                androidPermissions.add(Manifest.permission.CAMERA)
            }
        }
    }
    return allowedResources.distinct() to androidPermissions.distinct()
}

/**
 * Sanitizes WebView download filenames to prevent directory traversal (`../`, `/`, `\`),
 * hidden dotfiles, control characters, and OS-illegal filename characters.
 */
internal fun sanitizeDownloadFileName(rawName: String?): String {
    val base = rawName.orEmpty()
        .substringAfterLast('/')
        .substringAfterLast('\\')
        .trim()
        .trimStart('.', ' ')
        .replace(Regex("[\\u0000-\\u001F\\u007F:*?\"<>|]"), "_")
        .replace("..", "_")
        .trimStart('.', ' ')
        .trim()
        .take(120)
    return if (base.isBlank() || base.all { it == '_' || it == '.' }) {
        "antigravity-download"
    } else {
        base
    }
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

    val serverProfiles by antigravityViewModel.serverProfiles.collectAsStateWithLifecycle()
    val telemetry by antigravityViewModel.bridgeTelemetry.collectAsStateWithLifecycle()
    val statusBanner by antigravityViewModel.statusBanner.collectAsStateWithLifecycle()
    val remoteCanGoBack by RetainedWebViewManager.canGoBackState.collectAsStateWithLifecycle()
    val aiStudioCanGoBack by AiStudioPinnedWebViewManager.canGoBackState.collectAsStateWithLifecycle()
    val activeAiStudioSection by AiStudioPinnedWebViewManager.activeSectionState.collectAsStateWithLifecycle()
    val t3CanGoBack by T3CodePinnedWebViewManager.canGoBackState.collectAsStateWithLifecycle()
    val t3DesktopMode by T3CodePinnedWebViewManager.isDesktopModeState.collectAsStateWithLifecycle()
    val t3Environments by T3CodePinnedWebViewManager.savedEnvironments.collectAsStateWithLifecycle()
    val t3ActiveEnvId by T3CodePinnedWebViewManager.activeEnvironmentId.collectAsStateWithLifecycle()
    val t3PopupWebView by T3CodePinnedWebViewManager.popupWebViewState.collectAsStateWithLifecycle()
    val githubSession = remember { DevWebWorkspaceRegistry.session(DevWebWorkspace.GITHUB) }
    val vercelSession = remember { DevWebWorkspaceRegistry.session(DevWebWorkspace.VERCEL) }
    val supabaseSession = remember { DevWebWorkspaceRegistry.session(DevWebWorkspace.SUPABASE) }
    val geminiSession = remember { DevWebWorkspaceRegistry.session(DevWebWorkspace.GEMINI) }
    val githubCanGoBack by githubSession.canGoBackState.collectAsStateWithLifecycle()
    val vercelCanGoBack by vercelSession.canGoBackState.collectAsStateWithLifecycle()
    val supabaseCanGoBack by supabaseSession.canGoBackState.collectAsStateWithLifecycle()
    val geminiCanGoBack by geminiSession.canGoBackState.collectAsStateWithLifecycle()
    val githubActiveShortcut by githubSession.activeShortcutId.collectAsStateWithLifecycle()
    val vercelActiveShortcut by vercelSession.activeShortcutId.collectAsStateWithLifecycle()
    val supabaseActiveShortcut by supabaseSession.activeShortcutId.collectAsStateWithLifecycle()
    val geminiActiveShortcut by geminiSession.activeShortcutId.collectAsStateWithLifecycle()
    val githubDesktopMode by githubSession.isDesktopModeState.collectAsStateWithLifecycle()
    val vercelDesktopMode by vercelSession.isDesktopModeState.collectAsStateWithLifecycle()
    val supabaseDesktopMode by supabaseSession.isDesktopModeState.collectAsStateWithLifecycle()
    val geminiDesktopMode by geminiSession.isDesktopModeState.collectAsStateWithLifecycle()
    val browserTabs by PulseBrowserManager.tabs.collectAsStateWithLifecycle()
    val browserActiveTabId by PulseBrowserManager.activeTabId.collectAsStateWithLifecycle()
    val isBrowserBarVisible by PulseBrowserManager.isAddressBarVisible.collectAsStateWithLifecycle()
    val activeBrowserTab = browserTabs.firstOrNull { it.id == browserActiveTabId } ?: browserTabs.first()
    val pinnedRamCount by WorkspaceMemoryKeepAliveCoordinator.pinnedCount.collectAsStateWithLifecycle()
    val workspaceRamStatuses by WorkspaceMemoryKeepAliveCoordinator.workspaceStatuses.collectAsStateWithLifecycle()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(PulseTab.WEB_BRIDGE.ordinal) }
    // Window-Attached Lazy Workspace Stack: every visited web workspace stays mounted in the
    // Compose hierarchy and permanently attached to the Android window so switching tabs never
    // detaches, throttles, or reloads even after 1+ hour in the background.
    var mountedWebTabs by remember { mutableStateOf(setOf(PulseTab.WEB_BRIDGE)) }
    var showAppearanceSheet by rememberSaveable { mutableStateOf(false) }
    var showFloatingQuickMenu by rememberSaveable { mutableStateOf(false) }
    var showServerModal by rememberSaveable { mutableStateOf(false) }
    var showGlobalQrScanner by rememberSaveable { mutableStateOf(false) }
    var showT3EnvironmentsDialog by rememberSaveable { mutableStateOf(false) }
    var showT3QrScanner by rememberSaveable { mutableStateOf(false) }

    val activeProfile = serverProfiles.firstOrNull { it.isSelected } ?: serverProfiles.firstOrNull()

    // Native Android System File Picker hook for WebView <input type="file"> (e.g., Antigravity Remote "+" attach button)
    var pendingFileCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val webFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = pendingFileCallback
        pendingFileCallback = null
        if (callback == null) return@rememberLauncherForActivityResult

        val selectedUris = mutableListOf<Uri>()
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data
            val clipData = data?.clipData
            if (clipData != null && clipData.itemCount > 0) {
                for (i in 0 until clipData.itemCount) {
                    clipData.getItemAt(i)?.uri?.let { selectedUris.add(it) }
                }
            } else {
                data?.data?.let { selectedUris.add(it) }
            }
        }
        callback.onReceiveValue(
            if (selectedUris.isNotEmpty()) selectedUris.toTypedArray() else null
        )
    }

    DisposableEffect(Unit) {
        RetainedWebViewManager.onShowFileChooserHandler = { filePathCallback, fileChooserParams ->
            // Always release any stale callback so WebView never deadlocks on subsequent "+" clicks
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = filePathCallback

            try {
                val openDocIntent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                    addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    )

                    val rawAccepts = fileChooserParams?.acceptTypes
                        ?.flatMap { it.split(",") }
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() }
                        .orEmpty()

                    if (rawAccepts.isNotEmpty() && rawAccepts.none { it == "*/*" }) {
                        val resolvedMimes = rawAccepts.mapNotNull { token ->
                            when {
                                token.contains("/") -> token
                                token.startsWith(".") -> MimeTypeMap.getSingleton()
                                    .getMimeTypeFromExtension(token.removePrefix(".").lowercase())
                                else -> null
                            }
                        }.distinct()

                        // Only restrict picker types when every requested type resolved to a real MIME type
                        if (resolvedMimes.isNotEmpty() && resolvedMimes.size == rawAccepts.size) {
                            putExtra(Intent.EXTRA_MIME_TYPES, resolvedMimes.toTypedArray())
                        }
                    }
                }
                val chooserTitle = fileChooserParams?.title?.takeIf { it.isNotBlank() }
                    ?: "Select files from device"
                webFilePickerLauncher.launch(Intent.createChooser(openDocIntent, chooserTitle))
                true
            } catch (_: Exception) {
                // Fallback for OEM ROMs where ACTION_OPEN_DOCUMENT is unavailable
                try {
                    val getContentIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                        putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    webFilePickerLauncher.launch(Intent.createChooser(getContentIntent, "Select files from device"))
                    true
                } catch (_: Exception) {
                    pendingFileCallback?.onReceiveValue(null)
                    pendingFileCallback = null
                    false
                }
            }
        }

        onDispose {
            pendingFileCallback?.onReceiveValue(null)
            pendingFileCallback = null
            RetainedWebViewManager.onShowFileChooserHandler = null
        }
    }

    // Native Android Runtime Permission Bridge for WebView Microphone (Voice Input) & Camera
    var permissionsRefreshTick by remember { mutableIntStateOf(0) }
    var pendingWebPermissionRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    var pendingOriginMediaConfirmRequest by remember { mutableStateOf<PermissionRequest?>(null) }
    val latestActiveEndpoint by rememberUpdatedState(telemetry.activeEndpoint)

    val runtimePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        permissionsRefreshTick += 1
        val pending = pendingWebPermissionRequest
        pendingWebPermissionRequest = null
        if (pending != null) {
            val (allowedResources, _) = resolveAllowedWebResourcesAndPermissions(pending.resources)
            val approvedResources = allowedResources.filter { res ->
                when (res) {
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE ->
                        grantResults[Manifest.permission.RECORD_AUDIO] == true ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE ->
                        grantResults[Manifest.permission.CAMERA] == true ||
                            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
                    else -> false
                }
            }
            if (approvedResources.isNotEmpty()) {
                try {
                    pending.grant(approvedResources.toTypedArray())
                } catch (_: Exception) {
                }
            } else {
                try {
                    pending.deny()
                } catch (_: Exception) {
                }
                antigravityViewModel.recordWebViewBridgeEvent(
                    "Microphone/Camera permission denied. Enable in Settings -> Device Permissions."
                )
            }
        }
    }

    fun proceedWithApprovedWebMediaRequest(request: PermissionRequest) {
        val (allowedResources, neededPerms) = resolveAllowedWebResourcesAndPermissions(request.resources)
        if (allowedResources.isEmpty()) {
            try {
                request.deny()
            } catch (_: Exception) {
            }
            return
        }
        val missingPerms = neededPerms.filter { perm ->
            ContextCompat.checkSelfPermission(context, perm) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPerms.isEmpty()) {
            try {
                request.grant(allowedResources.toTypedArray())
            } catch (_: Exception) {
            }
        } else {
            pendingWebPermissionRequest?.let { stale ->
                try {
                    stale.deny()
                } catch (_: Exception) {
                }
            }
            pendingWebPermissionRequest = request
            runtimePermissionsLauncher.launch(missingPerms.toTypedArray())
        }
    }

    // Explicit per-origin confirmation dialog for third-party websites requesting Microphone/Camera
    // so random sites in Pulse Browser cannot silently hijack hardware once Android permissions are granted.
    pendingOriginMediaConfirmRequest?.let { pendingReq ->
        val originStr = pendingReq.origin?.toString().orEmpty()
        val displayHost = try {
            Uri.parse(originStr).host?.takeIf { it.isNotBlank() } ?: originStr
        } catch (_: Exception) {
            originStr
        }
        val (allowedRes, _) = resolveAllowedWebResourcesAndPermissions(pendingReq.resources)
        val asksAudio = allowedRes.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        val asksVideo = allowedRes.contains(PermissionRequest.RESOURCE_VIDEO_CAPTURE)
        val hardwareLabel = when {
            asksAudio && asksVideo -> "Camera and Microphone"
            asksVideo -> "Camera"
            else -> "Microphone"
        }
        AlertDialog(
            onDismissRequest = {
                try {
                    pendingReq.deny()
                } catch (_: Exception) {
                }
                pendingOriginMediaConfirmRequest = null
            },
            title = {
                Text(
                    text = "Allow $hardwareLabel Access?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "The website \"$displayHost\" wants to use your device $hardwareLabel. Only allow sites you trust."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingOriginMediaConfirmRequest = null
                        proceedWithApprovedWebMediaRequest(pendingReq)
                    },
                    modifier = Modifier.testTag("web_media_origin_allow_button")
                ) {
                    Text("Allow Once")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        try {
                            pendingReq.deny()
                        } catch (_: Exception) {
                        }
                        pendingOriginMediaConfirmRequest = null
                    },
                    modifier = Modifier.testTag("web_media_origin_deny_button")
                ) {
                    Text("Deny")
                }
            }
        )
    }

    DisposableEffect(Unit) {
        RetainedWebViewManager.onWebPermissionRequestHandler = { request ->
            val (allowedResources, _) = resolveAllowedWebResourcesAndPermissions(request.resources)
            val originUrl = request.origin?.toString().orEmpty()
            if (allowedResources.isEmpty() || !RetainedWebViewManager.isSecureMediaOrigin(originUrl)) {
                try {
                    request.deny()
                } catch (_: Exception) {
                }
            } else if (RetainedWebViewManager.isFirstPartyTrustedMediaOrigin(originUrl, latestActiveEndpoint)) {
                proceedWithApprovedWebMediaRequest(request)
            } else {
                // Untrusted/third-party origin: require explicit user confirmation before granting
                pendingOriginMediaConfirmRequest?.let { stale ->
                    try {
                        stale.deny()
                    } catch (_: Exception) {
                    }
                }
                pendingOriginMediaConfirmRequest = request
            }
        }

        RetainedWebViewManager.onWebPermissionRequestCanceled = { canceledRequest ->
            if (pendingWebPermissionRequest === canceledRequest) {
                pendingWebPermissionRequest = null
            }
            if (pendingOriginMediaConfirmRequest === canceledRequest) {
                pendingOriginMediaConfirmRequest = null
            }
        }

        RetainedWebViewManager.onWebDownloadRequested = { url, userAgent, contentDisposition, mimeType ->
            if (RetainedWebViewManager.isSafeHttpUrl(url)) {
                val safeFileName = sanitizeDownloadFileName(
                    URLUtil.guessFileName(url, contentDisposition, mimeType)
                )
                val safeUserAgent = userAgent.replace(Regex("[\\r\\n]"), "").trim().take(512)
                val safeMimeType = mimeType.replace(Regex("[\\r\\n;]"), "").trim().take(128)
                val allowCookieAttach = RetainedWebViewManager.isTrustedCookieDownloadUrl(
                    downloadUrl = url,
                    activeRemoteEndpoint = latestActiveEndpoint
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                        val dmReq = DownloadManager.Request(Uri.parse(url)).apply {
                            if (safeMimeType.isNotBlank()) setMimeType(safeMimeType)
                            // Strictly scope session cookies to verified HTTPS first-party workspaces
                            // or the user's own configured remote host to prevent cookie exfiltration.
                            if (allowCookieAttach) {
                                val cookies = try {
                                    CookieManager.getInstance().getCookie(url)
                                } catch (_: Exception) {
                                    null
                                }
                                val safeCookies = cookies?.replace(Regex("[\\r\\n]"), "")?.trim()
                                if (!safeCookies.isNullOrBlank()) {
                                    addRequestHeader("Cookie", safeCookies)
                                }
                            }
                            if (safeUserAgent.isNotBlank()) {
                                addRequestHeader("User-Agent", safeUserAgent)
                            }
                            setTitle(safeFileName)
                            setDescription("Downloading via Antigravity Pulse")
                            setNotificationVisibility(
                                DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                            )
                            setDestinationInExternalPublicDir(
                                Environment.DIRECTORY_DOWNLOADS,
                                safeFileName
                            )
                        }
                        dm?.enqueue(dmReq)
                        antigravityViewModel.recordWebViewBridgeEvent("Downloading $safeFileName to Downloads...")
                    } catch (_: Exception) {
                        try {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    .addCategory(Intent.CATEGORY_BROWSABLE)
                            )
                        } catch (_: Exception) {
                            antigravityViewModel.recordWebViewBridgeEvent("Unable to start download.")
                        }
                    }
                } else {
                    try {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addCategory(Intent.CATEGORY_BROWSABLE)
                        )
                    } catch (_: Exception) {
                        antigravityViewModel.recordWebViewBridgeEvent("No browser available to download file.")
                    }
                }
            }
        }

        onDispose {
            pendingWebPermissionRequest?.let { stale ->
                try {
                    stale.deny()
                } catch (_: Exception) {
                }
            }
            pendingWebPermissionRequest = null
            pendingOriginMediaConfirmRequest?.let { stale ->
                try {
                    stale.deny()
                } catch (_: Exception) {
                }
            }
            pendingOriginMediaConfirmRequest = null
            RetainedWebViewManager.onWebPermissionRequestHandler = null
            RetainedWebViewManager.onWebPermissionRequestCanceled = null
            RetainedWebViewManager.onWebDownloadRequested = null
        }
    }

    // Android 13+ (API 33+) runtime notification permission launcher
    var pendingSendTestNotification by remember { mutableStateOf(false) }
    var hasPromptedNotificationPermission by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        permissionsRefreshTick += 1
        if (granted && pendingSendTestNotification) {
            pendingSendTestNotification = false
            PulseNotificationManager.showTestNotification(context)
            antigravityViewModel.recordWebViewBridgeEvent("Test notification delivered to your phone!")
        } else if (!granted && pendingSendTestNotification) {
            pendingSendTestNotification = false
            antigravityViewModel.recordWebViewBridgeEvent("Enable Notifications in Android App Settings to receive agent alerts.")
        }
    }

    fun requestNotificationPermissionIfNeeded(sendTestAfterGrant: Boolean = false) {
        if (PulseNotificationManager.hasNotificationPermission(context)) {
            if (sendTestAfterGrant) {
                PulseNotificationManager.showTestNotification(context)
                antigravityViewModel.recordWebViewBridgeEvent("Test notification delivered to your phone!")
            }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingSendTestNotification = sendTestAfterGrant
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Request Android 13+ notification permission once when notifications are enabled
    LaunchedEffect(Unit) {
        if (!hasPromptedNotificationPermission &&
            (appearance.isApprovalNotificationsEnabled || appearance.isAppEventNotificationsEnabled) &&
            !PulseNotificationManager.hasNotificationPermission(context) &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        ) {
            hasPromptedNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Bridge remote PWA Web Notifications & live DOM agent activity alerts
    // straight into native Android heads-up notifications
    DisposableEffect(
        appearance.isApprovalNotificationsEnabled,
        appearance.isNotifyOnlyInBackground
    ) {
        RetainedWebViewManager.onWebNotificationReceived = { title, body, tag ->
            if (appearance.isApprovalNotificationsEnabled) {
                PulseNotificationManager.showAgentNotification(
                    context = context,
                    title = title,
                    body = body,
                    tag = tag,
                    onlyWhenBackground = appearance.isNotifyOnlyInBackground
                )
            }
        }
        onDispose {
            RetainedWebViewManager.onWebNotificationReceived = null
        }
    }

    // Keep auto-reconnect policy synced and maintain offline cached views across network drops
    LaunchedEffect(appearance.isAutoReconnectEnabled) {
        antigravityViewModel.setAutoReconnectEnabled(appearance.isAutoReconnectEnabled)
    }

    DisposableEffect(appearance.isAutoReconnectEnabled) {
        val connectivityManager = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val offlineStartedAtMs = AtomicLong(0L)
        val autoReloadOnLongOutage = appearance.isAutoReconnectEnabled

        // Initialize cache mode accurately if launched while already offline
        if (!WebViewOfflineCacheHelper.isNetworkCurrentlyAvailable(context)) {
            offlineStartedAtMs.set(SystemClock.elapsedRealtime())
            RetainedWebViewManager.onNetworkLost()
            AiStudioPinnedWebViewManager.onNetworkLost()
            T3CodePinnedWebViewManager.onNetworkLost()
            DevWebWorkspaceRegistry.onNetworkLostAll()
            PulseBrowserManager.onNetworkLostAll()
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                // Only enter offline cached mode if no other active internet network remains
                if (!WebViewOfflineCacheHelper.isNetworkCurrentlyAvailable(context)) {
                    offlineStartedAtMs.compareAndSet(0L, SystemClock.elapsedRealtime())
                    RetainedWebViewManager.onNetworkLost()
                    AiStudioPinnedWebViewManager.onNetworkLost()
                    T3CodePinnedWebViewManager.onNetworkLost()
                    DevWebWorkspaceRegistry.onNetworkLostAll()
                    PulseBrowserManager.onNetworkLostAll()
                }
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                val hasValidatedInternet =
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                if (hasValidatedInternet) {
                    val droppedAt = offlineStartedAtMs.getAndSet(0L)
                    if (droppedAt > 0L) {
                        val outageMs = SystemClock.elapsedRealtime() - droppedAt
                        val shouldAutoReload = autoReloadOnLongOutage && outageMs >= 15_000L
                        RetainedWebViewManager.onNetworkRestored(
                            autoReloadAfterLongOutage = shouldAutoReload
                        )
                        AiStudioPinnedWebViewManager.onNetworkRestored(
                            autoReloadAfterLongOutage = shouldAutoReload
                        )
                        T3CodePinnedWebViewManager.onNetworkRestored(
                            autoReloadAfterLongOutage = shouldAutoReload
                        )
                        DevWebWorkspaceRegistry.onNetworkRestoredAll(
                            autoReloadAfterLongOutage = shouldAutoReload
                        )
                        PulseBrowserManager.onNetworkRestoredAll(
                            autoReloadAfterLongOutage = shouldAutoReload
                        )
                        antigravityViewModel.recordWebViewBridgeEvent(
                            if (shouldAutoReload) {
                                "Back online — updating cached session to latest view"
                            } else {
                                "Back online — session resumed seamlessly"
                            }
                        )
                    }
                }
            }
        }
        try {
            connectivityManager?.registerDefaultNetworkCallback(callback)
        } catch (_: Exception) {
        }
        onDispose {
            try {
                connectivityManager?.unregisterNetworkCallback(callback)
            } catch (_: Exception) {
            }
        }
    }

    // Centralized multi-user isolation wipe: purges local Room hosts, saved URL, all WebView
    // RAM/DOM sessions, cookies, WebStorage, HTTP auth credentials, and lingering notifications.
    fun performFullLocalSessionWipe(clearSavedUid: Boolean) {
        WorkspaceKeepAliveService.stop(context)
        mountedWebTabs = setOf(PulseTab.WEB_BRIDGE)
        onUpdateAppearance {
            it.copy(
                lastActiveRemoteUrl = "",
                lastSignedInUid = if (clearSavedUid) "" else it.lastSignedInUid
            )
        }
        RetainedWebViewManager.clearSensitiveBrowsingData(context)
        AiStudioPinnedWebViewManager.clearSensitiveBrowsingData(context)
        T3CodePinnedWebViewManager.clearSensitiveBrowsingData(context)
        DevWebWorkspaceRegistry.clearAllSensitiveBrowsingData(context)
        PulseBrowserManager.clearAllSensitiveBrowsingData(context)
        antigravityViewModel.clearAllLocalWorkspaceData()
        try {
            NotificationManagerCompat.from(context.applicationContext).cancelAll()
        } catch (_: Exception) {
        }
    }

    // Global cross-user isolation guard: detects live sign-outs/revocations AND cold-start
    // account switches (User A -> User B) via persisted lastSignedInUid, wiping all local data
    // before the new user can access any previous session state.
    val currentAuthUser by Firebase.auth.authStateFlow()
        .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)
    var lastObservedAuthUid by remember { mutableStateOf(Firebase.auth.currentUser?.uid) }

    LaunchedEffect(currentAuthUser?.uid) {
        val newUid = currentAuthUser?.uid
        val previousMemoryUid = lastObservedAuthUid
        val persistedUid = appearance.lastSignedInUid

        when {
            newUid != null && persistedUid.isNotBlank() && persistedUid != newUid -> {
                // Account switch detected (same session or after cold restart)
                performFullLocalSessionWipe(clearSavedUid = false)
                onUpdateAppearance { it.copy(lastSignedInUid = newUid) }
            }
            newUid != null && persistedUid.isBlank() -> {
                // First sign-in on a clean device: bind local workspace to this UID
                onUpdateAppearance { it.copy(lastSignedInUid = newUid) }
            }
            previousMemoryUid != null && newUid == null -> {
                // Live sign-out or revoked auth session: purge local workspace & Firestore cache
                performFullLocalSessionWipe(clearSavedUid = true)
                clearFirestoreLocalCache(context)
            }
        }
        lastObservedAuthUid = newUid
    }

    // Sync active endpoint URL to synchronous SharedPreferences for instant restore after unlock/restart
    LaunchedEffect(telemetry.activeEndpoint) {
        val clean = telemetry.activeEndpoint.trim()
        if (RetainedWebViewManager.isSafeHttpUrl(clean) && appearance.lastActiveRemoteUrl != clean) {
            onUpdateAppearance { it.copy(lastActiveRemoteUrl = clean) }
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
    val smoothAnimations = appearance.isSmoothAnimationsEnabled

    // Smart Back navigation: close floating panel -> pop active workspace/browser WebView history -> return to Remote -> pop Remote
    val currentPulseTab = PulseTab.entries[selectedTabIndex.coerceIn(0, PulseTab.entries.lastIndex)]
    val effectiveMountedWebTabs = remember(mountedWebTabs, currentPulseTab) {
        if (currentPulseTab != PulseTab.STORAGE_SYNC) mountedWebTabs + currentPulseTab else mountedWebTabs
    }
    LaunchedEffect(currentPulseTab) {
        if (currentPulseTab != PulseTab.STORAGE_SYNC && currentPulseTab !in mountedWebTabs) {
            mountedWebTabs = mountedWebTabs + currentPulseTab
        }
    }

    // Evict released non-active workspaces from mountedWebTabs when user explicitly frees RAM in Settings
    val latestCurrentTab by rememberUpdatedState(currentPulseTab)
    LaunchedEffect(Unit) {
        WorkspaceMemoryKeepAliveCoordinator.releasedWorkspaceEvents.collect { releasedId ->
            val targetTab = PulseTab.entries.firstOrNull { it.workspaceCoordinatorId() == releasedId }
            if (targetTab != null && targetTab != latestCurrentTab) {
                mountedWebTabs = mountedWebTabs - targetTab
            }
        }
    }

    // Keep the Play-compliant dataSync Foreground Service synced with user preference & pinned workspaces
    LaunchedEffect(appearance.isBackgroundKeepAliveEnabled, pinnedRamCount) {
        if (appearance.isBackgroundKeepAliveEnabled && pinnedRamCount > 0) {
            if (!WorkspaceKeepAliveService.userPausedKeepAlive) {
                WorkspaceKeepAliveService.startOrUpdate(context)
            }
        } else {
            WorkspaceKeepAliveService.stop(context)
        }
    }
    val isT3CodeBackActive = currentPulseTab == PulseTab.T3_CODE && (t3CanGoBack || t3PopupWebView != null)
    val isAiStudioWebBackActive = currentPulseTab == PulseTab.AI_STUDIO && aiStudioCanGoBack
    val isBrowserBackActive = currentPulseTab == PulseTab.BROWSER && activeBrowserTab.canGoBack
    val activeDevBackSession = when (currentPulseTab) {
        PulseTab.GITHUB -> if (githubCanGoBack) githubSession else null
        PulseTab.VERCEL -> if (vercelCanGoBack) vercelSession else null
        PulseTab.SUPABASE -> if (supabaseCanGoBack) supabaseSession else null
        PulseTab.GEMINI -> if (geminiCanGoBack) geminiSession else null
        else -> null
    }
    BackHandler(
        enabled = !showAppearanceSheet &&
            (showFloatingQuickMenu ||
                selectedTabIndex != PulseTab.WEB_BRIDGE.ordinal ||
                remoteCanGoBack ||
                isT3CodeBackActive ||
                isAiStudioWebBackActive ||
                isBrowserBackActive ||
                activeDevBackSession != null)
    ) {
        when {
            showFloatingQuickMenu -> showFloatingQuickMenu = false
            isT3CodeBackActive -> T3CodePinnedWebViewManager.goBack()
            isAiStudioWebBackActive -> AiStudioPinnedWebViewManager.goBack()
            isBrowserBackActive -> PulseBrowserManager.goBackActiveTab()
            activeDevBackSession != null -> activeDevBackSession.goBack()
            selectedTabIndex != PulseTab.WEB_BRIDGE.ordinal -> selectedTabIndex = PulseTab.WEB_BRIDGE.ordinal
            else -> RetainedWebViewManager.goBack()
        }
    }

    fun connectAndOpenRemoteHost(hostName: String, url: String, token: String) {
        val cleanUrl = url.trim()
        if (cleanUrl.isBlank()) return
        val cleanName = hostName.trim().ifBlank { "Antigravity Remote" }
        if (RetainedWebViewManager.isSafeHttpUrl(cleanUrl)) {
            onUpdateAppearance { it.copy(lastActiveRemoteUrl = cleanUrl) }
        }
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
        if (appearance.isAppEventNotificationsEnabled) {
            val safeNotificationUrl = RetainedWebViewManager
                .stripSensitiveUrlCredentials(cleanUrl)
                .ifBlank { cleanName }
            PulseNotificationManager.showConnectionNotification(
                context = context,
                title = "Connected to $cleanName",
                body = safeNotificationUrl
            )
        }
    }

    val panelTabs = PulseTab.entries.mapIndexed { idx, tab ->
        FloatingPanelTabItem(
            index = idx,
            title = stringResource(id = tab.titleRes),
            icon = tab.icon,
            testTag = tab.tag
        )
    }

    val pinnedWorkspaceIds = remember(workspaceRamStatuses) {
        workspaceRamStatuses.filter { it.isPinnedInRam }.map { it.id }.toSet()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .then(if (showAppearanceSheet) Modifier.clearAndSetSemantics {} else Modifier)
        ) {
        val isWideScreen = maxWidth >= 720.dp

        Scaffold(
            modifier = Modifier
                .fillMaxSize()
                .background(bg),
            contentWindowInsets = WindowInsets.safeDrawing,
            topBar = {
                val barAnimMs = if (smoothAnimations) 160 else 0
                AnimatedVisibility(
                    visible = !isBarsHidden,
                    enter = slideInVertically(animationSpec = tween(barAnimMs), initialOffsetY = { -it }) + fadeIn(tween(barAnimMs)),
                    exit = slideOutVertically(animationSpec = tween(barAnimMs), targetOffsetY = { -it }) + fadeOut(tween(barAnimMs))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(surface)
                            .windowInsetsPadding(
                                WindowInsets.statusBars
                                    .union(WindowInsets.displayCutout)
                                    .only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
                            )
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
                                if (RetainedWebViewManager.isSafeHttpUrl(profile.endpointUrl)) {
                                    onUpdateAppearance { it.copy(lastActiveRemoteUrl = profile.endpointUrl.trim()) }
                                }
                                antigravityViewModel.selectServerProfile(profile)
                                RetainedWebViewManager.reloadOrForceLoadUrl(
                                    profile.endpointUrl,
                                    telemetry.domLagBoosterEnabled
                                )
                                selectedTabIndex = PulseTab.WEB_BRIDGE.ordinal
                            },
                            onOpenServerModal = { showServerModal = true },
                            onOpenQrScanner = { showGlobalQrScanner = true },
                            onOpenAppearance = { showAppearanceSheet = true },
                            onHideBarsFullscreen = {
                                onUpdateAppearance { it.copy(isFullscreenBarsHidden = true) }
                            }
                        )
                        HorizontalDivider(color = border, thickness = 1.dp)
                    }
                }
            },
            bottomBar = {
                val barAnimMs = if (smoothAnimations) 160 else 0
                AnimatedVisibility(
                    visible = !isBarsHidden && !isWideScreen,
                    enter = slideInVertically(animationSpec = tween(barAnimMs), initialOffsetY = { it }) + fadeIn(tween(barAnimMs)),
                    exit = slideOutVertically(animationSpec = tween(barAnimMs), targetOffsetY = { it }) + fadeOut(tween(barAnimMs))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(surface)
                            .windowInsetsPadding(
                                WindowInsets.navigationBars
                                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
                            )
                    ) {
                        HorizontalDivider(color = border, thickness = 1.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PulseTab.entries.forEachIndexed { index, tab ->
                                val selected = selectedTabIndex == index
                                val labelText = stringResource(id = tab.shortTitleRes)
                                val isLiveInRam = tab.workspaceCoordinatorId()?.let { it in pinnedWorkspaceIds } == true
                                val pillBg by animateColorAsState(
                                    targetValue = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
                                    animationSpec = tween(if (smoothAnimations) 160 else 0),
                                    label = "nav_pill_bg_${tab.name}"
                                )
                                val itemTint by animateColorAsState(
                                    targetValue = if (selected) accent else secondaryFg,
                                    animationSpec = tween(if (smoothAnimations) 150 else 0),
                                    label = "nav_item_tint_${tab.name}"
                                )
                                val iconScale by animateFloatAsState(
                                    targetValue = if (selected) 1.08f else 1.0f,
                                    animationSpec = tween(if (smoothAnimations) 150 else 0),
                                    label = "nav_icon_scale_${tab.name}"
                                )
                                Column(
                                    modifier = Modifier
                                        .widthIn(min = 58.dp)
                                        .heightIn(min = 52.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .clickable { selectedTabIndex = index }
                                        .padding(horizontal = 6.dp, vertical = 4.dp)
                                        .testTag(tab.tag),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(pillBg)
                                            .padding(horizontal = 12.dp, vertical = 3.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = stringResource(id = tab.titleRes),
                                            tint = itemTint,
                                            modifier = Modifier
                                                .size(20.dp)
                                                .graphicsLayer {
                                                    scaleX = iconScale
                                                    scaleY = iconScale
                                                }
                                        )
                                        if (isLiveInRam && !selected) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .size(5.dp)
                                                    .clip(CircleShape)
                                                    .background(activeTheme.successColor)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = labelText,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = itemTint,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
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
                        Column(
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally
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
                                            text = stringResource(id = tab.shortTitleRes),
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
                            Spacer(modifier = Modifier.height(8.dp))
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
                        AnimatedVisibility(
                            visible = statusBanner != null,
                            enter = if (smoothAnimations) {
                                slideInVertically(tween(160), initialOffsetY = { -it / 2 }) + fadeIn(tween(140))
                            } else {
                                fadeIn(tween(0))
                            },
                            exit = if (smoothAnimations) {
                                slideOutVertically(tween(120), targetOffsetY = { -it / 2 }) + fadeOut(tween(100))
                            } else {
                                fadeOut(tween(0))
                            }
                        ) {
                            val bannerText = statusBanner
                            if (bannerText != null) {
                                val bannerGreen = activeTheme.successColor
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 4.dp)
                                        .frostedGlass(
                                            shape = RoundedCornerShape(14.dp),
                                            baseColor = if (activeTheme.isLightMode) surface else bannerGreen.copy(alpha = 0.18f),
                                            tintColor = bannerGreen,
                                            borderColor = bannerGreen,
                                            enabled = activeTheme.isFrostedGlassEnabled,
                                            fillAlpha = if (activeTheme.isLightMode) 0.94f else 0.26f,
                                            tintAlpha = 0.10f,
                                            borderAlpha = if (activeTheme.isLightMode) 0.65f else 0.45f
                                        )
                                        .clickable { antigravityViewModel.clearBanner() }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                        .testTag("status_feedback_banner"),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = bannerText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = bannerGreen,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = "Dismiss",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = fg
                                    )
                                }
                            }
                        }

                        // Window-Attached Lazy Workspace Stack: every opened WebView workspace remains
                        // attached to the window permanently (zero detach, zero reload, 0ms switching).
                        Box(modifier = Modifier.weight(1f)) {
                            effectiveMountedWebTabs.forEach { tab ->
                                val isSlotActive = !showAppearanceSheet && currentPulseTab == tab
                                key(tab) {
                                    KeepAliveWorkspaceSlot(
                                        isActive = isSlotActive,
                                        smoothAnimationsEnabled = smoothAnimations
                                    ) {
                                        when (tab) {
                                            PulseTab.WEB_BRIDGE -> RetainedWebViewBridgeScreen(
                                                telemetry = telemetry,
                                                savedFallbackEndpoint = appearance.lastActiveRemoteUrl,
                                                isFullscreenBarsHidden = isBarsHidden,
                                                isWorkspaceActive = isSlotActive,
                                                onToggleDomBooster = antigravityViewModel::toggleDomLagBooster,
                                                onSaveAndConnectUrl = { cleanUrl ->
                                                    connectAndOpenRemoteHost(
                                                        hostName = activeProfile?.name ?: "Antigravity Remote",
                                                        url = cleanUrl,
                                                        token = activeProfile?.authToken.orEmpty()
                                                    )
                                                },
                                                onOpenHostSwitcher = { showServerModal = true },
                                                onOpenQrScanner = { showGlobalQrScanner = true },
                                                onOpenAiStudio = { selectedTabIndex = PulseTab.AI_STUDIO.ordinal },
                                                onOpenT3Code = { selectedTabIndex = PulseTab.T3_CODE.ordinal },
                                                onJsBridgeEvent = { eventMsg ->
                                                    antigravityViewModel.recordWebViewBridgeEvent(eventMsg)
                                                    if (appearance.isAppEventNotificationsEnabled &&
                                                        eventMsg.contains("SSL", ignoreCase = true)
                                                    ) {
                                                        PulseNotificationManager.showConnectionNotification(
                                                            context = context,
                                                            title = "Antigravity Security Warning",
                                                            body = eventMsg
                                                        )
                                                    }
                                                }
                                            )

                                            PulseTab.T3_CODE -> T3CodeWebScreen(
                                                domLagBoosterEnabled = telemetry.domLagBoosterEnabled,
                                                isFullscreenBarsHidden = isBarsHidden,
                                                isWorkspaceActive = isSlotActive,
                                                showEnvironmentsDialog = showT3EnvironmentsDialog,
                                                onShowEnvironmentsDialogChange = { showT3EnvironmentsDialog = it },
                                                onOpenT3QrScanner = { showT3QrScanner = true },
                                                onToggleDomBooster = antigravityViewModel::toggleDomLagBooster,
                                                onStatusEvent = { eventMsg ->
                                                    antigravityViewModel.recordWebViewBridgeEvent(eventMsg)
                                                }
                                            )

                                            PulseTab.AI_STUDIO -> AiStudioWebScreen(
                                                domLagBoosterEnabled = telemetry.domLagBoosterEnabled,
                                                isFullscreenBarsHidden = isBarsHidden,
                                                isWorkspaceActive = isSlotActive,
                                                onToggleDomBooster = antigravityViewModel::toggleDomLagBooster,
                                                onStatusEvent = { eventMsg ->
                                                    antigravityViewModel.recordWebViewBridgeEvent(eventMsg)
                                                }
                                            )

                                            PulseTab.GEMINI,
                                            PulseTab.GITHUB,
                                            PulseTab.VERCEL,
                                            PulseTab.SUPABASE -> {
                                                val workspace = tab.devWorkspace!!
                                                PinnedWebWorkspaceScreen(
                                                    workspace = workspace,
                                                    domLagBoosterEnabled = telemetry.domLagBoosterEnabled,
                                                    isFullscreenBarsHidden = isBarsHidden,
                                                    isWorkspaceActive = isSlotActive,
                                                    onToggleDomBooster = antigravityViewModel::toggleDomLagBooster,
                                                    onStatusEvent = { eventMsg ->
                                                        antigravityViewModel.recordWebViewBridgeEvent(eventMsg)
                                                    }
                                                )
                                            }

                                            PulseTab.BROWSER -> PulseBrowserScreen(
                                                domLagBoosterEnabled = telemetry.domLagBoosterEnabled,
                                                isWorkspaceActive = isSlotActive,
                                                onStatusEvent = { eventMsg ->
                                                    antigravityViewModel.recordWebViewBridgeEvent(eventMsg)
                                                }
                                            )

                                            PulseTab.STORAGE_SYNC -> Unit
                                        }
                                    }
                                }
                            }

                            if (currentPulseTab == PulseTab.STORAGE_SYNC) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .zIndex(2f)
                                        .background(bg)
                                ) {
                                    StorageAndCloudSyncScreen(
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
                                        onClearLocalData = {
                                            performFullLocalSessionWipe(clearSavedUid = false)
                                        },
                                        onBeforeSignOut = {
                                            performFullLocalSessionWipe(clearSavedUid = true)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    // Compact Translucent Movable Side Panel (Active by default in Fullscreen Mode)
                    if (isBarsHidden) {
                        val activeTab = PulseTab.entries[selectedTabIndex.coerceIn(0, PulseTab.entries.lastIndex)]
                        val isOnT3Code = activeTab == PulseTab.T3_CODE
                        val isOnAiStudio = activeTab == PulseTab.AI_STUDIO
                        val isOnBrowser = activeTab == PulseTab.BROWSER
                        val activeDevWorkspace = activeTab.devWorkspace
                        val activeDevSession = activeDevWorkspace?.let { DevWebWorkspaceRegistry.session(it) }
                        val activeT3EnvironmentLabel = t3Environments
                            .firstOrNull { it.id == t3ActiveEnvId }?.label
                            ?: T3CodePinnedWebViewManager.CLOUD_ENVIRONMENT.label
                        val activeDevShortcutId = when (activeDevWorkspace) {
                            DevWebWorkspace.GITHUB -> githubActiveShortcut
                            DevWebWorkspace.VERCEL -> vercelActiveShortcut
                            DevWebWorkspace.SUPABASE -> supabaseActiveShortcut
                            DevWebWorkspace.GEMINI -> geminiActiveShortcut
                            null -> ""
                        }
                        val activeDesktopMode = when {
                            isOnT3Code -> t3DesktopMode
                            isOnBrowser -> activeBrowserTab.isDesktopMode
                            activeDevWorkspace == DevWebWorkspace.GITHUB -> githubDesktopMode
                            activeDevWorkspace == DevWebWorkspace.VERCEL -> vercelDesktopMode
                            activeDevWorkspace == DevWebWorkspace.SUPABASE -> supabaseDesktopMode
                            activeDevWorkspace == DevWebWorkspace.GEMINI -> geminiDesktopMode
                            else -> false
                        }

                        MovableFloatingControlPanel(
                            isExpanded = showFloatingQuickMenu,
                            xFraction = appearance.floatingHandleXFraction,
                            yFraction = appearance.floatingHandleYFraction,
                            tabs = panelTabs,
                            selectedTabIndex = selectedTabIndex.coerceIn(0, PulseTab.entries.lastIndex),
                            activeHostLabel = activeProfile?.name ?: "Connect Remote URL",
                            isAiStudioActive = isOnAiStudio,
                            activeAiStudioSection = activeAiStudioSection,
                            activeDevWorkspace = activeDevWorkspace,
                            activeDevShortcutId = activeDevShortcutId,
                            isT3CodeActive = isOnT3Code,
                            activeT3EnvironmentLabel = activeT3EnvironmentLabel,
                            isBrowserActive = isOnBrowser,
                            isBrowserBarVisible = isBrowserBarVisible,
                            browserTabCount = browserTabs.size,
                            isDesktopModeEnabled = activeDesktopMode,
                            domLagBoosterEnabled = telemetry.domLagBoosterEnabled,
                            pinnedRamCount = pinnedRamCount,
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
                            onSelectAiStudioSection = { section ->
                                AiStudioPinnedWebViewManager.navigateToSection(section)
                            },
                            onSelectDevShortcut = { shortcut ->
                                activeDevSession?.navigateToShortcut(shortcut)
                            },
                            onOpenT3CloudHome = {
                                T3CodePinnedWebViewManager.navigateToCloudHome(context)
                                antigravityViewModel.recordWebViewBridgeEvent("Opened T3 Code Cloud (app.t3.codes)")
                            },
                            onOpenT3Environments = {
                                showT3EnvironmentsDialog = true
                            },
                            onOpenT3QrScanner = {
                                showT3QrScanner = true
                            },
                            onOpenT3External = {
                                try {
                                    val url = T3CodePinnedWebViewManager.currentUrl()
                                    if (RetainedWebViewManager.isSafeHttpUrl(url)) {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                        )
                                    } else {
                                        antigravityViewModel.recordWebViewBridgeEvent("Blocked unsafe external URL.")
                                    }
                                } catch (_: Exception) {
                                    antigravityViewModel.recordWebViewBridgeEvent("No external browser available.")
                                }
                            },
                            onToggleDesktopMode = { enabled ->
                                if (isOnT3Code) {
                                    T3CodePinnedWebViewManager.setDesktopMode(enabled)
                                    antigravityViewModel.recordWebViewBridgeEvent(
                                        if (enabled) "T3 Code: Desktop View enabled (pinch-to-zoom ON)"
                                        else "T3 Code: Mobile View enabled"
                                    )
                                } else if (isOnBrowser) {
                                    PulseBrowserManager.setActiveTabDesktopMode(enabled)
                                    antigravityViewModel.recordWebViewBridgeEvent(
                                        if (enabled) "Browser: Desktop View enabled"
                                        else "Browser: Mobile View enabled"
                                    )
                                } else {
                                    activeDevSession?.setDesktopMode(enabled)
                                    activeDevWorkspace?.let { ws ->
                                        antigravityViewModel.recordWebViewBridgeEvent(
                                            if (enabled) "${ws.displayName}: Desktop View enabled"
                                            else "${ws.displayName}: Mobile View enabled"
                                        )
                                    }
                                }
                            },
                            onToggleDomBooster = antigravityViewModel::toggleDomLagBooster,
                            onOpenAiStudioExternal = {
                                try {
                                    val url = AiStudioPinnedWebViewManager.currentUrl()
                                    if (RetainedWebViewManager.isSafeHttpUrl(url)) {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                        )
                                    } else {
                                        antigravityViewModel.recordWebViewBridgeEvent("Blocked unsafe external URL.")
                                    }
                                } catch (_: Exception) {
                                    antigravityViewModel.recordWebViewBridgeEvent("No external browser available.")
                                }
                            },
                            onOpenDevWorkspaceExternal = {
                                try {
                                    val url = activeDevSession?.currentUrl().orEmpty()
                                    if (RetainedWebViewManager.isSafeHttpUrl(url)) {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                        )
                                    } else {
                                        antigravityViewModel.recordWebViewBridgeEvent("Blocked unsafe external URL.")
                                    }
                                } catch (_: Exception) {
                                    antigravityViewModel.recordWebViewBridgeEvent("No external browser available.")
                                }
                            },
                            onNewBrowserTab = {
                                PulseBrowserManager.openNewTab()
                                PulseBrowserManager.setAddressBarVisible(true)
                            },
                            onToggleBrowserBar = { PulseBrowserManager.toggleAddressBar() },
                            onOpenBrowserExternal = {
                                try {
                                    val url = activeBrowserTab.url
                                    if (RetainedWebViewManager.isSafeHttpUrl(url)) {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                .addCategory(Intent.CATEGORY_BROWSABLE)
                                        )
                                    } else {
                                        antigravityViewModel.recordWebViewBridgeEvent("Open a webpage first.")
                                    }
                                } catch (_: Exception) {
                                    antigravityViewModel.recordWebViewBridgeEvent("No external browser available.")
                                }
                            },
                            onOpenHostSwitcher = { showServerModal = true },
                            onOpenQrScanner = { showGlobalQrScanner = true },
                            reloadActionLabel = when {
                                isOnT3Code -> "Reload T3 Code"
                                isOnAiStudio -> "Reload AI Studio"
                                isOnBrowser -> "Reload Page"
                                activeDevWorkspace != null -> "Reload ${activeDevWorkspace.displayName}"
                                else -> "Reload Remote"
                            },
                            onReloadWeb = {
                                when {
                                    isOnT3Code -> {
                                        T3CodePinnedWebViewManager.reloadCurrentPage()
                                        antigravityViewModel.recordWebViewBridgeEvent("Reloading T3 Code...")
                                    }

                                    isOnAiStudio -> {
                                        AiStudioPinnedWebViewManager.reloadCurrentPage()
                                        antigravityViewModel.recordWebViewBridgeEvent("Reloading AI Studio...")
                                    }

                                    isOnBrowser -> {
                                        PulseBrowserManager.reloadActiveTab()
                                        antigravityViewModel.recordWebViewBridgeEvent("Reloading browser tab...")
                                    }

                                    activeDevSession != null && activeDevWorkspace != null -> {
                                        activeDevSession.reloadCurrentPage()
                                        antigravityViewModel.recordWebViewBridgeEvent(
                                            "Reloading ${activeDevWorkspace.displayName}..."
                                        )
                                    }

                                    else -> {
                                        RetainedWebViewManager.reloadCurrentPage()
                                        antigravityViewModel.recordWebViewBridgeEvent("Reloading Antigravity Remote...")
                                    }
                                }
                            },
                            onShowBars = {
                                onUpdateAppearance { it.copy(isFullscreenBarsHidden = false) }
                            },
                            onOpenSettings = { showAppearanceSheet = true }
                        )
                    }
                }
            }
        }
        }

        // Settings rendered as an overlay so opening/closing Settings NEVER detaches any live WebView!
        AnimatedVisibility(
            visible = showAppearanceSheet,
            enter = if (smoothAnimations) {
                slideInVertically(tween(190), initialOffsetY = { it / 8 }) + fadeIn(tween(150))
            } else {
                fadeIn(tween(0))
            },
            exit = if (smoothAnimations) {
                slideOutVertically(tween(150), targetOffsetY = { it / 8 }) + fadeOut(tween(120))
            } else {
                fadeOut(tween(0))
            },
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
        ) {
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
                        permissionsRefreshTick = permissionsRefreshTick,
                        onUpdateSettings = onUpdateAppearance,
                        onSelectPreset = onSelectPreset,
                        onResetPreset = onResetPreset,
                        onToggleAutoReconnect = antigravityViewModel::setAutoReconnectEnabled,
                        onToggleBackgroundKeepAlive = { enabled ->
                            if (enabled) {
                                WorkspaceKeepAliveService.startOrUpdate(context)
                                antigravityViewModel.recordWebViewBridgeEvent("1+ Hour RAM Keep-Alive enabled.")
                            } else {
                                WorkspaceKeepAliveService.stop(context)
                                antigravityViewModel.recordWebViewBridgeEvent("Background RAM Keep-Alive paused.")
                            }
                        },
                        onReleaseWorkspaceFromRam = { workspaceId ->
                            WorkspaceMemoryKeepAliveCoordinator.releaseWorkspaceFromRam(workspaceId)
                            antigravityViewModel.recordWebViewBridgeEvent("Released workspace RAM (state saved for instant resume).")
                        },
                        onOpenBatterySettings = {
                            val opened = WorkspaceMemoryKeepAliveCoordinator.openBatteryOptimizationSettings(context)
                            if (opened) {
                                permissionsRefreshTick += 1
                            } else {
                                antigravityViewModel.recordWebViewBridgeEvent("Open Android Settings -> Apps -> Antigravity Pulse -> Battery -> Unrestricted.")
                            }
                        },
                        onRequestNotificationPermission = { requestNotificationPermissionIfNeeded(false) },
                        onSendTestNotification = { requestNotificationPermissionIfNeeded(true) },
                        onRequestRuntimePermissions = { permissionsToRequest ->
                            if (permissionsToRequest.isNotEmpty()) {
                                runtimePermissionsLauncher.launch(permissionsToRequest)
                            }
                        },
                        onOpenAppSystemSettings = {
                            try {
                                val appSettingsIntent = Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", context.packageName, null)
                                )
                                context.startActivity(appSettingsIntent)
                            } catch (_: Exception) {
                            }
                        },
                        onClose = { showAppearanceSheet = false }
                    )
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
                                    if (RetainedWebViewManager.isSafeHttpUrl(profile.endpointUrl)) {
                                        onUpdateAppearance { it.copy(lastActiveRemoteUrl = profile.endpointUrl.trim()) }
                                    }
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
                                color = activeTheme.successColor
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
                        contentColor = activeTheme.onAccentColor
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
                val isT3Domain = RetainedWebViewManager.isHostMatchingDomain(
                    payload.endpointUrl,
                    T3CodePinnedWebViewManager.T3_CLOUD_DOMAIN,
                    requireHttps = true
                )
                if (isT3Domain || currentPulseTab == PulseTab.T3_CODE) {
                    val result = T3CodePinnedWebViewManager.openPairingOrCustomUrl(
                        context = context,
                        label = payload.hostName,
                        rawUrl = payload.endpointUrl,
                        saveBookmark = true
                    )
                    result.fold(
                        onSuccess = { env ->
                            selectedTabIndex = PulseTab.T3_CODE.ordinal
                            antigravityViewModel.recordWebViewBridgeEvent("Paired T3 Code: ${env.label}")
                        },
                        onFailure = { err ->
                            antigravityViewModel.recordWebViewBridgeEvent(err.message ?: "Invalid T3 Code pairing QR.")
                        }
                    )
                } else {
                    connectAndOpenRemoteHost(
                        hostName = payload.hostName,
                        url = payload.endpointUrl,
                        token = payload.authToken
                    )
                }
                showGlobalQrScanner = false
            },
            onDismiss = { showGlobalQrScanner = false }
        )
    }

    // Dedicated T3 Code QR Pairing Scanner (preserves #token=... handshake fragments)
    if (showT3QrScanner) {
        QrPairingScannerDialog(
            dialogTitle = "Scan T3 Code Pairing QR",
            dialogSubtitle = "Connect T3 Code environment or app.t3.codes link",
            defaultPromptMessage = "Align the T3 Code pairing QR code inside the frame",
            onPairingScanned = { payload ->
                val result = T3CodePinnedWebViewManager.openPairingOrCustomUrl(
                    context = context,
                    label = payload.hostName,
                    rawUrl = payload.endpointUrl,
                    saveBookmark = true
                )
                result.fold(
                    onSuccess = { env ->
                        selectedTabIndex = PulseTab.T3_CODE.ordinal
                        antigravityViewModel.recordWebViewBridgeEvent("Paired T3 Code: ${env.label}")
                    },
                    onFailure = { err ->
                        antigravityViewModel.recordWebViewBridgeEvent(err.message ?: "Invalid T3 Code pairing QR.")
                    }
                )
                showT3QrScanner = false
            },
            onDismiss = { showT3QrScanner = false }
        )
    }
}

/**
 * Window-attached workspace slot that keeps an opened WebView permanently mounted in the
 * Android View hierarchy while switching tabs. Inactive slots drop immediately to `zIndex(0f)`
 * with `alpha = 0f` and cleared accessibility semantics; the newly active slot performs a
 * lightweight GPU-only fade-in without ever changing WebView layout bounds or triggering reloads.
 */
@Composable
private fun KeepAliveWorkspaceSlot(
    isActive: Boolean,
    smoothAnimationsEnabled: Boolean,
    content: @Composable () -> Unit
) {
    val slotAlpha by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (smoothAnimationsEnabled && isActive) 130 else 0
        ),
        label = "workspace_slot_alpha"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(if (isActive) 1f else 0f)
            .graphicsLayer { alpha = slotAlpha }
            .then(if (isActive) Modifier else Modifier.clearAndSetSemantics {})
    ) {
        content()
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
    onOpenAppearance: () -> Unit,
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
                    .clickable { onOpenServerModal() }
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
                    style = Stroke(
                        width = 2.4.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round
                    )
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            val barTheme = LocalAppearanceSettings.current
            val isFrosted = barTheme.isFrostedGlassEnabled
            Box {
                Row(
                    modifier = Modifier
                        .frostedGlass(
                            shape = RoundedCornerShape(14.dp),
                            baseColor = elevated,
                            tintColor = accent,
                            borderColor = border,
                            enabled = isFrosted,
                            fillAlpha = 0.72f,
                            tintAlpha = 0.08f,
                            borderAlpha = 0.55f
                        )
                        .clickable { showHostMenu = true }
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                        .testTag("top_session_dropdown_pill"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(9.dp)
                            .clip(CircleShape)
                            .background(if (activeProfile != null) barTheme.successColor else accent)
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
                }
            }
        }

        // Right: QR Scan, Settings, Fullscreen Hide Bars
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            IconButton(
                onClick = onOpenQrScanner,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("top_qr_scan_button")
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scan QR Code",
                    tint = accent,
                    modifier = Modifier.size(21.dp)
                )
            }

            IconButton(
                onClick = onOpenAppearance,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("top_appearance_settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = secondaryFg,
                    modifier = Modifier.size(21.dp)
                )
            }

            IconButton(
                onClick = onHideBarsFullscreen,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("top_hide_bars_fullscreen_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Fullscreen,
                    contentDescription = "Hide Top & Bottom Bars for Fullscreen",
                    tint = accent,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
