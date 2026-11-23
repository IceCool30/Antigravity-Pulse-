package com.example.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.BuildConfig
import com.example.ui.theme.LocalAppearanceSettings
import com.example.updater.LatestRelease
import com.example.updater.UpdateChecker
import com.example.updater.UpdateDownloader
import com.example.updater.UpdateDownloadStatus
import com.example.updater.UpdateInstaller
import kotlinx.coroutines.launch

/**
 * Compose Material3 "App Updates" settings section: current version, manual GitHub Releases
 * check, release notes / APK size disclosure, download with progress + SHA-256 verified install,
 * release page link, and the one-time "Install unknown apps" permission explainer.
 */
@Composable
internal fun UpdateSettingsSection(
    surface: Color,
    border: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    isFrostedGlass: Boolean
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activeTheme = LocalAppearanceSettings.current
    val elevated = activeTheme.elevatedSurfaceColor

    var release by remember { mutableStateOf<LatestRelease?>(null) }
    var isChecking by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf<String?>(null) }

    val downloadState by UpdateDownloader.state.collectAsState()

    LaunchedEffect(Unit) {
        runCatching { UpdateChecker.checkForUpdate(context) }
            .onSuccess { latest -> release = latest }
            .onFailure { checkError = "Unable to reach the Antigravity Pulse release service right now." }
    }

    val isNewerRelease = release != null && UpdateChecker.isNewer(release!!.tagName, BuildConfig.VERSION_NAME)

    fun requestInstall(latest: LatestRelease, apkFile: java.io.File) {
        if (!UpdateInstaller.canRequestPackageInstalls(context)) {
            UpdateInstaller.launchUnknownSourcesSettings(context)
            return
        }
        UpdateInstaller.buildInstallIntent(context, apkFile)?.let { intent ->
            context.startActivity(intent)
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSectionHeader(
            title = "App Updates",
            secondaryFg = secondaryFg
        )

        SettingsCard(
            surface = surface,
            accent = accent,
            border = border,
            isFrostedGlass = isFrostedGlass
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Current Version Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Current Version",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = fg
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = BuildConfig.VERSION_NAME,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                }

                HorizontalDivider(color = border.copy(alpha = 0.6f), thickness = 1.dp)

                // Release availability status
                when {
                    release == null && checkError != null -> {
                        Text(
                            text = checkError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = accent
                        )
                    }
                    release == null -> {
                        Text(
                            text = "You're up to date with the latest release.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryFg
                        )
                    }
                    isNewerRelease -> {
                        Text(
                            text = "Antigravity Pulse ${release!!.versionName} is available (current: ${BuildConfig.VERSION_NAME}).",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = fg
                        )
                        release?.publishedAt?.takeIf { it.isNotBlank() }?.let { publishedAt ->
                            Text(
                                text = "Released ${publishedAt.take(10)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryFg
                            )
                        }
                        val notes = release?.releaseNotes.orEmpty()
                        if (notes.isNotBlank()) {
                            Text(
                                text = if (notes.length > 140) notes.take(140) + "…" else notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryFg,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        release?.apkSizeBytes?.let { sizeBytes ->
                            Text(
                                text = "APK size: ${"%.1f".format(sizeBytes / 1024.0 / 1024.0)} MB",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryFg
                            )
                        }
                    }
                    else -> {
                        Text(
                            text = "You're up to date with the latest release.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryFg
                        )
                    }
                }

                // Manual check-for-updates action
                OutlinedButton(
                    onClick = {
                        isChecking = true
                        checkError = null
                        scope.launch {
                            val latest = UpdateChecker.checkForUpdate(context)
                            isChecking = false
                            if (latest == null) {
                                checkError = "Unable to reach the Antigravity Pulse release service right now."
                            } else {
                                release = latest
                            }
                        }
                    },
                    enabled = !isChecking,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("check_for_updates_button")
                ) {
                    Text(
                        text = if (isChecking) "Checking…" else "Check for updates",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Download & install action (only when a newer release exists)
                Button(
                    onClick = {
                        val latest = release ?: return@Button
                        scope.launch {
                            runCatching { UpdateDownloader.downloadApk(context, latest) }
                                .onSuccess { apkFile ->
                                    requestInstall(latest, apkFile)
                                }
                                .onFailure { downloadError ->
                                    UpdateDownloader.reset()
                                    checkError = downloadError.message ?: "Update download failed. Please retry."
                                }
                        }
                    },
                    enabled = isNewerRelease && downloadState.status != UpdateDownloadStatus.DOWNLOADING,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = activeTheme.onAccentColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("download_and_install_update_button")
                ) {
                    Text("Download & Install", fontWeight = FontWeight.Bold)
                }

                // Download progress + failure handling
                if (downloadState.status == UpdateDownloadStatus.DOWNLOADING) {
                    val progressFraction = ((downloadState.progress?.percent ?: 0) / 100f).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = progressFraction,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_download_progress")
                        )
                        Text(
                            text = "${downloadState.progress?.percent ?: 0}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryFg
                        )
                    }
                }
                if (downloadState.status == UpdateDownloadStatus.FAILED) {
                    Text(
                        text = downloadState.error ?: "Update download failed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = accent
                    )
                    OutlinedButton(
                        onClick = { UpdateDownloader.reset() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("update_retry_button")
                    ) {
                        Text("Retry", color = accent, fontWeight = FontWeight.Bold)
                    }
                }

                // Release page link
                TextButton(
                    onClick = {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                android.net.Uri.parse(
                                    "https://github.com/IceCool30/Antigravity-Pulse-/releases/latest"
                                )
                            ).addCategory(Intent.CATEGORY_BROWSABLE)
                        )
                    },
                    modifier = Modifier.testTag("update_release_notes_link")
                ) {
                    Text("View release notes", color = accent)
                }

                // One-time unknown sources permission explainer
                Text(
                    text = "To install over-the-air updates, Android asks you once to allow installing unknown apps for Antigravity Pulse. " +
                        "Open the Install unknown apps setting, allow Antigravity Pulse, then tap 'Download & Install' again.",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryFg
                )
            }
        }
    }
}
