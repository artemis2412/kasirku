package com.artemis.kasirku.nativeapp.data

import java.util.UUID
import kotlinx.coroutines.flow.Flow

/**
 * Store- and branch-scoped catalog operations for the native UI.
 * Callers must pass IDs from the authenticated/selected tenant context.
 */
data class CatalogProductDraft(
    val id: String? = null,
    val name: String,
    val categoryId: String? = null,
    val description: String = "",
    val basePrice: Long,
    val sellingPrice: Long,
    val quantity: Int,
    val photoUri: String? = null,
    val barcodes: List<String> = emptyList()
)

class CatalogRepository(
    private val dao: CatalogDao
) {
    fun observeProducts(storeId: String): Flow<List<ProductEntity>> {
        requireStore(storeId)
        return dao.observeProducts(storeId)
    }

    fun searchProducts(storeId: String, query: String): Flow<List<ProductEntity>> {
        requireStore(storeId)
        return dao.searchProducts(storeId, query.trim())
    }

    fun observeCategories(storeId: String): Flow<List<CategoryEntity>> {
        requireStore(storeId)
        return dao.observeCategories(storeId)
    }

    suspend fun saveProduct(
        storeId: String,
        branchId: String,
        draft: CatalogProductDraft
    ): String {
        requireStore(storeId)
        requireBranch(branchId)
        require(draft.name.isNotBlank()) { "Nama produk wajib diisi." }
        require(draft.basePrice >= 0L && draft.sellingPrice > 0L) { "Harga produk tidak valid." }
        require(draft.quantity >= 0) { "Stok tidak boleh negatif." }

        val productId = draft.id?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val existing = draft.id?.let { dao.getProduct(it) }
        if (existing != null && existing.storeId != storeId) {
            throw IllegalArgumentException("Produk tidak termasuk toko aktif.")
        }

        val normalizedBarcodes = draft.barcodes.map(String::trim).filter(String::isNotEmpty).distinct()
        normalizedBarcodes.forEach { barcode ->
            val owner = dao.findByBarcode(storeId, barcode)
            require(owner == null || owner.productId == productId) {
                "Barcode $barcode sudah digunakan produk lain."
            }
        }

        val product = ProductEntity(
            id = productId,
            storeId = storeId,
            categoryId = draft.categoryId,
            name = draft.name.trim(),
            description = draft.description.trim(),
            basePrice = draft.basePrice,
            photoUri = draft.photoUri?.takeIf(String::isNotBlank),
            legacySourceId = existing?.legacySourceId,
            isActive = true,
            createdAt = existing?.createdAt ?: now,
            updatedAt = now
        )
        val barcodes = normalizedBarcodes.map { value ->
            ProductBarcodeEntity(
                id = UUID.nameUUIDFromBytes("$storeId:$productId:$value".toByteArray()).toString(),
                storeId = storeId,
                productId = productId,
                barcode = value,
                createdAt = now
            )
        }

        dao.saveProductForBranch(
            product = product,
            branchPrice = BranchProductPriceEntity(branchId, productId, draft.sellingPrice, now),
            inventory = BranchInventoryEntity(branchId, productId, draft.quantity, now),
            barcodes = barcodes
        )
        return productId
    }

    private fun requireStore(storeId: String) {
        require(storeId.isNotBlank()) { "Toko aktif belum terverifikasi." }
    }

    private fun requireBranch(branchId: String) {
        require(branchId.isNotBlank()) { "Cabang aktif belum terverifikasi." }
    }
}
