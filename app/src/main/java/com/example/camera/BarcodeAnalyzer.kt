package com.example.camera

import android.util.Log
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.NotFoundException
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

class BarcodeAnalyzer(
    private val onBarcodeDetected: (barcode: String, format: String) -> Unit
) : ImageAnalysis.Analyzer {

    private val TAG = "BarcodeAnalyzer"
    private var lastScanTimestamp = 0L
    private val scanThrottleMs = 1200L

    private val reader = MultiFormatReader().apply {
        val hints = mapOf(
            DecodeHintType.POSSIBLE_FORMATS to listOf(
                BarcodeFormat.UPC_A,
                BarcodeFormat.UPC_E,
                BarcodeFormat.EAN_13,
                BarcodeFormat.EAN_8,
                BarcodeFormat.CODE_128,
                BarcodeFormat.CODE_39,
                BarcodeFormat.CODE_93,
                BarcodeFormat.ITF,
                BarcodeFormat.QR_CODE
            ),
            DecodeHintType.TRY_HARDER to true
        )
        setHints(hints)
    }

    override fun analyze(imageProxy: ImageProxy) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastScanTimestamp < scanThrottleMs) {
            imageProxy.close()
            return
        }

        try {
            val plane = imageProxy.planes[0]
            val buffer = plane.buffer
            val rowStride = plane.rowStride
            val width = imageProxy.width
            val height = imageProxy.height

            val yData = ByteArray(width * height)
            val originalPosition = buffer.position()

            for (row in 0 until height) {
                buffer.position(originalPosition + row * rowStride)
                val length = minOf(width, buffer.remaining())
                buffer.get(yData, row * width, length)
            }

            val source = PlanarYUVLuminanceSource(
                yData,
                width,
                height,
                0,
                0,
                width,
                height,
                false
            )

            val bitmap = BinaryBitmap(HybridBinarizer(source))
            try {
                val result = reader.decodeWithState(bitmap)
                val barcodeText = result.text.trim()
                if (barcodeText.isNotEmpty()) {
                    lastScanTimestamp = currentTime
                    Log.d(TAG, "Barcode scanned: $barcodeText (${result.barcodeFormat})")
                    onBarcodeDetected(barcodeText, result.barcodeFormat.name)
                }
            } catch (e: NotFoundException) {
                // No barcode in frame, normal
            } finally {
                reader.reset()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Analysis frame error: ${e.message}")
        } finally {
            imageProxy.close()
        }
    }
}
