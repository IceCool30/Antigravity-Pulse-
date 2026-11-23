package com.example.updater

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

enum class UpdateDownloadStatus {
    IDLE,
    DOWNLOADING,
    VERIFYING,
    SUCCESS,
    FAILED
}

data class UpdateDownloadProgress(
    val bytesDownloaded: Long,
    val totalBytes: Long,
    val percent: Int
)

data class UpdateDownloadState(
    val status: UpdateDownloadStatus,
    val progress: UpdateDownloadProgress?,
    val error: String?,
    val downloadedFile: File? = null
)

/**
 * Downloads the candidate APK from GitHub Releases into the app cache dir, verifies its SHA-256
 * against the release `SHA256SUMS.txt` entry, and only then reports success for install.
 */
object UpdateDownloader {

    private val _state = MutableStateFlow(UpdateDownloadState(UpdateDownloadStatus.IDLE, null, null))
    val state: StateFlow<UpdateDownloadState> = _state

    private const val DOWNLOAD_TIMEOUT_MS = 60_000L
    private const val IO_BUFFER_SIZE = 8192

    /** Resets the download state machine (e.g. after a user taps Retry). */
    fun reset() {
        _state.value = UpdateDownloadState(UpdateDownloadStatus.IDLE, null, null)
    }

    /**
     * Downloads + SHA-256 verifies the APK for [release]. Throws on any failure and reports the
     * failure through [state]; never returns an unverified APK.
     */
    suspend fun downloadApk(context: Context, release: LatestRelease): File = withContext(Dispatchers.IO) {
        val apkFileName = "Antigravity-Pulse-${release.tagName}.apk"
        val sha256SumsUrl = release.sha256SumsUrl
            ?: throw IllegalStateException("Release ${release.tagName} did not include a SHA256SUMS.txt asset.")
        val expectedSha256 = downloadText(context, sha256SumsUrl)
            ?.let { parseSha256Sums(it, apkFileName) }
            ?: throw IllegalStateException("SHA256SUMS.txt for ${release.tagName} did not contain an entry for $apkFileName.")
        val apkUrl = release.apkAssetUrl
            ?: throw IllegalStateException("Release ${release.tagName} did not include a downloadable APK asset.")

        val apkFile = File(File(context.cacheDir, "update"), apkFileName).apply { delete() }
        _state.value = UpdateDownloadState(
            status = UpdateDownloadStatus.DOWNLOADING,
            progress = UpdateDownloadProgress(bytesDownloaded = 0, totalBytes = release.apkSizeBytes ?: 0, percent = 0),
            error = null,
            downloadedFile = apkFile
        )
        downloadFile(context, apkUrl, apkFile)

        _state.value = UpdateDownloadState(status = UpdateDownloadStatus.VERIFYING, progress = null, error = null, downloadedFile = apkFile)
        val actualSha256 = sha256Hex(apkFile)
        if (actualSha256 != expectedSha256.trim().lowercase()) {
            apkFile.delete()
            _state.value = UpdateDownloadState(
                status = UpdateDownloadStatus.FAILED,
                progress = null,
                error = "SHA-256 checksum mismatch for $apkFileName. Refusing to install an unverified APK.",
                downloadedFile = apkFile
            )
            throw IllegalStateException("SHA-256 checksum mismatch for $apkFileName.")
        }

        _state.value = UpdateDownloadState(status = UpdateDownloadStatus.SUCCESS, progress = null, error = null, downloadedFile = apkFile)
        return@withContext apkFile
    }

    private suspend fun downloadFile(context: Context, urlString: String, targetFile: File) {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15_000
        connection.readTimeout = DOWNLOAD_TIMEOUT_MS.toInt()
        connection.setRequestProperty("Accept", UpdateChecker.APK_MIME_TYPE)
        connection.setRequestProperty("User-Agent", "AntigravityPulse/${BuildConfig.VERSION_NAME} (Android-AutoUpdate)")
        val totalBytes = connection.contentLengthLong
        connection.connect()
        try {
            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(IO_BUFFER_SIZE)
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) break
                        output.write(buffer, 0, bytesRead)
                        val bytesDownloaded = bytesRead.toLong()
                        val percent = if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                        _state.value = UpdateDownloadState(
                            status = UpdateDownloadStatus.DOWNLOADING,
                            progress = UpdateDownloadProgress(bytesDownloaded, totalBytes, percent),
                            error = null,
                            downloadedFile = targetFile
                        )
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun downloadText(context: Context, urlString: String): String? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(urlString).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15_000
            connection.readTimeout = DOWNLOAD_TIMEOUT_MS.toInt()
            connection.setRequestProperty("Accept", "text/plain")
            connection.setRequestProperty("User-Agent", "AntigravityPulse/${BuildConfig.VERSION_NAME} (Android-AutoUpdate)")
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            connection.disconnect()
            text
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses a `SHA256SUMS.txt` blob (`<sha256>  <filename>`) and returns the expected hash for the
     * requested APK file name.
     */
    internal fun parseSha256Sums(sumsText: String, apkFileName: String): String? {
        for (rawLine in sumsText.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val tokens = line.split(Regex("\\s+"))
            if (tokens.size < 2) continue
            val hash = tokens[0].trim().lowercase()
            val name = tokens[1].trim().trimStart('*')
            if (name == apkFileName) return hash
        }
        return null
    }

    private fun sha256Hex(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(IO_BUFFER_SIZE)
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead < 0) break
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
