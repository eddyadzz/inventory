package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.InventoryViewModel
import com.example.ui.UiEvent
import com.example.ui.components.ItemDetailBottomSheet
import com.example.ui.components.NewItemDialog
import com.example.ui.components.SyncStatusBar
import com.example.ui.screens.GoogleDriveSyncScreen
import com.example.ui.screens.HistoryLogScreen
import com.example.ui.screens.InventoryListScreen
import com.example.ui.screens.ScannerScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.ScannerOrange
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: InventoryViewModel by viewModels {
        InventoryViewModel.provideFactory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                InventoryApp(viewModel = viewModel)
            }
        }
    }
}

enum class NavTab(val title: String) {
    SCANNER("Scanner"),
    INVENTORY("Stock"),
    HISTORY("History Log"),
    SYNC("Google Drive")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryApp(viewModel: InventoryViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableIntStateOf(0) }

    val syncStatus by viewModel.syncStatus.collectAsState()
    val activeItem by viewModel.activeSelectedItem.collectAsState()
    val unknownBarcode by viewModel.unknownBarcode.collectAsState()

    // Observe ViewModel Events (Toasts / Notifications)
    LaunchedEffect(Unit) {
        viewModel.eventFlow.collectLatest { event ->
            when (event) {
                is UiEvent.ShowToast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                    scope.launch {
                        snackbarHostState.showSnackbar(event.message)
                    }
                }
                is UiEvent.BarcodeScannedSuccess -> {
                    Toast.makeText(context, "Scanned: ${event.itemName}", Toast.LENGTH_SHORT).show()
                }
                is UiEvent.LowStockAlert -> {
                    val q = if (event.item.onHandQty % 1.0 == 0.0) event.item.onHandQty.toLong().toString() else "%.1f".format(event.item.onHandQty)
                    val rop = if (event.item.reorderPoint % 1.0 == 0.0) event.item.reorderPoint.toLong().toString() else "%.1f".format(event.item.reorderPoint)
                    val alertText = "⚠️ LOW STOCK ALERT: ${event.item.itemName} has $q ${event.item.unitOfMeasure} (Reorder Point: $rop)"
                    Toast.makeText(context, alertText, Toast.LENGTH_LONG).show()
                    scope.launch {
                        snackbarHostState.showSnackbar(alertText)
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Inventory Sync",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                        Text(
                            text = "Real-time Google Drive ITEM LIST.xlsx",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .testTag("bottom_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                // 1. Scanner Tab
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = {
                        Icon(
                            if (selectedTab == 0) Icons.Filled.QrCodeScanner else Icons.Outlined.QrCodeScanner,
                            contentDescription = "Scanner"
                        )
                    },
                    label = { Text("Scanner") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        indicatorColor = ScannerOrange
                    ),
                    modifier = Modifier.testTag("nav_scanner")
                )

                // 2. Stock Inventory Tab
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        Icon(
                            if (selectedTab == 1) Icons.Filled.Inventory2 else Icons.Outlined.Inventory2,
                            contentDescription = "Stock"
                        )
                    },
                    label = { Text("Stock") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        indicatorColor = ScannerOrange
                    ),
                    modifier = Modifier.testTag("nav_inventory")
                )

                // 3. History Log Tab
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = {
                        Icon(
                            if (selectedTab == 2) Icons.Filled.History else Icons.Outlined.History,
                            contentDescription = "History Log"
                        )
                    },
                    label = { Text("History Log") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        indicatorColor = ScannerOrange
                    ),
                    modifier = Modifier.testTag("nav_history")
                )

                // 4. Google Drive Sync Tab
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = {
                        Icon(
                            if (selectedTab == 3) Icons.Filled.CloudSync else Icons.Outlined.CloudSync,
                            contentDescription = "Drive Sync"
                        )
                    },
                    label = { Text("Drive Sync") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        indicatorColor = ScannerOrange
                    ),
                    modifier = Modifier.testTag("nav_sync")
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Persistent Real-time Sync Status Bar
            SyncStatusBar(
                syncStatus = syncStatus,
                onSyncClick = { viewModel.syncNow() },
                onStatusClick = { selectedTab = 3 }
            )

            // Content Screen
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    0 -> ScannerScreen(viewModel = viewModel)
                    1 -> InventoryListScreen(viewModel = viewModel)
                    2 -> HistoryLogScreen(viewModel = viewModel)
                    3 -> GoogleDriveSyncScreen(viewModel = viewModel)
                }
            }
        }
    }

    // Modal Bottom Sheet when item is scanned / selected
    activeItem?.let { item ->
        ItemDetailBottomSheet(
            item = item,
            isAutoSyncEnabled = syncStatus.autoSyncEnabled,
            onDismiss = { viewModel.dismissItemDetail() },
            onSaveUpdate = { targetItem, newQty, newPrice, newReorderPoint, reason ->
                viewModel.updateStockAndPrice(targetItem, newQty, newPrice, newReorderPoint, reason)
            }
        )
    }

    // Dialog when an unknown barcode is scanned
    unknownBarcode?.let { barcode ->
        NewItemDialog(
            initialBarcode = barcode,
            onDismiss = { viewModel.dismissItemDetail() },
            onSaveNewItem = { upc, name, num, uom, qty, price, reorderPoint, desc, dept, alt, exp ->
                viewModel.addNewItemFromBarcode(upc, name, num, uom, qty, price, reorderPoint, desc, dept, alt, exp)
            }
        )
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Inventory Sync: $name", modifier = modifier)
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MyApplicationTheme { Greeting("Preview") }
}
