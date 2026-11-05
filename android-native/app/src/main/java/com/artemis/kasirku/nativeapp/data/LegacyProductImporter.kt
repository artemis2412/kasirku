package com.artemis.kasirku.nativeapp.data

import android.content.Context
import com.artemis.kasirku.nativeapp.ProductDatabase
import java.nio.charset.StandardCharsets
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class LegacyImportResult(
    val sourceCount: Int,
    val importedCount: Int,
    val alreadyImportedCount: Int,
    val skippedBarcodeCount: Int,
    val costValuesKeptInLegacyDatabase: Int
) {
    val countVerified: Boolean get() =
        sourceCount == importedCount + alreadyImportedCount
}

/**
 * Copies legacy products only after authenticated store and branch context
 * are supplied. It never copies cost prices into ordinary catalog tables.
 * The legacy database is read-only in this process and is never deleted.
 */
class LegacyProductImporter(
    private val context: Context,
    private val database: KasirkuRoomDatabase
) {
    suspend fun import(storeId: String, branchId: String): LegacyImportResult = withContext(Dispatchers.IO) {
        require(storeId.isNotBlank()) { "Store belum dipilih." }
        require(branchId.isNotBlank()) { "Cabang belum dipilih." }

        val legacy = ProductDatabase(context.applicationContext)
        val source = try { legacy.getAllProducts() } finally { legacy.close() }
        val now = System.currentTimeMillis()

        val categories = source.map { it.category.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .mapIndexed { index, categoryName ->
                CategoryEntity(
                    id = stableId("category:$storeId:${categoryName.lowercase()}"),
                    storeId = storeId,
                    name = categoryName,
                    sortOrder = index,
                    updatedAt = now
                )
            }
        val categoryIds = categories.associate { it.name.lowercase() to it.id }

        val products = source.map { old ->
            ProductEntity(
                id = stableId("product:$storeId:legacy:${old.id}"),
                storeId = storeId,
                categoryId = categoryIds[old.category.trim().lowercase()],
                name = old.name.trim(),
                basePrice = old.sellingPrice.coerceAtLeast(0L),
                photoUri = old.photoUri.takeIf { it.isNotBlank() },
                legacySourceId = old.id,
                createdAt = now,
                updatedAt = now
            )
        }
        val ids = source.associate { it.id to stableId("product:$storeId:legacy:${it.id}") }
        val branchPrices = source.map { old ->
            BranchProductPriceEntity(
                branchId = branchId,
                productId = ids.getValue(old.id),
                sellingPrice = old.sellingPrice.coerceAtLeast(0L),
                updatedAt = now
            )
        }
        val inventory = source.map { old ->
            BranchInventoryEntity(
                branchId = branchId,
                productId = ids.getValue(old.id),
                quantity = old.stock.coerceAtLeast(0),
                updatedAt = now
            )
        }

        val seen = mutableSetOf<String>()
        var skippedBarcodes = 0
        val barcodes = source.mapNotNull { old ->
            val value = old.barcode.trim()
            if (value.isEmpty()) return@mapNotNull null
            if (!seen.add(value)) {
                skippedBarcodes++
                return@mapNotNull null
            }
            ProductBarcodeEntity(
                id = stableId("barcode:$storeId:${old.id}:$value"),
                storeId = storeId,
                productId = ids.getValue(old.id),
                barcode = value,
                createdAt = now
            )
        }

        val dao = database.catalogDao()
        val before = dao.findImportedLegacyIds(storeId).toSet()
        val already = source.count { it.id in before }
        val newProducts = products.filter { it.legacySourceId !in before }
        val newIds = newProducts.map { it.id }.toSet()
        dao.importLegacyCatalog(
            categories,
            newProducts,
            branchPrices,
            inventory,
            barcodes.filter { it.productId in newIds }
        )
        val after = dao.findImportedLegacyIds(storeId).toSet()
        val imported = source.count { it.id in after } - already

        LegacyImportResult(
            sourceCount = source.size,
            importedCount = imported,
            alreadyImportedCount = already,
            skippedBarcodeCount = skippedBarcodes,
            costValuesKeptInLegacyDatabase = source.count { it.costPrice >= 0L }
        )
    }

    private fun stableId(value: String): String =
        UUID.nameUUIDFromBytes(value.toByteArray(StandardCharsets.UTF_8)).toString()
}
