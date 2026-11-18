package com.example.data.remote

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class NotificationApprovalAction(
    val messageId: String,
    val command: String,
    val approved: Boolean
)

/**
 * In-process event bus delivering Approve/Reject decisions tapped directly inside the Android
 * notification shade to `AntigravityViewModel`.
 */
object ApprovalNotificationBus {
    private val _actions = MutableSharedFlow<NotificationApprovalAction>(extraBufferCapacity = 16)
    val actions: SharedFlow<NotificationApprovalAction> = _actions.asSharedFlow()

    fun emit(action: NotificationApprovalAction) {
        _actions.tryEmit(action)
    }
}

/**
 * BroadcastReceiver wired to the Approve & Run / Reject action buttons on Antigravity step
 * approval notifications.
 */
class ApprovalNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getStringExtra(ApprovalNotificationHelper.EXTRA_MESSAGE_ID).orEmpty()
        val command = intent.getStringExtra(ApprovalNotificationHelper.EXTRA_COMMAND).orEmpty()
        val notificationId = intent.getIntExtra(ApprovalNotificationHelper.EXTRA_NOTIFICATION_ID, 0)
        val approved = when (intent.action) {
            ApprovalNotificationHelper.ACTION_APPROVE -> true
            ApprovalNotificationHelper.ACTION_REJECT -> false
            else -> return
        }

        if (messageId.isNotBlank()) {
            ApprovalNotificationBus.emit(
                NotificationApprovalAction(
                    messageId = messageId,
                    command = command,
                    approved = approved
                )
            )
        }
        if (notificationId != 0) {
            NotificationManagerCompat.from(context).cancel(notificationId)
        }
    }
}

object ApprovalNotificationHelper {
    const val CHANNEL_ID = "antigravity_step_approvals"
    const val ACTION_APPROVE = "com.aistudio.antigravitypulse.ACTION_APPROVE_STEP"
    const val ACTION_REJECT = "com.aistudio.antigravitypulse.ACTION_REJECT_STEP"
    const val EXTRA_MESSAGE_ID = "extra_message_id"
    const val EXTRA_COMMAND = "extra_command"
    const val EXTRA_NOTIFICATION_ID = "extra_notification_id"

    fun ensureNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Antigravity Step Approvals",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Approve or reject queued remote Antigravity commands directly from the notification shade."
                enableVibration(true)
            }
            manager.createNotificationChannel(channel)
        }
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

    fun showStepApprovalNotification(
        context: Context,
        messageId: String,
        workspaceName: String,
        command: String
    ): Boolean {
        if (!hasNotificationPermission(context)) return false
        ensureNotificationChannel(context)

        val notificationId = (messageId.hashCode() and 0x7FFFFFFF).coerceAtLeast(1001)
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val approveIntent = Intent(context, ApprovalNotificationReceiver::class.java).apply {
            action = ACTION_APPROVE
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_COMMAND, command)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val approvePendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 1,
            approveIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val rejectIntent = Intent(context, ApprovalNotificationReceiver::class.java).apply {
            action = ACTION_REJECT
            putExtra(EXTRA_MESSAGE_ID, messageId)
            putExtra(EXTRA_COMMAND, command)
            putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        }
        val rejectPendingIntent = PendingIntent.getBroadcast(
            context,
            notificationId + 2,
            rejectIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Approval Required • $workspaceName")
            .setContentText("$ $command")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Remote Antigravity agent is waiting for your confirmation:\n\n$ $command")
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)
            .addAction(0, "Approve & Run", approvePendingIntent)
            .addAction(0, "Reject", rejectPendingIntent)
            .build()

        return try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            true
        } catch (_: SecurityException) {
            false
        }
    }
}
