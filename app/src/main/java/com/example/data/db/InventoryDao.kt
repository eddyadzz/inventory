package com.example.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.model.InventoryItem
import com.example.model.TransactionRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface InventoryDao {

    @Query("SELECT * FROM inventory_items ORDER BY itemName ASC")
    fun getAllItems(): Flow<List<InventoryItem>>

    @Query("SELECT * FROM inventory_items WHERE id = :id LIMIT 1")
    suspend fun getItemById(id: Long): InventoryItem?

    @Query("""
        SELECT * FROM inventory_items 
        WHERE upc = :barcode 
           OR alternateLookup = :barcode 
        LIMIT 1
    """)
    suspend fun findItemByBarcode(barcode: String): InventoryItem?

    @Query("""
        SELECT * FROM inventory_items 
        WHERE upc LIKE '%' || :query || '%'
           OR alternateLookup LIKE '%' || :query || '%'
           OR itemName LIKE '%' || :query || '%'
           OR itemNumber LIKE '%' || :query || '%'
           OR department LIKE '%' || :query || '%'
        ORDER BY itemName ASC
    """)
    fun searchItems(query: String): Flow<List<InventoryItem>>

    @Query("SELECT COUNT(*) FROM inventory_items")
    suspend fun getItemCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: InventoryItem): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<InventoryItem>)

    @Update
    suspend fun updateItem(item: InventoryItem)

    @Query("DELETE FROM inventory_items WHERE id = :id")
    suspend fun deleteItemById(id: Long)

    @Query("DELETE FROM inventory_items")
    suspend fun clearAllItems()

    // Transaction History
    @Query("SELECT * FROM transaction_history ORDER BY id DESC")
    fun getAllTransactions(): Flow<List<TransactionRecord>>

    @Query("SELECT COUNT(*) FROM transaction_history")
    suspend fun getTransactionCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransaction(record: TransactionRecord): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransactions(records: List<TransactionRecord>)

    @Query("UPDATE transaction_history SET isSynced = 1 WHERE isSynced = 0")
    suspend fun markAllTransactionsSynced()

    @Query("DELETE FROM transaction_history")
    suspend fun clearAllTransactions()
}
