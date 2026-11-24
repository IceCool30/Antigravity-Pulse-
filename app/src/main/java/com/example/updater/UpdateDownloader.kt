package com.example.updater

import android.content.Context
import android.os.SystemClock
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
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
 * Downloads the candidate APK from GitHub Releases into `cacheDir/update/`, computes and verifies
 * its SHA-256 against the release `SHA256SUMS.txt` (or manifest checksum) before allowing install.
 */
object UpdateDownloader {

    private val _state = MutableStateFlow(UpdateDownloadState(UpdateDownloadStatus.IDLE, null, null))
    val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()

    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val DOWNLOAD_TIMEOUT_MS = 90_000
    private const val IO_BUFFER_SIZE = 65_536
    private const val MAX_REDIRECTS = 6
    private const val PROGRESS_EMIT_INTERVAL_MS = 100L

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
        val apkUrl = release.apkAssetUrl
            ?: failAndThrow("Release ${release.tagName} did not include a downloadable APK asset.")

        if (!UpdateChecker.isTrustedUpdateHost(apkUrl)) {
            failAndThrow("Blocked untrusted download host for $apkFileName.")
        }

        // Resolve expected SHA-256 either directly from manifest or from SHA256SUMS.txt
        val expectedSha256 = release.sha256?.takeIf { it.matches(Regex("(?i)[a-f0-9]{64}")) }
            ?: release.sha256SumsUrl?.let { sumsUrl ->
                if (!UpdateChecker.isTrustedUpdateHost(sumsUrl)) {
                    failAndThrow("Blocked untrusted checksum host.")
                }
                downloadText(sumsUrl)?.let { parseSha256Sums(it, apkFileName) }
            }
            ?: failAndThrow("Release ${release.tagName} did not include a verifiable SHA-256 checksum.")

        val updateDir = File(context.cacheDir, "update")
        if (!updateDir.exists() && !updateDir.mkdirs()) {
            failAndThrow("Could not create local update cache directory.")
        }
        // Clean up any stale older APKs in cacheDir/update before writing the new file
        updateDir.listFiles()?.forEach { existing ->
            if (existing.isFile && existing.name.endsWith(".apk", ignoreCase = true)) {
                existing.delete()
            }
        }

        val apkFile = File(updateDir, apkFileName)
        val tempFile = File(updateDir, "$apkFileName.part").apply { delete() }

        _state.value = UpdateDownloadState(
            status = UpdateDownloadStatus.DOWNLOADING,
            progress = UpdateDownloadProgress(
                bytesDownloaded = 0L,
                totalBytes = release.apkSizeBytes ?: 0L,
                percent = 0
            ),
            error = null,
            downloadedFile = null
        )

        val actualSha256 = try {
            downloadFileAndHash(
                urlString = apkUrl,
                targetFile = tempFile,
                fallbackTotalBytes = release.apkSizeBytes ?: -1L
            )
        } catch (err: Exception) {
            tempFile.delete()
            failAndThrow(err.message ?: "Download interrupted. Please retry.", err)
        }

        _state.value = UpdateDownloadState(
            status = UpdateDownloadStatus.VERIFYING,
            progress = null,
            error = null,
            downloadedFile = null
        )

        if (actualSha256 != expectedSha256.trim().lowercase()) {
            tempFile.delete()
            failAndThrow("SHA-256 checksum mismatch for $apkFileName. Refusing to install an unverified APK.")
        }

        if (apkFile.exists()) {
            apkFile.delete()
        }
        if (!tempFile.renameTo(apkFile)) {
            tempFile.copyTo(apkFile, overwrite = true)
            tempFile.delete()
        }

        _state.value = UpdateDownloadState(
            status = UpdateDownloadStatus.SUCCESS,
            progress = UpdateDownloadProgress(
                bytesDownloaded = apkFile.length(),
                totalBytes = apkFile.length(),
                percent = 100
            ),
            error = null,
            downloadedFile = apkFile
        )
        return@withContext apkFile
    }

    private fun failAndThrow(message: String, cause: Throwable? = null): Nothing {
        _state.value = UpdateDownloadState(
            status = UpdateDownloadStatus.FAILED,
            progress = null,
            error = message,
            downloadedFile = null
        )
        throw IllegalStateException(message, cause)
    }

    private fun openFollowingRedirects(
        initialUrl: String,
        acceptHeader: String
    ): HttpURLConnection {
        var currentUrl = initialUrl
        repeat(MAX_REDIRECTS + 1) {
            if (!UpdateChecker.isTrustedUpdateHost(currentUrl)) {
                throw SecurityException("Blocked redirect to untrusted host: $currentUrl")
            }
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = DOWNLOAD_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", acceptHeader)
            connection.setRequestProperty(
                "User-Agent",
                "AntigravityPulse/${BuildConfig.VERSION_NAME} (Android-AutoUpdate)"
            )
            val code = connection.responseCode
            if (code in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("HTTP $code redirect without Location header")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
                return@repeat
            }
            if (code !in 200..299) {
                connection.disconnect()
                throw IllegalStateException("Download server returned HTTP $code")
            }
            return connection
        }
        throw IllegalStateException("Too many redirects while downloading update")
    }

    private fun downloadFileAndHash(
        urlString: String,
        targetFile: File,
        fallbackTotalBytes: Long
    ): String {
        val connection = openFollowingRedirects(
            initialUrl = urlString,
            acceptHeader = "application/octet-stream, */*"
        )
        val headerLength = connection.contentLengthLong
        val totalBytes = if (headerLength > 0L) headerLength else fallbackTotalBytes
        val digest = MessageDigest.getInstance("SHA-256")
        var totalDownloaded = 0L
        var lastEmittedPercent = -1
        var lastEmitAtMs = 0L

        try {
            connection.inputStream.use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(IO_BUFFER_SIZE)
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) break
                        output.write(buffer, 0, bytesRead)
                        digest.update(buffer, 0, bytesRead)
                        totalDownloaded += bytesRead

                        val percent = if (totalBytes > 0L) {
                            ((totalDownloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        }
                        val nowMs = SystemClock.elapsedRealtime()
                        if (percent != lastEmittedPercent &&
                            (nowMs - lastEmitAtMs >= PROGRESS_EMIT_INTERVAL_MS || percent == 100)
                        ) {
                            lastEmittedPercent = percent
                            lastEmitAtMs = nowMs
                            _state.value = UpdateDownloadState(
                                status = UpdateDownloadStatus.DOWNLOADING,
                                progress = UpdateDownloadProgress(
                                    bytesDownloaded = totalDownloaded,
                                    totalBytes = totalBytes.coerceAtLeast(totalDownloaded),
                                    percent = percent
                                ),
                                error = null,
                                downloadedFile = null
                            )
                        }
                    }
                    output.flush()
                }
            }
        } finally {
            connection.disconnect()
        }
        if (totalDownloaded <= 0L) {
            throw IllegalStateException("Downloaded APK was empty (0 bytes).")
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun downloadText(urlString: String): String? {
        return try {
            val connection = openFollowingRedirects(
                initialUrl = urlString,
                acceptHeader = "text/plain, */*"
            )
            try {
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Parses a `SHA256SUMS.txt` blob (`<sha256>  <filename>`) and returns the expected hash for the
     * requested APK file name (falling back to the sole `.apk` entry if the file name matches loosely).
     */
    internal fun parseSha256Sums(sumsText: String, apkFileName: String): String? {
        var fallbackApkHash: String? = null
        var apkEntryCount = 0
        for (rawLine in sumsText.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty()) continue
            val tokens = line.split(Regex("\\s+"))
            if (tokens.size < 2) continue
            val hash = tokens[0].trim().lowercase()
            if (!hash.matches(Regex("[a-f0-9]{64}"))) continue
            val name = tokens[1].trim().trimStart('*')
            if (name.equals(apkFileName, ignoreCase = true)) return hash
            if (name.endsWith(".apk", ignoreCase = true)) {
                apkEntryCount += 1
                fallbackApkHash = hash
            }
        }
        return if (apkEntryCount == 1) fallbackApkHash else null
    }
}
