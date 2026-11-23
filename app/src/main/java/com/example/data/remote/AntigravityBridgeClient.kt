package com.example.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebViewDatabase
import com.example.BuildConfig
import com.example.notifications.PulseNotificationManager
import java.io.ByteArrayInputStream
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

enum class BridgeConnectionState {
    DISCONNECTED,
    CONNECTED_HTTP_BRIDGE,
    ERROR
}

data class BridgeTelemetry(
    val state: BridgeConnectionState = BridgeConnectionState.DISCONNECTED,
    val activeEndpoint: String = "",
    val latencyMs: Int = 0,
    val webViewPinnedInRam: Boolean = true,
    val domLagBoosterEnabled: Boolean = true,
    val lastBridgeEvent: String = "No remote host connected. Add your Antigravity Remote URL to begin.",
    val autoReconnectEnabled: Boolean = true
)

/**
 * Singleton holder that pins a single hardware-accelerated WebView in application memory.
 * When the user switches tabs or minimizes the Android app, the WebView is detached from its
 * temporary parent FrameLayout without being destroyed—eliminating browser tab eviction and
 * cold reloads when returning to the app.
 */
object RetainedWebViewManager {
    private const val MAX_URL_LENGTH = 2048

    @SuppressLint("StaticFieldLeak")
    private var pinnedWebView: WebView? = null
    private var lastLoadedEndpoint: String = "__UNINITIALIZED__"
    private var domLagBoosterActive: Boolean = true
    private val _canGoBackState = MutableStateFlow(false)
    val canGoBackState: StateFlow<Boolean> = _canGoBackState.asStateFlow()

    private val _pageLoadProgress = MutableStateFlow(100)
    val pageLoadProgress: StateFlow<Int> = _pageLoadProgress.asStateFlow()

    private val _hasActiveSessionState = MutableStateFlow(false)
    val hasActiveSessionState: StateFlow<Boolean> = _hasActiveSessionState.asStateFlow()

    private val _continuityState = MutableStateFlow(WebViewContinuityState())
    val continuityState: StateFlow<WebViewContinuityState> = _continuityState.asStateFlow()

    private val _rendererGeneration = MutableStateFlow(0)
    val rendererGeneration: StateFlow<Int> = _rendererGeneration.asStateFlow()

    private var lastGoodUrl: String = ""
    private var mainFrameFailedInCurrentLoad: Boolean = false
    private var savedStateBackup: WebViewStateBackup? = null
    private var pendingRestoreScroll: Pair<Int, Int>? = null
    private var pinnedSinceElapsedMs: Long = 0L

    fun isPinnedInRam(): Boolean = pinnedWebView != null

    fun pinnedSinceElapsedMs(): Long = pinnedSinceElapsedMs

    fun hasStateBackup(): Boolean = savedStateBackup != null

    private fun updateActiveEndpoint(endpoint: String) {
        lastLoadedEndpoint = endpoint
        val isSafe = isSafeHttpUrl(endpoint)
        _hasActiveSessionState.value = isSafe
        if (isSafe) {
            lastGoodUrl = endpoint
        }
    }

    private fun captureLatestSnapshotIfHealthy(view: WebView? = pinnedWebView) {
        val target = view ?: return
        if (!hasActiveLoadedSession() || mainFrameFailedInCurrentLoad) return
        WebViewOfflineCacheHelper.runOnMainThread(delayMs = 1200L) {
            if (!hasActiveLoadedSession() || mainFrameFailedInCurrentLoad) return@runOnMainThread
            val captured = WebViewOfflineCacheHelper.captureVisibleSnapshotThrottled(target)
            if (captured != null) {
                _continuityState.value = _continuityState.value.copy(cachedSnapshot = captured)
            }
        }
    }

    fun onNetworkLost() {
        val webView = pinnedWebView
        WebViewOfflineCacheHelper.applyCacheMode(webView, isOnline = false)
        if (!hasActiveLoadedSession()) {
            _continuityState.value = _continuityState.value.copy(isOnline = false)
            return
        }
        // Capture the exact screen the user is looking at right as the outage begins
        WebViewOfflineCacheHelper.runOnMainThread {
            val fresh = WebViewOfflineCacheHelper.captureWebViewSnapshot(webView)
            val prev = _continuityState.value
            _continuityState.value = prev.copy(
                isOnline = false,
                status = OfflineRecoveryStatus.OFFLINE_CACHED,
                cachedSnapshot = fresh ?: prev.cachedSnapshot,
                statusMessage = "Offline • Holding cached view until internet returns"
            )
        }
    }

    fun onNetworkRestored(autoReloadAfterLongOutage: Boolean) {
        val webView = pinnedWebView
        WebViewOfflineCacheHelper.applyCacheMode(webView, isOnline = true)
        if (!hasActiveLoadedSession()) {
            _continuityState.value = _continuityState.value.copy(
                isOnline = true,
                status = OfflineRecoveryStatus.ONLINE,
                showingSnapshotShield = false,
                statusMessage = ""
            )
            return
        }

        val prev = _continuityState.value
        val needsReload = prev.hadMainFrameError || autoReloadAfterLongOutage
        if (needsReload) {
            val targetUrl = lastGoodUrl.takeIf { isSafeHttpUrl(it) } ?: currentLoadedEndpoint()
            if (isSafeHttpUrl(targetUrl)) {
                WebViewOfflineCacheHelper.runOnMainThread {
                    val snap = if (!prev.hadMainFrameError) {
                        WebViewOfflineCacheHelper.captureWebViewSnapshot(webView) ?: prev.cachedSnapshot
                    } else {
                        prev.cachedSnapshot
                    }
                    _continuityState.value = prev.copy(
                        isOnline = true,
                        status = OfflineRecoveryStatus.RECONNECTING,
                        showingSnapshotShield = snap != null || prev.hadMainFrameError,
                        cachedSnapshot = snap,
                        statusMessage = "Back online • Updating to latest view..."
                    )
                    WebViewOfflineCacheHelper.runOnMainThread(delayMs = 650L) {
                        try {
                            mainFrameFailedInCurrentLoad = false
                            webView?.loadUrl(targetUrl)
                        } catch (_: Exception) {
                        }
                    }
                }
            }
        } else {
            // Short blip while live DOM stayed intact: notify SPA in-place without losing scroll/drafts
            WebViewOfflineCacheHelper.dispatchBrowserOnlineEvent(webView)
            _continuityState.value = prev.copy(
                isOnline = true,
                status = OfflineRecoveryStatus.RESTORED,
                showingSnapshotShield = false,
                statusMessage = "Back online • Session live"
            )
        }
    }

    fun refreshWithSnapshotShield() {
        val webView = pinnedWebView ?: return
        val targetUrl = lastGoodUrl.takeIf { isSafeHttpUrl(it) } ?: currentLoadedEndpoint()
        if (!isSafeHttpUrl(targetUrl)) return
        WebViewOfflineCacheHelper.applyCacheMode(webView, isOnline = true)
        WebViewOfflineCacheHelper.runOnMainThread {
            val prev = _continuityState.value
            val snap = if (!prev.hadMainFrameError) {
                WebViewOfflineCacheHelper.captureWebViewSnapshot(webView) ?: prev.cachedSnapshot
            } else {
                prev.cachedSnapshot
            }
            _continuityState.value = prev.copy(
                status = OfflineRecoveryStatus.RECONNECTING,
                showingSnapshotShield = snap != null,
                cachedSnapshot = snap,
                statusMessage = "Refreshing latest session..."
            )
            try {
                mainFrameFailedInCurrentLoad = false
                webView.loadUrl(targetUrl)
            } catch (_: Exception) {
            }
        }
    }

    fun dismissRestoredBanner() {
        val prev = _continuityState.value
        if (prev.status == OfflineRecoveryStatus.RESTORED) {
            _continuityState.value = prev.copy(
                status = OfflineRecoveryStatus.ONLINE,
                statusMessage = ""
            )
        }
    }

    private fun refreshCanGoBack(view: WebView? = pinnedWebView) {
        _canGoBackState.value = try {
            view?.canGoBack() == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Activity-registered callback for `<input type="file">` / folder attachment buttons
     * inside Google Antigravity Remote & Google AI Studio (e.g., the `+` button).
     */
    var onShowFileChooserHandler: ((ValueCallback<Array<Uri>>, WebChromeClient.FileChooserParams?) -> Boolean)? = null

    /**
     * Activity-registered runtime permission bridge for WebView `getUserMedia` requests
     * (microphone voice input & camera live input) across Remote and Google AI Studio.
     */
    var onWebPermissionRequestHandler: ((PermissionRequest) -> Unit)? = null
    var onWebPermissionRequestCanceled: ((PermissionRequest) -> Unit)? = null

    /**
     * Activity-registered native download handler for files exported from Remote or AI Studio.
     */
    var onWebDownloadRequested: ((url: String, userAgent: String, contentDisposition: String, mimeType: String) -> Unit)? = null

    /**
     * Activity-registered callback receiving sanitized remote PWA web notifications
     * (emitted via `window.Notification`, `ServiceWorkerRegistration.showNotification`,
     * or `antigravity:notify` custom events) so Android delivers them natively.
     */
    var onWebNotificationReceived: ((title: String, body: String, tag: String) -> Unit)? = null

    /**
     * Strict zero-trust HTTP(S) URL validator. Rejects non-HTTP(S) schemes (`file://`, `content://`,
     * `javascript:`, `intent:`, `data:`), control characters, whitespace, oversized URLs,
     * cloud metadata / link-local SSRF targets (`169.254.*`, `metadata.google.internal`, `0.0.0.0`),
     * and cleartext HTTP downgrade attempts on trusted OAuth/workspace domains.
     */
    fun isSafeHttpUrl(url: String): Boolean {
        val clean = url.trim()
        if (clean.isEmpty() || clean.length > MAX_URL_LENGTH) return false
        if (clean.any { it.isISOControl() || it.isWhitespace() }) return false
        // Reject backslashes before query/fragment to prevent authority-confusion parser bypasses
        val authorityAndPath = clean.substringBefore('?').substringBefore('#')
        if (authorityAndPath.contains('\\')) return false
        return try {
            val uri = Uri.parse(clean)
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()?.trimEnd('.').orEmpty()
            if ((scheme != "https" && scheme != "http") || host.isBlank()) return false
            if (isBlockedMetadataOrUnspecifiedHost(host)) return false
            if (scheme == "http" && isHttpsOnlyWorkspaceHost(host)) return false
            true
        } catch (_: Exception) {
            false
        }
    }

    internal fun isBlockedMetadataOrUnspecifiedHost(rawHost: String): Boolean {
        val host = rawHost.lowercase().trim().trim('[', ']').trimEnd('.')
        if (host.isEmpty()) return true
        // Block obfuscated single-label integer/hex/octal IP hosts (e.g. 2852039166 or 0xa9fea9fe)
        if (Regex("^(0x[0-9a-f]+|[0-9]+)$").matches(host)) return true
        // Block dotted-octal/hex IPv4 forms (leading zero octets like 0251.0376.0251.0376 or 0xa9.x)
        if (host.contains('.') && host.split('.').all { seg ->
                Regex("^(0x[0-9a-f]+|0[0-7]+|[0-9]+)$").matches(seg)
            } && host.split('.').any { seg ->
                seg.startsWith("0x") || (seg.length > 1 && seg.startsWith("0"))
            }
        ) {
            return true
        }
        return host == "0.0.0.0" ||
            host == "::" ||
            host.startsWith("::ffff:0.0.0.0") ||
            host.startsWith("::ffff:0:0") ||
            host.startsWith("169.254.") ||
            host.startsWith("::ffff:169.254.") ||
            host.startsWith("::ffff:a9fe:") ||
            host.startsWith("fe80:") ||
            host == "metadata.google.internal" ||
            host == "metadata.goog" ||
            host == "metadata" ||
            host.endsWith(".metadata.google.internal")
    }

    internal fun isHttpsOnlyWorkspaceHost(rawHost: String): Boolean {
        val host = rawHost.lowercase().trim().trimEnd('.')
        val protectedDomains = listOf(
            "aistudio.google.com",
            "gemini.google.com",
            "accounts.google.com",
            "github.com",
            "vercel.com",
            "supabase.com",
            "t3.codes"
        )
        return protectedDomains.any { domain -> host == domain || host.endsWith(".$domain") }
    }

    /**
     * Exact parsed-host domain matcher (never substring `.contains()`) to prevent lookalike
     * or query-string spoofing like `https://evil.com/?ref=github.com` or `https://evil-github.com`.
     */
    fun isHostMatchingDomain(url: String, domain: String, requireHttps: Boolean = true): Boolean {
        if (!isSafeHttpUrl(url)) return false
        val normalizedDomain = domain.lowercase().trim().trimStart('.').trimEnd('.')
        if (normalizedDomain.isEmpty()) return false
        return try {
            val uri = Uri.parse(url.trim())
            val scheme = uri.scheme?.lowercase()
            if (requireHttps && scheme != "https") return false
            val host = uri.host?.lowercase()?.trimEnd('.').orEmpty()
            host == normalizedDomain || host.endsWith(".$normalizedDomain")
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Strict scheme + host + effective-port origin comparison for gating privileged JS bridges
     * (notification nonce shims and prompt injection) to the intended origin only.
     */
    fun isSameOrigin(urlA: String, urlB: String): Boolean {
        if (!isSafeHttpUrl(urlA) || !isSafeHttpUrl(urlB)) return false
        return try {
            val a = Uri.parse(urlA.trim())
            val b = Uri.parse(urlB.trim())
            val schemeA = a.scheme?.lowercase() ?: return false
            val schemeB = b.scheme?.lowercase() ?: return false
            val hostA = a.host?.lowercase()?.trimEnd('.') ?: return false
            val hostB = b.host?.lowercase()?.trimEnd('.') ?: return false
            val portA = if (a.port != -1) a.port else if (schemeA == "https") 443 else 80
            val portB = if (b.port != -1) b.port else if (schemeB == "https") 443 else 80
            schemeA == schemeB && hostA == hostB && portA == portB
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Returns true only when a media (`getUserMedia`) request originates from a secure first-party
     * workspace (`aistudio.google.com`, `gemini.google.com`) or the user's own active remote host.
     * Arbitrary third-party sites in Pulse Browser require an explicit in-app user confirmation prompt.
     */
    fun isFirstPartyTrustedMediaOrigin(originUrl: String, activeRemoteEndpoint: String): Boolean {
        if (!isSecureMediaOrigin(originUrl)) return false
        if (isHostMatchingDomain(originUrl, "aistudio.google.com", requireHttps = true)) return true
        if (isHostMatchingDomain(originUrl, "gemini.google.com", requireHttps = true)) return true
        if (isHostMatchingDomain(originUrl, "t3.codes", requireHttps = true)) return true
        if (T3CodePinnedWebViewManager.isTrustedActiveOrigin(originUrl)) return true
        return activeRemoteEndpoint.isNotBlank() && isSameOrigin(originUrl, activeRemoteEndpoint)
    }

    /**
     * Only attaches WebView session cookies to `DownloadManager` requests when the download is
     * strictly HTTPS AND targets an allowlisted first-party workspace domain or the user's own
     * active remote workstation host—preventing cross-origin cookie exfiltration via third-party downloads.
     */
    fun isTrustedCookieDownloadUrl(downloadUrl: String, activeRemoteEndpoint: String): Boolean {
        if (!isSafeHttpUrl(downloadUrl)) return false
        return try {
            val uri = Uri.parse(downloadUrl.trim())
            if (uri.scheme?.lowercase() != "https") return false
            val host = uri.host?.lowercase()?.trimEnd('.').orEmpty()
            if (host.isBlank()) return false
            val trustedSuffixes = listOf(
                "google.com",
                "googleusercontent.com",
                "gstatic.com",
                "github.com",
                "githubusercontent.com",
                "vercel.com",
                "supabase.com",
                "supabase.co",
                "t3.codes"
            )
            if (trustedSuffixes.any { domain -> host == domain || host.endsWith(".$domain") }) {
                return true
            }
            if (T3CodePinnedWebViewManager.isTrustedHttpsDownloadHost(host)) {
                return true
            }
            val remoteHost = if (isSafeHttpUrl(activeRemoteEndpoint)) {
                Uri.parse(activeRemoteEndpoint.trim()).host?.lowercase()?.trimEnd('.').orEmpty()
            } else {
                ""
            }
            remoteHost.isNotBlank() && host == remoteHost
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Blocks page-initiated `file://`, `content://`, and cloud metadata subresource requests
     * across all WebViews while allowing normal HTTPS/HTTP traffic.
     */
    fun interceptUnsafeSubresource(request: WebResourceRequest?): WebResourceResponse? {
        val uri = request?.url ?: return null
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.host?.lowercase().orEmpty()
        val isBlocked = scheme == "file" ||
            scheme == "content" ||
            isBlockedMetadataOrUnspecifiedHost(host) ||
            (scheme == "http" && isHttpsOnlyWorkspaceHost(host))
        return if (isBlocked) {
            WebResourceResponse(
                "text/plain",
                "UTF-8",
                403,
                "Forbidden",
                mapOf("X-Content-Type-Options" to "nosniff"),
                ByteArrayInputStream(ByteArray(0))
            )
        } else {
            null
        }
    }

    /**
     * Strips embedded userinfo (`user:pass@`), query strings (`?token=...`), and fragments (`#...`)
     * so secret pairing tokens embedded in URLs are never synced to cloud backups or leaked onto
     * Android lock-screen notifications.
     */
    fun stripSensitiveUrlCredentials(url: String): String {
        val clean = url.trim()
        if (!isSafeHttpUrl(clean)) return ""
        return try {
            val uri = Uri.parse(clean)
            val scheme = uri.scheme?.lowercase() ?: return ""
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return ""
            val portPart = if (uri.port != -1) ":${uri.port}" else ""
            val pathPart = uri.encodedPath.orEmpty()
            "$scheme://$host$portPart$pathPart"
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Restricts WebView microphone/camera `getUserMedia` grants to HTTPS origins or local loopback
     * development servers (`localhost` / `127.0.0.1`), blocking cleartext public HTTP eavesdropping.
     */
    fun isSecureMediaOrigin(originUrl: String): Boolean {
        if (!isSafeHttpUrl(originUrl)) return false
        return try {
            val uri = Uri.parse(originUrl.trim())
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase().orEmpty()
            scheme == "https" || (scheme == "http" && (host == "localhost" || host == "127.0.0.1" || host == "::1"))
        } catch (_: Exception) {
            false
        }
    }

    fun hasActiveLoadedSession(): Boolean = isSafeHttpUrl(lastLoadedEndpoint)

    fun currentLoadedEndpoint(): String =
        if (isSafeHttpUrl(lastLoadedEndpoint)) lastLoadedEndpoint else ""

    fun flushCookies() {
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {
        }
    }

    /**
     * Keeps JavaScript timers and WebSocket heartbeats running while the user minimizes
     * Antigravity Pulse so the live DOM activity observer can deliver native agent alerts
     * as soon as the remote agent finishes or requests approval.
     */
    fun keepTimersRunning() {
        WebViewOfflineCacheHelper.keepTimersAliveWithoutResumingHidden(pinnedWebView)
    }

    fun dispatchKeepAliveHeartbeat() {
        WebViewOfflineCacheHelper.dispatchKeepAliveHeartbeat(pinnedWebView)
        flushCookies()
    }

    fun saveStateBackup() {
        val wv = pinnedWebView ?: return
        WebViewOfflineCacheHelper.runOnMainThread {
            WebViewOfflineCacheHelper.captureStateBackup(wv)?.let { savedStateBackup = it }
        }
    }

    fun releaseFromRam() {
        val oldView = pinnedWebView ?: return
        saveStateBackup()
        pinnedWebView = null
        pinnedSinceElapsedMs = 0L
        lastLoadedEndpoint = "__UNINITIALIZED__"
        WebViewOfflineCacheHelper.runOnMainThread {
            try {
                oldView.stopLoading()
                (oldView.parent as? ViewGroup)?.removeView(oldView)
                oldView.destroy()
            } catch (_: Exception) {
            }
        }
        _rendererGeneration.value = _rendererGeneration.value + 1
        WorkspaceMemoryKeepAliveCoordinator.notifyWorkspaceTopologyChanged()
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
            // Never allow remote Chrome DevTools inspection in release builds
            try {
                WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            } catch (_: Exception) {
            }

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            // Use default window layer pipeline (LAYER_TYPE_NONE) instead of forcing an offscreen
            // hardware FBO layer, preventing DRM rendernode errors on software-rendered environments.
            setLayerType(View.LAYER_TYPE_NONE, null)
            isNestedScrollingEnabled = false
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false
            setInitialScale(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    // Keep renderer at IMPORTANT priority even when switching tabs or backgrounding
                    // so Android's LMKD does not evict hidden sessions after 1+ hour.
                    setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                } catch (_: Exception) {
                }
            }
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                allowFileAccess = false
                @Suppress("DEPRECATION")
                allowFileAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                allowUniversalAccessFromFileURLs = false
                @Suppress("DEPRECATION")
                saveFormData = false
                setGeolocationEnabled(false)
                // Block direct page-initiated content:// provider access (user file picker uses onShowFileChooser)
                allowContentAccess = false
                javaScriptCanOpenWindowsAutomatically = false
                cacheMode = WebSettings.LOAD_DEFAULT
                // Strictly block active HTTP mixed-content injection inside HTTPS sessions
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    offscreenPreRaster = true
                }
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
            try {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
            } catch (_: Exception) {
            }
            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    _pageLoadProgress.value = newProgress.coerceIn(0, 100)
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (filePathCallback == null) return false
                    val handler = onShowFileChooserHandler
                    if (handler != null) {
                        return handler(filePathCallback, fileChooserParams)
                    }
                    filePathCallback.onReceiveValue(null)
                    return false
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    if (request == null) return
                    val originUrl = request.origin?.toString().orEmpty()
                    val handler = onWebPermissionRequestHandler
                    if (handler != null && isSecureMediaOrigin(originUrl)) {
                        handler(request)
                    } else {
                        request.deny()
                    }
                }

                override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                    if (request != null) {
                        onWebPermissionRequestCanceled?.invoke(request)
                    }
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    val raw = consoleMessage?.message()
                    if (raw != null && raw.startsWith(PulseNotificationManager.WEB_NOTIFY_CONSOLE_PREFIX)) {
                        val activePageUrl = try { pinnedWebView?.url.orEmpty() } catch (_: Exception) { "" }
                        val configuredEndpoint = currentLoadedEndpoint()
                        if (hasActiveLoadedSession() && isSameOrigin(activePageUrl, configuredEndpoint)) {
                            val parsed = PulseNotificationManager.parseAuthenticatedWebNotificationPayload(raw)
                            if (parsed != null) {
                                onWebNotificationReceived?.invoke(parsed.title, parsed.body, parsed.tag)
                            }
                        }
                        return true
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }
            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                if (isSafeHttpUrl(url)) {
                    onWebDownloadRequested?.invoke(
                        url,
                        userAgent.orEmpty(),
                        contentDisposition.orEmpty(),
                        mimeType.orEmpty()
                    )
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    return interceptUnsafeSubresource(request) ?: super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUrl = request?.url?.toString().orEmpty()
                    // Block non-HTTP(S) schemes (e.g., file://, content://, javascript:, intent:, data:)
                    return !isSafeHttpUrl(targetUrl)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    refreshCanGoBack(view)
                    if (!url.isNullOrBlank() && isSafeHttpUrl(url)) {
                        mainFrameFailedInCurrentLoad = false
                        lastGoodUrl = url
                        injectWebNotificationBridge(view, url)
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    refreshCanGoBack(view)
                    if (!url.isNullOrBlank() && isSafeHttpUrl(url)) {
                        lastGoodUrl = url
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true && error != null) {
                        val isOnlineNow = _continuityState.value.isOnline
                        if (WebViewOfflineCacheHelper.isNetworkErrorCode(error.errorCode, isOnlineNow)) {
                            mainFrameFailedInCurrentLoad = true
                            val prev = _continuityState.value
                            _continuityState.value = prev.copy(
                                isOnline = false,
                                status = OfflineRecoveryStatus.OFFLINE_CACHED,
                                hadMainFrameError = true,
                                showingSnapshotShield = true,
                                statusMessage = if (prev.cachedSnapshot != null) {
                                    "Offline • Showing cached view until internet returns"
                                } else {
                                    "Offline • Waiting for internet connection..."
                                }
                            )
                            WebViewOfflineCacheHelper.applyCacheMode(view, isOnline = false)
                        }
                    }
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    // Zero-trust: strictly reject invalid/MITM TLS certificates (never call proceed())
                    handler?.cancel()
                    onJsBridgeMessage("Blocked untrusted SSL certificate for your protection.")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    _pageLoadProgress.value = 100
                    refreshCanGoBack(view)
                    flushCookies()
                    val isErrorPage = url.isNullOrBlank() ||
                        url.startsWith("chrome-error:", ignoreCase = true) ||
                        url.startsWith("about:", ignoreCase = true)
                    if (!mainFrameFailedInCurrentLoad && !isErrorPage && isSafeHttpUrl(url.orEmpty())) {
                        lastGoodUrl = url.orEmpty()
                        val prev = _continuityState.value
                        val wasRecovering = prev.status == OfflineRecoveryStatus.RECONNECTING || prev.hadMainFrameError
                        _continuityState.value = prev.copy(
                            isOnline = true,
                            hadMainFrameError = false,
                            showingSnapshotShield = false,
                            status = if (wasRecovering) OfflineRecoveryStatus.RESTORED else OfflineRecoveryStatus.ONLINE,
                            statusMessage = if (wasRecovering) "Back online • Updated to latest" else ""
                        )
                        injectWebNotificationBridge(view, url)
                        pendingRestoreScroll?.let { (sx, sy) ->
                            pendingRestoreScroll = null
                            WebViewOfflineCacheHelper.restoreScrollOffset(view, sx, sy)
                        }
                        view?.postDelayed({
                            captureLatestSnapshotIfHealthy(view)
                            WebViewOfflineCacheHelper.captureStateBackup(view)?.let { savedStateBackup = it }
                        }, 650L)
                    }
                    if (!isErrorPage) {
                        injectMobileLayoutFix(view)
                        if (domLagBoosterActive) {
                            injectDomLagOptimizer(view)
                        } else {
                            removeDomLagOptimizer(view)
                        }
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    val crashedView = view ?: pinnedWebView
                    if (pinnedWebView === crashedView) {
                        pinnedWebView = null
                    }
                    pinnedSinceElapsedMs = 0L
                    lastLoadedEndpoint = "__UNINITIALIZED__"
                    WebViewOfflineCacheHelper.runOnMainThread {
                        try {
                            (crashedView?.parent as? ViewGroup)?.removeView(crashedView)
                            crashedView?.destroy()
                        } catch (_: Exception) {
                        }
                    }
                    _rendererGeneration.value = _rendererGeneration.value + 1
                    WorkspaceMemoryKeepAliveCoordinator.notifyWorkspaceTopologyChanged()
                    onJsBridgeMessage("Remote renderer recovered automatically at your last page.")
                    return true
                }
            }
            pinnedWebView = this
            pinnedSinceElapsedMs = android.os.SystemClock.elapsedRealtime()
            WorkspaceMemoryKeepAliveCoordinator.notifyWorkspaceTopologyChanged()
        }

        (webView.parent as? ViewGroup)?.removeView(webView)

        val cleanEndpoint = endpointUrl.trim()
        val recoveryTarget = lastGoodUrl.takeIf { isSafeHttpUrl(it) } ?: cleanEndpoint
        when {
            lastLoadedEndpoint == "__UNINITIALIZED__" && savedStateBackup != null && isSafeHttpUrl(recoveryTarget) -> {
                val backup = savedStateBackup
                pendingRestoreScroll = backup?.let { it.scrollX to it.scrollY }
                updateActiveEndpoint(recoveryTarget)
                mainFrameFailedInCurrentLoad = false
                WebViewOfflineCacheHelper.restoreStateBackupOrLoad(webView, backup, recoveryTarget)
            }
            isSafeHttpUrl(cleanEndpoint) -> {
                if (lastLoadedEndpoint != cleanEndpoint) {
                    updateActiveEndpoint(cleanEndpoint)
                    mainFrameFailedInCurrentLoad = false
                    webView.loadUrl(cleanEndpoint)
                }
            }
            // Never overwrite an already-active pinned remote session with the welcome screen
            // while Room is asynchronously restoring saved profiles after biometric unlock or resume.
            isSafeHttpUrl(lastLoadedEndpoint) -> Unit
            lastLoadedEndpoint != "__WELCOME__" -> {
                updateActiveEndpoint("__WELCOME__")
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

    /**
     * Loads [url] if the pinned WebView is still on the welcome screen or a different host,
     * without ever reloading an already-loaded active session.
     */
    fun ensureUrlLoadedIfIdle(url: String, domLagBooster: Boolean) {
        domLagBoosterActive = domLagBooster
        val cleanUrl = url.trim()
        if (!isSafeHttpUrl(cleanUrl)) return
        val webView = pinnedWebView ?: return
        if (lastLoadedEndpoint != cleanUrl) {
            updateActiveEndpoint(cleanUrl)
            mainFrameFailedInCurrentLoad = false
            webView.loadUrl(cleanUrl)
        }
    }

    fun resetToWelcomeScreen() {
        updateActiveEndpoint("__WELCOME__")
        lastGoodUrl = ""
        mainFrameFailedInCurrentLoad = false
        _continuityState.value = WebViewContinuityState()
        val webView = pinnedWebView ?: return
        webView.loadDataWithBaseURL(
            "https://antigravity.local",
            buildEmptyWebBridgeNoticeHtml(),
            "text/html",
            "UTF-8",
            null
        )
    }

    fun clearSensitiveBrowsingData(context: Context? = null) {
        val oldView = pinnedWebView
        pinnedWebView = null
        try {
            oldView?.stopLoading()
            oldView?.loadUrl("about:blank")
            oldView?.clearCache(true)
            oldView?.clearHistory()
            oldView?.clearFormData()
            (oldView?.parent as? ViewGroup)?.removeView(oldView)
            oldView?.destroy()
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeSessionCookies(null)
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
            WebStorage.getInstance().deleteAllData()
            context?.let {
                WebViewDatabase.getInstance(it.applicationContext).clearHttpAuthUsernamePassword()
            }
        } catch (_: Exception) {
        }
        _continuityState.value.cachedSnapshot?.let { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
        _continuityState.value = WebViewContinuityState()
        _canGoBackState.value = false
        savedStateBackup = null
        pendingRestoreScroll = null
        pinnedSinceElapsedMs = 0L
        _rendererGeneration.value = _rendererGeneration.value + 1
        resetToWelcomeScreen()
        WorkspaceMemoryKeepAliveCoordinator.notifyWorkspaceTopologyChanged()
    }

    fun reloadOrForceLoadUrl(url: String, domLagBooster: Boolean) {
        domLagBoosterActive = domLagBooster
        val webView = pinnedWebView ?: return
        val cleanUrl = url.trim()
        if (isSafeHttpUrl(cleanUrl)) {
            updateActiveEndpoint(cleanUrl)
            mainFrameFailedInCurrentLoad = false
            _continuityState.value = _continuityState.value.copy(
                hadMainFrameError = false,
                showingSnapshotShield = false
            )
            webView.loadUrl(cleanUrl)
        } else {
            resetToWelcomeScreen()
        }
    }

    fun reloadCurrentPage() {
        refreshWithSnapshotShield()
    }

    fun canGoBack(): Boolean = pinnedWebView?.canGoBack() == true

    fun goBack(): Boolean {
        val webView = pinnedWebView ?: return false
        return if (webView.canGoBack()) {
            webView.goBack()
            refreshCanGoBack(webView)
            true
        } else {
            refreshCanGoBack(webView)
            false
        }
    }

    /**
     * Fixes right-hand alignment / horizontal overflow inside web workspaces by enforcing
     * 1:1 HiDPI mobile viewport scaling, antialiased crisp typography, and clamping root
     * overflow to 100vw.
     */
    fun injectMobileLayoutFix(webView: WebView? = pinnedWebView) {
        val target = webView ?: return
        val js = """
            (function() {
              try {
                var vp = document.querySelector('meta[name="viewport"]');
                if (!vp && document.head) {
                  vp = document.createElement('meta');
                  vp.name = 'viewport';
                  vp.setAttribute('content', 'width=device-width, initial-scale=1.0, viewport-fit=cover');
                  document.head.appendChild(vp);
                }

                var styleId = 'antigravity-pulse-layout-fix';
                var existing = document.getElementById(styleId);
                if (!existing && document.head) {
                  existing = document.createElement('style');
                  existing.id = styleId;
                  existing.textContent =
                    'html, body { width: 100%; min-width: 0; margin-left: 0; margin-right: 0; ' +
                    'padding-left: 0; padding-right: 0; box-sizing: border-box; ' +
                    '-webkit-font-smoothing: antialiased; text-rendering: auto; }';
                  document.head.appendChild(existing);
                }
              } catch (e) {}
            })();
        """.trimIndent()
        target.post {
            try {
                target.evaluateJavascript(js, null)
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Injects a zero-`addJavascriptInterface` Web Notification polyfill + smart idle-aware DOM
     * Agent-Completion & Approval Observer into the remote session so agent replies, task
     * completions, and approval prompts trigger native Android notifications with minimal CPU.
     */
    fun injectWebNotificationBridge(webView: WebView? = pinnedWebView, pageUrl: String? = null) {
        val target = webView ?: return
        val candidateUrl = pageUrl ?: try { target.url.orEmpty() } catch (_: Exception) { "" }
        val configuredEndpoint = currentLoadedEndpoint()
        // Never leak the session bridge nonce into external/third-party origins navigated away from Remote
        if (configuredEndpoint.isBlank() || !isSameOrigin(candidateUrl, configuredEndpoint)) return
        val prefix = PulseNotificationManager.buildAuthenticatedConsolePrefix()
        val js = """
            (function() {
              try {
                var nativeConsoleInfo = console.info.bind(console);
                function emitNativeNotification(title, options) {
                  try {
                    var opts = options || {};
                    var payload = {
                      title: String(title || 'Antigravity Agent').slice(0, 120),
                      body: String(opts.body || opts.message || '').slice(0, 360),
                      tag: String(opts.tag || '').slice(0, 64)
                    };
                    nativeConsoleInfo('$prefix' + JSON.stringify(payload));
                  } catch (e) {}
                }

                if (!window.__agPulseNotifyPolyfilled) {
                  window.__agPulseNotifyPolyfilled = true;

                  function PulseNotification(title, options) {
                    this.title = String(title || 'Antigravity Agent');
                    this.body = String((options && options.body) || '');
                    this.tag = String((options && options.tag) || '');
                    this.onclick = null;
                    this.onclose = null;
                    this.onerror = null;
                    this.onshow = null;
                    window.__agPulseLastNotifiedAt = Date.now();
                    emitNativeNotification(this.title, options);
                  }
                  PulseNotification.permission = 'granted';
                  PulseNotification.maxActions = 2;
                  PulseNotification.requestPermission = function(callback) {
                    if (typeof callback === 'function') {
                      try { callback('granted'); } catch (e) {}
                    }
                    return Promise.resolve('granted');
                  };
                  PulseNotification.prototype.close = function() {
                    if (typeof this.onclose === 'function') {
                      try { this.onclose(); } catch (e) {}
                    }
                  };
                  PulseNotification.prototype.addEventListener = function() {};
                  PulseNotification.prototype.removeEventListener = function() {};

                  try {
                    Object.defineProperty(window, 'Notification', {
                      value: PulseNotification,
                      writable: true,
                      configurable: true
                    });
                  } catch (e) {
                    window.Notification = PulseNotification;
                  }

                  if (window.ServiceWorkerRegistration && window.ServiceWorkerRegistration.prototype) {
                    var origShow = window.ServiceWorkerRegistration.prototype.showNotification;
                    window.ServiceWorkerRegistration.prototype.showNotification = function(title, options) {
                      window.__agPulseLastNotifiedAt = Date.now();
                      emitNativeNotification(title, options);
                      if (typeof origShow === 'function') {
                        try { return origShow.call(this, title, options); } catch (e) {}
                      }
                      return Promise.resolve();
                    };
                  }

                  if (navigator.permissions && typeof navigator.permissions.query === 'function') {
                    var origQuery = navigator.permissions.query.bind(navigator.permissions);
                    navigator.permissions.query = function(desc) {
                      if (desc && desc.name === 'notifications') {
                        return Promise.resolve({ state: 'granted', onchange: null });
                      }
                      return origQuery(desc);
                    };
                  }

                  window.addEventListener('antigravity:notify', function(ev) {
                    var detail = (ev && ev.detail) || {};
                    window.__agPulseLastNotifiedAt = Date.now();
                    emitNativeNotification(detail.title || 'Antigravity Agent', {
                      body: detail.body || detail.message || '',
                      tag: detail.tag || ''
                    });
                  });

                  // Hook short notification audio chimes played by the remote PWA
                  if (window.HTMLAudioElement && window.HTMLAudioElement.prototype && !window.__agPulseAudioHooked) {
                    window.__agPulseAudioHooked = true;
                    var origAudioPlay = window.HTMLAudioElement.prototype.play;
                    window.HTMLAudioElement.prototype.play = function() {
                      try {
                        var now = Date.now();
                        var bootAt = window.__agPulseBootAt || now;
                        var lastAt = window.__agPulseLastNotifiedAt || 0;
                        var dur = this.duration;
                        var isShortChime = isNaN(dur) || dur === 0 || dur <= 8;
                        if (isShortChime && (now - bootAt) > 4500 && (now - lastAt) > 6500) {
                          window.__agPulseLastNotifiedAt = now;
                          emitNativeNotification('Antigravity Update', {
                            body: 'Your remote Antigravity agent sent an alert.',
                            tag: 'ag_audio_chime'
                          });
                        }
                      } catch (e) {}
                      return origAudioPlay.apply(this, arguments);
                    };
                  }
                }

                // Smart idle-aware DOM Agent-Completion & Approval Observer
                function attachAgentObserver() {
                  if (window.__agPulseDomObserverAttached || !document.body) { return; }
                  var host = (location.hostname || '').toLowerCase();
                  if (host.indexOf('accounts.google.') !== -1 || host.indexOf('signin.') !== -1) {
                    return;
                  }
                  window.__agPulseDomObserverAttached = true;
                  window.__agPulseBootAt = Date.now();
                  window.__agPulseLastNotifiedAt = 0;

                  var graceUntil = Date.now() + 4000;
                  var lastHref = location.href;
                  var isWorking = false;
                  var workStartedAt = 0;
                  var lastActivityAt = 0;
                  var lastTextLen = (document.body.textContent || '').length;
                  var streamTicks = 0;
                  var lastStreamTickAt = 0;
                  var seenApprovalKeys = {};
                  var baselineCaptured = false;
                  var lastWorkedMarkerCount = 0;
                  var domDirty = true;
                  var lastUserTouchAt = 0;
                  try {
                    var markTouch = function() { lastUserTouchAt = Date.now(); };
                    window.addEventListener('touchstart', markTouch, { passive: true, capture: true });
                    window.addEventListener('touchmove', markTouch, { passive: true, capture: true });
                    window.addEventListener('scroll', markTouch, { passive: true, capture: true });
                    window.addEventListener('wheel', markTouch, { passive: true, capture: true });
                  } catch (e) {}

                  function isVisible(el) {
                    if (!el) { return false; }
                    if (typeof el.checkVisibility === 'function') {
                      return el.checkVisibility({ checkOpacity: true, checkVisibilityCSS: true });
                    }
                    return (el.offsetWidth > 4 && el.offsetHeight > 4);
                  }

                  function countWorkedMarkers(text) {
                    var matches = text.match(/\b(Worked|Thought)\s+for\s+\d+\s*[smh]/gi);
                    return matches ? matches.length : 0;
                  }

                  function collectApprovalButtons() {
                    var found = [];
                    var nodes = document.querySelectorAll('button, [role="button"]');
                    for (var i = 0; i < nodes.length; i++) {
                      var el = nodes[i];
                      if (!isVisible(el) || el.disabled) { continue; }
                      var label = ((el.innerText || el.textContent || '') + '').trim();
                      if (label.length === 0 || label.length > 28) { continue; }
                      if (/^(approve|accept|accept all|allow|allow once|allow always|run|run command|proceed|confirm)$/i.test(label)) {
                        var ctx = '';
                        var parent = el.parentElement && el.parentElement.parentElement;
                        if (parent && parent.innerText) {
                          ctx = parent.innerText.replace(/\s+/g, ' ').trim().slice(0, 140);
                        }
                        found.push({ label: label, key: label.toLowerCase() + '|' + ctx.slice(0, 60), context: ctx });
                      }
                    }
                    return found;
                  }

                  function hasActiveWorkIndicator() {
                    var busy = document.querySelector('[aria-busy="true"], [role="progressbar"]');
                    if (busy && isVisible(busy)) { return true; }
                    var btns = document.querySelectorAll('button, [role="button"]');
                    for (var i = 0; i < btns.length; i++) {
                      var b = btns[i];
                      if (!isVisible(b) || b.disabled) { continue; }
                      var meta = (
                        (b.getAttribute('aria-label') || '') + ' ' +
                        (b.getAttribute('title') || '') + ' ' +
                        (b.getAttribute('data-testid') || '') + ' ' +
                        (b.innerText || '')
                      ).trim();
                      if (/\b(stop generating|stop response|stop agent|cancel generation|abort)\b/i.test(meta) || /^stop$/i.test(meta)) {
                        return true;
                      }
                    }
                    return false;
                  }

                  function extractLatestSnippet() {
                    try {
                      var blocks = document.querySelectorAll('main p, main li, [role="main"] p, article p, [class*="message" i] p, [class*="response" i] p, p');
                      for (var i = blocks.length - 1; i >= 0; i--) {
                        var el = blocks[i];
                        if (!isVisible(el)) { continue; }
                        var txt = (el.innerText || '').replace(/\s+/g, ' ').trim();
                        if (txt.length >= 18 && !/^ask anything/i.test(txt)) {
                          return txt.slice(0, 180);
                        }
                      }
                    } catch (e) {}
                    return 'Your remote Antigravity agent finished responding. Tap to view.';
                  }

                  function inspectState(fromTimer) {
                    try {
                      var now = Date.now();
                      if (location.href !== lastHref) {
                        lastHref = location.href;
                        graceUntil = now + 3500;
                        isWorking = false;
                        workStartedAt = 0;
                        streamTicks = 0;
                        baselineCaptured = false;
                        domDirty = true;
                        return;
                      }

                      // Never run DOM scans while the user is actively touching or scrolling
                      if ((now - lastUserTouchAt) < 2500) {
                        domDirty = true;
                        return;
                      }
                      // Zero-CPU idle fast path: skip DOM scans when nothing changed and agent is idle
                      if (fromTimer && !domDirty && !isWorking && baselineCaptured) {
                        return;
                      }
                      domDirty = false;

                      // Use textContent instead of innerText to avoid synchronous Blink layout reflows
                      var bodyText = (document.body && document.body.textContent) ? document.body.textContent : '';
                      var textLen = bodyText.length;
                      var workedCount = countWorkedMarkers(bodyText);

                      if (now < graceUntil) {
                        lastTextLen = textLen;
                        lastWorkedMarkerCount = workedCount;
                        return;
                      }

                      var approvals = collectApprovalButtons();
                      if (!baselineCaptured) {
                        baselineCaptured = true;
                        lastTextLen = textLen;
                        lastWorkedMarkerCount = workedCount;
                        for (var a = 0; a < approvals.length; a++) {
                          seenApprovalKeys[approvals[a].key] = true;
                        }
                        return;
                      }

                      // 1. Detect newly appearing Approval / Action prompts immediately
                      for (var j = 0; j < approvals.length; j++) {
                        var item = approvals[j];
                        if (!seenApprovalKeys[item.key]) {
                          seenApprovalKeys[item.key] = true;
                          if ((now - (window.__agPulseLastNotifiedAt || 0)) > 4000) {
                            window.__agPulseLastNotifiedAt = now;
                            emitNativeNotification('Antigravity Needs Approval', {
                              body: item.context || ('Action required: tap "' + item.label + '" in your session.'),
                              tag: 'ag_approval_needed'
                            });
                          }
                        }
                      }

                      // 2. Detect live streaming / active work state
                      var activeIndicator = hasActiveWorkIndicator();
                      var delta = textLen - lastTextLen;
                      if (delta > 16 && delta < 4000) {
                        if ((now - lastStreamTickAt) > 5000) {
                          streamTicks = 1;
                        } else if ((now - lastStreamTickAt) >= 450) {
                          streamTicks += 1;
                        }
                        lastStreamTickAt = now;
                        lastActivityAt = now;
                      }
                      lastTextLen = textLen;

                      var workedIncreased = workedCount > lastWorkedMarkerCount;
                      if (workedIncreased) {
                        lastWorkedMarkerCount = workedCount;
                        lastActivityAt = now;
                      }

                      if (activeIndicator || streamTicks >= 3 || workedIncreased) {
                        if (!isWorking) {
                          isWorking = true;
                          workStartedAt = now;
                        }
                        lastActivityAt = now;
                      } else if (isWorking) {
                        var workedDuration = now - workStartedAt;
                        var idleDuration = now - lastActivityAt;
                        if (idleDuration >= 2800) {
                          isWorking = false;
                          streamTicks = 0;
                          if (workedDuration >= 1800 && (now - (window.__agPulseLastNotifiedAt || 0)) > 6000) {
                            window.__agPulseLastNotifiedAt = now;
                            emitNativeNotification('Antigravity Agent Finished', {
                              body: extractLatestSnippet(),
                              tag: 'ag_agent_finished'
                            });
                          }
                        }
                      }
                    } catch (e) {}
                  }

                  var throttleTimer = null;
                  var observer = new MutationObserver(function() {
                    domDirty = true;
                    if (throttleTimer) { return; }
                    throttleTimer = setTimeout(function() {
                      throttleTimer = null;
                      inspectState(false);
                    }, 900);
                  });
                  observer.observe(document.body, { childList: true, subtree: true, characterData: true });
                  setInterval(function() { inspectState(true); }, 1600);
                }

                if (document.body) {
                  attachAgentObserver();
                } else {
                  document.addEventListener('DOMContentLoaded', attachAgentObserver, { once: true });
                }
              } catch (e) {}
            })();
        """.trimIndent()
        target.post {
            try {
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
                val activeUrl = webView.url.orEmpty()
                val configuredEndpoint = currentLoadedEndpoint()
                // Only inject private prompts into the user's configured remote origin
                if (configuredEndpoint.isNotBlank() && isSameOrigin(activeUrl, configuredEndpoint)) {
                    webView.evaluateJavascript(js, null)
                }
            } catch (_: Exception) {
            }
        }
    }

    fun injectDomLagOptimizer(webView: WebView? = pinnedWebView) {
        domLagBoosterActive = true
        val target = webView ?: return
        // Preserve CSS animations so running spinners, progress rings, and thinking indicators
        // stay fluid while eliminating 300ms tap delay and heavy smooth-scroll jank.
        val js = """
            (function() {
              var id = 'antigravity-pulse-dom-booster';
              var style = document.getElementById(id);
              if (!style && document.head) {
                style = document.createElement('style');
                style.id = id;
                document.head.appendChild(style);
              }
              if (style) {
                style.textContent =
                  'html, body { -webkit-tap-highlight-color: transparent !important; ' +
                  'touch-action: pan-x pan-y pinch-zoom; overscroll-behavior-y: contain; }';
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

    fun buildEmptyWebBridgeNoticeHtml(): String {
        return """
            <!DOCTYPE html>
            <html>
            <head>
              <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no, viewport-fit=cover">
              <style>
                * { box-sizing: border-box; -webkit-font-smoothing: antialiased; text-rendering: optimizeLegibility; }
                html, body {
                  margin: 0; padding: 24px 16px 40px 16px; width: 100%; max-width: 100vw; overflow-x: hidden;
                  background: #000000; color: #FFFFFF;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
                  display: flex; flex-direction: column; align-items: center; justify-content: flex-start;
                  min-height: 100vh; text-align: left; gap: 16px;
                }
                .box {
                  width: 100%; max-width: 420px; background: #0A0A0D; border: 1px solid #202026;
                  border-radius: 22px; padding: 22px 20px;
                }
                .box.studio {
                  border-color: rgba(125, 211, 252, 0.28);
                  background: linear-gradient(180deg, rgba(125, 211, 252, 0.06) 0%, #0A0A0D 42%);
                }
                .badge {
                  display: inline-block; font-size: 11px; font-weight: 700; letter-spacing: 0.6px;
                  text-transform: uppercase; color: #FFC799; background: rgba(255, 199, 153, 0.12);
                  border: 1px solid rgba(255, 199, 153, 0.28); border-radius: 999px;
                  padding: 4px 10px; margin-bottom: 12px;
                }
                .badge.studio {
                  color: #7DD3FC; background: rgba(125, 211, 252, 0.12);
                  border-color: rgba(125, 211, 252, 0.32);
                }
                .section-label {
                  font-size: 11px; font-weight: 700; letter-spacing: 0.7px; text-transform: uppercase;
                  color: #A1A1AA; margin: 0 0 12px 0;
                }
                h3 { margin: 0 0 8px 0; color: #FFFFFF; font-size: 20px; font-weight: 700; }
                p.lead { margin: 0 0 16px 0; color: #A1A1AA; font-size: 13.5px; line-height: 1.5; }
                .step {
                  display: flex; align-items: flex-start; gap: 10px; margin-bottom: 12px;
                }
                .step:last-of-type { margin-bottom: 0; }
                .num {
                  flex-shrink: 0; width: 22px; height: 22px; border-radius: 50%;
                  background: rgba(255, 199, 153, 0.16); color: #FFC799;
                  font-size: 12px; font-weight: 700; display: flex; align-items: center; justify-content: center;
                  margin-top: 1px;
                }
                .num.studio {
                  background: rgba(125, 211, 252, 0.16); color: #7DD3FC;
                }
                .step-text { color: #D4D4D8; font-size: 13px; line-height: 1.48; }
                .step-text strong { color: #FFFFFF; }
                .hint {
                  margin-top: 16px; padding: 11px 13px; border-radius: 12px;
                  background: rgba(255, 199, 153, 0.08); border: 1px solid rgba(255, 199, 153, 0.24);
                  color: #FFC799; font-size: 12px; line-height: 1.48;
                }
                .hint.studio {
                  background: rgba(125, 211, 252, 0.08); border-color: rgba(125, 211, 252, 0.24);
                  color: #BAE6FD;
                }
              </style>
            </head>
            <body>
              <div class="box">
                <div class="badge">Antigravity Pulse</div>
                <h3>Welcome</h3>
                <p class="lead">Connect your phone to Antigravity Remote and Google AI Studio with fullscreen controls, voice input, native file access, and zero-reload RAM pinning.</p>
                <div class="section-label">How to Use Antigravity Remote</div>
                <div class="step">
                  <div class="num">1</div>
                  <div class="step-text">Tap the translucent <strong>side handle</strong> to <strong>Scan QR Code</strong> or paste your <strong>Remote URL</strong>.</div>
                </div>
                <div class="step">
                  <div class="num">2</div>
                  <div class="step-text"><strong>Drag the side handle</strong> anywhere on screen to reposition it—dock it to either edge or float it freely.</div>
                </div>
                <div class="step">
                  <div class="num">3</div>
                  <div class="step-text">Tap <strong>+</strong> to attach files from your phone, tap the <strong>microphone</strong> for voice input, and use <strong>Cloud Sync</strong> to back up hosts &amp; prompts.</div>
                </div>
              </div>

              <div class="box studio">
                <div class="badge studio">Google AI Studio &bull; Pinned in RAM</div>
                <h3>AI Studio — How to Use It</h3>
                <p class="lead">Run Gemini prompts, open Build Apps, and browse your Drive library in distraction-free fullscreen without losing your session when switching tabs.</p>
                <div class="step">
                  <div class="num studio">1</div>
                  <div class="step-text">Tap the translucent <strong>side handle</strong> and select <strong>AI Studio</strong> (or switch tabs when bars are shown) to launch <strong>aistudio.google.com</strong> in fullscreen.</div>
                </div>
                <div class="step">
                  <div class="num studio">2</div>
                  <div class="step-text">While inside AI Studio, tap the <strong>side handle</strong> anytime for one-tap <strong>Prompts</strong>, <strong>Build Apps</strong>, <strong>Library</strong>, <strong>Reload</strong>, and <strong>Open in Browser</strong> shortcuts without cluttering your view.</div>
                </div>
                <div class="step">
                  <div class="num studio">3</div>
                  <div class="step-text">Keep <strong>Lag Booster</strong> active for smoother scrolling, tap the <strong>microphone / camera</strong> icons for voice &amp; live input, and tap <strong>+</strong> to upload photos, PDFs, or code files directly from Android.</div>
                </div>
                <div class="hint studio">Tip: Open <strong>Settings &rarr; Device Permissions</strong> from the side handle to verify Microphone, Camera, Notifications, and Native File Access anytime.</div>
              </div>
            </body>
            </html>
        """.trimIndent()
    }
}
