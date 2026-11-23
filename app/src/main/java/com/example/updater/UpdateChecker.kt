package com.example.updater

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Latest published GitHub Release payload for Antigravity Pulse.
 *
 * @param tagName Release tag (e.g. `v1.2.2`).
 * @param releaseNotes Raw release body (may be null when GitHub omits it).
 * @param publishedAt ISO-8601 publish timestamp (may be null).
 * @param apkAssetUrl Browser download URL for the matching `Antigravity-Pulse-v<tag>.apk` asset.
 * @param apkSizeBytes Reported asset size in bytes (may be null).
 * @param sha256SumsUrl Browser download URL for the release's `SHA256SUMS.txt` asset.
 * @param sha256 Optional SHA-256 supplied directly by the fallback manifest.
 */
data class LatestRelease(
    val tagName: String,
    val releaseNotes: String?,
    val publishedAt: String?,
    val apkAssetUrl: String?,
    val apkSizeBytes: Long?,
    val sha256SumsUrl: String?,
    val sha256: String? = null
) {
    /** Numeric release version without the leading `v` (e.g. `1.2.2`). */
    val versionName: String = tagName.trim().removePrefix("v")
}

object UpdateChecker {
    const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/IceCool30/Antigravity-Pulse-/releases/latest"
    /**
     * Raw, unauthenticated, rate-limit-free fallback. Updated with every release.
     */
    const val UPDATE_MANIFEST_URL =
        "https://raw.githubusercontent.com/IceCool30/Antigravity-Pulse-/main/app/update-manifest.json"
    const val CHECK_TIMEOUT_MS = 15_000L
    const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val TAG = "PulseUpdateChecker"

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
     * Queries GitHub Releases, falling back to the raw manifest when the API is rate-limited
     * or unreachable. Blocking socket I/O is confined to [Dispatchers.IO].
     */
    suspend fun checkForUpdate(context: Context): LatestRelease? = withContext(Dispatchers.IO) {
        if (BuildConfig.VERSION_NAME.isBlank()) return@withContext null
        val fromApi = runCatching { fetchFromApi() }
            .onFailure { Log.w(TAG, "GitHub Releases check failed: ${it.message}") }
            .getOrNull()
        if (fromApi != null) return@withContext fromApi

        Log.w(TAG, "Falling back to update manifest")
        runCatching { fetchFromManifest() }
            .onFailure { Log.w(TAG, "Manifest check failed: ${it.message}") }
            .getOrNull()
    }

    private fun openJson(urlString: String): JSONObject? {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = CHECK_TIMEOUT_MS.toInt()
        connection.readTimeout = CHECK_TIMEOUT_MS.toInt()
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty(
            "User-Agent",
            "AntigravityPulse/${BuildConfig.VERSION_NAME} (Android-AutoUpdate)"
        )
        val responseCode = connection.responseCode
        val stream: InputStream? =
            if (responseCode in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        connection.disconnect()
        if (responseCode !in 200..299 || text.isNullOrBlank()) {
            Log.w(TAG, "GET $urlString -> HTTP $responseCode")
            return null
        }
        return JSONObject(text)
    }

    private fun fetchFromApi(): LatestRelease? {
        val json = openJson(LATEST_RELEASE_API_URL) ?: return null
        val tagName = json.optString("tag_name")
        if (tagName.isBlank()) return null
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
    }

    /** Fallback used when GitHub's REST API is throttled or blocked on the device network. */
    private fun fetchFromManifest(): LatestRelease? {
        val json = openJson(UPDATE_MANIFEST_URL) ?: return null
        val tagName = json.optString("tagName").ifBlank { json.optString("tag_name") }
        val apkUrl = json.optString("apkUrl").ifBlank { json.optString("apkAssetUrl") }
        if (tagName.isBlank() || apkUrl.isBlank()) return null
        val sha256 = json.optString("sha256").ifBlank { null }
        return LatestRelease(
            tagName = tagName,
            releaseNotes = json.optString("releaseNotes").ifBlank { null },
            publishedAt = json.optString("publishedAt").ifBlank { null },
            apkAssetUrl = apkUrl,
            apkSizeBytes = json.optLong("apkSizeBytes").takeIf { it > 0 },
            sha256SumsUrl = json.optString("sha256SumsUrl").ifBlank { null },
            sha256 = sha256
        )
    }
}
