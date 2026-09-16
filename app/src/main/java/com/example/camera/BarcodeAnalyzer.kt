package com.example.camera

import android.graphics.Rect
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

/**
 * High-performance, rotation-aware BarcodeAnalyzer powered by Google ML Kit.
 * Eliminates ZXing scanline clipping, partial reads, and orientation sensitivity.
 */
class BarcodeAnalyzer(
    private val onBarcodeDetected: (barcode: String, format: String) -> Unit
) : ImageAnalysis.Analyzer {

    private val TAG = "BarcodeAnalyzer"

    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(
            Barcode.FORMAT_UPC_A,
            Barcode.FORMAT_UPC_E,
            Barcode.FORMAT_EAN_13,
            Barcode.FORMAT_EAN_8,
            Barcode.FORMAT_CODE_128,
            Barcode.FORMAT_CODE_39,
            Barcode.FORMAT_CODE_93,
            Barcode.FORMAT_ITF,
            Barcode.FORMAT_QR_CODE
        )
        .build()

    private val scanner = BarcodeScanning.getClient(options)

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        val image = InputImage.fromMediaImage(mediaImage, rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    val frameWidth = image.width
                    val frameHeight = image.height

                    // Central region of interest (ROI) filter: ignore stray barcodes at distant image periphery
                    val roi = Rect(
                        (frameWidth * 0.08f).toInt(),
                        (frameHeight * 0.12f).toInt(),
                        (frameWidth * 0.92f).toInt(),
                        (frameHeight * 0.88f).toInt()
                    )

                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue ?: barcode.displayValue ?: continue
                        val box = barcode.boundingBox

                        // If bounding box is available, verify it intersects the active scan zone
                        if (box != null && !Rect.intersects(roi, box)) {
                            continue
                        }

                        val formatName = getFormatName(barcode.format)
                        val confirmedBarcode = BarcodeValidator.processAndVerifyBarcode(rawValue, formatName)

                        if (confirmedBarcode != null) {
                            Log.d(TAG, "Verified Barcode: $confirmedBarcode ($formatName)")
                            onBarcodeDetected(confirmedBarcode, formatName)
                            break
                        }
                    }
                }
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "ML Kit barcode scan failure: ${e.message}")
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun getFormatName(format: Int): String {
        return when (format) {
            Barcode.FORMAT_UPC_A -> "UPC_A"
            Barcode.FORMAT_UPC_E -> "UPC_E"
            Barcode.FORMAT_EAN_13 -> "EAN_13"
            Barcode.FORMAT_EAN_8 -> "EAN_8"
            Barcode.FORMAT_CODE_128 -> "CODE_128"
            Barcode.FORMAT_CODE_39 -> "CODE_39"
            Barcode.FORMAT_CODE_93 -> "CODE_93"
            Barcode.FORMAT_ITF -> "ITF"
            Barcode.FORMAT_QR_CODE -> "QR_CODE"
            else -> "BARCODE_$format"
        }
    }
}
