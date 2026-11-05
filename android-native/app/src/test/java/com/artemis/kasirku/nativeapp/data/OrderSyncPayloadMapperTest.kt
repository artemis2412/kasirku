package com.artemis.kasirku.nativeapp.data

import java.time.Instant
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OrderSyncPayloadMapperTest {
    @Test
    fun mapsSaleUsingExplicitBranchTimezoneAndStableIdempotencyKey() {
        val sale = sale()
        val payload = OrderSyncPayloadMapper.map(
            sale,
            listOf(item("550e8400-e29b-41d4-a716-446655440000")),
            ZoneId.of("Asia/Makassar")
        )

        assertEquals(sale.id, payload.order.localId)
        assertEquals(sale.id, payload.order.orderNumber)
        assertEquals("2026-11-05", payload.order.orderDate)
        assertEquals("22:00:00", payload.order.orderTime)
        assertEquals(Instant.ofEpochMilli(sale.createdAt).toString(), payload.order.createdAt)
        assertEquals(sale.id, payload.items.single().orderId)
        assertEquals("550e8400-e29b-41d4-a716-446655440000", payload.items.single().productId)
    }

    @Test
    fun orderDateAndTimeFollowEachBranchesOwnTimezone() {
        val sale = sale()
        val items = listOf(item("product-1"))
        val jakarta = OrderSyncPayloadMapper.map(sale, items, ZoneId.of("Asia/Jakarta"))
        val makassar = OrderSyncPayloadMapper.map(sale, items, ZoneId.of("Asia/Makassar"))
        val jayapura = OrderSyncPayloadMapper.map(sale, items, ZoneId.of("Asia/Jayapura"))

        assertEquals("21:00:00", jakarta.order.orderTime)
        assertEquals("22:00:00", makassar.order.orderTime)
        assertEquals("23:00:00", jayapura.order.orderTime)
        assertEquals(jakarta.order.localId, makassar.order.localId)
        assertEquals(makassar.order.localId, jayapura.order.localId)
    }

    @Test
    fun legacyNonUuidProductIdIsKeptAsLocalId() {
        val payload = OrderSyncPayloadMapper.map(
            sale(),
            listOf(item("legacy-product-17")),
            ZoneId.of("Asia/Makassar")
        )
        assertNull(payload.items.single().productId)
        assertEquals("legacy-product-17", payload.items.single().productLocalId)
    }

    @Test
    fun rejectsItemsFromAnotherSale() {
        val error = runCatching {
            OrderSyncPayloadMapper.map(
                sale(),
                listOf(item("product-1").copy(saleId = "other-sale")),
                ZoneId.of("Asia/Makassar")
            )
        }.exceptionOrNull()
        assertEquals("Rincian transaksi tidak cocok.", error?.message)
    }

    @Test
    fun rejectsSubtotalMismatchBeforeAnyCloudWrite() {
        val error = runCatching {
            OrderSyncPayloadMapper.map(
                sale().copy(subtotal = 30_000, total = 30_000, paid = 30_000),
                listOf(item("product-1")),
                ZoneId.of("Asia/Makassar")
            )
        }.exceptionOrNull()
        assertEquals("Subtotal transaksi tidak sesuai rincian.", error?.message)
    }

    private fun sale() = SaleEntity(
        id = "sale-uuid-1", storeId = "store-1", branchId = "branch-1",
        cashierUserId = "user-1", subtotal = 20_000, discount = 0,
        total = 20_000, paid = 20_000, change = 0, paymentMethod = "CASH",
        createdAt = 1_793_887_200_000L
    )

    private fun item(productId: String) = SaleItemEntity(
        id = "item-1", saleId = "sale-uuid-1", productId = productId,
        productName = "Kopi", quantity = 2, unitPrice = 10_000, lineTotal = 20_000
    )
}
