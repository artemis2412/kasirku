package com.artemis.kasirku.nativeapp.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

@Dao
abstract class ProductionDao {
    @Query("SELECT * FROM catalog_ingredients WHERE storeId = :storeId AND isActive = 1 ORDER BY name COLLATE NOCASE")
    abstract fun observeIngredients(storeId: String): Flow<List<IngredientEntity>>

    @Query("SELECT * FROM branch_ingredient_inventory WHERE storeId = :storeId AND branchId = :branchId")
    abstract fun observeIngredientStock(storeId: String, branchId: String): Flow<List<BranchIngredientInventoryEntity>>

    @Query("SELECT * FROM product_recipes WHERE storeId = :storeId AND isActive = 1 ORDER BY name COLLATE NOCASE")
    abstract fun observeRecipes(storeId: String): Flow<List<ProductRecipeEntity>>

    @Query("SELECT * FROM product_recipe_items WHERE recipeId = :recipeId")
    abstract suspend fun getRecipeItems(recipeId: String): List<ProductRecipeItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putIngredient(row: IngredientEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putIngredientStock(row: BranchIngredientInventoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putRecipe(row: ProductRecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putRecipeItem(row: ProductRecipeItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putBatch(row: ProductionBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun putBatchItem(row: ProductionBatchItemEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun enqueueProductionSync(row: ProductionSyncOutboxEntity): Long

    @Query("SELECT * FROM production_sync_outbox WHERE storeId = :storeId AND branchId = :branchId AND status IN ('PENDING', 'RETRY') ORDER BY createdAt ASC")
    abstract suspend fun getPendingProductionSync(storeId: String, branchId: String): List<ProductionSyncOutboxEntity>

    @Query("UPDATE production_sync_outbox SET status = 'SYNCED', attempts = attempts + 1, lastError = NULL, updatedAt = :updatedAt WHERE batchId = :batchId AND storeId = :storeId AND branchId = :branchId")
    abstract suspend fun markProductionSynced(storeId: String, branchId: String, batchId: String, updatedAt: Long): Int

    @Query("UPDATE production_sync_outbox SET status = :status, attempts = attempts + 1, lastError = :error, updatedAt = :updatedAt WHERE batchId = :batchId AND storeId = :storeId AND branchId = :branchId")
    abstract suspend fun updateProductionSyncStatus(storeId: String, branchId: String, batchId: String, status: String, error: String?, updatedAt: Long): Int

    @Query("SELECT * FROM production_batches WHERE id = :batchId AND storeId = :storeId AND branchId = :branchId LIMIT 1")
    abstract suspend fun getProductionBatch(storeId: String, branchId: String, batchId: String): ProductionBatchEntity?

    @Query("SELECT * FROM branch_ingredient_inventory WHERE branchId = :branchId AND ingredientId = :ingredientId LIMIT 1")
    abstract suspend fun getIngredientStock(branchId: String, ingredientId: String): BranchIngredientInventoryEntity?

    @Query("SELECT * FROM product_recipes WHERE id = :recipeId AND storeId = :storeId AND isActive = 1 LIMIT 1")
    abstract suspend fun getActiveRecipe(storeId: String, recipeId: String): ProductRecipeEntity?

    @Query("SELECT * FROM catalog_products WHERE id = :productId AND storeId = :storeId AND isActive = 1 LIMIT 1")
    abstract suspend fun getActiveProduct(storeId: String, productId: String): ProductEntity?

    @Query("UPDATE branch_ingredient_inventory SET quantity = quantity - :amount, updatedAt = :now WHERE branchId = :branchId AND ingredientId = :ingredientId AND quantity >= :amount")
    abstract suspend fun deductIngredient(branchId: String, ingredientId: String, amount: Double, now: Long): Int

    @Query("UPDATE branch_inventory SET quantity = quantity + :amount, updatedAt = :now WHERE branchId = :branchId AND productId = :productId")
    abstract suspend fun increaseFinishedStock(branchId: String, productId: String, amount: Int, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertFinishedStock(row: BranchInventoryEntity): Long

    @Transaction
    open suspend fun completeProduction(
        storeId: String,
        branchId: String,
        userId: String,
        recipeId: String,
        outputQuantity: Int,
        notes: String?
    ): String {
        require(storeId.isNotBlank() && branchId.isNotBlank() && userId.isNotBlank())
        require(outputQuantity > 0) { "Jumlah produksi harus lebih dari nol." }
        val recipe = getActiveRecipe(storeId, recipeId)
            ?: throw IllegalArgumentException("Resep tidak ditemukan atau tidak aktif.")
        val product = getActiveProduct(storeId, recipe.productId)
            ?: throw IllegalArgumentException("Produk hasil produksi tidak aktif.")
        val lines = getRecipeItems(recipe.id)
        require(lines.isNotEmpty()) { "Resep belum memiliki bahan baku." }
        val now = System.currentTimeMillis()
        val batchId = UUID.randomUUID().toString()
        val usage = lines.map { line ->
            val required = line.quantity * outputQuantity.toDouble() / recipe.outputQuantity
            require(required > 0.0 && required.isFinite()) { "Jumlah bahan baku tidak valid." }
            Triple(line, required, getIngredientStock(branchId, line.ingredientId))
        }
        usage.forEach { (line, required, stock) ->
            require(stock != null && stock.storeId == storeId && stock.quantity >= required) {
                "Stok bahan baku tidak cukup untuk ingredient " + line.ingredientId + "."
            }
        }
        usage.forEach { (line, required, _) ->
            check(deductIngredient(branchId, line.ingredientId, required, now) == 1) {
                "Stok bahan baku berubah. Muat ulang sebelum produksi."
            }
            putBatchItem(ProductionBatchItemEntity(UUID.randomUUID().toString(), storeId, branchId, batchId, line.ingredientId, required, now))
        }
        val existingStock = getFinishedStock(branchId, product.id)
        if (existingStock == null) {
            insertFinishedStock(BranchInventoryEntity(branchId, product.id, outputQuantity, now))
        } else {
            check(increaseFinishedStock(branchId, product.id, outputQuantity, now) == 1)
        }
        putBatch(ProductionBatchEntity(batchId, storeId, branchId, recipe.id, product.id, outputQuantity, userId, notes?.takeIf { it.isNotBlank() }, now))
        enqueueProductionSync(ProductionSyncOutboxEntity(
            batchId = batchId,
            storeId = storeId,
            branchId = branchId,
            producedBy = userId,
            status = "PENDING",
            createdAt = now,
            updatedAt = now
        ))
        return batchId
    }

    @Query("SELECT * FROM branch_inventory WHERE branchId = :branchId AND productId = :productId LIMIT 1")
    abstract suspend fun getFinishedStock(branchId: String, productId: String): BranchInventoryEntity?
}
