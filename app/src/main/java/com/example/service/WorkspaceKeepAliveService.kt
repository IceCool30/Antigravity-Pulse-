package com.example.service

import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.data.remote.WorkspaceMemoryKeepAliveCoordinator
import com.example.notifications.PulseNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Play-policy compliant `dataSync` Foreground Service that elevates process priority while
 * multiple developer WebView workspaces are pinned in RAM, runs a lightweight 45-second
 * JS timer & WebSocket heartbeat loop, and handles Android 15+ FGS timeouts cleanly.
 */
class WorkspaceKeepAliveService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var heartbeatJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            userPausedKeepAlive = true
            stopKeepAliveCleanly(startId)
            return START_NOT_STICKY
        }

        userPausedKeepAlive = false
        val started = promoteToForeground()
        if (!started) {
            WorkspaceMemoryKeepAliveCoordinator.setKeepAliveServiceRunning(false)
            stopSelf(startId)
            return START_NOT_STICKY
        }

        WorkspaceMemoryKeepAliveCoordinator.setKeepAliveServiceRunning(true)
        ensureHeartbeatLoopRunning()
        return START_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        // Android 15+ (API 35+) enforces a 6-hour dataSync FGS budget.
        // Snapshot all WebView state bundles and stop gracefully so the OS never throws RemoteServiceException.
        WorkspaceMemoryKeepAliveCoordinator.saveAllStateBackups()
        stopKeepAliveCleanly(startId)
    }

    override fun onDestroy() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        serviceScope.cancel()
        WorkspaceMemoryKeepAliveCoordinator.setKeepAliveServiceRunning(false)
        super.onDestroy()
    }

    private fun stopKeepAliveCleanly(startId: Int? = null) {
        heartbeatJob?.cancel()
        heartbeatJob = null
        WorkspaceMemoryKeepAliveCoordinator.setKeepAliveServiceRunning(false)
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (_: Exception) {
        }
        if (startId != null) {
            stopSelf(startId)
        } else {
            stopSelf()
        }
    }

    private fun promoteToForeground(): Boolean {
        val notification = buildCurrentNotification()
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return try {
            ServiceCompat.startForeground(
                this,
                PulseNotificationManager.KEEP_ALIVE_NOTIFICATION_ID,
                notification,
                fgsType
            )
            true
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun buildCurrentNotification(): android.app.Notification {
        val stopIntent = Intent(this, WorkspaceKeepAliveService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            STOP_REQUEST_CODE,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        WorkspaceMemoryKeepAliveCoordinator.notifyWorkspaceTopologyChanged()
        val pinnedCount = WorkspaceMemoryKeepAliveCoordinator.pinnedCount.value
        val summary = WorkspaceMemoryKeepAliveCoordinator.buildActiveSummaryText()
        return PulseNotificationManager.buildKeepAliveNotification(
            context = this,
            pinnedCount = pinnedCount,
            workspaceSummary = summary,
            stopPendingIntent = stopPendingIntent
        )
    }

    private fun ensureHeartbeatLoopRunning() {
        if (heartbeatJob?.isActive == true) {
            refreshNotificationQuietly()
            return
        }
        heartbeatJob = serviceScope.launch {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                WorkspaceMemoryKeepAliveCoordinator.runKeepAliveHeartbeatAll()
                refreshNotificationQuietly()
            }
        }
    }

    private fun refreshNotificationQuietly() {
        try {
            if (PulseNotificationManager.hasNotificationPermission(this)) {
                NotificationManagerCompat.from(this).notify(
                    PulseNotificationManager.KEEP_ALIVE_NOTIFICATION_ID,
                    buildCurrentNotification()
                )
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    companion object {
        const val ACTION_START_OR_UPDATE = "com.example.action.START_OR_UPDATE_WORKSPACE_KEEP_ALIVE"
        const val ACTION_STOP = "com.example.action.STOP_WORKSPACE_KEEP_ALIVE"
        private const val STOP_REQUEST_CODE = 1051
        private const val HEARTBEAT_INTERVAL_MS = 45_000L

        @Volatile
        var userPausedKeepAlive: Boolean = false
            internal set

        fun startOrUpdate(context: Context): Boolean {
            userPausedKeepAlive = false
            val appContext = context.applicationContext
            val intent = Intent(appContext, WorkspaceKeepAliveService::class.java).apply {
                action = ACTION_START_OR_UPDATE
            }
            return try {
                ContextCompat.startForegroundService(appContext, intent)
                true
            } catch (_: SecurityException) {
                false
            } catch (_: IllegalStateException) {
                false
            } catch (_: Exception) {
                false
            }
        }

        fun stop(context: Context) {
            val appContext = context.applicationContext
            try {
                appContext.stopService(Intent(appContext, WorkspaceKeepAliveService::class.java))
            } catch (_: Exception) {
            }
            WorkspaceMemoryKeepAliveCoordinator.setKeepAliveServiceRunning(false)
        }
    }
}
