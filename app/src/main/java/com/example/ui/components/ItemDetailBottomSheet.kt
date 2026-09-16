package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.InventoryItem
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ScannerOrange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ItemDetailBottomSheet(
    item: InventoryItem,
    isAutoSyncEnabled: Boolean,
    onDismiss: () -> Unit,
    onSaveUpdate: (item: InventoryItem, newQty: Double, newPrice: Double, newReorderPoint: Double, reason: String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var currentQty by remember(item) { mutableStateOf(item.onHandQty) }
    var qtyInputText by remember(item) {
        val formatted = if (item.onHandQty % 1.0 == 0.0) item.onHandQty.toLong().toString() else "%.2f".format(item.onHandQty)
        mutableStateOf(formatted)
    }

    var currentPrice by remember(item) { mutableStateOf(item.activePrice) }
    var priceInputText by remember(item) {
        mutableStateOf(String.format(java.util.Locale.US, "%.2f", item.activePrice))
    }

    var currentReorderPoint by remember(item) { mutableStateOf(item.reorderPoint) }
    var reorderInputText by remember(item) {
        val formatted = if (item.reorderPoint % 1.0 == 0.0) item.reorderPoint.toLong().toString() else "%.2f".format(item.reorderPoint)
        mutableStateOf(formatted)
    }

    val isAlertActive = currentReorderPoint > 0.0 && currentQty <= currentReorderPoint

    val reasons = listOf(
        "Cycle Count",
        "Restock Delivery",
        "Price Update",
        "Damaged / Waste",
        "Sale / Deduction"
    )
    var selectedReason by remember { mutableStateOf(reasons[0]) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = null,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Drag indicator & Close
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ScannerOrange.copy(alpha = 0.15f),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            tint = ScannerOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = item.department.ifBlank { "Inventory Item" },
                            color = ScannerOrange,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 12.sp
                        )
                    }
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp).testTag("close_detail_sheet")
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close")
                }
            }

            // Title & Description
            Text(
                text = item.itemName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (item.itemDescription.isNotBlank()) {
                Text(
                    text = item.itemDescription,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 10.dp)
                )
            } else {
                Spacer(modifier = Modifier.height(10.dp))
            }

            // Low Stock Alert Banner (Real-time evaluation against Reorder Point)
            if (isAlertActive) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF1F2)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFDA4AF)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .testTag("detail_low_stock_alert_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Low Stock Alert",
                            tint = Color(0xFFE11D48),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "⚠️ Low Stock Alert!",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF9F1239)
                            )
                            val qText = if (currentQty % 1.0 == 0.0) currentQty.toLong().toString() else "%.1f".format(currentQty)
                            val ropText = if (currentReorderPoint % 1.0 == 0.0) currentReorderPoint.toLong().toString() else "%.1f".format(currentReorderPoint)
                            Text(
                                text = "On-hand stock ($qText ${item.unitOfMeasure}) is at or below the Reorder Point ($ropText). Restock recommended.",
                                fontSize = 12.sp,
                                color = Color(0xFFBE123C)
                            )
                        }
                    }
                }
            }

            // Item Metadata Grid Card
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetaCell("UPC (Barcode)", item.upc, isMono = true)
                        MetaCell("Alt Lookup", item.alternateLookup.ifBlank { "None" }, isMono = true)
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetaCell("Item#", item.itemNumber.ifBlank { "N/A" })
                        MetaCell("Unit of Measure", item.unitOfMeasure)
                        MetaCell("Reorder Point", if (item.reorderPoint % 1.0 == 0.0) item.reorderPoint.toLong().toString() else "%.1f".format(item.reorderPoint))
                        MetaCell("Expiry Date", item.expiry.ifBlank { "N/A" })
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Stock Quantity ("On-hand Qty") Section
            Text(
                text = "On-hand Qty (Current Stock)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Quick -1 button
                IconButton(
                    onClick = {
                        val newQ = maxOf(0.0, currentQty - 1.0)
                        currentQty = newQ
                        qtyInputText = if (newQ % 1.0 == 0.0) newQ.toLong().toString() else "%.2f".format(newQ)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .testTag("qty_minus_1")
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Decrease by 1")
                }

                // Exact Input
                OutlinedTextField(
                    value = qtyInputText,
                    onValueChange = { input ->
                        qtyInputText = input
                        input.toDoubleOrNull()?.let { currentQty = it }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("on_hand_qty_input"),
                    label = { Text("Stock Qty (${item.unitOfMeasure})") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )

                // Quick +1 button
                IconButton(
                    onClick = {
                        val newQ = currentQty + 1.0
                        currentQty = newQ
                        qtyInputText = if (newQ % 1.0 == 0.0) newQ.toLong().toString() else "%.2f".format(newQ)
                    },
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ScannerOrange.copy(alpha = 0.18f))
                        .testTag("qty_plus_1")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Increase by 1", tint = ScannerOrange)
                }
            }

            // Quick adjustment pills (+5, +10, -5, -10)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(-10.0, -5.0, 5.0, 10.0, 20.0).forEach { delta ->
                    val label = if (delta > 0) "+${delta.toInt()}" else "${delta.toInt()}"
                    OutlinedButton(
                        onClick = {
                            val newQ = maxOf(0.0, currentQty + delta)
                            currentQty = newQ
                            qtyInputText = if (newQ % 1.0 == 0.0) newQ.toLong().toString() else "%.2f".format(newQ)
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 2.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Price ("Active Price") Section
            Text(
                text = "Active Price ($)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = priceInputText,
                onValueChange = { input ->
                    priceInputText = input
                    input.toDoubleOrNull()?.let { currentPrice = it }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("active_price_input"),
                label = { Text("Active Price on Sheet") },
                prefix = { Text("$ ", fontWeight = FontWeight.Bold) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Reorder Point (Low Stock Threshold) Section
            Text(
                text = "Reorder Point (Low Stock Alert Threshold)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = reorderInputText,
                onValueChange = { input ->
                    reorderInputText = input
                    input.toDoubleOrNull()?.let { currentReorderPoint = it }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reorder_point_input"),
                label = { Text("Reorder Point (${item.unitOfMeasure})") },
                supportingText = { Text("Triggers Low Stock Alert when On-hand Qty <= Reorder Point") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Transaction History Reason Selector
            Text(
                text = "Transaction History Reason / Log",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                reasons.take(3).forEach { reason ->
                    FilterChip(
                        selected = selectedReason == reason,
                        onClick = { selectedReason = reason },
                        label = { Text(reason, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ScannerOrange.copy(alpha = 0.2f),
                            selectedLabelColor = ScannerOrange
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                reasons.drop(3).forEach { reason ->
                    FilterChip(
                        selected = selectedReason == reason,
                        onClick = { selectedReason = reason },
                        label = { Text(reason, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = ScannerOrange.copy(alpha = 0.2f),
                            selectedLabelColor = ScannerOrange
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action Button: Save and Sync
            Button(
                onClick = {
                    val finalQty = qtyInputText.toDoubleOrNull() ?: currentQty
                    val finalPrice = priceInputText.toDoubleOrNull() ?: currentPrice
                    val finalReorderPoint = reorderInputText.toDoubleOrNull() ?: currentReorderPoint
                    onSaveUpdate(item, finalQty, finalPrice, finalReorderPoint, selectedReason)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("save_and_sync_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ScannerOrange)
            ) {
                Icon(
                    imageVector = if (isAutoSyncEnabled) Icons.Default.Sync else Icons.Default.Check,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isAutoSyncEnabled) "Update & Sync to ITEM LIST.xlsx" else "Record & Update Inventory",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun MetaCell(label: String, value: String, isMono: Boolean = false) {
    Column {
        Text(
            text = label,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = if (isMono) FontFamily.Monospace else FontFamily.Default,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
