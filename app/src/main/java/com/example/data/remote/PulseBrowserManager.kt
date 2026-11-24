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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.URLEncoder
import java.util.concurrent.atomic.AtomicInteger

data class BrowserQuickLink(
    val id: String,
    val label: String,
    val subtitle: String,
    val url: String
)

data class PulseBrowserTabState(
    val id: String,
    val title: String = "New Tab",
    val url: String = "",
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val isDesktopMode: Boolean = false,
    val loadProgress: Int = 100,
    val rendererGeneration: Int = 0,
    val continuityState: WebViewContinuityState = WebViewContinuityState()
)

val ConnectedAppBrowserLinks: List<BrowserQuickLink> = listOf(
    BrowserQuickLink("t3_code", "T3 Code", "app.t3.codes", "https://app.t3.codes/"),
    BrowserQuickLink("ai_studio", "AI Studio", "aistudio.google.com", "https://aistudio.google.com/prompts/new_chat"),
    BrowserQuickLink("gemini", "Gemini", "gemini.google.com", "https://gemini.google.com/app"),
    BrowserQuickLink("github", "GitHub", "github.com", "https://github.com/"),
    BrowserQuickLink("vercel", "Vercel", "vercel.com", "https://vercel.com/dashboard"),
    BrowserQuickLink("supabase", "Supabase", "supabase.com", "https://supabase.com/dashboard/projects"),
    BrowserQuickLink("google", "Google", "google.com", "https://www.google.com/")
)

/**
 * Multi-tab RAM-pinned browser manager that shares the app's unified cookie jar and OAuth sessions
 * across Google AI Studio, Gemini, GitHub, Vercel, Supabase, and any custom URL or preview host.
 */
object PulseBrowserManager {
    private val tabCounter = AtomicInteger(1)

    @SuppressLint("StaticFieldLeak")
    private val tabWebViews = mutableMapOf<String, WebView>()
    private val tabDefaultUserAgents = mutableMapOf<String, String>()
    private val tabLastGoodUrls = mutableMapOf<String, String>()
    private val tabMainFrameFailed = mutableMapOf<String, Boolean>()
    private val tabStateBackups = mutableMapOf<String, WebViewStateBackup>()
    private val tabPendingRestoreScroll = mutableMapOf<String, Pair<Int, Int>>()
    private var pinnedSinceElapsedMs: Long = 0L
    private var domLagBoosterActive: Boolean = true

    private val initialTab = PulseBrowserTabState(id = "tab_1")

    private val _tabs = MutableStateFlow(listOf(initialTab))
    val tabs: StateFlow<List<PulseBrowserTabState>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow(initialTab.id)
    val activeTabId: StateFlow<String> = _activeTabId.asStateFlow()

    private val _isAddressBarVisible = MutableStateFlow(true)
    val isAddressBarVisible: StateFlow<Boolean> = _isAddressBarVisible.asStateFlow()

    fun activeTab(): PulseBrowserTabState {
        val current = _tabs.value
        return current.firstOrNull { it.id == _activeTabId.value } ?: current.first()
    }

    fun setAddressBarVisible(visible: Boolean) {
        _isAddressBarVisible.value = visible
    }

    fun toggleAddressBar() {
        _isAddressBarVisible.value = !_isAddressBarVisible.value
    }

    fun openNewTab(initialUrl: String = ""): PulseBrowserTabState {
        val id = "tab_${tabCounter.incrementAndGet()}"
        val resolved = if (initialUrl.isBlank()) "" else resolveAddressInput(initialUrl).orEmpty()
        val newTab = PulseBrowserTabState(
            id = id,
            title = if (resolved.isBlank()) "New Tab" else hostLabelFromUrl(resolved),
            url = resolved
        )
        _tabs.value = _tabs.value + newTab
        _activeTabId.value = id
        if (resolved.isNotBlank()) {
            tabLastGoodUrls[id] = resolved
        }
        return newTab
    }

    fun selectTab(tabId: String) {
        if (_tabs.value.any { it.id == tabId }) {
            _activeTabId.value = tabId
        }
    }

    fun closeTab(tabId: String) {
        val existing = _tabs.value
        val closingIndex = existing.indexOfFirst { it.id == tabId }
        if (closingIndex < 0) return

        destroyTabWebView(tabId)
        val remaining = existing.filterNot { it.id == tabId }
        if (remaining.isEmpty()) {
            val fresh = PulseBrowserTabState(id = "tab_${tabCounter.incrementAndGet()}")
            _tabs.value = listOf(fresh)
            _activeTabId.value = fresh.id
        } else {
            _tabs.value = remaining
            if (_activeTabId.value == tabId) {
                val nextIdx = (closingIndex - 1).coerceIn(0, remaining.lastIndex)
                _activeTabId.value = remaining[nextIdx].id
            }
        }
    }

    private fun destroyTabWebView(tabId: String) {
        val wv = tabWebViews.remove(tabId)
        tabDefaultUserAgents.remove(tabId)
        tabLastGoodUrls.remove(tabId)
        tabMainFrameFailed.remove(tabId)
        tabStateBackups.remove(tabId)
        tabPendingRestoreScroll.remove(tabId)
        if (tabWebViews.isEmpty()) {
            pinnedSinceElapsedMs = 0L
        }
        if (wv != null) {
            WebViewOfflineCacheHelper.runOnMainThread {
                try {
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.stopLoading()
                    wv.destroy()
                } catch (_: Exception) {
                }
            }
        }
        WorkspaceMemoryKeepAliveCoordinator.notifyWorkspaceTopologyChanged()
    }

    private fun updateTab(tabId: String, transform: (PulseBrowserTabState) -> PulseBrowserTabState) {
        _tabs.value = _tabs.value.map { tab ->
            if (tab.id == tabId) transform(tab) else tab
        }
    }

    fun navigateActiveTab(rawInput: String): String? {
        val resolved = resolveAddressInput(rawInput) ?: return null
        val targetId = activeTab().id
        tabLastGoodUrls[targetId] = resolved
        tabMainFrameFailed[targetId] = false
        updateTab(targetId) {
            it.copy(
                url = resolved,
                title = hostLabelFromUrl(resolved),
                loadProgress = 12
            )
        }
        tabWebViews[targetId]?.let { wv ->
            WebViewOfflineCacheHelper.runOnMainThread {
                wv.loadUrl(resolved)
            }
        }
        return resolved
    }

    fun goBackActiveTab(): Boolean {
        val tab = activeTab()
        val wv = tabWebViews[tab.id] ?: return false
        return if (wv.canGoBack()) {
            wv.goBack()
            refreshTabHistoryState(tab.id, wv)
            true
        } else {
            false
        }
    }

    fun goForwardActiveTab(): Boolean {
        val tab = activeTab()
        val wv = tabWebViews[tab.id] ?: return false
        return if (wv.canGoForward()) {
            wv.goForward()
            refreshTabHistoryState(tab.id, wv)
            true
        } else {
            false
        }
    }

    fun reloadActiveTab() {
        val tab = activeTab()
        val wv = tabWebViews[tab.id] ?: return
        WebViewOfflineCacheHelper.runOnMainThread {
            val live = wv.url
            if (!live.isNullOrBlank() && RetainedWebViewManager.isSafeHttpUrl(live)) {
                wv.reload()
            } else if (tab.url.isNotBlank() && RetainedWebViewManager.isSafeHttpUrl(tab.url)) {
                wv.loadUrl(tab.url)
            }
        }
    }

    fun reloadOrRetryActiveTab() {
        val tab = activeTab()
        val wv = tabWebViews[tab.id] ?: return
        val targetUrl = tabLastGoodUrls[tab.id]?.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) }
            ?: tab.url.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) }
            ?: return
        WebViewOfflineCacheHelper.runOnMainThread {
            WebViewOfflineCacheHelper.applyCacheMode(wv, isOnline = true)
            val prev = tab.continuityState
            val snap = if (!prev.hadMainFrameError) {
                WebViewOfflineCacheHelper.captureWebViewSnapshot(wv) ?: prev.cachedSnapshot
            } else {
                prev.cachedSnapshot
            }
            updateTab(tab.id) {
                it.copy(
                    continuityState = prev.copy(
                        status = OfflineRecoveryStatus.RECONNECTING,
                        cachedSnapshot = snap,
                        showingSnapshotShield = prev.hadMainFrameError || snap != null,
                        statusMessage = "Refreshing page..."
                    )
                )
            }
            tabMainFrameFailed[tab.id] = false
            wv.loadUrl(targetUrl)
        }
    }

    fun dismissActiveRestoredBanner() {
        val tab = activeTab()
        if (tab.continuityState.status == OfflineRecoveryStatus.RESTORED) {
            updateTab(tab.id) {
                it.copy(
                    continuityState = it.continuityState.copy(
                        status = OfflineRecoveryStatus.ONLINE,
                        statusMessage = ""
                    )
                )
            }
        }
    }

    fun setActiveTabDesktopMode(enabled: Boolean) {
        val tab = activeTab()
        if (tab.isDesktopMode == enabled) return
        updateTab(tab.id) { it.copy(isDesktopMode = enabled) }
        val wv = tabWebViews[tab.id] ?: return
        WebViewOfflineCacheHelper.runOnMainThread {
            applyViewportAndUserAgent(tab.id, wv, desktopMode = enabled)
            val reloadUrl = wv.url?.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) } ?: tab.url
            if (RetainedWebViewManager.isSafeHttpUrl(reloadUrl)) {
                wv.loadUrl(reloadUrl)
            }
        }
    }

    private fun applyViewportAndUserAgent(tabId: String, webView: WebView, desktopMode: Boolean) {
        val baseUa = tabDefaultUserAgents[tabId].orEmpty()
            .ifBlank { webView.settings.userAgentString.orEmpty() }
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

    private fun refreshTabHistoryState(tabId: String, webView: WebView?) {
        val back = try {
            webView?.canGoBack() == true
        } catch (_: Exception) {
            false
        }
        val forward = try {
            webView?.canGoForward() == true
        } catch (_: Exception) {
            false
        }
        updateTab(tabId) { it.copy(canGoBack = back, canGoForward = forward) }
    }

    private fun captureSnapshotIfHealthy(tabId: String, view: WebView?) {
        val target = view ?: return
        if (tabMainFrameFailed[tabId] == true) return
        WebViewOfflineCacheHelper.runOnMainThread(delayMs = 1200L) {
            if (tabMainFrameFailed[tabId] == true) return@runOnMainThread
            val captured = WebViewOfflineCacheHelper.captureVisibleSnapshotThrottled(target)
            if (captured != null) {
                updateTab(tabId) {
                    it.copy(continuityState = it.continuityState.copy(cachedSnapshot = captured))
                }
            }
        }
    }

    fun onNetworkLostAll() {
        _tabs.value.forEach { tab ->
            val wv = tabWebViews[tab.id]
            WebViewOfflineCacheHelper.applyCacheMode(wv, isOnline = false)
            if (wv == null) {
                updateTab(tab.id) {
                    it.copy(continuityState = it.continuityState.copy(isOnline = false))
                }
            } else {
                WebViewOfflineCacheHelper.runOnMainThread {
                    val fresh = WebViewOfflineCacheHelper.captureWebViewSnapshot(wv)
                    updateTab(tab.id) { current ->
                        val prev = current.continuityState
                        current.copy(
                            continuityState = prev.copy(
                                isOnline = false,
                                status = OfflineRecoveryStatus.OFFLINE_CACHED,
                                cachedSnapshot = fresh ?: prev.cachedSnapshot,
                                statusMessage = "Offline • Holding browser tab until internet returns"
                            )
                        )
                    }
                }
            }
        }
    }

    fun onNetworkRestoredAll(autoReloadAfterLongOutage: Boolean) {
        _tabs.value.forEach { tab ->
            val wv = tabWebViews[tab.id]
            WebViewOfflineCacheHelper.applyCacheMode(wv, isOnline = true)
            if (wv == null) {
                updateTab(tab.id) {
                    it.copy(
                        continuityState = it.continuityState.copy(
                            isOnline = true,
                            status = OfflineRecoveryStatus.ONLINE,
                            showingSnapshotShield = false,
                            statusMessage = ""
                        )
                    )
                }
            } else {
                val prev = tab.continuityState
                val needsReload = prev.hadMainFrameError || autoReloadAfterLongOutage
                if (needsReload) {
                    val targetUrl = tabLastGoodUrls[tab.id]?.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) }
                        ?: tab.url.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) }
                    if (targetUrl != null) {
                        WebViewOfflineCacheHelper.runOnMainThread {
                            val snap = if (!prev.hadMainFrameError) {
                                WebViewOfflineCacheHelper.captureWebViewSnapshot(wv) ?: prev.cachedSnapshot
                            } else {
                                prev.cachedSnapshot
                            }
                            updateTab(tab.id) { current ->
                                current.copy(
                                    continuityState = current.continuityState.copy(
                                        isOnline = true,
                                        status = OfflineRecoveryStatus.RECONNECTING,
                                        cachedSnapshot = snap,
                                        showingSnapshotShield = true,
                                        statusMessage = "Reconnecting • Updating browser tab..."
                                    )
                                )
                            }
                            tabMainFrameFailed[tab.id] = false
                            wv.loadUrl(targetUrl)
                        }
                    }
                } else if (prev.status != OfflineRecoveryStatus.ONLINE) {
                    updateTab(tab.id) { current ->
                        current.copy(
                            continuityState = current.continuityState.copy(
                                isOnline = true,
                                status = OfflineRecoveryStatus.RESTORED,
                                showingSnapshotShield = false,
                                statusMessage = "Back online • Browser tab ready"
                            )
                        )
                    }
                }
            }
        }
    }

    fun pinnedTabCount(): Int = tabWebViews.size

    fun isPinnedInRam(): Boolean = tabWebViews.isNotEmpty()

    fun pinnedSinceElapsedMs(): Long = pinnedSinceElapsedMs

    fun hasAnyStateBackup(): Boolean = tabStateBackups.isNotEmpty()

    fun keepTimersRunningAll() {
        tabWebViews.values.forEach { wv ->
            WebViewOfflineCacheHelper.keepTimersAliveWithoutResumingHidden(wv)
        }
    }

    fun flushCookiesAll() {
        try {
            CookieManager.getInstance().flush()
        } catch (_: Exception) {
        }
    }

    fun dispatchKeepAliveHeartbeatAll() {
        tabWebViews.values.forEach { wv ->
            WebViewOfflineCacheHelper.dispatchKeepAliveHeartbeat(wv)
        }
        flushCookiesAll()
    }

    fun saveAllStateBackups() {
        val entries = tabWebViews.entries.toList()
        if (entries.isEmpty()) return
        WebViewOfflineCacheHelper.runOnMainThread {
            entries.forEach { (tabId, wv) ->
                WebViewOfflineCacheHelper.captureStateBackup(wv)?.let { tabStateBackups[tabId] = it }
            }
        }
    }

    fun releaseAllTabsFromRam() {
        saveAllStateBackups()
        val entries = tabWebViews.entries.toList()
        tabWebViews.clear()
        pinnedSinceElapsedMs = 0L
        entries.forEach { (tabId, wv) ->
            WebViewOfflineCacheHelper.runOnMainThread {
                try {
                    wv.stopLoading()
                    (wv.parent as? ViewGroup)?.removeView(wv)
                    wv.destroy()
                } catch (_: Exception) {
                }
            }
            updateTab(tabId) { it.copy(rendererGeneration = it.rendererGeneration + 1) }
        }
        WorkspaceMemoryKeepAliveCoordinator.notifyWorkspaceTopologyChanged()
    }

    fun applyLagBooster(tabId: String, enabled: Boolean) {
        domLagBoosterActive = enabled
        val target = tabWebViews[tabId] ?: return
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

    fun clearAllSensitiveBrowsingData(context: Context? = null) {
        _tabs.value.forEach { tab ->
            tab.continuityState.cachedSnapshot?.let { bmp ->
                if (!bmp.isRecycled) bmp.recycle()
            }
        }
        val ids = tabWebViews.keys.toList()
        ids.forEach { id ->
            try {
                tabWebViews[id]?.stopLoading()
                tabWebViews[id]?.loadUrl("about:blank")
                tabWebViews[id]?.clearCache(true)
                tabWebViews[id]?.clearHistory()
                tabWebViews[id]?.clearFormData()
            } catch (_: Exception) {
            }
            destroyTabWebView(id)
        }
        try {
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
        val fresh = PulseBrowserTabState(id = "tab_${tabCounter.incrementAndGet()}")
        tabStateBackups.clear()
        tabPendingRestoreScroll.clear()
        pinnedSinceElapsedMs = 0L
        _tabs.value = listOf(fresh)
        _activeTabId.value = fresh.id
        WorkspaceMemoryKeepAliveCoordinator.notifyWorkspaceTopologyChanged()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun obtainWebViewForTab(
        context: Context,
        tabId: String,
        domLagBooster: Boolean,
        onPageEvent: (String) -> Unit
    ): WebView {
        domLagBoosterActive = domLagBooster
        val appContext = context.applicationContext
        val tabState = _tabs.value.firstOrNull { it.id == tabId } ?: activeTab()

        val webView = tabWebViews[tabId] ?: WebView(appContext).apply {
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

            tabDefaultUserAgents[tabId] = settings.userAgentString.orEmpty()
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
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
            applyViewportAndUserAgent(tabId, this, desktopMode = tabState.isDesktopMode)

            try {
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
            } catch (_: Exception) {
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    super.onProgressChanged(view, newProgress)
                    updateTab(tabId) { it.copy(loadProgress = newProgress.coerceIn(0, 100)) }
                }

                override fun onReceivedTitle(view: WebView?, title: String?) {
                    super.onReceivedTitle(view, title)
                    val clean = title?.trim().orEmpty()
                    if (clean.isNotBlank() && !clean.startsWith("http", ignoreCase = true)) {
                        updateTab(tabId) { it.copy(title = clean.take(40)) }
                    }
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
                // Deliberately do NOT bridge console.log -> OS notifications in general Pulse Browser
                // so arbitrary websites cannot spoof native Android agent or security alerts.
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
                    refreshTabHistoryState(tabId, view)
                    if (!url.isNullOrBlank() && RetainedWebViewManager.isSafeHttpUrl(url)) {
                        tabMainFrameFailed[tabId] = false
                        tabLastGoodUrls[tabId] = url
                        updateTab(tabId) { current ->
                            current.copy(
                                url = url,
                                title = current.title.takeIf { it.isNotBlank() && it != "New Tab" }
                                    ?: hostLabelFromUrl(url)
                            )
                        }
                    }
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    super.doUpdateVisitedHistory(view, url, isReload)
                    refreshTabHistoryState(tabId, view)
                    if (!url.isNullOrBlank() && RetainedWebViewManager.isSafeHttpUrl(url)) {
                        tabLastGoodUrls[tabId] = url
                        updateTab(tabId) { it.copy(url = url) }
                    }
                }

                override fun onReceivedError(
                    view: WebView?,
                    request: WebResourceRequest?,
                    error: WebResourceError?
                ) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true && error != null) {
                        val isOnlineNow = _tabs.value.firstOrNull { it.id == tabId }
                            ?.continuityState?.isOnline ?: true
                        if (WebViewOfflineCacheHelper.isNetworkErrorCode(error.errorCode, isOnlineNow)) {
                            tabMainFrameFailed[tabId] = true
                            updateTab(tabId) { current ->
                                val prev = current.continuityState
                                current.copy(
                                    continuityState = prev.copy(
                                        isOnline = false,
                                        status = OfflineRecoveryStatus.OFFLINE_CACHED,
                                        hadMainFrameError = true,
                                        showingSnapshotShield = true,
                                        statusMessage = if (prev.cachedSnapshot != null) {
                                            "Offline • Holding browser tab until internet returns"
                                        } else {
                                            "Offline • Waiting for internet connection..."
                                        }
                                    )
                                )
                            }
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
                    onPageEvent("Blocked untrusted SSL certificate in Pulse Browser.")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    refreshTabHistoryState(tabId, view)
                    flushCookiesAll()
                    val isErrorPage = url.isNullOrBlank() ||
                        url.startsWith("chrome-error:", ignoreCase = true) ||
                        url.startsWith("about:", ignoreCase = true)
                    val safeUrl = url.orEmpty()
                    if (tabMainFrameFailed[tabId] != true && !isErrorPage && RetainedWebViewManager.isSafeHttpUrl(safeUrl)) {
                        tabLastGoodUrls[tabId] = safeUrl
                        updateTab(tabId) { current ->
                            val prev = current.continuityState
                            val wasRecovering = prev.status == OfflineRecoveryStatus.RECONNECTING || prev.hadMainFrameError
                            current.copy(
                                url = safeUrl,
                                loadProgress = 100,
                                title = view?.title?.trim()?.takeIf { it.isNotBlank() }?.take(40)
                                    ?: hostLabelFromUrl(safeUrl),
                                continuityState = prev.copy(
                                    isOnline = true,
                                    hadMainFrameError = false,
                                    showingSnapshotShield = false,
                                    status = if (wasRecovering) OfflineRecoveryStatus.RESTORED else OfflineRecoveryStatus.ONLINE,
                                    statusMessage = if (wasRecovering) "Back online • Browser tab updated" else ""
                                )
                            )
                        }
                        tabPendingRestoreScroll.remove(tabId)?.let { (sx, sy) ->
                            WebViewOfflineCacheHelper.restoreScrollOffset(view, sx, sy)
                        }
                        view?.postDelayed({
                            captureSnapshotIfHealthy(tabId, view)
                            WebViewOfflineCacheHelper.captureStateBackup(view)?.let { tabStateBackups[tabId] = it }
                        }, 650L)
                    } else {
                        updateTab(tabId) { it.copy(loadProgress = 100) }
                    }
                    if (!isErrorPage) {
                        val desktop = _tabs.value.firstOrNull { it.id == tabId }?.isDesktopMode == true
                        if (!desktop) {
                            RetainedWebViewManager.injectMobileLayoutFix(view)
                        }
                        applyLagBooster(tabId, domLagBoosterActive)
                    }
                }

                override fun onRenderProcessGone(
                    view: WebView?,
                    detail: RenderProcessGoneDetail?
                ): Boolean {
                    val crashedView = view ?: tabWebViews[tabId]
                    if (tabWebViews[tabId] === crashedView) {
                        tabWebViews.remove(tabId)
                    }
                    if (tabWebViews.isEmpty()) {
                        pinnedSinceElapsedMs = 0L
                    }
                    WebViewOfflineCacheHelper.runOnMainThread {
                        try {
                            (crashedView?.parent as? ViewGroup)?.removeView(crashedView)
                            crashedView?.destroy()
                        } catch (_: Exception) {
                        }
                    }
                    updateTab(tabId) { it.copy(rendererGeneration = it.rendererGeneration + 1) }
                    WorkspaceMemoryKeepAliveCoordinator.notifyWorkspaceTopologyChanged()
                    onPageEvent("Browser tab renderer recovered at your last page.")
                    return true
                }
            }

            if (tabWebViews.isEmpty()) {
                pinnedSinceElapsedMs = android.os.SystemClock.elapsedRealtime()
            }
            tabWebViews[tabId] = this
            WorkspaceMemoryKeepAliveCoordinator.notifyWorkspaceTopologyChanged()
        }

        (webView.parent as? ViewGroup)?.removeView(webView)

        val currentUrl = tabState.url
        if (currentUrl.isNotBlank() && webView.url.isNullOrBlank()) {
            val backup = tabStateBackups[tabId]
            val fallbackUrl = tabLastGoodUrls[tabId]?.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) }
                ?: currentUrl
            if (backup != null) {
                tabPendingRestoreScroll[tabId] = backup.scrollX to backup.scrollY
                WebViewOfflineCacheHelper.restoreStateBackupOrLoad(webView, backup, fallbackUrl)
            } else {
                webView.loadUrl(fallbackUrl)
            }
        } else {
            applyLagBooster(tabId, domLagBooster)
        }
        return webView
    }

    /**
     * Resolves user input from the browser omnibox into a safe HTTP/HTTPS URL or Google Search query.
     * Returns `null` if the user explicitly supplied a dangerous scheme (`javascript:`, `file:`, etc.).
     */
    fun resolveAddressInput(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return null

        val hasWhitespace = trimmed.any { it.isWhitespace() }
        val isHostWithPort = !hasWhitespace &&
            Regex("^(localhost|([a-zA-Z0-9-]+\\.)+[a-zA-Z0-9-]+):\\d{1,5}(/.*)?$", RegexOption.IGNORE_CASE)
                .matches(trimmed)

        if (!isHostWithPort) {
            val schemeMatch = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):").find(trimmed)
            if (schemeMatch != null) {
                val scheme = schemeMatch.groupValues[1].lowercase()
                return if (scheme == "http" || scheme == "https") {
                    trimmed.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) }
                } else {
                    // Block javascript:, file:, content:, data:, intent:, etc.
                    null
                }
            }
        }

        if (!hasWhitespace) {
            val isLocalHost = trimmed.startsWith("localhost", ignoreCase = true) ||
                Regex("^(\\d{1,3}\\.){3}\\d{1,3}(:\\d{1,5})?(/.*)?$").matches(trimmed)
            if (isLocalHost) {
                val candidate = "http://$trimmed"
                return candidate.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) }
            }
            if (trimmed.contains('.')) {
                val candidate = "https://$trimmed"
                return candidate.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) }
            }
        }

        val encoded = try {
            URLEncoder.encode(trimmed, "UTF-8")
        } catch (_: Exception) {
            trimmed.replace(" ", "+")
        }
        return "https://www.google.com/search?q=$encoded"
    }

    private fun hostLabelFromUrl(url: String): String {
        return try {
            Uri.parse(url).host?.removePrefix("www.")?.ifBlank { "Web" } ?: "Web"
        } catch (_: Exception) {
            "Web"
        }
    }
}
