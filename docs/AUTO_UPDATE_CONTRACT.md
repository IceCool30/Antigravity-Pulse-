# Android in-app auto-update — implementation contract

Goal: v1.2.2+ can notify users of a new GitHub release, offer a manual check, download the APK with
progress, verify its SHA-256 against the release's `SHA256SUMS.txt`, and install it with one tap.

## Ownership (do not edit other files)

- `app/src/main/AndroidManifest.xml`
- `app/build.gradle.kts` (only the version bump described below)
- `app/src/main/res/xml/file_paths.xml` (new)
- `app/src/main/java/com/example/updater/**` (new package)
- `app/src/main/java/com/example/ui/screens/UpdateSettingsSection.kt` (new)
- `app/src/main/java/com/example/ui/screens/SettingsAdvancedSections.kt` (only to include the new section)
- `app/src/main/java/com/example/MainActivity.kt` (only a daily background auto-check + notification hook)
- `app/src/main/java/com/example/notifications/PulseNotificationManager.kt` (only if a small update-notification helper is needed)
- `app/src/test/java/com/example/updater/**` (new tests)

Everything else is read-only. Do not commit, push, tag, publish, or run `gh`. Do not run release
builds or sign anything — the orchestrator handles that. Never echo keystore passwords or `.env`
values.

## Required behavior

1. Version bump in `app/build.gradle.kts`: `versionCode = 5`, `versionName = "1.2.2"`.

2. `app/src/main/java/com/example/updater/`:
   - `UpdateChecker.kt`: `GET https://api.github.com/repos/IceCool30/Antigravity-Pulse-/releases/latest`
     with `HttpURLConnection` (already available), 15s timeouts, `Accept: application/vnd.github+json`,
     a User-Agent, and `org.json.JSONObject` (Android SDK). Parse `tag_name`, `body` (release notes),
     `published_at`, and assets. Find asset named `Antigravity-Pulse-v<tag>.apk` and `SHA256SUMS.txt`.
     Compare tags semantically with `BuildConfig.VERSION_NAME` (strip leading `v`, numeric parts).
     Expose pure `fun isNewer(latest: String, current: String): Boolean` for tests.
   - `UpdateDownloader.kt`: download the APK into `context.cacheDir/update/` using `HttpURLConnection`
     on `Dispatchers.IO`, stream to file, expose a `StateFlow` / callback with bytes and percent.
     Download `SHA256SUMS.txt`, parse the line for the APK filename, and verify the APK with
     `MessageDigest.getInstance("SHA-256")` **before** allowing install. Delete a mismatched file and
     report a clear error. Never trust an unverified APK.
   - `UpdateInstaller.kt`: build an `ACTION_VIEW` intent with
     `FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", apkFile)`,
     type `application/vnd.android.package-archive`, `FLAG_GRANT_READ_URI_PERMISSION` + `FLAG_ACTIVITY_NEW_TASK`.
     Check `PackageManager.canRequestPackageInstalls()` first; if false, launch
     `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` with the package URI so the user can grant it, then
     let the user retry. Handle Android 13+ notification permission gracefully.
   - Use only existing dependencies (`kotlinx.coroutines`, `androidx.core`, Android SDK). Do NOT add
     OkHttp/Retrofit/WorkManager or version-catalog entries.

3. Manifest:
   - Add `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />`.
   - Add a `FileProvider` with authorities `${applicationId}.fileprovider`, `exported=false`,
     `grantUriPermissions=true`, metadata `android.support.FILE_PROVIDER_PATHS` → `@xml/file_paths`.
   - `res/xml/file_paths.xml` must expose `<cache-path name="updates" path="update/" />`.

4. UI `UpdateSettingsSection.kt` (Compose Material3, match existing design/theme):
   - Current version (`BuildConfig.VERSION_NAME`), "Check for updates" button.
   - When available: version, release date, truncated release notes, APK size, "Download & install"
     button, linear progress with percent while downloading, status/error text with Retry.
   - Link to the release page via `ACTION_VIEW` (`https://github.com/IceCool30/Antigravity-Pulse-/releases/latest`).
   - Explain the one-time "Install unknown apps" permission in plain language.
   - Add the section to `SettingsAdvancedSections.kt` at a sensible position (e.g. near About/Advanced).

5. `MainActivity.kt`: on app start, if at least 24h since the last check (SharedPreferences), run a
   silent `UpdateChecker` check on `Dispatchers.IO`; if newer, call
   `PulseNotificationManager.showAppActivityNotification(...)` (only when background, via
   `isAppInForeground`) so the user is notified. Never show a blocking dialog on startup.

6. Tests in `app/src/test/java/com/example/updater/UpdateCheckerTest.kt`: pure unit tests for semantic
   version comparison (e.g. 1.2.1 → 1.2.2 true; 1.2.2 → 1.2.1 false; 1.10.0 > 1.9.9).

## Definition of done

- `./gradlew test` passes (existing tests + new).
- `./gradlew assembleDebug` succeeds (debug keystore exists).
- `git status` shows only files you own. No commits, no release commands.
- Final report: files changed, checks run, risks/deviations. State clearly that the first signed
  release with this feature must be v1.2.2 so existing v1.2.1 users can update in place.
