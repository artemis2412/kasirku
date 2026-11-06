package com.artemis.kasirku.nativeapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemis.kasirku.nativeapp.data.CatalogRepository
import com.artemis.kasirku.nativeapp.data.CheckoutLine
import com.artemis.kasirku.nativeapp.data.KasirkuRoomDatabase
import com.artemis.kasirku.nativeapp.data.SalesRepository
import com.artemis.kasirku.nativeapp.data.StoreTableEntity
import com.artemis.kasirku.nativeapp.data.SaleSyncWorker
import com.artemis.kasirku.nativeapp.data.VerifiedTenantContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun formatCheckoutNominal(value: Long): String = value.toString().reversed().chunked(3).joinToString(".").reversed()

private fun formatCheckoutNominalInput(value: String): String =
    value.filter(Char::isDigit).reversed().chunked(3).joinToString(".").reversed()

private data class ReceiptDraft(val saleId: String, val lines: List<CheckoutLine>, val subtotal: Long, val total: Long, val paid: Long, val change: Long, val orderType: String, val tableLabel: String?)

private data class CartEntry(
    val id: String,
    val name: String,
    val price: Long,
    val available: Int,
    val quantity: Int
)

@Composable
internal fun NativeCheckoutScreen(
    tenant: VerifiedTenantContext,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val database = remember { KasirkuRoomDatabase.getInstance(context) }
    val catalog = remember { CatalogRepository(database.catalogDao()) }
    val sales = remember { SalesRepository(database.catalogDao()) }
    val tableDao = remember { database.storeTableDao() }
    val tables by tableDao.observeActiveTables(tenant.storeId, tenant.branchId).collectAsState(initial = emptyList())
    var search by remember { mutableStateOf("") }
    val products by remember(tenant.storeId, tenant.branchId, search) {
        catalog.observeBranchCatalog(tenant.storeId, tenant.branchId, search)
    }.collectAsState(initial = emptyList())
    val cart = remember { mutableStateMapOf<String, CartEntry>() }
    val scope = rememberCoroutineScope()
    var payment by remember { mutableStateOf("") }
    var orderType by remember { mutableStateOf("Takeaway") }
    var selectedTableId by remember { mutableStateOf<String?>(null) }
    var paymentMethod by remember { mutableStateOf("CASH") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    var printerError by remember { mutableStateOf<String?>(null) }
    var printerList by remember { mutableStateOf<List<PairedPrinter>>(emptyList()) }
    var selectedPrinter by remember { mutableStateOf(BluetoothReceiptPrinter.selectedAddress(context)) }
    var receiptDraft by remember { mutableStateOf<ReceiptDraft?>(null) }
    var printing by remember { mutableStateOf(false) }
    var showPrinterSettings by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) printerList = BluetoothReceiptPrinter.pairedPrinters(context)
        else printerError = "Izin Bluetooth diperlukan untuk membaca printer yang sudah dipasangkan."
    }
    fun loadPrinters() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
        ) permissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        else printerList = BluetoothReceiptPrinter.pairedPrinters(context)
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        ) printerList = BluetoothReceiptPrinter.pairedPrinters(context)
    }
    val subtotal = runCatching { cart.values.fold(0L) { total, item -> Math.addExact(total, Math.multiplyExact(item.price, item.quantity.toLong())) } }.getOrElse { Long.MAX_VALUE }
    val paid = if (paymentMethod == "CASH") payment.filter(Char::isDigit).toLongOrNull() ?: 0L else subtotal

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("TRANSAKSI BARU", color = Color(0xFF168C83), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Penjualan", color = Color(0xFF17324D), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(tenant.storeName + " • " + tenant.branchName, color = Color(0xFF718096), fontSize = 12.sp)
            }
            TextButton(onClick = onBack) { Text("Kembali") }
        }
        Text("Jenis pesanan", color = Color(0xFF17324D), fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = orderType == "Takeaway", onClick = { orderType = "Takeaway"; selectedTableId = null }, label = { Text("Takeaway") })
            FilterChip(selected = orderType == "Dine In", onClick = { orderType = "Dine In" }, label = { Text("Dine In") })
        }
        if (orderType == "Dine In") {
            Text("Pilih meja", color = Color(0xFF17324D), fontWeight = FontWeight.Bold)
            if (tables.isEmpty()) Text("Belum ada meja aktif di cabang ini. Tambahkan meja melalui Manajemen Meja.", color = Color(0xFFB42318), fontSize = 12.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                tables.forEach { table ->
                    FilterChip(selected = selectedTableId == table.id, onClick = { selectedTableId = table.id }, label = { Text(table.name + " · " + table.capacity) })
                }
            }
        }
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Cari nama produk atau barcode") }, singleLine = true)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Printer nota", color = Color(0xFF17324D), fontWeight = FontWeight.Bold)
                Text(selectedPrinter?.let { address -> printerList.firstOrNull { it.address == address }?.name ?: address } ?: "Belum ada printer dipilih", color = Color(0xFF718096), fontSize = 12.sp)
            }
            TextButton(onClick = { if (!showPrinterSettings) loadPrinters(); showPrinterSettings = !showPrinterSettings }) {
                Text(if (showPrinterSettings) "Tutup" else "Atur")
            }
        }
        if (showPrinterSettings) {
            OutlinedButton(onClick = { loadPrinters(); printerError = null }, modifier = Modifier.fillMaxWidth()) { Text("Muat ulang printer terpasang") }
            if (printerList.isEmpty()) Text("Belum ada printer terpasang. Pasangkan printer dari Pengaturan Bluetooth HP.", color = Color(0xFF718096), fontSize = 11.sp)
            printerList.forEach { printer ->
                OutlinedButton(onClick = {
                    selectedPrinter = printer.address
                    BluetoothReceiptPrinter.saveSelectedAddress(context, printer.address)
                    printerError = null
                }, modifier = Modifier.fillMaxWidth(), enabled = selectedPrinter != printer.address) {
                    Text((if (selectedPrinter == printer.address) "✓ " else "") + printer.name + " • " + printer.address)
                }
            }
            OutlinedButton(
                onClick = {
                    val address = selectedPrinter
                    if (address == null) printerError = "Pilih printer terlebih dahulu."
                    else scope.launch {
                        printing = true
                        printerError = null
                        try {
                            BluetoothReceiptPrinter.print(context, address, tenant.storeName, tenant.branchName,
                                "TES-PRINTER", listOf(CheckoutLine("test", "Tes cetak Kasirku", 1, 1000L)),
                                1000L, 1000L, 2000L, 1000L)
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (failure: Exception) {
                            printerError = failure.message ?: "Tes printer gagal."
                        } finally {
                            printing = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !printing && selectedPrinter != null
            ) { Text(if (printing) "Mencetak tes…" else "Cetak tes printer") }
        }
        printerError?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
        Text("Pilih produk", color = Color(0xFF17324D), fontWeight = FontWeight.Bold)
        products.filter { it.activeSellingPrice != null }.forEach { product ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(13.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(product.name, color = Color(0xFF17324D), fontWeight = FontWeight.SemiBold)
                        Text("Rp " + formatCheckoutNominal(product.activeSellingPrice ?: 0L) + " • Stok " + product.branchQuantity, color = Color(0xFF718096), fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val old = cart[product.id]
                            val current = old?.quantity ?: 0
                            if (current < product.branchQuantity) {
                                cart[product.id] = CartEntry(product.id, product.name, product.activeSellingPrice ?: 0L, product.branchQuantity, current + 1)
                                error = null
                            } else error = "Stok produk tidak mencukupi."
                        },
                        enabled = product.branchQuantity > (cart[product.id]?.quantity ?: 0)
                    ) { Text("+") }
                }
            }
        }
        Text("Keranjang • ${cart.values.sumOf { it.quantity.toLong() }} item", color = Color(0xFF17324D), fontWeight = FontWeight.Bold)
        if (cart.isEmpty()) {
            Text("Pilih produk untuk memulai transaksi.", color = Color(0xFF718096), fontSize = 12.sp)
        } else {
            cart.values.toList().forEach { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, color = Color(0xFF17324D), fontWeight = FontWeight.SemiBold)
                            Text("Rp ${formatCheckoutNominal(item.price)} × ${item.quantity} = " + runCatching { "Rp ${formatCheckoutNominal(Math.multiplyExact(item.price, item.quantity.toLong()))}" }.getOrDefault("Nilai terlalu besar"), color = Color(0xFF718096), fontSize = 12.sp)
                        }
                        Row {
                            TextButton(onClick = {
                                if (item.quantity <= 1) cart.remove(item.id)
                                else cart[item.id] = item.copy(quantity = item.quantity - 1)
                            }) { Text("−") }
                            TextButton(onClick = {
                                if (item.quantity < item.available) cart[item.id] = item.copy(quantity = item.quantity + 1)
                                else error = "Stok produk tidak mencukupi."
                            }) { Text("+") }
                        }
                    }
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F4F1)), shape = RoundedCornerShape(15.dp)) {
            Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Subtotal", color = Color(0xFF17324D))
                    Text("Rp ${formatCheckoutNominal(subtotal)}", color = Color(0xFF17324D), fontWeight = FontWeight.Bold)
                }
                Text("Metode pembayaran", color = Color(0xFF17324D), fontWeight = FontWeight.SemiBold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("CASH" to "Tunai", "QRIS" to "QRIS", "TRANSFER" to "Transfer").forEach { (method, label) ->
                        FilterChip(
                            selected = paymentMethod == method,
                            onClick = { paymentMethod = method; payment = "" },
                            label = { Text(label) }
                        )
                    }
                }
                if (paymentMethod == "CASH") {
                    OutlinedTextField(
                        value = payment,
                        onValueChange = { payment = formatCheckoutNominalInput(it) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Uang diterima (Rp)") },
                        singleLine = true
                    )
                } else {
                    Text("Pembayaran $paymentMethod dicatat sesuai total transaksi. Pastikan pembayaran sudah diterima.", color = Color(0xFF718096), fontSize = 12.sp)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kembalian", color = Color(0xFF17324D))
                    Text("Rp ${formatCheckoutNominal((paid - subtotal).coerceAtLeast(0L))}", color = Color(0xFF168C83), fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            val receiptLines = cart.values.map { CheckoutLine(it.id, it.name, it.quantity, it.price) }
                            try {
                                val chosenTable = tables.firstOrNull { it.id == selectedTableId }\n                                val result = sales.checkout(tenant, receiptLines, paid, paymentMethod = paymentMethod, orderType = orderType, tableLocalId = chosenTable?.id, tableLabel = chosenTable?.name)
                                if (!tenant.branchTimeZoneId.isNullOrBlank()) {
                                    SaleSyncWorker.enqueue(context, tenant)
                                }
                                receiptDraft = ReceiptDraft(result.saleId, receiptLines, subtotal, result.total, paid, result.change, orderType, chosenTable?.name)
                                success = "Transaksi tersimpan. Total Rp ${result.total}, kembalian Rp ${result.change}."
                                cart.clear()
                                payment = ""
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (failure: Exception) {
                                error = failure.message ?: "Transaksi gagal disimpan."
                            } finally {
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && cart.isNotEmpty() && paid >= subtotal && subtotal > 0L && (orderType == "Takeaway" || tables.any { it.id == selectedTableId }),
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (busy) "Memproses…" else "Selesaikan transaksi") }
            }
        }
        error?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
        success?.let { Text(it, color = Color(0xFF168C83), fontSize = 12.sp) }
        receiptDraft?.let { receipt ->
            OutlinedButton(onClick = {
                val address = selectedPrinter
                if (address == null) printerError = "Pilih printer Bluetooth terlebih dahulu."
                else scope.launch {
                    printing = true
                    printerError = null
                    try {
                        BluetoothReceiptPrinter.print(context, address, tenant.storeName, tenant.branchName,
                            receipt.saleId, receipt.lines, receipt.subtotal, receipt.total, receipt.paid, receipt.change, orderType = receipt.orderType, tableLabel = receipt.tableLabel)
                        success = "Nota berhasil dikirim ke printer."
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (failure: Exception) {
                        printerError = "Transaksi berhasil tersimpan, tetapi nota belum tercetak. " +
                            (failure.message ?: "Periksa printer lalu coba cetak ulang.")
                    } finally {
                        printing = false
                    }
                }
            }, modifier = Modifier.fillMaxWidth(), enabled = !printing) {
                Text(if (printing) "Mengirim nota…" else "Cetak ulang nota Bluetooth")
            }
        }
        printerError?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
        Text("Transaksi tersimpan lokal. Jika printer gagal, nota dapat dicetak ulang tanpa membuat transaksi baru.", color = Color(0xFF718096), fontSize = 11.sp)
    }
}
