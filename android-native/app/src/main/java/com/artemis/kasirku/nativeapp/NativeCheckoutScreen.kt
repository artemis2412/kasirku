package com.artemis.kasirku.nativeapp

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemis.kasirku.nativeapp.data.CatalogRepository
import com.artemis.kasirku.nativeapp.data.CheckoutLine
import com.artemis.kasirku.nativeapp.data.KasirkuRoomDatabase
import com.artemis.kasirku.nativeapp.data.SalesRepository
import com.artemis.kasirku.nativeapp.data.VerifiedTenantContext
import kotlinx.coroutines.launch

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
    val products by remember(tenant.storeId, tenant.branchId) {
        catalog.observeBranchCatalog(tenant.storeId, tenant.branchId, "")
    }.collectAsState(initial = emptyList())
    val cart = remember { mutableStateMapOf<String, CartEntry>() }
    val scope = rememberCoroutineScope()
    var search by remember { mutableStateOf("") }
    var payment by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var success by remember { mutableStateOf<String?>(null) }
    val subtotal = cart.values.sumOf { it.price * it.quantity }
    val paid = payment.filter(Char::isDigit).toLongOrNull() ?: 0L

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
        OutlinedTextField(value = search, onValueChange = { search = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Cari produk") }, singleLine = true)
        Text("Pilih produk", color = Color(0xFF17324D), fontWeight = FontWeight.Bold)
        products.filter { it.name.contains(search, ignoreCase = true) }.forEach { product ->
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(13.dp)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(product.name, color = Color(0xFF17324D), fontWeight = FontWeight.SemiBold)
                        Text("Rp " + product.activeSellingPrice.toString() + " • Stok " + product.branchQuantity, color = Color(0xFF718096), fontSize = 12.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val old = cart[product.id]
                            val current = old?.quantity ?: 0
                            if (current < product.branchQuantity) {
                                cart[product.id] = CartEntry(product.id, product.name, product.activeSellingPrice, product.branchQuantity, current + 1)
                                error = null
                            } else error = "Stok produk tidak mencukupi."
                        },
                        enabled = product.branchQuantity > (cart[product.id]?.quantity ?: 0)
                    ) { Text("+") }
                }
            }
        }
        Text("Keranjang • ${cart.values.sumOf { it.quantity }} item", color = Color(0xFF17324D), fontWeight = FontWeight.Bold)
        if (cart.isEmpty()) {
            Text("Pilih produk untuk memulai transaksi.", color = Color(0xFF718096), fontSize = 12.sp)
        } else {
            cart.values.toList().forEach { item ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(12.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.name, color = Color(0xFF17324D), fontWeight = FontWeight.SemiBold)
                            Text("Rp ${item.price} × ${item.quantity} = Rp ${item.price * item.quantity}", color = Color(0xFF718096), fontSize = 12.sp)
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
                    Text("Rp $subtotal", color = Color(0xFF17324D), fontWeight = FontWeight.Bold)
                }
                OutlinedTextField(value = payment, onValueChange = { payment = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Uang diterima (Rp)") }, singleLine = true)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Kembalian", color = Color(0xFF17324D))
                    Text("Rp ${(paid - subtotal).coerceAtLeast(0L)}", color = Color(0xFF168C83), fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            error = null
                            runCatching {
                                sales.checkout(tenant, cart.values.map { CheckoutLine(it.id, it.name, it.quantity, it.price) }, paid)
                            }.onSuccess { result ->
                                success = "Transaksi tersimpan. Total Rp ${result.total}, kembalian Rp ${result.change}."
                                cart.clear()
                                payment = ""
                            }.onFailure { error = it.message ?: "Transaksi gagal disimpan." }
                            busy = false
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy && cart.isNotEmpty() && paid >= subtotal && subtotal > 0L,
                    shape = RoundedCornerShape(12.dp)
                ) { Text(if (busy) "Memproses…" else "Selesaikan transaksi") }
            }
        }
        error?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
        success?.let { Text(it, color = Color(0xFF168C83), fontSize = 12.sp) }
        Text("Transaksi disimpan lokal. Sinkronisasi Cloud dan cetak Bluetooth menyusul.", color = Color(0xFF718096), fontSize = 11.sp)
    }
}
