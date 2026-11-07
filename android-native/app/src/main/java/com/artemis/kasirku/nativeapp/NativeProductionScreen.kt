package com.artemis.kasirku.nativeapp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.artemis.kasirku.nativeapp.data.*
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
internal fun NativeProductionScreen(
    tenant: VerifiedTenantContext,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val db = remember { KasirkuRoomDatabase.getInstance(context) }
    val dao = remember { db.productionDao() }
    val catalog = remember { CatalogRepository(db.catalogDao()) }
    val products by remember(tenant.storeId) { catalog.observeProducts(tenant.storeId) }.collectAsState(initial = emptyList())
    val ingredients by remember(tenant.storeId) { dao.observeIngredients(tenant.storeId) }.collectAsState(initial = emptyList())
    val stock by remember(tenant.storeId, tenant.branchId) { dao.observeIngredientStock(tenant.storeId, tenant.branchId) }.collectAsState(initial = emptyList())
    val recipes by remember(tenant.storeId) { dao.observeRecipes(tenant.storeId) }.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val canProduce = tenant.role in setOf(StoreRole.OWNER, StoreRole.ADMIN, StoreRole.SUPERVISOR)
    var ingredientName by remember { mutableStateOf("") }
    var ingredientUnit by remember { mutableStateOf("gram") }
    var ingredientQuantity by remember { mutableStateOf("0") }
    var selectedIngredientId by remember { mutableStateOf<String?>(null) }
    var selectedProductId by remember { mutableStateOf<String?>(null) }
    var recipeName by remember { mutableStateOf("") }
    var recipeUsage by remember { mutableStateOf("100") }
    var productionQuantity by remember { mutableStateOf("1") }
    var selectedRecipeId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("BAHAN BAKU & PRODUKSI", color = Color(0xFF168C83), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Produksi", color = Color(0xFF17324D), fontSize = 25.sp, fontWeight = FontWeight.Bold)
                Text(tenant.storeName + " • " + tenant.branchName, color = Color(0xFF718096), fontSize = 12.sp)
            }
            TextButton(onClick = onBack) { Text("Kembali") }
        }
        if (!canProduce) {
            Text("Modul produksi hanya dapat digunakan Owner, Admin, dan Supervisor.", color = Color(0xFFB42318))
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Bahan baku", fontWeight = FontWeight.Bold, color = Color(0xFF17324D))
                    OutlinedTextField(ingredientName, { ingredientName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nama bahan") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(ingredientUnit, { ingredientUnit = it }, modifier = Modifier.weight(1f), label = { Text("Satuan") }, singleLine = true)
                        OutlinedTextField(ingredientQuantity, { ingredientQuantity = it.filter { ch -> ch.isDigit() || ch == '.' } }, modifier = Modifier.weight(1f), label = { Text("Stok awal cabang") }, singleLine = true)
                    }
                    Button(enabled = !busy && ingredientName.isNotBlank() && ingredientUnit.isNotBlank(), onClick = {
                        scope.launch {
                            busy = true
                            runCatching {
                                val id = UUID.randomUUID().toString()
                                val now = System.currentTimeMillis()
                                dao.putIngredient(IngredientEntity(id, tenant.storeId, ingredientName.trim(), ingredientUnit.trim(), true, now))
                                dao.putIngredientStock(BranchIngredientInventoryEntity(tenant.storeId, tenant.branchId, id, ingredientQuantity.toDoubleOrNull() ?: 0.0, now))
                            }.onSuccess { ingredientName = ""; ingredientQuantity = "0"; status = "Bahan baku disimpan." }
                                .onFailure { status = it.message ?: "Gagal menyimpan bahan baku." }
                            busy = false
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Simpan bahan baku") }
                    ingredients.forEach { item ->
                        val amount = stock.firstOrNull { it.ingredientId == item.id }?.quantity ?: 0.0
                        Text(item.name + ": " + amount + " " + item.unit, color = Color(0xFF475467), fontSize = 12.sp)
                    }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("2. Buat resep sederhana", fontWeight = FontWeight.Bold, color = Color(0xFF17324D))
                    var productMenu by remember { mutableStateOf(false) }
                    var ingredientMenu by remember { mutableStateOf(false) }
                    TextButton(onClick = { productMenu = true }) { Text(products.firstOrNull { it.id == selectedProductId }?.name ?: "Pilih produk jadi") }
                    DropdownMenu(expanded = productMenu, onDismissRequest = { productMenu = false }) {
                        products.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { selectedProductId = item.id; productMenu = false }) }
                    }
                    TextButton(onClick = { ingredientMenu = true }) { Text(ingredients.firstOrNull { it.id == selectedIngredientId }?.name ?: "Pilih satu bahan baku") }
                    DropdownMenu(expanded = ingredientMenu, onDismissRequest = { ingredientMenu = false }) {
                        ingredients.forEach { item -> DropdownMenuItem(text = { Text(item.name + " (" + item.unit + ")") }, onClick = { selectedIngredientId = item.id; ingredientMenu = false }) }
                    }
                    OutlinedTextField(recipeName, { recipeName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nama resep") }, singleLine = true)
                    OutlinedTextField(recipeUsage, { recipeUsage = it.filter { ch -> ch.isDigit() || ch == '.' } }, modifier = Modifier.fillMaxWidth(), label = { Text("Kebutuhan bahan per hasil resep") }, singleLine = true)
                    Button(enabled = !busy && selectedProductId != null && selectedIngredientId != null && recipeName.isNotBlank() && (recipeUsage.toDoubleOrNull() ?: 0.0) > 0.0, onClick = {
                        scope.launch {
                            busy = true
                            runCatching {
                                val now = System.currentTimeMillis()
                                val recipeId = UUID.randomUUID().toString()
                                dao.putRecipe(ProductRecipeEntity(recipeId, tenant.storeId, selectedProductId!!, recipeName.trim(), 1, true, now))
                                dao.putRecipeItem(ProductRecipeItemEntity(UUID.randomUUID().toString(), tenant.storeId, recipeId, selectedIngredientId!!, recipeUsage.toDouble(), now))
                                selectedRecipeId = recipeId
                            }.onSuccess { status = "Resep disimpan. Hasil resep: 1 unit." }
                                .onFailure { status = it.message ?: "Gagal menyimpan resep." }
                            busy = false
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Simpan resep") }
                }
            }
            Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("3. Produksi produk jadi", fontWeight = FontWeight.Bold, color = Color(0xFF17324D))
                    var recipeMenu by remember { mutableStateOf(false) }
                    TextButton(onClick = { recipeMenu = true }) { Text(recipes.firstOrNull { it.id == selectedRecipeId }?.name ?: "Pilih resep") }
                    DropdownMenu(expanded = recipeMenu, onDismissRequest = { recipeMenu = false }) {
                        recipes.forEach { item -> DropdownMenuItem(text = { Text(item.name) }, onClick = { selectedRecipeId = item.id; recipeMenu = false }) }
                    }
                    OutlinedTextField(productionQuantity, { productionQuantity = it.filter(Char::isDigit) }, modifier = Modifier.fillMaxWidth(), label = { Text("Jumlah produk yang dibuat") }, singleLine = true)
                    Button(enabled = !busy && selectedRecipeId != null && (productionQuantity.toIntOrNull() ?: 0) > 0, onClick = {
                        scope.launch {
                            busy = true
                            runCatching { dao.completeProduction(tenant.storeId, tenant.branchId, tenant.userId, selectedRecipeId!!, productionQuantity.toInt(), null) }
                                .onSuccess {
                                    ProductionSyncWorker.enqueue(context, tenant)
                                    status = "Produksi tersimpan lokal. Menunggu sinkronisasi Cloud; ID batch: " + it
                                }
                                .onFailure { status = it.message ?: "Produksi gagal." }
                            busy = false
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Memproses…" else "Proses produksi") }
                }
            }
        }
        status?.let { Text(it, color = Color(0xFF168C83), fontSize = 12.sp) }
    }
}
