package com.example.data.remote

import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

data class PinnedWorkspaceRamStatus(
    val id: String,
    val label: String,
    val isPinnedInRam: Boolean,
    val pinnedSinceElapsedMs: Long,
    val hasStateBackup: Boolean,
    val detail: String = ""
)

/**
 * Central coordinator that tracks every live RAM-pinned WebView workspace across the entire app
 * (Antigravity Remote, T3 Code, Google AI Studio, Gemini, GitHub, Vercel, Supabase, and Pulse Browser),
 * runs gentle keep-alive heartbeats, captures zero-loss state bundles on OS memory pressure
 * (`onTrimMemory`), and exposes live RAM controls to Settings & [com.example.service.WorkspaceKeepAliveService].
 */
object WorkspaceMemoryKeepAliveCoordinator {
    const val WORKSPACE_REMOTE = "remote"
    const val WORKSPACE_T3_CODE = "t3_code"
    const val WORKSPACE_AI_STUDIO = "ai_studio"
    const val WORKSPACE_GEMINI = "gemini"
    const val WORKSPACE_GITHUB = "github"
    const val WORKSPACE_VERCEL = "vercel"
    const val WORKSPACE_SUPABASE = "supabase"
    const val WORKSPACE_BROWSER = "browser"

    private val _workspaceStatuses = MutableStateFlow<List<PinnedWorkspaceRamStatus>>(emptyList())
    val workspaceStatuses: StateFlow<List<PinnedWorkspaceRamStatus>> = _workspaceStatuses.asStateFlow()

    private val _pinnedCount = MutableStateFlow(0)
    val pinnedCount: StateFlow<Int> = _pinnedCount.asStateFlow()

    private val _isKeepAliveServiceRunning = MutableStateFlow(false)
    val isKeepAliveServiceRunning: StateFlow<Boolean> = _isKeepAliveServiceRunning.asStateFlow()

    private val _lastHeartbeatElapsedMs = MutableStateFlow(0L)
    val lastHeartbeatElapsedMs: StateFlow<Long> = _lastHeartbeatElapsedMs.asStateFlow()

    private val _releasedWorkspaceEvents = MutableSharedFlow<String>(extraBufferCapacity = 16)
    val releasedWorkspaceEvents: SharedFlow<String> = _releasedWorkspaceEvents.asSharedFlow()

    init {
        notifyWorkspaceTopologyChanged()
    }

    fun setKeepAliveServiceRunning(running: Boolean) {
        _isKeepAliveServiceRunning.value = running
    }

    fun notifyWorkspaceTopologyChanged() {
        val browserTabCount = PulseBrowserManager.pinnedTabCount()
        val browserDetail = when {
            browserTabCount > 1 -> "$browserTabCount tabs pinned"
            browserTabCount == 1 -> "1 tab pinned"
            else -> ""
        }
        val statuses = listOf(
            PinnedWorkspaceRamStatus(
                id = WORKSPACE_REMOTE,
                label = "Remote",
                isPinnedInRam = RetainedWebViewManager.isPinnedInRam(),
                pinnedSinceElapsedMs = RetainedWebViewManager.pinnedSinceElapsedMs(),
                hasStateBackup = RetainedWebViewManager.hasStateBackup()
            ),
            PinnedWorkspaceRamStatus(
                id = WORKSPACE_T3_CODE,
                label = "T3 Code",
                isPinnedInRam = T3CodePinnedWebViewManager.isPinnedInRam(),
                pinnedSinceElapsedMs = T3CodePinnedWebViewManager.pinnedSinceElapsedMs(),
                hasStateBackup = T3CodePinnedWebViewManager.hasStateBackup()
            ),
            PinnedWorkspaceRamStatus(
                id = WORKSPACE_AI_STUDIO,
                label = "AI Studio",
                isPinnedInRam = AiStudioPinnedWebViewManager.isPinnedInRam(),
                pinnedSinceElapsedMs = AiStudioPinnedWebViewManager.pinnedSinceElapsedMs(),
                hasStateBackup = AiStudioPinnedWebViewManager.hasStateBackup()
            ),
            PinnedWorkspaceRamStatus(
                id = WORKSPACE_GEMINI,
                label = DevWebWorkspace.GEMINI.displayName,
                isPinnedInRam = DevWebWorkspaceRegistry.session(DevWebWorkspace.GEMINI).isPinnedInRam(),
                pinnedSinceElapsedMs = DevWebWorkspaceRegistry.session(DevWebWorkspace.GEMINI).pinnedSinceElapsedMs(),
                hasStateBackup = DevWebWorkspaceRegistry.session(DevWebWorkspace.GEMINI).hasStateBackup()
            ),
            PinnedWorkspaceRamStatus(
                id = WORKSPACE_GITHUB,
                label = DevWebWorkspace.GITHUB.displayName,
                isPinnedInRam = DevWebWorkspaceRegistry.session(DevWebWorkspace.GITHUB).isPinnedInRam(),
                pinnedSinceElapsedMs = DevWebWorkspaceRegistry.session(DevWebWorkspace.GITHUB).pinnedSinceElapsedMs(),
                hasStateBackup = DevWebWorkspaceRegistry.session(DevWebWorkspace.GITHUB).hasStateBackup()
            ),
            PinnedWorkspaceRamStatus(
                id = WORKSPACE_VERCEL,
                label = DevWebWorkspace.VERCEL.displayName,
                isPinnedInRam = DevWebWorkspaceRegistry.session(DevWebWorkspace.VERCEL).isPinnedInRam(),
                pinnedSinceElapsedMs = DevWebWorkspaceRegistry.session(DevWebWorkspace.VERCEL).pinnedSinceElapsedMs(),
                hasStateBackup = DevWebWorkspaceRegistry.session(DevWebWorkspace.VERCEL).hasStateBackup()
            ),
            PinnedWorkspaceRamStatus(
                id = WORKSPACE_SUPABASE,
                label = DevWebWorkspace.SUPABASE.displayName,
                isPinnedInRam = DevWebWorkspaceRegistry.session(DevWebWorkspace.SUPABASE).isPinnedInRam(),
                pinnedSinceElapsedMs = DevWebWorkspaceRegistry.session(DevWebWorkspace.SUPABASE).pinnedSinceElapsedMs(),
                hasStateBackup = DevWebWorkspaceRegistry.session(DevWebWorkspace.SUPABASE).hasStateBackup()
            ),
            PinnedWorkspaceRamStatus(
                id = WORKSPACE_BROWSER,
                label = "Browser",
                isPinnedInRam = PulseBrowserManager.isPinnedInRam(),
                pinnedSinceElapsedMs = PulseBrowserManager.pinnedSinceElapsedMs(),
                hasStateBackup = PulseBrowserManager.hasAnyStateBackup(),
                detail = browserDetail
            )
        )
        _workspaceStatuses.value = statuses
        _pinnedCount.value = statuses.count { it.isPinnedInRam }
    }

    /**
     * Keeps all currently pinned WebViews' JS timers, WebSockets, and cookie jars alive
     * without triggering page reloads, waking hidden GPU compositors, or bunching IPCs
     * onto a single UI frame.
     */
    fun runKeepAliveHeartbeatAll() {
        val tasks = mutableListOf<() -> Unit>()
        if (RetainedWebViewManager.isPinnedInRam()) {
            tasks += { RetainedWebViewManager.dispatchKeepAliveHeartbeat() }
        }
        if (T3CodePinnedWebViewManager.isPinnedInRam()) {
            tasks += { T3CodePinnedWebViewManager.dispatchKeepAliveHeartbeat() }
        }
        if (AiStudioPinnedWebViewManager.isPinnedInRam()) {
            tasks += { AiStudioPinnedWebViewManager.dispatchKeepAliveHeartbeat() }
        }
        DevWebWorkspace.entries.forEach { ws ->
            val session = DevWebWorkspaceRegistry.session(ws)
            if (session.isPinnedInRam()) {
                tasks += { session.dispatchKeepAliveHeartbeat() }
            }
        }
        if (PulseBrowserManager.isPinnedInRam()) {
            tasks += { PulseBrowserManager.dispatchKeepAliveHeartbeatAll() }
        }
        tasks.forEachIndexed { index, task ->
            WebViewOfflineCacheHelper.runOnMainThread(delayMs = index * 160L) {
                task()
            }
        }
        _lastHeartbeatElapsedMs.value = SystemClock.elapsedRealtime()
        notifyWorkspaceTopologyChanged()
    }

    /**
     * Proactively saves back/forward history, active URLs, and scroll offsets for every pinned
     * WebView without destroying any live renderer.
     */
    fun saveAllStateBackups() {
        if (RetainedWebViewManager.isPinnedInRam()) {
            RetainedWebViewManager.saveStateBackup()
        }
        if (T3CodePinnedWebViewManager.isPinnedInRam()) {
            T3CodePinnedWebViewManager.saveStateBackup()
        }
        if (AiStudioPinnedWebViewManager.isPinnedInRam()) {
            AiStudioPinnedWebViewManager.saveStateBackup()
        }
        DevWebWorkspaceRegistry.saveAllStateBackups()
        if (PulseBrowserManager.isPinnedInRam()) {
            PulseBrowserManager.saveAllStateBackups()
        }
        RetainedWebViewManager.flushCookies()
        notifyWorkspaceTopologyChanged()
    }

    /**
     * Responds to Android OS memory trim callbacks by snapshotting every live WebView's state
     * bundle and flushing cookies—never forcibly destroying user workspaces so sessions stay live.
     */
    @Suppress("DEPRECATION")
    fun onSystemTrimMemory(level: Int) {
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND ||
                level >= ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN ||
                level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW -> {
                saveAllStateBackups()
            }
            else -> {
                RetainedWebViewManager.flushCookies()
            }
        }
    }

    /**
     * On-demand RAM release for a single workspace initiated explicitly by the user in Settings.
     * Saves a state backup first so reopening the tab later resumes at the exact page and scroll position.
     */
    fun releaseWorkspaceFromRam(workspaceId: String) {
        when (workspaceId) {
            WORKSPACE_REMOTE -> RetainedWebViewManager.releaseFromRam()
            WORKSPACE_T3_CODE -> T3CodePinnedWebViewManager.releaseFromRam()
            WORKSPACE_AI_STUDIO -> AiStudioPinnedWebViewManager.releaseFromRam()
            WORKSPACE_GEMINI -> DevWebWorkspaceRegistry.session(DevWebWorkspace.GEMINI).releaseFromRam()
            WORKSPACE_GITHUB -> DevWebWorkspaceRegistry.session(DevWebWorkspace.GITHUB).releaseFromRam()
            WORKSPACE_VERCEL -> DevWebWorkspaceRegistry.session(DevWebWorkspace.VERCEL).releaseFromRam()
            WORKSPACE_SUPABASE -> DevWebWorkspaceRegistry.session(DevWebWorkspace.SUPABASE).releaseFromRam()
            WORKSPACE_BROWSER -> PulseBrowserManager.releaseAllTabsFromRam()
            else -> return
        }
        _releasedWorkspaceEvents.tryEmit(workspaceId)
        notifyWorkspaceTopologyChanged()
    }

    fun buildActiveSummaryText(statuses: List<PinnedWorkspaceRamStatus> = _workspaceStatuses.value): String {
        val activeLabels = statuses.filter { it.isPinnedInRam }.map { it.label }
        if (activeLabels.isEmpty()) return ""
        return if (activeLabels.size <= 3) {
            activeLabels.joinToString(", ")
        } else {
            val head = activeLabels.take(3).joinToString(", ")
            "$head +${activeLabels.size - 3} more"
        }
    }

    fun formatPinnedDuration(
        pinnedSinceElapsedMs: Long,
        nowElapsedMs: Long = SystemClock.elapsedRealtime()
    ): String {
        if (pinnedSinceElapsedMs <= 0L) return "Standby"
        val elapsedSeconds = ((nowElapsedMs - pinnedSinceElapsedMs) / 1000L).coerceAtLeast(0L)
        val totalMinutes = elapsedSeconds / 60L
        return when {
            totalMinutes < 1L -> "Live"
            totalMinutes < 60L -> "${totalMinutes}m"
            else -> {
                val hours = totalMinutes / 60L
                val mins = totalMinutes % 60L
                if (mins == 0L) "${hours}h" else "${hours}h ${mins}m"
            }
        }
    }

    /**
     * Play-policy compliant read-only check whether the user has set this app to "Unrestricted"
     * battery usage in Android system settings.
     */
    fun isIgnoringBatteryOptimizations(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return true
        return try {
            val pm = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            pm?.isIgnoringBatteryOptimizations(context.packageName) == true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Opens the standard Android Battery Optimization settings list (never using the restricted
     * `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` intent) with safe fallback to App Info.
     */
    fun openBatteryOptimizationSettings(context: Context): Boolean {
        val settingsIntent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(settingsIntent)
            true
        } catch (_: Exception) {
            try {
                val appInfoIntent = Intent(
                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", context.packageName, null)
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(appInfoIntent)
                true
            } catch (_: Exception) {
                false
            }
        }
    }
}
