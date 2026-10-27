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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.unit.dp
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
 * https://family-remote-support.app/pair#inv=<id>&tok=<token>; a plain
 * "<id>-<token>" string (typed or read aloud) is accepted as a fallback.
 * Validation itself stays in parsePairingCode — this only extracts.
 */
fun parsePairingPayload(raw: String): String? {
    val text = raw.trim()
    Regex("inv=([A-Za-z0-9]+)&tok=([0-9a-fA-F]+)").find(text)?.let { match ->
        return "${match.groupValues[1]}-${match.groupValues[2]}"
    }
    if (Regex("^[A-Za-z0-9]{4,10}-[0-9a-fA-F]{10,}$").matches(text)) {
        return text
    }
    return null
}

/**
 * Phase 5 — full-screen QR scan dialog (helper side). CameraX preview with an
 * on-device ML Kit QR analyser; as soon as a valid pairing payload is
 * recognised, [onCode] fires once and the caller closes the dialog.
 *
 * Uses [LifecycleCameraController] (camera-view) instead of
 * ProcessCameraProvider: the controller's public API does not touch
 * Guava's ListenableFuture, which keeps the Kotlin classpath simple.
 */
@Composable
fun QrScanDialog(
    onCode: (String) -> Unit,
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
                text = "Point the camera at the pairing QR code",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
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
                            text = "The camera is needed to scan the QR code. " +
                                "You can also type the code manually instead.",
                            color = Color.White
                        )
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                            Text("Allow camera")
                        }
                    }
                }
            }
            Button(onClick = onDismiss) {
                Text("Close the camera")
            }
        }
    }
}

@OptIn(ExperimentalGetImage::class)
@Composable
private fun QrCameraPreview(onCode: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val executor = remember { Executors.newSingleThreadExecutor() }
    var handled by remember { mutableStateOf(false) }
    val controller = remember { LifecycleCameraController(context) }

    DisposableEffect(Unit) {
        onDispose {
            try {
                controller.unbind()
            } catch (_: Exception) {
                // already unbound
            }
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            controller.setCameraSelector(CameraSelector.DEFAULT_BACK_CAMERA)
            controller.bindToLifecycle(lifecycleOwner)
            previewView.controller = controller

            val scanner = BarcodeScanning.getClient(
                BarcodeScannerOptions.Builder()
                    .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                    .build()
            )
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
                            onCode(parsed)
                        }
                        .addOnCompleteListener { proxy.close() }
                }
            )
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}
