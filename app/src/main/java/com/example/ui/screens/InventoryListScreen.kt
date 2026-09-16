package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.model.InventoryItem
import com.example.ui.InventoryViewModel
import com.example.ui.StockFilter
import com.example.ui.components.NewItemDialog
import com.example.ui.theme.CyanAccent
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.ScannerOrange

@Composable
fun InventoryListScreen(
    viewModel: InventoryViewModel,
    modifier: Modifier = Modifier
) {
    val items by viewModel.filteredItems.collectAsState()
    val departments by viewModel.departments.collectAsState()
    val selectedDept by viewModel.selectedDepartment.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val lowStockCount by viewModel.lowStockCount.collectAsState()

    var showAddNewItemDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                placeholder = { Text("Search by UPC, Alt Lookup, Name...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("inventory_search_field")
            )

            // Stock Status Filter Chips (All / Low Stock / Expiring)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == StockFilter.ALL,
                    onClick = { viewModel.selectedFilter.value = StockFilter.ALL },
                    label = { Text("All Items") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ScannerOrange.copy(alpha = 0.2f),
                        selectedLabelColor = ScannerOrange
                    )
                )
                FilterChip(
                    selected = selectedFilter == StockFilter.LOW_STOCK,
                    onClick = { viewModel.selectedFilter.value = StockFilter.LOW_STOCK },
                    label = {
                        Text(if (lowStockCount > 0) "Low Stock ($lowStockCount)" else "Low Stock")
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFFF1F2),
                        selectedLabelColor = Color(0xFFE11D48)
                    )
                )
                FilterChip(
                    selected = selectedFilter == StockFilter.EXPIRING_SOON,
                    onClick = { viewModel.selectedFilter.value = StockFilter.EXPIRING_SOON },
                    label = { Text("Has Expiry") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = ScannerOrange.copy(alpha = 0.2f),
                        selectedLabelColor = ScannerOrange
                    )
                )
            }

            // Low Stock Alert Summary Banner (if items are below their Reorder Point)
            if (lowStockCount > 0) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable {
                            viewModel.selectedFilter.value =
                                if (selectedFilter == StockFilter.LOW_STOCK) StockFilter.ALL else StockFilter.LOW_STOCK
                        }
                        .testTag("low_stock_summary_alert_banner"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFFFF1F2)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFFDA4AF))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Low Stock Alert",
                                tint = Color(0xFFE11D48),
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "$lowStockCount Item${if (lowStockCount > 1) "s" else ""} Below Reorder Point",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = Color(0xFF9F1239)
                                )
                                Text(
                                    text = "Tap to ${if (selectedFilter == StockFilter.LOW_STOCK) "view all stock" else "filter low stock items"}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFBE123C)
                                )
                            }
                        }
                        Text(
                            text = if (selectedFilter == StockFilter.LOW_STOCK) "Show All" else "Filter",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFFE11D48),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }

            // Department horizontal scroll
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                departments.forEach { dept ->
                    FilterChip(
                        selected = selectedDept == dept,
                        onClick = { viewModel.selectedDepartment.value = dept },
                        label = { Text(dept, fontSize = 12.sp) }
                    )
                }
            }

            // Items Count and Real-time stats header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${items.size} stock items in ITEM LIST.xlsx",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Tap item to edit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Inventory Items List
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Inventory2,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No matching items found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Try clearing filters or scan a new barcode",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items, key = { it.id }) { item ->
                        InventoryItemCard(
                            item = item,
                            onClick = { viewModel.selectItem(item) },
                            onQuickAdjust = { delta -> viewModel.quickAdjustStock(item, delta) }
                        )
                    }
                }
            }
        }

        // Floating Action Button to add new item
        FloatingActionButton(
            onClick = { showAddNewItemDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("fab_add_item"),
            containerColor = ScannerOrange,
            contentColor = Color.White
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add Item to Spreadsheet")
        }

        if (showAddNewItemDialog) {
            NewItemDialog(
                onDismiss = { showAddNewItemDialog = false },
                onSaveNewItem = { upc, name, num, uom, qty, price, reorderPoint, desc, dept, alt, exp ->
                    viewModel.addNewItemFromBarcode(upc, name, num, uom, qty, price, reorderPoint, desc, dept, alt, exp)
                    showAddNewItemDialog = false
                }
            )
        }
    }
}

@Composable
fun InventoryItemCard(
    item: InventoryItem,
    onClick: () -> Unit,
    onQuickAdjust: (delta: Double) -> Unit
) {
    val isLowStock = item.isLowStock

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag("item_card_${item.upc}"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = if (isLowStock) BorderStroke(1.dp, Color(0xFFFDA4AF)) else null,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Department, Low Stock & Expiry Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = item.department.ifBlank { "General" },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }

                    if (isLowStock) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = Color(0xFFFFF1F2),
                            border = BorderStroke(1.dp, Color(0xFFFDA4AF))
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Low Stock Alert",
                                    tint = Color(0xFFE11D48),
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                val ropStr = if (item.reorderPoint % 1.0 == 0.0) item.reorderPoint.toLong().toString() else "%.1f".format(item.reorderPoint)
                                Text(
                                    text = "LOW STOCK (ROP $ropStr)",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFE11D48)
                                )
                            }
                        }
                    }
                }

                if (item.expiry.isNotBlank() && item.expiry != "N/A") {
                    Text(
                        text = "Exp: ${item.expiry}",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Item Name
            Text(
                text = item.itemName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            if (item.itemDescription.isNotBlank()) {
                Text(
                    text = item.itemDescription,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // UPC & Alternate Lookup tags
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "UPC: ",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = item.upc,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (item.alternateLookup.isNotBlank()) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Alt: ",
                        fontSize = 11.sp,
                        color = CyanAccent
                    )
                    Text(
                        text = item.alternateLookup,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom row: Stock Quantity, Active Price, and Quick +/- controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Stock Qty badge
                Column {
                    Text(
                        text = "On-hand Qty",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        val formattedQty = if (item.onHandQty % 1.0 == 0.0) item.onHandQty.toLong().toString() else "%.2f".format(item.onHandQty)
                        Text(
                            text = formattedQty,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isLowStock) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = item.unitOfMeasure,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    val ropDisplay = if (item.reorderPoint % 1.0 == 0.0) item.reorderPoint.toLong().toString() else "%.1f".format(item.reorderPoint)
                    Text(
                        text = "Reorder Point: $ropDisplay",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isLowStock) Color(0xFFE11D48) else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Price badge
                Column {
                    Text(
                        text = "Active Price",
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "MVR ${String.format(java.util.Locale.US, "%.2f", item.activePrice)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldSuccess
                    )
                }

                // Quick steppers (-1 / +1)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconButton(
                        onClick = { onQuickAdjust(-1.0) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("card_minus_${item.upc}")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Decrease 1", modifier = Modifier.size(18.dp))
                    }

                    IconButton(
                        onClick = { onQuickAdjust(1.0) },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(ScannerOrange.copy(alpha = 0.2f))
                            .testTag("card_plus_${item.upc}")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Increase 1", tint = ScannerOrange, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
    }
}
