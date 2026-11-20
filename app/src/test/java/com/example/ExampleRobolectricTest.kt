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
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AntigravityDatabase
import com.example.data.local.ServerProfileEntity
import com.example.data.remote.RetainedWebViewManager
import com.example.notifications.PulseNotificationManager
import com.example.ui.screens.parseAntigravityPairingPayload
import com.example.ui.theme.AntigravityPulseTheme
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
        assertEquals("#000000", defaultSettings.backgroundHex)
        assertEquals("#000000", OfficialAntigravityPresets.first().backgroundHex)
        assertTrue(defaultSettings.isFullscreenBarsHidden)
        assertEquals(0f, defaultSettings.floatingHandleXFraction, 0.001f)
        assertEquals(0.42f, defaultSettings.floatingHandleYFraction, 0.001f)
        assertEquals("Solomon Ogar Bukie", defaultSettings.developerName)
        assertEquals("solomonogarbukie@gmail.com", defaultSettings.developerEmail)
        assertEquals("IceCool30", defaultSettings.developerGithub)
        assertTrue(defaultSettings.isMaterialYouDynamicEnabled)
        assertTrue(defaultSettings.isFrostedGlassEnabled)

        val manager = AppearancePreferencesManager(context)
        manager.update {
            it.copy(
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

        // Picking a manual preset cleanly switches off Material You dynamic accent so the preset color applies
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
}
