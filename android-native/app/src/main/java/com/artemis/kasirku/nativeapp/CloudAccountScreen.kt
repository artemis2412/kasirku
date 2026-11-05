package com.artemis.kasirku.nativeapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemis.kasirku.nativeapp.data.CloudAccountAccess
import com.artemis.kasirku.nativeapp.data.CloudAuthRepository
import com.artemis.kasirku.nativeapp.data.CatalogRepository
import com.artemis.kasirku.nativeapp.data.CatalogProductDraft
import com.artemis.kasirku.nativeapp.data.KasirkuRoomDatabase
import com.artemis.kasirku.nativeapp.data.LegacyProductImporter
import com.artemis.kasirku.nativeapp.data.VerifiedTenantContext
import kotlinx.coroutines.launch

private val AccountNavy = Color(0xFF17324D)
private val AccountTeal = Color(0xFF168C83)
private val AccountMuted = Color(0xFF718096)

private fun formatNominalInput(value: String): String =
    value.filter(Char::isDigit).reversed().chunked(3).joinToString(".").reversed()

@Composable
internal fun CloudAccountScreen(
    modifier: Modifier = Modifier,
    onContextChanged: (VerifiedTenantContext?) -> Unit
) {
    val repository = remember { CloudAuthRepository() }
    val appContext = LocalContext.current.applicationContext
    val selectionPrefs = remember { appContext.getSharedPreferences("kasirku_verified_tenant", android.content.Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var access by remember { mutableStateOf<CloudAccountAccess?>(null) }
    var selectedStoreId by remember { mutableStateOf<String?>(null) }
    var selectedBranchId by remember { mutableStateOf<String?>(null) }
    var verifiedContext by remember { mutableStateOf<VerifiedTenantContext?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("Masuk menggunakan akun Cloud Kasirku.") }
    var isError by remember { mutableStateOf(false) }
    var importSummary by remember { mutableStateOf<String?>(null) }

    fun applyAccess(result: CloudAccountAccess) {
        access = result
        val savedStoreId = selectionPrefs.getString("store_id", null)
        val savedBranchId = selectionPrefs.getString("branch_id", null)
        val store = result.stores.firstOrNull { it.id == savedStoreId }
            ?: result.stores.singleOrNull()
        val branch = store?.branches?.firstOrNull { it.id == savedBranchId }
            ?: store?.branches?.singleOrNull()
        selectedStoreId = store?.id
        selectedBranchId = branch?.id
        verifiedContext = if (store != null && branch != null) {
            result.verifiedContext(store.id, branch.id)
        } else null
        importSummary = null
        onContextChanged(verifiedContext)
        message = if (verifiedContext != null) {
            "Sesi dan akses toko/cabang berhasil dipulihkan."
        } else if (result.stores.size > 1) {
            "Pilih toko yang akan digunakan."
        } else {
            "Pilih cabang aktif untuk melanjutkan."
        }
        isError = false
    }

    LaunchedEffect(Unit) {
        busy = true
        runCatching { repository.restoreSession() }
            .onSuccess { result -> if (result != null) applyAccess(result) }
            .onFailure { message = it.message ?: "Gagal memulihkan sesi Cloud."; isError = true }
        busy = false
    }

    val selectedStore = access?.stores?.firstOrNull { it.id == selectedStoreId }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("AKUN & AKSES", color = AccountTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
            Text("Akun Cloud", color = AccountNavy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text("Masuk, lalu pilih toko dan cabang yang memang diizinkan untuk akunmu.", color = AccountMuted, fontSize = 13.sp)
        }

        if (access == null) {
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Email") },
                singleLine = true
            )
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Kata sandi") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation()
            )
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        isError = false
                        message = "Memverifikasi akun, toko, dan cabang…"
                        runCatching { repository.signIn(email, password) }
                            .onSuccess(::applyAccess)
                            .onFailure {
                                message = it.message ?: "Login Cloud gagal."
                                isError = true
                            }
                        busy = false
                    }
                },
                enabled = !busy && email.isNotBlank() && password.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp)
            ) { Text(if (busy) "Memeriksa…" else "Masuk ke Cloud") }
        } else {
            Text("Pilih toko", color = AccountNavy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            access!!.stores.forEach { store ->
                SelectionCard(
                    title = store.name,
                    subtitle = "Peran: ${store.role.name.lowercase()}",
                    selected = selectedStoreId == store.id,
                    onClick = {
                        selectedStoreId = store.id
                        selectedBranchId = null
                        selectionPrefs.edit().putString("store_id", store.id).remove("branch_id").apply()
                        verifiedContext = null
                        onContextChanged(null)
                    }
                )
            }

            if (selectedStore != null) {
                Spacer(Modifier.height(2.dp))
                Text("Pilih cabang", color = AccountNavy, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                selectedStore.branches.forEach { branch ->
                    SelectionCard(
                        title = branch.name,
                        subtitle = "Cabang aktif",
                        selected = selectedBranchId == branch.id,
                        onClick = {
                            selectedBranchId = branch.id
                            selectionPrefs.edit().putString("store_id", selectedStore.id).putString("branch_id", branch.id).apply()
                            verifiedContext = access!!.verifiedContext(selectedStore.id, branch.id)
                            onContextChanged(verifiedContext)
                            message = "Akses toko dan cabang berhasil diverifikasi."
                            isError = false
                        }
                    )
                }
            }

            verifiedContext?.let { context ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F4F1)),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(15.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("KONTEKS TERVERIFIKASI", color = AccountTeal, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                        Text(context.storeName, color = AccountNavy, fontWeight = FontWeight.Bold)
                        Text(context.branchName + " • " + context.role.name.lowercase(), color = AccountMuted, fontSize = 12.sp)
                        Text("Akses harga modal/laba: " + if (context.role.canViewCostAndProfit()) "Owner" else "Tidak diizinkan", color = AccountMuted, fontSize = 12.sp)
                    }
                }
            }

            verifiedContext?.takeIf { it.role == com.artemis.kasirku.nativeapp.data.StoreRole.OWNER || it.role == com.artemis.kasirku.nativeapp.data.StoreRole.ADMIN }?.let { tenant ->
                Button(
                    onClick = {
                        scope.launch {
                            busy = true
                            importSummary = null
                            runCatching {
                                val room = KasirkuRoomDatabase.getInstance(appContext)
                                LegacyProductImporter(appContext, room).import(tenant.storeId, tenant.branchId)
                            }.onSuccess { result ->
                                importSummary = "Impor selesai: ${result.importedCount} produk baru, ${result.alreadyImportedCount} sudah ada, ${result.skippedBarcodeCount} barcode dilewati."
                                message = if (result.countVerified) "Jumlah data sumber dan hasil impor terverifikasi." else "Perhatian: jumlah data perlu diperiksa."
                                isError = !result.countVerified
                            }.onFailure {
                                message = it.message ?: "Impor produk gagal."
                                isError = true
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                ) { Text(if (busy) "Mengimpor…" else "Impor produk lama ke Room") }
                importSummary?.let { summary ->
                    Text(summary, color = AccountMuted, fontSize = 12.sp)
                    Text("Data lama tidak dihapus. Harga modal tetap berada di database lama.", color = AccountMuted, fontSize = 11.sp)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    scope.launch {
                        busy = true
                        isError = false
                        runCatching { repository.signOut() }
                            .onSuccess {
                                access = null
                                verifiedContext = null
                                selectedStoreId = null
                                selectedBranchId = null
                                selectionPrefs.edit().clear().apply()
                                onContextChanged(null)
                                message = "Sesi Cloud telah diakhiri."
                            }
                            .onFailure {
                                message = it.message ?: "Gagal mengakhiri sesi Cloud. Coba lagi."
                                isError = true
                            }
                        busy = false
                    }
                }, enabled = !busy) { Text("Keluar dari Cloud") }
            }
        }

        Text(
            message,
            color = if (isError) Color(0xFFB42318) else AccountMuted,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun SelectionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (selected) Color(0xFFE8F4F1) else Color.White
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, color = AccountNavy, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = AccountMuted, fontSize = 11.sp)
        }
    }
}


@Composable
internal fun NativeProductsScreen(
    tenant: VerifiedTenantContext,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val repository = remember {
        CatalogRepository(KasirkuRoomDatabase.getInstance(context).catalogDao())
    }
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var editingId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var basePrice by remember { mutableStateOf("") }
    var sellingPrice by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("0") }
    var barcode by remember { mutableStateOf("") }
    var formError by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    val canManageCatalog = tenant.role == com.artemis.kasirku.nativeapp.data.StoreRole.OWNER || tenant.role == com.artemis.kasirku.nativeapp.data.StoreRole.ADMIN
    val products by remember(tenant.storeId, tenant.branchId, query) {
        repository.observeBranchCatalog(tenant.storeId, tenant.branchId, query)
    }.collectAsState(initial = emptyList())

    fun clearForm() {
        editingId = null
        name = ""
        basePrice = ""
        sellingPrice = ""
        quantity = "0"
        barcode = ""
        formError = null
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("KATALOG CABANG", color = AccountTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text("Produk", color = AccountNavy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(tenant.storeName + " • " + tenant.branchName, color = AccountMuted, fontSize = 12.sp)

        if (canManageCatalog) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text(if (editingId == null) "Tambah produk" else "Edit produk", color = AccountNavy, fontWeight = FontWeight.Bold)
                OutlinedTextField(value = name, onValueChange = { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nama produk *") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = basePrice, onValueChange = { basePrice = formatNominalInput(it) }, modifier = Modifier.weight(1f), label = { Text("Harga dasar *") }, singleLine = true)
                    OutlinedTextField(value = sellingPrice, onValueChange = { sellingPrice = formatNominalInput(it) }, modifier = Modifier.weight(1f), label = { Text("Harga jual cabang (opsional)") }, singleLine = true)
                }
                OutlinedTextField(value = quantity, onValueChange = { quantity = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Stok cabang") }, singleLine = true)
                OutlinedTextField(value = barcode, onValueChange = { barcode = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Barcode (opsional)") }, singleLine = true)
                formError?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val parsedBase = basePrice.filter(Char::isDigit).toLongOrNull() ?: 0L
                            val parsedSelling = sellingPrice.filter(Char::isDigit).toLongOrNull()
                            val parsedQuantity = quantity.toIntOrNull()
                            if (name.isBlank() || parsedBase <= 0L || (sellingPrice.isNotBlank() && parsedSelling == null) || parsedQuantity == null) {
                                formError = "Nama, harga dasar, dan stok harus diisi dengan benar. Harga jual cabang boleh dikosongkan."
                            } else {
                                scope.launch {
                                    saving = true
                                    formError = null
                                    runCatching {
                                        repository.saveProduct(
                                            tenant.storeId,
                                            tenant.branchId,
                                            CatalogProductDraft(
                                                id = editingId,
                                                name = name,
                                                basePrice = parsedBase,
                                                sellingPrice = parsedSelling,
                                                quantity = parsedQuantity,
                                                barcodes = barcode.split(",").map(String::trim).filter(String::isNotEmpty)
                                            )
                                        )
                                    }.onSuccess {
                                        notice = if (editingId == null) "Produk berhasil ditambahkan." else "Produk berhasil diperbarui."
                                        clearForm()
                                    }.onFailure {
                                        formError = it.message ?: "Gagal menyimpan produk."
                                    }
                                    saving = false
                                }
                            }
                        },
                        enabled = !saving,
                        shape = RoundedCornerShape(11.dp)
                    ) { Text(if (saving) "Menyimpan…" else "Simpan") }
                    TextButton(onClick = { clearForm() }, enabled = !saving) { Text("Batal") }
                }
            }
        }

        } else {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF1F3)), shape = RoundedCornerShape(15.dp)) {
                Text("Akses lihat katalog saja. Hanya owner dan admin yang dapat menambah atau mengubah produk.", modifier = Modifier.padding(14.dp), color = AccountMuted, fontSize = 12.sp)
            }
        }
        notice?.let { Text(it, color = AccountTeal, fontSize = 12.sp) }
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Cari nama produk atau barcode") },
            singleLine = true
        )
        if (products.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                shape = RoundedCornerShape(15.dp)
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("Belum ada produk di katalog Native", color = AccountNavy, fontWeight = FontWeight.SemiBold)
                    Text("Tambahkan produk di formulir atas atau impor katalog lama melalui menu Akun.", color = AccountMuted, fontSize = 12.sp)
                }
            }
        } else {
            Text("${products.size} produk", color = AccountMuted, fontSize = 12.sp)
            products.forEach { product ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(product.name, color = AccountNavy, fontWeight = FontWeight.SemiBold)
                                Text("Stok: ${product.branchQuantity}", color = AccountMuted, fontSize = 12.sp)
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(product.activeSellingPrice?.let { "Rp ${it.toString().reversed().chunked(3).joinToString(".").reversed()}" } ?: "Tidak dijual di cabang", color = if (product.activeSellingPrice == null) AccountMuted else AccountTeal, fontWeight = FontWeight.Bold)
                        }
                        if (canManageCatalog) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = {
                                editingId = product.id
                                name = product.name
                                basePrice = formatNominalInput(product.basePrice.toString())
                                sellingPrice = product.activeSellingPrice?.let { formatNominalInput(it.toString()) } ?: ""
                                quantity = product.branchQuantity.toString()
                                barcode = ""
                                notice = "Saat edit, barcode lama dipertahankan jika kolom barcode dikosongkan."
                                formError = null
                            }) { Text("Edit") }
                            TextButton(onClick = {
                                scope.launch {
                                    runCatching { repository.deactivateProduct(tenant.storeId, product.id) }
                                        .onSuccess { notice = "Produk dinonaktifkan dari katalog." }
                                        .onFailure { notice = it.message ?: "Gagal menonaktifkan produk." }
                                }
                            }) { Text("Nonaktifkan", color = Color(0xFFB42318)) }
                        }
                    }
                }
            }
        }
    }
}
