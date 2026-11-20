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
        val webView = pinnedAiStudioWebView ?: return false
        webView.post {
            try {
                if (RetainedWebViewManager.isSafeHttpUrl(currentLoadedUrl)) {
                    webView.reload()
                } else {
                    navigateToSection(AiStudioWebSection.NEW_CHAT)
                }
            } catch (_: Exception) {
            }
        }
        return true
    }

    fun navigateToSection(section: AiStudioWebSection) {
        _activeSectionState.value = section
        currentLoadedUrl = section.url
        val webView = pinnedAiStudioWebView ?: return
        webView.post {
            try {
                webView.loadUrl(section.url)
            } catch (_: Exception) {
            }
        }
    }

    fun applyLagBooster(enabled: Boolean) {
        domLagBoosterActive = enabled
        val target = pinnedAiStudioWebView ?: return
        val js = if (enabled) {
            """
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
                        currentLoadedUrl = url
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
                        updateSectionFromUrl(url)
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
                    refreshCanGoBack(view)
                    flushCookies()
                    if (!url.isNullOrBlank() && RetainedWebViewManager.isSafeHttpUrl(url)) {
                        currentLoadedUrl = url
                        if (url.contains("aistudio.google.com", ignoreCase = true)) {
                            updateSectionFromUrl(url)
                        }
                        injectAiStudioNotificationShim(view)
                    }
                    RetainedWebViewManager.injectMobileLayoutFix(view)
                    applyLagBooster(domLagBoosterActive)
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
