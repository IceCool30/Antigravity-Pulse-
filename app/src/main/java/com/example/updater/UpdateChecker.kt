package com.example.updater

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

/**
 * Latest published GitHub Release payload for Antigravity Pulse.
 *
 * @param tagName Release tag (e.g. `v1.2.4`).
 * @param releaseNotes Raw release body (may be null when GitHub omits it).
 * @param publishedAt ISO-8601 publish timestamp (may be null).
 * @param apkAssetUrl Browser download URL for the matching `Antigravity-Pulse-<tag>.apk` asset.
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
    /** Numeric release version without the leading `v` (e.g. `1.2.4`). */
    val versionName: String = tagName.trim().removePrefix("v").removePrefix("V")
}

sealed interface UpdateCheckResult {
    data class Success(
        val release: LatestRelease,
        val source: String
    ) : UpdateCheckResult

    data class Error(
        val userMessage: String,
        val technicalDetails: String
    ) : UpdateCheckResult
}

object UpdateChecker {
    const val REPO_OWNER = "IceCool30"
    const val REPO_NAME = "Antigravity-Pulse-"

    const val LATEST_RELEASE_API_URL =
        "https://api.github.com/repos/$REPO_OWNER/$REPO_NAME/releases/latest"

    /**
     * Direct GitHub HTML latest-release endpoint. Its HTTP 302 Location header points straight
     * to `/releases/tag/<tag>` with zero REST API rate limit.
     */
    const val LATEST_RELEASE_WEB_URL =
        "https://github.com/$REPO_OWNER/$REPO_NAME/releases/latest"

    /**
     * Raw, unauthenticated, rate-limit-free fallback. Updated with every release.
     */
    const val UPDATE_MANIFEST_URL =
        "https://raw.githubusercontent.com/$REPO_OWNER/$REPO_NAME/main/app/update-manifest.json"

    /**
     * Global CDN mirror of the update manifest for mobile carriers that throttle raw.githubusercontent.com.
     */
    const val CDN_MANIFEST_URL =
        "https://cdn.jsdelivr.net/gh/$REPO_OWNER/$REPO_NAME@main/app/update-manifest.json"

    const val CHECK_TIMEOUT_MS = 15_000L
    const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    private const val MAX_REDIRECTS = 6
    private const val TAG = "PulseUpdateChecker"

    private val _latestAvailableRelease = MutableStateFlow<LatestRelease?>(null)
    val latestAvailableRelease: StateFlow<LatestRelease?> = _latestAvailableRelease.asStateFlow()

    /**
     * Pure semantic version comparison: returns `true` only when [latest] is strictly newer
     * than [current]. Numeric dot-separated segments are compared component-wise so that
     * `1.10.0 > 1.9.9` (no accidental lexicographic ordering).
     */
    internal fun isNewer(latest: String, current: String): Boolean {
        val latestParts = normalizeVersionSegments(latest)
        val currentParts = normalizeVersionSegments(current)
        if (latestParts.isEmpty()) return false
        val maxLength = maxOf(latestParts.size, currentParts.size)
        for (index in 0 until maxLength) {
            val latestValue = latestParts.getOrElse(index) { 0 }
            val currentValue = currentParts.getOrElse(index) { 0 }
            if (latestValue != currentValue) return latestValue > currentValue
        }
        return false
    }

    private fun normalizeVersionSegments(raw: String): List<Int> {
        val cleaned = raw.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
        if (cleaned.isBlank()) return emptyList()
        return cleaned.split('.').map { segment ->
            segment.takeWhile { it.isDigit() }.toIntOrNull() ?: 0
        }
    }

    /**
     * Backwards-compatible helper returning the latest release or null on error.
     */
    suspend fun checkForUpdate(context: Context): LatestRelease? {
        return when (val result = checkForUpdateDetailed(context)) {
            is UpdateCheckResult.Success -> result.release
            is UpdateCheckResult.Error -> null
        }
    }

    /**
     * Resilient 4-tier release lookup confined to [Dispatchers.IO]:
     * 1. GitHub Releases REST API (`api.github.com`)
     * 2. Direct GitHub `/releases/latest` HTTP redirect (`github.com`, no API rate limit)
     * 3. Raw GitHub manifest (`raw.githubusercontent.com`, cache-busted)
     * 4. jsDelivr CDN mirror (`cdn.jsdelivr.net`, carrier-block fallback)
     */
    suspend fun checkForUpdateDetailed(context: Context): UpdateCheckResult = withContext(Dispatchers.IO) {
        if (BuildConfig.VERSION_NAME.isBlank()) {
            return@withContext UpdateCheckResult.Error(
                userMessage = "App version metadata is missing.",
                technicalDetails = "BuildConfig.VERSION_NAME is blank"
            )
        }

        val diagnostics = mutableListOf<String>()

        // Tier 1: GitHub Releases REST API
        val apiResult = runCatching { fetchFromApi() }
        apiResult.getOrNull()?.let { release ->
            recordDiscoveredRelease(release)
            return@withContext UpdateCheckResult.Success(release, source = "GitHub Releases API")
        }
        apiResult.exceptionOrNull()?.let { err ->
            val msg = "API: ${err.message ?: err.javaClass.simpleName}"
            diagnostics += msg
            Log.w(TAG, msg)
        }

        // Tier 2: Direct GitHub web /releases/latest redirect (zero API rate limit)
        val redirectResult = runCatching { fetchFromLatestRedirect() }
        redirectResult.getOrNull()?.let { redirectRelease ->
            // Best-effort enrichment from manifest if it matches the same tag
            val enriched = runCatching {
                val manifest = fetchFromManifestUrl("$UPDATE_MANIFEST_URL?ts=${System.currentTimeMillis()}")
                if (manifest != null && manifest.versionName == redirectRelease.versionName) {
                    redirectRelease.copy(
                        releaseNotes = manifest.releaseNotes ?: redirectRelease.releaseNotes,
                        publishedAt = manifest.publishedAt ?: redirectRelease.publishedAt,
                        apkSizeBytes = manifest.apkSizeBytes ?: redirectRelease.apkSizeBytes,
                        sha256 = manifest.sha256 ?: redirectRelease.sha256
                    )
                } else {
                    redirectRelease
                }
            }.getOrDefault(redirectRelease)
            recordDiscoveredRelease(enriched)
            return@withContext UpdateCheckResult.Success(enriched, source = "GitHub Releases")
        }
        redirectResult.exceptionOrNull()?.let { err ->
            val msg = "GitHub redirect: ${err.message ?: err.javaClass.simpleName}"
            diagnostics += msg
            Log.w(TAG, msg)
        }

        // Tier 3: Raw GitHub manifest (cache-busted)
        val rawManifestResult = runCatching {
            fetchFromManifestUrl("$UPDATE_MANIFEST_URL?ts=${System.currentTimeMillis()}")
        }
        rawManifestResult.getOrNull()?.let { release ->
            recordDiscoveredRelease(release)
            return@withContext UpdateCheckResult.Success(release, source = "Release Manifest")
        }
        rawManifestResult.exceptionOrNull()?.let { err ->
            val msg = "Raw manifest: ${err.message ?: err.javaClass.simpleName}"
            diagnostics += msg
            Log.w(TAG, msg)
        }

        // Tier 4: jsDelivr CDN mirror of update-manifest.json
        val cdnResult = runCatching {
            fetchFromManifestUrl("$CDN_MANIFEST_URL?ts=${System.currentTimeMillis()}")
        }
        cdnResult.getOrNull()?.let { release ->
            recordDiscoveredRelease(release)
            return@withContext UpdateCheckResult.Success(release, source = "CDN Mirror")
        }
        cdnResult.exceptionOrNull()?.let { err ->
            val msg = "CDN manifest: ${err.message ?: err.javaClass.simpleName}"
            diagnostics += msg
            Log.w(TAG, msg)
        }

        val detailString = diagnostics.joinToString(" | ").ifBlank { "No release metadata returned" }
        val userMessage = when {
            detailString.contains("UnknownHostException", ignoreCase = true) ||
                detailString.contains("Unable to resolve host", ignoreCase = true) ->
                "Unable to reach GitHub right now. Check your internet or DNS connection and try again."
            detailString.contains("Timeout", ignoreCase = true) ->
                "Connection to GitHub timed out. Please retry when your connection stabilizes."
            detailString.contains("HTTP 403", ignoreCase = true) ||
                detailString.contains("HTTP 429", ignoreCase = true) ->
                "GitHub temporarily rate-limited update checks from this network. Please retry in a moment."
            else ->
                "Could not fetch release info right now ($detailString)."
        }
        UpdateCheckResult.Error(userMessage = userMessage, technicalDetails = detailString)
    }

    private fun recordDiscoveredRelease(release: LatestRelease) {
        if (isNewer(release.tagName, BuildConfig.VERSION_NAME)) {
            _latestAvailableRelease.value = release
        } else {
            _latestAvailableRelease.value = null
        }
    }

    internal fun isTrustedUpdateHost(urlString: String): Boolean {
        return try {
            val uri = URI(urlString.trim())
            if (!uri.scheme.equals("https", ignoreCase = true)) return false
            val host = uri.host?.lowercase()?.trimEnd('.').orEmpty()
            host == "github.com" ||
                host == "api.github.com" ||
                host == "raw.githubusercontent.com" ||
                host == "objects.githubusercontent.com" ||
                host == "release-assets.githubusercontent.com" ||
                host.endsWith(".githubusercontent.com") ||
                host == "cdn.jsdelivr.net"
        } catch (_: Exception) {
            false
        }
    }

    private fun openHttpsFollowingRedirects(
        initialUrl: String,
        acceptHeader: String,
        extraHeaders: Map<String, String> = emptyMap()
    ): Pair<Int, String> {
        var currentUrl = initialUrl
        repeat(MAX_REDIRECTS + 1) {
            if (!isTrustedUpdateHost(currentUrl)) {
                throw SecurityException("Blocked untrusted update host: $currentUrl")
            }
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = CHECK_TIMEOUT_MS.toInt()
            connection.readTimeout = CHECK_TIMEOUT_MS.toInt()
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.setRequestProperty("Accept", acceptHeader)
            connection.setRequestProperty("Cache-Control", "no-cache")
            connection.setRequestProperty("Pragma", "no-cache")
            connection.setRequestProperty(
                "User-Agent",
                "AntigravityPulse/${BuildConfig.VERSION_NAME} (Android-AutoUpdate)"
            )
            extraHeaders.forEach { (k, v) -> connection.setRequestProperty(k, v) }

            val responseCode = connection.responseCode
            if (responseCode in listOf(301, 302, 303, 307, 308)) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) {
                    throw IllegalStateException("HTTP $responseCode redirect without Location")
                }
                currentUrl = URL(URL(currentUrl), location).toString()
                return@repeat
            }
            val stream: InputStream? =
                if (responseCode in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            connection.disconnect()
            return responseCode to body
        }
        throw IllegalStateException("Too many redirects while checking for updates")
    }

    private fun fetchFromApi(): LatestRelease {
        val (code, body) = openHttpsFollowingRedirects(
            initialUrl = LATEST_RELEASE_API_URL,
            acceptHeader = "application/vnd.github+json",
            extraHeaders = mapOf("X-GitHub-Api-Version" to "2022-11-28")
        )
        if (code !in 200..299 || body.isBlank()) {
            throw IllegalStateException("HTTP $code")
        }
        return parseReleaseJson(JSONObject(body))
            ?: throw IllegalStateException("Missing tag or APK asset in GitHub API payload")
    }

    internal fun parseReleaseJson(json: JSONObject): LatestRelease? {
        val tagName = json.optString("tag_name").trim()
        if (tagName.isBlank()) return null
        val releaseNotes = if (json.has("body") && !json.isNull("body")) json.optString("body") else null
        val publishedAt = if (json.has("published_at") && !json.isNull("published_at")) json.optString("published_at") else null
        val assets = json.optJSONArray("assets") ?: JSONArray()

        var exactApkUrl: String? = null
        var exactApkSize: Long? = null
        var fallbackApkUrl: String? = null
        var fallbackApkSize: Long? = null
        var sha256SumsUrl: String? = null

        val expectedAssetName = "Antigravity-Pulse-$tagName.apk"
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val assetName = asset.optString("name").trim()
            val downloadUrl = asset.optString("browser_download_url").trim()
            val size = asset.optLong("size").takeIf { it > 0 }
            if (downloadUrl.isBlank()) continue
            when {
                assetName.equals(expectedAssetName, ignoreCase = true) -> {
                    exactApkUrl = downloadUrl
                    exactApkSize = size
                }
                assetName.endsWith(".apk", ignoreCase = true) && fallbackApkUrl == null -> {
                    fallbackApkUrl = downloadUrl
                    fallbackApkSize = size
                }
                assetName.equals("SHA256SUMS.txt", ignoreCase = true) -> {
                    sha256SumsUrl = downloadUrl
                }
            }
        }
        val resolvedApkUrl = exactApkUrl ?: fallbackApkUrl ?: return null
        val resolvedApkSize = if (exactApkUrl != null) exactApkSize else fallbackApkSize
        return LatestRelease(
            tagName = tagName,
            releaseNotes = releaseNotes,
            publishedAt = publishedAt,
            apkAssetUrl = resolvedApkUrl,
            apkSizeBytes = resolvedApkSize,
            sha256SumsUrl = sha256SumsUrl
        )
    }

    private fun fetchFromLatestRedirect(): LatestRelease {
        val connection = URL(LATEST_RELEASE_WEB_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = CHECK_TIMEOUT_MS.toInt()
        connection.readTimeout = CHECK_TIMEOUT_MS.toInt()
        connection.instanceFollowRedirects = false
        connection.useCaches = false
        connection.setRequestProperty("Accept", "text/html,application/xhtml+xml")
        connection.setRequestProperty("Cache-Control", "no-cache")
        connection.setRequestProperty(
            "User-Agent",
            "AntigravityPulse/${BuildConfig.VERSION_NAME} (Android-AutoUpdate)"
        )
        val code = connection.responseCode
        val location = connection.getHeaderField("Location").orEmpty()
        connection.disconnect()

        if (code !in listOf(301, 302, 303, 307, 308) || location.isBlank()) {
            throw IllegalStateException("HTTP $code (no redirect target)")
        }
        val tagName = extractTagFromReleaseRedirectUrl(location)
            ?: throw IllegalStateException("Could not parse release tag from $location")
        return LatestRelease(
            tagName = tagName,
            releaseNotes = null,
            publishedAt = null,
            apkAssetUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$tagName/Antigravity-Pulse-$tagName.apk",
            apkSizeBytes = null,
            sha256SumsUrl = "https://github.com/$REPO_OWNER/$REPO_NAME/releases/download/$tagName/SHA256SUMS.txt",
            sha256 = null
        )
    }

    internal fun extractTagFromReleaseRedirectUrl(location: String): String? {
        val marker = "/releases/tag/"
        val idx = location.indexOf(marker)
        if (idx < 0) return null
        val rawTag = location.substring(idx + marker.length)
            .substringBefore('?')
            .substringBefore('#')
            .trim('/')
            .trim()
        return rawTag.takeIf { it.isNotBlank() && it.matches(Regex("^[vV]?[0-9]+(\\.[0-9]+)*([._-][a-zA-Z0-9]+)?$")) }
    }

    private fun fetchFromManifestUrl(url: String): LatestRelease {
        val (code, body) = openHttpsFollowingRedirects(
            initialUrl = url,
            acceptHeader = "application/json, text/plain, */*"
        )
        if (code !in 200..299 || body.isBlank()) {
            throw IllegalStateException("HTTP $code")
        }
        return parseManifestJson(JSONObject(body))
            ?: throw IllegalStateException("Invalid manifest JSON")
    }

    internal fun parseManifestJson(json: JSONObject): LatestRelease? {
        val tagName = json.optString("tagName").ifBlank { json.optString("tag_name") }.trim()
        val apkUrl = json.optString("apkUrl").ifBlank { json.optString("apkAssetUrl") }.trim()
        if (tagName.isBlank() || apkUrl.isBlank()) return null
        val sha256 = json.optString("sha256").trim().ifBlank { null }
        return LatestRelease(
            tagName = tagName,
            releaseNotes = json.optString("releaseNotes").ifBlank { null },
            publishedAt = json.optString("publishedAt").ifBlank { null },
            apkAssetUrl = apkUrl,
            apkSizeBytes = json.optLong("apkSizeBytes").takeIf { it > 0 },
            sha256SumsUrl = json.optString("sha256SumsUrl").trim().ifBlank { null },
            sha256 = sha256
        )
    }
}
