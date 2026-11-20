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
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.BuildConfig
import com.example.notifications.PulseNotificationManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AiStudioWebSection(val label: String, val url: String) {
    NEW_CHAT("Prompts", "https://aistudio.google.com/prompts/new_chat"),
    BUILD_APPS("Build Apps", "https://aistudio.google.com/apps"),
    DRIVE_LIBRARY("Library", "https://aistudio.google.com/library")
}

/**
 * Independent RAM-pinned WebView singleton dedicated to Google AI Studio (`aistudio.google.com`).
 *
 * Keeps your Google AI Studio Build apps, prompts, and Drive library pinned in memory alongside
 * the Antigravity Remote pinned WebView so switching tabs never triggers cold reloads. Shares the
 * Lag Booster engine (CSS animation acceleration, hardware layer, RENDERER_PRIORITY_IMPORTANT,
 * background timer keep-alive, HiDPI mobile viewport clamping, native file chooser, and clean
 * Web Notification bridging).
 */
object AiStudioPinnedWebViewManager {
    @SuppressLint("StaticFieldLeak")
    private var pinnedAiStudioWebView: WebView? = null
    private var currentLoadedUrl: String = ""
    private var domLagBoosterActive: Boolean = true

    private val _canGoBackState = MutableStateFlow(false)
    val canGoBackState: StateFlow<Boolean> = _canGoBackState.asStateFlow()

    private val _activeSectionState = MutableStateFlow(AiStudioWebSection.NEW_CHAT)
    val activeSectionState: StateFlow<AiStudioWebSection> = _activeSectionState.asStateFlow()

    private val _pageLoadProgress = MutableStateFlow(100)
    val pageLoadProgress: StateFlow<Int> = _pageLoadProgress.asStateFlow()

    private val _continuityState = MutableStateFlow(WebViewContinuityState())
    val continuityState: StateFlow<WebViewContinuityState> = _continuityState.asStateFlow()

    private var lastGoodUrl: String = AiStudioWebSection.NEW_CHAT.url
    private var mainFrameFailedInCurrentLoad: Boolean = false

    private fun captureLatestSnapshotIfHealthy(view: WebView? = pinnedAiStudioWebView) {
        val target = view ?: return
        if (mainFrameFailedInCurrentLoad) return
        WebViewOfflineCacheHelper.runOnMainThread {
            val captured = WebViewOfflineCacheHelper.captureWebViewSnapshot(target)
            if (captured != null) {
                _continuityState.value = _continuityState.value.copy(cachedSnapshot = captured)
            }
        }
    }

    fun onNetworkLost() {
        val webView = pinnedAiStudioWebView
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
                statusMessage = "Offline • Holding AI Studio view until internet returns"
            )
        }
    }

    fun onNetworkRestored(autoReloadAfterLongOutage: Boolean) {
        val webView = pinnedAiStudioWebView
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
                ?: AiStudioWebSection.NEW_CHAT.url
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
                    statusMessage = "Back online • Syncing AI Studio..."
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
            _continuityState.value = prev.copy(
                isOnline = true,
                status = OfflineRecoveryStatus.RESTORED,
                showingSnapshotShield = false,
                statusMessage = "Back online • AI Studio live"
            )
        }
    }

    fun refreshWithSnapshotShield(): Boolean {
        val webView = pinnedAiStudioWebView ?: return false
        val targetUrl = lastGoodUrl.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) }
            ?: currentUrl()
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
                statusMessage = "Refreshing AI Studio..."
            )
            try {
                mainFrameFailedInCurrentLoad = false
                webView.loadUrl(targetUrl)
            } catch (_: Exception) {
            }
        }
        return true
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

    private fun updateSectionFromUrl(url: String) {
        val lower = url.lowercase()
        _activeSectionState.value = when {
            lower.contains("/apps") -> AiStudioWebSection.BUILD_APPS
            lower.contains("/library") -> AiStudioWebSection.DRIVE_LIBRARY
            else -> AiStudioWebSection.NEW_CHAT
        }
    }

    private fun refreshCanGoBack(view: WebView? = pinnedAiStudioWebView) {
        _canGoBackState.value = try {
            view?.canGoBack() == true
        } catch (_: Exception) {
            false
        }
    }

    fun isPinnedInRam(): Boolean = pinnedAiStudioWebView != null

    fun currentUrl(): String = currentLoadedUrl.ifBlank { AiStudioWebSection.NEW_CHAT.url }

    fun flushCookies() {
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {
        }
    }

    fun keepTimersRunning() {
        try {
            pinnedAiStudioWebView?.resumeTimers()
        } catch (_: Exception) {
        }
    }

    fun canGoBack(): Boolean = pinnedAiStudioWebView?.canGoBack() == true

    fun goBack(): Boolean {
        val webView = pinnedAiStudioWebView ?: return false
        return if (webView.canGoBack()) {
            webView.goBack()
            refreshCanGoBack(webView)
            true
        } else {
            refreshCanGoBack(webView)
            false
        }
    }

    fun reloadCurrentPage(): Boolean {
        return refreshWithSnapshotShield()
    }

    fun navigateToSection(section: AiStudioWebSection) {
        _activeSectionState.value = section
        currentLoadedUrl = section.url
        lastGoodUrl = section.url
        val webView = pinnedAiStudioWebView ?: return
        webView.post {
            try {
                mainFrameFailedInCurrentLoad = false
                webView.loadUrl(section.url)
            } catch (_: Exception) {
            }
        }
    }

    fun applyLagBooster(enabled: Boolean) {
        domLagBoosterActive = enabled
        val target = pinnedAiStudioWebView ?: return
        // Never override `animation-duration` so loading spinners, running rings, and thinking
        // indicators always animate smoothly while scrolling and taps stay instant.
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
                  'html, body { scroll-behavior: auto !important; -webkit-tap-highlight-color: transparent !important; } ' +
                  'a, button, input, textarea, [role="button"] { touch-action: manipulation !important; }';
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

    /**
     * Lightweight Web Notification shim for AI Studio (bridges explicit `window.Notification`
     * and `ServiceWorkerRegistration.showNotification` calls without triggering false-positive
     * "Run" button approval alerts).
     */
    private fun injectAiStudioNotificationShim(webView: WebView?) {
        val target = webView ?: return
        val prefix = PulseNotificationManager.WEB_NOTIFY_CONSOLE_PREFIX
        val js = """
            (function() {
              try {
                if (window.__agStudioNotifyShimmed) { return; }
                window.__agStudioNotifyShimmed = true;
                function emitStudioNotify(title, options) {
                  try {
                    var opts = options || {};
                    var payload = {
                      title: String(title || 'Google AI Studio').slice(0, 120),
                      body: String(opts.body || '').slice(0, 360),
                      tag: String(opts.tag || 'ai_studio_web').slice(0, 64)
                    };
                    console.info('$prefix' + JSON.stringify(payload));
                  } catch (e) {}
                }
                function StudioNotification(title, options) {
                  this.title = String(title || 'Google AI Studio');
                  this.body = String((options && options.body) || '');
                  this.tag = String((options && options.tag) || 'ai_studio_web');
                  emitStudioNotify(this.title, options);
                }
                StudioNotification.permission = 'granted';
                StudioNotification.requestPermission = function(cb) {
                  if (typeof cb === 'function') { try { cb('granted'); } catch (e) {} }
                  return Promise.resolve('granted');
                };
                StudioNotification.prototype.close = function() {};
                try {
                  Object.defineProperty(window, 'Notification', {
                    value: StudioNotification,
                    writable: true,
                    configurable: true
                  });
                } catch (e) {
                  window.Notification = StudioNotification;
                }
                if (window.ServiceWorkerRegistration && window.ServiceWorkerRegistration.prototype) {
                  var origShow = window.ServiceWorkerRegistration.prototype.showNotification;
                  window.ServiceWorkerRegistration.prototype.showNotification = function(title, options) {
                    emitStudioNotify(title, options);
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

    fun clearSensitiveBrowsingData() {
        try {
            pinnedAiStudioWebView?.clearCache(true)
            pinnedAiStudioWebView?.clearHistory()
            pinnedAiStudioWebView?.clearFormData()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (_: Exception) {
        }
        currentLoadedUrl = ""
        lastGoodUrl = AiStudioWebSection.NEW_CHAT.url
        mainFrameFailedInCurrentLoad = false
        _continuityState.value = WebViewContinuityState()
        _canGoBackState.value = false
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun obtainPinnedWebView(
        context: Context,
        initialSection: AiStudioWebSection,
        domLagBooster: Boolean,
        onPageEvent: (String) -> Unit
    ): WebView {
        domLagBoosterActive = domLagBooster
        val appContext = context.applicationContext
        val webView = pinnedAiStudioWebView ?: WebView(appContext).apply {
            try {
                WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
            } catch (_: Exception) {
            }

            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(android.graphics.Color.BLACK)
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setInitialScale(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
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
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    safeBrowsingEnabled = true
                }
                useWideViewPort = true
                loadWithOverviewMode = false
                layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
                textZoom = 100
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                mediaPlaybackRequiresUserGesture = false
                userAgentString = RetainedWebViewManager.buildCleanMobileUserAgent(userAgentString.orEmpty())
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
                        val parsed = PulseNotificationManager.parseWebNotificationConsolePayload(raw)
                        if (parsed != null) {
                            RetainedWebViewManager.onWebNotificationReceived?.invoke(
                                parsed.title,
                                parsed.body,
                                parsed.tag
                            )
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
                        if (url.contains("aistudio.google.com", ignoreCase = true)) {
                            updateSectionFromUrl(url)
                        }
                        injectAiStudioNotificationShim(view)
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    refreshCanGoBack(view)
                    if (!url.isNullOrBlank() && url.contains("aistudio.google.com", ignoreCase = true)) {
                        currentLoadedUrl = url
                        lastGoodUrl = url
                        updateSectionFromUrl(url)
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
                                    "Offline • Holding AI Studio view until internet returns"
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
                    onPageEvent("Blocked untrusted SSL certificate in AI Studio Web workspace.")
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
                        if (currentLoadedUrl.contains("aistudio.google.com", ignoreCase = true)) {
                            updateSectionFromUrl(currentLoadedUrl)
                        }
                        val prev = _continuityState.value
                        val wasRecovering = prev.status == OfflineRecoveryStatus.RECONNECTING || prev.hadMainFrameError
                        _continuityState.value = prev.copy(
                            isOnline = true,
                            hadMainFrameError = false,
                            showingSnapshotShield = false,
                            status = if (wasRecovering) OfflineRecoveryStatus.RESTORED else OfflineRecoveryStatus.ONLINE,
                            statusMessage = if (wasRecovering) "Back online • AI Studio updated" else ""
                        )
                        injectAiStudioNotificationShim(view)
                        view?.postDelayed({ captureLatestSnapshotIfHealthy(view) }, 650L)
                    }
                    if (!isErrorPage) {
                        RetainedWebViewManager.injectMobileLayoutFix(view)
                        applyLagBooster(domLagBoosterActive)
                    }
                }
            }
            pinnedAiStudioWebView = this
        }

        (webView.parent as? ViewGroup)?.removeView(webView)

        if (currentLoadedUrl.isBlank()) {
            currentLoadedUrl = initialSection.url
            webView.loadUrl(initialSection.url)
        } else {
            applyLagBooster(domLagBooster)
        }
        return webView
    }
}
