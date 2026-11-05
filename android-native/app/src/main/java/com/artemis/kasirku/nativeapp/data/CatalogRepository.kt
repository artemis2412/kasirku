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

    fun observeBranchCatalog(storeId: String, branchId: String, query: String): Flow<List<BranchCatalogRow>> {
        requireStore(storeId)
        requireBranch(branchId)
        return dao.observeBranchCatalog(storeId, branchId, query.trim())
    }

    suspend fun deactivateProduct(storeId: String, productId: String): Boolean {
        requireStore(storeId)
        require(productId.isNotBlank()) { "Produk tidak valid." }
        val product = dao.getProduct(productId) ?: return false
        require(product.storeId == storeId) { "Produk tidak termasuk toko aktif." }
        return dao.deactivateProduct(storeId, productId, System.currentTimeMillis()) > 0
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
        val existing = draft.id?.takeIf { it.isNotBlank() }?.let { dao.getProduct(it) }
        if (!draft.id.isNullOrBlank() && existing == null) {
            throw IllegalArgumentException("Produk yang akan diedit tidak ditemukan.")
        }
        if (existing != null && existing.storeId != storeId) {
            throw IllegalArgumentException("Produk tidak termasuk toko aktif.")
        }
        if (!draft.categoryId.isNullOrBlank()) {
            val category = dao.getActiveCategory(storeId, draft.categoryId)
            require(category != null) { "Kategori tidak aktif atau bukan milik toko ini." }
        }

        val submittedBarcodes = draft.barcodes.map(String::trim).filter(String::isNotEmpty).distinct()
        val normalizedBarcodes = if (submittedBarcodes.isEmpty() && existing != null) {
            dao.getBarcodes(storeId, productId).map { it.barcode }
        } else {
            submittedBarcodes
        }
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
