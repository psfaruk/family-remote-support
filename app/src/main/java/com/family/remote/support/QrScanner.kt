package com.family.remote.support

import android.Manifest
import android.content.pm.PackageManager
import androidx.annotation.OptIn
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Extract the pairing code from a scanned payload. The QR we generate encodes
 * https://family-remote-support.app/pair#inv=<id>&tok=<token>&k=fam|gst — a
 * plain "<id>-<token>" string (typed or read aloud) is accepted as a fallback.
 * Validation itself stays in parsePairingCode — this only extracts.
 * The trailing &k= marker (Phase 12) is ignored here on purpose.
 */
fun parsePairingPayload(raw: String): String? {
    val text = raw.trim()
    Regex("inv=([A-Za-z0-9]+)&tok=([0-9a-fA-F]+)").find(text)?.let { match ->
        return "${match.groupValues[1]}-${match.groupValues[2]}"
    }
    // v0.25.0 (Phase 18.6): up to 16 id chars — current codes carry 12 (see
    // INVITE_ID_LENGTH), legacy codes 6; strict length validation stays in
    // parsePairingCode, this only extracts a typed/manual candidate.
    if (Regex("^[A-Za-z0-9]{4,16}-[0-9a-fA-F]{10,}$").matches(text)) {
        return text
    }
    return null
}

/**
 * Phase 12 — read the ROLE marker out of a scanned QR payload:
 *   …&k=fam → REL_KIND_FAMILY (permanent, family only)
 *   …&k=gst → REL_KIND_GUEST  (temporary 2-hour window)
 * Returns null for legacy codes (no marker) or plain typed strings.
 * The hint only drives the PRE-REDEEM preview sheet — the redemption
 * transaction re-reads the authoritative kind from the Firestore
 * invitation document, so a doctored marker cannot change the outcome.
 */
fun parsePairingKindHint(raw: String): String? =
    Regex("[?&]k=(fam|gst)\\b").find(raw.trim())?.let { match ->
        if (match.groupValues[1] == "gst") REL_KIND_GUEST else REL_KIND_FAMILY
    }

/**
 * Phase 5/12 — full-screen QR scan dialog (helper side). CameraX preview with
 * an on-device ML Kit QR analyser; as soon as a valid pairing payload is
 * recognised, [onCode] fires once with (code, kindHint) and the caller closes
 * the dialog. The viewfinder shows a rounded reticle and a small legend that
 * explains the center badge printed inside every QR we generate:
 * 👪 = Family (permanent) · ⏳ = Guest (2-hour window).
 *
 * Uses [LifecycleCameraController] (camera-view) instead of
 * ProcessCameraProvider: the controller's public API does not touch
 * Guava's ListenableFuture, which keeps the Kotlin classpath simple.
 */
@Composable
fun QrScanDialog(
    onCode: (code: String, kindHint: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.qr_scan_title),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.qr_scan_hint),
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                if (hasPermission) {
                    QrCameraPreview(onCode = onCode)
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.qr_scan_permission_text),
                            color = Color.White
                        )
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text(stringResource(R.string.qr_scan_permission_btn))
                        }
                    }
                }
            }
            // Phase 12 — the legend that makes the two center badges readable
            // BEFORE scanning: color + glyph + word (never color alone).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QrBadgeLegendChip(badge = QrBadge.FAMILY)
                QrBadgeLegendChip(badge = QrBadge.GUEST)
            }
            Button(onClick = onDismiss) {
                Text(stringResource(R.string.qr_scan_close_btn))
            }
        }
    }
}

/** One legend chip under the viewfinder: glyph + role + duration, in words. */
@Composable
private fun QrBadgeLegendChip(badge: QrBadge) {
    // v0.27.0 (Phase 19.1): label from localized resources (was labelBn).
    val label = stringResource(
        if (badge == QrBadge.FAMILY) R.string.qr_badge_family else R.string.qr_badge_guest
    )
    Text(
        text = "${badge.glyph} $label",
        color = Color.White,
        fontSize = 13.sp,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun QrCameraPreview(onCode: (code: String, kindHint: String?) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var handled by remember { mutableStateOf(false) }
    val controller = remember { LifecycleCameraController(context) }
    // v0.21.0 (audit): the ML Kit scanner handle is now remember-scoped and
    // CLOSEd on dispose — each dialog open used to leak a new native scanner
    // handle (only the executor was shut down), which adds up on low-end
    // family phones that scan repeatedly.
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
        )
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                controller.unbind()
            } catch (_: Exception) {
                // already unbound
            }
            try {
                scanner.close()
            } catch (_: Exception) {
                // already closed
            }
            executor.shutdown()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                controller.setCameraSelector(CameraSelector.DEFAULT_BACK_CAMERA)
                controller.bindToLifecycle(lifecycleOwner)
                previewView.controller = controller

                controller.setImageAnalysisAnalyzer(
                    executor,
                    ImageAnalysis.Analyzer { proxy: ImageProxy ->
                        if (handled) {
                            proxy.close()
                            return@Analyzer
                        }
                        val mediaImage = proxy.image
                        if (mediaImage == null) {
                            proxy.close()
                            return@Analyzer
                        }
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            proxy.imageInfo.rotationDegrees
                        )
                        scanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                if (handled) return@addOnSuccessListener
                                val raw = barcodes.firstOrNull()?.rawValue
                                    ?: return@addOnSuccessListener
                                val parsed = parsePairingPayload(raw)
                                    ?: return@addOnSuccessListener
                                handled = true
                                onCode(parsed, parsePairingKindHint(raw))
                            }
                            .addOnCompleteListener { proxy.close() }
                    }
                )
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )
        // Phase 12 — viewfinder reticle: a rounded frame with a see-through
        // center so the code's middle badge stays visible while aiming.
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(260.dp)
                .border(
                    width = 3.dp,
                    color = Color.White.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(28.dp)
                )
        )
    }
}
