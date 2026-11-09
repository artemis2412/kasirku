package com.artemis.kasirku.nativeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemis.kasirku.nativeapp.data.CloudAuthRepository
import com.artemis.kasirku.nativeapp.data.KasirkuRoomDatabase
import com.artemis.kasirku.nativeapp.data.StoreTableEntity
import com.artemis.kasirku.nativeapp.data.VerifiedTenantContext
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
internal fun NativeTableManagementScreen(
    tenant: VerifiedTenantContext,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val dao = remember { KasirkuRoomDatabase.getInstance(context).storeTableDao() }
    val cloud = remember { CloudAuthRepository() }
    val canManageTables = tenant.role in setOf(
        com.artemis.kasirku.nativeapp.data.StoreRole.OWNER,
        com.artemis.kasirku.nativeapp.data.StoreRole.ADMIN,
        com.artemis.kasirku.nativeapp.data.StoreRole.SUPERVISOR
    )
    val tables by dao.observeActiveTables(tenant.storeId, tenant.branchId).collectAsState(initial = emptyList())
    val count by dao.observeActiveCount(tenant.storeId, tenant.branchId).collectAsState(initial = 0)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<StoreTableEntity?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("4") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(tenant.storeId, tenant.branchId) {
        try {
            dao.upsertAll(cloud.fetchStoreTables(tenant))
        } catch (failure: Exception) {
            error = failure.message ?: "Gagal memuat status meja dari server."
        }
    }

    fun openForm(table: StoreTableEntity?) {
        editing = table
        name = table?.name.orEmpty()
        capacity = (table?.capacity ?: 4).toString()
        error = null
        showForm = true
    }

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("← Kembali") }
            Text("$count meja aktif", color = Color(0xFF718096), fontSize = 12.sp)
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("OPERASIONAL TOKO", color = Color(0xFF6422C7), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
            Text("Manajemen meja", color = Color(0xFF17324D), fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(tenant.storeName + " • " + tenant.branchName, color = Color(0xFF718096), fontSize = 12.sp)
        }
        error?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canManageTables) {
                Button(onClick = { openForm(null) }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp)) {
                    Text("+ Tambah meja")
                }
            }
            TextButton(onClick = {
                scope.launch {
                    busy = true
                    try {
                        dao.upsertAll(cloud.fetchStoreTables(tenant))
                        error = null
                    } catch (failure: Exception) {
                        error = failure.message ?: "Gagal menyegarkan status meja."
                    } finally {
                        busy = false
                    }
                }
            }, enabled = !busy) { Text(if (busy) "Memuat…" else "Muat ulang") }
        }
        if (tables.isEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Belum ada meja", color = Color(0xFF17324D), fontWeight = FontWeight.Bold)
                    Text("Tambahkan meja atau area layanan untuk cabang ini.", color = Color(0xFF718096), fontSize = 12.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(tables, key = { it.id }) { table ->
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
                        Row(Modifier.fillMaxWidth().padding(15.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(table.name, color = Color(0xFF17324D), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Kapasitas " + table.capacity + " orang", color = Color(0xFF718096), fontSize = 12.sp)
                                Text(if (table.occupiedSaleId == null) "Status: Tersedia" else "Status: Terisi • " + table.occupiedSaleId.take(8), color = if (table.occupiedSaleId == null) Color(0xFF168C83) else Color(0xFFB54708), fontSize = 11.sp)
                            }
                            if (table.occupiedSaleId != null) {
                                TextButton(onClick = {
                                    scope.launch {
                                        val cloudReleased = cloud.releaseStoreTable(tenant, table.id, table.occupiedSaleId)
                                        check(cloudReleased) { "Meja tidak berhasil dikosongkan di server. Periksa koneksi atau muat ulang." }
                                        val released = dao.release(tenant.storeId, tenant.branchId, table.id, table.occupiedSaleId, System.currentTimeMillis())
                                        check(released == 1) { "Status lokal meja berubah. Muat ulang untuk menyamakan data." }
                                    }
                                }) { Text("Kosongkan") }
                            } else if (canManageTables) {
                                TextButton(onClick = { openForm(table) }) { Text("Ubah") }
                                TextButton(onClick = {
                                    scope.launch {
                                        try {
                                            val archived = cloud.archiveStoreTable(tenant, table.id)
                                            check(archived) { "Meja tidak ditemukan atau gagal diarsipkan di server." }
                                            dao.upsertAll(cloud.fetchStoreTables(tenant))
                                            error = null
                                        } catch (failure: Exception) {
                                            error = failure.message ?: "Gagal mengarsipkan meja."
                                        }
                                    }
                                }) { Text("Arsip", color = Color(0xFFB42318)) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showForm) {
        AlertDialog(
            onDismissRequest = { if (!busy) showForm = false },
            title = { Text(if (editing == null) "Tambah meja" else "Ubah meja") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it; error = null }, label = { Text("Nama meja / area") }, singleLine = true)
                    OutlinedTextField(value = capacity, onValueChange = { capacity = it.filter(Char::isDigit).take(3) }, label = { Text("Kapasitas orang") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                    error?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val cleanName = name.trim()
                    val seats = capacity.toIntOrNull()
                    if (cleanName.isBlank()) { error = "Nama meja wajib diisi."; return@TextButton }
                    if (seats == null || seats !in 1..999) { error = "Kapasitas harus 1–999 orang."; return@TextButton }
                    busy = true
                    scope.launch {
                        try {
                            val now = System.currentTimeMillis()
                            val current = editing
                            if (current == null) {
                                val tableId = UUID.randomUUID().toString()
                                cloud.createStoreTable(tenant, tableId, cleanName, seats)
                                dao.insert(StoreTableEntity(tableId, tenant.storeId, tenant.branchId, cleanName, seats, true, now, now))
                            } else {
                                val cloudUpdated = cloud.updateStoreTable(tenant, current.id, cleanName, seats)
                                check(cloudUpdated) { "Meja tidak ditemukan, sedang terisi, atau perubahan ditolak server." }
                                val refreshed = cloud.fetchStoreTables(tenant)
                                dao.upsertAll(refreshed)
                            }
                            showForm = false
                        } catch (failure: Exception) {
                            error = if (failure.message.orEmpty().contains("UNIQUE", true)) "Nama meja sudah digunakan di cabang ini." else failure.message ?: "Gagal menyimpan meja."
                        } finally { busy = false }
                    }
                }) { Text(if (busy) "Menyimpan…" else "Simpan") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { showForm = false }) { Text("Batal") } }
        )
    }
}
