package com.example.data.sync

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.example.data.db.InventoryDao
import com.example.data.excel.XlsxManager
import com.example.model.InventoryItem
import com.example.model.TransactionRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class SyncStatus(
    val isLinked: Boolean = false,
    val fileName: String = "No Google Drive file linked",
    val fileUriString: String? = null,
    val lastSyncTime: String = "Never",
    val isSyncing: Boolean = false,
    val autoSyncEnabled: Boolean = true,
    val errorMessage: String? = null,
    val pendingChangesCount: Int = 0
)

class SyncRepository(
    private val context: Context,
    private val dao: InventoryDao
) {
    private val TAG = "SyncRepository"
    private val prefs = context.getSharedPreferences("inventory_sync_prefs", Context.MODE_PRIVATE)

    private val _syncStatus = MutableStateFlow(
        SyncStatus(
            isLinked = prefs.getString("linked_uri", null) != null,
            fileName = prefs.getString("linked_filename", "No Google Drive file linked") ?: "No Google Drive file linked",
            fileUriString = prefs.getString("linked_uri", null),
            lastSyncTime = prefs.getString("last_sync_time", "Never") ?: "Never",
            autoSyncEnabled = prefs.getBoolean("auto_sync", true)
        )
    )
    val syncStatus: StateFlow<SyncStatus> = _syncStatus.asStateFlow()

    val allItems: Flow<List<InventoryItem>> = dao.getAllItems()
    val allTransactions: Flow<List<TransactionRecord>> = dao.getAllTransactions()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        val count = dao.getItemCount()
        if (count == 0) {
            Log.d(TAG, "Database empty, preloading sample inventory...")
            dao.insertItems(XlsxManager.createSampleItems())
            dao.insertTransactions(XlsxManager.createSampleTransactions())
        }

        // Try syncing from linked URI if present
        val linkedUriStr = _syncStatus.value.fileUriString
        if (linkedUriStr != null) {
            try {
                val uri = Uri.parse(linkedUriStr)
                syncFromUri(uri)
            } catch (e: Exception) {
                Log.w(TAG, "Could not auto-sync on startup: ${e.message}")
            }
        }
    }

    suspend fun setLinkedFile(uri: Uri) = withContext(Dispatchers.IO) {
        val fileName = getFileName(uri) ?: "ITEM LIST.xlsx"
        prefs.edit()
            .putString("linked_uri", uri.toString())
            .putString("linked_filename", fileName)
            .apply()

        _syncStatus.value = _syncStatus.value.copy(
            isLinked = true,
            fileName = fileName,
            fileUriString = uri.toString(),
            errorMessage = null
        )

        // Read and sync spreadsheet content
        syncFromUri(uri)
    }

    suspend fun unlinkFile() = withContext(Dispatchers.IO) {
        prefs.edit().remove("linked_uri").remove("linked_filename").apply()
        _syncStatus.value = _syncStatus.value.copy(
            isLinked = false,
            fileName = "No Google Drive file linked",
            fileUriString = null
        )
    }

    fun setAutoSyncEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_sync", enabled).apply()
        _syncStatus.value = _syncStatus.value.copy(autoSyncEnabled = enabled)
    }

    suspend fun findItemByBarcode(query: String): InventoryItem? = withContext(Dispatchers.IO) {
        val clean = query.trim()
        if (clean.isBlank()) return@withContext null

        // 1. Exact match
        var found = dao.findItemByBarcode(clean)
        if (found != null) return@withContext found

        // 2. Strip leading zeros (e.g. 012345678901 vs 12345678901)
        val stripped = clean.trimStart('0')
        if (stripped.isNotEmpty() && stripped != clean) {
            found = dao.findItemByBarcode(stripped)
            if (found != null) return@withContext found
        }

        // 3. Fallback: search case-insensitive substring
        val all = dao.getAllItems().first()
        return@withContext all.firstOrNull { item ->
            item.upc.equals(clean, ignoreCase = true) ||
            item.alternateLookup.equals(clean, ignoreCase = true) ||
            (item.alternateLookup.isNotBlank() && clean.contains(item.alternateLookup, ignoreCase = true)) ||
            (item.upc.isNotBlank() && item.upc.trimStart('0') == stripped)
        }
    }

    suspend fun updateStockAndPrice(
        item: InventoryItem,
        newQty: Double,
        newPrice: Double,
        newReorderPoint: Double = item.reorderPoint,
        transactionType: String = "Stock Adjustment"
    ): Result<TransactionRecord> = withContext(Dispatchers.IO) {
        try {
            val oldQty = item.onHandQty
            val qtyChange = newQty - oldQty

            // 1. Update InventoryItem
            val updatedItem = item.copy(
                onHandQty = newQty,
                activePrice = newPrice,
                reorderPoint = newReorderPoint,
                lastUpdated = System.currentTimeMillis()
            )
            dao.updateItem(updatedItem)

            // 2. Create Transaction History Record
            // Required columns: UPC, Item Name, Item#, Unit of Measure, On-hand Qty,
            // Item Description, Department, Alternate Lookup, expiry, and date-time stamp.
            val txRecord = TransactionRecord(
                timestamp = TransactionRecord.currentTimestamp(),
                upc = item.upc,
                itemName = item.itemName,
                itemNumber = item.itemNumber,
                unitOfMeasure = item.unitOfMeasure,
                onHandQty = newQty,
                qtyChange = qtyChange,
                activePrice = newPrice,
                itemDescription = item.itemDescription,
                department = item.department,
                alternateLookup = item.alternateLookup,
                expiry = item.expiry,
                transactionType = transactionType,
                isSynced = false
            )
            dao.insertTransaction(txRecord)

            // 3. Auto sync to spreadsheet if enabled and linked
            if (_syncStatus.value.autoSyncEnabled && _syncStatus.value.fileUriString != null) {
                val uri = Uri.parse(_syncStatus.value.fileUriString)
                writeToUri(uri)
            } else {
                val currentPending = _syncStatus.value.pendingChangesCount + 1
                _syncStatus.value = _syncStatus.value.copy(pendingChangesCount = currentPending)
            }

            Result.success(txRecord)
        } catch (e: Exception) {
            Log.e(TAG, "Error updating stock and price", e)
            Result.failure(e)
        }
    }

    suspend fun addNewItem(item: InventoryItem): Result<Long> = withContext(Dispatchers.IO) {
        try {
            val id = dao.insertItem(item)
            val tx = TransactionRecord(
                timestamp = TransactionRecord.currentTimestamp(),
                upc = item.upc,
                itemName = item.itemName,
                itemNumber = item.itemNumber,
                unitOfMeasure = item.unitOfMeasure,
                onHandQty = item.onHandQty,
                qtyChange = item.onHandQty,
                activePrice = item.activePrice,
                itemDescription = item.itemDescription,
                department = item.department,
                alternateLookup = item.alternateLookup,
                expiry = item.expiry,
                transactionType = "Initial Stock / Add Item",
                isSynced = false
            )
            dao.insertTransaction(tx)

            if (_syncStatus.value.autoSyncEnabled && _syncStatus.value.fileUriString != null) {
                val uri = Uri.parse(_syncStatus.value.fileUriString)
                writeToUri(uri)
            }
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun syncFromUri(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        _syncStatus.value = _syncStatus.value.copy(isSyncing = true, errorMessage = null)
        try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                throw IllegalStateException("Cannot open input stream for $uri")
            }

            val workbookData = inputStream.use { XlsxManager.readWorkbook(it) }

            if (workbookData.items.isNotEmpty()) {
                dao.clearAllItems()
                dao.insertItems(workbookData.items)
            }

            if (workbookData.transactions.isNotEmpty()) {
                dao.clearAllTransactions()
                dao.insertTransactions(workbookData.transactions)
            }

            val nowFormatted = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date())
            prefs.edit().putString("last_sync_time", nowFormatted).apply()

            _syncStatus.value = _syncStatus.value.copy(
                isSyncing = false,
                lastSyncTime = nowFormatted,
                pendingChangesCount = 0,
                errorMessage = null
            )
            Log.d(TAG, "Successfully synced ${workbookData.items.size} items from spreadsheet.")
            Result.success(workbookData.items.size)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            _syncStatus.value = _syncStatus.value.copy(
                isSyncing = false,
                errorMessage = "Sync failed: ${e.localizedMessage ?: "Unknown error"}"
            )
            Result.failure(e)
        }
    }

    suspend fun writeToUri(uri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        _syncStatus.value = _syncStatus.value.copy(isSyncing = true, errorMessage = null)
        try {
            val items = dao.getAllItems().first()
            val transactions = dao.getAllTransactions().first()

            val outputStream: OutputStream? = context.contentResolver.openOutputStream(uri, "wt")
            if (outputStream == null) {
                throw IllegalStateException("Cannot open output stream for $uri")
            }

            outputStream.use { os ->
                XlsxManager.writeWorkbook(os, items, transactions)
            }

            dao.markAllTransactionsSynced()

            val nowFormatted = SimpleDateFormat("MMM d, HH:mm:ss", Locale.getDefault()).format(Date())
            prefs.edit().putString("last_sync_time", nowFormatted).apply()

            _syncStatus.value = _syncStatus.value.copy(
                isSyncing = false,
                lastSyncTime = nowFormatted,
                pendingChangesCount = 0,
                errorMessage = null
            )
            Log.d(TAG, "Successfully saved spreadsheet to $uri")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Save to spreadsheet failed", e)
            _syncStatus.value = _syncStatus.value.copy(
                isSyncing = false,
                errorMessage = "Write failed: ${e.localizedMessage ?: "Check file permissions"}"
            )
            Result.failure(e)
        }
    }

    suspend fun resetToSampleData(): Unit = withContext(Dispatchers.IO) {
        dao.clearAllItems()
        dao.clearAllTransactions()
        dao.insertItems(XlsxManager.createSampleItems())
        dao.insertTransactions(XlsxManager.createSampleTransactions())
        _syncStatus.value = _syncStatus.value.copy(pendingChangesCount = 0)
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        try {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val nameIdx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx >= 0) {
                        name = it.getString(nameIdx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error getting file name: ${e.message}")
        }
        return name ?: uri.lastPathSegment
    }
}
