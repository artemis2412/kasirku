package com.artemis.kasirku.nativeapp.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_products", indices = [
    Index(value = ["storeId", "name"]),
    Index(value = ["storeId", "legacySourceId"], unique = true)
])
data class ProductEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val categoryId: String?,
    val name: String,
    val description: String = "",
    val basePrice: Long,
    val photoUri: String? = null,
    val legacySourceId: Long? = null,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "catalog_categories", indices = [
    Index(value = ["storeId", "name"], unique = true)
])
data class CategoryEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val name: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val updatedAt: Long
)

@Entity(tableName = "branch_product_prices", primaryKeys = ["branchId", "productId"])
data class BranchProductPriceEntity(
    val branchId: String, val productId: String, val sellingPrice: Long, val updatedAt: Long
)

@Entity(tableName = "branch_inventory", primaryKeys = ["branchId", "productId"])
data class BranchInventoryEntity(
    val branchId: String, val productId: String, val quantity: Int, val updatedAt: Long
)

@Entity(tableName = "product_barcodes", indices = [
    Index(value = ["storeId", "barcode"], unique = true), Index(value = ["productId"])
])
data class ProductBarcodeEntity(
    @PrimaryKey val id: String, val storeId: String, val productId: String, val barcode: String, val createdAt: Long
)


/**
 * Read model returned by CatalogDao.observeBranchCatalog.
 * Selling price and stock are resolved for the selected branch.
 */
data class BranchCatalogRow(
    val id: String,
    val storeId: String,
    val categoryId: String?,
    val name: String,
    val description: String,
    val basePrice: Long,
    val photoUri: String?,
    val legacySourceId: Long?,
    val isActive: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    @androidx.room.ColumnInfo(name = "activeSellingPrice") val activeSellingPrice: Long?,
    @androidx.room.ColumnInfo(name = "branchQuantity") val branchQuantity: Int
)

@Entity(tableName = "sales", indices = [Index(value = ["storeId", "branchId", "createdAt"])])
data class SaleEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val branchId: String,
    val cashierUserId: String,
    val subtotal: Long,
    val discount: Long,
    val total: Long,
    val paid: Long,
    val change: Long,
    val paymentMethod: String,
    val createdAt: Long
)

@Entity(tableName = "sale_items", indices = [Index(value = ["saleId"])])
data class SaleItemEntity(
    @PrimaryKey val id: String,
    val saleId: String,
    val productId: String,
    val productName: String,
    val quantity: Int,
    val unitPrice: Long,
    val lineTotal: Long
)


@Entity(tableName = "sale_sync_outbox", indices = [Index(value = ["status", "createdAt"]), Index(value = ["storeId", "branchId", "status", "createdAt"])])
data class SaleSyncOutboxEntity(
    @PrimaryKey val saleId: String,
    val storeId: String,
    val branchId: String,
    val status: String = "PENDING",
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
