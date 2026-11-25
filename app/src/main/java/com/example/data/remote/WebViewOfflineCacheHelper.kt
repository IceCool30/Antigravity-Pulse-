package com.example.data.remote

import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.example.ui.components.TouchSafeWebViewContainer

enum class OfflineRecoveryStatus {
    ONLINE,
    OFFLINE_CACHED,
    RECONNECTING,
    RESTORED
}

data class WebViewContinuityState(
    val isOnline: Boolean = true,
    val status: OfflineRecoveryStatus = OfflineRecoveryStatus.ONLINE,
    val hadMainFrameError: Boolean = false,
    val showingSnapshotShield: Boolean = false,
    val cachedSnapshot: Bitmap? = null,
    val statusMessage: String = ""
)

/**
 * In-RAM Tier-2 recovery snapshot capturing a WebView's full Back/Forward navigation stack,
 * active URL, and viewport scroll coordinates so even if Android's Low Memory Killer evicts
 * an individual Chromium renderer under extreme device pressure, the tab resumes seamlessly.
 */
data class WebViewStateBackup(
    val bundle: Bundle,
    val scrollX: Int,
    val scrollY: Int,
    val url: String
)

/**
 * Provides seamless offline visual continuity and disk-cache mode switching for pinned WebViews
 * so temporary internet drops never replace the user's live interface with a blank error screen.
 */
object WebViewOfflineCacheHelper {
    private const val MAX_SNAPSHOT_DIMENSION_PX = 720
    private const val PERIODIC_SNAPSHOT_MIN_INTERVAL_MS = 30_000L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val activeRenderStateMap = java.util.WeakHashMap<WebView, Boolean>()
    private val lastSnapshotElapsedMap = java.util.WeakHashMap<WebView, Long>()

    /**
     * Runs [block] on the Android main thread immediately if already on it (and [delayMs] == 0),
     * or via the main Looper Handler so operations work reliably even when a pinned WebView is
     * temporarily detached from the view hierarchy while the user is on another tab.
     */
    fun runOnMainThread(delayMs: Long = 0L, block: () -> Unit) {
        if (delayMs <= 0L && Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else if (delayMs <= 0L) {
            mainHandler.post(block)
        } else {
            mainHandler.postDelayed(block, delayMs)
        }
    }

    /**
     * Instantiates a [WebView] backed by a [MutableContextWrapper] initialized with the active
     * Activity/UI [context] so Chromium receives the real display refresh rate (90/120Hz VSync),
     * touch slop, and fling velocity physics instead of falling back to non-UI `applicationContext`.
     */
    fun createDisplayAwareWebView(context: Context): WebView {
        return WebView(MutableContextWrapper(context))
    }

    /** Rebinds a pinned [WebView]'s [MutableContextWrapper] to the current active UI [context]. */
    fun attachActivityContext(webView: WebView, context: Context) {
        val wrapper = webView.context as? MutableContextWrapper ?: return
        if (wrapper.baseContext !== context) {
            wrapper.baseContext = context
        }
    }

    /** Rebinds a pinned [WebView] back to `applicationContext` on Activity destroy to prevent leaks. */
    fun detachActivityContext(webView: WebView?) {
        val wrapper = webView?.context as? MutableContextWrapper ?: return
        val appCtx = wrapper.applicationContext ?: return
        if (wrapper.baseContext !== appCtx) {
            wrapper.baseContext = appCtx
        }
    }

    fun isNetworkErrorCode(errorCode: Int, isDeviceOnline: Boolean): Boolean {
        return when (errorCode) {
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_TIMEOUT,
            WebViewClient.ERROR_IO,
            WebViewClient.ERROR_UNKNOWN,
            WebViewClient.ERROR_TOO_MANY_REQUESTS -> true
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> !isDeviceOnline
            else -> !isDeviceOnline
        }
    }

    fun applyCacheMode(webView: WebView?, isOnline: Boolean) {
        val target = webView ?: return
        runOnMainThread {
            try {
                target.settings.cacheMode = if (isOnline) {
                    WebSettings.LOAD_DEFAULT
                } else {
                    WebSettings.LOAD_CACHE_ELSE_NETWORK
                }
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Blocks offline bitmap snapshots on authentication, OAuth, 2FA, password, API token, key,
     * and billing pages so sensitive credentials are never retained in visual continuity bitmaps.
     */
    fun isSensitiveNoSnapshotUrl(url: String): Boolean {
        val clean = url.trim()
        if (clean.isEmpty()) return true
        return try {
            val uri = android.net.Uri.parse(clean)
            val host = uri.host?.lowercase()?.trimEnd('.').orEmpty()
            val path = uri.path?.lowercase().orEmpty()
            val isSensitiveHost = host == "accounts.google.com" ||
                host.endsWith(".accounts.google.com") ||
                host.startsWith("login.") ||
                host.startsWith("auth.") ||
                host.startsWith("sso.") ||
                host.startsWith("oauth.") ||
                host.startsWith("signin.") ||
                host.startsWith("id.")
            if (isSensitiveHost) return true

            val sensitivePathTokens = listOf(
                "/login", "/signin", "/sign-in", "/logout", "/oauth", "/authorize",
                "/auth/", "/sso", "/sessions", "/2fa", "/mfa", "/totp", "/passkey",
                "/password", "/reset-password", "/token", "/tokens", "/secret",
                "/secrets", "/keys", "/ssh", "/billing", "/checkout", "/payment",
                "/settings/security", "/account/tokens"
            )
            sensitivePathTokens.any { token -> path.contains(token) }
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Captures a lightweight, memory-safe visual snapshot of the currently rendered WebView frame
     * so Chromium's built-in error page or reload flash never blanks the user's screen.
     */
    fun captureWebViewSnapshot(webView: WebView?): Bitmap? {
        val target = webView ?: return null
        if (target.visibility != View.VISIBLE) return null
        val currentUrl = try {
            target.url.orEmpty()
        } catch (_: Exception) {
            ""
        }
        if (currentUrl.isBlank() ||
            currentUrl.startsWith("about:", ignoreCase = true) ||
            currentUrl.startsWith("chrome-error:", ignoreCase = true) ||
            !RetainedWebViewManager.isSafeHttpUrl(currentUrl) ||
            isSensitiveNoSnapshotUrl(currentUrl)
        ) {
            return null
        }

        val width = target.width
        val height = target.height
        if (width <= 0 || height <= 0) return null

        val scale = if (maxOf(width, height) > MAX_SNAPSHOT_DIMENSION_PX) {
            MAX_SNAPSHOT_DIMENSION_PX.toFloat() / maxOf(width, height).toFloat()
        } else {
            1f
        }
        val bmpWidth = (width * scale).toInt().coerceAtLeast(1)
        val bmpHeight = (height * scale).toInt().coerceAtLeast(1)

        return try {
            val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.RGB_565)
            val canvas = Canvas(bitmap)
            if (scale < 1f) {
                canvas.scale(scale, scale)
            }
            // Translate by current scroll offset so the snapshot captures the exact visible viewport
            canvas.translate(-target.scrollX.toFloat(), -target.scrollY.toFloat())
            target.draw(canvas)
            synchronized(lastSnapshotElapsedMap) {
                lastSnapshotElapsedMap[target] = android.os.SystemClock.elapsedRealtime()
            }
            bitmap
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Throttled snapshot capture for routine `onPageFinished` callbacks so rapid SPA navigations
     * never stall touch input with repeated synchronous software draws on the UI thread.
     */
    fun captureVisibleSnapshotThrottled(webView: WebView?): Bitmap? {
        val target = webView ?: return null
        if (target.visibility != View.VISIBLE) return null
        val now = android.os.SystemClock.elapsedRealtime()
        val lastAt = synchronized(lastSnapshotElapsedMap) {
            lastSnapshotElapsedMap[target] ?: 0L
        }
        if (lastAt > 0L && (now - lastAt) < PERIODIC_SNAPSHOT_MIN_INTERVAL_MS) {
            return null
        }
        return captureWebViewSnapshot(target)
    }

    /**
     * Notifies an already-mounted SPA WebView that network connectivity returned so its own
     * WebSockets and fetch retries resume right where the user left off without wiping drafts.
     */
    fun dispatchBrowserOnlineEvent(webView: WebView?) {
        val target = webView ?: return
        val js = """
            (function() {
              try {
                window.dispatchEvent(new Event('online'));
              } catch (e) {}
            })();
        """.trimIndent()
        runOnMainThread {
            try {
                target.evaluateJavascript(js, null)
            } catch (_: Exception) {
            }
        }
    }

    fun isNetworkCurrentlyAvailable(context: Context): Boolean {
        return try {
            val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
                ?: return true
            val active = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(active) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } catch (_: Exception) {
            true
        }
    }

    /**
     * Toggles a Window-attached WebView between active foreground (`View.VISIBLE` + `onResume`)
     * and hidden RAM-pinned state (`View.GONE` + per-instance `onPause` WITHOUT `pauseTimers`)
     * so:
     * 1. The WebView stays permanently attached to the Window (`isAttachedToWindow == true`),
     *    preserving its entire DOM, JS heap, scroll position, and WebSockets with zero reloads.
     * 2. Hidden tabs consume zero GPU compositor/rAF cycles and are completely excluded from
     *    Android ViewGroup touch hit-testing and drawing—keeping foreground touch instant at 120Hz.
     */
    @Volatile
    private var lastGlobalTimerResumeMs: Long = 0L

    /**
     * Resumes Chromium's process-wide JS/timer scheduler at most once per short burst so
     * restoring a screen or switching tabs never fires 5+ redundant native timer wakeups.
     */
    fun resumeTimersOnce(webView: WebView?, force: Boolean = false) {
        val target = webView ?: return
        val now = android.os.SystemClock.uptimeMillis()
        if (!force && now - lastGlobalTimerResumeMs < 250L) return
        lastGlobalTimerResumeMs = now
        try {
            target.resumeTimers()
        } catch (_: Exception) {
        }
    }

    /**
     * Applies common 60/120Hz scroll and hardware-compositing settings to a [WebView] without
     * `setInitialScale(0)` relayout quirks or offscreen FBO double-buffering.
     */
    fun configureScrollResponsiveWebView(webView: WebView) {
        try {
            webView.setLayerType(View.LAYER_TYPE_NONE, null)
            webView.isNestedScrollingEnabled = false
            webView.overScrollMode = View.OVER_SCROLL_NEVER
            webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY
            webView.isVerticalScrollBarEnabled = false
            webView.isHorizontalScrollBarEnabled = false
            webView.isHapticFeedbackEnabled = false
        } catch (_: Exception) {
        }
    }

    /**
     * Builds the idempotent JS snippet that injects/removes zero-wildcard lag-reduction CSS and
     * re-applies lightweight GPU-heavy style fixes on SPA route changes (`pushState` / `popstate`).
     */
    fun buildLagBoosterScript(
        enabled: Boolean,
        styleElementId: String,
        cssText: String
    ): String {
        val quotedId = org.json.JSONObject.quote(styleElementId)
        if (!enabled) {
            return """
                (function() {
                    try {
                        var el = document.getElementById($quotedId);
                        if (el && el.parentNode) el.parentNode.removeChild(el);
                        window.__agLagState = window.__agLagState || {};
                        window.__agLagState[$quotedId] = false;
                    } catch (_e) {}
                })();
            """.trimIndent()
        }
        val quotedCss = org.json.JSONObject.quote(cssText)
        return """
            (function() {
                try {
                    var id = $quotedId;
                    window.__agLagState = window.__agLagState || {};
                    window.__agLagState[id] = true;
                    var apply = function() {
                        var head = document.head || document.documentElement;
                        if (!head) return;
                        var el = document.getElementById(id);
                        if (!el) {
                            el = document.createElement('style');
                            el.id = id;
                            el.textContent = $quotedCss;
                            head.appendChild(el);
                        } else if (el.textContent !== $quotedCss) {
                            el.textContent = $quotedCss;
                        }
                        if (window.requestAnimationFrame && !window.__agSweepQueued) {
                            window.__agSweepQueued = true;
                            window.requestAnimationFrame(function() {
                                window.__agSweepQueued = false;
                                if (!window.__agLagState || !window.__agLagState[id]) return;
                                try {
                                    var nodes = document.querySelectorAll(
                                        'header, nav, footer, aside, dialog, mat-toolbar, ' +
                                        '[role="banner"], [role="navigation"], [role="toolbar"], [role="dialog"], ' +
                                        'main, [role="main"], body > div, #root > div, #app > div, app-root > div'
                                    );
                                    var limit = Math.min(nodes.length, 64);
                                    for (var i = 0; i < limit; i++) {
                                        var node = nodes[i];
                                        if (!node || !node.style || node.dataset.agOpt === '1') continue;
                                        var cs = window.getComputedStyle ? window.getComputedStyle(node) : null;
                                        if (!cs) continue;
                                        if ((cs.backdropFilter && cs.backdropFilter !== 'none') ||
                                            (cs.webkitBackdropFilter && cs.webkitBackdropFilter !== 'none')) {
                                            node.style.setProperty('backdrop-filter', 'none', 'important');
                                            node.style.setProperty('-webkit-backdrop-filter', 'none', 'important');
                                        }
                                        if (cs.backgroundAttachment === 'fixed') {
                                            node.style.setProperty('background-attachment', 'scroll', 'important');
                                        }
                                        if (cs.scrollBehavior === 'smooth') {
                                            node.style.setProperty('scroll-behavior', 'auto', 'important');
                                        }
                                        node.dataset.agOpt = '1';
                                    }
                                } catch (_sweepErr) {}
                            });
                        }
                    };
                    apply();
                    window.__agLagHooks = window.__agLagHooks || {};
                    if (!window.__agLagHooks[id] && window.history) {
                        window.__agLagHooks[id] = true;
                        var wrapHistory = function(methodName) {
                            var orig = window.history[methodName];
                            if (typeof orig !== 'function') return;
                            window.history[methodName] = function() {
                                var ret = orig.apply(this, arguments);
                                if (window.__agLagState && window.__agLagState[id]) {
                                    setTimeout(apply, 120);
                                }
                                return ret;
                            };
                        };
                        wrapHistory('pushState');
                        wrapHistory('replaceState');
                        window.addEventListener('popstate', function() {
                            if (window.__agLagState && window.__agLagState[id]) {
                                setTimeout(apply, 120);
                            }
                        }, { passive: true });
                    }
                } catch (_e) {}
            })();
        """.trimIndent()
    }

    fun applyWindowAttachedVisibility(webView: WebView, isActive: Boolean) {
        try {
            val targetVisibility = if (isActive) View.VISIBLE else View.GONE
            (webView.parent as? TouchSafeWebViewContainer)?.let { container ->
                container.isActiveWorkspace = isActive
                if (container.visibility != targetVisibility) {
                    container.visibility = targetVisibility
                }
            }
            if (webView.visibility != targetVisibility) {
                webView.visibility = targetVisibility
            }
            if (webView.isEnabled != isActive) {
                webView.isEnabled = isActive
            }
            if (webView.isFocusable != isActive) {
                webView.isFocusable = isActive
            }
            if (webView.isFocusableInTouchMode != isActive) {
                webView.isFocusableInTouchMode = isActive
            }
            val targetA11y = if (isActive) {
                View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            } else {
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            }
            if (webView.importantForAccessibility != targetA11y) {
                webView.importantForAccessibility = targetA11y
            }
            if (!isActive && webView.hasFocus()) {
                webView.clearFocus()
            }

            val previousState = synchronized(activeRenderStateMap) {
                val prev = activeRenderStateMap[webView]
                activeRenderStateMap[webView] = isActive
                prev
            }
            if (previousState != isActive) {
                if (isActive) {
                    webView.onResume()
                    resumeTimersOnce(webView, force = true)
                } else {
                    // Pause ONLY this hidden WebView's GPU compositing/rAF animations so hidden tabs
                    // never compete with the active workspace for UI/RenderThread/CrRendererMain frames.
                    // Do not call resumeTimers() here; the active tab owns timer resumption.
                    webView.onPause()
                }
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Keeps JS timers running across all WebViews during Activity lifecycle transitions without
     * waking up GPU/rAF rendering on hidden (`View.GONE`) background tabs.
     */
    fun keepTimersAliveWithoutResumingHidden(webView: WebView?) {
        val target = webView ?: return
        try {
            if (target.visibility == View.VISIBLE) {
                target.onResume()
                resumeTimersOnce(target)
            }
        } catch (_: Exception) {
        }
    }

    /**
     * Captures the WebView's Back/Forward state bundle and scroll offsets without interrupting
     * rendering or reloading. Must be invoked on the main thread (or via [runOnMainThread]).
     */
    fun captureStateBackup(webView: WebView?): WebViewStateBackup? {
        val target = webView ?: return null
        return try {
            val liveUrl = target.url.orEmpty()
            if (liveUrl.isBlank() ||
                liveUrl.startsWith("about:", ignoreCase = true) ||
                liveUrl.startsWith("chrome-error:", ignoreCase = true) ||
                !RetainedWebViewManager.isSafeHttpUrl(liveUrl)
            ) {
                return null
            }
            val bundle = Bundle()
            target.saveState(bundle)
            WebViewStateBackup(
                bundle = bundle,
                scrollX = target.scrollX.coerceAtLeast(0),
                scrollY = target.scrollY.coerceAtLeast(0),
                url = liveUrl
            )
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Restores a previously captured [WebViewStateBackup] (full navigation stack) or falls back
     * directly to [fallbackUrl] if no valid bundle exists.
     */
    fun restoreStateBackupOrLoad(
        webView: WebView,
        backup: WebViewStateBackup?,
        fallbackUrl: String
    ): Boolean {
        if (backup != null && !backup.bundle.isEmpty) {
            try {
                val restoredList = webView.restoreState(backup.bundle)
                if (restoredList != null && restoredList.size > 0) {
                    return true
                }
            } catch (_: Exception) {
            }
        }
        val targetUrl = backup?.url?.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) }
            ?: fallbackUrl.takeIf { RetainedWebViewManager.isSafeHttpUrl(it) }
            ?: return false
        try {
            webView.loadUrl(targetUrl)
        } catch (_: Exception) {
        }
        return false
    }

    fun restoreScrollOffset(webView: WebView?, scrollX: Int, scrollY: Int) {
        val target = webView ?: return
        if (scrollX <= 0 && scrollY <= 0) return
        runOnMainThread(delayMs = 280L) {
            try {
                target.scrollTo(scrollX.coerceAtLeast(0), scrollY.coerceAtLeast(0))
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Lightweight, zero-reload periodic heartbeat that keeps Chromium's JS timers and V8 isolate
     * warm for hidden pinned tabs WITHOUT waking up hidden GPU/rAF compositors (`onResume`), so
     * foreground scrolling and touch response remain completely lag-free.
     */
    fun dispatchKeepAliveHeartbeat(webView: WebView?) {
        val target = webView ?: return
        runOnMainThread {
            try {
                target.resumeTimers()
                val liveUrl = target.url.orEmpty()
                if (liveUrl.isNotBlank() && RetainedWebViewManager.isSafeHttpUrl(liveUrl)) {
                    target.evaluateJavascript(
                        "(function(){try{window.__agPulseKeepAliveAt=Date.now();}catch(e){}})();",
                        null
                    )
                }
            } catch (_: Exception) {
            }
        }
    }
}
