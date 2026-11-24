package com.example.updater

import com.example.data.remote.RetainedWebViewManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UpdateCheckerTest {

    @Test
    fun `isNewer returns true when latest version is greater than current`() {
        assertTrue(UpdateChecker.isNewer(latest = "1.2.2", current = "1.2.1"))
    }

    @Test
    fun `isNewer returns false when latest version equals current`() {
        assertFalse(UpdateChecker.isNewer(latest = "1.2.2", current = "1.2.2"))
    }

    @Test
    fun `isNewer returns false when latest version is older than current`() {
        assertFalse(UpdateChecker.isNewer(latest = "1.2.1", current = "1.2.2"))
    }

    @Test
    fun `isNewer compares numeric segments without lexicographic ordering`() {
        assertTrue(UpdateChecker.isNewer(latest = "1.10.0", current = "1.9.9"))
        assertTrue(UpdateChecker.isNewer(latest = "2.0.0", current = "1.9.9"))
        assertFalse(UpdateChecker.isNewer(latest = "1.9.9", current = "1.10.0"))
    }

    @Test
    fun `isNewer strips leading v prefix before comparing`() {
        assertTrue(UpdateChecker.isNewer(latest = "v1.2.2", current = "1.2.1"))
        assertFalse(UpdateChecker.isNewer(latest = "v1.2.1", current = "1.2.2"))
        assertFalse(UpdateChecker.isNewer(latest = "v1.2.2", current = "v1.2.2"))
    }

    @Test
    fun `extractTagFromReleaseRedirectUrl extracts tag cleanly across redirect formats`() {
        assertEquals(
            "v1.2.5",
            UpdateChecker.extractTagFromReleaseRedirectUrl(
                "https://github.com/IceCool30/Antigravity-Pulse-/releases/tag/v1.2.5"
            )
        )
        assertEquals(
            "v1.2.5",
            UpdateChecker.extractTagFromReleaseRedirectUrl(
                "https://github.com/IceCool30/Antigravity-Pulse-/releases/tag/v1.2.5/?ref=latest#notes"
            )
        )
        assertNull(
            UpdateChecker.extractTagFromReleaseRedirectUrl(
                "https://github.com/IceCool30/Antigravity-Pulse-/releases"
            )
        )
    }

    @Test
    fun `isTrustedUpdateHost allows only HTTPS GitHub and jsDelivr hosts`() {
        assertTrue(UpdateChecker.isTrustedUpdateHost("https://api.github.com/repos/IceCool30/Antigravity-Pulse-/releases/latest"))
        assertTrue(UpdateChecker.isTrustedUpdateHost("https://github.com/IceCool30/Antigravity-Pulse-/releases/latest"))
        assertTrue(UpdateChecker.isTrustedUpdateHost("https://raw.githubusercontent.com/IceCool30/Antigravity-Pulse-/main/app/update-manifest.json"))
        assertTrue(UpdateChecker.isTrustedUpdateHost("https://objects.githubusercontent.com/github-production-release-asset-123"))
        assertTrue(UpdateChecker.isTrustedUpdateHost("https://release-assets.githubusercontent.com/asset.apk"))
        assertTrue(UpdateChecker.isTrustedUpdateHost("https://cdn.jsdelivr.net/gh/IceCool30/Antigravity-Pulse-@main/app/update-manifest.json"))

        assertFalse(UpdateChecker.isTrustedUpdateHost("http://github.com/IceCool30/Antigravity-Pulse-/releases/latest"))
        assertFalse(UpdateChecker.isTrustedUpdateHost("https://evil-github.com/release.apk"))
        assertFalse(UpdateChecker.isTrustedUpdateHost("https://example.com/Antigravity-Pulse.apk"))
    }

    @Test
    fun `parseReleaseJson prefers exact APK asset and falls back to any APK asset`() {
        val exactPayload = JSONObject().apply {
            put("tag_name", "v1.2.5")
            put("body", "Updater and scroll fixes")
            put("published_at", "2026-11-24T12:00:00Z")
            put(
                "assets",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("name", "Antigravity-Pulse-v1.2.5.apk")
                            put("browser_download_url", "https://github.com/IceCool30/Antigravity-Pulse-/releases/download/v1.2.5/Antigravity-Pulse-v1.2.5.apk")
                            put("size", 42_500_000L)
                        }
                    )
                    put(
                        JSONObject().apply {
                            put("name", "SHA256SUMS.txt")
                            put("browser_download_url", "https://github.com/IceCool30/Antigravity-Pulse-/releases/download/v1.2.5/SHA256SUMS.txt")
                            put("size", 96L)
                        }
                    )
                }
            )
        }
        val parsedExact = UpdateChecker.parseReleaseJson(exactPayload)
        assertNotNull(parsedExact)
        assertEquals("1.2.5", parsedExact?.versionName)
        assertEquals(42_500_000L, parsedExact?.apkSizeBytes)
        assertTrue(parsedExact?.apkAssetUrl?.endsWith("Antigravity-Pulse-v1.2.5.apk") == true)
        assertTrue(parsedExact?.sha256SumsUrl?.endsWith("SHA256SUMS.txt") == true)

        val fallbackPayload = JSONObject().apply {
            put("tag_name", "v1.2.6")
            put(
                "assets",
                JSONArray().apply {
                    put(
                        JSONObject().apply {
                            put("name", "app-release.apk")
                            put("browser_download_url", "https://github.com/IceCool30/Antigravity-Pulse-/releases/download/v1.2.6/app-release.apk")
                            put("size", 41_000_000L)
                        }
                    )
                }
            )
        }
        val parsedFallback = UpdateChecker.parseReleaseJson(fallbackPayload)
        assertNotNull(parsedFallback)
        assertTrue(parsedFallback?.apkAssetUrl?.endsWith("app-release.apk") == true)
    }

    @Test
    fun `parseManifestJson parses manifest fields accurately`() {
        val manifest = JSONObject().apply {
            put("tagName", "v1.2.5")
            put("apkUrl", "https://github.com/IceCool30/Antigravity-Pulse-/releases/download/v1.2.5/Antigravity-Pulse-v1.2.5.apk")
            put("apkSizeBytes", 42_123_456L)
            put("sha256", "a".repeat(64))
            put("releaseNotes", "Smooth scrolling across all workspaces")
            put("publishedAt", "2026-11-24T12:00:00Z")
        }
        val parsed = UpdateChecker.parseManifestJson(manifest)
        assertNotNull(parsed)
        assertEquals("1.2.5", parsed?.versionName)
        assertEquals(42_123_456L, parsed?.apkSizeBytes)
        assertEquals("a".repeat(64), parsed?.sha256)
    }

    @Test
    fun `parseSha256Sums handles standard, binary marker, and single-apk fallback entries`() {
        val hashA = "0123456789abcdef".repeat(4)
        val sumsText = "$hashA  *Antigravity-Pulse-v1.2.5.apk\n"
        assertEquals(hashA, UpdateDownloader.parseSha256Sums(sumsText, "Antigravity-Pulse-v1.2.5.apk"))
        assertEquals(hashA, UpdateDownloader.parseSha256Sums(sumsText, "Antigravity-Pulse-1.2.5.apk"))
        assertNull(UpdateDownloader.parseSha256Sums("not-a-valid-hash  Antigravity-Pulse-v1.2.5.apk", "Antigravity-Pulse-v1.2.5.apk"))
    }

    @Test
    fun `lag booster CSS avoids universal wildcard selector that stalls Blink scroll`() {
        assertFalse(RetainedWebViewManager.LAG_BOOSTER_CSS.contains("*"))
    }
}
