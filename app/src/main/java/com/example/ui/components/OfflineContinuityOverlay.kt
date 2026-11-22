package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.remote.OfflineRecoveryStatus
import com.example.data.remote.WebViewContinuityState
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalAppearanceSettings
import com.example.ui.theme.frostedGlass
import kotlinx.coroutines.delay

/**
 * Renders seamless offline continuity over a pinned WebView:
 * 1. If a main-frame network error or reload occurs, displays the last-good cached visual snapshot
 *    instead of Chromium's blank error page until the fresh live view finishes painting.
 * 2. Displays a compact, non-intrusive frosted-glass status pill at the top during outages and
 *    recovery, allowing one-tap reload to the latest phase once internet returns.
 */
@Composable
fun OfflineContinuityOverlay(
    continuity: WebViewContinuityState,
    workspaceLabel: String,
    onRetryOrReloadLatest: () -> Unit,
    onDismissRestoredBanner: () -> Unit,
    modifier: Modifier = Modifier
) {
    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val border = appearance.borderColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor
    val isFrostedGlass = appearance.isFrostedGlassEnabled

    // Auto-dismiss the "Back online" pill cleanly after 5.5 seconds so fullscreen stays clutter-free
    LaunchedEffect(continuity.status) {
        if (continuity.status == OfflineRecoveryStatus.RESTORED) {
            delay(5_500L)
            onDismissRestoredBanner()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // 1. Visual Snapshot Shield (hides Chromium ERR_INTERNET_DISCONNECTED or white reload flashes)
        AnimatedVisibility(
            visible = continuity.showingSnapshotShield,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            val snapshot = continuity.cachedSnapshot
            if (snapshot != null && !snapshot.isRecycled) {
                val imageBitmap = remember(snapshot) { snapshot.asImageBitmap() }
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "$workspaceLabel cached view",
                    contentScale = ContentScale.FillBounds,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("offline_cached_snapshot_image")
                )
            } else {
                // Cold-start offline fallback (only shown if no cached frame existed before losing internet)
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(bg)
                        .padding(24.dp)
                        .testTag("offline_cold_start_card"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .frostedGlass(
                                shape = RoundedCornerShape(24.dp),
                                baseColor = surface,
                                tintColor = accent,
                                borderColor = border,
                                enabled = isFrostedGlass,
                                fillAlpha = 0.86f,
                                tintAlpha = 0.08f,
                                borderAlpha = 0.6f
                            )
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(accent.copy(alpha = 0.16f))
                                .border(1.5.dp, accent.copy(alpha = 0.5f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline",
                                tint = accent,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                        Text(
                            text = "$workspaceLabel Waiting for Internet",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = fg,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "Your connection dropped before the page could load. Antigravity Pulse will resume automatically as soon as Wi-Fi or cellular data returns.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryFg,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Button(
                            onClick = onRetryOrReloadLatest,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accent,
                                contentColor = appearance.onAccentColor
                            ),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                            modifier = Modifier
                                .height(48.dp)
                                .testTag("offline_retry_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Retry Connection", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 2. Non-Intrusive Top Frosted Status Pill
        val showPill = continuity.status != OfflineRecoveryStatus.ONLINE
        AnimatedVisibility(
            visible = showPill,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            val isRestoredOrReconnecting =
                continuity.status == OfflineRecoveryStatus.RESTORED ||
                    continuity.status == OfflineRecoveryStatus.RECONNECTING
            val pillAccent = if (isRestoredOrReconnecting) appearance.successColor else accent

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .frostedGlass(
                        shape = RoundedCornerShape(16.dp),
                        baseColor = surface,
                        tintColor = pillAccent,
                        borderColor = pillAccent,
                        enabled = isFrostedGlass,
                        fillAlpha = 0.90f,
                        tintAlpha = 0.14f,
                        borderAlpha = 0.55f
                    )
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .testTag("offline_continuity_status_pill"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (continuity.status) {
                        OfflineRecoveryStatus.RECONNECTING -> {
                            CircularProgressIndicator(
                                color = pillAccent,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                        OfflineRecoveryStatus.RESTORED -> {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "Back online",
                                tint = pillAccent,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Offline cached view",
                                tint = pillAccent,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = continuity.statusMessage.ifBlank {
                            when (continuity.status) {
                                OfflineRecoveryStatus.OFFLINE_CACHED ->
                                    "Offline • Cached view active"
                                OfflineRecoveryStatus.RECONNECTING ->
                                    "Back online • Updating to latest..."
                                OfflineRecoveryStatus.RESTORED ->
                                    "Back online • Session live"
                                OfflineRecoveryStatus.ONLINE -> ""
                            }
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = fg,
                        fontWeight = FontWeight.Medium
                    )
                }

                if (continuity.status == OfflineRecoveryStatus.OFFLINE_CACHED ||
                    continuity.status == OfflineRecoveryStatus.RESTORED
                ) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (continuity.status == OfflineRecoveryStatus.RESTORED) "Reload" else "Retry",
                        style = MaterialTheme.typography.labelMedium,
                        color = pillAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(pillAccent.copy(alpha = 0.16f))
                            .clickable { onRetryOrReloadLatest() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .testTag("offline_pill_action_button")
                    )
                }
            }
        }
    }
}
