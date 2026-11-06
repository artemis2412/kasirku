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
        branchPrice: BranchProductPriceEntity?,
        inventory: BranchInventoryEntity,
        barcodes: List<ProductBarcodeEntity>
    ) {
        require(product.storeId.isNotBlank()) { "Store wajib ditentukan." }
        require(inventory.branchId.isNotBlank()) { "Cabang stok wajib ditentukan." }
        require(branchPrice == null || (branchPrice.branchId == inventory.branchId && branchPrice.productId == product.id)) {
            "Relasi harga cabang tidak cocok."
        }
        require(inventory.productId == product.id) { "Relasi stok produk tidak cocok." }
        require(barcodes.all { it.storeId == product.storeId && it.productId == product.id }) {
            "Barcode harus berasal dari toko dan produk yang sama."
        }
        upsertProduct(product)
        if (branchPrice != null) upsertBranchPrice(branchPrice)
        else deleteBranchPrice(inventory.branchId, product.id)
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

    @Query("SELECT COUNT(*) FROM catalog_products WHERE storeId = :storeId AND isActive = 1")
    fun observeActiveProductCount(storeId: String): Flow<Int>

    @Query("SELECT COALESCE(SUM(quantity), 0) FROM branch_inventory WHERE branchId = :branchId")
    fun observeBranchStockTotal(branchId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM branch_inventory WHERE branchId = :branchId AND quantity <= :threshold")
    fun observeLowStockCount(branchId: String, threshold: Int = 5): Flow<Int>

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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertInventoriesIfMissing(inventory: List<BranchInventoryEntity>)

    @Query("SELECT * FROM branch_product_prices WHERE branchId = :branchId AND productId = :productId LIMIT 1")
    suspend fun getBranchPrice(branchId: String, productId: String): BranchProductPriceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBranchPrice(price: BranchProductPriceEntity)

    @Query("DELETE FROM branch_product_prices WHERE branchId = :branchId AND productId = :productId")
    suspend fun deleteBranchPrice(branchId: String, productId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBranchPrices(prices: List<BranchProductPriceEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertBranchPricesIfMissing(prices: List<BranchProductPriceEntity>)

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
        if (prices.isNotEmpty()) insertBranchPricesIfMissing(prices)
        if (inventory.isNotEmpty()) insertInventoriesIfMissing(inventory)
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


    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun enqueueSaleSync(item: SaleSyncOutboxEntity)

    @Query("SELECT * FROM sale_sync_outbox WHERE storeId = :storeId AND branchId = :branchId AND status IN ('PENDING', 'RETRY') ORDER BY createdAt ASC")
    suspend fun getPendingSaleSync(storeId: String, branchId: String): List<SaleSyncOutboxEntity>

    @Query("SELECT COUNT(*) FROM sale_sync_outbox WHERE storeId = :storeId AND branchId = :branchId AND status IN ('PENDING', 'RETRY')")
    fun observePendingSaleSyncCount(storeId: String, branchId: String): Flow<Int>

    @Query("""
        SELECT * FROM sale_sync_outbox
        WHERE storeId = :storeId AND branchId = :branchId
          AND status IN ('PENDING', 'RETRY', 'FAILED')
        ORDER BY createdAt DESC
        LIMIT 10
    """)
    fun observeSaleSyncQueue(storeId: String, branchId: String): Flow<List<SaleSyncOutboxEntity>>

    @Query("UPDATE sale_sync_outbox SET status = :status, attempts = attempts + 1, lastError = :error, updatedAt = :updatedAt WHERE saleId = :saleId AND storeId = :storeId AND branchId = :branchId")
    suspend fun updateSaleSyncStatus(storeId: String, branchId: String, saleId: String, status: String, error: String?, updatedAt: Long): Int

    @Query("UPDATE sale_sync_outbox SET status = 'SYNCED', lastError = NULL, updatedAt = :updatedAt WHERE saleId = :saleId AND storeId = :storeId AND branchId = :branchId")
    suspend fun markSaleSynced(storeId: String, branchId: String, saleId: String, updatedAt: Long): Int

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

    @Query("UPDATE store_tables SET occupiedSaleId = :saleId, occupiedAt = :occupiedAt, updatedAt = :occupiedAt WHERE id = :tableId AND storeId = :storeId AND branchId = :branchId AND name = :tableLabel AND isActive = 1 AND occupiedSaleId IS NULL")
    suspend fun occupyTable(storeId: String, branchId: String, tableId: String, tableLabel: String, saleId: String, occupiedAt: Long): Int

    @Transaction
    suspend fun completeSale(sale: SaleEntity, items: List<SaleItemEntity>) {
        require(items.isNotEmpty()) { "Keranjang masih kosong." }
        require(sale.storeId.isNotBlank() && sale.branchId.isNotBlank()) {
            "Toko dan cabang transaksi wajib ditentukan."
        }
        require(sale.subtotal > 0L && sale.discount >= 0L && sale.discount <= sale.subtotal) {
            "Subtotal atau diskon transaksi tidak valid."
        }
        require(sale.total == sale.subtotal - sale.discount && sale.total > 0L) {
            "Perhitungan total transaksi tidak valid."
        }
        require(sale.paid >= sale.total && sale.change == sale.paid - sale.total) {
            "Pembayaran atau uang kembalian tidak valid."
        }
        require(sale.paymentMethod in setOf("CASH", "QRIS", "TRANSFER")) {
            "Metode pembayaran tidak dikenali."
        }
        if (sale.paymentMethod != "CASH") {
            require(sale.paid == sale.total && sale.change == 0L) {
                "Pembayaran non-tunai harus sesuai total transaksi."
            }
        }

        if (sale.orderType == "Dine In") {
            require(!sale.tableLocalId.isNullOrBlank() && !sale.tableLabel.isNullOrBlank()) { "Meja wajib dipilih untuk Dine In." }
        } else {
            require(sale.orderType == "Takeaway" && sale.tableLocalId == null && sale.tableLabel == null) { "Data meja Takeaway tidak valid." }
        }

        var calculatedSubtotal = 0L
        items.forEach { item ->
            require(item.saleId == sale.id && item.quantity > 0 && item.unitPrice > 0L) {
                "Detail transaksi tidak valid."
            }
            val expectedLineTotal = Math.multiplyExact(item.quantity.toLong(), item.unitPrice)
            require(item.lineTotal == expectedLineTotal) {
                "Jumlah harga pada detail transaksi tidak sesuai."
            }
            calculatedSubtotal = Math.addExact(calculatedSubtotal, item.lineTotal)

            val product = getProduct(item.productId)
            require(product != null && product.storeId == sale.storeId && product.isActive) {
                "Produk tidak tersedia di toko aktif."
            }
            require(item.productName == product.name) {
                "Nama produk pada transaksi tidak sesuai katalog."
            }
            val branchPrice = getBranchPrice(sale.branchId, item.productId)
            val expectedPrice = branchPrice?.sellingPrice ?: product.basePrice
            require(item.unitPrice == expectedPrice) {
                "Harga produk berubah. Perbarui keranjang sebelum menyimpan transaksi."
            }

            val changed = deductStock(sale.branchId, item.productId, item.quantity, sale.createdAt)
            if (changed == 0) throw IllegalStateException("Stok produk tidak mencukupi.")
        }
        require(calculatedSubtotal == sale.subtotal) {
            "Subtotal transaksi tidak sama dengan rincian produk."
        }
        if (sale.orderType == "Dine In") {
            val occupied = occupyTable(sale.storeId, sale.branchId, sale.tableLocalId!!, sale.tableLabel!!, sale.id, sale.createdAt)
            check(occupied == 1) { "Meja sudah digunakan atau tidak tersedia. Pilih meja lain." }
        }
        insertSale(sale)
        insertSaleItems(items)
        enqueueSaleSync(SaleSyncOutboxEntity(saleId = sale.id, storeId = sale.storeId, branchId = sale.branchId, createdAt = sale.createdAt, updatedAt = sale.createdAt))
    }


    @Query("SELECT * FROM sales WHERE id = :saleId AND storeId = :storeId AND branchId = :branchId LIMIT 1")
    suspend fun getSaleForSync(storeId: String, branchId: String, saleId: String): SaleEntity?

    @Query("""
        SELECT * FROM sales
        WHERE storeId = :storeId AND branchId = :branchId
          AND createdAt >= :startAt AND createdAt < :endAt
        ORDER BY createdAt DESC
    """)
    fun observeSales(storeId: String, branchId: String, startAt: Long, endAt: Long): Flow<List<SaleEntity>>

    @Query("""
        SELECT COALESCE(SUM(i.quantity), 0) FROM sale_items AS i
        INNER JOIN sales AS s ON s.id = i.saleId
        WHERE s.storeId = :storeId AND s.branchId = :branchId
          AND s.createdAt >= :startAt AND s.createdAt < :endAt
    """)
    fun observeSoldQuantity(storeId: String, branchId: String, startAt: Long, endAt: Long): Flow<Int>

    @Query("""
        SELECT i.* FROM sale_items AS i
        INNER JOIN sales AS s ON s.id = i.saleId
        WHERE i.saleId = :saleId AND s.storeId = :storeId AND s.branchId = :branchId
        ORDER BY i.productName COLLATE NOCASE
    """)
    suspend fun getSaleItems(storeId: String, branchId: String, saleId: String): List<SaleItemEntity>

    @Query("""
        SELECT COUNT(*) FROM sales
        WHERE storeId = :storeId AND branchId = :branchId
          AND createdAt >= :startAt AND createdAt < :endAt
    """)
    fun observeSaleCount(storeId: String, branchId: String, startAt: Long, endAt: Long): Flow<Int>

    @Query("""
        SELECT COALESCE(SUM(total), 0) FROM sales
        WHERE storeId = :storeId AND branchId = :branchId
          AND createdAt >= :startAt AND createdAt < :endAt
    """)
    fun observeSalesTotal(storeId: String, branchId: String, startAt: Long, endAt: Long): Flow<Long>

    @Query("""
        SELECT * FROM sales
        WHERE storeId = :storeId AND branchId = :branchId
          AND createdAt >= :startAt AND createdAt < :endAt
        ORDER BY createdAt ASC
    """)
    suspend fun getSalesRange(storeId: String, branchId: String, startAt: Long, endAt: Long): List<SaleEntity>
}
