package com.store478.rtvcheck.scanner

import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
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
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import java.util.concurrent.Executors

/**
 * Full-screen camera preview that continuously analyzes frames for barcodes.
 * Calls onBarcodeDetected once per successful decode of a NEW value
 * (de-duplicates consecutive identical scans so one barcode doesn't fire repeatedly).
 */
@Composable
fun BarcodeScannerView(
    modifier: Modifier = Modifier,
    onBarcodeDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val reader = remember {
        MultiFormatReader().apply {
            setHints(
                mapOf(
                    DecodeHintType.POSSIBLE_FORMATS to listOf(
                        BarcodeFormat.EAN_13,
                        BarcodeFormat.EAN_8,
                        BarcodeFormat.UPC_A,
                        BarcodeFormat.UPC_E,
                        BarcodeFormat.CODE_128,
                        BarcodeFormat.CODE_39,
                        BarcodeFormat.QR_CODE
                    ),
                    DecodeHintType.TRY_HARDER to true
                )
            )
        }
    }
    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    val lastDetectedValue = remember { mutableStateOf<String?>(null) }

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
                    .setTargetResolution(Size(1280, 720))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    try {
                        val plane = imageProxy.planes[0]
                        val buffer = plane.buffer
                        val rowStride = plane.rowStride
                        val width = imageProxy.width
                        val height = imageProxy.height

                        val bytes: ByteArray
                        if (rowStride == width) {
                            // No padding, can copy directly.
                            bytes = ByteArray(buffer.remaining())
                            buffer.get(bytes)
                        } else {
                            // Row stride has padding beyond actual width; strip it out
                            // row by row so ZXing gets a tightly packed luminance buffer.
                            bytes = ByteArray(width * height)
                            val rowBytes = ByteArray(rowStride)
                            for (row in 0 until height) {
                                buffer.position(row * rowStride)
                                buffer.get(rowBytes, 0, minOf(rowStride, buffer.remaining()))
                                System.arraycopy(rowBytes, 0, bytes, row * width, width)
                            }
                        }

                        val source = PlanarYUVLuminanceSource(
                            bytes,
                            width,
                            height,
                            0, 0,
                            width,
                            height,
                            false
                        )
                        val bitmap = BinaryBitmap(HybridBinarizer(source))

                        try {
                            val result = reader.decode(bitmap)
                            val text = result.text
                            if (text != null && text != lastDetectedValue.value) {
                                lastDetectedValue.value = text
                                onBarcodeDetected(text)
                            }
                        } catch (e: NotFoundException) {
                            // No barcode in this frame; expected most of the time.
                        } finally {
                            reader.reset()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        imageProxy.close()
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
        }
    }
}
