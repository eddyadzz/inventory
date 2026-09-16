package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Represents a row in the "Transaction History" log sheet within ITEM LIST.xlsx.
 * Requirements: UPC, Item Name, Item#, Unit of Measure, On-hand Qty, Item Description,
 * Department, Alternate Lookup, expiry, and the date-time stamp.
 */
@Entity(tableName = "transaction_history")
data class TransactionRecord(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val timestamp: String = currentTimestamp(),
    val timestampEpoch: Long = System.currentTimeMillis(),
    val upc: String,
    val itemName: String,
    val itemNumber: String = "",
    val unitOfMeasure: String = "EA",
    val onHandQty: Double,
    val qtyChange: Double = 0.0,
    val activePrice: Double = 0.0,
    val itemDescription: String = "",
    val department: String = "",
    val alternateLookup: String = "",
    val expiry: String = "",
    val transactionType: String = "Stock Adjustment", // "Barcode Scan", "Manual Update", "Price Change", "Restock"
    val isSynced: Boolean = false
) {
    companion object {
        fun currentTimestamp(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date())
        }
    }
}
