package com.example

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Language
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AntigravityDatabase
import com.example.data.local.ServerProfileEntity
import com.example.data.remote.RetainedWebViewManager
import com.example.notifications.PulseNotificationManager
import com.example.ui.screens.parseAntigravityPairingPayload
import com.example.ui.theme.AntigravityPulseTheme
import com.example.ui.theme.AntigravityThemeMode
import com.example.ui.theme.AppearancePreferencesManager
import com.example.ui.theme.AppearanceSettings
import com.example.ui.theme.OfficialAntigravityPresets
import com.example.ui.theme.parseHexColor
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    private lateinit var context: Context
    private lateinit var database: AntigravityDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(
            context,
            AntigravityDatabase::class.java
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun `verify app name and clean Room v4 database with server profiles CRUD`() = runBlocking {
        val appName = context.getString(R.string.app_name)
        assertEquals("Antigravity Pulse", appName)

        val dao = database.dao()
        assertTrue(dao.getAllServerProfilesOnce().isEmpty())

        val firstProfile = ServerProfileEntity(
            id = "srv_workstation",
            name = "Workstation Pro",
            endpointUrl = "https://workstation.tailnet.ts.net:8443",
            connectionMode = "DIRECT_TUNNEL",
            authToken = "",
            keepAlivePinned = true,
            lastLatencyMs = 24,
            isSelected = false,
            updatedAtEpoch = 1_000L
        )
        val secondProfile = ServerProfileEntity(
            id = "srv_lab",
            name = "Home Lab",
            endpointUrl = "https://192.168.1.50:8443",
            connectionMode = "HYBRID_BRIDGE",
            authToken = "tok_lab",
            keepAlivePinned = true,
            lastLatencyMs = 8,
            isSelected = false,
            updatedAtEpoch = 2_000L
        )
        dao.upsertServerProfiles(listOf(firstProfile, secondProfile))

        // Before selection, newest updatedAtEpoch comes first
        val initialOrdered = dao.observeServerProfiles().first()
        assertEquals(2, initialOrdered.size)
        assertEquals("srv_lab", initialOrdered[0].id)

        // Selecting firstProfile moves it to index 0
        dao.selectServerProfile("srv_workstation")
        val afterSelect = dao.observeServerProfiles().first()
        assertEquals("srv_workstation", afterSelect[0].id)
        assertTrue(afterSelect[0].isSelected)
        assertFalse(afterSelect[1].isSelected)

        // Delete single profile and verify clearAll leaves 0 residual records
        dao.deleteServerProfile("srv_lab")
        assertEquals(1, dao.getAllServerProfilesOnce().size)

        dao.clearAllServerProfiles()
        assertTrue(dao.getAllServerProfilesOnce().isEmpty())
    }

    @Test
    fun `security audit hardened URL scheme validator blocks dangerous schemes and malformed URIs`() {
        assertTrue(RetainedWebViewManager.isSafeHttpUrl("https://workstation.example.com:8443"))
        assertTrue(RetainedWebViewManager.isSafeHttpUrl("http://192.168.1.50:8443/remote?tab=1"))
        assertTrue(RetainedWebViewManager.isSafeHttpUrl("https://aistudio.google.com/prompts/new_chat"))

        // Block dangerous schemes
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("file:///etc/passwd"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("javascript:alert(1)"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("content://com.example.provider/secret"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("data:text/html,<script>alert(1)</script>"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("intent://scan/#Intent;end"))

        // Block empty, scheme-only, whitespace/control chars, and oversized URLs
        assertFalse(RetainedWebViewManager.isSafeHttpUrl(""))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("https://"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("http://"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("https://example.com/bad path"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("https://example.com/\nheader"))
        val oversizedUrl = "https://example.com/" + "a".repeat(2050)
        assertFalse(RetainedWebViewManager.isSafeHttpUrl(oversizedUrl))
    }

    @Test
    fun `theme presets hex validation and fullscreen persistence stress test`() {
        // Verify all 12 official dark presets exist and parse to valid Compose Colors
        assertEquals(12, OfficialAntigravityPresets.size)
        val manager = AppearancePreferencesManager(context)

        for (preset in OfficialAntigravityPresets) {
            manager.applyPreset(preset)
            val current = manager.settings.value
            assertEquals(preset.name, current.presetName)
            assertEquals(preset.backgroundHex, current.backgroundHex)
            assertEquals(preset.foregroundHex, current.foregroundHex)
            assertEquals(preset.accentHex, current.accentHex)
            assertNotNull(current.backgroundColor)
            assertNotNull(current.foregroundColor)
            assertNotNull(current.accentColor)
        }

        // Verify malformed hex inputs safely return the fallback color without throwing exceptions
        val fallbackColor = parseHexColor("invalid_hex", Color.Black)
        assertEquals(Color.Black, fallbackColor)

        // Verify Fullscreen Mode toggle persists accurately
        manager.update { it.copy(isFullscreenBarsHidden = true) }
        assertTrue(manager.settings.value.isFullscreenBarsHidden)
        manager.update { it.copy(isFullscreenBarsHidden = false) }
        assertFalse(manager.settings.value.isFullscreenBarsHidden)
    }

    @Test
    fun `QR pairing payload parser handles JSON deep-link HTTPS and blocks unsafe schemes`() {
        val jsonPayload = parseAntigravityPairingPayload(
            """{"name":"Workstation Pro","url":"https://dev.tailnet.ts.net:8443","token":"tok_abc123"}"""
        )
        assertNotNull(jsonPayload)
        assertEquals("Workstation Pro", jsonPayload!!.hostName)
        assertEquals("https://dev.tailnet.ts.net:8443", jsonPayload.endpointUrl)
        assertEquals("tok_abc123", jsonPayload.authToken)

        // wss:// and ws:// are safely normalized to https:// and http:// for WebView loading
        val deepLink = parseAntigravityPairingPayload(
            "antigravity://pair?url=wss%3A%2F%2F192.168.1.42%3A8443&token=secret99&name=LabBox"
        )
        assertNotNull(deepLink)
        assertEquals("LabBox", deepLink!!.hostName)
        assertEquals("https://192.168.1.42:8443", deepLink.endpointUrl)
        assertEquals("secret99", deepLink.authToken)

        val wsJson = parseAntigravityPairingPayload(
            """{"name":"Local Dev","url":"ws://10.0.0.12:3000"}"""
        )
        assertNotNull(wsJson)
        assertEquals("http://10.0.0.12:3000", wsJson!!.endpointUrl)

        val directHttps = parseAntigravityPairingPayload(
            "https://my-host.example.com:8443/remote?token=xyz789"
        )
        assertNotNull(directHttps)
        assertEquals("https://my-host.example.com:8443/remote?token=xyz789", directHttps!!.endpointUrl)
        assertEquals("xyz789", directHttps.authToken)

        // Security audit: malicious QR payloads must be rejected at the QR boundary
        assertNull(parseAntigravityPairingPayload("""{"name":"Evil","url":"javascript:alert(1)"}"""))
        assertNull(parseAntigravityPairingPayload("antigravity://pair?url=file%3A%2F%2F%2Fetc%2Fpasswd"))
        assertNull(parseAntigravityPairingPayload("antigravity://pair?url=data%3Atext%2Fhtml%2C%3Cscript%3E"))
        assertNull(parseAntigravityPairingPayload("javascript:alert(1)"))
        assertNull(parseAntigravityPairingPayload("not a valid url"))
        assertNull(parseAntigravityPairingPayload(""))
    }

    @Test
    fun `biometric lock approval notifications and auto reconnect persist`() {
        val manager = AppearancePreferencesManager(context)
        manager.update {
            it.copy(
                isBiometricLockEnabled = true,
                isApprovalNotificationsEnabled = false,
                isAutoReconnectEnabled = false
            )
        }

        // Reload fresh manager instance from SharedPreferences
        val reloaded = AppearancePreferencesManager(context).settings.value
        assertTrue(reloaded.isBiometricLockEnabled)
        assertFalse(reloaded.isApprovalNotificationsEnabled)
        assertFalse(reloaded.isAutoReconnectEnabled)
    }

    @Test
    fun `app defaults to pure black OLED background fullscreen and locked developer profile`() {
        val defaultSettings = AppearanceSettings()
        assertEquals(AntigravityThemeMode.DARK, defaultSettings.themeMode)
        assertEquals("Default Dark", defaultSettings.presetName)
        assertEquals("#000000", defaultSettings.backgroundHex)
        assertEquals("#FFFFFF", defaultSettings.foregroundHex)
        assertEquals("#FFC799", defaultSettings.accentHex)
        assertEquals("#000000", OfficialAntigravityPresets.first().backgroundHex)
        assertEquals("#FFFFFF", OfficialAntigravityPresets.first().foregroundHex)
        assertEquals("#FFC799", OfficialAntigravityPresets.first().accentHex)
        assertTrue(defaultSettings.isFullscreenBarsHidden)
        assertEquals(0f, defaultSettings.floatingHandleXFraction, 0.001f)
        assertEquals(0.42f, defaultSettings.floatingHandleYFraction, 0.001f)
        assertEquals("Solomon Ogar Bukie", defaultSettings.developerName)
        assertEquals("solomonogarbukie@gmail.com", defaultSettings.developerEmail)
        assertEquals("IceCool30", defaultSettings.developerGithub)
        // Material You defaults OFF so #FFC799 is guaranteed out-of-the-box on Android 12+
        assertFalse(defaultSettings.isMaterialYouDynamicEnabled)
        assertTrue(defaultSettings.isFrostedGlassEnabled)

        val manager = AppearancePreferencesManager(context)
        manager.update {
            it.copy(
                themeMode = AntigravityThemeMode.LIGHT,
                presetName = "Dracula",
                backgroundHex = "#282A36",
                foregroundHex = "#F8F8F2",
                accentHex = "#BD93F9",
                floatingHandleXFraction = 0.85f,
                floatingHandleYFraction = 0.72f,
                isMaterialYouDynamicEnabled = true,
                isFrostedGlassEnabled = false
            )
        }
        val reloaded = AppearancePreferencesManager(context).settings.value
        assertEquals(0.85f, reloaded.floatingHandleXFraction, 0.001f)
        assertEquals(0.72f, reloaded.floatingHandleYFraction, 0.001f)
        // Developer attribution is strictly locked & immutable
        assertEquals("Solomon Ogar Bukie", reloaded.developerName)
        assertEquals("solomonogarbukie@gmail.com", reloaded.developerEmail)
        assertEquals("IceCool30", reloaded.developerGithub)
        assertTrue(reloaded.isMaterialYouDynamicEnabled)
        assertFalse(reloaded.isFrostedGlassEnabled)

        // Reset to Default Dark restores Dark theme + #000000 / #FFFFFF / #FFC799 and disables Material You
        manager.resetToDefaultDark()
        val resetState = manager.settings.value
        assertEquals(AntigravityThemeMode.DARK, resetState.themeMode)
        assertEquals("Default Dark", resetState.presetName)
        assertEquals("#000000", resetState.backgroundHex)
        assertEquals("#FFFFFF", resetState.foregroundHex)
        assertEquals("#FFC799", resetState.accentHex)
        assertFalse(resetState.isMaterialYouDynamicEnabled)

        // Picking a manual preset cleanly switches off Material You dynamic accent so the preset color applies
        manager.update { it.copy(isMaterialYouDynamicEnabled = true) }
        manager.applyPreset(OfficialAntigravityPresets.first())
        assertFalse(manager.settings.value.isMaterialYouDynamicEnabled)

        val rawWvUa = "Mozilla/5.0 (Linux; Android 15; Pixel 9; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.0.0 Mobile Safari/537.36"
        val cleanUa = RetainedWebViewManager.buildCleanMobileUserAgent(rawWvUa)
        assertFalse(cleanUa.contains("; wv"))
        assertFalse(cleanUa.contains("Version/4.0 "))
        assertTrue(cleanUa.contains("Chrome/131.0.0.0"))

        // Verify WebView file chooser hook attaches and clears cleanly for "+" file picker
        var invoked = false
        RetainedWebViewManager.onShowFileChooserHandler = { cb, _ ->
            invoked = true
            cb.onReceiveValue(null)
            true
        }
        assertNotNull(RetainedWebViewManager.onShowFileChooserHandler)
        RetainedWebViewManager.onShowFileChooserHandler?.invoke(android.webkit.ValueCallback {}, null)
        assertTrue(invoked)
        RetainedWebViewManager.onShowFileChooserHandler = null
        assertNull(RetainedWebViewManager.onShowFileChooserHandler)
    }

    @Test
    fun `MovableFloatingControlPanel opens on tap and triggers reload and tab actions`() {
        var expanded = false
        var reloaded = false
        var selectedTab = 0

        composeTestRule.setContent {
            AntigravityPulseTheme(appearance = AppearanceSettings()) {
                val isExp = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(expanded) }
                com.example.ui.components.MovableFloatingControlPanel(
                    isExpanded = isExp.value,
                    xFraction = 0f,
                    yFraction = 0.42f,
                    tabs = listOf(
                        com.example.ui.components.FloatingPanelTabItem(0, "Remote", Icons.Default.Language, "nav_tab_bridge"),
                        com.example.ui.components.FloatingPanelTabItem(1, "Cloud Sync", Icons.Default.CloudSync, "nav_tab_sync")
                    ),
                    selectedTabIndex = selectedTab,
                    activeHostLabel = "My Workstation",
                    surface = Color(0xFF0A0A0C),
                    elevated = Color(0xFF141418),
                    border = Color(0xFF222228),
                    fg = Color.White,
                    secondaryFg = Color.LightGray,
                    accent = Color(0xFFFFC799),
                    onExpandedChange = {
                        expanded = it
                        isExp.value = it
                    },
                    onPersistPositionFraction = { _, _ -> },
                    onSelectTab = { selectedTab = it },
                    onOpenHostSwitcher = {},
                    onOpenQrScanner = {},
                    onReloadWeb = { reloaded = true },
                    onShowBars = {},
                    onOpenSettings = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("floating_fullscreen_edge_handle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("floating_fullscreen_edge_handle").performClick()
        composeTestRule.waitForIdle()

        assertTrue(expanded)
        composeTestRule.onNodeWithTag("floating_quick_menu_overlay").assertIsDisplayed()
        composeTestRule.onNodeWithTag("floating_panel_drag_header").assertIsDisplayed()
        composeTestRule.onNodeWithTag("floating_quick_reload_web").performClick()
        composeTestRule.waitForIdle()

        assertTrue(reloaded)
        assertFalse(expanded)
    }

    @Test
    fun `returning after biometric unlock preserves active remote session and lastActiveRemoteUrl`() {
        val manager = AppearancePreferencesManager(context)
        val remoteUrl = "https://workstation.tailnet.ts.net:8443/remote"
        manager.update { it.copy(lastActiveRemoteUrl = remoteUrl) }
        assertEquals(remoteUrl, AppearancePreferencesManager(context).settings.value.lastActiveRemoteUrl)

        // Simulate loading an active remote session into RetainedWebViewManager
        RetainedWebViewManager.obtainPinnedWebView(
            context = context,
            endpointUrl = remoteUrl,
            domLagBooster = true,
            onJsBridgeMessage = {}
        )
        assertTrue(RetainedWebViewManager.hasActiveLoadedSession())
        assertEquals(remoteUrl, RetainedWebViewManager.currentLoadedEndpoint())

        // Simulate resuming after biometric unlock before Room finishes async load (blank endpoint)
        RetainedWebViewManager.obtainPinnedWebView(
            context = context,
            endpointUrl = "",
            domLagBooster = true,
            onJsBridgeMessage = {}
        )
        // Active remote session MUST remain intact (never overwritten with welcome screen)
        assertTrue(RetainedWebViewManager.hasActiveLoadedSession())
        assertEquals(remoteUrl, RetainedWebViewManager.currentLoadedEndpoint())

        // Explicit reset clears back to welcome screen
        RetainedWebViewManager.resetToWelcomeScreen()
        assertFalse(RetainedWebViewManager.hasActiveLoadedSession())
    }

    @Test
    fun `web notification bridge parses sanitizes and persists app notification settings`() {
        PulseNotificationManager.resetDedupForTests()
        PulseNotificationManager.ensureChannelsCreated(context)

        val validRaw = PulseNotificationManager.WEB_NOTIFY_CONSOLE_PREFIX +
            """{"title":"Agent Finished\nBuild","body":"All 12 tests passed cleanly.\tReady for review.","tag":"agent_done_1"}"""
        val parsed = PulseNotificationManager.parseWebNotificationConsolePayload(validRaw)
        assertNotNull(parsed)
        assertEquals("Agent Finished Build", parsed!!.title)
        assertEquals("All 12 tests passed cleanly. Ready for review.", parsed.body)
        assertEquals("agent_done_1", parsed.tag)

        // Normal console logs or malformed payloads must be ignored safely
        assertNull(PulseNotificationManager.parseWebNotificationConsolePayload("Normal console.log message"))
        assertNull(PulseNotificationManager.parseWebNotificationConsolePayload(PulseNotificationManager.WEB_NOTIFY_CONSOLE_PREFIX + "{invalid"))

        // Verify app notification preference defaults and persistence
        val defaultSettings = AppearanceSettings()
        assertTrue(defaultSettings.isApprovalNotificationsEnabled)
        assertTrue(defaultSettings.isAppEventNotificationsEnabled)
        assertFalse(defaultSettings.isNotifyOnlyInBackground)

        // Verify foreground suppression behavior for background-only alert mode
        PulseNotificationManager.isAppInForeground = true
        assertFalse(PulseNotificationManager.shouldSuppressForForeground(onlyWhenBackground = false))
        assertTrue(PulseNotificationManager.shouldSuppressForForeground(onlyWhenBackground = true))
        PulseNotificationManager.isAppInForeground = false
        assertFalse(PulseNotificationManager.shouldSuppressForForeground(onlyWhenBackground = true))
        PulseNotificationManager.isAppInForeground = true

        val manager = AppearancePreferencesManager(context)
        manager.update {
            it.copy(
                isAppEventNotificationsEnabled = false,
                isNotifyOnlyInBackground = true
            )
        }
        val reloaded = AppearancePreferencesManager(context).settings.value
        assertFalse(reloaded.isAppEventNotificationsEnabled)
        assertTrue(reloaded.isNotifyOnlyInBackground)
    }

    @Test
    fun `Fullscreen AI Studio Web workspace hides toolbar cleanly and exposes Prompts, Build Apps, and Lag Booster`() {
        var lagBoosterEnabled = true
        val fullscreenState = androidx.compose.runtime.mutableStateOf(true)
        val boosterState = androidx.compose.runtime.mutableStateOf(true)

        composeTestRule.setContent {
            AntigravityPulseTheme(appearance = AppearanceSettings()) {
                com.example.ui.screens.AiStudioWebScreen(
                    domLagBoosterEnabled = boosterState.value,
                    isFullscreenBarsHidden = fullscreenState.value,
                    onToggleDomBooster = { enabled ->
                        lagBoosterEnabled = enabled
                        boosterState.value = enabled
                    },
                    onStatusEvent = {}
                )
            }
        }

        // 1. In Fullscreen Mode: pure edge-to-edge WebView with zero distracting toolbar
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_studio_web_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_studio_pinned_webview_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_studio_collapsible_toolbar").assertDoesNotExist()
        assertTrue(com.example.data.remote.AiStudioPinnedWebViewManager.isPinnedInRam())

        // 2. When bars are shown: Prompts, Build Apps, Library, Lag Booster, and Pinned in RAM appear
        composeTestRule.runOnIdle { fullscreenState.value = false }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("ai_studio_collapsible_toolbar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_studio_web_section_new_chat").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_studio_web_section_build_apps").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_studio_web_section_drive_library").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_studio_lag_booster_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_studio_pinned_ram_badge").assertIsDisplayed()

        // 3. Tapping Build Apps and toggling Lag Booster updates state cleanly
        composeTestRule.onNodeWithTag("ai_studio_web_section_build_apps").performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            com.example.data.remote.AiStudioWebSection.BUILD_APPS,
            com.example.data.remote.AiStudioPinnedWebViewManager.activeSectionState.value
        )

        composeTestRule.onNodeWithTag("ai_studio_lag_booster_toggle").performClick()
        composeTestRule.waitForIdle()
        assertFalse(lagBoosterEnabled)
    }

    @Test
    fun `welcome page includes AI Studio guide and WebView permission bridge maps mic and camera safely`() {
        val welcomeHtml = RetainedWebViewManager.buildEmptyWebBridgeNoticeHtml()
        assertTrue(welcomeHtml.contains("How to Use Antigravity Remote"))
        assertTrue(welcomeHtml.contains("AI Studio — How to Use It"))
        assertTrue(welcomeHtml.contains("Build Apps"))
        assertTrue(welcomeHtml.contains("Prompts"))
        assertTrue(welcomeHtml.contains("Lag Booster"))
        assertTrue(welcomeHtml.contains("microphone"))
        assertTrue(welcomeHtml.contains("Device Permissions"))

        // Verify least-privilege WebView permission mapper maps mic & camera and rejects unsupported types
        val (audioRes, audioPerms) = resolveAllowedWebResourcesAndPermissions(
            arrayOf(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE)
        )
        assertEquals(listOf(android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE), audioRes)
        assertEquals(listOf(android.Manifest.permission.RECORD_AUDIO), audioPerms)

        val (bothRes, bothPerms) = resolveAllowedWebResourcesAndPermissions(
            arrayOf(
                android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE,
                android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID,
                android.webkit.PermissionRequest.RESOURCE_MIDI_SYSEX
            )
        )
        assertEquals(
            listOf(
                android.webkit.PermissionRequest.RESOURCE_AUDIO_CAPTURE,
                android.webkit.PermissionRequest.RESOURCE_VIDEO_CAPTURE
            ),
            bothRes
        )
        assertEquals(
            listOf(
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.CAMERA
            ),
            bothPerms
        )

        val (deniedRes, deniedPerms) = resolveAllowedWebResourcesAndPermissions(
            arrayOf(android.webkit.PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID)
        )
        assertTrue(deniedRes.isEmpty())
        assertTrue(deniedPerms.isEmpty())

        // Verify download and web permission hooks attach & clear cleanly
        RetainedWebViewManager.onWebDownloadRequested = { _, _, _, _ -> }
        assertNotNull(RetainedWebViewManager.onWebDownloadRequested)
        RetainedWebViewManager.onWebDownloadRequested = null
        assertNull(RetainedWebViewManager.onWebDownloadRequested)
    }

    @Test
    fun `native Welcome page shows Remote and AI Studio launch buttons and triggers both actions`() {
        RetainedWebViewManager.resetToWelcomeScreen()
        var remoteLaunchCount = 0
        var qrScanCount = 0
        var aiStudioLaunchCount = 0

        composeTestRule.setContent {
            AntigravityPulseTheme(appearance = AppearanceSettings()) {
                com.example.ui.screens.RetainedWebViewBridgeScreen(
                    telemetry = com.example.data.remote.BridgeTelemetry(activeEndpoint = ""),
                    savedFallbackEndpoint = "",
                    isFullscreenBarsHidden = true,
                    onToggleDomBooster = {},
                    onSaveAndConnectUrl = {},
                    onOpenHostSwitcher = { remoteLaunchCount++ },
                    onOpenQrScanner = { qrScanCount++ },
                    onOpenAiStudio = { aiStudioLaunchCount++ },
                    onJsBridgeEvent = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("native_welcome_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_guide_tab_remote").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_guide_tab_ai_studio").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_bottom_launch_remote_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("welcome_bottom_launch_ai_studio_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("welcome_guide_tab_ai_studio").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("welcome_guide_tab_remote").performClick()
        composeTestRule.onNodeWithTag("welcome_bottom_launch_remote_button").performClick()
        composeTestRule.onNodeWithTag("welcome_bottom_launch_ai_studio_button").performClick()
        composeTestRule.onNodeWithTag("welcome_scan_qr_button")
            .performScrollTo()
            .assertIsDisplayed()
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(1, remoteLaunchCount)
        assertEquals(1, aiStudioLaunchCount)
        assertEquals(1, qrScanCount)
    }

    @Test
    fun `offline continuity helper and overlay hold cached view on network drop and recover on reconnect`() {
        // 1. Verify network error classification
        assertTrue(
            com.example.data.remote.WebViewOfflineCacheHelper.isNetworkErrorCode(
                android.webkit.WebViewClient.ERROR_HOST_LOOKUP,
                isDeviceOnline = true
            )
        )
        assertTrue(
            com.example.data.remote.WebViewOfflineCacheHelper.isNetworkErrorCode(
                android.webkit.WebViewClient.ERROR_CONNECT,
                isDeviceOnline = true
            )
        )
        assertTrue(
            com.example.data.remote.WebViewOfflineCacheHelper.isNetworkErrorCode(
                android.webkit.WebViewClient.ERROR_TIMEOUT,
                isDeviceOnline = false
            )
        )
        assertFalse(
            com.example.data.remote.WebViewOfflineCacheHelper.isNetworkErrorCode(
                android.webkit.WebViewClient.ERROR_FAILED_SSL_HANDSHAKE,
                isDeviceOnline = true
            )
        )
        assertTrue(
            com.example.data.remote.WebViewOfflineCacheHelper.isNetworkErrorCode(
                android.webkit.WebViewClient.ERROR_FAILED_SSL_HANDSHAKE,
                isDeviceOnline = false
            )
        )

        // 2. Verify RetainedWebViewManager offline/reconnect continuity transitions
        val remoteUrl = "https://workstation.tailnet.ts.net:8443/remote"
        RetainedWebViewManager.obtainPinnedWebView(
            context = context,
            endpointUrl = remoteUrl,
            domLagBooster = true,
            onJsBridgeMessage = {}
        )
        assertTrue(RetainedWebViewManager.hasActiveSessionState.value)

        RetainedWebViewManager.onNetworkLost()
        val offlineState = RetainedWebViewManager.continuityState.value
        assertFalse(offlineState.isOnline)
        assertEquals(com.example.data.remote.OfflineRecoveryStatus.OFFLINE_CACHED, offlineState.status)

        RetainedWebViewManager.onNetworkRestored(autoReloadAfterLongOutage = false)
        val restoredState = RetainedWebViewManager.continuityState.value
        assertTrue(restoredState.isOnline)
        assertEquals(com.example.data.remote.OfflineRecoveryStatus.RESTORED, restoredState.status)

        RetainedWebViewManager.dismissRestoredBanner()
        assertEquals(
            com.example.data.remote.OfflineRecoveryStatus.ONLINE,
            RetainedWebViewManager.continuityState.value.status
        )

        RetainedWebViewManager.resetToWelcomeScreen()
        assertFalse(RetainedWebViewManager.hasActiveSessionState.value)

        // 3. Verify OfflineContinuityOverlay displays status pill, cold-start fallback, and retry action
        var retryClicks = 0
        composeTestRule.setContent {
            AntigravityPulseTheme(appearance = AppearanceSettings()) {
                com.example.ui.components.OfflineContinuityOverlay(
                    continuity = com.example.data.remote.WebViewContinuityState(
                        isOnline = false,
                        status = com.example.data.remote.OfflineRecoveryStatus.OFFLINE_CACHED,
                        hadMainFrameError = true,
                        showingSnapshotShield = true,
                        cachedSnapshot = null,
                        statusMessage = "Offline • Holding cached screen until internet returns"
                    ),
                    workspaceLabel = "Google AI Studio",
                    onRetryOrReloadLatest = { retryClicks++ },
                    onDismissRestoredBanner = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("offline_cold_start_card").assertIsDisplayed()
        composeTestRule.onNodeWithTag("offline_continuity_status_pill").assertIsDisplayed()
        composeTestRule.onNodeWithTag("offline_retry_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("offline_pill_action_button").assertIsDisplayed()

        composeTestRule.onNodeWithTag("offline_pill_action_button").performClick()
        composeTestRule.onNodeWithTag("offline_retry_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, retryClicks)
    }

    @Test
    fun `security audit redacts URL query tokens enforces secure media origins and sanitizes download filenames`() {
        // 1. URL credential & query-token redaction (Cloud Sync & lock-screen notification safety)
        assertEquals(
            "https://my-host.example.com:8443/remote",
            RetainedWebViewManager.stripSensitiveUrlCredentials(
                "https://user:pass@my-host.example.com:8443/remote?token=secret123#session"
            )
        )
        assertEquals(
            "http://192.168.1.50:8443/app",
            RetainedWebViewManager.stripSensitiveUrlCredentials(
                "http://192.168.1.50:8443/app?auth=xyz"
            )
        )
        assertEquals("", RetainedWebViewManager.stripSensitiveUrlCredentials("javascript:alert(1)"))

        // 2. Secure media origin enforcement for WebView microphone & camera requests
        assertTrue(RetainedWebViewManager.isSecureMediaOrigin("https://aistudio.google.com"))
        assertTrue(RetainedWebViewManager.isSecureMediaOrigin("https://workstation.tailnet.ts.net:8443"))
        assertTrue(RetainedWebViewManager.isSecureMediaOrigin("http://localhost:8443"))
        assertTrue(RetainedWebViewManager.isSecureMediaOrigin("http://127.0.0.1:3000"))
        assertFalse(RetainedWebViewManager.isSecureMediaOrigin("http://public-site.example.com"))
        assertFalse(RetainedWebViewManager.isSecureMediaOrigin("file:///etc/passwd"))

        // 3. Download filename path traversal & illegal character sanitization
        assertEquals("passwd", sanitizeDownloadFileName("../../etc/passwd"))
        assertEquals("payload.txt", sanitizeDownloadFileName("..\\..\\Windows\\payload.txt"))
        assertEquals("hidden_env", sanitizeDownloadFileName("...hidden_env"))
        assertEquals("antigravity-download", sanitizeDownloadFileName("   ...  "))
        assertEquals("report_2026_.pdf", sanitizeDownloadFileName("report:2026?.pdf"))
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun `redesigned Settings sheet starts with collapsed customization and code preview and supports reset`() {
        var resetCount = 0
        val settingsState = androidx.compose.runtime.mutableStateOf(
            AppearanceSettings(
                themeMode = AntigravityThemeMode.LIGHT,
                presetName = "Dracula",
                backgroundHex = "#282A36",
                foregroundHex = "#F8F8F2",
                accentHex = "#BD93F9"
            )
        )

        composeTestRule.setContent {
            AntigravityPulseTheme(appearance = settingsState.value) {
                com.example.ui.screens.AppearanceSettingsSheet(
                    settings = settingsState.value,
                    onUpdateSettings = { transform -> settingsState.value = transform(settingsState.value) },
                    onSelectPreset = { preset ->
                        settingsState.value = settingsState.value.copy(
                            presetName = preset.name,
                            backgroundHex = preset.backgroundHex,
                            foregroundHex = preset.foregroundHex,
                            accentHex = preset.accentHex,
                            isMaterialYouDynamicEnabled = false
                        )
                    },
                    onResetPreset = {
                        resetCount++
                        val defaultDark = OfficialAntigravityPresets.first()
                        settingsState.value = settingsState.value.copy(
                            themeMode = AntigravityThemeMode.DARK,
                            presetName = defaultDark.name,
                            backgroundHex = defaultDark.backgroundHex,
                            foregroundHex = defaultDark.foregroundHex,
                            accentHex = defaultDark.accentHex,
                            isMaterialYouDynamicEnabled = false
                        )
                    },
                    onClose = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("appearance_settings_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("reset_preset_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("open_preset_picker_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("customize_appearance_toggle").assertIsDisplayed()

        // Advanced Colors/Font & Live Code Preview must be collapsed by default
        composeTestRule.onNodeWithTag("customize_appearance_content").assertDoesNotExist()
        composeTestRule.onNodeWithTag("code_preview_box").assertDoesNotExist()

        // Tapping Reset Default immediately restores Dark + #000000 / #FFFFFF / #FFC799
        composeTestRule.onNodeWithTag("reset_preset_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, resetCount)
        assertEquals(AntigravityThemeMode.DARK, settingsState.value.themeMode)
        assertEquals("#000000", settingsState.value.backgroundHex)
        assertEquals("#FFFFFF", settingsState.value.foregroundHex)
        assertEquals("#FFC799", settingsState.value.accentHex)

        // Expanding Customize reveals hex color pills & collapsed Code Preview toggle
        composeTestRule.onNodeWithTag("customize_appearance_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("customize_appearance_content").assertIsDisplayed()
        composeTestRule.onNodeWithTag("hex_color_pill_background").assertIsDisplayed()
        composeTestRule.onNodeWithTag("code_preview_box").assertDoesNotExist()

        // Scroll down gently to tap Live Code Preview toggle and verify preview box displays
        composeTestRule.onNodeWithTag("settings_list").performTouchInput {
            swipeUp(startY = centerY, endY = top + 40f, durationMillis = 500)
        }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("code_preview_toggle").assertIsDisplayed().performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("code_preview_box").assertIsDisplayed()
    }

    @Test
    fun `GitHub, Vercel, and Supabase workspaces support lazy RAM pinning, shortcuts, Desktop View, and offline recovery`() {
        val github = com.example.data.remote.DevWebWorkspaceRegistry.session(
            com.example.data.remote.DevWebWorkspace.GITHUB
        )
        val vercel = com.example.data.remote.DevWebWorkspaceRegistry.session(
            com.example.data.remote.DevWebWorkspace.VERCEL
        )
        val supabase = com.example.data.remote.DevWebWorkspaceRegistry.session(
            com.example.data.remote.DevWebWorkspace.SUPABASE
        )

        // 1. Verify Desktop User-Agent builder strips mobile/wv tokens cleanly
        val desktopUa = com.example.data.remote.PinnedWebWorkspaceSession.buildCleanDesktopUserAgent(
            "Mozilla/5.0 (Linux; Android 15; Pixel 9; wv) AppleWebKit/537.36 Chrome/131.0.0.0 Mobile Safari/537.36"
        )
        assertTrue(desktopUa.contains("X11; Linux x86_64"))
        assertTrue(desktopUa.contains("Chrome/131.0.0.0"))
        assertFalse(desktopUa.contains("Android"))
        assertFalse(desktopUa.contains("; wv"))

        // 2. Verify all 3 workspaces have expected developer shortcuts
        assertEquals("https://github.com/", com.example.data.remote.DevWebWorkspace.GITHUB.defaultShortcut.url)
        assertEquals("https://vercel.com/dashboard", com.example.data.remote.DevWebWorkspace.VERCEL.defaultShortcut.url)
        assertEquals("https://supabase.com/dashboard/projects", com.example.data.remote.DevWebWorkspace.SUPABASE.defaultShortcut.url)

        // 3. Verify PinnedWebWorkspaceScreen fullscreen vs collapsible toolbar + Desktop View + shortcuts
        val fullscreenState = androidx.compose.runtime.mutableStateOf(true)
        val boosterState = androidx.compose.runtime.mutableStateOf(true)
        var boosterToggledTo = true

        composeTestRule.setContent {
            AntigravityPulseTheme(appearance = AppearanceSettings()) {
                com.example.ui.screens.PinnedWebWorkspaceScreen(
                    workspace = com.example.data.remote.DevWebWorkspace.GITHUB,
                    domLagBoosterEnabled = boosterState.value,
                    isFullscreenBarsHidden = fullscreenState.value,
                    onToggleDomBooster = { enabled ->
                        boosterToggledTo = enabled
                        boosterState.value = enabled
                    },
                    onStatusEvent = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("github_web_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("github_pinned_webview_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("github_collapsible_toolbar").assertDoesNotExist()
        assertTrue(github.isPinnedInRam())
        // Lazy allocation: Vercel and Supabase remain unallocated until opened
        assertFalse(vercel.isPinnedInRam())
        assertFalse(supabase.isPinnedInRam())

        // Expand bars -> verify shortcuts, Desktop View toggle, Lag Booster, and Pinned badge
        composeTestRule.runOnIdle { fullscreenState.value = false }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("github_collapsible_toolbar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("github_shortcut_home").assertIsDisplayed()
        composeTestRule.onNodeWithTag("github_shortcut_repos").assertIsDisplayed()
        composeTestRule.onNodeWithTag("github_shortcut_pulls").assertIsDisplayed()
        composeTestRule.onNodeWithTag("github_desktop_mode_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("github_lag_booster_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("github_pinned_ram_badge").assertIsDisplayed()

        // Tap Repos shortcut + toggle Desktop View + toggle Lag Booster
        composeTestRule.onNodeWithTag("github_shortcut_repos").performClick()
        composeTestRule.onNodeWithTag("github_desktop_mode_toggle").performClick()
        composeTestRule.onNodeWithTag("github_lag_booster_toggle").performClick()
        composeTestRule.waitForIdle()

        assertEquals("repos", github.activeShortcutId.value)
        assertTrue(github.isDesktopModeState.value)
        assertFalse(boosterToggledTo)

        // 4. Verify Offline Visual Continuity transitions on registry
        com.example.data.remote.DevWebWorkspaceRegistry.onNetworkLostAll()
        assertFalse(github.continuityState.value.isOnline)
        assertEquals(
            com.example.data.remote.OfflineRecoveryStatus.OFFLINE_CACHED,
            github.continuityState.value.status
        )
        com.example.data.remote.DevWebWorkspaceRegistry.onNetworkRestoredAll(autoReloadAfterLongOutage = false)
        assertTrue(github.continuityState.value.isOnline)
        assertEquals(
            com.example.data.remote.OfflineRecoveryStatus.RESTORED,
            github.continuityState.value.status
        )

        // Clean reset
        com.example.data.remote.DevWebWorkspaceRegistry.clearAllSensitiveBrowsingData()
        assertFalse(github.isDesktopModeState.value)
        assertEquals("home", github.activeShortcutId.value)
    }

    @Test
    fun `switching between GitHub, Vercel, Supabase, and Gemini mounts distinct RAM-pinned WebViews`() {
        com.example.data.remote.DevWebWorkspaceRegistry.clearAllSensitiveBrowsingData()
        val activeWorkspace = androidx.compose.runtime.mutableStateOf(
            com.example.data.remote.DevWebWorkspace.GITHUB
        )

        composeTestRule.setContent {
            AntigravityPulseTheme(appearance = AppearanceSettings()) {
                val ws = activeWorkspace.value
                androidx.compose.runtime.key(ws) {
                    com.example.ui.screens.PinnedWebWorkspaceScreen(
                        workspace = ws,
                        domLagBoosterEnabled = true,
                        isFullscreenBarsHidden = false,
                        onToggleDomBooster = {},
                        onStatusEvent = {}
                    )
                }
            }
        }

        // 1. Starts on GitHub — only GitHub should be pinned initially
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("github_web_screen").assertIsDisplayed()
        assertTrue(com.example.data.remote.DevWebWorkspaceRegistry.session(com.example.data.remote.DevWebWorkspace.GITHUB).isPinnedInRam())
        assertFalse(com.example.data.remote.DevWebWorkspaceRegistry.session(com.example.data.remote.DevWebWorkspace.VERCEL).isPinnedInRam())

        // 2. Switch to Vercel -> Vercel screen & distinct WebView must mount immediately
        composeTestRule.runOnIdle { activeWorkspace.value = com.example.data.remote.DevWebWorkspace.VERCEL }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("vercel_web_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("vercel_shortcut_deployments").assertIsDisplayed()
        assertTrue(com.example.data.remote.DevWebWorkspaceRegistry.session(com.example.data.remote.DevWebWorkspace.VERCEL).isPinnedInRam())

        // 3. Switch to Supabase -> Supabase screen & distinct WebView must mount immediately
        composeTestRule.runOnIdle { activeWorkspace.value = com.example.data.remote.DevWebWorkspace.SUPABASE }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("supabase_web_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("supabase_shortcut_projects").assertIsDisplayed()
        assertTrue(com.example.data.remote.DevWebWorkspaceRegistry.session(com.example.data.remote.DevWebWorkspace.SUPABASE).isPinnedInRam())

        // 4. Switch to Gemini Web -> Gemini screen & shortcuts (Chat, Gems, Saved) mount cleanly
        composeTestRule.runOnIdle { activeWorkspace.value = com.example.data.remote.DevWebWorkspace.GEMINI }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("gemini_web_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gemini_shortcut_chat").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gemini_shortcut_gems").assertIsDisplayed()
        composeTestRule.onNodeWithTag("gemini_shortcut_saved").assertIsDisplayed()
        assertTrue(com.example.data.remote.DevWebWorkspaceRegistry.session(com.example.data.remote.DevWebWorkspace.GEMINI).isPinnedInRam())

        composeTestRule.onNodeWithTag("gemini_shortcut_gems").performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            "gems",
            com.example.data.remote.DevWebWorkspaceRegistry.session(com.example.data.remote.DevWebWorkspace.GEMINI).activeShortcutId.value
        )

        com.example.data.remote.DevWebWorkspaceRegistry.clearAllSensitiveBrowsingData()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun `Pulse Browser resolves URLs safely, supports Speed Dial, multi-tab lifecycle, and Desktop View`() {
        com.example.data.remote.PulseBrowserManager.clearAllSensitiveBrowsingData()

        // 1. Smart omnibox URL & search resolution + unsafe scheme rejection
        assertEquals(
            "https://vercel.app",
            com.example.data.remote.PulseBrowserManager.resolveAddressInput("vercel.app")
        )
        assertEquals(
            "http://localhost:3000/dashboard",
            com.example.data.remote.PulseBrowserManager.resolveAddressInput("localhost:3000/dashboard")
        )
        assertEquals(
            "http://192.168.1.40:8080",
            com.example.data.remote.PulseBrowserManager.resolveAddressInput("192.168.1.40:8080")
        )
        val searchUrl = com.example.data.remote.PulseBrowserManager.resolveAddressInput("jetpack compose webview")
        assertNotNull(searchUrl)
        assertTrue(searchUrl!!.startsWith("https://www.google.com/search?q="))

        assertNull(com.example.data.remote.PulseBrowserManager.resolveAddressInput("javascript:alert(1)"))
        assertNull(com.example.data.remote.PulseBrowserManager.resolveAddressInput("file:///etc/passwd"))
        assertNull(com.example.data.remote.PulseBrowserManager.resolveAddressInput("data:text/html,<script>"))

        // 2. Render PulseBrowserScreen and verify Speed Dial + multi-tab open/switch/close + Desktop View
        composeTestRule.setContent {
            AntigravityPulseTheme(appearance = AppearanceSettings()) {
                com.example.ui.screens.PulseBrowserScreen(
                    domLagBoosterEnabled = true,
                    onStatusEvent = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("pulse_browser_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("browser_top_chrome").assertIsDisplayed()
        composeTestRule.onNodeWithTag("browser_speed_dial").assertIsDisplayed()
        composeTestRule.onNodeWithTag("browser_quick_link_vercel").assertIsDisplayed()
        composeTestRule.onNodeWithTag("browser_quick_link_gemini").assertIsDisplayed()

        // Tap Vercel Speed Dial tile -> loads Vercel in tab 1 and hides Speed Dial
        composeTestRule.onNodeWithTag("browser_quick_link_vercel").performClick()
        composeTestRule.waitForIdle()
        val firstTab = com.example.data.remote.PulseBrowserManager.activeTab()
        assertEquals("https://vercel.com/dashboard", firstTab.url)

        // Open a second tab -> Speed Dial returns for tab 2
        composeTestRule.onNodeWithTag("browser_new_tab_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(2, com.example.data.remote.PulseBrowserManager.tabs.value.size)
        val secondTab = com.example.data.remote.PulseBrowserManager.activeTab()
        assertTrue(secondTab.id != firstTab.id)
        composeTestRule.onNodeWithTag("browser_speed_dial").assertIsDisplayed()

        // Toggle Desktop View on active tab
        composeTestRule.onNodeWithTag("browser_desktop_mode_button").performClick()
        composeTestRule.waitForIdle()
        assertTrue(com.example.data.remote.PulseBrowserManager.activeTab().isDesktopMode)

        // Close second tab -> cleanly falls back to tab 1
        composeTestRule.onNodeWithTag("browser_close_tab_${secondTab.id}").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, com.example.data.remote.PulseBrowserManager.tabs.value.size)
        assertEquals(firstTab.id, com.example.data.remote.PulseBrowserManager.activeTabId.value)

        // Offline visual continuity transition
        com.example.data.remote.PulseBrowserManager.onNetworkLostAll()
        assertFalse(com.example.data.remote.PulseBrowserManager.activeTab().continuityState.isOnline)
        com.example.data.remote.PulseBrowserManager.onNetworkRestoredAll(autoReloadAfterLongOutage = false)
        assertTrue(com.example.data.remote.PulseBrowserManager.activeTab().continuityState.isOnline)

        com.example.data.remote.PulseBrowserManager.clearAllSensitiveBrowsingData()
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun `compact MovableFloatingControlPanel starts with collapsed dropdowns and expands Workspaces and Page Tools`() {
        val isExpandedState = androidx.compose.runtime.mutableStateOf(true)
        var selectedTab = 6
        var newTabClicks = 0
        var desktopMode = false

        composeTestRule.setContent {
            AntigravityPulseTheme(appearance = AppearanceSettings()) {
                com.example.ui.components.MovableFloatingControlPanel(
                    isExpanded = isExpandedState.value,
                    xFraction = 0f,
                    yFraction = 0.2f,
                    tabs = listOf(
                        com.example.ui.components.FloatingPanelTabItem(0, "Remote", Icons.Default.Language, "nav_tab_bridge"),
                        com.example.ui.components.FloatingPanelTabItem(6, "Browser", Icons.Default.Language, "nav_tab_browser"),
                        com.example.ui.components.FloatingPanelTabItem(7, "Cloud Sync", Icons.Default.CloudSync, "nav_tab_sync")
                    ),
                    selectedTabIndex = selectedTab,
                    activeHostLabel = "My Workstation",
                    isBrowserActive = true,
                    isBrowserBarVisible = true,
                    browserTabCount = 2,
                    isDesktopModeEnabled = desktopMode,
                    surface = Color(0xFF0A0A0C),
                    elevated = Color(0xFF141418),
                    border = Color(0xFF222228),
                    fg = Color.White,
                    secondaryFg = Color.LightGray,
                    accent = Color(0xFFFFC799),
                    onExpandedChange = { isExpandedState.value = it },
                    onPersistPositionFraction = { _, _ -> },
                    onSelectTab = { selectedTab = it },
                    onNewBrowserTab = { newTabClicks++ },
                    onToggleDesktopMode = { desktopMode = it },
                    onOpenHostSwitcher = {},
                    onOpenQrScanner = {},
                    onReloadWeb = {},
                    onShowBars = {},
                    onOpenSettings = {}
                )
            }
        }

        // Both Workspaces and Page Tools dropdowns must be collapsed by default
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("floating_workspaces_dropdown_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("floating_page_tools_dropdown_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("floating_workspaces_dropdown_content").assertDoesNotExist()
        composeTestRule.onNodeWithTag("floating_page_tools_dropdown_content").assertDoesNotExist()

        // Expand Page Tools -> Browser actions appear and work
        composeTestRule.onNodeWithTag("floating_page_tools_dropdown_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("floating_page_tools_dropdown_content").assertIsDisplayed()
        composeTestRule.onNodeWithTag("floating_browser_desktop_mode").performClick()
        composeTestRule.waitForIdle()
        assertTrue(desktopMode)

        composeTestRule.onNodeWithTag("floating_browser_new_tab").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, newTabClicks)
        assertFalse(isExpandedState.value)

        // Re-open panel -> dropdowns auto-reset to collapsed, expand Workspaces and switch tab
        composeTestRule.runOnIdle { isExpandedState.value = true }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("floating_workspaces_dropdown_content").assertDoesNotExist()
        composeTestRule.onNodeWithTag("floating_workspaces_dropdown_toggle").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("floating_workspaces_dropdown_content").assertIsDisplayed()
        composeTestRule.onNodeWithTag("floating_tab_nav_tab_sync").performClick()
        composeTestRule.waitForIdle()
        assertEquals(7, selectedTab)
    }

    @Test
    fun `Phase 1 multi-user isolation destroys all pinned WebViews bumps rendererGeneration and strips persisted URL credentials`() = runBlocking {
        // 1. Pin AI Studio and GitHub WebViews in RAM and capture their initial renderer generations
        com.example.data.remote.AiStudioPinnedWebViewManager.obtainPinnedWebView(
            context = context,
            initialSection = com.example.data.remote.AiStudioWebSection.NEW_CHAT,
            domLagBooster = true,
            onPageEvent = {}
        )
        val githubSession = com.example.data.remote.DevWebWorkspaceRegistry.session(
            com.example.data.remote.DevWebWorkspace.GITHUB
        )
        githubSession.obtainPinnedWebView(
            context = context,
            domLagBooster = true,
            onPageEvent = {}
        )
        assertTrue(com.example.data.remote.AiStudioPinnedWebViewManager.isPinnedInRam())
        assertTrue(githubSession.isPinnedInRam())

        val aiGenBefore = com.example.data.remote.AiStudioPinnedWebViewManager.rendererGeneration.value
        val ghGenBefore = githubSession.rendererGeneration.value

        // 2. Wipe sensitive browsing data -> live WebViews must be destroyed (unpinned) & generations incremented
        com.example.data.remote.AiStudioPinnedWebViewManager.clearSensitiveBrowsingData(context)
        com.example.data.remote.DevWebWorkspaceRegistry.clearAllSensitiveBrowsingData(context)
        com.example.data.remote.RetainedWebViewManager.clearSensitiveBrowsingData(context)
        com.example.data.remote.PulseBrowserManager.clearAllSensitiveBrowsingData(context)

        assertFalse(com.example.data.remote.AiStudioPinnedWebViewManager.isPinnedInRam())
        assertFalse(githubSession.isPinnedInRam())
        assertTrue(com.example.data.remote.AiStudioPinnedWebViewManager.rendererGeneration.value > aiGenBefore)
        assertTrue(githubSession.rendererGeneration.value > ghGenBefore)

        // 3. Verify lastSignedInUid persists for cold-start cross-user account-switch detection
        val prefs = AppearancePreferencesManager(context)
        prefs.update { it.copy(lastSignedInUid = "user_alice_uid") }
        assertEquals("user_alice_uid", AppearancePreferencesManager(context).settings.value.lastSignedInUid)
        prefs.update { it.copy(lastSignedInUid = "") }
        assertEquals("", AppearancePreferencesManager(context).settings.value.lastSignedInUid)

        // 4. Verify AntigravityViewModel strips embedded URL userinfo/query tokens before saving to Room
        val dao = database.dao()
        val vm = com.example.viewmodel.AntigravityViewModel(dao)
        vm.addOrUpdateServerProfile(
            existingId = "srv_sanitized",
            name = "Workstation",
            endpointUrl = "https://admin:hunter2@dev.example.com:8443/app?token=leaked_secret#frag",
            connectionMode = "UNKNOWN_MODE",
            authToken = "local_tok",
            keepAlivePinned = true
        )
        // Wait briefly for viewModelScope coroutine to commit to in-memory Room DB
        var saved: ServerProfileEntity? = null
        repeat(20) {
            saved = dao.getAllServerProfilesOnce().firstOrNull { it.id == "srv_sanitized" }
            if (saved != null) return@repeat
            kotlinx.coroutines.delay(25L)
        }
        assertNotNull(saved)
        assertEquals("https://dev.example.com:8443/app", saved!!.endpointUrl)
        assertEquals("HYBRID_BRIDGE", saved.connectionMode)
    }

    @Test
    fun `Phase 2 WebView and bridge security blocks obfuscated SSRF hosts, lookalike origins, untrusted cookie downloads, silent third-party media grants, and sensitive page snapshots`() {
        // 1. Obfuscated integer/hex/octal/IPv6-mapped metadata IPs and backslash authority confusion
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("http://2852039166/latest/meta-data"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("http://0xa9fea9fe/latest/meta-data"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("http://0251.0376.0251.0376/"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("http://[::ffff:169.254.169.254]/"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("https://evil.com\\@aistudio.google.com"))
        assertTrue(RetainedWebViewManager.isSafeHttpUrl("https://192.168.1.50:8443/app"))

        // 2. Strict parsed-host domain matching defeats lookalike & query-string spoofing
        assertTrue(RetainedWebViewManager.isHostMatchingDomain("https://aistudio.google.com/prompts/new_chat", "aistudio.google.com"))
        assertTrue(RetainedWebViewManager.isHostMatchingDomain("https://api.github.com/repos", "github.com"))
        assertFalse(RetainedWebViewManager.isHostMatchingDomain("https://evil-github.com/login", "github.com"))
        assertFalse(RetainedWebViewManager.isHostMatchingDomain("https://evil.com/?ref=aistudio.google.com", "aistudio.google.com"))
        assertFalse(RetainedWebViewManager.isHostMatchingDomain("http://github.com/", "github.com", requireHttps = true))

        // 3. Strict same-origin matching for notification shims & prompt injection
        assertTrue(RetainedWebViewManager.isSameOrigin("https://dev.example.com:8443/chat", "https://dev.example.com:8443/"))
        assertTrue(RetainedWebViewManager.isSameOrigin("https://dev.example.com:443/a", "https://dev.example.com/b"))
        assertFalse(RetainedWebViewManager.isSameOrigin("https://evil.com/", "https://dev.example.com:8443/"))
        assertFalse(RetainedWebViewManager.isSameOrigin("https://dev.example.com:9443/", "https://dev.example.com:8443/"))

        // 4. First-party media origin trust (third-party sites require explicit confirmation dialog)
        val remoteEndpoint = "https://workstation.tailnet.ts.net:8443"
        assertTrue(RetainedWebViewManager.isFirstPartyTrustedMediaOrigin("https://aistudio.google.com", remoteEndpoint))
        assertTrue(RetainedWebViewManager.isFirstPartyTrustedMediaOrigin("https://gemini.google.com", remoteEndpoint))
        assertTrue(RetainedWebViewManager.isFirstPartyTrustedMediaOrigin("https://workstation.tailnet.ts.net:8443", remoteEndpoint))
        assertFalse(RetainedWebViewManager.isFirstPartyTrustedMediaOrigin("https://random-third-party.example.com", remoteEndpoint))

        // 5. Cookie-scoped DownloadManager guard blocks third-party cookie exfiltration
        assertTrue(RetainedWebViewManager.isTrustedCookieDownloadUrl("https://github.com/user/repo/archive.zip", remoteEndpoint))
        assertTrue(RetainedWebViewManager.isTrustedCookieDownloadUrl("https://workstation.tailnet.ts.net:8443/export.tar", remoteEndpoint))
        assertFalse(RetainedWebViewManager.isTrustedCookieDownloadUrl("https://attacker.example.com/steal.bin", remoteEndpoint))
        assertFalse(RetainedWebViewManager.isTrustedCookieDownloadUrl("http://github.com/archive.zip", remoteEndpoint))

        // 6. Offline snapshot exclusion blocks auth, OAuth, 2FA, tokens, and billing pages
        assertTrue(com.example.data.remote.WebViewOfflineCacheHelper.isSensitiveNoSnapshotUrl("https://accounts.google.com/v3/signin"))
        assertTrue(com.example.data.remote.WebViewOfflineCacheHelper.isSensitiveNoSnapshotUrl("https://github.com/settings/tokens"))
        assertTrue(com.example.data.remote.WebViewOfflineCacheHelper.isSensitiveNoSnapshotUrl("https://vercel.com/login"))
        assertTrue(com.example.data.remote.WebViewOfflineCacheHelper.isSensitiveNoSnapshotUrl("https://supabase.com/dashboard/account/tokens"))
        assertFalse(com.example.data.remote.WebViewOfflineCacheHelper.isSensitiveNoSnapshotUrl("https://aistudio.google.com/prompts/new_chat"))
        assertFalse(com.example.data.remote.WebViewOfflineCacheHelper.isSensitiveNoSnapshotUrl("https://github.com/IceCool30"))
    }

    @Test
    fun `Phase 3 device input and transport hardening blocks malicious QR SSRF or downgrade payloads and marks clipboard prompts sensitive`() {
        // 1. QR pairing parser rejects cloud metadata IPs, obfuscated IP encodings, backslash confusion, and HTTPS-only downgrades
        assertNull(parseAntigravityPairingPayload("http://169.254.169.254/latest/meta-data"))
        assertNull(parseAntigravityPairingPayload("http://2852039166/latest/meta-data"))
        assertNull(parseAntigravityPairingPayload("http://[::ffff:169.254.169.254]/"))
        assertNull(parseAntigravityPairingPayload("https://evil.com\\@aistudio.google.com"))
        assertNull(parseAntigravityPairingPayload("""{"name":"Downgrade","url":"http://github.com/login"}"""))
        assertNull(parseAntigravityPairingPayload("antigravity://pair?url=http%3A%2F%2Faistudio.google.com"))

        // Legitimate HTTPS, WSS, WS, and private LAN HTTP endpoints remain supported
        val validLan = parseAntigravityPairingPayload("""{"name":"Desk","url":"http://192.168.1.42:8080/remote","token":"tok123"}""")
        assertNotNull(validLan)
        assertEquals("http://192.168.1.42:8080/remote", validLan!!.endpointUrl)
        val validWss = parseAntigravityPairingPayload("wss://host.tailnet.ts.net:8443/ws")
        assertNotNull(validWss)
        assertEquals("https://host.tailnet.ts.net:8443/ws", validWss!!.endpointUrl)

        // 2. Sensitive clipboard helper attaches EXTRA_IS_SENSITIVE flag to prevent visual clipboard preview leaks
        com.example.ui.screens.copySensitiveTextToClipboard(
            context = context,
            label = "Antigravity Prompt",
            text = "Private production deployment checklist"
        )
        val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val primaryClip = cm.primaryClip
        assertNotNull(primaryClip)
        assertEquals("Private production deployment checklist", primaryClip!!.getItemAt(0).text.toString())
        val extras = primaryClip.description.extras
        assertNotNull(extras)
        assertTrue(extras!!.getBoolean("android.content.extra.IS_SENSITIVE", false))
    }

    @Test
    fun `Light and Dark mode lighting guarantees WCAG AA contrast across all 12 presets, AI Studio accents, and filled button ink`() {
        // Every official preset must achieve >= 4.5:1 accent contrast on Light Mode cards and >= 4.5:1 button ink contrast
        OfficialAntigravityPresets.forEach { preset ->
            val light = AppearanceSettings(
                themeMode = AntigravityThemeMode.LIGHT,
                presetName = preset.name,
                backgroundHex = preset.backgroundHex,
                foregroundHex = preset.foregroundHex,
                accentHex = preset.accentHex
            )
            val accentOnSurface = com.example.ui.theme.contrastRatio(light.accentColor, light.surfaceColor)
            assertTrue(
                "Preset '${preset.name}' Light Mode accent contrast was $accentOnSurface (< 4.5)",
                accentOnSurface >= 4.5f
            )
            val onAccentContrast = com.example.ui.theme.contrastRatio(light.onAccentColor, light.accentColor)
            assertTrue(
                "Preset '${preset.name}' Light Mode onAccent contrast was $onAccentContrast (< 4.5)",
                onAccentContrast >= 4.5f
            )
            val fgOnSurface = com.example.ui.theme.contrastRatio(light.foregroundColor, light.surfaceColor)
            assertTrue("Light Mode primary text contrast must exceed 10:1", fgOnSurface >= 10f)
            val secondaryOnSurface = com.example.ui.theme.contrastRatio(light.secondaryTextColor, light.surfaceColor)
            assertTrue("Light Mode secondary text contrast must exceed 4.5:1", secondaryOnSurface >= 4.5f)
            val aiStudioOnSurface = com.example.ui.theme.contrastRatio(light.aiStudioAccentColor, light.surfaceColor)
            assertTrue("Light Mode AI Studio accent contrast must exceed 4.5:1", aiStudioOnSurface >= 4.5f)
            val successOnSurface = com.example.ui.theme.contrastRatio(light.successColor, light.surfaceColor)
            assertTrue("Light Mode success color contrast must exceed 4.5:1", successOnSurface >= 4.5f)
            val errorOnSurface = com.example.ui.theme.contrastRatio(light.errorColor, light.surfaceColor)
            assertTrue("Light Mode error color contrast must exceed 4.5:1", errorOnSurface >= 4.5f)

            // Dark Mode preserves authentic high-contrast presets and readable button ink
            val dark = light.copy(themeMode = AntigravityThemeMode.DARK)
            val darkAccentOnSurface = com.example.ui.theme.contrastRatio(dark.accentColor, dark.surfaceColor)
            assertTrue(
                "Preset '${preset.name}' Dark Mode accent contrast was $darkAccentOnSurface (< 4.5)",
                darkAccentOnSurface >= 4.5f
            )
            val darkOnAccentContrast = com.example.ui.theme.contrastRatio(dark.onAccentColor, dark.accentColor)
            assertTrue(
                "Preset '${preset.name}' Dark Mode onAccent contrast was $darkOnAccentContrast (< 4.5)",
                darkOnAccentContrast >= 4.5f
            )
        }

        // Default Dark keeps exact #FFC799 unchanged with dark obsidian ink
        val defaultDark = AppearanceSettings(themeMode = AntigravityThemeMode.DARK)
        assertEquals(Color(0xFFFFC799), defaultDark.accentColor)
        assertEquals(Color(0xFF070708), defaultDark.onAccentColor)

        // Light Mode with Default Dark preset adapts #FFC799 into high-contrast rich amber with crisp White button ink
        val defaultLight = AppearanceSettings(themeMode = AntigravityThemeMode.LIGHT)
        assertEquals(Color.White, defaultLight.onAccentColor)
        assertTrue(com.example.ui.theme.contrastRatio(defaultLight.accentColor, defaultLight.surfaceColor) >= 4.8f)
    }

    @Test
    fun `T3 Code Web workspace defaults to app t3 codes, manages custom environments, preserves pairing fragments, and enforces security`() {
        com.example.data.remote.T3CodePinnedWebViewManager.clearSensitiveBrowsingData(context)

        // 1. Security allowlists: https://app.t3.codes/ is trusted HTTPS-only; cleartext http://app.t3.codes/ is blocked
        assertTrue(RetainedWebViewManager.isSafeHttpUrl("https://app.t3.codes/"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("http://app.t3.codes/"))
        assertTrue(RetainedWebViewManager.isFirstPartyTrustedMediaOrigin("https://app.t3.codes/", ""))
        assertTrue(RetainedWebViewManager.isTrustedCookieDownloadUrl("https://app.t3.codes/export.zip", ""))

        // 2. QR pairing parser handles T3 Code #token=... fragments and t3:// deep links
        val t3FragmentQr = parseAntigravityPairingPayload("https://app.t3.codes/#token=pair_secret_99")
        assertNotNull(t3FragmentQr)
        assertEquals("https://app.t3.codes/#token=pair_secret_99", t3FragmentQr!!.endpointUrl)
        assertEquals("pair_secret_99", t3FragmentQr.authToken)

        val t3DeepLink = parseAntigravityPairingPayload("t3://pair?url=https%3A%2F%2Fdev.ts.net%3A3773%2F%23token%3Dabc&name=DevBox")
        assertNotNull(t3DeepLink)
        assertEquals("DevBox", t3DeepLink!!.hostName)
        assertEquals("https://dev.ts.net:3773/#token=abc", t3DeepLink.endpointUrl)
        assertEquals("abc", t3DeepLink.authToken)

        // 3. Default state starts on built-in T3 Code Cloud (https://app.t3.codes/)
        com.example.data.remote.T3CodePinnedWebViewManager.ensureEnvironmentsLoaded(context)
        assertEquals(1, com.example.data.remote.T3CodePinnedWebViewManager.savedEnvironments.value.size)
        assertEquals(
            "https://app.t3.codes/",
            com.example.data.remote.T3CodePinnedWebViewManager.activeEnvironment().baseUrl
        )

        // 4. Adding a custom self-hosted T3 environment strips secret #token=... from persisted bookmark
        val addResult = com.example.data.remote.T3CodePinnedWebViewManager.openPairingOrCustomUrl(
            context = context,
            label = "Mac Studio Tailscale",
            rawUrl = "https://mac-studio.tailnet.ts.net:3773/#token=super_secret_token",
            saveBookmark = true
        )
        assertTrue(addResult.isSuccess)
        val savedList = com.example.data.remote.T3CodePinnedWebViewManager.savedEnvironments.value
        assertEquals(2, savedList.size)
        val customEnv = savedList.last()
        assertEquals("Mac Studio Tailscale", customEnv.label)
        assertEquals("https://mac-studio.tailnet.ts.net:3773/", customEnv.baseUrl)
        assertFalse(customEnv.baseUrl.contains("super_secret_token"))
        assertTrue(
            com.example.data.remote.T3CodePinnedWebViewManager.isTrustedActiveOrigin(
                "https://mac-studio.tailnet.ts.net:3773"
            )
        )

        // Unsafe scheme is rejected
        val badResult = com.example.data.remote.T3CodePinnedWebViewManager.openPairingOrCustomUrl(
            context = context,
            label = "Evil",
            rawUrl = "javascript:alert(1)",
            saveBookmark = true
        )
        assertTrue(badResult.isFailure)

        // 5. Compose UI verification: Fullscreen hides toolbar, expanded mode exposes Cloud, Environments, Desktop View, and Dialog
        val fullscreenState = androidx.compose.runtime.mutableStateOf(true)
        val showEnvDialogState = androidx.compose.runtime.mutableStateOf(false)
        val boosterState = androidx.compose.runtime.mutableStateOf(true)
        var qrClicked = false

        composeTestRule.setContent {
            AntigravityPulseTheme(appearance = AppearanceSettings()) {
                com.example.ui.screens.T3CodeWebScreen(
                    domLagBoosterEnabled = boosterState.value,
                    isFullscreenBarsHidden = fullscreenState.value,
                    showEnvironmentsDialog = showEnvDialogState.value,
                    onShowEnvironmentsDialogChange = { showEnvDialogState.value = it },
                    onOpenT3QrScanner = { qrClicked = true },
                    onToggleDomBooster = { boosterState.value = it },
                    onStatusEvent = {}
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("t3_code_web_screen").assertIsDisplayed()
        composeTestRule.onNodeWithTag("t3_code_pinned_webview_container").assertIsDisplayed()
        composeTestRule.onNodeWithTag("t3_code_collapsible_toolbar").assertDoesNotExist()
        assertTrue(com.example.data.remote.T3CodePinnedWebViewManager.isPinnedInRam())

        // Expand bars -> verify toolbar controls and Desktop View toggle
        composeTestRule.runOnIdle { fullscreenState.value = false }
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("t3_code_collapsible_toolbar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("t3_code_cloud_chip").assertIsDisplayed()
        composeTestRule.onNodeWithTag("t3_code_environments_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("t3_code_qr_pair_button").assertIsDisplayed()
        composeTestRule.onNodeWithTag("t3_code_desktop_mode_toggle").assertIsDisplayed()
        composeTestRule.onNodeWithTag("t3_code_pinned_ram_badge").assertIsDisplayed()

        composeTestRule.onNodeWithTag("t3_code_desktop_mode_toggle").performClick()
        composeTestRule.onNodeWithTag("t3_code_qr_pair_button").performClick()
        composeTestRule.onNodeWithTag("t3_code_environments_button").performClick()
        composeTestRule.waitForIdle()

        assertTrue(com.example.data.remote.T3CodePinnedWebViewManager.isDesktopModeState.value)
        assertTrue(qrClicked)
        composeTestRule.onNodeWithTag("t3_code_environments_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithTag("t3_env_item_t3_cloud").assertIsDisplayed()
        composeTestRule.onNodeWithTag("t3_env_item_${customEnv.id}").assertIsDisplayed()

        // Switching back to T3 Code Cloud closes dialog and sets active env to app.t3.codes
        composeTestRule.onNodeWithTag("t3_env_item_t3_cloud").performClick()
        composeTestRule.waitForIdle()
        assertEquals(
            com.example.data.remote.T3CodePinnedWebViewManager.T3_CLOUD_ENV_ID,
            com.example.data.remote.T3CodePinnedWebViewManager.activeEnvironmentId.value
        )

        // 6. Offline continuity and full session wipe verification
        com.example.data.remote.T3CodePinnedWebViewManager.onNetworkLost()
        assertFalse(com.example.data.remote.T3CodePinnedWebViewManager.continuityState.value.isOnline)
        com.example.data.remote.T3CodePinnedWebViewManager.onNetworkRestored(autoReloadAfterLongOutage = false)
        assertTrue(com.example.data.remote.T3CodePinnedWebViewManager.continuityState.value.isOnline)

        com.example.data.remote.T3CodePinnedWebViewManager.clearSensitiveBrowsingData(context)
        assertEquals(1, com.example.data.remote.T3CodePinnedWebViewManager.savedEnvironments.value.size)
        assertFalse(com.example.data.remote.T3CodePinnedWebViewManager.isDesktopModeState.value)
    }

    @Test
    fun `RAM keep-alive coordinator formats durations, summarizes active workspaces, builds silent ongoing notification, and persists preferences`() {
        // 1. Default preferences enable 1+ hour Keep-Alive and Smooth GPU-only UI Animations
        val defaults = AppearanceSettings()
        assertTrue(defaults.isBackgroundKeepAliveEnabled)
        assertTrue(defaults.isSmoothAnimationsEnabled)

        val prefsManager = AppearancePreferencesManager(context)
        prefsManager.update {
            it.copy(
                isBackgroundKeepAliveEnabled = false,
                isSmoothAnimationsEnabled = false
            )
        }
        val reloaded = AppearancePreferencesManager(context).settings.value
        assertFalse(reloaded.isBackgroundKeepAliveEnabled)
        assertFalse(reloaded.isSmoothAnimationsEnabled)

        // Restore defaults cleanly
        prefsManager.update {
            it.copy(
                isBackgroundKeepAliveEnabled = true,
                isSmoothAnimationsEnabled = true
            )
        }

        // 2. Duration formatting for Standby, Live (<1m), minutes, exact hours, and hours+minutes
        val now = 10_000_000L
        assertEquals(
            "Standby",
            com.example.data.remote.WorkspaceMemoryKeepAliveCoordinator.formatPinnedDuration(0L, now)
        )
        assertEquals(
            "Live",
            com.example.data.remote.WorkspaceMemoryKeepAliveCoordinator.formatPinnedDuration(now - 30_000L, now)
        )
        assertEquals(
            "12m",
            com.example.data.remote.WorkspaceMemoryKeepAliveCoordinator.formatPinnedDuration(now - 12 * 60_000L, now)
        )
        assertEquals(
            "1h",
            com.example.data.remote.WorkspaceMemoryKeepAliveCoordinator.formatPinnedDuration(now - 60 * 60_000L, now)
        )
        assertEquals(
            "1h 15m",
            com.example.data.remote.WorkspaceMemoryKeepAliveCoordinator.formatPinnedDuration(now - 75 * 60_000L, now)
        )

        // 3. Active workspace summary formatting (0, <=3, and >3 items with "+N more")
        assertEquals(
            "",
            com.example.data.remote.WorkspaceMemoryKeepAliveCoordinator.buildActiveSummaryText(emptyList())
        )
        val threeStatuses = listOf(
            com.example.data.remote.PinnedWorkspaceRamStatus("remote", "Remote", true, 100L, false),
            com.example.data.remote.PinnedWorkspaceRamStatus("t3_code", "T3 Code", true, 100L, false),
            com.example.data.remote.PinnedWorkspaceRamStatus("ai_studio", "AI Studio", true, 100L, false)
        )
        assertEquals(
            "Remote, T3 Code, AI Studio",
            com.example.data.remote.WorkspaceMemoryKeepAliveCoordinator.buildActiveSummaryText(threeStatuses)
        )
        val fiveStatuses = threeStatuses + listOf(
            com.example.data.remote.PinnedWorkspaceRamStatus("github", "GitHub", true, 100L, false),
            com.example.data.remote.PinnedWorkspaceRamStatus("vercel", "Vercel", true, 100L, false),
            com.example.data.remote.PinnedWorkspaceRamStatus("supabase", "Supabase", false, 0L, false)
        )
        assertEquals(
            "Remote, T3 Code, AI Studio +2 more",
            com.example.data.remote.WorkspaceMemoryKeepAliveCoordinator.buildActiveSummaryText(fiveStatuses)
        )

        // 4. Trim-memory handler runs safely without crashing even with zero or multiple pinned workspaces
        com.example.data.remote.WorkspaceMemoryKeepAliveCoordinator.onSystemTrimMemory(
            android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND
        )
        com.example.data.remote.WorkspaceMemoryKeepAliveCoordinator.onSystemTrimMemory(
            android.content.ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN
        )

        // 5. Silent ongoing keep-alive foreground notification verification
        PulseNotificationManager.ensureChannelsCreated(context)
        val stopPendingIntent = android.app.PendingIntent.getService(
            context,
            1051,
            android.content.Intent(context, com.example.service.WorkspaceKeepAliveService::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )
        val notification = PulseNotificationManager.buildKeepAliveNotification(
            context = context,
            pinnedCount = 4,
            workspaceSummary = "Remote, T3 Code, AI Studio +1 more",
            stopPendingIntent = stopPendingIntent
        )
        assertNotNull(notification)
        assertTrue((notification.flags and android.app.Notification.FLAG_ONGOING_EVENT) != 0)
        assertEquals(PulseNotificationManager.CHANNEL_WORKSPACE_KEEP_ALIVE, notification.channelId)
        val title = notification.extras.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        val text = notification.extras.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        assertEquals("4 Workspaces Pinned in RAM", title)
        assertTrue(text?.contains("Remote, T3 Code, AI Studio +1 more") == true)
    }

    @Test
    @Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
    fun `Memory and Keep-Alive section renders controls and toggles keep-alive, battery settings, and smooth animations`() {
        val settingsState = androidx.compose.runtime.mutableStateOf(AppearanceSettings())
        var keepAliveCallbackValue: Boolean? = null
        var batterySettingsClicks = 0

        composeTestRule.setContent {
            AntigravityPulseTheme(appearance = settingsState.value) {
                com.example.ui.screens.SettingsKeepAliveSection(
                    settings = settingsState.value,
                    permissionsRefreshTick = 0,
                    bg = settingsState.value.backgroundColor,
                    surface = settingsState.value.surfaceColor,
                    elevated = settingsState.value.elevatedSurfaceColor,
                    border = settingsState.value.borderColor,
                    fg = settingsState.value.foregroundColor,
                    secondaryFg = settingsState.value.foregroundColor.copy(alpha = 0.72f),
                    accent = settingsState.value.accentColor,
                    isFrostedGlass = settingsState.value.isFrostedGlassEnabled,
                    onUpdateSettings = { transform -> settingsState.value = transform(settingsState.value) },
                    onToggleBackgroundKeepAlive = { enabled -> keepAliveCallbackValue = enabled },
                    onReleaseWorkspaceFromRam = {},
                    onOpenBatterySettings = { batterySettingsClicks++ }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("settings_keep_alive_section").assertIsDisplayed()
        composeTestRule.onNodeWithTag("keep_alive_pinned_count_badge").assertIsDisplayed()
        composeTestRule.onNodeWithTag("background_keep_alive_switch").assertIsDisplayed()
        composeTestRule.onNodeWithTag("battery_unrestricted_settings_row").assertIsDisplayed()
        composeTestRule.onNodeWithTag("smooth_animations_switch").assertIsDisplayed()

        // Toggle 1+ Hour RAM Keep-Alive OFF
        composeTestRule.onNodeWithTag("background_keep_alive_switch").performClick()
        composeTestRule.waitForIdle()
        assertFalse(settingsState.value.isBackgroundKeepAliveEnabled)
        assertEquals(false, keepAliveCallbackValue)

        // Toggle Smooth UI Animations OFF
        composeTestRule.onNodeWithTag("smooth_animations_switch").performClick()
        composeTestRule.waitForIdle()
        assertFalse(settingsState.value.isSmoothAnimationsEnabled)

        // Tap Battery Unrestricted settings row
        composeTestRule.onNodeWithTag("battery_unrestricted_settings_row").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, batterySettingsClicks)
    }

    @Test
    fun `applyWindowAttachedVisibility switches inactive WebViews to View GONE and active to VISIBLE for lag-free touch`() {
        val webView = android.webkit.WebView(context)
        com.example.data.remote.WebViewOfflineCacheHelper.applyWindowAttachedVisibility(webView, isActive = false)
        assertEquals(android.view.View.GONE, webView.visibility)
        assertFalse(webView.isEnabled)
        assertFalse(webView.isFocusable)

        // Snapshotting a hidden View.GONE WebView is skipped immediately (zero UI-thread draw stall)
        assertNull(com.example.data.remote.WebViewOfflineCacheHelper.captureVisibleSnapshotThrottled(webView))

        // Activating restores VISIBLE + touch/focus cleanly
        com.example.data.remote.WebViewOfflineCacheHelper.applyWindowAttachedVisibility(webView, isActive = true)
        assertEquals(android.view.View.VISIBLE, webView.visibility)
        assertTrue(webView.isEnabled)
        assertTrue(webView.isFocusable)

        // Keeping timers alive runs safely without throwing on both visible and hidden WebViews
        com.example.data.remote.WebViewOfflineCacheHelper.keepTimersAliveWithoutResumingHidden(webView)
        com.example.data.remote.WebViewOfflineCacheHelper.applyWindowAttachedVisibility(webView, isActive = false)
        com.example.data.remote.WebViewOfflineCacheHelper.keepTimersAliveWithoutResumingHidden(webView)
        assertEquals(android.view.View.GONE, webView.visibility)
    }

    @Test
    fun `TouchSafeWebViewContainer requests parent disallow intercept when active and drops touches when hidden`() {
        var disallowInterceptCalls = 0
        val trackingParent = object : android.widget.FrameLayout(context) {
            override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
                super.requestDisallowInterceptTouchEvent(disallowIntercept)
                if (disallowIntercept) disallowInterceptCalls++
            }
        }
        val container = com.example.ui.components.TouchSafeWebViewContainer(context)
        val webView = android.webkit.WebView(context)
        com.example.ui.components.attachWebViewToContainer(container, webView)
        trackingParent.addView(container)

        com.example.data.remote.WebViewOfflineCacheHelper.configureScrollResponsiveWebView(webView)
        assertEquals(android.view.View.LAYER_TYPE_NONE, webView.layerType)
        assertFalse(webView.isNestedScrollingEnabled)
        assertEquals(android.view.View.OVER_SCROLL_NEVER, webView.overScrollMode)
        assertFalse(webView.isVerticalScrollBarEnabled)
        assertFalse(webView.isHorizontalScrollBarEnabled)

        com.example.data.remote.WebViewOfflineCacheHelper.applyWindowAttachedVisibility(webView, isActive = true)
        assertTrue(container.isActiveWorkspace)
        assertEquals(android.view.View.VISIBLE, container.visibility)

        val downEvent = android.view.MotionEvent.obtain(0L, 10L, android.view.MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        try {
            assertFalse(container.onInterceptTouchEvent(downEvent))
            container.dispatchTouchEvent(downEvent)
            assertTrue(disallowInterceptCalls >= 1)
        } finally {
            downEvent.recycle()
        }

        val callsWhileActive = disallowInterceptCalls
        com.example.data.remote.WebViewOfflineCacheHelper.applyWindowAttachedVisibility(webView, isActive = false)
        assertFalse(container.isActiveWorkspace)
        assertEquals(android.view.View.GONE, container.visibility)
        assertEquals(android.view.View.GONE, webView.visibility)

        val inactiveEvent = android.view.MotionEvent.obtain(0L, 20L, android.view.MotionEvent.ACTION_DOWN, 50f, 50f, 0)
        try {
            assertTrue(container.onInterceptTouchEvent(inactiveEvent))
            assertFalse(container.dispatchTouchEvent(inactiveEvent))
            assertEquals(callsWhileActive, disallowInterceptCalls)
        } finally {
            inactiveEvent.recycle()
        }
    }

    @Test
    fun `PulseBrowserManager tracks activeTabCanGoBack safely across tab lifecycle`() {
        val originalTabs = com.example.data.remote.PulseBrowserManager.tabs.value
        assertFalse(com.example.data.remote.PulseBrowserManager.activeTabCanGoBack.value)
        val extraTab = com.example.data.remote.PulseBrowserManager.openNewTab("https://example.com")
        assertFalse(com.example.data.remote.PulseBrowserManager.activeTabCanGoBack.value)
        com.example.data.remote.PulseBrowserManager.closeTab(extraTab.id)
        com.example.data.remote.PulseBrowserManager.selectTab(originalTabs.first().id)
        assertFalse(com.example.data.remote.PulseBrowserManager.activeTabCanGoBack.value)
    }
}

