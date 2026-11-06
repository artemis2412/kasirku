package com.artemis.kasirku.nativeapp.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogDaoTransactionTest {
    private lateinit var database: KasirkuRoomDatabase
    private lateinit var dao: CatalogDao

    @Before
    fun createDatabase() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            KasirkuRoomDatabase::class.java
        ).allowMainThreadQueries().build()
        dao = database.catalogDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun successfulSaleDeductsStockAndCreatesOutboxEntry() = runBlocking {
        seedProduct("product-1", "Kopi", 10)
        dao.completeSale(sale("sale-1", subtotal = 20_000), listOf(item("item-1", "sale-1", "product-1", "Kopi", 2)))

        assertEquals(8, dao.getInventory("branch-1", "product-1")?.quantity)
        assertEquals(1, dao.getPendingSaleSync("store-1", "branch-1").size)
        assertEquals(1, dao.getSalesRange("store-1", "branch-1", 0, Long.MAX_VALUE).size)
    }

    @Test
    fun insufficientStockRollsBackEarlierStockDeductionAndSaleWrites() = runBlocking {
        seedProduct("product-1", "Kopi", 10)
        seedProduct("product-2", "Teh", 1)

        val error = runCatching {
            dao.completeSale(
                sale("sale-2", subtotal = 30_000),
                listOf(
                    item("item-2a", "sale-2", "product-1", "Kopi", 2),
                    item("item-2b", "sale-2", "product-2", "Teh", 2)
                )
            )
        }.exceptionOrNull()

        assertEquals("Stok produk tidak mencukupi.", error?.message)
        assertEquals(10, dao.getInventory("branch-1", "product-1")?.quantity)
        assertEquals(1, dao.getInventory("branch-1", "product-2")?.quantity)
        assertEquals(0, dao.getSalesRange("store-1", "branch-1", 0, Long.MAX_VALUE).size)
        assertEquals(0, dao.getPendingSaleSync("store-1", "branch-1").size)
    }

    @Test
    fun invalidLaterItemRollsBackEarlierStockDeduction() = runBlocking {
        seedProduct("product-1", "Kopi", 10)
        seedProduct("product-2", "Teh", 10)

        val error = runCatching {
            dao.completeSale(
                sale("sale-3", subtotal = 30_000),
                listOf(
                    item("item-3a", "sale-3", "product-1", "Kopi", 2),
                    item("item-3b", "sale-3", "product-2", "Nama Palsu", 1)
                )
            )
        }.exceptionOrNull()

        assertEquals("Nama produk pada transaksi tidak sesuai katalog.", error?.message)
        assertEquals(10, dao.getInventory("branch-1", "product-1")?.quantity)
        assertEquals(10, dao.getInventory("branch-1", "product-2")?.quantity)
        assertEquals(0, dao.getSalesRange("store-1", "branch-1", 0, Long.MAX_VALUE).size)
    }

    @Test
    fun pendingSyncQueueIsIsolatedByStoreAndBranch() = runBlocking {
        seedProduct("product-a", "Kopi A", 5, storeId = "store-a", branchId = "branch-a")
        seedProduct("product-b", "Kopi B", 5, storeId = "store-b", branchId = "branch-b")

        dao.completeSale(
            sale("sale-a", 10_000, storeId = "store-a", branchId = "branch-a"),
            listOf(item("item-a", "sale-a", "product-a", "Kopi A", 1))
        )
        dao.completeSale(
            sale("sale-b", 10_000, storeId = "store-b", branchId = "branch-b"),
            listOf(item("item-b", "sale-b", "product-b", "Kopi B", 1))
        )

        assertEquals(listOf("sale-a"), dao.getPendingSaleSync("store-a", "branch-a").map { it.saleId })
        assertEquals(listOf("sale-b"), dao.getPendingSaleSync("store-b", "branch-b").map { it.saleId })
        assertEquals(0, dao.getPendingSaleSync("store-a", "branch-b").size)
        assertEquals(0, dao.getPendingSaleSync("store-b", "branch-a").size)
    }

    private suspend fun seedProduct(
        id: String,
        name: String,
        stock: Int,
        storeId: String = "store-1",
        branchId: String = "branch-1"
    ) {
        dao.upsertProduct(ProductEntity(
            id = id, storeId = storeId, categoryId = null, name = name,
            basePrice = 10_000, createdAt = 1, updatedAt = 1
        ))
        dao.upsertInventory(BranchInventoryEntity(branchId, id, stock, 1))
    }

    private fun sale(
        id: String,
        subtotal: Long,
        storeId: String = "store-1",
        branchId: String = "branch-1"
    ) = SaleEntity(
        id = id, storeId = storeId, branchId = branchId,
        cashierUserId = "user-1", subtotal = subtotal, discount = 0,
        total = subtotal, paid = subtotal, change = 0,
        paymentMethod = "CASH", createdAt = 10
    )

    private fun item(id: String, saleId: String, productId: String, name: String, quantity: Int) =
        SaleItemEntity(
            id = id, saleId = saleId, productId = productId, productName = name,
            quantity = quantity, unitPrice = 10_000, lineTotal = quantity * 10_000L
        )
}
