package com.store478.rtvcheck

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.store478.rtvcheck.data.ItemResult
import com.store478.rtvcheck.data.LookupState
import com.store478.rtvcheck.scanner.BarcodeScannerView

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                RTVCheckApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RTVCheckApp(viewModel: MainViewModel) {
    val context = LocalContext.current
    val lookupState by viewModel.lookupState.collectAsState()
    val inputText by viewModel.inputText.collectAsState()

    var showScanner by remember { mutableStateOf(false) }
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (granted) showScanner = true
    }

    if (showScanner) {
        ScannerScreen(
            onClose = { showScanner = false },
            onBarcodeDetected = { code ->
                showScanner = false
                viewModel.lookup(code)
            }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("RTV / Non-RTV Check", fontWeight = FontWeight.Bold)
                        Text("Store 478 - FMT Karawaci", fontSize = 12.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { viewModel.onInputTextChanged(it) },
                    modifier = Modifier.weight(1f),
                    label = { Text("Scan / Input UPC atau SKU") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                        onSearch = { viewModel.lookup(inputText) }
                    ),
                    trailingIcon = {
                        if (inputText.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clear() }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    }
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledIconButton(
                    onClick = {
                        if (hasCameraPermission) {
                            showScanner = true
                        } else {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        }
                    },
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, contentDescription = "Scan barcode")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { viewModel.lookup(inputText) },
                modifier = Modifier.fillMaxWidth(),
                enabled = inputText.isNotBlank()
            ) {
                Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Cari")
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (val state = lookupState) {
                is LookupState.Idle -> {
                    EmptyHint()
                }
                is LookupState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is LookupState.Found -> {
                    ItemResultCard(state.item)
                }
                is LookupState.NotFound -> {
                    NotFoundCard(state.query)
                }
            }
        }
    }
}

@Composable
fun EmptyHint() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            Icons.Default.Search,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = Color.LightGray
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Scan barcode atau ketik UPC / SKU untuk cek status RTV",
            color = Color.Gray
        )
    }
}

@Composable
fun NotFoundCard(query: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Barang tidak ditemukan",
                fontWeight = FontWeight.Bold,
                color = Color(0xFFE65100)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("UPC/SKU: $query", color = Color(0xFF6D4C00))
        }
    }
}

@Composable
fun ItemResultCard(item: ItemResult) {
    val (badgeColor, badgeText) = when {
        item.isTukarGuling -> Color(0xFF7B1FA2) to "TUKAR GULING"
        item.isNonRtv -> Color(0xFFD32F2F) to "NON RTV"
        item.isRtv -> Color(0xFF2E7D32) to item.keterangan.ifBlank { "RTV" }
        else -> Color(0xFF757575) to (item.keterangan.ifBlank { "TIDAK DIKETAHUI" })
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp)) {

            Box(
                modifier = Modifier
                    .background(badgeColor, RoundedCornerShape(8.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    badgeText,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                item.description,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            InfoRow("UPC", item.upc)
            InfoRow("SKU", item.sku)
            InfoRow("Status", item.status)
            InfoRow("Supplier No", item.supplierNo)
            InfoRow("Supplier Name", item.supplierName)
            InfoRow("Keterangan", item.keterangan.ifBlank { "-" })
        }
    }
}

@Composable
fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(
            label,
            modifier = Modifier.width(120.dp),
            color = Color.Gray,
            fontSize = 14.sp
        )
        Text(
            value,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp
        )
    }
}

@Composable
fun ScannerScreen(onClose: () -> Unit, onBarcodeDetected: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxSize()) {
        BarcodeScannerView(
            modifier = Modifier.fillMaxSize(),
            onBarcodeDetected = onBarcodeDetected
        )

        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(260.dp, 140.dp)
                .background(Color.Transparent)
        )

        Text(
            "Arahkan kamera ke barcode",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 48.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )

        FilledIconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Tutup scanner")
        }
    }
}
