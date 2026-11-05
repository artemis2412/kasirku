package com.artemis.kasirku.nativeapp

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
import com.artemis.kasirku.nativeapp.data.SalesRepository
import com.artemis.kasirku.nativeapp.data.VerifiedTenantContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private val OrdersNavy = Color(0xFF17324D)
private val OrdersTeal = Color(0xFF168C83)
private val OrdersMuted = Color(0xFF718096)

@Composable
internal fun NativeOrdersScreen(
    tenant: VerifiedTenantContext,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val repository = remember { SalesRepository(KasirkuRoomDatabase.getInstance(context).catalogDao()) }
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
                    Text("Rp ${total}", color = OrdersTeal, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                            selectedItems = repository.saleItems(sale.id)
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
                        Text("Rp ${sale.total}", color = OrdersTeal, fontWeight = FontWeight.Bold)
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
                            Text("Rp ${item.lineTotal}", color = OrdersNavy, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Text("Subtotal: Rp ${sale.subtotal}", color = OrdersMuted, fontSize = 12.sp)
                    Text("Diskon: Rp ${sale.discount}", color = OrdersMuted, fontSize = 12.sp)
                    Text("Total: Rp ${sale.total}", color = OrdersTeal, fontWeight = FontWeight.Bold)
                    Text("Dibayar: Rp ${sale.paid} • Kembalian: Rp ${sale.change}", color = OrdersMuted, fontSize = 11.sp)
                }
            },
            confirmButton = { TextButton(onClick = { selectedSale = null }) { Text("Tutup") } }
        )
    }
}
