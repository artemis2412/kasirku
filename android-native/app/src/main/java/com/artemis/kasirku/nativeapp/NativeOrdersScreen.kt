package com.artemis.kasirku.nativeapp

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemis.kasirku.nativeapp.data.KasirkuRoomDatabase
import com.artemis.kasirku.nativeapp.data.SaleEntity
import com.artemis.kasirku.nativeapp.data.SaleItemEntity
import com.artemis.kasirku.nativeapp.data.SaleSyncOutboxEntity
import com.artemis.kasirku.nativeapp.data.SalesRepository
import com.artemis.kasirku.nativeapp.data.VerifiedTenantContext
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch


private fun formatOrderNominal(value: Long): String = value.toString().reversed().chunked(3).joinToString(".").reversed()

private fun csvCell(value: String): String {
    val guarded = if (value.firstOrNull() in listOf('=', '+', '-', '@', '\\t', '\\r')) "'$value" else value
    val safe = guarded.replace("\"", "\"\"")
    return if (safe.contains(';') || safe.contains('"') || safe.contains('\n')) "\"$safe\"" else safe
}

private val OrdersNavy = Color(0xFF17324D)
private val OrdersTeal = Color(0xFF168C83)
private val OrdersMuted = Color(0xFF718096)

@Composable
internal fun NativeOrdersScreen(
    tenant: VerifiedTenantContext,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val dao = remember { KasirkuRoomDatabase.getInstance(context).catalogDao() }
    val repository = remember { SalesRepository(dao) }
    val pendingSync by dao.observePendingSaleSyncCount(tenant.storeId, tenant.branchId).collectAsState(initial = 0)
    val syncQueue by dao.observeSaleSyncQueue(tenant.storeId, tenant.branchId).collectAsState(initial = emptyList())
    val now = remember { System.currentTimeMillis() }
    val dayStart = remember {
        Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val dayEnd = remember {
        Calendar.getInstance().apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_YEAR, 1)
        }.timeInMillis
    }
    val sales by remember(tenant.storeId, tenant.branchId, dayStart, dayEnd) {
        repository.observeSales(tenant, dayStart, dayEnd)
    }.collectAsState(initial = emptyList())
    val count by remember(tenant.storeId, tenant.branchId, dayStart, dayEnd) {
        repository.observeSaleCount(tenant, dayStart, dayEnd)
    }.collectAsState(initial = 0)
    val total by remember(tenant.storeId, tenant.branchId, dayStart, dayEnd) {
        repository.observeSalesTotal(tenant, dayStart, dayEnd)
    }.collectAsState(initial = 0L)
    val scope = rememberCoroutineScope()
    var selectedSale by remember { mutableStateOf<SaleEntity?>(null) }
    var selectedItems by remember { mutableStateOf<List<SaleItemEntity>>(emptyList()) }
    var loadingDetail by remember { mutableStateOf(false) }
    var archiveMessage by remember { mutableStateOf<String?>(null) }
    var archiveBusy by remember { mutableStateOf(false) }
    val archiveStart = remember {
        Calendar.getInstance().apply {
            timeInMillis = dayStart
            add(Calendar.DAY_OF_YEAR, -29)
        }.timeInMillis
    }
    val archiveEnd = dayEnd
    val archiveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                archiveBusy = true
                archiveMessage = null
                runCatching {
                    val rows = repository.exportRows(tenant, archiveStart, archiveEnd)
                    context.contentResolver.openOutputStream(uri)?.use { stream ->
                        OutputStreamWriter(stream, Charsets.UTF_8).use { writer ->
                            writer.write("\uFEFFTanggal;Jam;ID Transaksi;Metode;Produk;Jumlah;Harga Satuan;Total Baris;Subtotal;Diskon;Total;Dibayar;Kembalian\n")
                            val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                            val timeFmt = SimpleDateFormat("HH:mm:ss", Locale.US)
                            rows.forEach { (sale, items) ->
                                items.forEach { item ->
                                    val values = listOf(
                                        dateFmt.format(Date(sale.createdAt)), timeFmt.format(Date(sale.createdAt)), sale.id,
                                        sale.paymentMethod, item.productName, item.quantity.toString(), item.unitPrice.toString(),
                                        item.lineTotal.toString(), sale.subtotal.toString(), sale.discount.toString(),
                                        sale.total.toString(), sale.paid.toString(), sale.change.toString()
                                    )
                                    writer.write(values.joinToString(";") { csvCell(it) } + "\n")
                                }
                            }
                            writer.flush()
                        }
                    } ?: error("File tidak dapat dibuat di lokasi pilihan.")
                    rows.size
                }.onSuccess { size -> archiveMessage = "Arsip berhasil disimpan: $size transaksi (30 hari)." }
                    .onFailure { archiveMessage = it.message ?: "Gagal membuat arsip." }
                archiveBusy = false
            }
        }
    }
    val dateFormatter = remember { SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")) }
    val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale("id", "ID")) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)
    ) {
        Text("RIWAYAT PENJUALAN", color = OrdersTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text("Pesanan", color = OrdersNavy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(tenant.storeName + " • " + tenant.branchName, color = OrdersMuted, fontSize = 12.sp)
        Text("Hari ini • ${dateFormatter.format(Date(dayStart))}", color = OrdersMuted, fontSize = 12.sp)
        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF1F3)), shape = RoundedCornerShape(13.dp)) {
            Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Menunggu sinkronisasi cloud", color = OrdersNavy, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Text("Transaksi lokal tetap tersimpan meski internet terputus.", color = OrdersMuted, fontSize = 10.sp)
                }
                Text(pendingSync.toString(), color = OrdersTeal, fontWeight = FontWeight.Bold, fontSize = 19.sp)
            }
        }
        if (syncQueue.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(13.dp)) {
                Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    Text("Rincian antrean terakhir", color = OrdersNavy, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    syncQueue.forEach { item ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(item.saleId.take(8).uppercase(), color = OrdersNavy, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text(if (item.status == "FAILED") "Gagal" else "Menunggu", color = if (item.status == "FAILED") Color(0xFFB45309) else OrdersTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Text("Percobaan: ${item.attempts} • ${dateFormatter.format(Date(item.createdAt))}", color = OrdersMuted, fontSize = 10.sp)
                            item.lastError?.takeIf { it.isNotBlank() }?.let { Text(it, color = Color(0xFFB45309), fontSize = 10.sp) }
                        }
                    }
                }
            }
        }
        Button(
            onClick = { archiveLauncher.launch("Kasirku_Arsip_30_Hari_${SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(dayStart))}.csv") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !archiveBusy,
            shape = RoundedCornerShape(12.dp)
        ) { Text(if (archiveBusy) "Menyiapkan arsip…" else "Unduh arsip 30 hari") }
        Text("Arsip mencakup 30 hari terakhir sampai hari ini. Data tetap tersimpan di aplikasi.", color = OrdersMuted, fontSize = 11.sp)
        archiveMessage?.let { Text(it, color = OrdersTeal, fontSize = 12.sp) }

        Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Transaksi", color = OrdersMuted, fontSize = 11.sp)
                    Text(count.toString(), color = OrdersNavy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                }
            }
            Card(modifier = Modifier.weight(1f), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Total penjualan", color = OrdersMuted, fontSize = 11.sp)
                    Text("Rp ${formatOrderNominal(total)}", color = OrdersTeal, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        if (sales.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Belum ada transaksi hari ini", color = OrdersNavy, fontWeight = FontWeight.SemiBold)
                    Text("Transaksi yang selesai akan tercatat otomatis di halaman ini.", color = OrdersMuted, fontSize = 12.sp)
                }
            }
        } else {
            Text("${sales.size} transaksi tercatat", color = OrdersMuted, fontSize = 12.sp)
            sales.forEach { sale ->
                Card(
                    modifier = Modifier.fillMaxWidth().clickable {
                        selectedSale = sale
                        loadingDetail = true
                        scope.launch {
                            selectedItems = repository.saleItems(tenant, sale.id)
                            loadingDetail = false
                        }
                    },
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Transaksi ${sale.id.take(8).uppercase()}", color = OrdersNavy, fontWeight = FontWeight.SemiBold)
                            Text(timeFormatter.format(Date(sale.createdAt)) + " • ${sale.paymentMethod}", color = OrdersMuted, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text("Rp ${formatOrderNominal(sale.total)}", color = OrdersTeal, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Text("Riwayat lama tetap tersimpan di perangkat. Tampilan ini difokuskan pada transaksi hari ini.", color = OrdersMuted, fontSize = 11.sp)
    }

    selectedSale?.let { sale ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { selectedSale = null },
            title = { Text("Detail transaksi", color = OrdersNavy, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    Text("ID: ${sale.id}", color = OrdersMuted, fontSize = 10.sp)
                    Text(dateFormatter.format(Date(sale.createdAt)) + " • " + timeFormatter.format(Date(sale.createdAt)), color = OrdersMuted, fontSize = 11.sp)
                    if (loadingDetail) Text("Memuat rincian…", color = OrdersMuted)
                    selectedItems.forEach { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${item.productName} × ${item.quantity}", color = OrdersNavy, modifier = Modifier.weight(1f))
                            Text("Rp ${formatOrderNominal(item.lineTotal)}", color = OrdersNavy, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text("Subtotal: Rp ${formatOrderNominal(sale.subtotal)}", color = OrdersMuted, fontSize = 12.sp)
                    Text("Diskon: Rp ${formatOrderNominal(sale.discount)}", color = OrdersMuted, fontSize = 12.sp)
                    Text("Total: Rp ${formatOrderNominal(sale.total)}", color = OrdersTeal, fontWeight = FontWeight.Bold)
                    Text("Dibayar: Rp ${formatOrderNominal(sale.paid)} • Kembalian: Rp ${formatOrderNominal(sale.change)}", color = OrdersMuted, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { selectedSale = null }) { Text("Tutup") } }
        )
    }
}
