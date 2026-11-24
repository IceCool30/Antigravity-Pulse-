package com.example.data.remote

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class T3EnvironmentProfile(
    val id: String,
    val label: String,
    val baseUrl: String,
    val isBuiltInCloud: Boolean = false
)

/**
 * Dedicated lazy RAM-pinned WebView engine for **T3 Code Web** (`https://app.t3.codes/`)
 * and all user-connected T3 Code environments (local `t3 serve`, Tailscale HTTPS hosts,
 * remote VMs/sandboxes, and T3 Connect pairing links).
 *
 * Key capabilities tailored for T3 Code:
 * - Defaults directly to `https://app.t3.codes/` with full WebSocket, IndexedDB, LocalStorage,
 *   and cookie persistence across tab switches and app backgrounding.
 * - Preserves unconstrained horizontal scrolling inside code diffs, split panes, and integrated
 *   web terminals (never forces destructive `overflow-x: hidden !important`).
 * - Supports safe in-app multi-window popups (`onCreateWindow`) for OAuth sign-in flows and
 *   `window.open` / `target="_blank"` actions.
 * - Preserves one-time `#token=...` / `?token=...` pairing fragments for the live handshake while
 *   strictly stripping secrets before bookmarking to local preferences or showing notifications.
 */
object T3CodePinnedWebViewManager {
    const val T3_CLOUD_ENV_ID = "t3_cloud"
    const val T3_CLOUD_DEFAULT_URL = "https://app.t3.codes/"
    const val T3_CLOUD_DOMAIN = "t3.codes"

    private const val PREFS_NAME = "t3_code_environments_prefs"
    private const val KEY_SAVED_ENVIRONMENTS_JSON = "saved_custom_environments_json"
    private const val KEY_ACTIVE_ENV_ID = "active_environment_id"
    private const val MAX_CUSTOM_ENVIRONMENTS = 24

    val CLOUD_ENVIRONMENT = T3EnvironmentProfile(
        id = T3_CLOUD_ENV_ID,
        label = "T3 Code Cloud",
        baseUrl = T3_CLOUD_DEFAULT_URL,
        isBuiltInCloud = true
    )

    @SuppressLint("StaticFieldLeak")
    private var pinnedWebView: WebView? = null
    private var defaultMobileUserAgent: String = ""
    private var currentLoadedUrl: String = ""
    private var lastGoodUrl: String = T3_CLOUD_DEFAULT_URL
    private var domLagBoosterActive: Boolean = true
    private var mainFrameFailedInCurrentLoad: Boolean = false
    private var prefsLoaded: Boolean = false
    private var savedStateBackup: WebViewStateBackup? = null
    private var pendingRestoreScroll: Pair<Int, Int>? = null
    private var pinnedSinceElapsedMs: Long = 0L

    private val _savedEnvironments = MutableStateFlow<List<T3EnvironmentProfile>>(listOf(CLOUD_ENVIRONMENT))
    val savedEnvironments: StateFlow<List<T3EnvironmentProfile>> = _savedEnvironments.asStateFlow()

    private val _activeEnvironmentId = MutableStateFlow(T3_CLOUD_ENV_ID)
    val activeEnvironmentId: StateFlow<String> = _activeEnvironmentId.asStateFlow()

    private val _currentUrlState = MutableStateFlow(T3_CLOUD_DEFAULT_URL)
    val currentUrlState: StateFlow<String> = _currentUrlState.asStateFlow()

    private val _canGoBackState = MutableStateFlow(false)
    val canGoBackState: StateFlow<Boolean> = _canGoBackState.asStateFlow()

    private val _isDesktopModeState = MutableStateFlow(false)
    val isDesktopModeState: StateFlow<Boolean> = _isDesktopModeState.asStateFlow()

    private val _pageLoadProgress = MutableStateFlow(100)
    val pageLoadProgress: StateFlow<Int> = _pageLoadProgress.asStateFlow()

    private val _continuityState = MutableStateFlow(WebViewContinuityState())
    val continuityState: StateFlow<WebViewContinuityState> = _continuityState.asStateFlow()

    private val _rendererGeneration = MutableStateFlow(0)
    val rendererGeneration: StateFlow<Int> = _rendererGeneration.asStateFlow()

    @SuppressLint("StaticFieldLeak")
    private val _popupWebViewState = MutableStateFlow<WebView?>(null)
    val popupWebViewState: StateFlow<WebView?> = _popupWebViewState.asStateFlow()

    private val _popupUrlState = MutableStateFlow("")
    val popupUrlState: StateFlow<String> = _popupUrlState.asStateFlow()

    fun isPinnedInRam(): Boolean = pinnedWebView != null

    fun pinnedSinceElapsedMs(): Long = pinnedSinceElapsedMs

    fun hasStateBackup(): Boolean = savedStateBackup != null

    fun currentUrl(): String {
        val live = pinnedWebView?.url?.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) }
        return live ?: currentLoadedUrl.ifBlank { activeEnvironment().baseUrl }
    }

    fun activeEnvironment(): T3EnvironmentProfile {
        val id = _activeEnvironmentId.value
        return _savedEnvironments.value.firstOrNull { it.id == id } ?: CLOUD_ENVIRONMENT
    }

    /**
     * Loads saved custom T3 Code environments from local device preferences once.
     */
    fun ensureEnvironmentsLoaded(context: Context) {
        if (prefsLoaded) return
        prefsLoaded = true
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val rawJson = prefs.getString(KEY_SAVED_ENVIRONMENTS_JSON, "").orEmpty()
            val customList = parseCustomEnvironmentsJson(rawJson)
            val merged = listOf(CLOUD_ENVIRONMENT) + customList
            _savedEnvironments.value = merged

            val savedActiveId = prefs.getString(KEY_ACTIVE_ENV_ID, T3_CLOUD_ENV_ID).orEmpty()
            val validActive = merged.firstOrNull { it.id == savedActiveId } ?: CLOUD_ENVIRONMENT
            _activeEnvironmentId.value = validActive.id
            if (currentLoadedUrl.isBlank()) {
                _currentUrlState.value = validActive.baseUrl
                lastGoodUrl = validActive.baseUrl
            }
        } catch (_: Exception) {
            _savedEnvironments.value = listOf(CLOUD_ENVIRONMENT)
            _activeEnvironmentId.value = T3_CLOUD_ENV_ID
        }
    }

    private fun persistEnvironments(context: Context) {
        try {
            val customOnly = _savedEnvironments.value.filterNot { it.isBuiltInCloud }
            val jsonArray = JSONArray()
            customOnly.forEach { env ->
                jsonArray.put(
                    JSONObject().apply {
                        put("id", env.id)
                        put("label", env.label)
                        put("baseUrl", env.baseUrl)
                    }
                )
            }
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putString(KEY_SAVED_ENVIRONMENTS_JSON, jsonArray.toString())
                .putString(KEY_ACTIVE_ENV_ID, _activeEnvironmentId.value)
                .apply()
        } catch (_: Exception) {
        }
    }

    private fun parseCustomEnvironmentsJson(rawJson: String): List<T3EnvironmentProfile> {
        if (rawJson.isBlank()) return emptyList()
        return try {
            val array = JSONArray(rawJson)
            val results = mutableListOf<T3EnvironmentProfile>()
            for (i in 0 until array.length().coerceAtMost(MAX_CUSTOM_ENVIRONMENTS)) {
                val obj = array.optJSONObject(i) ?: continue
                val id = obj.optString("id").trim().take(80)
                val label = obj.optString("label").trim().take(60)
                val sanitizedBase = RetainedWebViewManager.stripSensitiveUrlCredentials(
                    obj.optString("baseUrl").trim()
                )
                if (id.isNotBlank() && id != T3_CLOUD_ENV_ID && label.isNotBlank() &&
                    RetainedWebViewManager.isSafeHttpUrl(sanitizedBase)
                ) {
                    results.add(
                        T3EnvironmentProfile(
                            id = id,
                            label = label,
                            baseUrl = sanitizedBase,
                            isBuiltInCloud = false
                        )
                    )
                }
            }
            results
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Checks whether [originUrl] matches `*.t3.codes` (HTTPS) or the user's currently active
     * saved T3 Code environment origin. Used to gate privileged JS notification shims and media.
     */
    fun isTrustedActiveOrigin(originUrl: String): Boolean {
        if (!RetainedWebViewManager.isSafeHttpUrl(originUrl)) return false
        if (RetainedWebViewManager.isHostMatchingDomain(originUrl, T3_CLOUD_DOMAIN, requireHttps = true)) {
            return true
        }
        val activeBase = activeEnvironment().baseUrl
        return activeBase.isNotBlank() && RetainedWebViewManager.isSameOrigin(originUrl, activeBase)
    }

    /**
     * Checks whether [host] matches the active HTTPS T3 Code environment for scoped download cookies.
     */
    fun isTrustedHttpsDownloadHost(host: String): Boolean {
        val normalized = host.lowercase().trim().trimEnd('.')
        if (normalized.isBlank()) return false
        val activeBase = activeEnvironment().baseUrl
        if (!RetainedWebViewManager.isSafeHttpUrl(activeBase)) return false
        return try {
            val uri = Uri.parse(activeBase)
            uri.scheme?.lowercase() == "https" &&
                uri.host?.lowercase()?.trimEnd('.') == normalized
        } catch (_: Exception) {
            false
        }
    }

    fun selectEnvironment(context: Context, environment: T3EnvironmentProfile) {
        ensureEnvironmentsLoaded(context)
        val cleanUrl = environment.baseUrl.trim()
        if (!RetainedWebViewManager.isSafeHttpUrl(cleanUrl)) return
        _activeEnvironmentId.value = environment.id
        currentLoadedUrl = cleanUrl
        lastGoodUrl = cleanUrl
        _currentUrlState.value = cleanUrl
        persistEnvironments(context)
        pinnedWebView?.let { wv ->
            WebViewOfflineCacheHelper.runOnMainThread {
                mainFrameFailedInCurrentLoad = false
                wv.loadUrl(cleanUrl)
            }
        }
    }

    fun navigateToCloudHome(context: Context) {
        selectEnvironment(context, CLOUD_ENVIRONMENT)
    }

    /**
     * Opens a custom T3 Code server URL or pairing link (including `#token=...` / `?token=...`
     * handshake fragments). If [saveBookmark] is true and the URL is not already `app.t3.codes`,
     * persists ONLY the credential-stripped base URL while loading the intact [rawUrl] live.
     */
    fun openPairingOrCustomUrl(
        context: Context,
        label: String,
        rawUrl: String,
        saveBookmark: Boolean = true
    ): Result<T3EnvironmentProfile> {
        ensureEnvironmentsLoaded(context)
        val trimmed = rawUrl.trim()
        val normalized = when {
            trimmed.startsWith("wss://", ignoreCase = true) -> "https://" + trimmed.substring(6)
            trimmed.startsWith("ws://", ignoreCase = true) -> "http://" + trimmed.substring(5)
            else -> trimmed
        }
        if (!RetainedWebViewManager.isSafeHttpUrl(normalized)) {
            return Result.failure(IllegalArgumentException("Only valid https:// or http:// T3 Code URLs are allowed."))
        }
        val sanitizedBase = RetainedWebViewManager.stripSensitiveUrlCredentials(normalized)
        if (!RetainedWebViewManager.isSafeHttpUrl(sanitizedBase)) {
            return Result.failure(IllegalArgumentException("Invalid T3 Code host address."))
        }

        val isCloudDomain = RetainedWebViewManager.isHostMatchingDomain(
            sanitizedBase,
            T3_CLOUD_DOMAIN,
            requireHttps = true
        )

        val targetProfile: T3EnvironmentProfile = if (isCloudDomain) {
            CLOUD_ENVIRONMENT
        } else {
            val cleanLabel = label.trim().take(60).ifBlank { deriveDefaultEnvLabel(sanitizedBase) }
            val existing = _savedEnvironments.value.firstOrNull {
                !it.isBuiltInCloud && it.baseUrl.equals(sanitizedBase, ignoreCase = true)
            }
            if (saveBookmark) {
                val created = existing?.copy(label = cleanLabel) ?: T3EnvironmentProfile(
                    id = "t3_env_${System.currentTimeMillis()}",
                    label = cleanLabel,
                    baseUrl = sanitizedBase,
                    isBuiltInCloud = false
                )
                val updatedCustom = _savedEnvironments.value
                    .filterNot { it.isBuiltInCloud || it.id == created.id }
                    .take(MAX_CUSTOM_ENVIRONMENTS - 1) + created
                _savedEnvironments.value = listOf(CLOUD_ENVIRONMENT) + updatedCustom
                created
            } else {
                existing ?: T3EnvironmentProfile(
                    id = "t3_ephemeral",
                    label = cleanLabel,
                    baseUrl = sanitizedBase,
                    isBuiltInCloud = false
                )
            }
        }

        _activeEnvironmentId.value = if (targetProfile.id == "t3_ephemeral") {
            _activeEnvironmentId.value
        } else {
            targetProfile.id
        }
        // Keep the full URL (with #token=... if present) only in live RAM for the immediate WebView load
        currentLoadedUrl = normalized
        lastGoodUrl = sanitizedBase
        _currentUrlState.value = sanitizedBase
        persistEnvironments(context)

        pinnedWebView?.let { wv ->
            WebViewOfflineCacheHelper.runOnMainThread {
                mainFrameFailedInCurrentLoad = false
                wv.loadUrl(normalized)
            }
        }
        return Result.success(targetProfile)
    }

    fun removeCustomEnvironment(context: Context, environmentId: String) {
        if (environmentId == T3_CLOUD_ENV_ID) return
        ensureEnvironmentsLoaded(context)
        val remaining = _savedEnvironments.value.filterNot { !it.isBuiltInCloud && it.id == environmentId }
        _savedEnvironments.value = remaining
        if (_activeEnvironmentId.value == environmentId) {
            selectEnvironment(context, CLOUD_ENVIRONMENT)
        } else {
            persistEnvironments(context)
        }
    }

    private fun deriveDefaultEnvLabel(sanitizedUrl: String): String {
        return try {
            val uri = Uri.parse(sanitizedUrl)
            val host = uri.host.orEmpty()
            val port = if (uri.port != -1) ":${uri.port}" else ""
            if (host.isNotBlank()) "T3 ($host$port)".take(60) else "T3 Environment"
        } catch (_: Exception) {
            "T3 Environment"
        }
    }

    private fun refreshCanGoBack(view: WebView? = pinnedWebView) {
        _canGoBackState.value = try {
            view?.canGoBack() == true
        } catch (_: Exception) {
            false
        }
    }

    private fun captureLatestSnapshotIfHealthy(view: WebView? = pinnedWebView) {
        val target = view ?: return
        if (mainFrameFailedInCurrentLoad) return
        WebViewOfflineCacheHelper.runOnMainThread(delayMs = 1200L) {
            if (mainFrameFailedInCurrentLoad) return@runOnMainThread
            val captured = WebViewOfflineCacheHelper.captureVisibleSnapshotThrottled(target)
            if (captured != null) {
                _continuityState.value = _continuityState.value.copy(cachedSnapshot = captured)
            }
        }
    }

    fun onNetworkLost() {
        val webView = pinnedWebView
        WebViewOfflineCacheHelper.applyCacheMode(webView, isOnline = false)
        if (webView == null) {
            _continuityState.value = _continuityState.value.copy(isOnline = false)
            return
        }
        WebViewOfflineCacheHelper.runOnMainThread {
            val fresh = WebViewOfflineCacheHelper.captureWebViewSnapshot(webView)
            val prev = _continuityState.value
            _continuityState.value = prev.copy(
                isOnline = false,
                status = OfflineRecoveryStatus.OFFLINE_CACHED,
                cachedSnapshot = fresh ?: prev.cachedSnapshot,
                statusMessage = "Offline • Holding T3 Code view until internet returns"
            )
        }
    }

    fun onNetworkRestored(autoReloadAfterLongOutage: Boolean) {
        val webView = pinnedWebView
        WebViewOfflineCacheHelper.applyCacheMode(webView, isOnline = true)
        if (webView == null) {
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
            val targetUrl = lastGoodUrl.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) }
                ?: activeEnvironment().baseUrl
            WebViewOfflineCacheHelper.runOnMainThread {
                val snap = if (!prev.hadMainFrameError) {
                    WebViewOfflineCacheHelper.captureWebViewSnapshot(webView) ?: prev.cachedSnapshot
                } else {
                    prev.cachedSnapshot
                }
                _continuityState.value = prev.copy(
                    isOnline = true,
                    status = OfflineRecoveryStatus.RECONNECTING,
                    cachedSnapshot = snap,
                    showingSnapshotShield = snap != null || prev.hadMainFrameError,
                    statusMessage = "Reconnecting • Syncing T3 Code environments..."
                )
                WebViewOfflineCacheHelper.runOnMainThread(delayMs = 650L) {
                    try {
                        mainFrameFailedInCurrentLoad = false
                        webView.loadUrl(targetUrl)
                    } catch (_: Exception) {
                    }
                }
            }
        } else {
            WebViewOfflineCacheHelper.dispatchBrowserOnlineEvent(webView)
            if (prev.status != OfflineRecoveryStatus.ONLINE) {
                _continuityState.value = prev.copy(
                    isOnline = true,
                    status = OfflineRecoveryStatus.RESTORED,
                    showingSnapshotShield = false,
                    statusMessage = "Back online • T3 Code live"
                )
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

    fun reloadOrRetryLatest() {
        val webView = pinnedWebView ?: return
        val targetUrl = lastGoodUrl.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) }
            ?: activeEnvironment().baseUrl
        WebViewOfflineCacheHelper.runOnMainThread {
            WebViewOfflineCacheHelper.applyCacheMode(webView, isOnline = true)
            val prev = _continuityState.value
            val snap = if (!prev.hadMainFrameError) {
                WebViewOfflineCacheHelper.captureWebViewSnapshot(webView) ?: prev.cachedSnapshot
            } else {
                prev.cachedSnapshot
            }
            _continuityState.value = prev.copy(
                status = OfflineRecoveryStatus.RECONNECTING,
                cachedSnapshot = snap,
                showingSnapshotShield = prev.hadMainFrameError || snap != null,
                statusMessage = "Refreshing T3 Code..."
            )
            mainFrameFailedInCurrentLoad = false
            webView.loadUrl(targetUrl)
        }
    }

    fun reloadCurrentPage() {
        pinnedWebView?.let { wv ->
            WebViewOfflineCacheHelper.runOnMainThread {
                val live = wv.url
                if (!live.isNullOrBlank() && RetainedWebViewManager.isSafeHttpUrl(live)) {
                    wv.reload()
                } else {
                    wv.loadUrl(currentLoadedUrl.ifBlank { activeEnvironment().baseUrl })
                }
            }
        }
    }

    fun setDesktopMode(enabled: Boolean) {
        if (_isDesktopModeState.value == enabled) return
        _isDesktopModeState.value = enabled
        val wv = pinnedWebView ?: return
        WebViewOfflineCacheHelper.runOnMainThread {
            applyViewportAndUserAgent(wv, desktopMode = enabled)
            val reloadTarget = currentUrl()
            if (RetainedWebViewManager.isSafeHttpUrl(reloadTarget)) {
                wv.loadUrl(reloadTarget)
            } else {
                wv.reload()
            }
        }
    }

    private fun applyViewportAndUserAgent(webView: WebView, desktopMode: Boolean) {
        val baseUa = defaultMobileUserAgent.ifBlank { webView.settings.userAgentString.orEmpty() }
        webView.settings.apply {
            userAgentString = if (desktopMode) {
                PinnedWebWorkspaceSession.buildCleanDesktopUserAgent(baseUa)
            } else {
                RetainedWebViewManager.buildCleanMobileUserAgent(baseUa)
            }
            useWideViewPort = true
            loadWithOverviewMode = desktopMode
            setSupportZoom(desktopMode)
            builtInZoomControls = desktopMode
            displayZoomControls = false
        }
    }

    fun goBack(): Boolean {
        val popup = _popupWebViewState.value
        if (popup != null) {
            if (popup.canGoBack()) {
                popup.goBack()
            } else {
                dismissPopupWebView()
            }
            return true
        }
        val wv = pinnedWebView ?: return false
        return if (wv.canGoBack()) {
            wv.goBack()
            refreshCanGoBack(wv)
            true
        } else {
            refreshCanGoBack(wv)
            false
        }
    }

    fun dismissPopupWebView() {
        val popup = _popupWebViewState.value
        _popupWebViewState.value = null
        _popupUrlState.value = ""
        if (popup != null) {
            WebViewOfflineCacheHelper.runOnMainThread {
                try {
                    popup.stopLoading()
                    popup.loadUrl("about:blank")
                    (popup.parent as? ViewGroup)?.removeView(popup)
                    popup.destroy()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun keepTimersRunning() {
        WebViewOfflineCacheHelper.keepTimersAliveWithoutResumingHidden(pinnedWebView)
    }

    fun flushCookies() {
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {
        }
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
        dismissPopupWebView()
        pinnedWebView = null
        pinnedSinceElapsedMs = 0L
        currentLoadedUrl = ""
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

    fun applyLagBooster(enabled: Boolean) {
        domLagBoosterActive = enabled
        val target = pinnedWebView ?: return
        // Preserve native animation timing and unconstrained horizontal scrolling for code diffs
        // and integrated terminals while keeping tap response instant.
        val js = if (enabled) {
            """
            (function() {
              var id = 'antigravity-pulse-t3-dom-booster';
              var style = document.getElementById(id);
              if (!style && document.head) {
                style = document.createElement('style');
                style.id = id;
                document.head.appendChild(style);
              }
              if (style) {
                style.textContent =
                  '${RetainedWebViewManager.LAG_BOOSTER_CSS}';
              }
            })();
            """.trimIndent()
        } else {
            """
            (function() {
              var el = document.getElementById('antigravity-pulse-t3-dom-booster');
              if (el && el.parentNode) { el.parentNode.removeChild(el); }
            })();
            """.trimIndent()
        }
        target.post {
            try {
                target.evaluateJavascript(js, null)
            } catch (_: Exception) {
            }
        }
    }

    private fun injectNotificationShim(webView: WebView?, pageUrl: String? = null) {
        val target = webView ?: return
        val candidateUrl = pageUrl ?: try { target.url.orEmpty() } catch (_: Exception) { "" }
        if (!isTrustedActiveOrigin(candidateUrl)) return
        val prefix = PulseNotificationManager.buildAuthenticatedConsolePrefix()
        val js = """
            (function() {
              try {
                if (window.__agT3NotifyShimmed) { return; }
                window.__agT3NotifyShimmed = true;
                var nativeConsoleInfo = console.info.bind(console);
                function emitT3Notify(title, options) {
                  try {
                    var opts = options || {};
                    var payload = {
                      title: String(title || 'T3 Code').slice(0, 120),
                      body: String(opts.body || '').slice(0, 360),
                      tag: String(opts.tag || 't3_code_web').slice(0, 64)
                    };
                    nativeConsoleInfo('$prefix' + JSON.stringify(payload));
                  } catch (e) {}
                }
                function T3Notification(title, options) {
                  this.title = String(title || 'T3 Code');
                  this.body = String((options && options.body) || '');
                  this.tag = String((options && options.tag) || 't3_code_web');
                  emitT3Notify(this.title, options);
                }
                T3Notification.permission = 'granted';
                T3Notification.requestPermission = function(cb) {
                  if (typeof cb === 'function') { try { cb('granted'); } catch (e) {} }
                  return Promise.resolve('granted');
                };
                T3Notification.prototype.close = function() {};
                try {
                  Object.defineProperty(window, 'Notification', {
                    value: T3Notification,
                    writable: true,
                    configurable: true
                  });
                } catch (e) {
                  window.Notification = T3Notification;
                }
                if (window.ServiceWorkerRegistration && window.ServiceWorkerRegistration.prototype) {
                  var origShow = window.ServiceWorkerRegistration.prototype.showNotification;
                  window.ServiceWorkerRegistration.prototype.showNotification = function(title, options) {
                    emitT3Notify(title, options);
                    if (typeof origShow === 'function') {
                      try { return origShow.call(this, title, options); } catch (e) {}
                    }
                    return Promise.resolve();
                  };
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

    @SuppressLint("SetJavaScriptEnabled")
    private fun createPopupWebView(
        appContext: Context,
        onPageEvent: (String) -> Unit
    ): WebView {
        dismissPopupWebView()
        return WebView(appContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            setLayerType(View.LAYER_TYPE_NONE, null)
            isNestedScrollingEnabled = false
            // OVER_SCROLL_NEVER: Android 12+ "stretch" overscroll re-renders the entire WebView
            // through an offscreen stretch layer while dragging past either end of the page,
            // which is a well-known source of dropped frames on mid-range mobile GPUs.
            overScrollMode = View.OVER_SCROLL_NEVER
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            // Pages paint their own scroll indicators; the native overlay scrollbar otherwise
            // invalidates the WebView on every scroll frame.
            isVerticalScrollBarEnabled = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                settings.offscreenPreRaster = true
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
                allowContentAccess = false
                setSupportMultipleWindows(false)
                javaScriptCanOpenWindowsAutomatically = false
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    offscreenPreRaster = true
                }
                useWideViewPort = true
                loadWithOverviewMode = false
                mediaPlaybackRequiresUserGesture = false
            }
            applyViewportAndUserAgent(this, desktopMode = _isDesktopModeState.value)
            try {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
            } catch (_: Exception) {
            }
            webChromeClient = object : WebChromeClient() {
                override fun onCloseWindow(window: WebView?) {
                    super.onCloseWindow(window)
                    dismissPopupWebView()
                }
            }
            webViewClient = object : WebViewClient() {
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    return RetainedWebViewManager.interceptUnsafeSubresource(request)
                        ?: super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUrl = request?.url?.toString().orEmpty()
                    return !RetainedWebViewManager.isSafeHttpUrl(targetUrl)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    if (!url.isNullOrBlank() && RetainedWebViewManager.isSafeHttpUrl(url)) {
                        _popupUrlState.value = RetainedWebViewManager.stripSensitiveUrlCredentials(url)
                    }
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    flushCookies()
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    handler?.cancel()
                    onPageEvent("Blocked untrusted SSL certificate in T3 Code auth popup.")
                    dismissPopupWebView()
                }
            }
        }
    }

    fun clearSensitiveBrowsingData(context: Context? = null) {
        dismissPopupWebView()
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
                it.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply()
            }
        } catch (_: Exception) {
        }
        _continuityState.value.cachedSnapshot?.let { bmp ->
            if (!bmp.isRecycled) bmp.recycle()
        }
        prefsLoaded = false
        currentLoadedUrl = ""
        lastGoodUrl = T3_CLOUD_DEFAULT_URL
        mainFrameFailedInCurrentLoad = false
        savedStateBackup = null
        pendingRestoreScroll = null
        pinnedSinceElapsedMs = 0L
        _savedEnvironments.value = listOf(CLOUD_ENVIRONMENT)
        _activeEnvironmentId.value = T3_CLOUD_ENV_ID
        _currentUrlState.value = T3_CLOUD_DEFAULT_URL
        _isDesktopModeState.value = false
        _continuityState.value = WebViewContinuityState()
        _canGoBackState.value = false
        _rendererGeneration.value = _rendererGeneration.value + 1
        WorkspaceMemoryKeepAliveCoordinator.notifyWorkspaceTopologyChanged()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun obtainPinnedWebView(
        context: Context,
        domLagBooster: Boolean,
        onPageEvent: (String) -> Unit
    ): WebView {
        ensureEnvironmentsLoaded(context)
        domLagBoosterActive = domLagBooster
        val appContext = context.applicationContext
        val webView = pinnedWebView ?: WebView(appContext).apply {
            try {
                WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            } catch (_: Exception) {
            }

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            setLayerType(View.LAYER_TYPE_NONE, null)
            isNestedScrollingEnabled = false
            // OVER_SCROLL_NEVER: Android 12+ "stretch" overscroll re-renders the entire WebView
            // through an offscreen stretch layer while dragging past either end of the page,
            // which is a well-known source of dropped frames on mid-range mobile GPUs.
            overScrollMode = View.OVER_SCROLL_NEVER
            scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            // Pages paint their own scroll indicators; the native overlay scrollbar otherwise
            // invalidates the WebView on every scroll frame.
            isVerticalScrollBarEnabled = false
            setInitialScale(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    setRendererPriorityPolicy(WebView.RENDERER_PRIORITY_IMPORTANT, false)
                } catch (_: Exception) {
                }
            }

            defaultMobileUserAgent = settings.userAgentString.orEmpty()
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
                allowContentAccess = false
                // Support OAuth sign-in popups & window.open flows safely via onCreateWindow
                setSupportMultipleWindows(true)
                javaScriptCanOpenWindowsAutomatically = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    offscreenPreRaster = true
                }
                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                textZoom = 100
                mediaPlaybackRequiresUserGesture = false
            }
            applyViewportAndUserAgent(this, desktopMode = _isDesktopModeState.value)

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

                override fun onCreateWindow(
                    view: WebView?,
                    isDialog: Boolean,
                    isUserGesture: Boolean,
                    resultMsg: Message?
                ): Boolean {
                    val transport = resultMsg?.obj as? WebView.WebViewTransport ?: return false
                    val popupView = createPopupWebView(appContext, onPageEvent)
                    _popupWebViewState.value = popupView
                    transport.webView = popupView
                    resultMsg.sendToTarget()
                    return true
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    if (filePathCallback == null) return false
                    val handler = RetainedWebViewManager.onShowFileChooserHandler
                    if (handler != null) {
                        return handler(filePathCallback, fileChooserParams)
                    }
                    filePathCallback.onReceiveValue(null)
                    return false
                }

                override fun onPermissionRequest(request: PermissionRequest?) {
                    if (request == null) return
                    val originUrl = request.origin?.toString().orEmpty()
                    val handler = RetainedWebViewManager.onWebPermissionRequestHandler
                    if (handler != null && RetainedWebViewManager.isSecureMediaOrigin(originUrl)) {
                        handler(request)
                    } else {
                        request.deny()
                    }
                }

                override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                    if (request != null) {
                        RetainedWebViewManager.onWebPermissionRequestCanceled?.invoke(request)
                    }
                }

                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    val raw = consoleMessage?.message()
                    if (raw != null && raw.startsWith(PulseNotificationManager.WEB_NOTIFY_CONSOLE_PREFIX)) {
                        val activePageUrl = try { pinnedWebView?.url.orEmpty() } catch (_: Exception) { "" }
                        if (isTrustedActiveOrigin(activePageUrl)) {
                            val parsed = PulseNotificationManager.parseAuthenticatedWebNotificationPayload(raw)
                            if (parsed != null) {
                                RetainedWebViewManager.onWebNotificationReceived?.invoke(
                                    parsed.title,
                                    parsed.body,
                                    parsed.tag
                                )
                            }
                        }
                        return true
                    }
                    return super.onConsoleMessage(consoleMessage)
                }
            }

            setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
                if (RetainedWebViewManager.isSafeHttpUrl(url)) {
                    RetainedWebViewManager.onWebDownloadRequested?.invoke(
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
                    return RetainedWebViewManager.interceptUnsafeSubresource(request)
                        ?: super.shouldInterceptRequest(view, request)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?
                ): Boolean {
                    val targetUrl = request?.url?.toString().orEmpty()
                    return !RetainedWebViewManager.isSafeHttpUrl(targetUrl)
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    refreshCanGoBack(view)
                    if (!url.isNullOrBlank() && RetainedWebViewManager.isSafeHttpUrl(url)) {
                        mainFrameFailedInCurrentLoad = false
                        currentLoadedUrl = url
                        val sanitized = RetainedWebViewManager.stripSensitiveUrlCredentials(url)
                        if (sanitized.isNotBlank()) {
                            lastGoodUrl = sanitized
                            _currentUrlState.value = sanitized
                        }
                        if (isTrustedActiveOrigin(url)) {
                            injectNotificationShim(view, url)
                        }
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    refreshCanGoBack(view)
                    if (!url.isNullOrBlank() && RetainedWebViewManager.isSafeHttpUrl(url)) {
                        currentLoadedUrl = url
                        val sanitized = RetainedWebViewManager.stripSensitiveUrlCredentials(url)
                        if (sanitized.isNotBlank()) {
                            lastGoodUrl = sanitized
                            _currentUrlState.value = sanitized
                        }
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
                                    "Offline • Holding T3 Code view until internet returns"
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
                    handler?.cancel()
                    onPageEvent("Blocked untrusted SSL certificate in T3 Code workspace.")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    _pageLoadProgress.value = 100
                    refreshCanGoBack(view)
                    flushCookies()
                    val isErrorPage = url.isNullOrBlank() ||
                        url.startsWith("chrome-error:", ignoreCase = true) ||
                        url.startsWith("about:", ignoreCase = true)
                    if (!mainFrameFailedInCurrentLoad && !isErrorPage && RetainedWebViewManager.isSafeHttpUrl(url.orEmpty())) {
                        val safeLive = url.orEmpty()
                        currentLoadedUrl = safeLive
                        val sanitized = RetainedWebViewManager.stripSensitiveUrlCredentials(safeLive)
                        if (sanitized.isNotBlank()) {
                            lastGoodUrl = sanitized
                            _currentUrlState.value = sanitized
                        }
                        if (isTrustedActiveOrigin(safeLive)) {
                            injectNotificationShim(view, safeLive)
                        }
                        val prev = _continuityState.value
                        val wasRecovering = prev.status == OfflineRecoveryStatus.RECONNECTING || prev.hadMainFrameError
                        _continuityState.value = prev.copy(
                            isOnline = true,
                            hadMainFrameError = false,
                            showingSnapshotShield = false,
                            status = if (wasRecovering) OfflineRecoveryStatus.RESTORED else OfflineRecoveryStatus.ONLINE,
                            statusMessage = if (wasRecovering) "Back online • T3 Code updated" else ""
                        )
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
                        applyLagBooster(domLagBoosterActive)
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
                    currentLoadedUrl = ""
                    WebViewOfflineCacheHelper.runOnMainThread {
                        try {
                            (crashedView?.parent as? ViewGroup)?.removeView(crashedView)
                            crashedView?.destroy()
                        } catch (_: Exception) {
                        }
                    }
                    _rendererGeneration.value = _rendererGeneration.value + 1
                    WorkspaceMemoryKeepAliveCoordinator.notifyWorkspaceTopologyChanged()
                    onPageEvent("T3 Code renderer recovered at your last session.")
                    return true
                }
            }
            pinnedWebView = this
            pinnedSinceElapsedMs = android.os.SystemClock.elapsedRealtime()
            WorkspaceMemoryKeepAliveCoordinator.notifyWorkspaceTopologyChanged()
        }

        (webView.parent as? ViewGroup)?.removeView(webView)

        if (currentLoadedUrl.isBlank()) {
            val fallbackUrl = lastGoodUrl.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) }
                ?: activeEnvironment().baseUrl
            val backup = savedStateBackup
            val targetUrl = backup?.url?.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) } ?: fallbackUrl
            currentLoadedUrl = targetUrl
            lastGoodUrl = RetainedWebViewManager.stripSensitiveUrlCredentials(targetUrl).ifBlank { fallbackUrl }
            _currentUrlState.value = lastGoodUrl
            if (backup != null) {
                pendingRestoreScroll = backup.scrollX to backup.scrollY
                WebViewOfflineCacheHelper.restoreStateBackupOrLoad(webView, backup, fallbackUrl)
            } else {
                webView.loadUrl(fallbackUrl)
            }
        } else {
            applyLagBooster(domLagBooster)
        }
        return webView
    }
}
