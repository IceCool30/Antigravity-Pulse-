package com.example.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicInteger

data class ParsedWebNotification(
    val title: String,
    val body: String,
    val tag: String
)

/**
 * Centralized native Android notification manager for Antigravity Pulse.
 *
 * Bridges web notifications emitted by the connected Antigravity Remote PWA
 * (`window.Notification`, `ServiceWorkerRegistration.showNotification`, and custom
 * `antigravity:notify` events) as well as app-level events (host connections, Cloud Sync,
 * security warnings) into Android's native notification channels.
 */
object PulseNotificationManager {
    const val WEB_NOTIFY_CONSOLE_PREFIX = "__AG_PULSE_NOTIFY__:"

    const val CHANNEL_AGENT_EVENTS = "antigravity_agent_events"
    const val CHANNEL_CONNECTION_STATUS = "antigravity_connection_status"
    const val CHANNEL_APP_ACTIVITY = "antigravity_app_activity"

    private const val MAX_TITLE_CHARS = 120
    private const val MAX_BODY_CHARS = 360
    private const val MAX_TAG_CHARS = 64
    private const val DEDUP_WINDOW_MS = 1_500L

    private val notificationIdCounter = AtomicInteger(2000)
    private val dedupLock = Any()
    private var lastSignature: String = ""
    private var lastDispatchedAtMs: Long = 0L

    @Volatile
    var isAppInForeground: Boolean = true

    fun shouldSuppressForForeground(onlyWhenBackground: Boolean): Boolean {
        return onlyWhenBackground && isAppInForeground
    }

    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun ensureChannelsCreated(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val agentChannel = NotificationChannel(
            CHANNEL_AGENT_EVENTS,
            "Agent & Remote Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Native alerts when your Antigravity agent finishes a task, replies, or requests approval."
            enableVibration(true)
            setShowBadge(true)
        }

        val connectionChannel = NotificationChannel(
            CHANNEL_CONNECTION_STATUS,
            "Connection & Security Status",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Remote host switches, reconnect status, and TLS security warnings."
            setShowBadge(false)
        }

        val activityChannel = NotificationChannel(
            CHANNEL_APP_ACTIVITY,
            "Cloud Sync & App Activity",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Cloud Sync backups, restores, and saved prompt confirmations."
            setShowBadge(false)
        }

        manager.createNotificationChannels(
            listOf(agentChannel, connectionChannel, activityChannel)
        )
    }

    /**
     * Safely parses and sanitizes a console-bridged Web Notification payload emitted by
     * our WebView polyfill. Returns null if the message does not match our protocol or is empty.
     */
    fun parseWebNotificationConsolePayload(rawMessage: String?): ParsedWebNotification? {
        if (rawMessage.isNullOrBlank() || !rawMessage.startsWith(WEB_NOTIFY_CONSOLE_PREFIX)) {
            return null
        }
        val jsonPart = rawMessage.removePrefix(WEB_NOTIFY_CONSOLE_PREFIX).trim()
        if (jsonPart.isEmpty()) return null

        return try {
            val obj = JSONObject(jsonPart)
            val cleanTitle = sanitizeText(obj.optString("title"), MAX_TITLE_CHARS)
                .ifBlank { "Antigravity Agent" }
            val cleanBody = sanitizeText(obj.optString("body"), MAX_BODY_CHARS)
            val cleanTag = sanitizeText(obj.optString("tag"), MAX_TAG_CHARS)
            ParsedWebNotification(
                title = cleanTitle,
                body = cleanBody,
                tag = cleanTag
            )
        } catch (_: Exception) {
            null
        }
    }

    fun showAgentNotification(
        context: Context,
        title: String,
        body: String,
        tag: String = "",
        onlyWhenBackground: Boolean = false
    ): Boolean {
        if (shouldSuppressForForeground(onlyWhenBackground)) {
            return false
        }

        val cleanTitle = sanitizeText(title, MAX_TITLE_CHARS).ifBlank { "Antigravity Agent" }
        val cleanBody = sanitizeText(body, MAX_BODY_CHARS).ifBlank { "Your remote agent task has an update." }
        val cleanTag = sanitizeText(tag, MAX_TAG_CHARS)

        if (isDuplicateBurst("$cleanTitle|$cleanBody|$cleanTag")) {
            return false
        }

        return postNotification(
            context = context,
            channelId = CHANNEL_AGENT_EVENTS,
            title = cleanTitle,
            body = cleanBody,
            priority = NotificationCompat.PRIORITY_HIGH,
            category = NotificationCompat.CATEGORY_MESSAGE,
            tag = cleanTag.ifBlank { null },
            stableId = if (cleanTag.isNotBlank()) cleanTag.hashCode() else notificationIdCounter.incrementAndGet()
        )
    }

    fun showConnectionNotification(
        context: Context,
        title: String,
        body: String
    ): Boolean {
        val cleanTitle = sanitizeText(title, MAX_TITLE_CHARS).ifBlank { "Antigravity Remote" }
        val cleanBody = sanitizeText(body, MAX_BODY_CHARS)
        if (cleanBody.isBlank() || isDuplicateBurst("conn|$cleanTitle|$cleanBody")) {
            return false
        }

        return postNotification(
            context = context,
            channelId = CHANNEL_CONNECTION_STATUS,
            title = cleanTitle,
            body = cleanBody,
            priority = NotificationCompat.PRIORITY_DEFAULT,
            category = NotificationCompat.CATEGORY_STATUS,
            tag = "connection_status",
            stableId = 1001
        )
    }

    fun showAppActivityNotification(
        context: Context,
        title: String,
        body: String
    ): Boolean {
        val cleanTitle = sanitizeText(title, MAX_TITLE_CHARS).ifBlank { "Antigravity Pulse" }
        val cleanBody = sanitizeText(body, MAX_BODY_CHARS)
        if (cleanBody.isBlank() || isDuplicateBurst("app|$cleanTitle|$cleanBody")) {
            return false
        }

        return postNotification(
            context = context,
            channelId = CHANNEL_APP_ACTIVITY,
            title = cleanTitle,
            body = cleanBody,
            priority = NotificationCompat.PRIORITY_LOW,
            category = NotificationCompat.CATEGORY_STATUS,
            tag = "app_activity",
            stableId = 1002
        )
    }

    fun showTestNotification(context: Context): Boolean {
        return postNotification(
            context = context,
            channelId = CHANNEL_AGENT_EVENTS,
            title = "Antigravity Agent Ready",
            body = "Native remote notifications are active. Tap to jump straight back to your session.",
            priority = NotificationCompat.PRIORITY_HIGH,
            category = NotificationCompat.CATEGORY_MESSAGE,
            tag = "pulse_test_notification",
            stableId = 1099
        )
    }

    private fun postNotification(
        context: Context,
        channelId: String,
        title: String,
        body: String,
        priority: Int,
        category: String,
        tag: String?,
        stableId: Int
    ): Boolean {
        val appContext = context.applicationContext
        if (!hasNotificationPermission(appContext)) return false

        ensureChannelsCreated(appContext)

        val launchIntent = Intent(appContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            appContext,
            stableId,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(appContext, channelId)
            .setSmallIcon(R.drawable.ic_pulse_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(priority)
            .setCategory(category)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        return try {
            val manager = NotificationManagerCompat.from(appContext)
            if (tag != null) {
                manager.notify(tag, stableId, notification)
            } else {
                manager.notify(stableId, notification)
            }
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun isDuplicateBurst(signature: String): Boolean {
        val now = System.currentTimeMillis()
        synchronized(dedupLock) {
            val isDup = signature == lastSignature && (now - lastDispatchedAtMs) < DEDUP_WINDOW_MS
            if (!isDup) {
                lastSignature = signature
                lastDispatchedAtMs = now
            }
            return isDup
        }
    }

    internal fun resetDedupForTests() {
        synchronized(dedupLock) {
            lastSignature = ""
            lastDispatchedAtMs = 0L
        }
    }

    private fun sanitizeText(raw: String?, maxLen: Int): String {
        if (raw.isNullOrBlank()) return ""
        return raw
            .replace(Regex("[\\r\\n\\t]+"), " ")
            .replace(Regex("[\\u0000-\\u001F\\u007F]"), "")
            .trim()
            .take(maxLen)
    }
}
