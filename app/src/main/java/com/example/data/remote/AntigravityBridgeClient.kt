package com.example.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class BridgeConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED_LIVE_SOCKET,
    CONNECTED_HTTP_BRIDGE,
    ERROR
}

data class RemoteShellExecutionResult(
    val command: String,
    val output: String,
    val exitCode: Int,
    val durationMs: Long
)

data class RemoteFileEntry(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val sizeBytes: Long = 0L
)

data class BridgeTelemetry(
    val state: BridgeConnectionState = BridgeConnectionState.DISCONNECTED,
    val activeEndpoint: String = "",
    val latencyMs: Int = 0,
    val webViewPinnedInRam: Boolean = true,
    val domLagBoosterEnabled: Boolean = true,
    val deltaFramesReceived: Int = 0,
    val lastBridgeEvent: String = "No remote host connected. Add your Antigravity Remote URL to begin.",
    val isReconnecting: Boolean = false,
    val reconnectAttempt: Int = 0,
    val queuedCommandsCount: Int = 0,
    val autoReconnectEnabled: Boolean = true
)

class AntigravityBridgeClient {
    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .pingInterval(15, TimeUnit.SECONDS)
        .build()

    private val bridgeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var reconnectJob: Job? = null
    private var activeWebSocket: WebSocket? = null
    private var currentAuthToken: String = ""
    private var currentEndpointUrl: String = ""
    private var currentOnRemoteEvent: ((String) -> Unit)? = null

    private val offlineQueueLock = Any()
    private val offlineJsonRpcQueue = ArrayDeque<String>()

    private val _telemetry = MutableStateFlow(BridgeTelemetry())
    val telemetry: StateFlow<BridgeTelemetry> = _telemetry.asStateFlow()

    fun setAutoReconnectEnabled(enabled: Boolean) {
        if (!enabled) {
            reconnectJob?.cancel()
            reconnectJob = null
        }
        _telemetry.value = _telemetry.value.copy(
            autoReconnectEnabled = enabled,
            isReconnecting = if (!enabled) false else _telemetry.value.isReconnecting,
            reconnectAttempt = if (!enabled) 0 else _telemetry.value.reconnectAttempt,
            lastBridgeEvent = if (enabled) {
                "Auto-reconnect enabled (exponential backoff 2s..30s)"
            } else {
                "Auto-reconnect paused"
            }
        )
    }

    fun clearOfflineQueue() {
        synchronized(offlineQueueLock) {
            offlineJsonRpcQueue.clear()
        }
        _telemetry.value = _telemetry.value.copy(queuedCommandsCount = 0)
    }

    private fun enqueueOfflinePayload(payload: String): Int {
        val count = synchronized(offlineQueueLock) {
            if (offlineJsonRpcQueue.size >= MAX_OFFLINE_QUEUE_SIZE) {
                offlineJsonRpcQueue.removeFirst()
            }
            offlineJsonRpcQueue.addLast(payload)
            offlineJsonRpcQueue.size
        }
        _telemetry.value = _telemetry.value.copy(queuedCommandsCount = count)
        return count
    }

    private fun flushOfflineQueue(webSocket: WebSocket): Int {
        val pending = synchronized(offlineQueueLock) {
            val copy = offlineJsonRpcQueue.toList()
            offlineJsonRpcQueue.clear()
            copy
        }
        var flushed = 0
        val failed = mutableListOf<String>()
        for (item in pending) {
            if (webSocket.send(item)) {
                flushed++
            } else {
                failed.add(item)
            }
        }
        val remaining = synchronized(offlineQueueLock) {
            for (item in failed.asReversed()) {
                offlineJsonRpcQueue.addFirst(item)
            }
            offlineJsonRpcQueue.size
        }
        _telemetry.value = _telemetry.value.copy(queuedCommandsCount = remaining)
        return flushed
    }

    fun connectToEndpoint(
        endpointUrl: String,
        authToken: String,
        onRemoteEvent: (String) -> Unit
    ) {
        reconnectJob?.cancel()
        reconnectJob = null
        activeWebSocket?.cancel()
        activeWebSocket = null

        val normalizedUrl = endpointUrl.trim()
        currentEndpointUrl = normalizedUrl
        currentAuthToken = authToken.trim()
        currentOnRemoteEvent = onRemoteEvent

        if (normalizedUrl.isBlank()) {
            clearOfflineQueue()
            _telemetry.value = _telemetry.value.copy(
                state = BridgeConnectionState.DISCONNECTED,
                activeEndpoint = "",
                latencyMs = 0,
                isReconnecting = false,
                reconnectAttempt = 0,
                lastBridgeEvent = "Disconnected. Configure a remote host URL to connect."
            )
            return
        }

        openWebSocketInternal(normalizedUrl, attempt = 0, onRemoteEvent = onRemoteEvent)
    }

    private fun openWebSocketInternal(
        normalizedUrl: String,
        attempt: Int,
        onRemoteEvent: (String) -> Unit
    ) {
        _telemetry.value = _telemetry.value.copy(
            state = BridgeConnectionState.CONNECTING,
            activeEndpoint = normalizedUrl,
            isReconnecting = attempt > 0,
            reconnectAttempt = attempt,
            lastBridgeEvent = if (attempt > 0) {
                "Reconnecting to $normalizedUrl (attempt #$attempt)..."
            } else {
                "Connecting to $normalizedUrl..."
            }
        )

        val wsUrl = when {
            normalizedUrl.startsWith("ws://") || normalizedUrl.startsWith("wss://") -> normalizedUrl
            normalizedUrl.startsWith("https://") -> "wss://" + normalizedUrl.removePrefix("https://")
            normalizedUrl.startsWith("http://") -> "ws://" + normalizedUrl.removePrefix("http://")
            else -> null
        }

        if (wsUrl != null) {
            try {
                val requestBuilder = Request.Builder().url(wsUrl)
                if (currentAuthToken.isNotBlank()) {
                    requestBuilder.addHeader("Authorization", "Bearer $currentAuthToken")
                }
                activeWebSocket = okHttpClient.newWebSocket(
                    requestBuilder.build(),
                    object : WebSocketListener() {
                        override fun onOpen(webSocket: WebSocket, response: Response) {
                            reconnectJob?.cancel()
                            reconnectJob = null
                            val flushedCount = flushOfflineQueue(webSocket)
                            val flushNote = if (flushedCount > 0) " • Flushed $flushedCount queued command(s)" else ""
                            _telemetry.value = _telemetry.value.copy(
                                state = BridgeConnectionState.CONNECTED_LIVE_SOCKET,
                                isReconnecting = false,
                                reconnectAttempt = 0,
                                deltaFramesReceived = _telemetry.value.deltaFramesReceived + 1,
                                lastBridgeEvent = "Live WebSocket connected ($normalizedUrl)$flushNote"
                            )
                            onRemoteEvent("Connected to live remote WebSocket: $normalizedUrl$flushNote")
                        }

                        override fun onMessage(webSocket: WebSocket, text: String) {
                            val boundedText = if (text.length > MAX_BRIDGE_PAYLOAD_CHARS) {
                                text.take(MAX_BRIDGE_PAYLOAD_CHARS) + "\n...[Output truncated at 32KB for memory safety]..."
                            } else {
                                text
                            }
                            _telemetry.value = _telemetry.value.copy(
                                deltaFramesReceived = _telemetry.value.deltaFramesReceived + 1,
                                lastBridgeEvent = "Frame received (${text.length}B)"
                            )
                            onRemoteEvent(boundedText)
                        }

                        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                            webSocket.close(1000, null)
                        }

                        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                            if (activeWebSocket === webSocket) {
                                activeWebSocket = null
                            }
                            if (code != 1000 && currentEndpointUrl == normalizedUrl) {
                                scheduleAutoReconnect(normalizedUrl, attempt + 1, onRemoteEvent)
                            }
                        }

                        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                            if (activeWebSocket === webSocket) {
                                activeWebSocket = null
                            }
                            val isHttpEndpoint = normalizedUrl.startsWith("http://") ||
                                normalizedUrl.startsWith("https://")
                            _telemetry.value = _telemetry.value.copy(
                                state = if (isHttpEndpoint) {
                                    BridgeConnectionState.CONNECTED_HTTP_BRIDGE
                                } else {
                                    BridgeConnectionState.ERROR
                                },
                                lastBridgeEvent = if (isHttpEndpoint) {
                                    "HTTP/WebView bridge ready ($normalizedUrl)"
                                } else {
                                    "Connection failed: ${t.localizedMessage ?: "Unreachable host"}"
                                }
                            )
                            if (currentEndpointUrl == normalizedUrl && _telemetry.value.autoReconnectEnabled) {
                                scheduleAutoReconnect(normalizedUrl, attempt + 1, onRemoteEvent)
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                _telemetry.value = _telemetry.value.copy(
                    state = BridgeConnectionState.ERROR,
                    isReconnecting = false,
                    lastBridgeEvent = "Invalid endpoint URL: ${e.localizedMessage}"
                )
            }
        } else {
            _telemetry.value = _telemetry.value.copy(
                state = BridgeConnectionState.ERROR,
                isReconnecting = false,
                lastBridgeEvent = "Endpoint must start with https://, http://, wss://, or ws://"
            )
        }
    }

    private fun scheduleAutoReconnect(
        endpointUrl: String,
        nextAttempt: Int,
        onRemoteEvent: (String) -> Unit
    ) {
        if (!_telemetry.value.autoReconnectEnabled || endpointUrl.isBlank()) return
        if (nextAttempt > MAX_RECONNECT_ATTEMPTS) {
            _telemetry.value = _telemetry.value.copy(
                isReconnecting = false,
                lastBridgeEvent = "Auto-reconnect paused after $MAX_RECONNECT_ATTEMPTS attempts. Tap Refresh to retry."
            )
            return
        }

        val delayMs = computeBackoffDelayMs(nextAttempt)
        _telemetry.value = _telemetry.value.copy(
            isReconnecting = true,
            reconnectAttempt = nextAttempt,
            lastBridgeEvent = "Reconnecting in ${delayMs / 1000}s (attempt #$nextAttempt)..."
        )

        reconnectJob?.cancel()
        reconnectJob = bridgeScope.launch {
            delay(delayMs)
            if (currentEndpointUrl == endpointUrl && _telemetry.value.autoReconnectEnabled) {
                openWebSocketInternal(endpointUrl, nextAttempt, onRemoteEvent)
            }
        }
    }

    suspend fun measureLatency(endpointUrl: String): Int = withContext(Dispatchers.IO) {
        val cleanUrl = endpointUrl.trim()
        if (cleanUrl.isBlank()) return@withContext 0

        val httpTarget = toHttpUrl(cleanUrl) ?: return@withContext 0

        val start = System.currentTimeMillis()
        try {
            val reqBuilder = Request.Builder().url(httpTarget).head()
            if (currentAuthToken.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $currentAuthToken")
            }
            okHttpClient.newCall(reqBuilder.build()).execute().use {
                val elapsed = (System.currentTimeMillis() - start).toInt().coerceAtLeast(1)
                _telemetry.value = _telemetry.value.copy(
                    latencyMs = elapsed,
                    deltaFramesReceived = _telemetry.value.deltaFramesReceived + 1,
                    lastBridgeEvent = "Remote host responded in ${elapsed}ms"
                )
                return@withContext elapsed
            }
        } catch (e: Exception) {
            _telemetry.value = _telemetry.value.copy(
                lastBridgeEvent = "Ping unreachable: ${e.localizedMessage ?: "Check host URL"}"
            )
            return@withContext 0
        }
    }

    fun sendJsonRpcMessage(method: String, params: Map<String, Any?>): Boolean {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", System.currentTimeMillis())
            put("method", method)
            put("params", JSONObject(params))
        }.toString()
        val sent = activeWebSocket?.send(payload) ?: false
        if (sent) {
            _telemetry.value = _telemetry.value.copy(
                deltaFramesReceived = _telemetry.value.deltaFramesReceived + 1,
                lastBridgeEvent = "Dispatched RPC '$method' (${payload.length}B)"
            )
        } else {
            val queued = enqueueOfflinePayload(payload)
            _telemetry.value = _telemetry.value.copy(
                lastBridgeEvent = "RPC '$method' queued offline ($queued pending — auto-flushes on reconnect)"
            )
        }
        return sent
    }

    fun sendTerminalInterrupt(sessionId: String): Boolean {
        RetainedWebViewManager.dispatchTerminalCommandToWebView("\u0003")
        return sendJsonRpcMessage(
            method = "terminal.signal",
            params = mapOf(
                "sessionId" to sessionId,
                "signal" to "SIGINT",
                "data" to "\u0003"
            )
        )
    }

    /**
     * Queries the connected remote bridge for a directory listing (`workspace.listFiles` JSON-RPC).
     */
    suspend fun fetchRemoteDirectory(path: String): Result<List<RemoteFileEntry>> = withContext(Dispatchers.IO) {
        val cleanPath = path.trim().ifBlank { "." }
        val endpoint = _telemetry.value.activeEndpoint.trim()
        val httpUrl = toHttpUrl(endpoint)
            ?: return@withContext Result.failure(IllegalStateException("No remote host configured."))

        val rpcPayload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", System.currentTimeMillis())
            put("method", "workspace.listFiles")
            put("params", JSONObject().apply { put("path", cleanPath) })
        }.toString()

        try {
            val reqBuilder = Request.Builder()
                .url(httpUrl)
                .post(rpcPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            if (currentAuthToken.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $currentAuthToken")
            }
            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty().take(MAX_BRIDGE_PAYLOAD_CHARS)
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("HTTP ${response.code}: ${response.message}")
                    )
                }
                val entries = parseRemoteFileEntriesJson(rawBody, cleanPath)
                Result.success(entries)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Queries the connected remote bridge for file contents (`workspace.readFile` JSON-RPC).
     */
    suspend fun fetchRemoteFileContent(path: String): Result<String> = withContext(Dispatchers.IO) {
        val cleanPath = path.trim()
        if (cleanPath.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("File path is empty."))
        }
        val endpoint = _telemetry.value.activeEndpoint.trim()
        val httpUrl = toHttpUrl(endpoint)
            ?: return@withContext Result.failure(IllegalStateException("No remote host configured."))

        val rpcPayload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", System.currentTimeMillis())
            put("method", "workspace.readFile")
            put("params", JSONObject().apply { put("path", cleanPath) })
        }.toString()

        try {
            val reqBuilder = Request.Builder()
                .url(httpUrl)
                .post(rpcPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            if (currentAuthToken.isNotBlank()) {
                reqBuilder.addHeader("Authorization", "Bearer $currentAuthToken")
            }
            okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                val rawBody = response.body?.string().orEmpty().take(MAX_BRIDGE_PAYLOAD_CHARS)
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("HTTP ${response.code}: ${response.message}")
                    )
                }
                val content = try {
                    val json = JSONObject(rawBody)
                    json.optJSONObject("result")?.optString("content")
                        ?: json.optString("content").ifBlank { rawBody }
                } catch (_: Exception) {
                    rawBody
                }
                Result.success(content)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Interfaces with the remote bridge to execute a shell command over:
     * 1. Active bidirectional WebSocket (`terminal.exec` JSON-RPC frame), or
     * 2. Remote HTTP JSON-RPC POST endpoint, and
     * 3. Pinned WebView JS bridge (if an Antigravity Remote web session is open).
     */
    suspend fun executeShellCommandOverBridge(
        sessionId: String,
        command: String
    ): RemoteShellExecutionResult = withContext(Dispatchers.IO) {
        val startMs = System.currentTimeMillis()
        val endpoint = _telemetry.value.activeEndpoint.trim()

        val rpcPayload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", startMs)
            put("method", "terminal.exec")
            put(
                "params",
                JSONObject().apply {
                    put("sessionId", sessionId)
                    put("command", command)
                }
            )
        }.toString()

        // Also forward command to the memory-pinned WebView JS bridge (safely posted to WebView UI thread)
        RetainedWebViewManager.dispatchTerminalCommandToWebView(command)

        if (endpoint.isBlank()) {
            return@withContext RemoteShellExecutionResult(
                command = command,
                output = "No remote host configured. Tap the top session bar -> 'Configure Remote Host' (or use the Startup Guide) to connect your Antigravity server URL.",
                exitCode = 127,
                durationMs = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            )
        }

        // 1. Try sending via live WebSocket if open
        val sentOverWs = activeWebSocket?.send(rpcPayload) ?: false
        if (sentOverWs) {
            val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
            _telemetry.value = _telemetry.value.copy(
                deltaFramesReceived = _telemetry.value.deltaFramesReceived + 1,
                lastBridgeEvent = "Sent terminal.exec: $command"
            )
            return@withContext RemoteShellExecutionResult(
                command = command,
                output = "[Dispatched via WebSocket JSON-RPC to $endpoint]\nAwaiting live stdout/stderr stream from remote PTY...",
                exitCode = 0,
                durationMs = elapsed
            )
        }

        // 2. Otherwise attempt HTTP JSON-RPC POST to the remote endpoint
        val httpUrl = toHttpUrl(endpoint)
        if (httpUrl != null) {
            try {
                val reqBuilder = Request.Builder()
                    .url(httpUrl)
                    .post(rpcPayload.toRequestBody("application/json; charset=utf-8".toMediaType()))
                if (currentAuthToken.isNotBlank()) {
                    reqBuilder.addHeader("Authorization", "Bearer $currentAuthToken")
                }
                okHttpClient.newCall(reqBuilder.build()).execute().use { response ->
                    val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                    val rawBodyText = response.body?.string().orEmpty().trim()
                    val bodyText = if (rawBodyText.length > MAX_BRIDGE_PAYLOAD_CHARS) {
                        rawBodyText.take(MAX_BRIDGE_PAYLOAD_CHARS) + "\n...[Output truncated at 32KB for memory safety]..."
                    } else {
                        rawBodyText
                    }
                    val parsedOutput = try {
                        val json = JSONObject(bodyText)
                        json.optJSONObject("result")?.optString("output")
                            ?: json.optString("output").ifBlank { bodyText }
                    } catch (_: Exception) {
                        bodyText.ifBlank { "HTTP ${response.code} ${response.message}" }
                    }
                    val exitCode = if (response.isSuccessful) 0 else response.code
                    _telemetry.value = _telemetry.value.copy(
                        latencyMs = elapsed.toInt(),
                        deltaFramesReceived = _telemetry.value.deltaFramesReceived + 1,
                        lastBridgeEvent = "terminal.exec HTTP ${response.code} (${elapsed}ms)"
                    )
                    return@withContext RemoteShellExecutionResult(
                        command = command,
                        output = parsedOutput,
                        exitCode = exitCode,
                        durationMs = elapsed
                    )
                }
            } catch (e: Exception) {
                val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
                val queued = enqueueOfflinePayload(rpcPayload)
                return@withContext RemoteShellExecutionResult(
                    command = command,
                    output = "Remote bridge offline ($endpoint): ${e.localizedMessage ?: "Connection refused"}\n[Queued #$queued for automatic dispatch upon WebSocket reconnect]",
                    exitCode = 1,
                    durationMs = elapsed
                )
            }
        }

        val elapsed = (System.currentTimeMillis() - startMs).coerceAtLeast(1L)
        RemoteShellExecutionResult(
            command = command,
            output = "Unable to reach $endpoint. Verify your Antigravity server URL and network connection.",
            exitCode = 1,
            durationMs = elapsed
        )
    }

    fun setDomLagBooster(enabled: Boolean) {
        _telemetry.value = _telemetry.value.copy(
            domLagBoosterEnabled = enabled,
            lastBridgeEvent = if (enabled) {
                "DOM Lag Booster enabled"
            } else {
                "DOM Lag Booster disabled"
            }
        )
    }

    fun recordBridgeEvent(event: String) {
        _telemetry.value = _telemetry.value.copy(
            deltaFramesReceived = _telemetry.value.deltaFramesReceived + 1,
            lastBridgeEvent = event
        )
    }

    private fun toHttpUrl(url: String): String? {
        return when {
            url.startsWith("http://") || url.startsWith("https://") -> url
            url.startsWith("wss://") -> "https://" + url.removePrefix("wss://")
            url.startsWith("ws://") -> "http://" + url.removePrefix("ws://")
            else -> null
        }
    }

    companion object {
        const val MAX_BRIDGE_PAYLOAD_CHARS = 32_768
        const val MAX_OFFLINE_QUEUE_SIZE = 50
        const val MAX_RECONNECT_ATTEMPTS = 8

        fun computeBackoffDelayMs(attempt: Int): Long {
            val clampedShift = (attempt - 1).coerceIn(0, 4)
            return (2_000L shl clampedShift).coerceAtMost(30_000L)
        }

        fun parseRemoteFileEntriesJson(rawJson: String, basePath: String): List<RemoteFileEntry> {
            val trimmed = rawJson.trim()
            if (trimmed.isEmpty()) return emptyList()
            val array: JSONArray = try {
                if (trimmed.startsWith("[")) {
                    JSONArray(trimmed)
                } else {
                    val obj = JSONObject(trimmed)
                    obj.optJSONObject("result")?.optJSONArray("entries")
                        ?: obj.optJSONObject("result")?.optJSONArray("files")
                        ?: obj.optJSONArray("entries")
                        ?: obj.optJSONArray("files")
                        ?: JSONArray()
                }
            } catch (_: Exception) {
                return emptyList()
            }

            val normalizedBase = basePath.trimEnd('/')
            val results = mutableListOf<RemoteFileEntry>()
            for (i in 0 until array.length()) {
                val item = array.opt(i)
                when (item) {
                    is JSONObject -> {
                        val name = item.optString("name").ifBlank {
                            item.optString("path").substringAfterLast('/')
                        }
                        if (name.isBlank()) continue
                        val fullPath = item.optString("path").ifBlank {
                            if (normalizedBase == "." || normalizedBase.isBlank()) name else "$normalizedBase/$name"
                        }
                        val isDir = item.optBoolean("isDirectory", false) ||
                            item.optString("type").equals("directory", ignoreCase = true) ||
                            item.optString("type").equals("dir", ignoreCase = true)
                        val size = item.optLong("sizeBytes", item.optLong("size", 0L))
                        results.add(RemoteFileEntry(name, fullPath, isDir, size))
                    }
                    is String -> {
                        val isDir = item.endsWith("/")
                        val cleanName = item.trimEnd('/')
                        if (cleanName.isBlank()) continue
                        val fullPath = if (normalizedBase == "." || normalizedBase.isBlank()) {
                            cleanName
                        } else {
                            "$normalizedBase/$cleanName"
                        }
                        results.add(RemoteFileEntry(cleanName, fullPath, isDir, 0L))
                    }
                }
            }
            return results.sortedWith(compareByDescending<RemoteFileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        }
    }
}

/**
 * Singleton holder that pins a single hardware-accelerated WebView in application memory.
 * When the user switches tabs or minimizes the Android app, the WebView is detached from its
 * temporary parent FrameLayout without being destroyed—eliminating browser tab eviction and
 * cold reloads when returning to the app.
 */
object RetainedWebViewManager {
    @SuppressLint("StaticFieldLeak")
    private var pinnedWebView: WebView? = null
    private var lastLoadedEndpoint: String = "__UNINITIALIZED__"
    private var domLagBoosterActive: Boolean = true

    fun isSafeHttpUrl(url: String): Boolean {
        val clean = url.trim().lowercase()
        return clean.startsWith("https://") || clean.startsWith("http://")
    }

    fun buildCleanMobileUserAgent(rawUserAgent: String): String {
        return rawUserAgent
            .replace("; wv", "")
            .replace("Version/4.0 ", "")
            .trim()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun obtainPinnedWebView(
        context: Context,
        endpointUrl: String,
        domLagBooster: Boolean,
        onJsBridgeMessage: (String) -> Unit
    ): WebView {
        domLagBoosterActive = domLagBooster
        val appContext = context.applicationContext
        val webView = pinnedWebView ?: WebView(appContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.parseColor("#070708"))
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setInitialScale(0)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                allowContentAccess = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                useWideViewPort = true
                loadWithOverviewMode = false
                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                textZoom = 100
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                userAgentString = buildCleanMobileUserAgent(userAgentString.orEmpty())
            }
            addJavascriptInterface(
                object {
                    @JavascriptInterface
                    fun postNativeAction(actionJson: String) {
                        val safeJson = actionJson.take(AntigravityBridgeClient.MAX_BRIDGE_PAYLOAD_CHARS)
                        onJsBridgeMessage(safeJson)
                    }
                },
                "AntigravityNativeBridge"
            )
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUrl = request?.url?.toString().orEmpty()
                    // Block non-HTTP(S) schemes (e.g., file://, content://, javascript:, intent:)
                    return !isSafeHttpUrl(targetUrl)
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectMobileLayoutFix(view)
                    // Re-apply after SPA hydration to prevent late horizontal layout shift
                    view?.postDelayed({ injectMobileLayoutFix(view) }, 450L)
                    if (domLagBoosterActive) {
                        injectDomLagOptimizer(view)
                    } else {
                        removeDomLagOptimizer(view)
                    }
                }
            }
            pinnedWebView = this
        }

        (webView.parent as? ViewGroup)?.removeView(webView)

        val cleanEndpoint = endpointUrl.trim()
        if (lastLoadedEndpoint != cleanEndpoint) {
            lastLoadedEndpoint = cleanEndpoint
            if (isSafeHttpUrl(cleanEndpoint)) {
                webView.loadUrl(cleanEndpoint)
            } else {
                webView.loadDataWithBaseURL(
                    "https://antigravity.local",
                    buildEmptyWebBridgeNoticeHtml(),
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        }
        return webView
    }

    fun reloadOrForceLoadUrl(url: String, domLagBooster: Boolean) {
        domLagBoosterActive = domLagBooster
        val webView = pinnedWebView ?: return
        val cleanUrl = url.trim()
        lastLoadedEndpoint = cleanUrl
        if (isSafeHttpUrl(cleanUrl)) {
            webView.loadUrl(cleanUrl)
        } else {
            webView.loadDataWithBaseURL(
                "https://antigravity.local",
                buildEmptyWebBridgeNoticeHtml(),
                "text/html",
                "UTF-8",
                null
            )
        }
    }

    fun reloadCurrentPage() {
        val webView = pinnedWebView ?: return
        webView.post {
            try {
                if (isSafeHttpUrl(lastLoadedEndpoint)) {
                    webView.reload()
                }
            } catch (_: Exception) {
            }
        }
    }

    fun canGoBack(): Boolean = pinnedWebView?.canGoBack() == true

    fun goBack(): Boolean {
        val webView = pinnedWebView ?: return false
        return if (webView.canGoBack()) {
            webView.goBack()
            true
        } else {
            false
        }
    }

    /**
     * Fixes right-hand alignment / horizontal overflow inside Antigravity Remote web UI
     * by enforcing a 1:1 mobile viewport, clamping html/body max-width to 100vw, and
     * resetting any horizontal scroll offset on root containers.
     */
    fun injectMobileLayoutFix(webView: WebView? = pinnedWebView) {
        val target = webView ?: return
        val js = """
            (function() {
              try {
                var vp = document.querySelector('meta[name="viewport"]');
                var vpContent = 'width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover';
                if (!vp && document.head) {
                  vp = document.createElement('meta');
                  vp.name = 'viewport';
                  document.head.appendChild(vp);
                }
                if (vp) { vp.setAttribute('content', vpContent); }

                var styleId = 'antigravity-pulse-layout-fix';
                var existing = document.getElementById(styleId);
                if (!existing && document.head) {
                  existing = document.createElement('style');
                  existing.id = styleId;
                  existing.textContent =
                    'html, body { width: 100% !important; max-width: 100vw !important; min-width: 0 !important; ' +
                    'overflow-x: hidden !important; margin-left: 0 !important; margin-right: 0 !important; ' +
                    'padding-left: 0 !important; padding-right: 0 !important; box-sizing: border-box !important; } ' +
                    '#root, #app, main, [role="main"] { max-width: 100vw !important; box-sizing: border-box !important; }';
                  document.head.appendChild(existing);
                }

                if (document.documentElement) { document.documentElement.scrollLeft = 0; }
                if (document.body) { document.body.scrollLeft = 0; }
                window.scrollTo(0, window.scrollY || 0);
              } catch (e) {}
            })();
        """.trimIndent()
        target.post {
            try {
                target.scrollTo(0, target.scrollY)
                target.evaluateJavascript(js, null)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Inserts a prompt directly into the live Antigravity Remote web composer
     * (supporting React/Angular/WebComponent <textarea>, [contenteditable], or <input>),
     * in addition to calling window.receiveNativePrompt if provided.
     */
    fun injectPromptIntoWebView(prompt: String) {
        val webView = pinnedWebView ?: return
        val escaped = JSONObject.quote(prompt)
        val js = """
            (function(text) {
              try {
                if (window.receiveNativePrompt) {
                  window.receiveNativePrompt(text);
                  return true;
                }
                var candidates = Array.from(document.querySelectorAll('textarea, [contenteditable="true"], input[type="text"]'));
                var target = candidates.find(function(el) {
                  var r = el.getBoundingClientRect();
                  return r.width > 40 && r.height > 16 && !el.disabled && !el.readOnly;
                }) || candidates[0];
                if (!target) { return false; }
                target.focus();
                if (target.isContentEditable) {
                  target.textContent = text;
                } else {
                  var proto = target.tagName === 'TEXTAREA'
                    ? window.HTMLTextAreaElement.prototype
                    : window.HTMLInputElement.prototype;
                  var setter = Object.getOwnPropertyDescriptor(proto, 'value');
                  if (setter && setter.set) {
                    setter.set.call(target, text);
                  } else {
                    target.value = text;
                  }
                }
                target.dispatchEvent(new Event('input', { bubbles: true }));
                target.dispatchEvent(new Event('change', { bubbles: true }));
                return true;
              } catch (e) {
                return false;
              }
            })($escaped);
        """.trimIndent()
        webView.post {
            try {
                webView.evaluateJavascript(js, null)
            } catch (_: Exception) {
            }
        }
    }

    fun dispatchTerminalCommandToWebView(command: String) {
        val webView = pinnedWebView ?: return
        val escaped = JSONObject.quote(command)
        webView.post {
            try {
                webView.evaluateJavascript(
                    "if (window.executeNativeShellCommand) { window.executeNativeShellCommand($escaped); }",
                    null
                )
            } catch (_: Exception) {
            }
        }
    }

    fun injectDomLagOptimizer(webView: WebView? = pinnedWebView) {
        domLagBoosterActive = true
        val target = webView ?: return
        val js = """
            (function() {
              var id = 'antigravity-pulse-dom-booster';
              if (!document.getElementById(id) && document.head) {
                var style = document.createElement('style');
                style.id = id;
                style.textContent = '*, *::before, *::after { animation-duration: 0.01ms !important; transition-duration: 0.05ms !important; scroll-behavior: auto !important; }';
                document.head.appendChild(style);
              }
            })();
        """.trimIndent()
        target.post {
            try {
                target.evaluateJavascript(js, null)
            } catch (_: Exception) {
            }
        }
    }

    fun removeDomLagOptimizer(webView: WebView? = pinnedWebView) {
        domLagBoosterActive = false
        val target = webView ?: return
        val js = """
            (function() {
              var el = document.getElementById('antigravity-pulse-dom-booster');
              if (el && el.parentNode) { el.parentNode.removeChild(el); }
            })();
        """.trimIndent()
        target.post {
            try {
                target.evaluateJavascript(js, null)
            } catch (_: Exception) {
            }
        }
    }

    private fun buildEmptyWebBridgeNoticeHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
              <style>
                * { box-sizing: border-box; }
                html, body {
                  margin: 0; padding: 24px 18px; width: 100%; max-width: 100vw; overflow-x: hidden;
                  background: #070708; color: #FFFFFF;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                  display: flex; flex-direction: column; align-items: center; justify-content: center;
                  min-height: 82vh; text-align: center;
                }
                .box {
                  width: 100%; max-width: 400px; background: #111114; border: 1px solid #26262B;
                  border-radius: 18px; padding: 24px;
                }
                h3 { margin: 0 0 10px 0; color: #FFC799; font-size: 18px; }
                p { margin: 0; color: #A1A1AA; font-size: 14px; line-height: 1.55; }
                .hint {
                  margin-top: 14px; padding: 10px 12px; border-radius: 12px;
                  background: rgba(255, 199, 153, 0.10); border: 1px solid rgba(255, 199, 153, 0.30);
                  color: #FFC799; font-size: 12.5px;
                }
              </style>
            </head>
            <body>
              <div class="box">
                <h3>Antigravity Remote Ready</h3>
                <p>Tap the floating side control handle to <strong>Scan QR Code</strong> or <strong>Connect Remote URL</strong>. Your session stays pinned in Android RAM with zero reloads.</p>
                <div class="hint">Tip: Drag the floating control handle anywhere on screen to reposition it.</div>
              </div>
            </body>
            </html>
        """.trimIndent()
    }
}
