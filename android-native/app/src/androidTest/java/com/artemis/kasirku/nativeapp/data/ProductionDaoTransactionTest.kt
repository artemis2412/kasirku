package com.artemis.kasirku.nativeapp.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProductionDaoTransactionTest {
    private lateinit var database: KasirkuRoomDatabase
    private lateinit var dao: ProductionDao
    private lateinit var catalogDao: CatalogDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            KasirkuRoomDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.productionDao()
        catalogDao = database.catalogDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun productionConsumesIngredientsAddsFinishedStockAndQueuesCloudSyncAtomically() = runBlocking {
        val now = 100L
        val product = ProductEntity(
            id = "product-finished",
            storeId = "store-1",
            categoryId = null,
            name = "Roti",
            basePrice = 5000,
            createdAt = now,
            updatedAt = now
        )
        catalogDao.upsertProduct(product)
        catalogDao.upsertInventory(BranchInventoryEntity("branch-1", product.id, 3, now))

        val ingredient = IngredientEntity("flour-1", "store-1", "Tepung", "gram", true, now)
        dao.putIngredient(ingredient)
        dao.putIngredientStock(
            BranchIngredientInventoryEntity("store-1", "branch-1", ingredient.id, 100.0, now)
        )

        val recipe = ProductRecipeEntity(
            id = "recipe-1",
            storeId = "store-1",
            productId = product.id,
            name = "Resep roti",
            outputQuantity = 1,
            isActive = true,
            updatedAt = now
        )
        dao.putRecipe(recipe)
        dao.putRecipeItem(
            ProductRecipeItemEntity("recipe-item-1", "store-1", recipe.id, ingredient.id, 10.0, now)
        )

        val batchId = dao.completeProduction(
            storeId = "store-1",
            branchId = "branch-1",
            userId = "user-1",
            recipeId = recipe.id,
            outputQuantity = 2,
            notes = null
        )

        assertEquals(80.0, dao.getIngredientStock("branch-1", ingredient.id)?.quantity ?: -1.0, 0.0001)
        assertEquals(5, catalogDao.getInventory("branch-1", product.id)?.quantity)
        val queue = dao.getPendingProductionSync("store-1", "branch-1", "user-1")
        assertEquals(1, queue.size)
        assertEquals(batchId, queue.single().batchId)
        assertEquals(batchId, dao.getProductionBatch("store-1", "branch-1", batchId)?.id)
    }

    @Test
    fun insufficientIngredientStockRollsBackBatchAndFinishedStock() = runBlocking {
        val now = 100L
        val product = ProductEntity(
            id = "product-finished",
            storeId = "store-1",
            categoryId = null,
            name = "Roti",
            basePrice = 5000,
            createdAt = now,
            updatedAt = now
        )
        catalogDao.upsertProduct(product)
        catalogDao.upsertInventory(BranchInventoryEntity("branch-1", product.id, 3, now))
        dao.putIngredient(IngredientEntity("flour-1", "store-1", "Tepung", "gram", true, now))
        dao.putIngredientStock(
            BranchIngredientInventoryEntity("store-1", "branch-1", "flour-1", 5.0, now)
        )
        dao.putRecipe(ProductRecipeEntity("recipe-1", "store-1", product.id, "Resep", 1, true, now))
        dao.putRecipeItem(ProductRecipeItemEntity("recipe-item-1", "store-1", "recipe-1", "flour-1", 10.0, now))

        val error = runCatching {
            dao.completeProduction("store-1", "branch-1", "user-1", "recipe-1", 1, null)
        }.exceptionOrNull()

        assertEquals("Stok bahan baku tidak cukup untuk ingredient flour-1.", error?.message)
        assertEquals(5.0, dao.getIngredientStock("branch-1", "flour-1")?.quantity ?: -1.0, 0.0001)
        assertEquals(3, catalogDao.getInventory("branch-1", product.id)?.quantity)
        assertEquals(0, dao.getPendingProductionSync("store-1", "branch-1", "user-1").size)
    }
}
