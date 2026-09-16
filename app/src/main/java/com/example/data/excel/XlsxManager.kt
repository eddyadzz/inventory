package com.example.data.excel

import android.util.Log
import android.util.Xml
import com.example.model.InventoryItem
import com.example.model.TransactionRecord
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * High-performance, pure Kotlin OpenXML (.xlsx) Reader and Writer.
 * Fully compatible with Google Drive, Google Sheets, Microsoft Excel, and LibreOffice.
 */
object XlsxManager {

    private const val TAG = "XlsxManager"

    data class WorkbookData(
        val items: List<InventoryItem>,
        val transactions: List<TransactionRecord>
    )

    /**
     * Reads an .xlsx input stream and extracts items and transaction history.
     */
    fun readWorkbook(inputStream: InputStream): WorkbookData {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(inputStream).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.replace('\\', '/')
                    entries[name] = zis.readBytes()
                }
                entry = zis.nextEntry
            }
        }

        // 1. Parse shared strings table if available
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) } ?: emptyList()

        // 2. Parse workbook to find sheets
        val sheetMap = entries["xl/workbook.xml"]?.let { parseWorkbookSheets(it) } ?: emptyList()
        val relsMap = entries["xl/_rels/workbook.xml.rels"]?.let { parseRelationships(it) } ?: emptyMap()

        var itemsSheetPath: String? = null
        var historySheetPath: String? = null

        for (sheet in sheetMap) {
            val target = relsMap[sheet.rId] ?: continue
            val fullPath = if (target.startsWith("/")) target.substring(1) else "xl/$target"
            val lowerName = sheet.name.lowercase()
            if (lowerName.contains("history") || lowerName.contains("log") || lowerName.contains("transact")) {
                historySheetPath = fullPath
            } else if (itemsSheetPath == null) {
                itemsSheetPath = fullPath
            }
        }

        // Fallback if sheets not resolved by name
        if (itemsSheetPath == null) {
            itemsSheetPath = entries.keys.firstOrNull { it.startsWith("xl/worksheets/sheet") }
        }
        if (historySheetPath == null && sheetMap.size > 1) {
            historySheetPath = entries.keys.filter { it.startsWith("xl/worksheets/sheet") }
                .firstOrNull { it != itemsSheetPath }
        }

        val items = mutableListOf<InventoryItem>()
        val transactions = mutableListOf<TransactionRecord>()

        // 3. Parse Item List sheet
        itemsSheetPath?.let { path ->
            entries[path]?.let { data ->
                val rows = parseWorksheetRows(data, sharedStrings)
                items.addAll(parseInventoryItemsFromRows(rows))
            }
        }

        // 4. Parse History Log sheet if present
        historySheetPath?.let { path ->
            entries[path]?.let { data ->
                val rows = parseWorksheetRows(data, sharedStrings)
                transactions.addAll(parseTransactionsFromRows(rows))
            }
        }

        Log.d(TAG, "Loaded ${items.size} items and ${transactions.size} history logs from spreadsheet.")
        return WorkbookData(items, transactions)
    }

    /**
     * Writes the inventory items and transaction history into a standard .xlsx stream.
     */
    fun writeWorkbook(
        outputStream: OutputStream,
        items: List<InventoryItem>,
        transactions: List<TransactionRecord>
    ) {
        ZipOutputStream(outputStream).use { zos ->
            // [Content_Types].xml
            zos.putNextEntry(ZipEntry("[Content_Types].xml"))
            zos.write(getContentTypesXml().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // _rels/.rels
            zos.putNextEntry(ZipEntry("_rels/.rels"))
            zos.write(getRootRelsXml().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // xl/_rels/workbook.xml.rels
            zos.putNextEntry(ZipEntry("xl/_rels/workbook.xml.rels"))
            zos.write(getWorkbookRelsXml().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // xl/workbook.xml
            zos.putNextEntry(ZipEntry("xl/workbook.xml"))
            zos.write(getWorkbookXml().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // xl/styles.xml
            zos.putNextEntry(ZipEntry("xl/styles.xml"))
            zos.write(getStylesXml().toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // xl/worksheets/sheet1.xml (Item List)
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            zos.write(buildItemListSheetXml(items).toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // xl/worksheets/sheet2.xml (Transaction History)
            zos.putNextEntry(ZipEntry("xl/worksheets/sheet2.xml"))
            zos.write(buildTransactionHistorySheetXml(transactions).toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }
    }

    // --- Parser Helpers ---

    private fun parseSharedStrings(xmlData: ByteArray): List<String> {
        val strings = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlData), "UTF-8")

        var eventType = parser.eventType
        var currentString = StringBuilder()
        var insideSi = false
        var insideT = false

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "si" -> {
                            insideSi = true
                            currentString = StringBuilder()
                        }
                        "t" -> if (insideSi) insideT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideSi && insideT) {
                        currentString.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "t" -> insideT = false
                        "si" -> {
                            insideSi = false
                            strings.add(currentString.toString())
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return strings
    }

    private data class SheetInfo(val name: String, val rId: String)

    private fun parseWorkbookSheets(xmlData: ByteArray): List<SheetInfo> {
        val sheets = mutableListOf<SheetInfo>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlData), "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "sheet") {
                val name = parser.getAttributeValue(null, "name") ?: "Sheet"
                var rId = parser.getAttributeValue("http://schemas.openxmlformats.org/officeDocument/2006/relationships", "id")
                if (rId == null) {
                    rId = parser.getAttributeValue(null, "r:id") ?: ""
                }
                sheets.add(SheetInfo(name, rId))
            }
            eventType = parser.next()
        }
        return sheets
    }

    private fun parseRelationships(xmlData: ByteArray): Map<String, String> {
        val rels = mutableMapOf<String, String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlData), "UTF-8")

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
                val id = parser.getAttributeValue(null, "Id")
                val target = parser.getAttributeValue(null, "Target")
                if (id != null && target != null) {
                    rels[id] = target
                }
            }
            eventType = parser.next()
        }
        return rels
    }

    private fun parseWorksheetRows(xmlData: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(xmlData), "UTF-8")

        var currentRow = mutableMapOf<Int, String>()
        var currentCellRef = ""
        var currentCellType = ""
        var currentCellValue = StringBuilder()
        var insideV = false
        var insideT = false
        var maxCol = 0

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> {
                            currentRow = mutableMapOf()
                            maxCol = 0
                        }
                        "c" -> {
                            currentCellRef = parser.getAttributeValue(null, "r") ?: ""
                            currentCellType = parser.getAttributeValue(null, "t") ?: ""
                            currentCellValue = StringBuilder()
                        }
                        "v" -> insideV = true
                        "t" -> insideT = true
                    }
                }
                XmlPullParser.TEXT -> {
                    if (insideV || insideT) {
                        currentCellValue.append(parser.text)
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "v" -> insideV = false
                        "t" -> insideT = false
                        "c" -> {
                            val rawText = currentCellValue.toString().trim()
                            val cellStr = when (currentCellType) {
                                "s" -> {
                                    val idx = rawText.toIntOrNull()
                                    if (idx != null && idx in sharedStrings.indices) sharedStrings[idx] else rawText
                                }
                                "inlineStr" -> rawText
                                else -> rawText
                            }
                            val colIdx = colRefToIndex(currentCellRef)
                            if (colIdx >= 0) {
                                currentRow[colIdx] = cellStr
                                if (colIdx > maxCol) maxCol = colIdx
                            }
                        }
                        "row" -> {
                            if (currentRow.isNotEmpty()) {
                                val rowList = ArrayList<String>(maxCol + 1)
                                for (i in 0..maxCol) {
                                    rowList.add(currentRow[i] ?: "")
                                }
                                rows.add(rowList)
                            }
                        }
                    }
                }
            }
            eventType = parser.next()
        }
        return rows
    }

    private fun colRefToIndex(ref: String): Int {
        var col = 0
        var foundLetters = false
        for (ch in ref.uppercase()) {
            if (ch in 'A'..'Z') {
                foundLetters = true
                col = col * 26 + (ch - 'A' + 1)
            } else if (foundLetters) {
                break
            }
        }
        return if (col > 0) col - 1 else -1
    }

    private fun cleanHeader(header: String): String {
        return header.lowercase().replace(" ", "").replace("_", "").replace("-", "")
            .replace("#", "num").replace(".", "")
    }

    private fun parseInventoryItemsFromRows(rows: List<List<String>>): List<InventoryItem> {
        if (rows.isEmpty()) return emptyList()

        val headerRow = rows[0]
        val colMap = mutableMapOf<String, Int>()

        headerRow.forEachIndexed { index, title ->
            val clean = cleanHeader(title)
            when {
                clean in listOf("upc", "barcode", "upccode", "upcean", "gtin") -> colMap["upc"] = index
                clean in listOf("itemname", "name", "productname", "item", "descriptionname") -> colMap["itemName"] = index
                clean in listOf("itemnum", "itemno", "itemnumber", "sku", "partnum", "partno") -> colMap["itemNumber"] = index
                clean in listOf("unitofmeasure", "uom", "unit", "measure") -> colMap["unitOfMeasure"] = index
                clean in listOf("onhandqty", "onhand", "qty", "quantity", "stock", "stockqty") -> colMap["onHandQty"] = index
                clean in listOf("activeprice", "price", "retailprice", "unitprice", "cost") -> colMap["activePrice"] = index
                clean in listOf("itemdescription", "description", "desc", "details") -> colMap["itemDescription"] = index
                clean in listOf("department", "dept", "category", "section") -> colMap["department"] = index
                clean in listOf("alternatelookup", "altlookup", "alternateupc", "altsku", "lookup2") -> colMap["alternateLookup"] = index
                clean in listOf("expiry", "expirydate", "expdate", "expiration", "exp") -> colMap["expiry"] = index
                clean in listOf("reorderpoint", "reorder", "rop", "reorderlevel", "reorderthreshold", "minstock", "minqty", "alertpoint", "orderpoint", "threshold", "lowstocklevel") -> colMap["reorderPoint"] = index
            }
        }

        // Fallbacks if headers weren't found by exact name
        if (!colMap.containsKey("upc")) colMap["upc"] = 0
        if (!colMap.containsKey("itemName")) colMap["itemName"] = 1
        if (!colMap.containsKey("onHandQty")) colMap["onHandQty"] = 4
        if (!colMap.containsKey("activePrice")) colMap["activePrice"] = 5

        val items = mutableListOf<InventoryItem>()
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.all { it.isBlank() }) continue

            fun get(key: String, default: String = ""): String {
                val idx = colMap[key] ?: return default
                return if (idx < row.size) row[idx].trim() else default
            }

            val upc = get("upc")
            val name = get("itemName")
            if (upc.isBlank() && name.isBlank()) continue

            val qtyStr = get("onHandQty").replace(",", "").replace("$", "").replace("MVR", "").replace("mvr", "")
            val priceStr = get("activePrice").replace(",", "").replace("$", "").replace("MVR", "").replace("mvr", "")
            val ropStr = get("reorderPoint").replace(",", "").replace("$", "").replace("MVR", "").replace("mvr", "")

            items.add(
                InventoryItem(
                    upc = upc,
                    itemName = if (name.isNotBlank()) name else "Item $upc",
                    itemNumber = get("itemNumber"),
                    unitOfMeasure = get("unitOfMeasure", "EA"),
                    onHandQty = qtyStr.toDoubleOrNull() ?: 0.0,
                    activePrice = priceStr.toDoubleOrNull() ?: 0.0,
                    reorderPoint = ropStr.toDoubleOrNull() ?: 10.0,
                    itemDescription = get("itemDescription"),
                    department = get("department", "General"),
                    alternateLookup = get("alternateLookup"),
                    expiry = get("expiry")
                )
            )
        }
        return items
    }

    private fun parseTransactionsFromRows(rows: List<List<String>>): List<TransactionRecord> {
        if (rows.size <= 1) return emptyList()

        val headerRow = rows[0]
        val colMap = mutableMapOf<String, Int>()

        headerRow.forEachIndexed { index, title ->
            val clean = cleanHeader(title)
            when {
                clean in listOf("datetime", "datetimestamp", "timestamp", "date", "time") -> colMap["timestamp"] = index
                clean in listOf("upc", "barcode") -> colMap["upc"] = index
                clean in listOf("itemname", "name", "productname") -> colMap["itemName"] = index
                clean in listOf("itemnum", "itemno", "itemnumber", "sku") -> colMap["itemNumber"] = index
                clean in listOf("unitofmeasure", "uom", "unit") -> colMap["unitOfMeasure"] = index
                clean in listOf("onhandqty", "onhand", "qty", "newqty") -> colMap["onHandQty"] = index
                clean in listOf("activeprice", "price") -> colMap["activePrice"] = index
                clean in listOf("itemdescription", "description", "desc") -> colMap["itemDescription"] = index
                clean in listOf("department", "dept") -> colMap["department"] = index
                clean in listOf("alternatelookup", "altlookup") -> colMap["alternateLookup"] = index
                clean in listOf("expiry", "expdate") -> colMap["expiry"] = index
                clean in listOf("type", "transactiontype", "action") -> colMap["transactionType"] = index
            }
        }

        val records = mutableListOf<TransactionRecord>()
        for (i in 1 until rows.size) {
            val row = rows[i]
            if (row.all { it.isBlank() }) continue

            fun get(key: String, default: String = ""): String {
                val idx = colMap[key] ?: return default
                return if (idx < row.size) row[idx].trim() else default
            }

            val upc = get("upc")
            if (upc.isBlank()) continue

            records.add(
                TransactionRecord(
                    timestamp = get("timestamp", TransactionRecord.currentTimestamp()),
                    upc = upc,
                    itemName = get("itemName"),
                    itemNumber = get("itemNumber"),
                    unitOfMeasure = get("unitOfMeasure", "EA"),
                    onHandQty = get("onHandQty").toDoubleOrNull() ?: 0.0,
                    activePrice = get("activePrice").toDoubleOrNull() ?: 0.0,
                    itemDescription = get("itemDescription"),
                    department = get("department"),
                    alternateLookup = get("alternateLookup"),
                    expiry = get("expiry"),
                    transactionType = get("transactionType", "Stock Adjustment"),
                    isSynced = true
                )
            )
        }
        return records
    }

    // --- XML Writers (OpenXML Compliant) ---

    private fun escapeXml(str: String): String {
        return str.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun getColLetter(colIdx: Int): String {
        var num = colIdx + 1
        var result = ""
        while (num > 0) {
            val rem = (num - 1) % 26
            result = (('A'.code + rem).toChar()) + result
            num = (num - 1) / 26
        }
        return result
    }

    private fun buildCellInlineStr(col: Int, row: Int, value: String): String {
        val ref = "${getColLetter(col)}$row"
        return """<c r="$ref" t="inlineStr"><is><t>${escapeXml(value)}</t></is></c>"""
    }

    private fun buildCellNumber(col: Int, row: Int, value: Double): String {
        val ref = "${getColLetter(col)}$row"
        val formatted = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(value)
        return """<c r="$ref"><v>$formatted</v></c>"""
    }

    private fun buildItemListSheetXml(items: List<InventoryItem>): String {
        val sb = StringBuilder(16384)
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetData>""")

        // Header Row: UPC, Item Name, Item#, Unit of Measure, On-hand Qty, Active Price, Reorder Point, Item Description, Department, Alternate Lookup, expiry
        val headers = listOf(
            "UPC", "Item Name", "Item#", "Unit of Measure", "On-hand Qty",
            "Active Price", "Reorder Point", "Item Description", "Department", "Alternate Lookup", "expiry"
        )
        sb.append("""<row r="1">""")
        headers.forEachIndexed { index, header ->
            sb.append(buildCellInlineStr(index, 1, header))
        }
        sb.append("""</row>""")

        // Data Rows
        items.forEachIndexed { itemIdx, item ->
            val rowNum = itemIdx + 2
            sb.append("""<row r="$rowNum">""")
            sb.append(buildCellInlineStr(0, rowNum, item.upc))
            sb.append(buildCellInlineStr(1, rowNum, item.itemName))
            sb.append(buildCellInlineStr(2, rowNum, item.itemNumber))
            sb.append(buildCellInlineStr(3, rowNum, item.unitOfMeasure))
            sb.append(buildCellNumber(4, rowNum, item.onHandQty))
            sb.append(buildCellNumber(5, rowNum, item.activePrice))
            sb.append(buildCellNumber(6, rowNum, item.reorderPoint))
            sb.append(buildCellInlineStr(7, rowNum, item.itemDescription))
            sb.append(buildCellInlineStr(8, rowNum, item.department))
            sb.append(buildCellInlineStr(9, rowNum, item.alternateLookup))
            sb.append(buildCellInlineStr(10, rowNum, item.expiry))
            sb.append("""</row>""")
        }

        sb.append("""</sheetData>""")
        sb.append("""</worksheet>""")
        return sb.toString()
    }

    private fun buildTransactionHistorySheetXml(transactions: List<TransactionRecord>): String {
        val sb = StringBuilder(16384)
        sb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        sb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        sb.append("""<sheetData>""")

        // Header Row: UPC, Item Name, Item#, Unit of Measure, On-hand Qty, Item Description, Department, Alternate Lookup, expiry, and the date-time stamp
        val headers = listOf(
            "Date-Time Stamp", "UPC", "Item Name", "Item#", "Unit of Measure",
            "On-hand Qty", "Active Price", "Item Description", "Department", "Alternate Lookup", "expiry", "Transaction Type"
        )
        sb.append("""<row r="1">""")
        headers.forEachIndexed { index, header ->
            sb.append(buildCellInlineStr(index, 1, header))
        }
        sb.append("""</row>""")

        // Data Rows
        transactions.forEachIndexed { txIdx, tx ->
            val rowNum = txIdx + 2
            sb.append("""<row r="$rowNum">""")
            sb.append(buildCellInlineStr(0, rowNum, tx.timestamp))
            sb.append(buildCellInlineStr(1, rowNum, tx.upc))
            sb.append(buildCellInlineStr(2, rowNum, tx.itemName))
            sb.append(buildCellInlineStr(3, rowNum, tx.itemNumber))
            sb.append(buildCellInlineStr(4, rowNum, tx.unitOfMeasure))
            sb.append(buildCellNumber(5, rowNum, tx.onHandQty))
            sb.append(buildCellNumber(6, rowNum, tx.activePrice))
            sb.append(buildCellInlineStr(7, rowNum, tx.itemDescription))
            sb.append(buildCellInlineStr(8, rowNum, tx.department))
            sb.append(buildCellInlineStr(9, rowNum, tx.alternateLookup))
            sb.append(buildCellInlineStr(10, rowNum, tx.expiry))
            sb.append(buildCellInlineStr(11, rowNum, tx.transactionType))
            sb.append("""</row>""")
        }

        sb.append("""</sheetData>""")
        sb.append("""</worksheet>""")
        return sb.toString()
    }

    private fun getContentTypesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/worksheets/sheet2.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""

    private fun getRootRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""

    private fun getWorkbookRelsXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet2.xml"/>
  <Relationship Id="rId3" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""

    private fun getWorkbookXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="Item List" sheetId="1" r:id="rId1"/>
    <sheet name="Transaction History" sheetId="2" r:id="rId2"/>
  </sheets>
</workbook>"""

    private fun getStylesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="1">
    <font><sz val="11"/><color theme="1"/><name val="Calibri"/><family val="2"/></font>
  </fonts>
  <fills count="1">
    <fill><patternFill patternType="none"/></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
  </cellXfs>
</styleSheet>"""

    /**
     * Preloaded sample dataset matching the requested spreadsheet format.
     */
    fun createSampleItems(): List<InventoryItem> {
        return listOf(
            InventoryItem(
                upc = "012000001291",
                itemName = "Pepsi Cola 12oz Can 12-Pack",
                itemNumber = "ITM-1001",
                unitOfMeasure = "PK",
                onHandQty = 48.0,
                activePrice = 7.99,
                reorderPoint = 24.0,
                itemDescription = "Crisp refreshing carbonated soft drink 12-pack cans",
                department = "Beverages",
                alternateLookup = "PEP-12PK-001",
                expiry = "2027-03-15"
            ),
            InventoryItem(
                upc = "049000050103",
                itemName = "Coca-Cola Classic 2 Liter",
                itemNumber = "ITM-1002",
                unitOfMeasure = "BTL",
                onHandQty = 75.0,
                activePrice = 2.49,
                reorderPoint = 20.0,
                itemDescription = "Original formula sparkling beverage 2L bottle",
                department = "Beverages",
                alternateLookup = "KO-2L-CLASSIC",
                expiry = "2027-01-20"
            ),
            InventoryItem(
                upc = "078742351896",
                itemName = "Organic Whole Milk 1 Gallon",
                itemNumber = "ITM-2005",
                unitOfMeasure = "GAL",
                onHandQty = 18.0,
                activePrice = 4.89,
                reorderPoint = 25.0, // Low stock alert! (18 <= 25)
                itemDescription = "Grade A pasteurized organic whole vitamin D milk",
                department = "Dairy",
                alternateLookup = "MILK-ORG-GAL",
                expiry = "2026-10-12"
            ),
            InventoryItem(
                upc = "021130070154",
                itemName = "Sharp Cheddar Cheese Block 8oz",
                itemNumber = "ITM-2009",
                unitOfMeasure = "EA",
                onHandQty = 32.0,
                activePrice = 3.49,
                reorderPoint = 15.0,
                itemDescription = "Aged natural cheddar cheese brick",
                department = "Dairy",
                alternateLookup = "CHED-SHARP-8Z",
                expiry = "2026-12-05"
            ),
            InventoryItem(
                upc = "038000198547",
                itemName = "Kellogg's Frosted Flakes 17.3oz",
                itemNumber = "ITM-3011",
                unitOfMeasure = "BOX",
                onHandQty = 24.0,
                activePrice = 5.29,
                reorderPoint = 12.0,
                itemDescription = "Sweetened toasted corn cereal with vitamin fortification",
                department = "Grocery",
                alternateLookup = "CEREAL-FF-17",
                expiry = "2027-06-30"
            ),
            InventoryItem(
                upc = "011110853128",
                itemName = "Extra Virgin Olive Oil 500ml",
                itemNumber = "ITM-3040",
                unitOfMeasure = "BTL",
                onHandQty = 15.0,
                activePrice = 9.99,
                reorderPoint = 20.0, // Low stock alert! (15 <= 20)
                itemDescription = "Cold pressed Mediterranean extra virgin olive oil",
                department = "Grocery",
                alternateLookup = "EVOO-MED-500",
                expiry = "2027-11-15"
            ),
            InventoryItem(
                upc = "037000143899",
                itemName = "Tide Liquid Detergent Clean Breeze 64 loads",
                itemNumber = "ITM-4015",
                unitOfMeasure = "JUG",
                onHandQty = 12.0,
                activePrice = 14.99,
                reorderPoint = 15.0, // Low stock alert! (12 <= 15)
                itemDescription = "High efficiency laundry detergent for bright whites",
                department = "Household",
                alternateLookup = "TIDE-LD-64",
                expiry = "2028-01-01"
            ),
            InventoryItem(
                upc = "036000291452",
                itemName = "Kleenex Ultra Soft Facial Tissues 3-Pack",
                itemNumber = "ITM-4022",
                unitOfMeasure = "PK",
                onHandQty = 40.0,
                activePrice = 6.49,
                reorderPoint = 20.0,
                itemDescription = "3-ply gentle soothing facial tissue boxes",
                department = "Household",
                alternateLookup = "KLX-ULTRA-3PK",
                expiry = "2029-01-01"
            ),
            InventoryItem(
                upc = "041220789012",
                itemName = "DeWalt 20V MAX Cordless Drill Kit",
                itemNumber = "ITM-5001",
                unitOfMeasure = "KIT",
                onHandQty = 6.0,
                activePrice = 99.00,
                reorderPoint = 10.0, // Low stock alert! (6 <= 10)
                itemDescription = "Compact brushless 1/2 in drill with battery & charger",
                department = "Hardware",
                alternateLookup = "DCD771C2",
                expiry = "N/A"
            ),
            InventoryItem(
                upc = "045242567891",
                itemName = "Milwaukee Heavy-Duty 25ft Tape Measure",
                itemNumber = "ITM-5012",
                unitOfMeasure = "EA",
                onHandQty = 25.0,
                activePrice = 19.97,
                reorderPoint = 10.0,
                itemDescription = "Impact resistant reinforced frame measuring tape",
                department = "Hardware",
                alternateLookup = "48-22-6625",
                expiry = "N/A"
            ),
            InventoryItem(
                upc = "019425203712",
                itemName = "USB-C to Lightning Fast Charge Cable 2M",
                itemNumber = "ITM-6003",
                unitOfMeasure = "EA",
                onHandQty = 35.0,
                activePrice = 18.50,
                reorderPoint = 15.0,
                itemDescription = "Braided high speed charging and sync cable",
                department = "Electronics",
                alternateLookup = "CBL-USBC-LTG",
                expiry = "N/A"
            ),
            InventoryItem(
                upc = "084005612345",
                itemName = "Anker 65W GaN Nano II Wall Charger",
                itemNumber = "ITM-6010",
                unitOfMeasure = "EA",
                onHandQty = 14.0,
                activePrice = 39.99,
                reorderPoint = 10.0,
                itemDescription = "Ultra compact foldable GaN fast charging adapter",
                department = "Electronics",
                alternateLookup = "ANK-NANO-65W",
                expiry = "N/A"
            )
        )
    }

    fun createSampleTransactions(): List<TransactionRecord> {
        return listOf(
            TransactionRecord(
                timestamp = "2026-09-14 09:15:22",
                upc = "012000001291",
                itemName = "Pepsi Cola 12oz Can 12-Pack",
                itemNumber = "ITM-1001",
                unitOfMeasure = "PK",
                onHandQty = 48.0,
                qtyChange = 24.0,
                activePrice = 7.99,
                itemDescription = "Crisp refreshing carbonated soft drink 12-pack cans",
                department = "Beverages",
                alternateLookup = "PEP-12PK-001",
                expiry = "2027-03-15",
                transactionType = "Restock Delivery",
                isSynced = true
            ),
            TransactionRecord(
                timestamp = "2026-09-14 14:40:10",
                upc = "041220789012",
                itemName = "DeWalt 20V MAX Cordless Drill Kit",
                itemNumber = "ITM-5001",
                unitOfMeasure = "KIT",
                onHandQty = 6.0,
                qtyChange = -1.0,
                activePrice = 99.00,
                itemDescription = "Compact brushless 1/2 in drill with battery & charger",
                department = "Hardware",
                alternateLookup = "DCD771C2",
                expiry = "N/A",
                transactionType = "Cycle Count Audit",
                isSynced = true
            )
        )
    }

    /**
     * Generates a complete ITEM LIST.xlsx as ByteArray.
     */
    fun createSampleWorkbookBytes(): ByteArray {
        val baos = ByteArrayOutputStream()
        writeWorkbook(baos, createSampleItems(), createSampleTransactions())
        return baos.toByteArray()
    }
}
