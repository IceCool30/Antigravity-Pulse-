package com.example.updater

import android.content.Context
import java.net.HttpURLConnection
import java.net.URL
import com.example.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

/**
 * Latest published GitHub Release payload for Antigravity Pulse.
 *
 * @param tagName Release tag (e.g. `v1.2.2`).
 * @param releaseNotes Raw release body (may be null when GitHub omits it).
 * @param publishedAt ISO-8601 publish timestamp (may be null).
 * @param apkAssetUrl Browser download URL for the matching `Antigravity-Pulse-v<tag>.apk` asset.
 * @param apkSizeBytes Reported asset size in bytes (may be null).
 * @param sha256SumsUrl Browser download URL for the release's `SHA256SUMS.txt` asset.
 */
data class LatestRelease(
    val tagName: String,
    val releaseNotes: String?,
    val publishedAt: String?,
    val apkAssetUrl: String?,
    val apkSizeBytes: Long?,
    val sha256SumsUrl: String?
) {
    /** Numeric release version without the leading `v` (e.g. `1.2.2`). */
    val versionName: String = tagName.trim().removePrefix("v")
}

object UpdateChecker {
    const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/IceCool30/Antigravity-Pulse-/releases/latest"
    const val CHECK_TIMEOUT_MS = 15_000L
    const val APK_MIME_TYPE = "application/vnd.android.package-archive"

    /**
     * Pure semantic version comparison: returns `true` only when [latest] is strictly newer
     * than [current]. Numeric dot-separated segments are compared component-wise so that
     * `1.10.0 > 1.9.9` (no accidental lexicographic ordering).
     */
    internal fun isNewer(latest: String, current: String): Boolean {
        val latestParts = latest.trim().removePrefix("v").split('.').map { segment ->
            segment.toIntOrNull() ?: 0
        }
        val currentParts = current.trim().removePrefix("v").split('.').map { segment ->
            segment.toIntOrNull() ?: 0
        }
        val maxLength = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until maxLength) {
            val latestValue = latestParts.getOrElse(index) { 0 }
            val currentValue = currentParts.getOrElse(index) { 0 }
            if (latestValue != currentValue) return latestValue > currentValue
        }
        return false
    }

    /**
     * Queries the GitHub latest-release API via `HttpURLConnection` + `org.json` and maps the
     * release onto a [LatestRelease]. Never throws; returns null on any network/parse failure.
     */
    suspend fun checkForUpdate(context: Context): LatestRelease? {
        if (BuildConfig.VERSION_NAME.isBlank()) return null
        return try {
            val connection = URL(LATEST_RELEASE_API_URL).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CHECK_TIMEOUT_MS.toInt()
            connection.readTimeout = CHECK_TIMEOUT_MS.toInt()
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "AntigravityPulse/${BuildConfig.VERSION_NAME} (Android-AutoUpdate)")
            val responseCode = connection.responseCode
            val responseStream: InputStream? =
                if (responseCode in 200..299) connection.inputStream else connection.errorStream
            if (responseCode !in 200..299 || responseStream == null) {
                connection.disconnect()
                return null
            }
            val jsonText = responseStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            connection.disconnect()

            val json = JSONObject(jsonText)
            val tagName = json.optString("tag_name")
            val releaseNotes = if (json.has("body")) json.optString("body") else null
            val publishedAt = if (json.has("published_at")) json.optString("published_at") else null
            val assets = json.optJSONArray("assets") ?: JSONArray()
            var apkAssetUrl: String? = null
            var apkSizeBytes: Long? = null
            var sha256SumsUrl: String? = null
            val expectedAssetName = "Antigravity-Pulse-$tagName.apk"
            for (index in 0 until assets.length()) {
                val asset = assets.optJSONObject(index) ?: continue
                val assetName = asset.optString("name")
                if (assetName == expectedAssetName) {
                    apkAssetUrl = asset.optString("browser_download_url")
                    apkSizeBytes = asset.optLong("size")
                } else if (assetName == "SHA256SUMS.txt") {
                    sha256SumsUrl = asset.optString("browser_download_url")
                }
            }
            if (apkAssetUrl == null) return null
            return LatestRelease(
                tagName = tagName,
                releaseNotes = releaseNotes,
                publishedAt = publishedAt,
                apkAssetUrl = apkAssetUrl,
                apkSizeBytes = apkSizeBytes,
                sha256SumsUrl = sha256SumsUrl
            )
        } catch (_: Exception) {
            null
        }
    }
}
