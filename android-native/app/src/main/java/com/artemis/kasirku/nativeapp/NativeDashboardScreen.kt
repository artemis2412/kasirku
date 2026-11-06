package com.artemis.kasirku.nativeapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
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
import kotlinx.coroutines.delay

private val HomeNavy = Color(0xFF17324D)
private val HomeTeal = Color(0xFF6422C7)
private val HomeMuted = Color(0xFF718096)
private fun homeMoney(value: Long) = value.toString().reversed().chunked(3).joinToString(".").reversed()

@Composable
internal fun NativeDashboardScreen(tenant: VerifiedTenantContext, modifier: Modifier = Modifier, onNewTransaction: () -> Unit, onOpenTab: (Int) -> Unit) {
    val context = LocalContext.current.applicationContext
    val dao = remember { KasirkuRoomDatabase.getInstance(context).catalogDao() }
    val repository = remember { SalesRepository(dao) }
    val branchZone = remember(tenant.branchTimeZoneId) { BranchTime.zoneId(tenant) }
    var start by remember(branchZone) { mutableLongStateOf(BranchTime.startOfToday(branchZone)) }
    LaunchedEffect(branchZone) {
        while (true) {
            val currentStart = BranchTime.startOfToday(branchZone)
            if (start != currentStart) start = currentStart
            val nextStart = BranchTime.startOfDayOffset(currentStart, 1, branchZone)
            delay((nextStart - System.currentTimeMillis()).coerceAtLeast(1_000L))
        }
    }
    val end = remember(start, branchZone) { BranchTime.startOfDayOffset(start, 1, branchZone) }
    val products by remember(tenant.storeId) { dao.observeActiveProductCount(tenant.storeId) }.collectAsState(initial=0)
    val stock by remember(tenant.branchId) { dao.observeBranchStockTotal(tenant.branchId) }.collectAsState(initial=0)
    val lowStock by remember(tenant.branchId) { dao.observeLowStockCount(tenant.branchId) }.collectAsState(initial=0)
    val sales by remember(tenant.storeId,tenant.branchId,start,end) { repository.observeSales(tenant,start,end) }.collectAsState(initial=emptyList())
    val count by remember(tenant.storeId,tenant.branchId,start,end) { repository.observeSaleCount(tenant,start,end) }.collectAsState(initial=0)
    val total by remember(tenant.storeId,tenant.branchId,start,end) { repository.observeSalesTotal(tenant,start,end) }.collectAsState(initial=0L)
    val sold by remember(tenant.storeId,tenant.branchId,start,end) { dao.observeSoldQuantity(tenant.storeId,tenant.branchId,start,end) }.collectAsState(initial=0)
    val pendingSync by dao.observePendingSaleSyncCount(tenant.storeId, tenant.branchId).collectAsState(initial=0)

    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement=Arrangement.spacedBy(15.dp)) {
        Column(verticalArrangement=Arrangement.spacedBy(3.dp)) {
            Text("KASIRKU", color=HomeTeal, fontSize=11.sp, fontWeight=FontWeight.Bold, letterSpacing=1.5.sp)
            Text("Beranda", color=HomeNavy, fontSize=25.sp, fontWeight=FontWeight.Bold)
            Text(tenant.storeName+" • "+tenant.branchName, color=HomeMuted, fontSize=12.sp)
        }
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFF6422C7)), shape=RoundedCornerShape(20.dp)) {
            Column(Modifier.fillMaxWidth().padding(19.dp), verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("PENJUALAN HARI INI", color=Color(0xFFD3DFE9), fontSize=11.sp, fontWeight=FontWeight.Bold)
                Text("Rp ${homeMoney(total)}", color=Color.White, fontSize=29.sp, fontWeight=FontWeight.Bold)
                HorizontalDivider(color=Color(0xFF3A5268))
                Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
                    Column { Text("Transaksi", color=Color.LightGray, fontSize=11.sp); Text("$count", color=Color.White, fontSize=17.sp, fontWeight=FontWeight.Bold) }
                    Column { Text("Produk terjual", color=Color.LightGray, fontSize=11.sp); Text("$sold item", color=Color.White, fontSize=17.sp, fontWeight=FontWeight.Bold) }
                }
            }
        }
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Metric("Produk",products.toString(),Modifier.weight(1f))
            Metric("Stok",stock.toString(),Modifier.weight(1f))
            Metric("Menipis",lowStock.toString(),Modifier.weight(1f))
        }
        Card(colors=CardDefaults.cardColors(containerColor=Color(0xFFF1EAFE)), shape=RoundedCornerShape(13.dp)) {
            Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("Antrean sinkronisasi", color=HomeNavy, fontWeight=FontWeight.Bold, fontSize=12.sp)
                    Text(
                        if (tenant.branchTimeZoneId.isNullOrBlank())
                            "Zona waktu cabang belum diatur. Tanggal dan ringkasan sementara mengikuti zona waktu perangkat; atur zona waktu cabang di menu Akun agar sesuai operasional."
                        else
                            "Transaksi tersimpan aman di perangkat dan menunggu pengiriman cloud.",
                        color=HomeMuted,
                        fontSize=10.sp
                    )
                }
                Text("$pendingSync", color=HomeTeal, fontSize=20.sp, fontWeight=FontWeight.Bold)
            }
        }
        Button(onClick=onNewTransaction, modifier=Modifier.fillMaxWidth(), shape=RoundedCornerShape(12.dp)) { Text("+ Transaksi baru") }
        Text("Menu cepat", color=HomeNavy, fontSize=16.sp, fontWeight=FontWeight.Bold)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Action("Produk",Modifier.weight(1f)){onOpenTab(1)}
            Action("Pesanan",Modifier.weight(1f)){onOpenTab(2)}
            Action("Laporan",Modifier.weight(1f)){onOpenTab(3)}
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement=Arrangement.SpaceBetween) {
            Text("Aktivitas terbaru", color=HomeNavy, fontSize=16.sp, fontWeight=FontWeight.Bold)
            Text("Lihat semua", color=HomeTeal, fontSize=12.sp, modifier=Modifier.clickable{onOpenTab(2)})
        }
        if(sales.isEmpty()) Text("Belum ada transaksi hari ini.", color=HomeMuted, fontSize=13.sp)
        else sales.take(5).forEach { sale ->
            Card(colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(13.dp)) {
                Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement=Arrangement.SpaceBetween, verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Transaksi ${sale.id.take(8).uppercase()}", color=HomeNavy, fontWeight=FontWeight.SemiBold, fontSize=12.sp)
                        Text(BranchTime.format(sale.createdAt, "HH:mm • dd MMM", branchZone)+" • "+sale.paymentMethod, color=HomeMuted, fontSize=10.sp)
                    }
                    Text("Rp ${homeMoney(sale.total)}", color=HomeTeal, fontWeight=FontWeight.Bold, fontSize=12.sp)
                }
            }
        }
    }
}

@Composable private fun Metric(label:String,value:String,modifier:Modifier=Modifier) {
    Card(modifier, colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(13.dp)) {
        Column(Modifier.padding(11.dp), verticalArrangement=Arrangement.spacedBy(4.dp)) {
            Text(value,color=HomeNavy,fontSize=19.sp,fontWeight=FontWeight.Bold)
            Text(label,color=HomeMuted,fontSize=10.sp)
        }
    }
}
@Composable private fun Action(label:String,modifier:Modifier=Modifier,onClick:()->Unit) {
    Card(modifier.clickable(onClick=onClick), colors=CardDefaults.cardColors(containerColor=Color.White), shape=RoundedCornerShape(13.dp)) {
        Text(label,color=HomeNavy,fontWeight=FontWeight.Bold,fontSize=12.sp,modifier=Modifier.padding(14.dp))
    }
}
