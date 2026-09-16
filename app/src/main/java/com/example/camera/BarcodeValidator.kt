package com.example.camera

import android.util.Log

/**
 * BarcodeValidator provides mathematical checksum verification, format detection,
 * and multi-frame consensus filtering to prevent partial/phantom barcode reads.
 */
object BarcodeValidator {

    private const val TAG = "BarcodeValidator"

    // Multi-frame consensus tracking
    private var candidateBarcode: String? = null
    private var candidateTimestamp: Long = 0L
    private var candidateCount: Int = 0

    // Cooldown tracking for confirmed scans
    private var lastEmittedBarcode: String? = null
    private var lastEmittedTimestamp: Long = 0L
    private const val SAME_BARCODE_COOLDOWN_MS = 1500L
    private const val CONSENSUS_WINDOW_MS = 400L

    /**
     * Validates Modulo 10 check digit for standard 12-digit UPC-A barcodes.
     * Formula: (3 * (d1 + d3 + d5 + d7 + d9 + d11) + (d2 + d4 + d6 + d8 + d10) + d12) % 10 == 0
     */
    fun isValidUpcA(barcode: String): Boolean {
        if (barcode.length != 12 || !barcode.all { it.isDigit() }) return false
        val digits = barcode.map { it - '0' }
        val oddSum = digits[0] + digits[2] + digits[4] + digits[6] + digits[8] + digits[10]
        val evenSum = digits[1] + digits[3] + digits[5] + digits[7] + digits[9]
        val total = (oddSum * 3) + evenSum + digits[11]
        return (total % 10) == 0
    }

    /**
     * Validates Modulo 10 check digit for standard 13-digit EAN-13 barcodes.
     * Formula: (odd_positions + 3 * even_positions + check_digit) % 10 == 0
     */
    fun isValidEan13(barcode: String): Boolean {
        if (barcode.length != 13 || !barcode.all { it.isDigit() }) return false
        val digits = barcode.map { it - '0' }
        val oddSum = digits[0] + digits[2] + digits[4] + digits[6] + digits[8] + digits[10]
        val evenSum = (digits[1] + digits[3] + digits[5] + digits[7] + digits[9] + digits[11]) * 3
        val total = oddSum + evenSum + digits[12]
        return (total % 10) == 0
    }

    /**
     * Validates Modulo 10 check digit for 8-digit EAN-8 barcodes.
     */
    fun isValidEan8(barcode: String): Boolean {
        if (barcode.length != 8 || !barcode.all { it.isDigit() }) return false
        val digits = barcode.map { it - '0' }
        val oddSum = (digits[0] + digits[2] + digits[4] + digits[6]) * 3
        val evenSum = digits[1] + digits[3] + digits[5]
        val total = oddSum + evenSum + digits[7]
        return (total % 10) == 0
    }

    /**
     * Verifies if a detected barcode has high integrity.
     * Returns true if mathematical checksum validates or if format does not have a strict checksum.
     */
    fun isMathematicallyValid(barcode: String, formatName: String): Boolean {
        val clean = barcode.trim()
        if (clean.length < 3) return false

        return when {
            formatName.contains("UPC_A", ignoreCase = true) -> isValidUpcA(clean)
            formatName.contains("EAN_13", ignoreCase = true) -> isValidEan13(clean)
            formatName.contains("EAN_8", ignoreCase = true) -> isValidEan8(clean)
            formatName.contains("UPC_E", ignoreCase = true) -> clean.length in 6..8 && clean.all { it.isDigit() }
            clean.length == 12 && clean.all { it.isDigit() } -> isValidUpcA(clean)
            clean.length == 13 && clean.all { it.isDigit() } -> isValidEan13(clean)
            else -> true // Code 128, QR Code, Code 39, etc. rely on frame consensus
        }
    }

    /**
     * Evaluates a frame detection. Returns the barcode string ONLY if confirmed and ready to emit,
     * or null if it needs more consensus frames or is in cooldown.
     */
    @Synchronized
    fun processAndVerifyBarcode(rawText: String, formatName: String): String? {
        val clean = rawText.trim()
        if (clean.isBlank()) return null

        val now = System.currentTimeMillis()

        // Cooldown check for the exact same barcode to avoid firing dozens of times per second
        if (clean == lastEmittedBarcode && (now - lastEmittedTimestamp) < SAME_BARCODE_COOLDOWN_MS) {
            return null
        }

        // 1. If it's a UPC-A or EAN-13, strictly require valid checksum
        val isUpcOrEan = clean.length in 12..13 && clean.all { it.isDigit() }
        if (isUpcOrEan) {
            val validChecksum = if (clean.length == 12) isValidUpcA(clean) else isValidEan13(clean)
            if (!validChecksum) {
                Log.w(TAG, "Rejected partial/invalid checksum barcode: $clean ($formatName)")
                return null
            }
            // Valid UPC/EAN check digit is mathematically sound: accept immediately!
            lastEmittedBarcode = clean
            lastEmittedTimestamp = now
            candidateBarcode = null
            candidateCount = 0
            return clean
        }

        // 2. For other formats (Code 128, Code 39, QR, etc.), require 2-frame consensus
        if (clean == candidateBarcode && (now - candidateTimestamp) <= CONSENSUS_WINDOW_MS) {
            candidateCount++
            if (candidateCount >= 2) {
                // Consensus reached across multiple camera frames
                lastEmittedBarcode = clean
                lastEmittedTimestamp = now
                candidateBarcode = null
                candidateCount = 0
                return clean
            }
        } else {
            // First candidate frame
            candidateBarcode = clean
            candidateTimestamp = now
            candidateCount = 1
        }

        return null
    }

    /**
     * Resets the cooldown to allow re-scanning the same barcode immediately if desired.
     */
    @Synchronized
    fun resetCooldown() {
        lastEmittedBarcode = null
        lastEmittedTimestamp = 0L
        candidateBarcode = null
        candidateCount = 0
    }
}
