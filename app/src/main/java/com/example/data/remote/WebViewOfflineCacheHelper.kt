package com.example.data.remote

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Handler
import android.os.Looper
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

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
 * Provides seamless offline visual continuity and disk-cache mode switching for pinned WebViews
 * so temporary internet drops never replace the user's live interface with a blank error screen.
 */
object WebViewOfflineCacheHelper {
    private const val MAX_SNAPSHOT_DIMENSION_PX = 1440
    private val mainHandler = Handler(Looper.getMainLooper())

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
            bitmap
        } catch (_: OutOfMemoryError) {
            null
        } catch (_: Exception) {
            null
        }
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
}
