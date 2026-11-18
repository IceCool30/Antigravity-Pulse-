package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        ServerProfileEntity::class,
        AgentSessionEntity::class,
        SessionMessageEntity::class,
        FileDiffEntity::class,
        TerminalLogEntity::class,
        CopilotMessageEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AntigravityDatabase : RoomDatabase() {
    abstract fun dao(): AntigravityDao

    companion object {
        @Volatile
        private var INSTANCE: AntigravityDatabase? = null

        fun getInstance(context: Context): AntigravityDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AntigravityDatabase::class.java,
                    "antigravity_pulse.db"
                )
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}

object InitialSeedData {
    suspend fun ensureSeeded(dao: AntigravityDao) {
        if (dao.getAllServerProfilesOnce().isNotEmpty()) return

        val now = System.currentTimeMillis()
        val profiles = listOf(
            ServerProfileEntity(
                id = "srv_hybrid_sim",
                name = "Antigravity Workstation (Hybrid Bridge)",
                endpointUrl = "https://antigravity.local.tailnet.ts.net:8443",
                connectionMode = "HYBRID_BRIDGE",
                authToken = "ag_standalone_oauth_9f82a1",
                keepAlivePinned = true,
                lastLatencyMs = 9,
                isSelected = true,
                updatedAtEpoch = now
            ),
            ServerProfileEntity(
                id = "srv_cloud_box",
                name = "Linux Cloud Box (--standalone)",
                endpointUrl = "wss://ag-headless.dev.internal:42100/ws",
                connectionMode = "DIRECT_TUNNEL",
                authToken = "jetski_token_7741c",
                keepAlivePinned = true,
                lastLatencyMs = 24,
                isSelected = false,
                updatedAtEpoch = now - 60_000L
            )
        )
        dao.upsertServerProfiles(profiles)

        val sessions = listOf(
            AgentSessionEntity(
                id = "sess_bridge_core",
                serverId = "srv_hybrid_sim",
                workspaceName = "antigravity-remote-bridge",
                branchName = "feat/zero-reload-websocket",
                status = "AWAITING_APPROVAL",
                lastSummary = "Awaiting approval: language_server --standalone --port 42100",
                messageCount = 5,
                savedScrollIndex = 0,
                draftPrompt = "",
                updatedAtEpoch = now
            ),
            AgentSessionEntity(
                id = "sess_mcp_chrome",
                serverId = "srv_hybrid_sim",
                workspaceName = "chrome-devtools-mcp-agent",
                branchName = "main",
                status = "ACTIVE",
                lastSummary = "Verified remote DOM snapshot & reduced frame latency by 78%",
                messageCount = 3,
                savedScrollIndex = 0,
                draftPrompt = "",
                updatedAtEpoch = now - 180_000L
            ),
            AgentSessionEntity(
                id = "sess_api_gateway",
                serverId = "srv_cloud_box",
                workspaceName = "payments-kotlin-microservice",
                branchName = "fix/retry-backoff-policy",
                status = "COMPLETED",
                lastSummary = "All 42 unit tests passing after coroutine timeout patch",
                messageCount = 3,
                savedScrollIndex = 0,
                draftPrompt = "",
                updatedAtEpoch = now - 900_000L
            )
        )
        dao.upsertAgentSessions(sessions)

        val messages = listOf(
            SessionMessageEntity(
                id = "msg_1",
                sessionId = "sess_bridge_core",
                senderRole = "USER",
                content = "The browser remote app keeps reloading whenever I minimize my phone and lags on long threads. Can we pin the WebSocket stream and buffer delta updates?",
                timestampEpoch = now - 240_000L
            ),
            SessionMessageEntity(
                id = "msg_2",
                sessionId = "sess_bridge_core",
                senderRole = "THOUGHT",
                content = "Analyzing Antigravity language_server session multiplexer. By caching conversation frames in local SQLite and pinning the WebSocket heartbeat at 15s intervals, mobile clients open threads in 0ms without re-hydrating the full web DOM.",
                durationMs = 420,
                timestampEpoch = now - 210_000L
            ),
            SessionMessageEntity(
                id = "msg_3",
                sessionId = "sess_bridge_core",
                senderRole = "AGENT",
                content = "I have refactored `SessionStreamMultiplexer.ts` and `BridgeKeepAlive.kt` to stream lightweight delta frames and persist every message locally in Room. I also staged a terminal verification command below.",
                commandOrPath = "src/bridge/SessionStreamMultiplexer.ts",
                timestampEpoch = now - 160_000L
            ),
            SessionMessageEntity(
                id = "msg_4",
                sessionId = "sess_bridge_core",
                senderRole = "TOOL_APPROVAL",
                content = "Antigravity Agent requests permission to restart the standalone language server bridge with delta frame compression enabled:",
                commandOrPath = "language_server --standalone --port 42100 --enable-delta-compression",
                approvalStatus = "PENDING",
                timestampEpoch = now - 60_000L
            ),
            SessionMessageEntity(
                id = "msg_5",
                sessionId = "sess_mcp_chrome",
                senderRole = "USER",
                content = "/browser inspect localhost:3000 dashboard layout and measure Time-to-Interactive",
                timestampEpoch = now - 500_000L
            ),
            SessionMessageEntity(
                id = "msg_6",
                sessionId = "sess_mcp_chrome",
                senderRole = "AGENT",
                content = "Connected to Chrome DevTools MCP on port 9222. Captured layout metrics: First Contentful Paint is 140ms and WebSocket bridge round-trip is 9ms.",
                commandOrPath = "chrome://inspect/#remote-debugging",
                timestampEpoch = now - 450_000L
            ),
            SessionMessageEntity(
                id = "msg_7",
                sessionId = "sess_api_gateway",
                senderRole = "USER",
                content = "Run the retry policy test suite and verify zero flaky timeouts.",
                timestampEpoch = now - 1_000_000L
            ),
            SessionMessageEntity(
                id = "msg_8",
                sessionId = "sess_api_gateway",
                senderRole = "AGENT",
                content = "Executed `./gradlew test`. All 42 tests passed in 3.4s with exponential backoff verified.",
                commandOrPath = "src/main/kotlin/com/gateway/RetryPolicy.kt",
                timestampEpoch = now - 950_000L
            )
        )
        dao.insertSessionMessages(messages)

        val diffs = listOf(
            FileDiffEntity(
                id = "diff_1",
                sessionId = "sess_bridge_core",
                filePath = "src/bridge/SessionStreamMultiplexer.ts",
                language = "TypeScript",
                additions = 14,
                deletions = 4,
                unifiedPatch = """
@@ -42,8 +42,18 @@ export class SessionStreamMultiplexer {
-  public async sendFullHistory(socket: WebSocket) {
-    const allEvents = await this.store.loadAll();
-    socket.send(JSON.stringify({ type: 'FULL_SYNC', payload: allEvents }));
-  }
+  public async streamDeltaSince(socket: WebSocket, lastSeqId: number) {
+    const deltaEvents = await this.store.loadSince(lastSeqId);
+    socket.send(JSON.stringify({
+      type: 'DELTA_SYNC',
+      seq: this.currentSeq,
+      kompressed: true,
+      events: deltaEvents
+    }));
+  }
+
+  public enableHeartbeatPinning(intervalMs: number = 15000) {
+    this.heartbeatTimer = setInterval(() => this.broadcastPing(), intervalMs);
+  }
                """.trimIndent(),
                status = "MODIFIED",
                updatedAtEpoch = now - 120_000L
            ),
            FileDiffEntity(
                id = "diff_2",
                sessionId = "sess_bridge_core",
                filePath = "android-client/BridgeWebViewHolder.kt",
                language = "Kotlin",
                additions = 11,
                deletions = 2,
                unifiedPatch = """
@@ -18,6 +18,15 @@ object BridgeWebViewHolder {
-  fun createFreshWebView(context: Context): WebView {
-    return WebView(context)
+  fun getOrCreatePinnedWebView(appContext: Context): WebView {
+    return retainedWebView ?: WebView(appContext).apply {
+      settings.javaScriptEnabled = true
+      settings.domStorageEnabled = true
+      settings.cacheMode = WebSettings.LOAD_DEFAULT
+      setLayerType(View.LAYER_TYPE_HARDWARE, null)
+      retainedWebView = this
+    }
   }
                """.trimIndent(),
                status = "ADDED",
                updatedAtEpoch = now - 90_000L
            ),
            FileDiffEntity(
                id = "diff_3",
                sessionId = "sess_mcp_chrome",
                filePath = "mcp/ChromeDevToolsBridge.ts",
                language = "TypeScript",
                additions = 8,
                deletions = 1,
                unifiedPatch = """
@@ -88,4 +88,11 @@ export async function attachSubagentBrowser(port = 9222) {
-  await page.goto(targetUrl);
+  await page.setBypassCSP(true);
+  await page.goto(targetUrl, { waitUntil: 'domcontentloaded' });
+  await page.evaluate(() => {
+    document.documentElement.style.scrollBehavior = 'auto';
+  });
                """.trimIndent(),
                status = "MODIFIED",
                updatedAtEpoch = now - 300_000L
            )
        )
        dao.upsertFileDiffs(diffs)

        val terminalLogs = listOf(
            TerminalLogEntity(
                id = "term_1",
                sessionId = "sess_bridge_core",
                command = "language_server --version && ps aux | grep language_server",
                output = "Google Antigravity Language Server v2.6.4-standalone (linux-x64)\nroot     14892  1.2  3.8 1842900 312400 ? Ssl 01:10 0:18 /opt/antigravity/bin/language_server --standalone --port 42100",
                exitCode = 0,
                durationMs = 18,
                timestampEpoch = now - 200_000L
            ),
            TerminalLogEntity(
                id = "term_2",
                sessionId = "sess_bridge_core",
                command = "cat ~/.gemini/jetski-standalone-oauth-token | sha256sum",
                output = "9f82a17c4b8e201948d7e6f5a3b2c1d0e9f8a7b6c5d4e3f2a1b0c9d8e7f6a5b4  - (OAuth Token Active & Verified)",
                exitCode = 0,
                durationMs = 11,
                timestampEpoch = now - 140_000L
            )
        )
        dao.insertTerminalLogs(terminalLogs)

        dao.insertCopilotMessage(
            CopilotMessageEntity(
                id = "cop_welcome",
                senderRole = "MODEL",
                text = "Welcome to **Antigravity Pulse Copilot**. I am connected via Firebase AI Logic with multi-model switching (`gemini-4-argon`, `gemini-3.8-flash`, `gemini-3.5-flash-lite`) and **Nano Banana 2.1** (`gemini-nano-banana-2.1`) for UI asset generation & image editing.\n\nDraft complex prompts or UI mockups here, then tap **Send to Antigravity Agent** to dispatch them directly into your active remote session with zero browser lag.",
                modelUsed = "gemini-4-argon",
                rolePersona = "Antigravity Prompt Architect",
                timestampEpoch = now - 300_000L
            )
        )
    }
}
