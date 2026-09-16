package com.example.ui

import android.app.Application
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.db.InventoryDatabase
import com.example.data.sync.SyncRepository
import com.example.data.sync.SyncStatus
import com.example.model.InventoryItem
import com.example.model.TransactionRecord
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed class UiEvent {
    data class ShowToast(val message: String) : UiEvent()
    data class BarcodeScannedSuccess(val barcode: String, val itemName: String) : UiEvent()
    data class LowStockAlert(val item: InventoryItem) : UiEvent()
}

enum class StockFilter {
    ALL, LOW_STOCK, EXPIRING_SOON
}

class InventoryViewModel(
    application: Application,
    private val repository: SyncRepository
) : AndroidViewModel(application) {

    private val TAG = "InventoryViewModel"

    val syncStatus: StateFlow<SyncStatus> = repository.syncStatus

    private val _eventFlow = MutableSharedFlow<UiEvent>()
    val eventFlow: SharedFlow<UiEvent> = _eventFlow.asSharedFlow()

    // Search and filters
    val searchQuery = MutableStateFlow("")
    val selectedDepartment = MutableStateFlow("All")
    val selectedFilter = MutableStateFlow(StockFilter.ALL)

    // Scanner state
    val isTorchOn = MutableStateFlow(false)
    val isContinuousMode = MutableStateFlow(false)
    val manualBarcodeInput = MutableStateFlow("")

    // Active item being viewed/edited (e.g. from camera scan or list click)
    val activeSelectedItem = MutableStateFlow<InventoryItem?>(null)
    val unknownBarcode = MutableStateFlow<String?>(null)

    // Filtered Items Flow
    val filteredItems: StateFlow<List<InventoryItem>> = combine(
        repository.allItems,
        searchQuery,
        selectedDepartment,
        selectedFilter
    ) { items, query, dept, filter ->
        items.filter { item ->
            val matchesQuery = query.isBlank() ||
                item.itemName.contains(query, ignoreCase = true) ||
                item.upc.contains(query, ignoreCase = true) ||
                item.alternateLookup.contains(query, ignoreCase = true) ||
                item.itemNumber.contains(query, ignoreCase = true) ||
                item.department.contains(query, ignoreCase = true)

            val matchesDept = dept == "All" || item.department.equals(dept, ignoreCase = true)

            val matchesFilter = when (filter) {
                StockFilter.ALL -> true
                StockFilter.LOW_STOCK -> item.isLowStock
                StockFilter.EXPIRING_SOON -> item.expiry.isNotBlank() && item.expiry != "N/A"
            }

            matchesQuery && matchesDept && matchesFilter
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allItems: StateFlow<List<InventoryItem>> = repository.allItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockItems: StateFlow<List<InventoryItem>> = repository.allItems
        .map { list -> list.filter { it.isLowStock } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val lowStockCount: StateFlow<Int> = repository.allItems
        .map { list -> list.count { it.isLowStock } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val departments: StateFlow<List<String>> = repository.allItems.combine(searchQuery) { items, _ ->
        val depts = items.map { it.department.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        listOf("All") + depts
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("All"))

    val transactions: StateFlow<List<TransactionRecord>> = repository.allTransactions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            repository.initialize()
        }
    }

    fun onBarcodeScanned(barcode: String, format: String = "") {
        viewModelScope.launch {
            triggerHapticFeedback()
            Log.d(TAG, "Looking up barcode: $barcode ($format)")

            val found = repository.findItemByBarcode(barcode)
            if (found != null) {
                activeSelectedItem.value = found
                unknownBarcode.value = null
                if (found.isLowStock) {
                    _eventFlow.emit(UiEvent.LowStockAlert(found))
                } else {
                    _eventFlow.emit(UiEvent.BarcodeScannedSuccess(barcode, found.itemName))
                }
            } else {
                unknownBarcode.value = barcode
                activeSelectedItem.value = null
                _eventFlow.emit(UiEvent.ShowToast("Item not found for barcode: $barcode"))
            }
        }
    }

    fun selectItem(item: InventoryItem) {
        activeSelectedItem.value = item
        unknownBarcode.value = null
    }

    fun dismissItemDetail() {
        activeSelectedItem.value = null
        unknownBarcode.value = null
    }

    fun quickAdjustStock(item: InventoryItem, delta: Double) {
        viewModelScope.launch {
            val newQty = maxOf(0.0, item.onHandQty + delta)
            val action = if (delta > 0) "Restock (+${delta.toInt()})" else "Stock Count (${delta.toInt()})"
            repository.updateStockAndPrice(
                item = item,
                newQty = newQty,
                newPrice = item.activePrice,
                newReorderPoint = item.reorderPoint,
                transactionType = action
            )
            triggerHapticFeedback()
        }
    }

    fun updateStockAndPrice(
        item: InventoryItem,
        newQty: Double,
        newPrice: Double,
        newReorderPoint: Double = item.reorderPoint,
        reason: String
    ) {
        viewModelScope.launch {
            val result = repository.updateStockAndPrice(
                item = item,
                newQty = newQty,
                newPrice = newPrice,
                newReorderPoint = newReorderPoint,
                transactionType = reason
            )
            result.onSuccess {
                activeSelectedItem.value = null
                val alertMsg = if (newReorderPoint > 0.0 && newQty <= newReorderPoint) {
                    "⚠️ LOW STOCK ALERT: ${item.itemName} (${newQty.toInt()}) reached Reorder Point (${newReorderPoint.toInt()})!"
                } else {
                    "Updated ${item.itemName} (Stock: $newQty, Price: MVR $newPrice)"
                }
                _eventFlow.emit(UiEvent.ShowToast(alertMsg))
            }.onFailure { err ->
                _eventFlow.emit(UiEvent.ShowToast("Error updating: ${err.message}"))
            }
        }
    }

    fun addNewItemFromBarcode(
        upc: String,
        name: String,
        itemNumber: String,
        unitOfMeasure: String,
        initialQty: Double,
        price: Double,
        reorderPoint: Double = 10.0,
        description: String,
        department: String,
        alternateLookup: String,
        expiry: String
    ) {
        viewModelScope.launch {
            val newItem = InventoryItem(
                upc = upc.trim(),
                itemName = name.trim(),
                itemNumber = itemNumber.trim(),
                unitOfMeasure = unitOfMeasure.trim().ifEmpty { "EA" },
                onHandQty = initialQty,
                activePrice = price,
                reorderPoint = reorderPoint,
                itemDescription = description.trim(),
                department = department.trim().ifEmpty { "General" },
                alternateLookup = alternateLookup.trim(),
                expiry = expiry.trim()
            )
            val res = repository.addNewItem(newItem)
            res.onSuccess {
                unknownBarcode.value = null
                activeSelectedItem.value = null
                _eventFlow.emit(UiEvent.ShowToast("Added new item: $name"))
            }.onFailure { err ->
                _eventFlow.emit(UiEvent.ShowToast("Failed to add item: ${err.message}"))
            }
        }
    }

    fun linkGoogleDriveFile(uri: Uri) {
        viewModelScope.launch {
            repository.setLinkedFile(uri)
            _eventFlow.emit(UiEvent.ShowToast("Linked ITEM LIST.xlsx with Google Drive!"))
        }
    }

    fun unlinkGoogleDriveFile() {
        viewModelScope.launch {
            repository.unlinkFile()
            _eventFlow.emit(UiEvent.ShowToast("Unlinked Google Drive file"))
        }
    }

    fun toggleAutoSync(enabled: Boolean) {
        repository.setAutoSyncEnabled(enabled)
    }

    fun syncNow() {
        viewModelScope.launch {
            val uriStr = syncStatus.value.fileUriString
            if (uriStr != null) {
                val uri = Uri.parse(uriStr)
                // First write local updates, then pull any changes
                repository.writeToUri(uri)
                repository.syncFromUri(uri)
                _eventFlow.emit(UiEvent.ShowToast("Synced with Google Drive spreadsheet!"))
            } else {
                _eventFlow.emit(UiEvent.ShowToast("No spreadsheet linked. Link a file first."))
            }
        }
    }

    fun exportToUri(uri: Uri) {
        viewModelScope.launch {
            repository.writeToUri(uri)
            _eventFlow.emit(UiEvent.ShowToast("Spreadsheet exported successfully!"))
        }
    }

    fun reloadSampleData() {
        viewModelScope.launch {
            repository.resetToSampleData()
            _eventFlow.emit(UiEvent.ShowToast("Reloaded sample inventory dataset"))
        }
    }

    private fun triggerHapticFeedback() {
        try {
            val app = getApplication<Application>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = app.getSystemService(VibratorManager::class.java)
                vibratorManager?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(45, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val vibrator = app.getSystemService(Vibrator::class.java)
                @Suppress("DEPRECATION")
                vibrator?.vibrate(45)
            }
        } catch (e: Exception) {
            // Ignore if vibration unsupported
        }
    }

    companion object {
        fun provideFactory(application: Application): ViewModelProvider.Factory {
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    val db = InventoryDatabase.getInstance(application)
                    val repo = SyncRepository(application, db.inventoryDao())
                    return InventoryViewModel(application, repo) as T
                }
            }
        }
    }
}
