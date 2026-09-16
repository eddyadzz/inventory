package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.db.InventoryDatabase
import com.example.data.excel.XlsxManager
import com.example.model.InventoryItem
import com.example.model.TransactionRecord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class InventorySyncTest {

    private lateinit var db: InventoryDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, InventoryDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun testBarcodeAndAlternateLookup() = runBlocking {
        val dao = db.inventoryDao()
        val item1 = InventoryItem(
            upc = "012000001291",
            itemName = "Pepsi 12-Pack Cans",
            itemNumber = "ITM-1001",
            unitOfMeasure = "PK",
            onHandQty = 48.0,
            activePrice = 6.99,
            itemDescription = "12 fl oz cans, 12 pack",
            department = "Beverages",
            alternateLookup = "PEP-12PK-CAN",
            expiry = "2026-11-30"
        )
        dao.insertItem(item1)

        // Lookup by primary UPC
        val byUpc = dao.findItemByBarcode("012000001291")
        assertNotNull(byUpc)
        assertEquals("Pepsi 12-Pack Cans", byUpc?.itemName)

        // Lookup by Alternate Lookup
        val byAlt = dao.findItemByBarcode("PEP-12PK-CAN")
        assertNotNull(byAlt)
        assertEquals("Pepsi 12-Pack Cans", byAlt?.itemName)
    }

    @Test
    fun testTransactionLogging() = runBlocking {
        val dao = db.inventoryDao()
        val tx = TransactionRecord(
            timestamp = "2026-09-15 12:30:00",
            upc = "012000001291",
            itemName = "Pepsi 12-Pack Cans",
            itemNumber = "ITM-1001",
            unitOfMeasure = "PK",
            onHandQty = 58.0,
            qtyChange = 10.0,
            activePrice = 7.49,
            itemDescription = "12 fl oz cans, 12 pack",
            department = "Beverages",
            alternateLookup = "PEP-12PK-CAN",
            expiry = "2026-11-30",
            transactionType = "Restock Delivery"
        )
        dao.insertTransaction(tx)

        val txs = dao.getAllTransactions().first()
        assertEquals(1, txs.size)
        assertEquals("012000001291", txs[0].upc)
        assertEquals("PEP-12PK-CAN", txs[0].alternateLookup)
        assertEquals(58.0, txs[0].onHandQty, 0.01)
        assertEquals(7.49, txs[0].activePrice, 0.01)
    }

    @Test
    fun testXlsxReadWriteRoundTrip() {
        val items = XlsxManager.createSampleItems()
        val txs = XlsxManager.createSampleTransactions()

        val outputStream = ByteArrayOutputStream()
        XlsxManager.writeWorkbook(outputStream, items, txs)

        val bytes = outputStream.toByteArray()
        assert(bytes.isNotEmpty())

        val inputStream = ByteArrayInputStream(bytes)
        val workbookData = XlsxManager.readWorkbook(inputStream)

        assertEquals(items.size, workbookData.items.size)
        assertEquals(txs.size, workbookData.transactions.size)

        // Verify first item values
        val firstItem = workbookData.items[0]
        assertEquals("012000001291", firstItem.upc)
        assertEquals("Pepsi Cola 12oz Can 12-Pack", firstItem.itemName)
        assertEquals(48.0, firstItem.onHandQty, 0.01)
        assertEquals(7.99, firstItem.activePrice, 0.01)
        assertEquals(24.0, firstItem.reorderPoint, 0.01)

        // Verify transaction history record
        val firstTx = workbookData.transactions[0]
        assertEquals("012000001291", firstTx.upc)
        assertEquals("PEP-12PK-001", firstTx.alternateLookup)
    }

    @Test
    fun testLowStockAlertBasedOnReorderPoint() {
        val lowStockItem = InventoryItem(
            upc = "012000001291",
            itemName = "Test Item",
            itemNumber = "ITM-001",
            unitOfMeasure = "EA",
            onHandQty = 5.0,
            activePrice = 10.0,
            reorderPoint = 12.0
        )
        // onHandQty (5.0) <= reorderPoint (12.0) -> triggers low stock alert
        assertEquals(true, lowStockItem.isLowStock)

        val normalStockItem = InventoryItem(
            upc = "012000001292",
            itemName = "Test Item Normal",
            itemNumber = "ITM-002",
            unitOfMeasure = "EA",
            onHandQty = 20.0,
            activePrice = 10.0,
            reorderPoint = 12.0
        )
        // onHandQty (20.0) > reorderPoint (12.0) -> normal stock
        assertEquals(false, normalStockItem.isLowStock)

        val zeroThresholdItem = InventoryItem(
            upc = "012000001293",
            itemName = "Test Item No Threshold",
            itemNumber = "ITM-003",
            unitOfMeasure = "EA",
            onHandQty = 0.0,
            activePrice = 10.0,
            reorderPoint = 0.0
        )
        // reorderPoint is 0.0 -> alert not active unless threshold is configured
        assertEquals(false, zeroThresholdItem.isLowStock)
    }

    @Test
    fun testBarcodeChecksumAndValidation() {
        // Valid UPC-A barcodes
        assertEquals(true, com.example.camera.BarcodeValidator.isValidUpcA("012000001291"))
        assertEquals(true, com.example.camera.BarcodeValidator.isValidUpcA("049000050103"))
        assertEquals(true, com.example.camera.BarcodeValidator.isValidUpcA("078742351896"))

        // Partial / Truncated / Corrupted barcodes must be rejected
        assertEquals(false, com.example.camera.BarcodeValidator.isValidUpcA("01200000129")) // 11 digits
        assertEquals(false, com.example.camera.BarcodeValidator.isValidUpcA("012000001299")) // wrong checksum
        assertEquals(false, com.example.camera.BarcodeValidator.isValidUpcA("1291")) // partial slice

        // EAN-13 checksum validation
        assertEquals(true, com.example.camera.BarcodeValidator.isValidEan13("4006381333931"))
        assertEquals(false, com.example.camera.BarcodeValidator.isValidEan13("4006381333930")) // wrong check digit
    }
}
