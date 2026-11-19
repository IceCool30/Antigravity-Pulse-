package com.example

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Language
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AgentSessionEntity
import com.example.data.local.AntigravityDatabase
import com.example.data.local.FileDiffEntity
import com.example.data.local.SessionMessageEntity
import com.example.data.local.TerminalLogEntity
import com.example.data.remote.AntigravityBridgeClient
import com.example.data.remote.BridgeConnectionState
import com.example.data.remote.BridgeTelemetry
import com.example.data.remote.RetainedWebViewManager
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
@Config(sdk = [36])
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
    fun `verify app name and clean database with zero dummy data`() = runBlocking {
        val appName = context.getString(R.string.app_name)
        assertEquals("Antigravity Pulse", appName)

        val dao = database.dao()
        assertTrue(dao.getAllServerProfilesOnce().isEmpty())
        assertTrue(dao.getAllSessionsOnce().isEmpty())
        assertTrue(dao.observeAllSessionMessages().first().isEmpty())
        assertTrue(dao.observeAllDiffs().first().isEmpty())
        assertTrue(dao.observeAllTerminalLogs().first().isEmpty())
        assertTrue(dao.observeCopilotMessages().first().isEmpty())
    }

    @Test
    fun `stress test 500 rapid terminal logs and session messages in Room`() = runBlocking {
        val dao = database.dao()
        val sessionId = "stress_sess_1"
        dao.upsertAgentSession(
            AgentSessionEntity(
                id = sessionId,
                serverId = "srv_1",
                workspaceName = "stress-workspace",
                branchName = "main",
                status = "ACTIVE",
                lastSummary = "Stress testing 500 concurrent logs",
                messageCount = 500,
                updatedAtEpoch = System.currentTimeMillis()
            )
        )

        // Rapid burst of 500 terminal logs and 200 session messages
        for (i in 1..500) {
            dao.insertTerminalLog(
                TerminalLogEntity(
                    id = "stress_term_$i",
                    sessionId = sessionId,
                    command = "echo 'stress-line-$i'",
                    output = "stress-line-$i\nexit 0",
                    exitCode = if (i % 25 == 0) 1 else 0,
                    durationMs = i % 15,
                    timestampEpoch = 1_000_000L + i
                )
            )
        }

        for (j in 1..200) {
            dao.insertSessionMessage(
                SessionMessageEntity(
                    id = "stress_msg_$j",
                    sessionId = sessionId,
                    senderRole = if (j % 2 == 0) "AGENT" else "USER",
                    content = "High frequency message frame #$j",
                    timestampEpoch = 2_000_000L + j
                )
            )
        }

        for (k in 1..50) {
            dao.upsertFileDiffs(
                listOf(
                    FileDiffEntity(
                        id = "stress_diff_$k",
                        sessionId = sessionId,
                        filePath = "app/src/main/java/com/example/File$k.kt",
                        language = "kotlin",
                        additions = 12,
                        deletions = 3,
                        unifiedPatch = "@@ -1,3 +1,12 @@\n+val x = $k",
                        status = "MODIFIED"
                    )
                )
            )
        }

        val logs = dao.observeAllTerminalLogs().first()
        val messages = dao.observeAllSessionMessages().first()
        val diffs = dao.observeAllDiffs().first()

        assertEquals(500, logs.size)
        assertEquals(200, messages.size)
        assertEquals(50, diffs.size)

        // Verify clear all operations leave 0 residual records
        dao.clearAllTerminalLogs()
        dao.clearAllSessionMessages()
        dao.clearAllFileDiffs()
        dao.clearAllAgentSessions()
        assertTrue(dao.observeAllTerminalLogs().first().isEmpty())
        assertTrue(dao.observeAllSessionMessages().first().isEmpty())
        assertTrue(dao.observeAllDiffs().first().isEmpty())
        assertTrue(dao.getAllSessionsOnce().isEmpty())
    }

    @Test
    fun `security audit url scheme validation and bridge disconnected shell handling`() = runBlocking {
        // Verify WebView URL scheme validator blocks file://, javascript:, content://, intent://
        assertTrue(RetainedWebViewManager.isSafeHttpUrl("https://workstation.example.com:8443"))
        assertTrue(RetainedWebViewManager.isSafeHttpUrl("http://192.168.1.50:8443"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("file:///etc/passwd"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("javascript:alert(1)"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("content://com.example.provider/secret"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl("intent://scan/#Intent;end"))
        assertFalse(RetainedWebViewManager.isSafeHttpUrl(""))

        // Verify AntigravityBridgeClient starts DISCONNECTED and rejects invalid schemes
        val bridge = AntigravityBridgeClient()
        assertEquals(BridgeConnectionState.DISCONNECTED, bridge.telemetry.value.state)
        assertEquals("", bridge.telemetry.value.activeEndpoint)

        bridge.connectToEndpoint("javascript:evil()", "") {}
        assertEquals(BridgeConnectionState.ERROR, bridge.telemetry.value.state)

        // Reset to blank and verify executeShellCommandOverBridge returns exitCode 127 cleanly
        bridge.connectToEndpoint("", "") {}
        assertEquals(BridgeConnectionState.DISCONNECTED, bridge.telemetry.value.state)

        val shellRes = bridge.executeShellCommandOverBridge("sess_1", "uname -a")
        assertEquals("uname -a", shellRes.command)
        assertEquals(127, shellRes.exitCode)
        assertTrue(shellRes.output.contains("No remote host configured"))
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
    fun `QR pairing payload parser handles JSON deep-link HTTPS and invalid payloads`() {
        val jsonPayload = parseAntigravityPairingPayload(
            """{"name":"Workstation Pro","url":"https://dev.tailnet.ts.net:8443","token":"tok_abc123"}"""
        )
        assertNotNull(jsonPayload)
        assertEquals("Workstation Pro", jsonPayload!!.hostName)
        assertEquals("https://dev.tailnet.ts.net:8443", jsonPayload.endpointUrl)
        assertEquals("tok_abc123", jsonPayload.authToken)

        val deepLink = parseAntigravityPairingPayload(
            "antigravity://pair?url=wss%3A%2F%2F192.168.1.42%3A8443&token=secret99&name=LabBox"
        )
        assertNotNull(deepLink)
        assertEquals("LabBox", deepLink!!.hostName)
        assertEquals("wss://192.168.1.42:8443", deepLink.endpointUrl)
        assertEquals("secret99", deepLink.authToken)

        val directHttps = parseAntigravityPairingPayload(
            "https://my-host.example.com:8443/remote?token=xyz789"
        )
        assertNotNull(directHttps)
        assertEquals("https://my-host.example.com:8443/remote?token=xyz789", directHttps!!.endpointUrl)
        assertEquals("xyz789", directHttps.authToken)

        assertNull(parseAntigravityPairingPayload("not a valid url"))
        assertNull(parseAntigravityPairingPayload(""))
    }

    @Test
    fun `bridge offline queue exponential backoff and remote file JSON parser work reliably`() {
        // Exponential backoff: 2s -> 4s -> 8s -> 16s -> 30s cap
        assertEquals(2_000L, AntigravityBridgeClient.computeBackoffDelayMs(1))
        assertEquals(4_000L, AntigravityBridgeClient.computeBackoffDelayMs(2))
        assertEquals(8_000L, AntigravityBridgeClient.computeBackoffDelayMs(3))
        assertEquals(16_000L, AntigravityBridgeClient.computeBackoffDelayMs(4))
        assertEquals(30_000L, AntigravityBridgeClient.computeBackoffDelayMs(5))
        assertEquals(30_000L, AntigravityBridgeClient.computeBackoffDelayMs(10))

        // Offline JSON-RPC queue tracks queued commands when socket is disconnected
        val bridge = AntigravityBridgeClient()
        assertFalse(bridge.sendJsonRpcMessage("agent.sendPrompt", mapOf("prompt" to "hello")))
        assertEquals(1, bridge.telemetry.value.queuedCommandsCount)
        assertFalse(bridge.sendTerminalInterrupt("sess_1"))
        assertEquals(2, bridge.telemetry.value.queuedCommandsCount)

        bridge.setAutoReconnectEnabled(false)
        assertFalse(bridge.telemetry.value.autoReconnectEnabled)
        bridge.clearOfflineQueue()
        assertEquals(0, bridge.telemetry.value.queuedCommandsCount)

        // Remote file entries parser sorts directories first, then alphabetically
        val json = """
            {"result":{"entries":[
              {"name":"README.md","path":"README.md","isDirectory":false,"sizeBytes":1024},
              {"name":"src","path":"src","isDirectory":true},
              {"name":"build.gradle.kts","path":"build.gradle.kts","isDirectory":false,"sizeBytes":2048}
            ]}}
        """.trimIndent()
        val entries = AntigravityBridgeClient.parseRemoteFileEntriesJson(json, ".")
        assertEquals(3, entries.size)
        assertEquals("src", entries[0].name)
        assertTrue(entries[0].isDirectory)
        assertEquals("build.gradle.kts", entries[1].name)
        assertEquals("README.md", entries[2].name)
    }

    @Test
    fun `biometric lock approval notifications auto reconnect and terminal macros persist`() {
        val manager = AppearancePreferencesManager(context)
        manager.update {
            it.copy(
                isBiometricLockEnabled = true,
                isApprovalNotificationsEnabled = false,
                isAutoReconnectEnabled = false,
                savedTerminalMacros = listOf("git status --short", "gradle :app:testDebugUnitTest")
            )
        }

        // Reload fresh manager instance from SharedPreferences
        val reloaded = AppearancePreferencesManager(context).settings.value
        assertTrue(reloaded.isBiometricLockEnabled)
        assertFalse(reloaded.isApprovalNotificationsEnabled)
        assertFalse(reloaded.isAutoReconnectEnabled)
        assertEquals(
            listOf("git status --short", "gradle :app:testDebugUnitTest"),
            reloaded.savedTerminalMacros
        )
    }

    @Test
    fun `app defaults to pure black OLED background fullscreen and developer profile persists`() {
        val defaultSettings = AppearanceSettings()
        assertEquals("#000000", defaultSettings.backgroundHex)
        assertEquals("#000000", OfficialAntigravityPresets.first().backgroundHex)
        assertTrue(defaultSettings.isFullscreenBarsHidden)
        assertFalse(defaultSettings.showStartupGuideOnLaunch)
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
                developerName = "Solomon Bukie",
                developerGithub = "solomondev",
                isMaterialYouDynamicEnabled = true,
                isFrostedGlassEnabled = false
            )
        }
        val reloaded = AppearancePreferencesManager(context).settings.value
        assertEquals(0.85f, reloaded.floatingHandleXFraction, 0.001f)
        assertEquals(0.72f, reloaded.floatingHandleYFraction, 0.001f)
        assertEquals("Solomon Bukie", reloaded.developerName)
        assertEquals("solomondev", reloaded.developerGithub)
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
}
