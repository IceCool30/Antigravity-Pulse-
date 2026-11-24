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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class WorkspaceShortcut(
    val id: String,
    val label: String,
    val url: String
)

enum class DevWebWorkspace(
    val id: String,
    val displayName: String,
    val hostDomain: String,
    val shortcuts: List<WorkspaceShortcut>
) {
    GITHUB(
        id = "github",
        displayName = "GitHub",
        hostDomain = "github.com",
        shortcuts = listOf(
            WorkspaceShortcut("home", "Home", "https://github.com/"),
            WorkspaceShortcut("repos", "Repos", "https://github.com/?tab=repositories"),
            WorkspaceShortcut("pulls", "Pulls", "https://github.com/pulls"),
            WorkspaceShortcut("alerts", "Alerts", "https://github.com/notifications")
        )
    ),
    VERCEL(
        id = "vercel",
        displayName = "Vercel",
        hostDomain = "vercel.com",
        shortcuts = listOf(
            WorkspaceShortcut("dashboard", "Dashboard", "https://vercel.com/dashboard"),
            WorkspaceShortcut("deployments", "Deployments", "https://vercel.com/dashboard/deployments"),
            WorkspaceShortcut("domains", "Domains", "https://vercel.com/dashboard/domains")
        )
    ),
    SUPABASE(
        id = "supabase",
        displayName = "Supabase",
        hostDomain = "supabase.com",
        shortcuts = listOf(
            WorkspaceShortcut("projects", "Projects", "https://supabase.com/dashboard/projects"),
            WorkspaceShortcut("account", "Account", "https://supabase.com/dashboard/account/me"),
            WorkspaceShortcut("docs", "Docs", "https://supabase.com/docs")
        )
    ),
    GEMINI(
        id = "gemini",
        displayName = "Gemini",
        hostDomain = "gemini.google.com",
        shortcuts = listOf(
            WorkspaceShortcut("chat", "Chat", "https://gemini.google.com/app"),
            WorkspaceShortcut("gems", "Gems", "https://gemini.google.com/gems/view"),
            WorkspaceShortcut("saved", "Saved", "https://gemini.google.com/saved-info")
        )
    );

    val defaultShortcut: WorkspaceShortcut get() = shortcuts.first()
}

/**
 * Independent lazy RAM-pinned WebView session for a first-class developer web workspace
 * (GitHub, Vercel, or Supabase).
 *
 * Each session allocates its WebView only on first visit and retains it in RAM across tab
 * switches with Lag Booster, per-workspace Desktop View toggling, Offline Visual Continuity,
 * OAuth-compatible clean User-Agent & cookie handling, native file picker, hardened downloads,
 * and strict SSL rejection.
 */
class PinnedWebWorkspaceSession(val workspace: DevWebWorkspace) {

    @SuppressLint("StaticFieldLeak")
    private var pinnedWebView: WebView? = null
    private var defaultMobileUserAgent: String = ""
    private var currentLoadedUrl: String = ""
    private var domLagBoosterActive: Boolean = true
    private var lastGoodUrl: String = workspace.defaultShortcut.url
    private var mainFrameFailedInCurrentLoad: Boolean = false
    private var savedStateBackup: WebViewStateBackup? = null
    private var pendingRestoreScroll: Pair<Int, Int>? = null
    private var pinnedSinceElapsedMs: Long = 0L

    private val _canGoBackState = MutableStateFlow(false)
    val canGoBackState: StateFlow<Boolean> = _canGoBackState.asStateFlow()

    private val _activeShortcutId = MutableStateFlow(workspace.defaultShortcut.id)
    val activeShortcutId: StateFlow<String> = _activeShortcutId.asStateFlow()

    private val _isDesktopModeState = MutableStateFlow(false)
    val isDesktopModeState: StateFlow<Boolean> = _isDesktopModeState.asStateFlow()

    private val _pageLoadProgress = MutableStateFlow(100)
    val pageLoadProgress: StateFlow<Int> = _pageLoadProgress.asStateFlow()

    private val _continuityState = MutableStateFlow(WebViewContinuityState())
    val continuityState: StateFlow<WebViewContinuityState> = _continuityState.asStateFlow()

    private val _rendererGeneration = MutableStateFlow(0)
    val rendererGeneration: StateFlow<Int> = _rendererGeneration.asStateFlow()

    fun isPinnedInRam(): Boolean = pinnedWebView != null

    fun pinnedSinceElapsedMs(): Long = pinnedSinceElapsedMs

    fun hasStateBackup(): Boolean = savedStateBackup != null

    fun currentUrl(): String {
        val live = pinnedWebView?.url?.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) }
        return live ?: currentLoadedUrl.ifBlank { lastGoodUrl.ifBlank { workspace.defaultShortcut.url } }
    }

    private fun refreshCanGoBack(view: WebView? = pinnedWebView) {
        _canGoBackState.value = try {
            view?.canGoBack() == true
        } catch (_: Exception) {
            false
        }
    }

    private fun updateShortcutFromUrl(url: String) {
        val lower = url.lowercase()
        val matched = when (workspace) {
            DevWebWorkspace.GITHUB -> when {
                lower.contains("/notifications") -> "alerts"
                lower.contains("/pulls") || lower.contains("/issues") -> "pulls"
                lower.contains("tab=repositories") -> "repos"
                else -> "home"
            }

            DevWebWorkspace.VERCEL -> when {
                lower.contains("/domains") -> "domains"
                lower.contains("/deployments") -> "deployments"
                else -> "dashboard"
            }

            DevWebWorkspace.SUPABASE -> when {
                lower.contains("/docs") -> "docs"
                lower.contains("/account") -> "account"
                else -> "projects"
            }

            DevWebWorkspace.GEMINI -> when {
                lower.contains("/gems") -> "gems"
                lower.contains("/saved-info") -> "saved"
                else -> "chat"
            }
        }
        _activeShortcutId.value = matched
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
                statusMessage = "Offline • Holding ${workspace.displayName} view until internet returns"
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
                ?: workspace.defaultShortcut.url
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
                    showingSnapshotShield = true,
                    statusMessage = "Reconnecting • Updating ${workspace.displayName}..."
                )
                mainFrameFailedInCurrentLoad = false
                webView.loadUrl(targetUrl)
            }
        } else if (prev.status != OfflineRecoveryStatus.ONLINE) {
            _continuityState.value = prev.copy(
                isOnline = true,
                status = OfflineRecoveryStatus.RESTORED,
                showingSnapshotShield = false,
                statusMessage = "Back online • ${workspace.displayName} ready"
            )
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
            ?: workspace.defaultShortcut.url
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
                statusMessage = "Refreshing ${workspace.displayName}..."
            )
            mainFrameFailedInCurrentLoad = false
            webView.loadUrl(targetUrl)
        }
    }

    fun navigateToShortcut(shortcut: WorkspaceShortcut) {
        _activeShortcutId.value = shortcut.id
        currentLoadedUrl = shortcut.url
        lastGoodUrl = shortcut.url
        pinnedWebView?.let { wv ->
            WebViewOfflineCacheHelper.runOnMainThread {
                wv.loadUrl(shortcut.url)
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
                buildCleanDesktopUserAgent(baseUa)
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

    fun reloadCurrentPage() {
        pinnedWebView?.let { wv ->
            WebViewOfflineCacheHelper.runOnMainThread {
                val live = wv.url
                if (!live.isNullOrBlank() && RetainedWebViewManager.isSafeHttpUrl(live)) {
                    wv.reload()
                } else {
                    wv.loadUrl(currentLoadedUrl.ifBlank { workspace.defaultShortcut.url })
                }
            }
        }
    }

    fun goBack(): Boolean {
        val wv = pinnedWebView ?: return false
        return if (wv.canGoBack()) {
            wv.goBack()
            refreshCanGoBack(wv)
            true
        } else {
            false
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
        val js = if (enabled) {
            """
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
                  '${RetainedWebViewManager.LAG_BOOSTER_CSS}';
              }
            })();
            """.trimIndent()
        } else {
            """
            (function() {
              var el = document.getElementById('antigravity-pulse-dom-booster');
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
        // Only inject authenticated notification nonce into verified HTTPS pages for this workspace domain
        if (!RetainedWebViewManager.isHostMatchingDomain(candidateUrl, workspace.hostDomain, requireHttps = true)) {
            return
        }
        val prefix = PulseNotificationManager.buildAuthenticatedConsolePrefix()
        val workspaceName = workspace.displayName
        val tagDefault = "${workspace.id}_web"
        val js = """
            (function() {
              try {
                if (window.__agWorkspaceNotifyShimmed) { return; }
                window.__agWorkspaceNotifyShimmed = true;
                var nativeConsoleInfo = console.info.bind(console);
                function emitWorkspaceNotify(title, options) {
                  try {
                    var opts = options || {};
                    var payload = {
                      title: String(title || '$workspaceName').slice(0, 120),
                      body: String(opts.body || '').slice(0, 360),
                      tag: String(opts.tag || '$tagDefault').slice(0, 64)
                    };
                    nativeConsoleInfo('$prefix' + JSON.stringify(payload));
                  } catch (e) {}
                }
                function WorkspaceNotification(title, options) {
                  this.title = String(title || '$workspaceName');
                  this.body = String((options && options.body) || '');
                  this.tag = String((options && options.tag) || '$tagDefault');
                  emitWorkspaceNotify(this.title, options);
                }
                WorkspaceNotification.permission = 'granted';
                WorkspaceNotification.requestPermission = function(cb) {
                  if (typeof cb === 'function') { try { cb('granted'); } catch (e) {} }
                  return Promise.resolve('granted');
                };
                WorkspaceNotification.prototype.close = function() {};
                try {
                  Object.defineProperty(window, 'Notification', {
                    value: WorkspaceNotification,
                    writable: true,
                    configurable: true
                  });
                } catch (e) {
                  window.Notification = WorkspaceNotification;
                }
                if (window.ServiceWorkerRegistration && window.ServiceWorkerRegistration.prototype) {
                  var origShow = window.ServiceWorkerRegistration.prototype.showNotification;
                  window.ServiceWorkerRegistration.prototype.showNotification = function(title, options) {
                    emitWorkspaceNotify(title, options);
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
        currentLoadedUrl = ""
        lastGoodUrl = workspace.defaultShortcut.url
        mainFrameFailedInCurrentLoad = false
        savedStateBackup = null
        pendingRestoreScroll = null
        pinnedSinceElapsedMs = 0L
        _activeShortcutId.value = workspace.defaultShortcut.id
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
            isHorizontalScrollBarEnabled = false
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
                javaScriptCanOpenWindowsAutomatically = false
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
                        if (RetainedWebViewManager.isHostMatchingDomain(activePageUrl, workspace.hostDomain, requireHttps = true)) {
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
                        lastGoodUrl = url
                        if (RetainedWebViewManager.isHostMatchingDomain(url, workspace.hostDomain, requireHttps = true)) {
                            updateShortcutFromUrl(url)
                            injectNotificationShim(view, url)
                        }
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    refreshCanGoBack(view)
                    if (!url.isNullOrBlank() &&
                        RetainedWebViewManager.isHostMatchingDomain(url, workspace.hostDomain, requireHttps = true)
                    ) {
                        currentLoadedUrl = url
                        lastGoodUrl = url
                        updateShortcutFromUrl(url)
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
                                    "Offline • Holding ${workspace.displayName} view until internet returns"
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
                    onPageEvent("Blocked untrusted SSL certificate in ${workspace.displayName} workspace.")
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
                        currentLoadedUrl = url.orEmpty()
                        lastGoodUrl = url.orEmpty()
                        if (RetainedWebViewManager.isHostMatchingDomain(currentLoadedUrl, workspace.hostDomain, requireHttps = true)) {
                            updateShortcutFromUrl(currentLoadedUrl)
                            injectNotificationShim(view, currentLoadedUrl)
                        }
                        val prev = _continuityState.value
                        val wasRecovering = prev.status == OfflineRecoveryStatus.RECONNECTING || prev.hadMainFrameError
                        _continuityState.value = prev.copy(
                            isOnline = true,
                            hadMainFrameError = false,
                            showingSnapshotShield = false,
                            status = if (wasRecovering) OfflineRecoveryStatus.RESTORED else OfflineRecoveryStatus.ONLINE,
                            statusMessage = if (wasRecovering) "Back online • ${workspace.displayName} updated" else ""
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
                        if (!_isDesktopModeState.value) {
                            RetainedWebViewManager.injectMobileLayoutFix(view)
                        }
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
                    onPageEvent("${workspace.displayName} renderer recovered at your last page.")
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
                ?: workspace.defaultShortcut.url
            val backup = savedStateBackup
            currentLoadedUrl = backup?.url?.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) } ?: fallbackUrl
            lastGoodUrl = currentLoadedUrl
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

    companion object {
        fun buildCleanDesktopUserAgent(rawUa: String): String {
            val chromeMatch = Regex("Chrome/[\\d.]+").find(rawUa)?.value ?: "Chrome/131.0.0.0"
            return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) $chromeMatch Safari/537.36"
        }
    }
}

object DevWebWorkspaceRegistry {
    private val sessions: Map<DevWebWorkspace, PinnedWebWorkspaceSession> =
        DevWebWorkspace.entries.associateWith { PinnedWebWorkspaceSession(it) }

    fun session(workspace: DevWebWorkspace): PinnedWebWorkspaceSession =
        sessions.getValue(workspace)

    fun onNetworkLostAll() {
        sessions.values.forEach { it.onNetworkLost() }
    }

    fun onNetworkRestoredAll(autoReloadAfterLongOutage: Boolean) {
        sessions.values.forEach { it.onNetworkRestored(autoReloadAfterLongOutage) }
    }

    fun keepTimersRunningAll() {
        sessions.values.forEach { it.keepTimersRunning() }
    }

    fun flushCookiesAll() {
        sessions.values.forEach { it.flushCookies() }
    }

    fun dispatchKeepAliveHeartbeatAll() {
        sessions.values.forEach { if (it.isPinnedInRam()) it.dispatchKeepAliveHeartbeat() }
    }

    fun saveAllStateBackups() {
        sessions.values.forEach { if (it.isPinnedInRam()) it.saveStateBackup() }
    }

    fun allSessions(): Collection<PinnedWebWorkspaceSession> = sessions.values

    fun applyLagBoosterAll(enabled: Boolean) {
        sessions.values.forEach { if (it.isPinnedInRam()) it.applyLagBooster(enabled) }
    }

    fun clearAllSensitiveBrowsingData(context: Context? = null) {
        sessions.values.forEach { it.clearSensitiveBrowsingData(context) }
    }
}
