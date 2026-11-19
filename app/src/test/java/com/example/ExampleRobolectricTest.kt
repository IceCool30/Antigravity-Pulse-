package com.example

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
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
import com.example.ui.screens.TerminalView
import com.example.ui.screens.parseAnsiToAnnotatedString
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
    fun `TerminalView composable renders real-time logs and executes commands`() {
        val executedCommands = mutableListOf<String>()
        var clearedCount = 0

        val liveLogs = listOf(
            TerminalLogEntity(
                id = "log_1",
                sessionId = "sess_1",
                command = "gradle :app:assembleDebug",
                output = "BUILD SUCCESSFUL in 4s\n32 actionable tasks: 32 executed",
                exitCode = 0,
                durationMs = 4120,
                timestampEpoch = System.currentTimeMillis()
            )
        )

        composeTestRule.setContent {
            AntigravityPulseTheme(appearance = AppearanceSettings()) {
                TerminalView(
                    terminalLogs = liveLogs,
                    telemetry = BridgeTelemetry(
                        state = BridgeConnectionState.CONNECTED_LIVE_SOCKET,
                        activeEndpoint = "wss://workstation.local:8443",
                        latencyMs = 11
                    ),
                    isExecutingCommand = false,
                    onExecuteCommand = { cmd -> executedCommands.add(cmd) },
                    onClearTerminal = { clearedCount++ },
                    onOpenServerConfig = {}
                )
            }
        }

        composeTestRule.waitForIdle()

        // Verify TerminalView LazyColumn and real-time command output are displayed
        composeTestRule.onNodeWithTag("terminal_view_root").assertIsDisplayed()
        composeTestRule.onNodeWithTag("terminal_output_list").assertIsDisplayed()
        composeTestRule.onNodeWithText("$ gradle :app:assembleDebug").assertIsDisplayed()
        composeTestRule.onNodeWithText("exit 0 • 4120ms").assertIsDisplayed()

        // Enter a new shell command and click Run
        composeTestRule.onNodeWithTag("terminal_command_input")
            .performTextInput("git status --short")
        composeTestRule.onNodeWithTag("run_terminal_command_button")
            .performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("git status --short"), executedCommands)

        // Click Clear terminal button
        composeTestRule.onNodeWithTag("clear_terminal_button").performClick()
        composeTestRule.waitForIdle()
        assertEquals(1, clearedCount)
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
    fun `ANSI parser strips escape sequences and applies color and bold spans`() {
        val raw = "\u001B[1;31mERROR:\u001B[0m Build failed • \u001B[32m2 tests passed\u001B[0m"
        val parsed = parseAnsiToAnnotatedString(raw, Color.White)
        assertEquals("ERROR: Build failed • 2 tests passed", parsed.text)
        assertTrue(parsed.spanStyles.size >= 3)
        assertFalse(parsed.text.contains("\u001B"))
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
    fun `TerminalView accessory key bar Ctrl-C and Explain Error with Copilot work interactively`() {
        var ctrlCCount = 0
        var debuggedCommand = ""
        var debuggedOutput = ""

        val failedLog = listOf(
            TerminalLogEntity(
                id = "err_log_1",
                sessionId = "sess_1",
                command = "npm test",
                output = "\u001B[31mFAIL\u001B[0m src/bridge.test.ts: AssertionError",
                exitCode = 1,
                durationMs = 890,
                timestampEpoch = System.currentTimeMillis()
            )
        )

        composeTestRule.setContent {
            AntigravityPulseTheme(appearance = AppearanceSettings()) {
                TerminalView(
                    terminalLogs = failedLog,
                    telemetry = BridgeTelemetry(
                        state = BridgeConnectionState.CONNECTED_LIVE_SOCKET,
                        activeEndpoint = "wss://workstation.local:8443"
                    ),
                    isExecutingCommand = false,
                    onExecuteCommand = {},
                    onSendCtrlC = { ctrlCCount++ },
                    onClearTerminal = {},
                    onDebugErrorWithCopilot = { cmd, out ->
                        debuggedCommand = cmd
                        debuggedOutput = out
                    }
                )
            }
        }

        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("terminal_accessory_key_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("accessory_key_ctrl_c").performClick()
        assertEquals(1, ctrlCCount)

        // Verify Explain & Fix with Copilot button appears on failed exitCode != 0
        composeTestRule.onNodeWithTag("explain_error_copilot_err_log_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("explain_error_copilot_err_log_1").performClick()
        assertEquals("npm test", debuggedCommand)
        assertTrue(debuggedOutput.contains("AssertionError"))
    }

    @Test
    fun `app defaults to fullscreen with persisted movable side panel position and clean mobile UA`() {
        val defaultSettings = AppearanceSettings()
        assertTrue(defaultSettings.isFullscreenBarsHidden)
        assertFalse(defaultSettings.showStartupGuideOnLaunch)
        assertEquals(0f, defaultSettings.floatingHandleXFraction, 0.001f)
        assertEquals(0.42f, defaultSettings.floatingHandleYFraction, 0.001f)

        val manager = AppearancePreferencesManager(context)
        manager.update {
            it.copy(
                floatingHandleXFraction = 0.85f,
                floatingHandleYFraction = 0.72f
            )
        }
        val reloaded = AppearancePreferencesManager(context).settings.value
        assertEquals(0.85f, reloaded.floatingHandleXFraction, 0.001f)
        assertEquals(0.72f, reloaded.floatingHandleYFraction, 0.001f)

        val rawWvUa = "Mozilla/5.0 (Linux; Android 15; Pixel 9; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/131.0.0.0 Mobile Safari/537.36"
        val cleanUa = RetainedWebViewManager.buildCleanMobileUserAgent(rawWvUa)
        assertFalse(cleanUa.contains("; wv"))
        assertFalse(cleanUa.contains("Version/4.0 "))
        assertTrue(cleanUa.contains("Chrome/131.0.0.0"))
    }

    @Test
    fun `CopilotViewModel defaults to billing-free Gemini 3_8 Flash`() {
        val copilotVm = com.example.viewmodel.CopilotViewModel(database.dao())
        assertEquals(
            com.example.viewmodel.GeminiCopilotModel.GEMINI_3_8_FLASH,
            copilotVm.selectedModel.value
        )
        assertEquals("gemini-3.8-flash", copilotVm.selectedModel.value.modelId)
    }

    @Test
    fun `MovableFloatingControlPanel opens on tap and triggers recenter and tab actions`() {
        var expanded = false
        var recentered = false
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
                        com.example.ui.components.FloatingPanelTabItem(1, "AI Copilot", Icons.Default.AutoAwesome, "nav_tab_copilot"),
                        com.example.ui.components.FloatingPanelTabItem(2, "Cloud Sync", Icons.Default.CloudSync, "nav_tab_sync")
                    ),
                    selectedTabIndex = selectedTab,
                    activeHostLabel = "My Workstation",
                    surface = Color(0xFF141418),
                    elevated = Color(0xFF1E1E24),
                    border = Color(0xFF2D2D36),
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
                    onRecenterWebLayout = { recentered = true },
                    onReloadWeb = {},
                    onOpenLiveVoice = {},
                    onShowBars = {},
                    onOpenSettings = {},
                    onOpenGuide = {}
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
        composeTestRule.onNodeWithTag("floating_quick_recenter_layout").performClick()
        composeTestRule.waitForIdle()

        assertTrue(recentered)
        assertFalse(expanded)
    }
}
