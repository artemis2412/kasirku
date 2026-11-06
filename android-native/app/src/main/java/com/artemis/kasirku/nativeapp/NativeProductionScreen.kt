package com.artemis.kasirku.nativeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.artemis.kasirku.nativeapp.data.CloudAuthRepository
import com.artemis.kasirku.nativeapp.data.ProductionCloudSnapshot
import com.artemis.kasirku.nativeapp.data.ProductionIngredientInputDto
import com.artemis.kasirku.nativeapp.data.MenuIngredientInputDto
import com.artemis.kasirku.nativeapp.data.VerifiedTenantContext
import com.artemis.kasirku.nativeapp.data.StoreRole
import kotlinx.coroutines.launch

// Shared production UI for branch-scoped inventory and recipes.
private val ProductionNavy = Color(0xFF17324D)
private val ProductionTeal = Color(0xFF168C83)
private val ProductionMuted = Color(0xFF718096)

private data class ProductionIngredientDraft(val itemId: String = "", val quantity: String = "")

@Composable
internal fun NativeProductionScreen(
    tenant: VerifiedTenantContext,
    modifier: Modifier = Modifier
) {
    val repository = remember { CloudAuthRepository() }
    val scope = rememberCoroutineScope()
    val canManage = tenant.role in setOf(StoreRole.OWNER, StoreRole.ADMIN, StoreRole.SUPERVISOR)
    var snapshot by remember { mutableStateOf<ProductionCloudSnapshot?>(null) }
    var loading by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }

    var itemName by remember { mutableStateOf("") }
    var itemType by remember { mutableStateOf("raw") }
    var itemUnit by remember { mutableStateOf("ekor") }
    var minimumStock by remember { mutableStateOf("0") }

    var stockItemId by remember { mutableStateOf("") }
    var stockMovementType by remember { mutableStateOf("purchase") }
    var stockQuantity by remember { mutableStateOf("") }
    var stockUnitCost by remember { mutableStateOf("0") }
    var stockNote by remember { mutableStateOf("") }

    var recipeName by remember { mutableStateOf("") }
    var recipeOutputId by remember { mutableStateOf("") }
    var recipeYield by remember { mutableStateOf("10") }
    var recipeNotes by remember { mutableStateOf("") }
    var recipeIngredients by remember { mutableStateOf(listOf(ProductionIngredientDraft())) }

    var selectedRecipeId by remember { mutableStateOf("") }
    var productionOutput by remember { mutableStateOf("") }
    var productionNotes by remember { mutableStateOf("") }

    var menuProductId by remember { mutableStateOf("") }
    var menuIngredients by remember { mutableStateOf(listOf(ProductionIngredientDraft())) }

    suspend fun reload() {
        loading = true
        error = null
        try {
            snapshot = repository.loadProductionSnapshot(tenant)
            if (stockItemId.isBlank()) stockItemId = snapshot?.items?.firstOrNull()?.id.orEmpty()
            if (recipeOutputId.isBlank()) recipeOutputId = snapshot?.items?.firstOrNull { it.itemType == "prepared" }?.id.orEmpty()
            if (selectedRecipeId.isBlank()) selectedRecipeId = snapshot?.recipes?.firstOrNull()?.id.orEmpty()
            if (menuProductId.isBlank()) menuProductId = snapshot?.products?.firstOrNull()?.id.orEmpty()
        } catch (failure: Exception) {
            error = failure.message ?: "Gagal memuat data produksi."
        } finally {
            loading = false
        }
    }

    LaunchedEffect(tenant.storeId, tenant.branchId) { reload() }

    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("OPERASIONAL DAPUR", color = ProductionTeal, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
        Text("Produksi & Resep", color = ProductionNavy, fontSize = 25.sp, fontWeight = FontWeight.Bold)
        Text(tenant.storeName + " • " + tenant.branchName, color = ProductionMuted, fontSize = 12.sp)
        OutlinedButton(onClick = { scope.launch { reload() } }, enabled = !loading) {
            Text(if (loading) "Memuat…" else "↻ Perbarui data")
        }
        notice?.let { Text(it, color = ProductionTeal, fontSize = 12.sp) }
        error?.let { Text(it, color = Color(0xFFB42318), fontSize = 12.sp) }

        val data = snapshot
        if (data == null && loading) {
            Text("Memuat bahan, resep, dan riwayat…", color = ProductionMuted, fontSize = 13.sp)
        } else if (data != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SummaryCard("Bahan mentah", data.items.count { it.itemType == "raw" }.toString(), Modifier.weight(1f))
                SummaryCard("Siap pakai", data.items.count { it.itemType == "prepared" }.toString(), Modifier.weight(1f))
                SummaryCard("Resep", data.recipes.size.toString(), Modifier.weight(1f))
            }

            if (canManage) {
                SectionCard("Tambah bahan baku / hasil olahan") {
                    OutlinedTextField(itemName, { itemName = it }, label = { Text("Nama bahan") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    SelectField("Jenis bahan", listOf("raw" to "Bahan mentah", "prepared" to "Bahan siap pakai"), itemType) { itemType = it }
                    OutlinedTextField(itemUnit, { itemUnit = it }, label = { Text("Satuan (kg, gram, ekor, porsi)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(minimumStock, { minimumStock = it }, label = { Text("Stok minimum") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(
                        onClick = {
                            val min = minimumStock.toDoubleOrNull()
                            if (itemName.isBlank() || itemUnit.isBlank() || min == null || min < 0) {
                                error = "Nama, satuan, dan stok minimum harus valid."
                            } else scope.launch {
                                saving = true; error = null; notice = null
                                runCatching { repository.createProductionInventoryItem(tenant, itemName, itemType, itemUnit, min) }
                                    .onSuccess { itemName = ""; notice = "Bahan berhasil ditambahkan."; reload() }
                                    .onFailure { error = it.message ?: "Gagal menambahkan bahan." }
                                saving = false
                            }
                        }, enabled = !saving && itemName.isNotBlank(), modifier = Modifier.fillMaxWidth()
                    ) { Text(if (saving) "Menyimpan…" else "Simpan bahan") }
                }

                SectionCard("Tambah / kurangi stok") {
                    SelectField("Bahan", data.items.map { it.id to (it.name + " · " + it.baseUnit) }, stockItemId) { stockItemId = it }
                    SelectField("Jenis perubahan", listOf("purchase" to "Stok masuk", "waste" to "Bahan terbuang", "adjustment" to "Penyesuaian"), stockMovementType) { stockMovementType = it }
                    OutlinedTextField(stockQuantity, { stockQuantity = it }, label = { Text("Jumlah") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(stockUnitCost, { stockUnitCost = it }, label = { Text("Biaya per satuan (Rp)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(stockNote, { stockNote = it }, label = { Text("Catatan (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        val q = stockQuantity.toDoubleOrNull()
                        val cost = stockUnitCost.toDoubleOrNull() ?: 0.0
                        if (stockItemId.isBlank() || q == null || q <= 0 || cost < 0) error = "Bahan, jumlah, atau biaya tidak valid."
                        else scope.launch {
                            saving = true; error = null; notice = null
                            val delta = if (stockMovementType == "waste") -q else q
                            runCatching { repository.adjustProductionStock(tenant, stockItemId, delta, stockMovementType, cost, stockNote) }
                                .onSuccess { stockQuantity = ""; notice = "Perubahan stok berhasil dicatat."; reload() }
                                .onFailure { error = it.message ?: "Gagal memperbarui stok." }
                            saving = false
                        }
                    }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Simpan perubahan stok") }
                }

                SectionCard("Buat resep produksi") {
                    OutlinedTextField(recipeName, { recipeName = it }, label = { Text("Nama resep") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    SelectField("Hasil produksi", data.items.filter { it.itemType == "prepared" }.map { it.id to (it.name + " · " + it.baseUnit) }, recipeOutputId) { recipeOutputId = it }
                    OutlinedTextField(recipeYield, { recipeYield = it }, label = { Text("Hasil standar") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("Bahan yang digunakan", color = ProductionNavy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    IngredientEditor(data.items, recipeIngredients) { recipeIngredients = it }
                    OutlinedTextField(recipeNotes, { recipeNotes = it }, label = { Text("Catatan (opsional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        val yield = recipeYield.toDoubleOrNull()
                        val ingredients = recipeIngredients.mapNotNull { row ->
                            val q = row.quantity.toDoubleOrNull()
                            if (row.itemId.isBlank() || q == null || q <= 0) null else ProductionIngredientInputDto(row.itemId, q)
                        }
                        if (recipeName.isBlank() || recipeOutputId.isBlank() || yield == null || yield <= 0 || ingredients.isEmpty()) {
                            error = "Nama resep, hasil standar, bahan hasil, dan takaran wajib valid."
                        } else scope.launch {
                            saving = true; error = null; notice = null
                            runCatching { repository.saveProductionRecipe(tenant, null, recipeName, recipeOutputId, yield, recipeNotes, ingredients) }
                                .onSuccess { recipeName = ""; recipeNotes = ""; recipeIngredients = listOf(ProductionIngredientDraft()); notice = "Resep produksi berhasil disimpan."; reload() }
                                .onFailure { error = it.message ?: "Gagal menyimpan resep." }
                            saving = false
                        }
                    }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Simpan resep produksi") }
                }

                SectionCard("Catat produksi") {
                    SelectField("Pilih resep", data.recipes.map { it.id to it.name }, selectedRecipeId) { selectedRecipeId = it }
                    OutlinedTextField(productionOutput, { productionOutput = it }, label = { Text("Jumlah hasil aktual") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(productionNotes, { productionNotes = it }, label = { Text("Catatan produksi") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Button(onClick = {
                        val qty = productionOutput.toDoubleOrNull()
                        if (selectedRecipeId.isBlank() || qty == null || qty <= 0) error = "Pilih resep dan isi hasil produksi yang valid."
                        else scope.launch {
                            saving = true; error = null; notice = null
                            runCatching { repository.completeProduction(tenant, selectedRecipeId, qty, productionNotes) }
                                .onSuccess { productionOutput = ""; productionNotes = ""; notice = "Produksi berhasil dicatat. Stok bahan dan hasil olahan diperbarui."; reload() }
                                .onFailure { error = it.message ?: "Produksi gagal dicatat." }
                            saving = false
                        }
                    }, enabled = !saving && data.recipes.isNotEmpty(), modifier = Modifier.fillMaxWidth()) { Text("Konfirmasi produksi") }
                }

                SectionCard("Hubungkan resep dengan menu jualan") {
                    SelectField("Menu jualan", data.products.map { it.id to it.name }, menuProductId) { menuProductId = it }
                    Text("Takaran bahan per satu menu", color = ProductionNavy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    IngredientEditor(data.items, menuIngredients) { menuIngredients = it }
                    Button(onClick = {
                        val ingredients = menuIngredients.mapNotNull { row ->
                            val q = row.quantity.toDoubleOrNull()
                            if (row.itemId.isBlank() || q == null || q <= 0) null else MenuIngredientInputDto(row.itemId, q)
                        }
                        if (menuProductId.isBlank() || ingredients.isEmpty()) error = "Pilih menu dan minimal satu bahan."
                        else scope.launch {
                            saving = true; error = null; notice = null
                            runCatching { repository.replaceMenuRecipe(tenant, menuProductId, ingredients) }
                                .onSuccess { notice = "Resep menu berhasil disimpan."; menuIngredients = listOf(ProductionIngredientDraft()); reload() }
                                .onFailure { error = it.message ?: "Gagal menyimpan resep menu." }
                            saving = false
                        }
                    }, enabled = !saving, modifier = Modifier.fillMaxWidth()) { Text("Simpan resep menu") }
                }
            } else {
                SectionCard("Akses terbatas") {
                    Text("Akun Kasir dapat melihat bahan dan resep. Pengelolaan stok dan produksi hanya tersedia untuk Owner, Admin, dan Supervisor.", color = ProductionMuted, fontSize = 12.sp)
                }
            }

            SectionCard("Stok bahan") {
                if (data.items.isEmpty()) Text("Belum ada bahan terdaftar.", color = ProductionMuted, fontSize = 12.sp)
                data.items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(item.name, color = ProductionNavy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Text((if (item.itemType == "raw") "Mentah" else "Siap pakai") + " · " + item.baseUnit, color = ProductionMuted, fontSize = 10.sp)
                        }
                        Text("${item.onHand} ${item.baseUnit}", color = ProductionNavy, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    }
                }
            }

            SectionCard("Resep dan riwayat produksi") {
                data.recipes.forEach { recipe ->
                    val output = data.items.firstOrNull { it.id == recipe.outputItemId }
                    Text(recipe.name, color = ProductionNavy, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Hasil: ${recipe.yieldQuantity} ${output?.baseUnit.orEmpty()}", color = ProductionMuted, fontSize = 11.sp)
                    data.recipeItems.filter { it.recipeId == recipe.id }.forEach { ingredient ->
                        val item = data.items.firstOrNull { it.id == ingredient.inventoryItemId }
                        Text("• ${item?.name ?: "Bahan"}: ${ingredient.quantity} ${item?.baseUnit.orEmpty()}", color = ProductionMuted, fontSize = 11.sp)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (data.batches.isEmpty()) Text("Belum ada riwayat produksi.", color = ProductionMuted, fontSize = 12.sp)
                data.batches.take(10).forEach { batch ->
                    val recipe = data.recipes.firstOrNull { it.id == batch.recipeId }
                    val output = data.items.firstOrNull { it.id == batch.outputItemId }
                    Text("${recipe?.name ?: "Produksi"} · +${batch.actualOutputQuantity} ${output?.baseUnit.orEmpty()}", color = ProductionNavy, fontSize = 12.sp)
                    Text(batch.completedAt, color = ProductionMuted, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(15.dp)) {
        Column(Modifier.fillMaxWidth().padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, color = ProductionNavy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun SummaryCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(13.dp)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = ProductionMuted, fontSize = 10.sp)
            Text(value, color = ProductionNavy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun SelectField(label: String, options: List<Pair<String, String>>, selected: String, onSelected: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val current = options.firstOrNull { it.first == selected }?.second ?: "Pilih $label"
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label: $current", maxLines = 1)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option.second) }, onClick = { onSelected(option.first); expanded = false })
            }
        }
    }
}

@Composable
private fun IngredientEditor(
    items: List<com.artemis.kasirku.nativeapp.data.ProductionInventoryItemDto>,
    rows: List<ProductionIngredientDraft>,
    onChange: (List<ProductionIngredientDraft>) -> Unit
) {
    rows.forEachIndexed { index, row ->
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            SelectField("Bahan ${index + 1}", items.map { it.id to (it.name + " · " + it.baseUnit) }, row.itemId) { id ->
                onChange(rows.toMutableList().also { it[index] = row.copy(itemId = id) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = row.quantity,
                    onValueChange = { value -> onChange(rows.toMutableList().also { it[index] = row.copy(quantity = value) }) },
                    label = { Text("Takaran bahan") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                if (rows.size > 1) TextButton(onClick = { onChange(rows.filterIndexed { i, _ -> i != index }) }) { Text("Hapus") }
            }
        }
    }
    TextButton(onClick = { onChange(rows + ProductionIngredientDraft()) }) { Text("＋ Tambah bahan") }
}
