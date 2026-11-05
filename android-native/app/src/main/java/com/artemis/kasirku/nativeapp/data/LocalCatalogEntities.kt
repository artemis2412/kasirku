package com.artemis.kasirku.nativeapp.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Store-level product master. Branch stock and branch price are deliberately
 * stored in separate tables so a product can be shared across branches.
 * Monetary values are whole rupiah (Long); stock quantities are whole units.
 */
@Entity(
    tableName = "catalog_products",
    indices = [Index(value = ["storeId", "name"])]
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val categoryId: String?,
    val name: String,
    val description: String = "",
    val basePrice: Long,
    val isActive: Boolean = true,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "catalog_categories",
    indices = [Index(value = ["storeId", "name"], unique = true)]
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val name: String,
    val sortOrder: Int = 0,
    val isActive: Boolean = true,
    val updatedAt: Long
)

/** A branch may override the store's default selling price. */
@Entity(
    tableName = "branch_product_prices",
    primaryKeys = ["branchId", "productId"],
    indices = [Index(value = ["productId"])]
)
data class BranchProductPriceEntity(
    val branchId: String,
    val productId: String,
    val sellingPrice: Long,
    val updatedAt: Long
)

/** Stock is integer-only and independent for each branch. */
@Entity(
    tableName = "branch_inventory",
    primaryKeys = ["branchId", "productId"],
    indices = [Index(value = ["productId"])]
)
data class BranchInventoryEntity(
    val branchId: String,
    val productId: String,
    val quantity: Int,
    val updatedAt: Long
)

/** A product can have multiple barcode values. */
@Entity(
    tableName = "product_barcodes",
    indices = [
        Index(value = ["barcode"], unique = true),
        Index(value = ["productId"])
    ]
)
data class ProductBarcodeEntity(
    @PrimaryKey val id: String,
    val productId: String,
    val barcode: String,
    val createdAt: Long
)
