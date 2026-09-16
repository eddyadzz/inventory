package com.example.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents an item in the ITEM LIST.xlsx spreadsheet.
 */
@Entity(
    tableName = "inventory_items",
    indices = [
        Index(value = ["upc"]),
        Index(value = ["alternateLookup"])
    ]
)
data class InventoryItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val upc: String,
    val itemName: String,
    val itemNumber: String = "",
    val unitOfMeasure: String = "EA",
    val onHandQty: Double = 0.0,
    val activePrice: Double = 0.0,
    val itemDescription: String = "",
    val department: String = "General",
    val alternateLookup: String = "",
    val expiry: String = "",
    val reorderPoint: Double = 10.0,
    val lastUpdated: Long = System.currentTimeMillis()
) {
    /**
     * Item is considered low stock if current on-hand quantity has fallen to or below the Reorder Point.
     */
    val isLowStock: Boolean
        get() = reorderPoint > 0.0 && onHandQty <= reorderPoint
}
