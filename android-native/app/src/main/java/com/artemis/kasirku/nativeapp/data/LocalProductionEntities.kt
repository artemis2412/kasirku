package com.artemis.kasirku.nativeapp.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "catalog_ingredients", indices = [Index(value = ["storeId", "name"], unique = true)])
data class IngredientEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val name: String,
    val unit: String,
    val isActive: Boolean = true,
    val updatedAt: Long
)

@Entity(tableName = "branch_ingredient_inventory", primaryKeys = ["branchId", "ingredientId"])
data class BranchIngredientInventoryEntity(
    val storeId: String,
    val branchId: String,
    val ingredientId: String,
    val quantity: Double,
    val updatedAt: Long
)

@Entity(tableName = "product_recipes", indices = [Index(value = ["storeId", "productId"], unique = true)])
data class ProductRecipeEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val productId: String,
    val name: String,
    val outputQuantity: Int = 1,
    val isActive: Boolean = true,
    val updatedAt: Long
)

@Entity(tableName = "product_recipe_items", indices = [Index(value = ["recipeId", "ingredientId"], unique = true)])
data class ProductRecipeItemEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val recipeId: String,
    val ingredientId: String,
    val quantity: Double,
    val createdAt: Long
)

@Entity(tableName = "production_batches")
data class ProductionBatchEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val branchId: String,
    val recipeId: String,
    val productId: String,
    val outputQuantity: Int,
    val producedBy: String,
    val notes: String?,
    val createdAt: Long
)

@Entity(tableName = "production_batch_items")
data class ProductionBatchItemEntity(
    @PrimaryKey val id: String,
    val storeId: String,
    val branchId: String,
    val batchId: String,
    val ingredientId: String,
    val quantityUsed: Double,
    val createdAt: Long
)

@Entity(tableName = "production_sync_outbox", indices = [
    Index(value = ["storeId", "branchId", "status", "createdAt"])
])
data class ProductionSyncOutboxEntity(
    @PrimaryKey val batchId: String,
    val storeId: String,
    val branchId: String,
    val producedBy: String,
    val status: String = "PENDING",
    val attempts: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
