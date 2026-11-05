package com.artemis.kasirku.nativeapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Navy = Color(0xFF17324D)
private val Teal = Color(0xFF168C83)
private val Canvas = Color(0xFFF5F7F9)
private val Muted = Color(0xFF718096)

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
    Reports("Laporan", "▥")
}

@Composable
private fun KasirkuNativeApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showNewTransaction by remember { mutableStateOf(false) }

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Navy,
            secondary = Teal,
            background = Canvas,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = Navy,
            onSurface = Navy
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Canvas) {
            Scaffold(
                containerColor = Canvas,
                bottomBar = {
                    NavigationBar(containerColor = Color.White, tonalElevation = 4.dp) {
                        KasirkuTab.values().forEachIndexed { index, tab ->
                            NavigationBarItem(
                                selected = selectedTab == index && !showNewTransaction,
                                onClick = {
                                    selectedTab = index
                                    showNewTransaction = false
                                },
                                icon = { Text(tab.symbol, fontSize = 21.sp, fontWeight = FontWeight.SemiBold) },
                                label = { Text(tab.title, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            ) { innerPadding ->
                when {
                    showNewTransaction -> SimpleFeatureScreen(
                        title = "Transaksi baru",
                        description = "Halaman transaksi akan kita bangun berikutnya, termasuk keranjang, jumlah barang, diskon, pembayaran, dan cetak nota.",
                        onBack = { showNewTransaction = false },
                        modifier = Modifier.padding(innerPadding)
                    )
                    selectedTab == 0 -> DashboardScreen(
                        modifier = Modifier.padding(innerPadding),
                        onNewTransaction = { showNewTransaction = true },
                        onOpenTab = { selectedTab = it }
                    )
                    selectedTab == 1 -> SimpleFeatureScreen(
                        title = "Produk",
                        description = "Tempat mengelola daftar barang, harga jual, stok, barcode, dan kategori.",
                        modifier = Modifier.padding(innerPadding)
                    )
                    selectedTab == 2 -> SimpleFeatureScreen(
                        title = "Pesanan",
                        description = "Daftar pesanan dan transaksi harian akan ditampilkan di sini.",
                        modifier = Modifier.padding(innerPadding)
                    )
                    else -> SimpleFeatureScreen(
                        title = "Laporan",
                        description = "Ringkasan penjualan, riwayat harian, dan unduhan laporan akan tersedia di sini.",
                        modifier = Modifier.padding(innerPadding)
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
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp)
            .padding(top = 18.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("KASIRKU", color = Navy, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
                Spacer(Modifier.height(3.dp))
                Text("Selamat datang!", color = Navy, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                Text("Ringkasan tokomu hari ini", color = Muted, fontSize = 13.sp)
            }
            Box(
                modifier = Modifier.size(46.dp).background(Color(0xFFE5F1F0), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("T", color = Teal, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }

        SalesSummaryCard()

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Transaksi", "0", "Hari ini", "▤", Modifier.weight(1f))
            MetricCard("Produk", "0", "Terdaftar", "▦", Modifier.weight(1f))
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
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier.size(48.dp).background(Canvas, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("▤", color = Muted, fontSize = 22.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Belum ada transaksi", color = Navy, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(Modifier.height(3.dp))
                    Text("Transaksi yang kamu buat akan muncul di sini.", color = Muted, fontSize = 12.sp)
                }
            }
        }

        Text(
            "Kasirku Native • Versi 0.2.0",
            color = Muted,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        )
    }
}

@Composable
private fun SalesSummaryCard() {
    Card(
        colors = CardDefaults.cardColors(containerColor = Navy),
        shape = RoundedCornerShape(22.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("PENJUALAN HARI INI", color = Color(0xFFD3DFE9), fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                Spacer(Modifier.weight(1f))
                Text("● LIVE", color = Color(0xFF8DE0C9), fontSize = 10.sp, fontWeight = FontWeight.Bold)
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
private fun MetricCard(
    title: String,
    value: String,
    subtitle: String,
    symbol: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
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
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = background),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Text(symbol, color = if (emphasized) Teal else Navy, fontSize = 23.sp, fontWeight = FontWeight.Bold)
            Text(title, color = Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
    }
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
        Text(description, color = Muted, fontSize = 14.sp, lineHeight = 22.sp)
        Spacer(Modifier.height(22.dp))
        if (onBack != null) {
            OutlinedButton(onClick = onBack) { Text("Kembali ke dashboard") }
        } else {
            Text("Fitur ini akan dikembangkan pada tahap berikutnya.", color = Muted, fontSize = 12.sp)
        }
    }
}
