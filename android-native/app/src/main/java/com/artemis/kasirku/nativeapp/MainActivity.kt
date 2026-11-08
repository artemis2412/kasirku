package com.artemis.kasirku.nativeapp

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemis.kasirku.nativeapp.data.VerifiedTenantContext
import com.artemis.kasirku.nativeapp.data.SaleSyncWorker
import com.artemis.kasirku.nativeapp.data.ProductionSyncWorker

private val Navy = Color(0xFF17324D)
private val Teal = Color(0xFF168C83)
private val Purple = Color(0xFF6422C7)
private val Canvas = Color(0xFFF5F7F9)
private val Muted = Color(0xFF718096)

private fun formatNumber(value: Long): String {
    val digits = value.toString()
    return digits.reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()
}

private fun formatDigitsWithDots(value: String): String {
    val digits = value.filter(Char::isDigit)
    if (digits.isEmpty()) return ""
    return digits.reversed()
        .chunked(3)
        .joinToString(".")
        .reversed()
}

private fun parseFormattedNumber(value: String): Long? =
    value.replace(".", "").filter(Char::isDigit).toLongOrNull()

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { KasirkuNativeApp() }
    }
}

private enum class KasirkuTab(val title: String, val symbol: String) {
    Home("Beranda", "⌂"),
    Products("Produk", "▦"),
    Orders("Pesanan", "▤"),
    Reports("Laporan", "▥"),
    Account("Akun", "◎")
}

@Composable
private fun KasirkuNativeApp() {
    val appContext = LocalContext.current.applicationContext
    var selectedTab by remember { mutableIntStateOf(0) }
    var showNewTransaction by remember { mutableStateOf(false) }
    var showTableManagement by remember { mutableStateOf(false) }
    var showProduction by remember { mutableStateOf(false) }
    var verifiedTenantContext by remember { mutableStateOf<VerifiedTenantContext?>(null) }

    LaunchedEffect(verifiedTenantContext?.storeId, verifiedTenantContext?.branchId, verifiedTenantContext?.branchTimeZoneId) {
        verifiedTenantContext?.takeIf { !it.branchTimeZoneId.isNullOrBlank() }?.let {
            SaleSyncWorker.enqueue(appContext, it)
            ProductionSyncWorker.enqueue(appContext, it)
        }
    }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Purple,
            secondary = Teal,
            background = Canvas,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = Navy,
            onSurface = Navy,
            secondaryContainer = Color(0xFFE5F1F0),
            onSecondaryContainer = Teal
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
            Scaffold(
                containerColor = Canvas,
                bottomBar = {
                    if (verifiedTenantContext != null) {
                        NavigationBar(containerColor = Color.White, tonalElevation = 2.dp) {
                            KasirkuTab.values().forEachIndexed { index, tab ->
                                NavigationBarItem(
                                    selected = selectedTab == index && !showNewTransaction && !showTableManagement && !showProduction,
                                    onClick = {
                                        selectedTab = index
                                        showNewTransaction = false
                                        showTableManagement = false
                                        showProduction = false
                                    },
                                    icon = { Text(tab.symbol, fontSize = 21.sp, fontWeight = FontWeight.SemiBold) },
                                    label = { Text(tab.title, fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                when {
                    showProduction && verifiedTenantContext != null -> NativeProductionScreen(tenant = verifiedTenantContext!!, onBack = { showProduction = false }, modifier = Modifier.padding(innerPadding))
                    showTableManagement && verifiedTenantContext != null -> NativeTableManagementScreen(tenant = verifiedTenantContext!!, onBack = { showTableManagement = false }, modifier = Modifier.padding(innerPadding))
                    showNewTransaction -> if (verifiedTenantContext != null) {
                        NativeCheckoutScreen(
                            tenant = verifiedTenantContext!!,
                            onBack = { showNewTransaction = false },
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        CloudAccountScreen(
                            modifier = Modifier.padding(innerPadding),
                            onContextChanged = { verifiedTenantContext = it }
                        )
                    }
                    selectedTab == 0 -> if (verifiedTenantContext != null) {
                        NativeDashboardScreen(
                            tenant = verifiedTenantContext!!,
                            modifier = Modifier.padding(innerPadding),
                            onNewTransaction = { showNewTransaction = true },
                            onOpenTab = { selectedTab = it },
                            onOpenTables = { showTableManagement = true }
                        )
                    } else {
                        CloudAccountScreen(
                            modifier = Modifier.padding(innerPadding),
                            onContextChanged = { verifiedTenantContext = it }
                        )
                    }
                    selectedTab == 1 -> if (verifiedTenantContext != null) {
                        NativeProductsScreen(
                            tenant = verifiedTenantContext!!,
                            modifier = Modifier.padding(innerPadding),
                            onOpenProduction = { showProduction = true }
                        )
                    } else {
                        CloudAccountScreen(
                            modifier = Modifier.padding(innerPadding),
                            onContextChanged = { verifiedTenantContext = it }
                        )
                    }
                    selectedTab == 2 -> if (verifiedTenantContext != null) {
                        NativeOrdersScreen(
                            tenant = verifiedTenantContext!!,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        CloudAccountScreen(
                            modifier = Modifier.padding(innerPadding),
                            onContextChanged = { verifiedTenantContext = it }
                        )
                    }
                    selectedTab == 3 -> if (verifiedTenantContext != null) {
                        if (verifiedTenantContext!!.role == com.artemis.kasirku.nativeapp.data.StoreRole.CASHIER) {
                            SimpleFeatureScreen(
                                title = "Laporan terbatas",
                                description = "Laporan penjualan hanya dapat diakses owner, admin, dan supervisor.",
                                modifier = Modifier.padding(innerPadding)
                            )
                        } else NativeReportsScreen(
                            tenant = verifiedTenantContext!!,
                            modifier = Modifier.padding(innerPadding)
                        )
                    } else {
                        CloudAccountScreen(
                            modifier = Modifier.padding(innerPadding),
                            onContextChanged = { verifiedTenantContext = it }
                        )
                    }
                    else -> CloudAccountScreen(
                        modifier = Modifier.padding(innerPadding),
                        onContextChanged = { verifiedTenantContext = it }
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(
    modifier: Modifier = Modifier,
    onNewTransaction: () -> Unit,
    onOpenTab: (Int) -> Unit
) {
    val context = LocalContext.current
    val dashboardProducts = remember { mutableStateListOf<KasirkuProduct>() }

    LaunchedEffect(Unit) {
        val database = ProductDatabase(context.applicationContext)
        dashboardProducts.clear()
        dashboardProducts.addAll(database.getAllProducts())
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp).padding(top = 18.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("KASIRKU", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(3.dp))
                Text("Selamat datang!", color = Navy, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Ringkasan tokomu hari ini", color = Muted, fontSize = 13.sp)
            }
            Box(modifier = Modifier.size(46.dp).background(Color(0xFFE5F1F0), CircleShape), contentAlignment = Alignment.Center) {
                Text("T", color = Teal, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
        SalesSummaryCard()
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Transaksi", "0", "Hari ini", "▤", Modifier.weight(1f))
            MetricCard("Produk", dashboardProducts.size.toString(), "Terdaftar", "▦", Modifier.weight(1f))
        }
        Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
            SectionHeading("Menu cepat", "Akses fitur utama")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("+", "Transaksi baru", "Mulai penjualan", Modifier.weight(1f), true, onNewTransaction)
                QuickAction("▦", "Produk", "Kelola barang", Modifier.weight(1f), onClick = { onOpenTab(1) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction("▤", "Pesanan", "Lihat pesanan", Modifier.weight(1f), onClick = { onOpenTab(2) })
                QuickAction("▥", "Laporan", "Lihat penjualan", Modifier.weight(1f), onClick = { onOpenTab(3) })
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionHeading("Aktivitas terbaru", "Transaksi terakhir")
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(modifier = Modifier.size(48.dp).background(Canvas, CircleShape), contentAlignment = Alignment.Center) {
                        Text("▤", color = Muted, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Belum ada transaksi", color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("Transaksi yang kamu buat akan muncul di sini.", color = Muted, fontSize = 12.sp)
                }
            }
        }
        Text("Kasirku Native • Versi 0.4.1", color = Muted, fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun SalesSummaryCard() {
    Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PENJUALAN HARI INI", color = Color(0xFFD3DFE9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text("HARI INI", color = Color(0xFF8DE0C9), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Text("Rp0", color = Color.White, fontSize = 31.sp, fontWeight = FontWeight.Bold)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF3A5268)))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Uang masuk", color = Color(0xFFD3DFE9), fontSize = 11.sp)
                    Text("Rp0", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Produk terjual", color = Color(0xFFD3DFE9), fontSize = 11.sp)
                    Text("0 item", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, subtitle: String, symbol: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(symbol, color = Teal, fontSize = 20.sp)
            Text(value, color = Navy, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SectionHeading(title: String, subtitle: String) {
    Column {
        Text(title, color = Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun QuickAction(
    symbol: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    emphasized: Boolean = false,
    onClick: () -> Unit
) {
    val background = if (emphasized) Color(0xFFE8F4F2) else Color.White
    Card(modifier = modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = background), shape = RoundedCornerShape(18.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(symbol, color = if (emphasized) Teal else Navy, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ProductsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val database = remember { ProductDatabase(context.applicationContext) }
    val products = remember { mutableStateListOf<KasirkuProduct>() }
    var search by remember { mutableStateOf("") }
    var editingProduct by remember { mutableStateOf<KasirkuProduct?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<KasirkuProduct?>(null) }

    fun refreshProducts() {
        products.clear()
        products.addAll(database.getAllProducts())
    }

    LaunchedEffect(Unit) { refreshProducts() }

    val filteredProducts = products.filter {
        it.name.contains(search, ignoreCase = true) ||
            it.category.contains(search, ignoreCase = true) ||
            it.barcode.contains(search, ignoreCase = true)
    }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp).padding(top = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(15.dp)
    ) {
        Column {
            Text("KELOLA TOKO", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Produk", color = Navy, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text("Atur barang, harga, dan persediaan tokomu.", color = Muted, fontSize = 13.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProductStatCard("Total produk", products.size.toString(), Modifier.weight(1f))
            ProductStatCard("Total stok", formatNumber(products.sumOf { it.stock }.toLong()), Modifier.weight(1f))
        }

        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text("Cari nama, kategori, barcode...") },
            leadingIcon = { Text("⌕", color = Muted, fontSize = 22.sp) },
            shape = RoundedCornerShape(14.dp)
        )

        Button(
            onClick = { editingProduct = null; showForm = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp)
        ) {
            Text("+  Tambah produk", modifier = Modifier.padding(vertical = 4.dp), fontWeight = FontWeight.Bold)
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Daftar produk", color = Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("${filteredProducts.size} barang", color = Muted, fontSize = 12.sp)
        }

        if (filteredProducts.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp)) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("▦", color = Teal, fontSize = 34.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(if (search.isBlank()) "Belum ada produk" else "Produk tidak ditemukan", color = Navy, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (search.isBlank()) "Tambahkan barang pertama tokomu." else "Coba kata pencarian yang lain.",
                        color = Muted,
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            filteredProducts.forEach { product ->
                ProductCard(
                    product = product,
                    onEdit = { editingProduct = product; showForm = true },
                    onDelete = { deleteTarget = product }
                )
            }
        }
    }

    if (showForm) {
        ProductFormDialog(
            product = editingProduct,
            onDismiss = { showForm = false },
            onSave = { product ->
                database.saveProduct(product)
                refreshProducts()
                showForm = false
            }
        )
    }

    deleteTarget?.let { product ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Hapus produk?") },
            text = { Text("Produk \"${product.name}\" akan dihapus dari perangkat ini.") },
            confirmButton = {
                TextButton(onClick = {
                    database.deleteProduct(product.id)
                    refreshProducts()
                    deleteTarget = null
                }) { Text("Hapus", color = Color(0xFFB42318)) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Batal") } }
        )
    }
}

@Composable
private fun ProductStatCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier = modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, color = Navy, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ProductCard(product: KasirkuProduct, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(17.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ProductPhoto(product.photoUri, Modifier.size(66.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(product.name, color = Navy, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(product.category.ifBlank { "Tanpa kategori" }, color = Muted, fontSize = 11.sp)
                Text("Rp ${formatNumber(product.sellingPrice)}", color = Teal, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text("Stok: ${formatNumber(product.stock.toLong())}", color = Muted, fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.End) {
                TextButton(onClick = onEdit) { Text("Edit") }
                TextButton(onClick = onDelete) { Text("Hapus", color = Color(0xFFB42318)) }
            }
        }
    }
}

@Composable
private fun ProductPhoto(uriString: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(uriString) {
        if (uriString.isBlank()) null else runCatching {
            context.contentResolver.openInputStream(Uri.parse(uriString))?.use {
                BitmapFactory.decodeStream(it)?.asImageBitmap()
            }
        }.getOrNull()
    }
    Box(modifier = modifier.background(Color(0xFFEAF1F3), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = "Foto produk", modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Text("▦", color = Teal, fontSize = 25.sp)
        }
    }
}

@Composable
private fun ProductFormDialog(
    product: KasirkuProduct?,
    onDismiss: () -> Unit,
    onSave: (KasirkuProduct) -> Unit
) {
    val context = LocalContext.current
    var name by remember(product?.id) { mutableStateOf(product?.name ?: "") }
    var category by remember(product?.id) { mutableStateOf(product?.category ?: "") }
    var costPrice by remember(product?.id) { mutableStateOf(product?.costPrice?.toString() ?: "") }
    var sellingPrice by remember(product?.id) { mutableStateOf(product?.sellingPrice?.toString() ?: "") }
    var stock by remember(product?.id) { mutableStateOf(product?.stock?.toString() ?: "0") }
    var barcode by remember(product?.id) { mutableStateOf(product?.barcode ?: "") }
    var photoUri by remember(product?.id) { mutableStateOf(product?.photoUri ?: "") }
    var error by remember(product?.id) { mutableStateOf("") }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            photoUri = uri.toString()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (product == null) "Tambah produk" else "Edit produk", color = Navy, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().height(470.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ProductPhoto(photoUri, Modifier.size(58.dp))
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = { photoPicker.launch(arrayOf("image/*")) }) {
                        Text(if (photoUri.isBlank()) "Pilih foto" else "Ganti foto")
                    }
                    if (photoUri.isNotBlank()) {
                        TextButton(onClick = { photoUri = "" }) { Text("Hapus") }
                    }
                }
                OutlinedTextField(value = name, onValueChange = { name = it; error = "" }, label = { Text("Nama produk *") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Kategori") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = costPrice, onValueChange = { costPrice = formatDigitsWithDots(it) }, label = { Text("Harga modal (Rp, opsional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = sellingPrice, onValueChange = { sellingPrice = formatDigitsWithDots(it) }, label = { Text("Harga jual (Rp) *") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = stock, onValueChange = { stock = formatDigitsWithDots(it) }, label = { Text("Stok") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = barcode, onValueChange = { barcode = it }, label = { Text("Barcode / kode produk") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (error.isNotBlank()) Text(error, color = Color(0xFFB42318), fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                val parsedPrice = parseFormattedNumber(sellingPrice)
                if (name.isBlank()) {
                    error = "Nama produk wajib diisi."
                } else if (parsedPrice == null || parsedPrice <= 0L) {
                    error = "Harga jual harus lebih dari 0."
                } else if ((parseFormattedNumber(stock)?.takeIf { it <= Int.MAX_VALUE }?.toInt() ?: -1) < 0) {
                    error = "Stok tidak valid."
                } else {
                    onSave(
                        KasirkuProduct(
                            id = product?.id ?: 0L,
                            name = name.trim(),
                            category = category.trim(),
                            costPrice = parseFormattedNumber(costPrice) ?: 0L,
                            sellingPrice = parsedPrice,
                            stock = parseFormattedNumber(stock)?.toInt() ?: 0,
                            barcode = barcode.trim(),
                            photoUri = photoUri
                        )
                    )
                }
            }) { Text("Simpan") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}

@Composable
private fun SimpleFeatureScreen(
    title: String,
    description: String,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("KASIRKU", color = Teal, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
        Spacer(Modifier.height(12.dp))
        Text(title, color = Navy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(description, color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(22.dp))
        if (onBack != null) {
            OutlinedButton(onClick = onBack) { Text("Kembali ke dashboard") }
        } else {
            Text("Fitur ini akan dikembangkan pada tahap berikutnya.", color = Muted, fontSize = 12.sp)
        }
    }
}
