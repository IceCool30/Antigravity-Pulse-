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
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.BuildConfig
import com.example.notifications.PulseNotificationManager
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
     * `javascript:`, `intent:`, `data:`), control characters, whitespace, oversized URLs, and
     * scheme-only strings lacking a valid host.
     */
    fun isSafeHttpUrl(url: String): Boolean {
        val clean = url.trim()
        if (clean.isEmpty() || clean.length > MAX_URL_LENGTH) return false
        if (clean.any { it.isISOControl() || it.isWhitespace() }) return false
        return try {
            val uri = Uri.parse(clean)
            val scheme = uri.scheme?.lowercase()
            (scheme == "https" || scheme == "http") && !uri.host.isNullOrBlank()
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
        try {
            pinnedWebView?.resumeTimers()
        } catch (_: Exception) {
        }
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
            setLayerType(View.LAYER_TYPE_HARDWARE, null)
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            setInitialScale(0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                try {
                    // Keep the WebView renderer high-priority so scrolling & resume stay instant
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
                // Required so WebView can read content:// URIs returned by Android's system file picker
                allowContentAccess = true
                cacheMode = WebSettings.LOAD_DEFAULT
                // Strictly block active HTTP mixed-content injection inside HTTPS sessions
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
                    if (handler != null && isSafeHttpUrl(originUrl)) {
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
                        if (hasActiveLoadedSession()) {
                            val parsed = PulseNotificationManager.parseWebNotificationConsolePayload(raw)
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
                        injectWebNotificationBridge(view)
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    refreshCanGoBack(view)
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
                    if (!url.isNullOrBlank() && isSafeHttpUrl(url)) {
                        injectWebNotificationBridge(view)
                    }
                    injectMobileLayoutFix(view)
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
        when {
            isSafeHttpUrl(cleanEndpoint) -> {
                if (lastLoadedEndpoint != cleanEndpoint) {
                    lastLoadedEndpoint = cleanEndpoint
                    webView.loadUrl(cleanEndpoint)
                }
            }
            // Never overwrite an already-active pinned remote session with the welcome screen
            // while Room is asynchronously restoring saved profiles after biometric unlock or resume.
            isSafeHttpUrl(lastLoadedEndpoint) -> Unit
            lastLoadedEndpoint != "__WELCOME__" -> {
                lastLoadedEndpoint = "__WELCOME__"
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
            lastLoadedEndpoint = cleanUrl
            webView.loadUrl(cleanUrl)
        }
    }

    fun resetToWelcomeScreen() {
        lastLoadedEndpoint = "__WELCOME__"
        val webView = pinnedWebView ?: return
        webView.loadDataWithBaseURL(
            "https://antigravity.local",
            buildEmptyWebBridgeNoticeHtml(),
            "text/html",
            "UTF-8",
            null
        )
    }

    fun clearSensitiveBrowsingData() {
        try {
            pinnedWebView?.clearCache(true)
            pinnedWebView?.clearHistory()
            pinnedWebView?.clearFormData()
            CookieManager.getInstance().removeAllCookies(null)
            CookieManager.getInstance().flush()
        } catch (_: Exception) {
        }
        _canGoBackState.value = false
        resetToWelcomeScreen()
    }

    fun reloadOrForceLoadUrl(url: String, domLagBooster: Boolean) {
        domLagBoosterActive = domLagBooster
        val webView = pinnedWebView ?: return
        val cleanUrl = url.trim()
        if (isSafeHttpUrl(cleanUrl)) {
            lastLoadedEndpoint = cleanUrl
            webView.loadUrl(cleanUrl)
        } else {
            resetToWelcomeScreen()
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
                    'padding-left: 0 !important; padding-right: 0 !important; box-sizing: border-box !important; ' +
                    '-webkit-font-smoothing: antialiased; text-rendering: optimizeLegibility; } ' +
                    '#root, #app, main, [role="main"] { max-width: 100vw !important; box-sizing: border-box !important; }';
                  document.head.appendChild(existing);
                }

                window.scrollTo(0, window.scrollY || 0);
                if (document.documentElement) { document.documentElement.scrollLeft = 0; }
                if (document.body) { document.body.scrollLeft = 0; }
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
    fun injectWebNotificationBridge(webView: WebView? = pinnedWebView) {
        val target = webView ?: return
        val prefix = PulseNotificationManager.WEB_NOTIFY_CONSOLE_PREFIX
        val js = """
            (function() {
              try {
                function emitNativeNotification(title, options) {
                  try {
                    var opts = options || {};
                    var payload = {
                      title: String(title || 'Antigravity Agent').slice(0, 120),
                      body: String(opts.body || opts.message || '').slice(0, 360),
                      tag: String(opts.tag || '').slice(0, 64)
                    };
                    console.info('$prefix' + JSON.stringify(payload));
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
                  var lastTextLen = (document.body.innerText || '').length;
                  var streamTicks = 0;
                  var lastStreamTickAt = 0;
                  var seenApprovalKeys = {};
                  var baselineCaptured = false;
                  var lastWorkedMarkerCount = 0;
                  var domDirty = true;

                  function isVisible(el) {
                    if (!el || !el.getBoundingClientRect) { return false; }
                    var r = el.getBoundingClientRect();
                    return r.width > 4 && r.height > 4;
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

                      // Zero-CPU idle fast path: skip DOM scans when nothing changed and agent is idle
                      if (fromTimer && !domDirty && !isWorking && baselineCaptured) {
                        return;
                      }
                      domDirty = false;

                      var bodyText = (document.body && document.body.innerText) ? document.body.innerText : '';
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
                webView.evaluateJavascript(js, null)
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
                  'html, body { scroll-behavior: auto !important; -webkit-tap-highlight-color: transparent !important; } ' +
                  'a, button, input, textarea, [role="button"] { touch-action: manipulation !important; }';
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
