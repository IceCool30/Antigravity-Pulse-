package com.example.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.ui.theme.LocalAppearanceSettings
import com.example.updater.LatestRelease
import com.example.updater.UpdateCheckResult
import com.example.updater.UpdateChecker
import com.example.updater.UpdateDownloadStatus
import com.example.updater.UpdateDownloader
import com.example.updater.UpdateInstaller
import kotlinx.coroutines.launch
import java.io.File

/**
 * Compose Material3 "App Updates" settings section: current version, manual GitHub Releases
 * check with accurate checking/error diagnostics, release notes & APK size disclosure,
 * download with live progress + SHA-256 verified install, and unknown-sources guidance.
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

    val cachedAvailable by UpdateChecker.latestAvailableRelease.collectAsStateWithLifecycle()
    var release by remember { mutableStateOf(cachedAvailable) }
    var releaseSource by remember { mutableStateOf<String?>(null) }
    var isChecking by remember { mutableStateOf(true) }
    var hasCompletedCheck by remember { mutableStateOf(false) }
    var checkError by remember { mutableStateOf<String?>(null) }
    var installHint by remember { mutableStateOf<String?>(null) }

    val downloadState by UpdateDownloader.state.collectAsStateWithLifecycle()

    suspend fun runUpdateCheck() {
        isChecking = true
        checkError = null
        installHint = null
        when (val result = UpdateChecker.checkForUpdateDetailed(context)) {
            is UpdateCheckResult.Success -> {
                release = result.release
                releaseSource = result.source
                checkError = null
            }
            is UpdateCheckResult.Error -> {
                checkError = result.userMessage
            }
        }
        hasCompletedCheck = true
        isChecking = false
    }

    LaunchedEffect(Unit) {
        runUpdateCheck()
    }

    val isNewerRelease = release != null && UpdateChecker.isNewer(release!!.tagName, BuildConfig.VERSION_NAME)

    fun launchInstall(apkFile: File) {
        if (!UpdateInstaller.canRequestPackageInstalls(context)) {
            installHint = "Allow 'Install unknown apps' for Antigravity Pulse, then tap Install again."
            UpdateInstaller.launchUnknownSourcesSettings(context)
            return
        }
        installHint = null
        val intent = UpdateInstaller.buildInstallIntent(context, apkFile)
        if (intent != null) {
            try {
                context.startActivity(intent)
            } catch (err: Exception) {
                installHint = "Unable to open Android package installer (${err.message ?: "unknown error"})."
            }
        } else {
            installHint = "Downloaded APK was missing. Tap Download & Install to retry."
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
                        .padding(horizontal = 4.dp, vertical = 4.dp),
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
                            text = "v${BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                    if (isChecking) {
                        CircularProgressIndicator(
                            color = accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                HorizontalDivider(color = border.copy(alpha = 0.6f), thickness = 1.dp)

                // Release availability status
                when {
                    isChecking && !hasCompletedCheck -> {
                        Text(
                            text = "Checking GitHub Releases for newer versions…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryFg
                        )
                    }
                    checkError != null && !isNewerRelease -> {
                        Text(
                            text = checkError!!,
                            style = MaterialTheme.typography.bodyMedium,
                            color = activeTheme.errorColor,
                            modifier = Modifier.testTag("update_check_error_text")
                        )
                    }
                    isNewerRelease -> {
                        Text(
                            text = "Antigravity Pulse v${release!!.versionName} is available (current: v${BuildConfig.VERSION_NAME}).",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = fg
                        )
                        val metaLine = buildList {
                            release?.publishedAt?.takeIf { it.isNotBlank() }?.let {
                                add("Released ${it.take(10)}")
                            }
                            release?.apkSizeBytes?.takeIf { it > 0 }?.let { sizeBytes ->
                                add("${"%.1f".format(sizeBytes / 1024.0 / 1024.0)} MB")
                            }
                            releaseSource?.let { add(it) }
                        }.joinToString(" • ")
                        if (metaLine.isNotBlank()) {
                            Text(
                                text = metaLine,
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryFg
                            )
                        }
                        val notes = release?.releaseNotes.orEmpty().trim()
                        if (notes.isNotBlank()) {
                            Text(
                                text = if (notes.length > 220) notes.take(220) + "…" else notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = secondaryFg,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    hasCompletedCheck && release != null -> {
                        Text(
                            text = "You're up to date (latest: v${release!!.versionName}).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = activeTheme.successColor
                        )
                    }
                    else -> {
                        Text(
                            text = "Tap 'Check for updates' to check for newer releases.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryFg
                        )
                    }
                }

                // Manual check-for-updates action
                OutlinedButton(
                    onClick = {
                        scope.launch { runUpdateCheck() }
                    },
                    enabled = !isChecking,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("check_for_updates_button")
                ) {
                    Text(
                        text = if (isChecking) "Checking…" else "Check for updates",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Download & install action (only enabled when a newer release exists)
                val readyApkFile = downloadState.downloadedFile?.takeIf {
                    downloadState.status == UpdateDownloadStatus.SUCCESS && it.exists()
                }
                val isBusyDownloading = downloadState.status == UpdateDownloadStatus.DOWNLOADING ||
                    downloadState.status == UpdateDownloadStatus.VERIFYING

                Button(
                    onClick = {
                        if (readyApkFile != null) {
                            launchInstall(readyApkFile)
                            return@Button
                        }
                        val latest = release ?: return@Button
                        installHint = null
                        scope.launch {
                            runCatching { UpdateDownloader.downloadApk(context, latest) }
                                .onSuccess { apkFile ->
                                    launchInstall(apkFile)
                                }
                        }
                    },
                    enabled = isNewerRelease && !isBusyDownloading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = activeTheme.onAccentColor
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("download_and_install_update_button")
                ) {
                    Text(
                        text = when {
                            downloadState.status == UpdateDownloadStatus.DOWNLOADING -> "Downloading…"
                            downloadState.status == UpdateDownloadStatus.VERIFYING -> "Verifying SHA-256…"
                            readyApkFile != null -> "Install v${release?.versionName.orEmpty()}"
                            else -> "Download & Install"
                        },
                        fontWeight = FontWeight.Bold
                    )
                }

                // Download progress / verification / failure feedback
                if (downloadState.status == UpdateDownloadStatus.DOWNLOADING) {
                    val progress = downloadState.progress
                    val progressFraction = ((progress?.percent ?: 0) / 100f).coerceIn(0f, 1f)
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progressFraction },
                            color = accent,
                            trackColor = border.copy(alpha = 0.4f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("update_download_progress")
                        )
                        val downloadedMb = (progress?.bytesDownloaded ?: 0L) / (1024.0 * 1024.0)
                        val totalMb = (progress?.totalBytes ?: 0L) / (1024.0 * 1024.0)
                        val progressText = if (totalMb > 0.1) {
                            "${progress?.percent ?: 0}%  (${"%.1f".format(downloadedMb)} / ${"%.1f".format(totalMb)} MB)"
                        } else {
                            "${"%.1f".format(downloadedMb)} MB downloaded…"
                        }
                        Text(
                            text = progressText,
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryFg
                        )
                    }
                } else if (downloadState.status == UpdateDownloadStatus.VERIFYING) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        CircularProgressIndicator(
                            color = accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Verifying SHA-256 checksum…",
                            style = MaterialTheme.typography.labelMedium,
                            color = secondaryFg
                        )
                    }
                } else if (downloadState.status == UpdateDownloadStatus.FAILED) {
                    Text(
                        text = downloadState.error ?: "Update download failed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = activeTheme.errorColor,
                        modifier = Modifier.testTag("update_download_error_text")
                    )
                    OutlinedButton(
                        onClick = {
                            UpdateDownloader.reset()
                            val latest = release ?: return@OutlinedButton
                            scope.launch {
                                runCatching { UpdateDownloader.downloadApk(context, latest) }
                                    .onSuccess { apkFile -> launchInstall(apkFile) }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("update_retry_button")
                    ) {
                        Text("Retry Download", color = accent, fontWeight = FontWeight.Bold)
                    }
                }

                installHint?.let { hint ->
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = accent
                    )
                }

                // Release page link
                TextButton(
                    onClick = {
                        try {
                            context.startActivity(
                                Intent(
                                    Intent.ACTION_VIEW,
                                    android.net.Uri.parse(UpdateChecker.LATEST_RELEASE_WEB_URL)
                                ).addCategory(Intent.CATEGORY_BROWSABLE)
                            )
                        } catch (_: Exception) {
                        }
                    },
                    modifier = Modifier.testTag("update_release_notes_link")
                ) {
                    Text("View release notes on GitHub", color = accent)
                }

                // One-time unknown sources permission explainer
                Text(
                    text = "To install over-the-air updates, Android asks you once to allow installing unknown apps for Antigravity Pulse. " +
                        "After enabling it, return here and tap Install.",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryFg
                )
            }
        }
    }
}
