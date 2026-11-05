package com.artemis.kasirku.nativeapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemis.kasirku.nativeapp.data.KasirkuRoomDatabase
import com.artemis.kasirku.nativeapp.data.SalesRepository
import com.artemis.kasirku.nativeapp.data.VerifiedTenantContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val HomeNavy = Color(0xFF17324D)
private val HomeTeal = Color(0xFF168C83)
private val HomeMuted = Color(0xFF718096)
private val HomeCanvas = Color(0xFFF5F7F9)
private fun homeMoney(value: Long) = value.toString().reversed().chunked(3).joinToString(".").reversed()

@Composable
internal fun NativeDashboardScreen(
    tenant: VerifiedTenantContext,
    modifier: Modifier = Modifier,
    onNewTransaction: () -> Unit,
    onOpenTab: (Int) -> Unit
) {
    val context = LocalContext.current.applicationContext
    val dao = remember { KasirkuRoomDatabase.getInstance(context).catalogDao() }
    val repository = remember { SalesRepository(dao) }
    val todayStart = remember {
        Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }
    val tomorrow = remember(todayStart) { Calendar.getInstance().apply { timeInMillis = todayStart; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis }
    val products by remember(tenant.storeId) { dao.observeActiveProductCount(tenant.storeId) }.collectAsState(initial = 0)
    val stock by remember(tenant.branchId) { dao.observeBranchStockTotal(tenant.branchId) }.collectAsState(initial = 0)
    val lowStock by remember(tenant.branchId) { dao.observeLowStockCount(tenant.branchId) }.collectAsState(initial = 0)
    val sales by remember(tenant.storeId, tenant.branchId, todayStart, tomorrow) {
        repository.observeSales(tenant, todayStart, tomorrow)
    }.collectAsState(initial = emptyList())
    val count by remember(tenant.storeId, tenant.branchId, todayStart, tomorrow) {
        repository.observeSaleCount(tenant, todayStart, tomorrow)
    }.collectAsState(initial = 0)
    val total by remember(tenant.storeId, tenant.branchId, todayStart, tomorrow) {
        repository.observeSalesTotal(tenant, todayStart, tomorrow)
    }.collectAsState(initial = 0L)

    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("KASIRKU", color = HomeTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.7.sp)
                Text("Beranda", color = HomeNavy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(tenant.storeName + " • " + tenant.branchName, color = HomeMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Surface(shape = CircleShape, color = Color(0xFFE5F1F0), modifier = Modifier.size(46.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(tenant.role.name.take(1), color = HomeTeal, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            }
        }
        Card(colors = CardDefaults.cardColors(containerColor = HomeNavy), shape = RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("PENJUALAN HARI INI", color = Color(0xFFD3DFE9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Text("Rp ${homeMoney(total)}", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                HorizontalDivider(color = Color(0xFF3A5268))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column { Text("Transaksi", color = Color(0xFFD3DFE9), fontSize = 11.sp); Text(count.toString(), color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                    Column { Text("Produk terjual", color = Color(0xFFD3DFE9), fontSize = 11.sp); Text(sales.size.let { "" } + "Lihat", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeMetric("Produk aktif", products.toString(), Modifier.weight(1f))
            HomeMetric("Total stok", stock.toString(), Modifier.weight(1f))
            HomeMetric("Stok menipis", lowStock.toString(), Modifier.weight(1f))
        }
        Button(onClick = onNewTransaction, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)) {
            Text("+  Transaksi baru", modifier = Modifier.padding(vertical = 4.dp), fontWeight = FontWeight.Bold)
        }
        Text("Menu cepat", color = HomeNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeAction("Produk", "Kelola barang", Modifier.weight(1f)) { onOpenTab(1) }
            HomeAction("Pesanan", "Riwayat hari ini", Modifier.weight(1f)) { onOpenTab(2) }
            HomeAction("Laporan", "Analisis penjualan", Modifier.weight(1f)) { onOpenTab(3) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Aktivitas terbaru", color = HomeNavy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Text("Lihat semua", color = HomeTeal, fontSize = 12.sp, modifier = Modifier.clickable { onOpenTab(2) })
        }
        if (sales.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
                Text("Belum ada transaksi hari ini.", color = HomeMuted, fontSize = 13.sp, modifier = Modifier.padding(18.dp))
            }
        } else sales.take(5).forEach { sale ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text("Transaksi ${sale.id.take(8).uppercase()}", color = HomeNavy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        Text(SimpleDateFormat("HH:mm • dd MMM", Locale("id", "ID")).format(Date(sale.createdAt)) + " • " + sale.paymentMethod, color = HomeMuted, fontSize = 11.sp)
                    }
                    Text("Rp ${homeMoney(sale.total)}", color = HomeTeal, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }
        Text("Data lokal • ${SimpleDateFormat("dd MMM yyyy", Locale("id", "ID")).format(Date())}", color = HomeMuted, fontSize = 10.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
    }
}

@Composable
private fun HomeMetric(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(value, color = HomeNavy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text(title, color = HomeMuted, fontSize = 10.sp, maxLines = 1)
        }
    }
}

@Composable
private fun HomeAction(title: String, subtitle: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(title, color = HomeNavy, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = HomeMuted, fontSize = 10.sp)
        }
    }
}
