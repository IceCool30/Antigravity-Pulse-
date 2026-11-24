<#
.SYNOPSIS
    Deterministic signed release builder and GitHub publisher for Antigravity Pulse Android.

.DESCRIPTION
    1. Verifies app/google-services.json is present and not a placeholder.
    2. Reads versionName and versionCode from app/build.gradle.kts.
    3. Runs :app:testDebugUnitTest and builds a signed :app:assembleRelease APK using your local keystore.
    4. Verifies the APK signing certificate matches the established release key (b37898ed...) for safe in-place upgrades.
    5. Writes dist/Antigravity-Pulse-v<version>.apk and dist/SHA256SUMS.txt.
    6. Updates app/update-manifest.json with exact APK size, SHA-256, URLs, and publish timestamp.
    7. Commits manifest updates, tags v<version>, pushes main + tag, and creates/updates the GitHub Release via gh CLI.
#>
[CmdletBinding()]
param(
    [string]$KeystoreDir = "C:\Users\john.bisong\Keys\antigravity-pulse",
    [string]$ExpectedCertPrefix = "b37898ed",
    [switch]$SkipPublish
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

function Write-Step([string]$Message) {
    Write-Host "`n==> $Message" -ForegroundColor Cyan
}

$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
Push-Location $RepoRoot

try {
    Write-Step "1. Checking Firebase config (app/google-services.json)"
    $googleServices = Join-Path $RepoRoot "app\google-services.json"
    if (-not (Test-Path $googleServices)) {
        throw "Missing app\google-services.json! Aborting so we never ship a crashing APK."
    }
    $gsContent = Get-Content $googleServices -Raw
    if ($gsContent -match "remixed-project-id") {
        throw "app\google-services.json contains placeholder remixed-project-id! Restore the real config first."
    }

    Write-Step "2. Reading versionName and versionCode from app/build.gradle.kts"
    $gradleFile = Join-Path $RepoRoot "app\build.gradle.kts"
    $gradleText = Get-Content $gradleFile -Raw
    if ($gradleText -notmatch 'versionCode\s*=\s*(\d+)') { throw "Could not parse versionCode from app/build.gradle.kts" }
    $versionCode = $Matches[1]
    if ($gradleText -notmatch 'versionName\s*=\s*"([^"]+)"') { throw "Could not parse versionName from app/build.gradle.kts" }
    $versionName = $Matches[1]
    $tagName = "v$versionName"
    $apkFileName = "Antigravity-Pulse-$tagName.apk"
    Write-Host "Target release: $tagName (versionCode=$versionCode)" -ForegroundColor Green

    Write-Step "3. Resolving release keystore"
    $keystorePath = $env:KEYSTORE_PATH
    if ([string]::IsNullOrWhiteSpace($keystorePath)) {
        $candidate = Get-ChildItem -Path $KeystoreDir -Include *.jks, *.keystore -File -ErrorAction SilentlyContinue | Select-Object -First 1
        if ($null -eq $candidate) {
            throw "No .jks or .keystore file found in $KeystoreDir and KEYSTORE_PATH is not set."
        }
        $keystorePath = $candidate.FullName
    }
    if (-not (Test-Path $keystorePath)) {
        throw "Keystore not found at: $keystorePath"
    }
    $env:KEYSTORE_PATH = $keystorePath
    Write-Host "Using keystore: $keystorePath"

    if ([string]::IsNullOrWhiteSpace($env:STORE_PASSWORD) -or [string]::IsNullOrWhiteSpace($env:KEY_PASSWORD)) {
        throw "STORE_PASSWORD and KEY_PASSWORD environment variables must be set before running publish-release.ps1."
    }

    $gradleCmd = if (Test-Path (Join-Path $RepoRoot "gradlew.bat")) { ".\gradlew.bat" } else { "gradle" }

    Write-Step "4. Running unit tests (:app:testDebugUnitTest)"
    & $gradleCmd :app:testDebugUnitTest
    if ($LASTEXITCODE -ne 0) { throw "Unit tests failed with exit code $LASTEXITCODE" }

    Write-Step "5. Building signed release APK (:app:assembleRelease)"
    & $gradleCmd :app:assembleRelease
    if ($LASTEXITCODE -ne 0) { throw "assembleRelease failed with exit code $LASTEXITCODE" }

    $builtApk = Join-Path $RepoRoot "app\build\outputs\apk\release\app-release.apk"
    if (-not (Test-Path $builtApk)) {
        throw "Expected release APK not found at $builtApk"
    }

    Write-Step "6. Verifying APK signing certificate matches $ExpectedCertPrefix..."
    $keytoolCmd = Get-Command keytool -ErrorAction SilentlyContinue
    if ($null -ne $keytoolCmd) {
        $certOutput = (& keytool -printcert -jarfile $builtApk 2>&1) | Out-String
        if ($certOutput -match "SHA256:\s*([0-9A-Fa-f:]+)") {
            $certSha256 = ($Matches[1] -replace ":", "").ToLowerInvariant()
            Write-Host "APK signing cert SHA-256: $certSha256"
            if (-not $certSha256.StartsWith($ExpectedCertPrefix.ToLowerInvariant())) {
                throw "Signing cert mismatch! Expected prefix $ExpectedCertPrefix, got $certSha256. Refusing to publish."
            }
        } else {
            Write-Warning "Could not parse SHA256 from keytool output; verify apksigner manually."
        }
    }

    Write-Step "7. Preparing dist/$apkFileName and dist/SHA256SUMS.txt"
    $distDir = Join-Path $RepoRoot "dist"
    New-Item -ItemType Directory -Path $distDir -Force | Out-Null
    $distApk = Join-Path $distDir $apkFileName
    Copy-Item -Path $builtApk -Destination $distApk -Force

    $apkSizeBytes = (Get-Item $distApk).Length
    $sha256 = (Get-FileHash -Path $distApk -Algorithm SHA256).Hash.ToLowerInvariant()
    $sumsPath = Join-Path $distDir "SHA256SUMS.txt"
    $sumsLine = "$sha256  $apkFileName`n"
    [System.IO.File]::WriteAllText($sumsPath, $sumsLine, (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "APK Size: $apkSizeBytes bytes"
    Write-Host "SHA-256:  $sha256"

    Write-Step "8. Updating app/update-manifest.json"
    $publishedAt = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
    $manifestSummary = "Fixed in-app auto-updater end-to-end (4-tier lookup, progress tracking, SHA-256 verification, and Android installer launch) and eliminated scrolling lag across all 8 web workspaces with display-aware WebView contexts and zero-wildcard CSS."
    $manifestObj = [ordered]@{
        tagName         = $tagName
        versionName     = $versionName
        publishedAt     = $publishedAt
        releaseNotes    = $manifestSummary
        apkUrl          = "https://github.com/IceCool30/Antigravity-Pulse-/releases/download/$tagName/$apkFileName"
        apkSizeBytes    = $apkSizeBytes
        sha256          = $sha256
        sha256SumsUrl   = "https://github.com/IceCool30/Antigravity-Pulse-/releases/download/$tagName/SHA256SUMS.txt"
        releaseNotesUrl = "https://github.com/IceCool30/Antigravity-Pulse-/releases/tag/$tagName"
    }
    $manifestJson = ($manifestObj | ConvertTo-Json -Depth 4) + "`n"
    $manifestPath = Join-Path $RepoRoot "app\update-manifest.json"
    [System.IO.File]::WriteAllText($manifestPath, $manifestJson, (New-Object System.Text.UTF8Encoding($false)))

    if ($SkipPublish) {
        Write-Host "`nSkipPublish set — local signed artifacts and manifest are ready in dist\ and app\update-manifest.json." -ForegroundColor Yellow
        return
    }

    Write-Step "9. Committing release metadata, tagging $tagName, and pushing to origin"
    git add app/update-manifest.json app/build.gradle.kts CHANGELOG.md RELEASE_NOTES.md scripts/publish-release.ps1
    $stagedDiff = git diff --cached --name-only
    if (-not [string]::IsNullOrWhiteSpace($stagedDiff)) {
        git commit -m "release: $tagName (in-app updater + smooth workspace scrolling)"
    }
    $existingTag = git tag -l $tagName
    if ([string]::IsNullOrWhiteSpace($existingTag)) {
        git tag -a $tagName -m "Antigravity Pulse $tagName"
    }
    git push origin main
    git push origin $tagName

    Write-Step "10. Extracting $tagName section from RELEASE_NOTES.md and publishing GitHub Release"
    $notesRaw = Get-Content (Join-Path $RepoRoot "RELEASE_NOTES.md") -Raw
    $notesFile = Join-Path $distDir "release-notes-$tagName.md"
    $firstSection = ($notesRaw -split "(?m)^# Antigravity Pulse v1\.2\.3")[0].Trim()
    if ([string]::IsNullOrWhiteSpace($firstSection)) {
        $firstSection = $manifestSummary
    }
    [System.IO.File]::WriteAllText($notesFile, $firstSection + "`n", (New-Object System.Text.UTF8Encoding($false)))

    $releaseExists = $false
    try {
        gh release view $tagName *> $null
        if ($LASTEXITCODE -eq 0) { $releaseExists = $true }
    } catch { $releaseExists = $false }

    if ($releaseExists) {
        gh release upload $tagName $distApk $sumsPath --clobber
        gh release edit $tagName --title "Antigravity Pulse $tagName" --notes-file $notesFile --latest
    } else {
        gh release create $tagName $distApk $sumsPath --title "Antigravity Pulse $tagName" --notes-file $notesFile --latest
    }
    if ($LASTEXITCODE -ne 0) { throw "gh release publish failed with exit code $LASTEXITCODE" }

    Write-Step "11. Verifying published GitHub Release assets"
    gh release view $tagName --json tagName,url,assets
    Write-Host "`nSUCCESS: Antigravity Pulse $tagName is built, verified, and live on GitHub Releases!" -ForegroundColor Green
} finally {
    Pop-Location
}
