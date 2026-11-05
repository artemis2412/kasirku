package com.artemis.kasirku.nativeapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_products WHERE storeId = :storeId AND isActive = 1 ORDER BY name COLLATE NOCASE")
    fun observeProducts(storeId: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM catalog_products WHERE id = :productId LIMIT 1")
    suspend fun getProduct(productId: String): ProductEntity?

    @Query("SELECT legacySourceId FROM catalog_products WHERE storeId = :storeId AND legacySourceId IS NOT NULL")
    suspend fun findImportedLegacyIds(storeId: String): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProduct(product: ProductEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProducts(products: List<ProductEntity>)

    @Query("SELECT * FROM catalog_categories WHERE storeId = :storeId AND isActive = 1 ORDER BY sortOrder, name COLLATE NOCASE")
    fun observeCategories(storeId: String): Flow<List<CategoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategories(categories: List<CategoryEntity>)

    @Query("SELECT * FROM branch_inventory WHERE branchId = :branchId AND productId = :productId LIMIT 1")
    suspend fun getInventory(branchId: String, productId: String): BranchInventoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInventory(inventory: BranchInventoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertInventories(inventory: List<BranchInventoryEntity>)

    @Query("SELECT * FROM branch_product_prices WHERE branchId = :branchId AND productId = :productId LIMIT 1")
    suspend fun getBranchPrice(branchId: String, productId: String): BranchProductPriceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBranchPrice(price: BranchProductPriceEntity)

    @Query("SELECT * FROM product_barcodes WHERE storeId = :storeId AND barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(storeId: String, barcode: String): ProductBarcodeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBarcodes(barcodes: List<ProductBarcodeEntity>)

    @Transaction
    suspend fun importLegacyCatalog(
        categories: List<CategoryEntity>,
        products: List<ProductEntity>,
        inventory: List<BranchInventoryEntity>,
        barcodes: List<ProductBarcodeEntity>
    ) {
        if (categories.isNotEmpty()) upsertCategories(categories)
        if (products.isNotEmpty()) upsertProducts(products)
        if (inventory.isNotEmpty()) upsertInventories(inventory)
        if (barcodes.isNotEmpty()) insertBarcodes(barcodes)
    }

    @Transaction
    suspend fun saveCatalog(
        products: List<ProductEntity>,
        categories: List<CategoryEntity>,
        inventory: List<BranchInventoryEntity>
    ) {
        if (categories.isNotEmpty()) upsertCategories(categories)
        if (products.isNotEmpty()) upsertProducts(products)
        if (inventory.isNotEmpty()) upsertInventories(inventory)
    }
}
