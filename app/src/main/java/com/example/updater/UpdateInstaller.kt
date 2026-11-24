package com.example.updater

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.example.BuildConfig
import java.io.File

/**
 * Handles the Android package-install hand-off for the verified self-update APK:
 * one-tap [Intent.ACTION_VIEW] install when the user has granted the
 * "Install unknown apps" permission, otherwise a deep link into the system
 * unknown-sources settings so the user can grant it and retry.
 */
object UpdateInstaller {

    const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    fun canRequestPackageInstalls(context: Context): Boolean {
        // ACTION_MANAGE_UNKNOWN_APP_SOURCES and this API require Android O (API 26).
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    /**
     * Builds the [Intent.ACTION_VIEW] install intent for the verified APK file using
     * [Intent.setDataAndType] (never calling `type = ...` after setting `data`, which clears `data`).
     */
    fun buildInstallIntent(context: Context, apkFile: File): Intent? {
        if (!apkFile.exists() || apkFile.length() <= 0L) return null
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            )
        }
    }

    /** Deep-links the user into the Android "Install unknown apps" settings screen. */
    fun launchUnknownSourcesSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.fromParts("package", context.packageName, null)
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
