package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.OptIn
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.BuildConfig
import com.example.data.remote.RetainedWebViewManager
import com.example.ui.theme.CrimsonError
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalAppearanceSettings
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val QR_TAG = "QrPairingScanner"

data class ParsedPairingPayload(
    val hostName: String,
    val endpointUrl: String,
    val authToken: String
)

private const val MAX_QR_URL_LENGTH = 512
private const val MAX_QR_NAME_LENGTH = 80
private const val MAX_QR_TOKEN_LENGTH = 512

/**
 * Validates and normalizes a scanned endpoint URL.
 * Accepts only `https://`, `http://`, `wss://`, or `ws://` that pass [RetainedWebViewManager.isSafeHttpUrl],
 * rejecting dangerous schemes, cloud metadata IPs, obfuscated IP encodings, backslash authority confusion,
 * and cleartext downgrades of HTTPS-only cloud workspaces at the QR boundary.
 */
private fun sanitizeScannedEndpointUrl(rawUrl: String): String? {
    val clean = rawUrl.trim()
    if (clean.isEmpty() || clean.length > MAX_QR_URL_LENGTH) return null
    if (clean.any { it.isISOControl() || it.isWhitespace() }) return null
    return try {
        val uri = Uri.parse(clean)
        val scheme = uri.scheme?.lowercase() ?: return null
        val host = uri.host
        if (host.isNullOrBlank()) return null
        val normalized = when (scheme) {
            "https", "http" -> clean
            "wss" -> "https://" + clean.substring(6)
            "ws" -> "http://" + clean.substring(5)
            else -> return null
        }
        if (!RetainedWebViewManager.isSafeHttpUrl(normalized)) return null
        normalized
    } catch (_: Exception) {
        null
    }
}

/**
 * Parses QR code contents generated by Google Antigravity Remote Control, Tailscale, Cloudflare
 * tunnels, JSON pairing cards, or `antigravity://pair?url=...&token=...` deep links.
 */
fun parseAntigravityPairingPayload(rawValue: String): ParsedPairingPayload? {
    val clean = rawValue.trim()
    if (clean.isEmpty() || clean.length > 2048) return null

    // 1. JSON payload: {"url": "...", "token": "...", "name": "..."}
    if (clean.startsWith("{")) {
        return try {
            val json = JSONObject(clean)
            val rawUrl = json.optString("url")
                .ifBlank { json.optString("endpointUrl") }
                .ifBlank { json.optString("endpoint") }
                .trim()
            val safeUrl = sanitizeScannedEndpointUrl(rawUrl) ?: return null
            val token = json.optString("token")
                .ifBlank { json.optString("authToken") }
                .ifBlank { json.optString("pairingToken") }
                .trim()
                .take(MAX_QR_TOKEN_LENGTH)
            val name = json.optString("name")
                .ifBlank { json.optString("label") }
                .trim()
                .take(MAX_QR_NAME_LENGTH)
                .ifBlank { deriveHostLabelFromUrl(safeUrl) }
            ParsedPairingPayload(hostName = name, endpointUrl = safeUrl, authToken = token)
        } catch (_: Exception) {
            null
        }
    }

    // 2. Custom deep-link scheme: antigravity://pair?... or t3://pair?... or t3code://pair?...
    val lowerClean = clean.lowercase()
    if (lowerClean.startsWith("antigravity://") ||
        lowerClean.startsWith("t3://") ||
        lowerClean.startsWith("t3code://")
    ) {
        return try {
            val uri = Uri.parse(clean)
            val embeddedUrl = uri.getQueryParameter("url")
                ?: uri.getQueryParameter("endpoint")
                ?: return null
            val safeUrl = sanitizeScannedEndpointUrl(embeddedUrl) ?: return null
            val token = (uri.getQueryParameter("token")
                ?: uri.getQueryParameter("auth")
                ?: extractFragmentToken(safeUrl))
                .trim()
                .take(MAX_QR_TOKEN_LENGTH)
            val name = uri.getQueryParameter("name")
                ?.trim()
                ?.take(MAX_QR_NAME_LENGTH)
                ?.takeIf { it.isNotBlank() }
                ?: deriveHostLabelFromUrl(safeUrl)
            ParsedPairingPayload(
                hostName = name,
                endpointUrl = safeUrl,
                authToken = token
            )
        } catch (_: Exception) {
            null
        }
    }

    // 3. Direct URL: https://..., http://..., wss://..., ws://... (with optional ?token=... or #token=...)
    val safeUrl = sanitizeScannedEndpointUrl(clean) ?: return null
    return try {
        val uri = Uri.parse(safeUrl)
        val token = (uri.getQueryParameter("token")
            ?: uri.getQueryParameter("code")
            ?: uri.getQueryParameter("auth")
            ?: extractFragmentToken(safeUrl))
            .trim()
            .take(MAX_QR_TOKEN_LENGTH)
        ParsedPairingPayload(
            hostName = deriveHostLabelFromUrl(safeUrl),
            endpointUrl = safeUrl,
            authToken = token
        )
    } catch (_: Exception) {
        ParsedPairingPayload(
            hostName = "Antigravity Remote",
            endpointUrl = safeUrl,
            authToken = ""
        )
    }
}

private fun extractFragmentToken(url: String): String {
    return try {
        val fragment = Uri.parse(url).fragment.orEmpty()
        if (fragment.isBlank()) return ""
        val parsedFragment = Uri.parse("https://placeholder.local/?$fragment")
        (parsedFragment.getQueryParameter("token")
            ?: parsedFragment.getQueryParameter("code")
            ?: parsedFragment.getQueryParameter("auth")
            ?: "")
            .trim()
    } catch (_: Exception) {
        ""
    }
}

private fun deriveHostLabelFromUrl(url: String): String {
    return try {
        val host = Uri.parse(url).host.orEmpty()
        if (host.isNotBlank()) "Antigravity ($host)".take(MAX_QR_NAME_LENGTH) else "Antigravity Remote"
    } catch (_: Exception) {
        "Antigravity Remote"
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
fun QrPairingScannerDialog(
    onPairingScanned: (ParsedPairingPayload) -> Unit,
    onDismiss: () -> Unit,
    dialogTitle: String = "Scan Antigravity QR Code",
    dialogSubtitle: String = "Instant host URL & pairing token import",
    defaultPromptMessage: String = "Align the Google Antigravity Remote QR code inside the frame"
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    var torchEnabled by remember { mutableStateOf(false) }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }
    var boundCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    var statusMessage by remember { mutableStateOf(defaultPromptMessage) }
    var pendingScannedPayload by remember { mutableStateOf<ParsedPairingPayload?>(null) }
    val scanDelivered = remember { AtomicBoolean(false) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
        BarcodeScanning.getClient(options)
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                boundCameraProvider?.unbindAll()
            } catch (_: Exception) {
            }
            try {
                barcodeScanner.close()
            } catch (_: Exception) {
            }
            analyzerExecutor.shutdown()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("qr_pairing_scanner_dialog"),
            color = bg
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surface)
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = dialogTitle,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = fg
                            )
                            Text(
                                text = dialogSubtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryFg
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hasCameraPermission && activeCamera?.cameraInfo?.hasFlashUnit() == true) {
                            IconButton(
                                onClick = {
                                    torchEnabled = !torchEnabled
                                    activeCamera?.cameraControl?.enableTorch(torchEnabled)
                                },
                                modifier = Modifier.testTag("qr_torch_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (torchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Toggle Flashlight",
                                    tint = if (torchEnabled) accent else secondaryFg
                                )
                            }
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("qr_scanner_close_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close QR Scanner",
                                tint = fg
                            )
                        }
                    }
                }

                // CameraX Preview + ML Kit Barcode Analyzer
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (hasCameraPermission) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        boundCameraProvider = cameraProvider
                                        val preview = Preview.Builder().build().also {
                                            it.surfaceProvider = previewView.surfaceProvider
                                        }

                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()

                                        imageAnalysis.setAnalyzer(analyzerExecutor) { imageProxy ->
                                            val mediaImage = imageProxy.image
                                            if (mediaImage != null && !scanDelivered.get()) {
                                                val inputImage = InputImage.fromMediaImage(
                                                    mediaImage,
                                                    imageProxy.imageInfo.rotationDegrees
                                                )
                                                barcodeScanner.process(inputImage)
                                                    .addOnSuccessListener { barcodes ->
                                                        for (barcode in barcodes) {
                                                            val raw = barcode.rawValue ?: continue
                                                            val parsed = parseAntigravityPairingPayload(raw)
                                                            if (parsed != null && scanDelivered.compareAndSet(false, true)) {
                                                                ContextCompat.getMainExecutor(ctx).execute {
                                                                    pendingScannedPayload = parsed
                                                                }
                                                                break
                                                            } else if (parsed == null) {
                                                                statusMessage = "QR detected, waiting for valid https://, http://, or Antigravity pairing payload..."
                                                            }
                                                        }
                                                    }
                                                    .addOnCompleteListener {
                                                        imageProxy.close()
                                                    }
                                            } else {
                                                imageProxy.close()
                                            }
                                        }

                                        cameraProvider.unbindAll()
                                        activeCamera = cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (e: Exception) {
                                        if (BuildConfig.DEBUG) {
                                            Log.e(QR_TAG, "Failed to bind CameraX QR scanner", e)
                                        }
                                        statusMessage = "Camera preview unavailable on this device."
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        // Viewfinder Reticle Overlay
                        if (pendingScannedPayload == null) {
                            Box(
                                modifier = Modifier
                                    .size(250.dp)
                                    .clip(RoundedCornerShape(24.dp))
                                    .border(2.5.dp, EmeraldOnline, RoundedCornerShape(24.dp))
                            )

                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(24.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.Black.copy(alpha = 0.72f))
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = statusMessage,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        } else {
                            // Explicit preview & confirmation gate to prevent drive-by QR phishing/auto-connect
                            val scanned = pendingScannedPayload!!
                            val sanitizedDisplayUrl = remember(scanned.endpointUrl) {
                                RetainedWebViewManager.stripSensitiveUrlCredentials(scanned.endpointUrl)
                            }
                            val isHttpsEndpoint = remember(scanned.endpointUrl) {
                                scanned.endpointUrl.lowercase().startsWith("https://")
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.78f))
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(surface)
                                        .border(1.dp, appearance.borderColor, RoundedCornerShape(20.dp))
                                        .padding(20.dp)
                                        .testTag("qr_confirmation_card"),
                                    verticalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isHttpsEndpoint) Icons.Default.Lock else Icons.Default.Warning,
                                            contentDescription = null,
                                            tint = if (isHttpsEndpoint) appearance.successColor else appearance.errorColor,
                                            modifier = Modifier.size(22.dp)
                                        )
                                        Text(
                                            text = "Confirm Scanned Host",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = fg
                                        )
                                    }

                                    Text(
                                        text = "Verify this destination before saving and opening it in Antigravity Pulse:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = secondaryFg
                                    )

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(bg)
                                            .border(1.dp, appearance.borderColor, RoundedCornerShape(12.dp))
                                            .padding(12.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Text(
                                            text = scanned.hostName,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold,
                                            color = fg,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = sanitizedDisplayUrl,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = secondaryFg,
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (scanned.authToken.isNotBlank()) {
                                            Text(
                                                text = "Includes pairing token (hidden)",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = accent
                                            )
                                        }
                                    }

                                    if (!isHttpsEndpoint) {
                                        Text(
                                            text = "Warning: This QR code uses an unencrypted HTTP address. Only connect on a trusted private network.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = appearance.errorColor
                                        )
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                pendingScannedPayload = null
                                                scanDelivered.set(false)
                                                statusMessage = defaultPromptMessage
                                            },
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("qr_rescan_button")
                                        ) {
                                            Text("Scan Again", color = fg)
                                        }
                                        Button(
                                            onClick = { onPairingScanned(scanned) },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = accent,
                                                contentColor = appearance.onAccentColor
                                            ),
                                            modifier = Modifier
                                                .weight(1f)
                                                .testTag("qr_confirm_connect_button")
                                        ) {
                                            Text("Connect Host", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier.padding(28.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = accent,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = "Camera Access Needed for QR Pairing",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = fg,
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Grant camera access to scan your Google Antigravity desktop Remote QR code automatically. You can still paste your URL manually anytime.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = appearance.onAccentColor
                                ),
                                modifier = Modifier.testTag("request_camera_permission_button")
                            ) {
                                Text("Grant Camera Permission", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
