package com.artemis.kasirku.nativeapp

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemis.kasirku.nativeapp.data.KasirkuRoomDatabase
import com.artemis.kasirku.nativeapp.data.BranchTime
import com.artemis.kasirku.nativeapp.data.SalesRepository
import com.artemis.kasirku.nativeapp.data.VerifiedTenantContext
import java.io.OutputStreamWriter
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

private val ReportNavy = Color(0xFF17324D)
private val ReportTeal = Color(0xFF168C83)
private val ReportMuted = Color(0xFF718096)
private fun reportMoney(value: Long) = value.toString().reversed().chunked(3).joinToString(".").reversed()
private fun reportCsvCell(value: String): String {
    val firstMeaningful = value.trimStart().firstOrNull()
    val guarded = if (firstMeaningful in listOf('=', '+', '-', '@', '\t', '\r')) "'$value" else value
    val escaped = guarded.replace("\"", "\"\"")
    return if (escaped.contains(';') || escaped.contains('\"') || escaped.contains('\n')) "\"$escaped\"" else escaped
}

@Composable
internal fun NativeReportsScreen(tenant: VerifiedTenantContext, modifier: Modifier = Modifier) {
    val context = LocalContext.current.applicationContext
    val repository = remember { SalesRepository(KasirkuRoomDatabase.getInstance(context).catalogDao()) }
    var period by remember { mutableIntStateOf(7) }
    val scope = rememberCoroutineScope()
    var exporting by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val branchZone = remember(tenant.branchTimeZoneId) { BranchTime.zoneId(tenant) }
    var endAt by remember(branchZone) {
        mutableLongStateOf(BranchTime.startOfDayOffset(BranchTime.startOfToday(branchZone), 1, branchZone))
    }
    LaunchedEffect(branchZone) {
        while (true) {
            val todayStart = BranchTime.startOfToday(branchZone)
            val nextDayStart = BranchTime.startOfDayOffset(todayStart, 1, branchZone)
            if (endAt != nextDayStart) endAt = nextDayStart
            delay((nextDayStart - System.currentTimeMillis()).coerceAtLeast(1_000L))
        }
    }
    val startAt = remember(period, endAt, branchZone) {
        BranchTime.startOfDayOffset(endAt, -period.toLong(), branchZone)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri: Uri? ->
        if (uri != null) scope.launch {
            exporting = true
            exportMessage = null
            runCatching {
                val rows = repository.exportRows(tenant, startAt, endAt)
                context.contentResolver.openOutputStream(uri)?.use { output ->
                    OutputStreamWriter(output, Charsets.UTF_8).use { writer ->
                        writer.write("\uFEFFTanggal;Jam;ID Transaksi;Kasir;Metode;Produk;Jumlah;Harga Satuan;Total Baris;Subtotal;Diskon;Total;Dibayar;Kembalian\n")
                        rows.forEach { (sale, items) -> items.forEach { item ->
                            val values = listOf(BranchTime.format(sale.createdAt, "yyyy-MM-dd", branchZone), BranchTime.format(sale.createdAt, "HH:mm:ss", branchZone), sale.id, sale.cashierUserId, sale.paymentMethod, item.productName, item.quantity.toString(), item.unitPrice.toString(), item.lineTotal.toString(), sale.subtotal.toString(), sale.discount.toString(), sale.total.toString(), sale.paid.toString(), sale.change.toString())
                            writer.write(values.joinToString(";") { reportCsvCell(it) } + "\n")
                        } }
                        writer.flush()
                    }
                } ?: error("Tidak dapat membuat file di lokasi pilihan.")
                rows.size
            }.onSuccess { exportMessage = "Laporan berhasil diekspor: $it transaksi." }
                .onFailure { exportMessage = it.message ?: "Gagal mengekspor laporan." }
            exporting = false
        }
    }

    val sales by remember(tenant.storeId, tenant.branchId, startAt, endAt) { repository.observeSales(tenant, startAt, endAt) }.collectAsState(initial = emptyList())
    val count by remember(tenant.storeId, tenant.branchId, startAt, endAt) { repository.observeSaleCount(tenant, startAt, endAt) }.collectAsState(initial = 0)
    val total by remember(tenant.storeId, tenant.branchId, startAt, endAt) { repository.observeSalesTotal(tenant, startAt, endAt) }.collectAsState(initial = 0L)

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("ANALISIS TOKO", color = ReportTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text("Laporan penjualan", color = ReportNavy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(tenant.storeName + " • " + tenant.branchName, color = ReportMuted, fontSize = 12.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { period = 7 }, shape = RoundedCornerShape(12.dp)) { Text(if (period == 7) "✓ 7 hari" else "7 hari") }
            OutlinedButton(onClick = { period = 30 }, shape = RoundedCornerShape(12.dp)) { Text(if (period == 30) "✓ 30 hari" else "30 hari") }
        }
        Button(
            onClick = { exportLauncher.launch("Kasirku_Laporan_" + period + "Hari.csv") },
            modifier = Modifier.fillMaxWidth(),
            enabled = !exporting,
            shape = RoundedCornerShape(12.dp)
        ) { Text(if (exporting) "Menyiapkan laporan…" else "Unduh laporan CSV • $period hari") }
        exportMessage?.let { Text(it, color = ReportTeal, fontSize = 12.sp) }
        Card(colors = CardDefaults.cardColors(containerColor = ReportNavy), shape = RoundedCornerShape(18.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("TOTAL PENJUALAN • $period HARI", color = Color(0xFFD3DFE9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text("Rp ${reportMoney(total)}", color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Text("$count transaksi tercatat", color = Color(0xFFD3DFE9), fontSize = 12.sp)
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Rata-rata transaksi", color = ReportMuted, fontSize = 11.sp)
                Text("Rp ${reportMoney(if (count > 0) total / count else 0L)}", color = ReportNavy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }
        Text("Transaksi terbaru", color = ReportNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        if (sales.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                Text("Belum ada penjualan pada periode ini.", modifier = Modifier.padding(18.dp), color = ReportMuted, fontSize = 13.sp)
            }
        } else sales.take(20).forEach { sale ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(BranchTime.format(sale.createdAt, "dd MMM yyyy • HH:mm", branchZone), color = ReportNavy, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text(sale.paymentMethod + " • " + sale.id.take(8).uppercase(), color = ReportMuted, fontSize = 11.sp)
                    }
                    Text("Rp ${reportMoney(sale.total)}", color = ReportTeal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        Text("Data dihitung dari transaksi lokal pada toko dan cabang yang sedang aktif.", color = ReportMuted, fontSize = 11.sp)
    }
}
