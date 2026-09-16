package com.example.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ScannerOrange

@Composable
fun NewItemDialog(
    initialBarcode: String = "",
    onDismiss: () -> Unit,
    onSaveNewItem: (
        upc: String,
        name: String,
        itemNumber: String,
        unitOfMeasure: String,
        initialQty: Double,
        price: Double,
        reorderPoint: Double,
        description: String,
        department: String,
        alternateLookup: String,
        expiry: String
    ) -> Unit
) {
    var upc by remember { mutableStateOf(initialBarcode) }
    var alternateLookup by remember { mutableStateOf("") }
    var itemName by remember { mutableStateOf("") }
    var itemNumber by remember { mutableStateOf("ITM-${(1000..9999).random()}") }
    var unitOfMeasure by remember { mutableStateOf("EA") }
    var initialQty by remember { mutableStateOf("10") }
    var activePrice by remember { mutableStateOf("4.99") }
    var reorderPoint by remember { mutableStateOf("10") }
    var department by remember { mutableStateOf("General") }
    var description by remember { mutableStateOf("") }
    var expiry by remember { mutableStateOf("N/A") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add Item to ITEM LIST.xlsx",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "This item will be added to the main stock sheet and logged in transaction history.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = upc,
                    onValueChange = { upc = it },
                    label = { Text("UPC (Primary Barcode)") },
                    modifier = Modifier.fillMaxWidth().testTag("new_item_upc"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = alternateLookup,
                    onValueChange = { alternateLookup = it },
                    label = { Text("Alternate Lookup (Secondary Barcode / SKU)") },
                    modifier = Modifier.fillMaxWidth().testTag("new_item_alt_lookup"),
                    singleLine = true
                )

                OutlinedTextField(
                    value = itemName,
                    onValueChange = { itemName = it },
                    label = { Text("Item Name *") },
                    modifier = Modifier.fillMaxWidth().testTag("new_item_name"),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = itemNumber,
                        onValueChange = { itemNumber = it },
                        label = { Text("Item#") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = unitOfMeasure,
                        onValueChange = { unitOfMeasure = it },
                        label = { Text("UOM") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = initialQty,
                        onValueChange = { initialQty = it },
                        label = { Text("On-hand Qty") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = activePrice,
                        onValueChange = { activePrice = it },
                        label = { Text("Active Price ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                // Reorder Point (Low Stock Alert Threshold)
                OutlinedTextField(
                    value = reorderPoint,
                    onValueChange = { reorderPoint = it },
                    label = { Text("Reorder Point (Low Stock Alert)") },
                    supportingText = { Text("Alerts trigger when On-hand Qty <= Reorder Point") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("new_item_reorder_point"),
                    singleLine = true
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = department,
                        onValueChange = { department = it },
                        label = { Text("Department") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = expiry,
                        onValueChange = { expiry = it },
                        label = { Text("Expiry (YYYY-MM-DD)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Item Description") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 2
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (itemName.isNotBlank()) {
                        onSaveNewItem(
                            upc,
                            itemName,
                            itemNumber,
                            unitOfMeasure,
                            initialQty.toDoubleOrNull() ?: 0.0,
                            activePrice.toDoubleOrNull() ?: 0.0,
                            reorderPoint.toDoubleOrNull() ?: 10.0,
                            description,
                            department,
                            alternateLookup,
                            expiry
                        )
                    }
                },
                enabled = itemName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = ScannerOrange),
                modifier = Modifier.testTag("confirm_add_item_button")
            ) {
                Text("Add & Sync")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
