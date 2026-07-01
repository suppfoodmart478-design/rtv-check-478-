package com.store478.rtvcheck.scanner

import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * Full-screen camera preview that continuously analyzes frames for barcodes
 * using ML Kit (Google's on-device barcode scanning model).
 *
 * ML Kit handles all image format conversion internally (YUV_420_888 from
 * CameraX -> InputImage), which avoids the manual byte-buffer/row-stride bugs
 * that plague hand-rolled ZXing + ImageProxy pipelines across different
 * device camera implementations.
 *
 * Calls onBarcodeDetected once per successful detection of a NEW value
 * (de-duplicates consecutive identical scans so one barcode doesn't fire repeatedly).
 */
@OptIn(ExperimentalGetImage::class)
@Composable
fun BarcodeScannerView(
    modifier: Modifier = Modifier,
    onBarcodeDetected: (String) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val lastDetectedValue = remember { mutableStateOf<String?>(null) }

    val scannerOptions = remember {
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_QR_CODE
            )
            .build()
    }
    val scanner = remember { BarcodeScanning.getClient(scannerOptions) }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    processImageProxy(scanner, imageProxy) { value ->
                        if (value != lastDetectedValue.value) {
                            lastDetectedValue.value = value
                            onBarcodeDetected(value)
                        }
                    }
                }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
            scanner.close()
        }
    }
}

@ExperimentalGetImage
private fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onResult: (String) -> Unit
) {
    val mediaImage = imageProxy.image
    if (mediaImage == null) {
        imageProxy.close()
        return
    }

    val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            for (barcode in barcodes) {
                val value = barcode.rawValue
                if (!value.isNullOrBlank()) {
                    onResult(value)
                    break
                }
            }
        }
        .addOnFailureListener { e ->
            e.printStackTrace()
        }
        .addOnCompleteListener {
            imageProxy.close()
        }
}
