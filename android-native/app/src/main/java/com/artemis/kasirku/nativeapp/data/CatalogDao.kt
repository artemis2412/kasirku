package com.artemis.kasirku.nativeapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT * FROM catalog_products WHERE storeId = :storeId AND isActive = 1 ORDER BY name COLLATE NOCASE")
    fun observeProducts(storeId: String): Flow<List<ProductEntity>>


    @Query("""
        SELECT p.* FROM catalog_products AS p
        WHERE p.storeId = :storeId AND p.isActive = 1
          AND (:query = '' OR p.name LIKE '%' || :query || '%' COLLATE NOCASE
               OR EXISTS (
                   SELECT 1 FROM product_barcodes AS b
                   WHERE b.productId = p.id AND b.storeId = :storeId
                     AND b.barcode LIKE '%' || :query || '%'
               ))
        ORDER BY p.name COLLATE NOCASE
    """)
    fun searchProducts(storeId: String, query: String): Flow<List<ProductEntity>>

    @Query("SELECT * FROM product_barcodes WHERE productId = :productId AND storeId = :storeId")
    suspend fun getBarcodes(storeId: String, productId: String): List<ProductBarcodeEntity>

    @Query("DELETE FROM product_barcodes WHERE productId = :productId AND storeId = :storeId")
    suspend fun deleteBarcodesForProduct(storeId: String, productId: String)

    @Transaction
    suspend fun saveProductForBranch(
        product: ProductEntity,
        branchPrice: BranchProductPriceEntity,
        inventory: BranchInventoryEntity,
        barcodes: List<ProductBarcodeEntity>
    ) {
        require(product.storeId.isNotBlank()) { "Store wajib ditentukan." }
        require(branchPrice.branchId.isNotBlank() && inventory.branchId == branchPrice.branchId) {
            "Cabang harga dan stok harus sama."
        }
        require(branchPrice.productId == product.id && inventory.productId == product.id) {
            "Relasi produk tidak cocok."
        }
        require(barcodes.all { it.storeId == product.storeId && it.productId == product.id }) {
            "Barcode harus berasal dari toko dan produk yang sama."
        }
        upsertProduct(product)
        upsertBranchPrice(branchPrice)
        upsertInventory(inventory)
        deleteBarcodesForProduct(product.storeId, product.id)
        if (barcodes.isNotEmpty()) insertBarcodes(barcodes)
    }


    @Query("""
        SELECT p.*, 
            COALESCE((SELECT sellingPrice FROM branch_product_prices
                      WHERE branchId = :branchId AND productId = p.id), p.basePrice) AS activeSellingPrice,
            COALESCE((SELECT quantity FROM branch_inventory
                      WHERE branchId = :branchId AND productId = p.id), 0) AS branchQuantity
        FROM catalog_products AS p
        WHERE p.storeId = :storeId AND p.isActive = 1
          AND (:query = '' OR p.name LIKE '%' || :query || '%' COLLATE NOCASE
               OR EXISTS (SELECT 1 FROM product_barcodes AS b
                          WHERE b.storeId = :storeId AND b.productId = p.id
                            AND b.barcode LIKE '%' || :query || '%'))
        ORDER BY p.name COLLATE NOCASE
    """)
    fun observeBranchCatalog(storeId: String, branchId: String, query: String): Flow<List<BranchCatalogRow>>

    @Query("""
        UPDATE catalog_products SET isActive = 0, updatedAt = :updatedAt
        WHERE id = :productId AND storeId = :storeId AND isActive = 1
    """)
    suspend fun deactivateProduct(storeId: String, productId: String, updatedAt: Long): Int

    @Query("SELECT * FROM catalog_products WHERE id = :productId LIMIT 1")
    suspend fun getProduct(productId: String): ProductEntity?

    @Query("SELECT * FROM catalog_categories WHERE id = :categoryId AND storeId = :storeId AND isActive = 1 LIMIT 1")
    suspend fun getActiveCategory(storeId: String, categoryId: String): CategoryEntity?

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBranchPrices(prices: List<BranchProductPriceEntity>)

    @Query("SELECT * FROM product_barcodes WHERE storeId = :storeId AND barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(storeId: String, barcode: String): ProductBarcodeEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBarcodes(barcodes: List<ProductBarcodeEntity>)

    @Transaction
    suspend fun importLegacyCatalog(
        categories: List<CategoryEntity>,
        products: List<ProductEntity>,
        prices: List<BranchProductPriceEntity>,
        inventory: List<BranchInventoryEntity>,
        barcodes: List<ProductBarcodeEntity>
    ) {
        if (categories.isNotEmpty()) upsertCategories(categories)
        if (products.isNotEmpty()) upsertProducts(products)
        if (prices.isNotEmpty()) upsertBranchPrices(prices)
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

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSale(sale: SaleEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertSaleItems(items: List<SaleItemEntity>)

    @Query("""
        UPDATE branch_inventory
        SET quantity = quantity - :quantity, updatedAt = :updatedAt
        WHERE branchId = :branchId AND productId = :productId AND quantity >= :quantity
    """)
    suspend fun deductStock(branchId: String, productId: String, quantity: Int, updatedAt: Long): Int

    @Transaction
    suspend fun completeSale(sale: SaleEntity, items: List<SaleItemEntity>) {
        require(items.isNotEmpty()) { "Keranjang masih kosong." }
        require(sale.total > 0L && sale.paid >= sale.total) { "Pembayaran belum mencukupi." }
        items.forEach { item ->
            require(item.saleId == sale.id && item.quantity > 0 && item.unitPrice > 0L) {
                "Detail transaksi tidak valid."
            }
            val product = getProduct(item.productId)
            require(product != null && product.storeId == sale.storeId && product.isActive) {
                "Produk tidak tersedia di toko aktif."
            }
            val changed = deductStock(sale.branchId, item.productId, item.quantity, sale.createdAt)
            if (changed == 0) throw IllegalStateException("Stok produk tidak mencukupi.")
        }
        insertSale(sale)
        insertSaleItems(items)
    }

}
